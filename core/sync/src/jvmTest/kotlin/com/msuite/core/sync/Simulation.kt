package com.msuite.core.sync

import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.FractionalIndex
import com.msuite.core.model.Hlc
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.Stamp
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.uuid.Uuid

/** One step of a generated schedule. Indices are taken modulo the device and record counts. */
sealed interface Step {
    val device: Int

    data class Write(override val device: Int, val record: Int, val field: Int, val seed: Long) : Step

    /** The wall clock moves forward by [millis]. */
    data class Tick(override val device: Int, val millis: Long) : Step

    /** The wall clock is set forward or back by [millis]. */
    data class Jump(override val device: Int, val millis: Long) : Step

    /** Uploads the delta; a [fails] put is lost and retried by a later push. */
    data class Push(override val device: Int, val fails: Boolean) : Step

    /** Downloads a seeded subset of the unseen batches in a seeded order, re-fetching some seen ones. */
    data class Pull(override val device: Int, val seed: Long) : Step

    /** The device goes offline or comes back. */
    data class Toggle(override val device: Int) : Step
}

/** The fields of the simulated records, each with its own kind of value. */
enum class SimField(val id: FieldId) {
    TITLE(FieldId(10)),
    DONE(FieldId(11)),
    POSITION(FieldId(12)),
    DELETED(FieldId(2)),
}

class SimDevice(val id: DeviceId, wallMillis: Long, seed: Long, backend: SimBackend) {
    val wall = TestClock(wallMillis)
    val state = backend(wall, id)
    val ingested = mutableSetOf<Int>()
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
 * The Layer 1 merge/HLC simulator (decision 0007, docs/testing.md): devices with their own clocks,
 * sharing an append-only store of batches through the real [ingest], [buildDelta] and [merge], each
 * device's state kept by [backend]: in memory, or in a [Replica] over SQLite.
 */
class Simulation(
    wallOffsets: List<Long>,
    seed: Long,
    recordCount: Int = 4,
    backend: SimBackend = ::MemoryState,
) : AutoCloseable {
    val records = List(recordCount) { Uuid.fromLongs(seed, it.toLong()) }
    val devices = wallOffsets.mapIndexed { i, offset ->
        SimDevice(DeviceId(seed + i), START + offset, seed + i, backend)
    }
    val store = mutableListOf<Batch>()

    /** Every cell ever written, the oracle for the LWW invariant. */
    val writes = mutableListOf<Cell>()

    fun run(step: Step) {
        val device = devices[step.device.mod(devices.size)]
        when (step) {
            is Step.Write -> write(device, records[step.record.mod(records.size)], step.field, step.seed)
            is Step.Tick -> device.setWall(device.wall.millis + step.millis)
            is Step.Jump -> device.setWall(device.wall.millis + step.millis)
            is Step.Push -> if (device.online && !step.fails) push(device)
            is Step.Pull -> if (device.online) pull(device, Random(step.seed))
            is Step.Toggle -> device.online = !device.online
        }
        checkClockBound(device)
    }

    fun write(device: SimDevice, record: Uuid, field: Int, seed: Long) {
        val simField = SimField.entries[field.mod(SimField.entries.size)]
        val seen = device.cells().values
        val value = when (simField) {
            SimField.TITLE -> Value.Text("t$seed")
            SimField.DONE, SimField.DELETED -> Value.Bool(seed % 2 == 0L)
            SimField.POSITION -> Value.Text(newPosition(device, record, seed))
        }
        val cell = device.state.write(CellKey(record, simField.id), ValueCodec.encode(value))
        // Invariant 5: a write orders after everything the device has seen.
        seen.forEach { cell.stamp.hlc shouldBeGreaterThan it.stamp.hlc }
        writes += cell
    }

    fun push(device: SimDevice) {
        val delta = device.state.delta() ?: return
        store += delta
        device.ingested += store.lastIndex
        device.state.pushed(delta)
    }

    fun pull(device: SimDevice, random: Random) {
        val unseen = store.indices.filter { it !in device.ingested && random.nextInt(10) < 7 }
        val again = device.ingested.filter { random.nextInt(10) == 0 }
        for (index in (unseen + again).shuffled(random)) ingestOne(device, index)
    }

    /** Downloads every unseen batch, in store order. */
    fun pullAll(device: SimDevice) {
        store.indices.filter { it !in device.ingested }.forEach { ingestOne(device, it) }
    }

    /** Connects everyone, moves every wall clock past the 24 h bound, and syncs to a fixpoint. */
    fun quiesce() {
        val latest = (devices.map { it.wall.millis } + writes.map { it.stamp.hlc.pt }).max()
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
                device.state.delta().shouldBe(null)
                (store.indices.toSet() - device.ingested).shouldBeEmpty()
            }
        }
    }

    fun value(device: Int, record: Int, field: SimField): Value? =
        devices[device].cells()[CellKey(records[record], field.id)]?.let { ValueCodec.decode(it.value) }

    /** The records with a position, as every device sorts them: by `(position, recordId)`. */
    fun order(device: SimDevice): List<Uuid> = device.cells().values
        .filter { it.key.fieldId == SimField.POSITION.id }
        .sortedWith(compareBy<Cell> { (ValueCodec.decode(it.value) as Value.Text).value }.thenBy { it.key.recordId })
        .map { it.key.recordId }

    private fun ingestOne(device: SimDevice, index: Int) {
        val batch = store[index]
        when (val result = device.state.ingest(index.toString(), batch)) {
            IngestResult.Deferred -> {
                // Invariant 13: deferred only when more than 24 h ahead of this device's clock.
                batch.hlcHi()!!.pt shouldBeGreaterThan device.wall.millis + HlcClock.MAX_AHEAD.inWholeMilliseconds
            }

            is IngestResult.Merged -> {
                // Invariant 3: delivering a batch again changes nothing.
                if (index in device.ingested) result.changes.shouldBeEmpty()
                device.ingested += index
            }
        }
    }

    private fun newPosition(device: SimDevice, record: Uuid, seed: Long): String {
        val others = order(device).filter { it != record }.map { positionOf(device, it) }
        val slot = seed.mod(others.size + 1)
        val before = others.getOrNull(slot - 1)
        val after = others.getOrNull(slot)
        // Equal keys from concurrent inserts leave no gap between them: append instead.
        return if (before != null && after != null && before >= after) {
            FractionalIndex.keyBetween(others.last(), null, device.jitter)
        } else {
            FractionalIndex.keyBetween(before, after, device.jitter)
        }
    }

    private fun positionOf(device: SimDevice, record: Uuid): String =
        (ValueCodec.decode(device.cells().getValue(CellKey(record, SimField.POSITION.id)).value) as Value.Text).value

    // Invariant 13: merging never pushes a device's HLC more than 24 h past its wall clock.
    private fun checkClockBound(device: SimDevice) {
        device.cells().values.forEach {
            it.stamp.hlc.pt shouldBeLessThanOrEqualTo device.maxWallSeen + HlcClock.MAX_AHEAD.inWholeMilliseconds
        }
    }

    /** Invariants 1, 4, 6, 8, 9 and 10 after [quiesce]. */
    fun checkConverged() {
        val bytes = devices.map { ProtoBatchEncoder.encode(it.snapshot()).toList() }
        withClue("invariant 1: identical state") { bytes.distinct().size shouldBe 1 }
        val expected = writes.groupBy { it.key }.mapValues { (_, cells) -> cells.maxWith(LWW_ORDER) }
        withClue("invariants 4, 6, 10: each field holds its LWW winner, and only written fields exist") {
            devices.forEach { it.cells() shouldBe expected }
        }
        val positioned = writes.filter { it.key.fieldId == SimField.POSITION.id }.map { it.key.recordId }.toSet()
        withClue("invariants 8, 9: every positioned item once, in the same order everywhere") {
            devices.forEach {
                order(it).sorted() shouldBe positioned.sorted()
                order(it) shouldBe order(devices.first())
            }
        }
    }

    /** Invariant 11: a snapshot stands in for the deltas it folds, for a fresh device and for [earlier] state. */
    fun checkSnapshots(earlier: Map<CellKey, Cell>) {
        val final = devices.first().cells()
        val snapshot = devices.first().snapshot()
        fun ingestAll(start: Map<CellKey, Cell>, batches: List<Batch>): Map<CellKey, Cell> {
            val state = start.toMutableMap()
            val clock = HlcClock(TestClock(devices.first().wall.millis))
            batches.forEach { batch ->
                val result = ingest(batch, state::get, clock) as IngestResult.Merged
                result.changes.forEach { state[it.key] = it }
            }
            return state
        }
        ingestAll(emptyMap(), store) shouldBe final
        ingestAll(emptyMap(), listOf(snapshot)) shouldBe final
        ingestAll(earlier, listOf(snapshot)) shouldBe ingestAll(earlier, store)
    }

    override fun close() = devices.forEach { it.state.close() }

    companion object {
        const val START = 1_760_000_000_000L
        private const val QUIESCENCE_ROUNDS = 3
    }
}
