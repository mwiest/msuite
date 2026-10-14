package com.msuite.core.sync.testing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import com.msuite.core.model.PlugId
import com.msuite.core.model.Stamp
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.HlcClock
import com.msuite.core.sync.IngestResult
import com.msuite.core.sync.Replica
import com.msuite.core.sync.buildDelta
import com.msuite.core.sync.hlcHi
import com.msuite.core.sync.ingest
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.time.Clock

/** Where a simulated device keeps its state and clock. */
interface SimState : AutoCloseable {
    fun cells(): Map<CellKey, Cell>

    /** A local write, stamped by the device's clock. */
    fun write(key: CellKey, value: ByteArray): Cell

    fun delta(): Batch?

    fun pushed(delta: Batch)

    fun ingest(batchKey: String, batch: Batch): IngestResult

    override fun close() {}
}

typealias SimBackend = (wall: Clock, deviceId: DeviceId) -> SimState

/** The pure functions over a map. */
class MemoryState(wall: Clock, private val deviceId: DeviceId) : SimState {
    private val clock = HlcClock(wall)
    private val state = mutableMapOf<CellKey, Cell>()
    private var watermark: Hlc? = null

    override fun cells(): Map<CellKey, Cell> = state.toMap()

    override fun write(key: CellKey, value: ByteArray): Cell =
        Cell(key, value, Stamp(clock.now(), deviceId)).also { state[key] = it }

    override fun delta(): Batch? = buildDelta(state.values, watermark)

    override fun pushed(delta: Batch) {
        watermark = delta.hlcHi()
    }

    override fun ingest(batchKey: String, batch: Batch): IngestResult = ingest(batch, state::get, clock).also {
        if (it is IngestResult.Merged) it.changes.forEach { cell -> state[cell.key] = cell }
    }
}

/** The real [Replica] over an in-memory SQLite database. */
class ReplicaState(wall: Clock, deviceId: DeviceId) : SimState {
    /** For app commands that write through the replica themselves. */
    val replica =
        Replica(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema), deviceId, wall)

    override fun cells(): Map<CellKey, Cell> = runBlocking { replica.cells() }.associateBy { it.key }

    override fun write(key: CellKey, value: ByteArray): Cell = runBlocking {
        replica.write(mapOf(key to value))
    }.single()

    override fun delta(): Batch? = runBlocking { replica.delta(PLUG) }

    override fun pushed(delta: Batch) = runBlocking { replica.pushed(PLUG, delta) }

    override fun ingest(batchKey: String, batch: Batch): IngestResult =
        runBlocking { replica.ingest(PLUG, batchKey, batch) }

    override fun close() = replica.close()

    private companion object {
        val PLUG = PlugId("sim")
    }
}
