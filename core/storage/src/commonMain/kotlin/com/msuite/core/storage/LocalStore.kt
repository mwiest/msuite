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

    fun maxHlc(): Hlc? = db.fieldStateQueries.maxHlc().executeAsOne().hlc?.let(::hlcFromSql)

    /** Writes [cells], replacing any stored cell with the same key. */
    fun write(cells: Iterable<Cell>) = transaction {
        cells.forEach { db.fieldStateQueries.upsert(it.toRow()) }
    }

    fun isIngested(plug: PlugId, batchKey: String): Boolean =
        db.plugStateQueries.isIngested(plug.id, batchKey).executeAsOne()

    fun markIngested(plug: PlugId, batchKey: String) {
        db.plugStateQueries.markIngested(plug.id, batchKey)
    }

    /** The highest HLC pushed to [plug], or `null` if nothing was pushed yet. */
    fun watermark(plug: PlugId): Hlc? = db.plugStateQueries.watermark(plug.id).executeAsOneOrNull()?.let(::hlcFromSql)

    fun setWatermark(plug: PlugId, hlc: Hlc) {
        db.plugStateQueries.setWatermark(plug.id, hlc.toSql())
    }

    override fun close() = driver.close()

    companion object {
        /** For the platform driver, which creates or migrates the database. */
        val Schema: SqlSchema<QueryResult.Value<Unit>> get() = MsuiteDatabase.Schema
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
