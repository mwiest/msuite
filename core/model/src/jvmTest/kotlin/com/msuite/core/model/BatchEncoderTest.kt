package com.msuite.core.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.uuid.Uuid

private val arbCell: Arb<Cell> = arbitrary {
    Cell(
        key = CellKey(Uuid.fromLongs(Arb.long().bind(), Arb.long().bind()), FieldId(Arb.int(0..Int.MAX_VALUE).bind())),
        value = Arb.byteArray(Arb.int(0..8), Arb.byte()).bind(),
        stamp = Stamp(Hlc.fromPacked(Arb.long().bind()), DeviceId(Arb.long().bind())),
    )
}
private val arbBatch: Arb<Batch> = arbitrary {
    Batch(Arb.int(1..5).bind(), Arb.list(arbCell, 0..10).bind().distinctBy { it.key })
}

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

private val encoder: BatchEncoder = ProtoBatchEncoder

class BatchEncoderTest : StringSpec({
    "a batch survives encoding and decoding" {
        checkAll(arbBatch) { batch ->
            val decoded = encoder.decode(encoder.encode(batch))
            decoded.dataModelVersion shouldBe batch.dataModelVersion
            decoded.cells shouldBe batch.cells.sortedWith(Batch.CELL_ORDER)
        }
    }

    "the cell order doesn't change the bytes" {
        checkAll(arbBatch) { batch ->
            encoder.encode(Batch(batch.dataModelVersion, batch.cells.reversed())) shouldBe encoder.encode(batch)
        }
    }

    "the bytes are pinned" {
        val cell = Cell(
            key = CellKey(Uuid.fromLongs(0x0102030405060708, 0x090A0B0C0D0E0F10), FieldId(3)),
            value = byteArrayOf(0x2A),
            stamp = Stamp(Hlc.of(1, 2), DeviceId(-1)),
        )
        encoder.encode(Batch(1, listOf(cell))).hex() shouldBe
            "0801" + // dataModelVersion = 1
            "1229" + // cell, 41 bytes
            "0a100102030405060708090a0b0c0d0e0f10" + // recordId
            "1003" + // fieldId = 3
            "1a012a" + // value
            "210200010000000000" + // hlc, fixed64 little-endian
            "29ffffffffffffffff" // deviceId, fixed64
    }

    "two cells for one key are rejected" {
        val cell = Cell(CellKey(Uuid.random(), FieldId(1)), byteArrayOf(), Stamp(Hlc.ZERO, DeviceId(0)))
        shouldThrow<IllegalArgumentException> { Batch(1, listOf(cell, cell)) }
    }

    "fields added by a newer app version are skipped" {
        checkAll(arbBatch) { batch ->
            val newer = encoder.encode(batch) + byteArrayOf(0x78, 0x01) // field 15, varint 1
            encoder.decode(newer).cells shouldBe batch.cells.sortedWith(Batch.CELL_ORDER)
        }
    }

    "bytes that are not a batch are rejected" {
        shouldThrow<MalformedBatch> { encoder.decode(byteArrayOf(0x08)) }
    }
})
