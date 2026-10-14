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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * The device's state, in the database behind [driver], with its HLC clock. The clock starts from
 * the highest stored HLC, so writes after a restart order after everything stored, even if the
 * wall clock went back. Every operation runs alone (the clock isn't thread-safe) and in one
 * transaction. The replica owns the database: open one per database and write only through it.
 */
class Replica(driver: SqlDriver, private val deviceId: DeviceId, wallClock: Clock) : AutoCloseable {
    private val store = LocalStore(driver)
    private val clock = HlcClock(wallClock, store.maxHlc() ?: Hlc.ZERO)
    private val mutex = Mutex()

    /** A local edit: every value gets the same fresh HLC. Returns the stored cells. */
    suspend fun write(values: Map<CellKey, ByteArray>): List<Cell> = serialized {
        val stamp = Stamp(clock.now(), deviceId)
        values.map { (key, value) -> Cell(key, value, stamp) }.also(store::write)
    }

    /** Merges [batch], fetched from [plug] under [batchKey]; a key ingested before is skipped. */
    suspend fun ingest(plug: PlugId, batchKey: String, batch: Batch): IngestResult = serialized {
        if (store.isIngested(plug, batchKey)) return@serialized IngestResult.Merged(emptyList())
        ingest(batch, store::cell, clock).also { result ->
            if (result is IngestResult.Merged) {
                store.write(result.changes)
                store.markIngested(plug, batchKey)
            }
        }
    }

    /** What [plug] hasn't been sent yet, or `null`; after a successful put, call [pushed]. */
    suspend fun delta(plug: PlugId): Batch? = serialized {
        val watermark = store.watermark(plug)
        buildDelta(store.cells(above = watermark), watermark)
    }

    suspend fun pushed(plug: PlugId, delta: Batch) = serialized {
        delta.hlcHi()?.let { store.setWatermark(plug, it) }
    }

    suspend fun cells(): List<Cell> = serialized { store.cells() }

    override fun close() = store.close()

    private suspend fun <R> serialized(block: () -> R): R = mutex.withLock { store.transaction(block) }
}
