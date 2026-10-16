package com.msuite.core.sync

import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.Stamp
import com.msuite.core.sync.testing.TestClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

private val record = Uuid.random()
private val dayMs = HlcClock.MAX_AHEAD.inWholeMilliseconds

private fun cell(field: Int, pt: Long) =
    Cell(CellKey(record, FieldId(field)), byteArrayOf(field.toByte()), Stamp(Hlc.of(pt, 0), DeviceId(1)))

class IngestTest : StringSpec({
    "a batch within the bound is merged and moves the clock past it" {
        val clock = HlcClock(TestClock(1_000))
        val batch = Batch(Model.DATA_MODEL_VERSION, listOf(cell(1, 500), cell(2, 5_000)))
        ingest(batch, { null }, clock) shouldBe IngestResult.Merged(batch.cells)
        clock.now() shouldBeGreaterThan Hlc.of(5_000, 0)
    }

    "only winning cells are returned" {
        val clock = HlcClock(TestClock(1_000))
        val local = cell(1, 900)
        ingest(Batch(1, listOf(cell(1, 800), cell(2, 800))), { if (it == local.key) local else null }, clock) shouldBe
            IngestResult.Merged(listOf(cell(2, 800)))
    }

    "a batch more than 24 h ahead is deferred and leaves the clock alone" {
        val clock = HlcClock(TestClock(1_000))
        val batch = Batch(1, listOf(cell(1, 500), cell(2, 1_000 + dayMs + 1)))
        ingest(batch, { null }, clock) shouldBe IngestResult.Deferred
        clock.now() shouldBe Hlc.of(1_000, 0)
    }

    "an empty batch merges nothing" {
        ingest(Batch(1, emptyList()), { null }, HlcClock(TestClock(0))) shouldBe IngestResult.Merged(emptyList())
    }

    "a batch from a newer data-model version is held back and leaves the clock alone" {
        val clock = HlcClock(TestClock(1_000))
        val batch = Batch(Model.DATA_MODEL_VERSION + 1, listOf(cell(1, 500)))
        ingest(batch, { null }, clock) shouldBe IngestResult.TooNew
        clock.now() shouldBe Hlc.of(1_000, 0)
    }

    "decodeOrNull skips unreadable bytes but returns a batch" {
        val batch = Batch(Model.DATA_MODEL_VERSION, listOf(cell(1, 10)))
        decodeOrNull(ProtoBatchEncoder.encode(batch))?.cells shouldContainExactly batch.cells
        decodeOrNull(byteArrayOf(0x08)).shouldBeNull()
    }

    "a delta holds the cells newer than the watermark" {
        val cells = listOf(cell(1, 10), cell(2, 20), cell(3, 30))
        val delta = buildDelta(cells, watermark = Hlc.of(20, 0)).shouldNotBeNull()
        delta.dataModelVersion shouldBe Model.DATA_MODEL_VERSION
        delta.cells shouldContainExactly listOf(cell(3, 30))
        delta.hlcHi() shouldBe Hlc.of(30, 0)
    }

    "without a watermark every cell is in the delta" {
        buildDelta(listOf(cell(1, 10)), watermark = null).shouldNotBeNull().cells shouldContainExactly
            listOf(cell(1, 10))
    }

    "nothing new gives no delta" {
        buildDelta(listOf(cell(1, 10)), watermark = Hlc.of(10, 0)).shouldBeNull()
        Batch(1, emptyList()).hlcHi().shouldBeNull()
    }
})
