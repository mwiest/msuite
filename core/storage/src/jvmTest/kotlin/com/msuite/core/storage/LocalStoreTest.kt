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

class LocalStoreTest : StringSpec({
    "a written cell reads back identical" {
        val store = open()
        val written = cell(1, Hlc.of(5, 3), device = -7)
        store.write(listOf(written))
        store.cell(written.key) shouldBe written
        store.cell(CellKey(record, FieldId(2))).shouldBeNull()
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
        store.isIngested(folder, "k1") shouldBe false
        store.markIngested(folder, "k1")
        store.markIngested(folder, "k1")
        store.isIngested(folder, "k1") shouldBe true
        store.isIngested(relay, "k1") shouldBe false
    }

    "a watermark is kept per plug and can move" {
        val store = open()
        store.watermark(folder).shouldBeNull()
        store.setWatermark(folder, hlc(5))
        store.setWatermark(folder, signBitHlc)
        store.watermark(folder) shouldBe signBitHlc
        store.watermark(relay).shouldBeNull()
    }

    "a failed transaction leaves nothing behind" {
        val store = open()
        shouldThrow<IllegalStateException> {
            store.transaction {
                store.write(listOf(cell(1, hlc(1))))
                store.markIngested(folder, "k1")
                error("boom")
            }
        }
        store.cells().shouldBeEmpty()
        store.isIngested(folder, "k1") shouldBe false
    }

    "state persists across reopening the database" {
        val file = tempfile(suffix = ".db")
        val url = "jdbc:sqlite:${file.absolutePath}"
        val written = cell(1, signBitHlc)
        open(url).run {
            write(listOf(written))
            markIngested(folder, "k1")
            setWatermark(folder, hlc(7))
            close()
        }
        open(url).run {
            cells() shouldContainExactly listOf(written)
            isIngested(folder, "k1") shouldBe true
            watermark(folder) shouldBe hlc(7)
            close()
        }
    }
})
