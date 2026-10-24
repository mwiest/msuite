package com.msuite.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.PlugId
import com.msuite.core.model.Stamp
import com.msuite.core.storage.db.Field_state
import com.msuite.core.storage.db.MsuiteDatabase
import com.msuite.core.storage.db.PlugStateQueries
import kotlin.uuid.Uuid

/**
 * The device's synced state (`field_state`, decision 0035) and its per-plug bookkeeping (decision
 * 0036), in SQLite. Knows nothing of merge or HLC rules; `core/sync` builds on it.
 */
class LocalStore(private val driver: SqlDriver) : AutoCloseable {
    private val db = MsuiteDatabase(driver)

    /** Runs [block] atomically; nested calls join the outer transaction. */
    fun <R> transaction(block: () -> R): R = db.transactionWithResult { block() }

    fun cell(key: CellKey): Cell? =
        db.fieldStateQueries.cell(key.recordId.toByteArray(), key.fieldId.id.toLong()).executeAsOneOrNull()?.toCell()

    /** Every cell with an HLC above [above], or all of them for `null`. */
    fun cells(above: Hlc? = null): List<Cell> {
        val query = if (above == null) db.fieldStateQueries.cells() else db.fieldStateQueries.cellsAbove(above.toSql())
        return query.executeAsList().map { it.toCell() }
    }

    fun cells(recordId: Uuid): List<Cell> =
        db.fieldStateQueries.recordCells(recordId.toByteArray()).executeAsList().map { it.toCell() }

    /** This device's ID; on first call, stores the one from [create] (decision 0058). */
    fun deviceId(create: () -> DeviceId): DeviceId = transaction {
        db.deviceQueries.deviceId().executeAsOneOrNull()?.let(::DeviceId)
            ?: create().also { db.deviceQueries.setDeviceId(it.bits) }
    }

    fun maxHlc(): Hlc? = db.fieldStateQueries.maxHlc().executeAsOne().hlc?.let(::hlcFromSql)

    /** Writes [cells], replacing any stored cell with the same key. */
    fun write(cells: Iterable<Cell>) = transaction {
        cells.forEach { db.fieldStateQueries.upsert(it.toRow()) }
    }

    /** Per-plug bookkeeping (decision 0036). */
    val plugs = PlugBook(db.plugStateQueries)

    override fun close() = driver.close()

    companion object {
        /** For the platform driver, which creates or migrates the database. */
        val Schema: SqlSchema<QueryResult.Value<Unit>> get() = MsuiteDatabase.Schema
    }
}

class PlugBook internal constructor(private val queries: PlugStateQueries) {
    fun isIngested(plug: PlugId, batchKey: String): Boolean = queries.isIngested(plug.id, batchKey).executeAsOne()

    fun markIngested(plug: PlugId, batchKey: String) {
        queries.markIngested(plug.id, batchKey)
    }

    /** One more failed decode of [batchKey]; returns how many were recorded in total (decision 0069). */
    fun recordUnreadable(plug: PlugId, batchKey: String): Int = queries.transactionWithResult {
        if (queries.bumpUnreadable(plug.id, batchKey).value > 0) {
            queries.unreadable(plug.id, batchKey).executeAsOne().toInt()
        } else {
            queries.insertUnreadable(plug.id, batchKey)
            1
        }
    }

    /** Flags [batchKey] to stop being fetched; the bytes stay in the backend (decision 0069). */
    fun markCorrupt(plug: PlugId, batchKey: String) {
        queries.markCorrupt(plug.id, batchKey)
    }

    fun isCorrupt(plug: PlugId, batchKey: String): Boolean = queries.isCorrupt(plug.id, batchKey).executeAsOne()

    /** The highest HLC pushed to [plug], or `null` if nothing was pushed yet. */
    fun watermark(plug: PlugId): Hlc? = queries.watermark(plug.id).executeAsOneOrNull()?.let(::hlcFromSql)

    fun setWatermark(plug: PlugId, hlc: Hlc) {
        queries.setWatermark(plug.id, hlc.toSql())
    }

    /** Get all ingested batch keys for a plug (for pruning). */
    fun ingestedKeys(plug: PlugId): List<String> = queries.ingestedKeys(plug.id).executeAsList()

    /** Get all unreadable batch keys for a plug (for pruning). */
    fun unreadableKeys(plug: PlugId): List<String> = queries.unreadableKeys(plug.id).executeAsList()

    /** Delete a specific ingested batch entry (for pruning). */
    fun deleteIngested(plug: PlugId, batchKey: String) {
        queries.deleteIngested(plug.id, batchKey)
    }

    /** Delete a specific unreadable batch entry (for pruning). */
    fun deleteUnreadable(plug: PlugId, batchKey: String) {
        queries.deleteUnreadable(plug.id, batchKey)
    }

    /**
     * Prune bookkeeping entries that are no longer in the backend. Removes rows from
     * `ingested_batch` and `unreadable_batch` for keys not in [currentBackendKeys].
     * This keeps bookkeeping bounded after compaction (decision 0002).
     */
    fun prune(plug: PlugId, currentBackendKeys: Set<String>) = queries.transactionWithResult {
        val ingested = ingestedKeys(plug)
        val unreadable = unreadableKeys(plug)

        var prunedCount = 0
        for (key in ingested) {
            if (key !in currentBackendKeys) {
                deleteIngested(plug, key)
                prunedCount++
            }
        }
        for (key in unreadable) {
            if (key !in currentBackendKeys) {
                deleteUnreadable(plug, key)
                prunedCount++
            }
        }
        prunedCount
    }
}

private fun Hlc.toSql(): Long = packed xor Long.MIN_VALUE

private fun hlcFromSql(stored: Long): Hlc = Hlc.fromPacked(stored xor Long.MIN_VALUE)

private fun Cell.toRow() = Field_state(
    record_id = key.recordId.toByteArray(),
    field_id = key.fieldId.id.toLong(),
    value_ = value,
    hlc = stamp.hlc.toSql(),
    device_id = stamp.deviceId.bits,
)

private fun Field_state.toCell() = Cell(
    key = CellKey(Uuid.fromByteArray(record_id), FieldId(field_id.toInt())),
    value = value_,
    stamp = Stamp(hlcFromSql(hlc), DeviceId(device_id)),
)
