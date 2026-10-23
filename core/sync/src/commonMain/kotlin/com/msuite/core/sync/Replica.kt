package com.msuite.core.sync

import app.cash.sqldelight.db.SqlDriver
import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import com.msuite.core.model.PlugId
import com.msuite.core.model.Stamp
import com.msuite.core.storage.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Failed decodes of one key before it is flagged corrupt and stops being fetched (decision 0069). */
const val MAX_DECODE_ATTEMPTS = 3

/**
 * The device's state, in its database, with its HLC clock. The clock starts from the highest
 * stored HLC, so writes after a restart order after everything stored, even if the wall clock went
 * back. Every operation runs alone (the clock isn't thread-safe), in one transaction, in [context]
 * (the driver blocks; keep it off the UI thread). The replica owns the database: open one per
 * database and write only through it.
 * Apps use [open]; the constructor with an explicit [deviceId] is for tests and the simulator.
 */
class Replica private constructor(
    private val store: LocalStore,
    private val deviceId: DeviceId,
    wallClock: Clock,
    private val context: CoroutineContext,
) : AutoCloseable {
    constructor(
        driver: SqlDriver,
        deviceId: DeviceId,
        wallClock: Clock,
        context: CoroutineContext = Dispatchers.Default,
    ) : this(LocalStore(driver), deviceId, wallClock, context)

    private val clock = HlcClock(wallClock, store.maxHlc() ?: Hlc.ZERO)
    private val mutex = Mutex()
    private val ticks = MutableStateFlow(0L)

    /** Ticks after each committed write or ingest that changed state (decision 0049). */
    val changes: StateFlow<Long> = ticks.asStateFlow()

    /** A local edit: every value gets the same fresh HLC. Returns the stored cells. */
    suspend fun write(values: Map<CellKey, ByteArray>): List<Cell> {
        if (values.isEmpty()) return emptyList()
        return serialized {
            val stamp = Stamp(clock.now(), deviceId)
            values.map { (key, value) -> Cell(key, value, stamp) }.also(store::write)
        }.also { tick() }
    }

    /** Merges [batch], fetched from [plug] under [batchKey]; a key ingested before is skipped. */
    suspend fun ingest(plug: PlugId, batchKey: String, batch: Batch): IngestResult = serialized {
        if (store.plugs.isIngested(plug, batchKey)) return@serialized IngestResult.Merged(emptyList())
        mergeLocked(batch).also { result ->
            if (result is IngestResult.Merged) store.plugs.markIngested(plug, batchKey)
        }
    }

    /**
     * Records that [batchKey]'s bytes failed to decode; returns true once it is flagged corrupt
     * (decision 0069): after [MAX_DECODE_ATTEMPTS] failures it is not fetched again, the bytes
     * staying in the backend. A failed *fetch* is not a failed decode: a key absent at get time
     * (a list/get race) is skipped and never recorded, so a pruned or briefly missing object is
     * not flagged corrupt.
     */
    suspend fun unreadable(plug: PlugId, batchKey: String): Boolean = serialized {
        if (store.plugs.recordUnreadable(plug, batchKey) < MAX_DECODE_ATTEMPTS) return@serialized false
        store.plugs.markCorrupt(plug, batchKey)
        true
    }

    /** Whether [batchKey] failed to decode too often and must not be fetched again (decision 0069). */
    suspend fun isCorrupt(plug: PlugId, batchKey: String): Boolean = serialized {
        store.plugs.isCorrupt(plug, batchKey)
    }

    /** Merges [batch] with no plug bookkeeping: the manual snapshot path (decision 0066). */
    suspend fun mergeRemote(batch: Batch): IngestResult = serialized { mergeLocked(batch) }

    /** What [plug] hasn't been sent yet, or `null`; after a successful put, call [pushed]. */
    suspend fun delta(plug: PlugId): Batch? = serialized {
        val watermark = store.plugs.watermark(plug)
        buildDelta(store.cells(above = watermark), watermark)
    }

    suspend fun pushed(plug: PlugId, delta: Batch) {
        val hlcHi = delta.hlcHi() ?: return
        serialized { store.plugs.setWatermark(plug, hlcHi) }
    }

    /** Every cell, or only [recordId]'s cells. */
    suspend fun cells(recordId: Uuid? = null): List<Cell> = serialized {
        if (recordId == null) store.cells() else store.cells(recordId)
    }

    /**
     * Prunes bookkeeping entries for keys no longer in the backend (decision 0002).
     * Removes rows from `ingested_batch` and `unreadable_batch` for keys not in [currentBackendKeys].
     * Returns the number of entries pruned.
     */
    suspend fun prune(plug: PlugId, currentBackendKeys: Set<String>): Int = serialized {
        store.plugs.prune(plug, currentBackendKeys)
    }

    override fun close() = store.close()

    private fun tick() = ticks.update { it + 1 }

    /** Merges under the lock, ticking when something changed; no plug bookkeeping. */
    private fun mergeLocked(batch: Batch): IngestResult {
        val result = ingest(batch, store::cell, clock)
        if (result is IngestResult.Merged && result.changes.isNotEmpty()) {
            store.write(result.changes)
            tick()
        }
        return result
    }

    companion object {
        /** Opens the replica with the device ID stored in the database, created on first open (decision 0058). */
        fun open(
            driver: SqlDriver,
            wallClock: Clock,
            random: Random = Random.Default,
            context: CoroutineContext = Dispatchers.Default,
        ): Replica {
            val store = LocalStore(driver)
            return Replica(store, store.deviceId { DeviceId.random(random) }, wallClock, context)
        }
    }

    private suspend fun <R> serialized(block: () -> R): R = withContext(context) {
        mutex.withLock { store.transaction(block) }
    }
}
