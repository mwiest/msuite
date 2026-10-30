package com.msuite.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.PlugId
import com.msuite.core.model.Stamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.util.Properties
import kotlin.uuid.Uuid

private val record = Uuid.random()

private fun cell(field: Int, hlc: Hlc, value: Byte = field.toByte(), device: Long = 1) =
    Cell(CellKey(record, FieldId(field)), byteArrayOf(value), Stamp(hlc, DeviceId(device)))

private fun hlc(pt: Long) = Hlc.of(pt, 0)

private fun open(url: String = JdbcSqliteDriver.IN_MEMORY) =
    LocalStore(JdbcSqliteDriver(url, Properties(), LocalStore.Schema))

/** Above 2^47 ms the packed HLC has its sign bit set, so a signed compare would put it below [hlc]. */
private val signBitHlc = Hlc.of(1L shl 47, 0)

private val folder = PlugId("folder")
private val relay = PlugId("relay")

/** The schema before the unreadable table (migration 1): pinned so the migration stays honest. */
private val V1_SCHEMA = listOf(
    """
    CREATE TABLE field_state (
        record_id BLOB NOT NULL,
        field_id INTEGER NOT NULL,
        value BLOB NOT NULL,
        hlc INTEGER NOT NULL,
        device_id INTEGER NOT NULL,
        PRIMARY KEY (record_id, field_id)
    ) WITHOUT ROWID
    """.trimIndent(),
    "CREATE INDEX field_state_hlc ON field_state (hlc)",
    """
    CREATE TABLE device (
        singleton INTEGER NOT NULL PRIMARY KEY CHECK (singleton = 0),
        device_id INTEGER NOT NULL
    )
    """.trimIndent(),
    """
    CREATE TABLE ingested_batch (
        plug TEXT NOT NULL,
        batch_key TEXT NOT NULL,
        PRIMARY KEY (plug, batch_key)
    ) WITHOUT ROWID
    """.trimIndent(),
    """
    CREATE TABLE plug_watermark (
        plug TEXT NOT NULL PRIMARY KEY,
        hlc INTEGER NOT NULL
    )
    """.trimIndent(),
)

class LocalStoreTest : StringSpec({
    "a written cell reads back identical" {
        val store = open()
        val written = cell(1, Hlc.of(5, 3), device = -7)
        store.write(listOf(written))
        store.cell(written.key) shouldBe written
        store.cell(CellKey(record, FieldId(2))).shouldBeNull()
    }

    "a record's cells read back alone" {
        val store = open()
        val other = Cell(CellKey(Uuid.random(), FieldId(1)), byteArrayOf(1), Stamp(hlc(1), DeviceId(1)))
        store.write(listOf(cell(1, hlc(1)), cell(2, hlc(2)), other))
        store.cells(record) shouldContainExactlyInAnyOrder listOf(cell(1, hlc(1)), cell(2, hlc(2)))
        store.cells(Uuid.random()).shouldBeEmpty()
    }

    "writing a key again replaces its cell" {
        val store = open()
        store.write(listOf(cell(1, hlc(1), value = 1)))
        store.write(listOf(cell(1, hlc(2), value = 2)))
        store.cells() shouldContainExactly listOf(cell(1, hlc(2), value = 2))
    }

    "any cell round-trips" {
        val store = open()
        checkAll(
            Arb.list(Arb.int(), 1..5),
            Arb.byteArray(Arb.int(0..32), Arb.byte()),
            Arb.long(),
            Arb.long(),
        ) { fields, value, packed, device ->
            val cells = fields.distinct().map {
                Cell(CellKey(Uuid.random(), FieldId(it)), value, Stamp(Hlc.fromPacked(packed), DeviceId(device)))
            }
            store.write(cells)
            cells.forEach { store.cell(it.key) shouldBe it }
        }
    }

    "cells above a watermark compare HLCs unsigned" {
        val store = open()
        val low = cell(1, hlc(10))
        val mid = cell(2, hlc(20))
        val high = cell(3, signBitHlc)
        store.write(listOf(low, mid, high))
        store.cells(above = hlc(10)) shouldContainExactlyInAnyOrder listOf(mid, high)
        store.cells(above = hlc(20)) shouldContainExactly listOf(high)
        store.cells(above = signBitHlc).shouldBeEmpty()
        store.cells() shouldContainExactlyInAnyOrder listOf(low, mid, high)
    }

    "the highest HLC is unsigned, or null when empty" {
        val store = open()
        store.maxHlc().shouldBeNull()
        store.write(listOf(cell(1, signBitHlc), cell(2, hlc(20))))
        store.maxHlc() shouldBe signBitHlc
    }

    "ingested batch keys are kept per plug" {
        val store = open()
        store.plugs.isIngested(folder, "k1") shouldBe false
        store.plugs.markIngested(folder, "k1")
        store.plugs.markIngested(folder, "k1")
        store.plugs.isIngested(folder, "k1") shouldBe true
        store.plugs.isIngested(relay, "k1") shouldBe false
    }

    "unreadable keys count attempts per plug and can be flagged corrupt" {
        val store = open()
        store.plugs.recordUnreadable(folder, "k1") shouldBe 1
        store.plugs.recordUnreadable(folder, "k1") shouldBe 2
        store.plugs.recordUnreadable(relay, "k1") shouldBe 1
        store.plugs.isCorrupt(folder, "k1") shouldBe false
        store.plugs.markCorrupt(folder, "k1")
        store.plugs.isCorrupt(folder, "k1") shouldBe true
        store.plugs.isCorrupt(relay, "k1") shouldBe false
    }

    "a version 1 database gains the unreadable table when migrated" {
        val url = "jdbc:sqlite:${tempfile(suffix = ".db").absolutePath}"
        val v1 = JdbcSqliteDriver(url, Properties())
        V1_SCHEMA.forEach { v1.execute(null, it, 0) }
        v1.execute(null, "INSERT INTO ingested_batch VALUES ('folder', 'k1')", 0)
        v1.execute(null, "INSERT INTO field_state VALUES (x'000102030405060708090a0b0c0d0e0f', 1, x'01', 2, 3)", 0)
        LocalStore.Schema.migrate(v1, 1, 2).value
        v1.close()
        // Reopen without the schema: the driver above would create it again on top of the migration.
        LocalStore(JdbcSqliteDriver(url, Properties())).use {
            it.plugs.isIngested(folder, "k1") shouldBe true
            it.plugs.recordUnreadable(folder, "k2") shouldBe 1
            it.cells() shouldHaveSize 1
        }
    }

    "a watermark is kept per plug and can move" {
        val store = open()
        store.plugs.watermark(folder).shouldBeNull()
        store.plugs.setWatermark(folder, hlc(5))
        store.plugs.setWatermark(folder, signBitHlc)
        store.plugs.watermark(folder) shouldBe signBitHlc
        store.plugs.watermark(relay).shouldBeNull()
    }

    "pruning removes bookkeeping for keys no longer in the backend" {
        val store = open()
        store.plugs.markIngested(folder, "k1")
        store.plugs.markIngested(folder, "k2")
        store.plugs.markIngested(folder, "k3")
        store.plugs.recordUnreadable(folder, "u1")
        store.plugs.recordUnreadable(folder, "u2")

        // Only k2 and u1 are still in the backend
        val pruned = store.plugs.prune(folder, setOf("k2", "u1"))

        pruned shouldBe 3 // k1, k3, u2 were pruned
        store.plugs.isIngested(folder, "k1") shouldBe false
        store.plugs.isIngested(folder, "k2") shouldBe true
        store.plugs.isIngested(folder, "k3") shouldBe false
        store.plugs.isCorrupt(folder, "u1") shouldBe false // u1 exists but not corrupt yet
        store.plugs.recordUnreadable(folder, "u1") shouldBe 2 // u1 still has attempts
        // u2 was pruned, so starting fresh
        store.plugs.recordUnreadable(folder, "u2") shouldBe 1
    }

    "pruning on an empty backend removes all bookkeeping" {
        val store = open()
        store.plugs.markIngested(folder, "k1")
        store.plugs.markIngested(relay, "k2")
        store.plugs.recordUnreadable(folder, "u1")

        store.plugs.prune(folder, emptySet()) shouldBe 2 // k1 and u1
        store.plugs.isIngested(folder, "k1") shouldBe false
        store.plugs.isIngested(relay, "k2") shouldBe true // relay unaffected
    }

    "a failed transaction leaves nothing behind" {
        val store = open()
        shouldThrow<IllegalStateException> {
            store.transaction {
                store.write(listOf(cell(1, hlc(1))))
                store.plugs.markIngested(folder, "k1")
                error("boom")
            }
        }
        store.cells().shouldBeEmpty()
        store.plugs.isIngested(folder, "k1") shouldBe false
    }

    "the device ID is created once and kept across reopening" {
        val url = "jdbc:sqlite:${tempfile(suffix = ".db").absolutePath}"
        open(url).use { it.deviceId { DeviceId(-3) } } shouldBe DeviceId(-3)
        open(url).use { it.deviceId { DeviceId(4) } } shouldBe DeviceId(-3)
    }

    "state persists across reopening the database" {
        val file = tempfile(suffix = ".db")
        val url = "jdbc:sqlite:${file.absolutePath}"
        val written = cell(1, signBitHlc)
        open(url).run {
            write(listOf(written))
            plugs.markIngested(folder, "k1")
            plugs.setWatermark(folder, hlc(7))
            close()
        }
        open(url).run {
            cells() shouldContainExactly listOf(written)
            plugs.isIngested(folder, "k1") shouldBe true
            plugs.watermark(folder) shouldBe hlc(7)
            close()
        }
    }

    "an unconfirmed publish keeps its previous watermark and counts misses until confirmed" {
        val store = open()
        store.unconfirmed.record(folder, "k1", previous = null)
        store.unconfirmed.record(folder, "k2", previous = hlc(5))
        store.unconfirmed.missed(folder, "k2")

        store.unconfirmed.all(folder) shouldContainExactlyInAnyOrder listOf(
            UnconfirmedPublish("k1", previous = null, misses = 0),
            UnconfirmedPublish("k2", previous = hlc(5), misses = 1),
        )
        store.unconfirmed.all(relay).shouldBeEmpty()

        store.unconfirmed.confirmed(folder, "k1")
        store.unconfirmed.all(folder).map { it.key } shouldContainExactly listOf("k2")
    }

    "a watermark can be cleared" {
        val store = open()
        store.plugs.setWatermark(folder, hlc(5))
        store.plugs.clearWatermark(folder)
        store.plugs.watermark(folder).shouldBeNull()
    }

    "a version 1 database gains the unconfirmed table when migrated to the latest version" {
        val url = "jdbc:sqlite:${tempfile(suffix = ".db").absolutePath}"
        val v1 = JdbcSqliteDriver(url, Properties())
        V1_SCHEMA.forEach { v1.execute(null, it, 0) }
        LocalStore.Schema.migrate(v1, 1, LocalStore.Schema.version).value
        v1.close()
        LocalStore(JdbcSqliteDriver(url, Properties())).use {
            it.unconfirmed.record(folder, "k1", previous = hlc(5))
            it.unconfirmed.all(folder) shouldContainExactly listOf(UnconfirmedPublish("k1", hlc(5), 0))
        }
    }
})
