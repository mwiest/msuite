package com.msuite.core.sync.testing

import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.HlcClock
import com.msuite.core.sync.IngestResult
import com.msuite.core.sync.ObjectKey
import com.msuite.core.sync.hlcHi
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import kotlin.random.Random

/** One step of a generated schedule. Device indices are taken modulo the device count; apps add write steps. */
interface SimStep {
    val device: Int
}

/** The steps every app shares: clocks and the network. */
sealed interface NetStep : SimStep {
    /** The wall clock moves forward by [millis]. */
    data class Tick(override val device: Int, val millis: Long) : NetStep

    /** The wall clock is set forward or back by [millis]. */
    data class Jump(override val device: Int, val millis: Long) : NetStep

    /** Uploads the delta; a [fails] put is lost and retried by a later push. */
    data class Push(override val device: Int, val fails: Boolean) : NetStep

    /** Downloads a seeded subset of the unseen batches in a seeded order, re-fetching some seen ones. */
    data class Pull(override val device: Int, val seed: Long) : NetStep

    /** The device goes offline or comes back. */
    data class Toggle(override val device: Int) : NetStep

    companion object {
        const val HOUR = 3_600_000L
        private const val MAX_TICK = 5_000L
        private const val MAX_JUMP = 2 * HOUR

        /** Ticks of up to 5 s, jumps of up to 2 h either way, and pushes and pulls twice as likely. */
        fun arb(device: Arb<Int>): Arb<NetStep> = Arb.choice(
            Arb.bind(device, Arb.long(0L..MAX_TICK), ::Tick),
            Arb.bind(device, Arb.long(-MAX_JUMP..MAX_JUMP), ::Jump),
            Arb.bind(device, Arb.boolean(), ::Push),
            Arb.bind(device, Arb.long(), ::Pull),
            Arb.bind(device, Arb.long(), ::Pull),
            device.map(::Toggle),
        )
    }
}

class SimDevice(val id: DeviceId, wallMillis: Long, seed: Long, backend: SimBackend) {
    val wall = TestClock(wallMillis)
    val state = backend(wall, id)
    val ingested = mutableSetOf<String>()
    var online = true
    var maxWallSeen = wallMillis
    val jitter = Random(seed)

    fun cells(): Map<CellKey, Cell> = state.cells()

    fun snapshot(): Batch = Batch(Model.DATA_MODEL_VERSION, cells().values.toList())

    fun setWall(millis: Long) {
        wall.millis = millis.coerceAtLeast(0)
        maxWallSeen = maxOf(maxWallSeen, wall.millis)
    }
}

/**
 * The multi-device harness of the simulator (decisions 0007, 0057, docs/testing.md): devices with
 * their own clocks, syncing through an append-only [store], each device's state kept by [backend].
 * Layer 1 publishes deltas into a [BatchStore]; Layer 2 sets [snapshots] and a [PlugStore] to run
 * the real plug contract. Apps extend it with their own write steps and invariants. Checks
 * invariants 3 and 13 as it goes.
 */
open class Cluster(
    wallOffsets: List<Long>,
    seed: Long,
    backend: SimBackend,
    protected val store: SimStore = BatchStore(),
    protected val snapshots: Boolean = false,
) : AutoCloseable {
    val devices = wallOffsets.mapIndexed { i, offset ->
        SimDevice(DeviceId(seed + i), START + offset, seed + i, backend)
    }

    fun device(step: SimStep): SimDevice = devices[step.device.mod(devices.size)]

    fun run(step: NetStep) {
        val device = device(step)
        when (step) {
            is NetStep.Tick -> device.setWall(device.wall.millis + step.millis)
            is NetStep.Jump -> device.setWall(device.wall.millis + step.millis)
            is NetStep.Push -> if (device.online && !step.fails) push(device)
            is NetStep.Pull -> if (device.online) pull(device, Random(step.seed))
            is NetStep.Toggle -> device.online = !device.online
        }
        checkClockBound(device)
    }

    fun push(device: SimDevice) {
        val payload = device.state.outgoing(snapshots) ?: return
        device.ingested += store.put(payload)
        device.state.pushed(payload)
    }

    fun pull(device: SimDevice, random: Random) {
        val unseen = store.keys().filter {
            it !in device.ingested &&
                !device.state.isCorrupt(it) &&
                random.nextInt(TENTHS) < FETCH_TENTHS &&
                !isCoveredBySnapshot(it, device.ingested)
        }
        val again = device.ingested.filter { random.nextInt(TENTHS) < REFETCH_TENTHS }
        for (key in (unseen + again).shuffled(random)) ingestOne(device, key)
    }

    /** Downloads every unseen batch, in store order. */
    fun pullAll(device: SimDevice) {
        store.keys().filter {
            it !in device.ingested &&
                !device.state.isCorrupt(it) &&
                !isCoveredBySnapshot(it, device.ingested)
        }.forEach { ingestOne(device, it) }
    }

    /**
     * Span skip: a delta is covered if the device has ingested a snapshot from the same writer
     * whose HLC span contains the delta's span. Only applies when using real key grammar (0029).
     */
    private fun isCoveredBySnapshot(key: String, ingested: Set<String>): Boolean {
        val objKey = try {
            ObjectKey.parse(key)
        } catch (e: IllegalArgumentException) {
            return false // Not a real key (e.g., Layer 1 uses numeric keys)
        }

        if (objKey.prefix.type != BatchType.Delta) return false

        // Find all ingested snapshots from the same writer
        val writerSnapshots = ingested.mapNotNull { ingestedKey ->
            try {
                val ingestedObjKey = ObjectKey.parse(ingestedKey)
                if (ingestedObjKey.prefix.type == BatchType.Snapshot &&
                    ingestedObjKey.prefix.deviceId == objKey.prefix.deviceId
                ) {
                    ingestedObjKey.prefix.hlcLo to ingestedObjKey.prefix.hlcHi
                } else {
                    null
                }
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        // Check if any snapshot covers this delta's span
        return writerSnapshots.any { (snapLo, snapHi) ->
            snapLo <= objKey.prefix.hlcLo && objKey.prefix.hlcHi <= snapHi
        }
    }

    /** Connects everyone, moves every wall clock past the 24 h bound, and syncs to a fixpoint. */
    fun quiesce() {
        val written = devices.flatMap { it.cells().values } + store.batches().flatMap { it.cells }
        val latest = (devices.map { it.wall.millis } + written.map { it.stamp.hlc.pt }).max()
        val caughtUp = latest + HlcClock.MAX_AHEAD.inWholeMilliseconds + 1
        devices.forEach {
            it.online = true
            it.setWall(maxOf(it.wall.millis, caughtUp))
        }
        repeat(QUIESCENCE_ROUNDS) {
            devices.forEach(::push)
            devices.forEach(::pullAll)
        }
        withClue("still syncing after $QUIESCENCE_ROUNDS rounds") {
            devices.forEach { device ->
                device.state.outgoing(snapshots).shouldBe(null)
                (store.keys().toSet() - device.ingested).filterNot(device.state::isCorrupt).shouldBeEmpty()
            }
        }
    }

    /** Invariant 1 after [quiesce]: byte-identical state on every device. */
    fun checkIdentical() {
        val bytes = devices.map { ProtoBatchEncoder.encode(it.snapshot()).toList() }
        withClue("invariant 1: identical state") { bytes.distinct().size shouldBe 1 }
    }

    // Invariant 13: merging never pushes a device's HLC more than 24 h past its wall clock.
    protected fun checkClockBound(device: SimDevice) {
        device.cells().values.forEach {
            it.stamp.hlc.pt shouldBeLessThanOrEqualTo device.maxWallSeen + HlcClock.MAX_AHEAD.inWholeMilliseconds
        }
    }

    private fun ingestOne(device: SimDevice, key: String) {
        when (val fetched = store.get(key)) {
            // Absent is a list/get race, not a failed decode: skipped, never recorded (decision 0069).
            null -> Unit

            // A too-new header or an unknown keyGen is held back without counting as corrupt (0067, 0008).
            Fetch.Skip -> Unit

            // A key that won't decode is recorded and retried, then flagged corrupt (decision 0069).
            Fetch.Unreadable -> device.state.unreadable(key)

            is Fetch.Read -> ingest(device, key, fetched.batch)
        }
    }

    private fun ingest(device: SimDevice, key: String, batch: Batch) {
        when (val result = device.state.ingest(key, batch)) {
            IngestResult.Deferred -> {
                // Invariant 13: deferred only when more than 24 h ahead of this device's clock.
                batch.hlcHi()!!.pt shouldBeGreaterThan device.wall.millis + HlcClock.MAX_AHEAD.inWholeMilliseconds
            }

            IngestResult.TooNew -> error("the simulator never produces a newer-version batch")

            IngestResult.UnknownKey -> error("the simulator holds every root")

            is IngestResult.Merged -> {
                // Invariant 3: delivering a batch again changes nothing.
                if (key in device.ingested) result.changes.shouldBeEmpty()
                device.ingested += key
            }
        }
    }

    override fun close() = devices.forEach { it.state.close() }

    companion object {
        const val START = 1_760_000_000_000L
        private const val QUIESCENCE_ROUNDS = 3

        // A pull fetches 7 in 10 unseen batches and re-fetches 1 in 10 seen ones.
        private const val TENTHS = 10
        private const val FETCH_TENTHS = 7
        private const val REFETCH_TENTHS = 1
    }
}
