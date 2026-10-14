package com.msuite.core.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

class ValueCodecTest : StringSpec({
    "values survive encoding and decoding, defaults included" {
        checkAll(Arb.string()) { ValueCodec.decode(ValueCodec.encode(Value.Text(it))) shouldBe Value.Text(it) }
        checkAll(Arb.boolean()) { ValueCodec.decode(ValueCodec.encode(Value.Bool(it))) shouldBe Value.Bool(it) }
        checkAll(Arb.long()) { ValueCodec.decode(ValueCodec.encode(Value.Int64(it))) shouldBe Value.Int64(it) }
        checkAll(Arb.long(-4_000_000L..4_000_000L).map(LocalDate::fromEpochDays)) {
            ValueCodec.decode(ValueCodec.encode(Value.Date(it))) shouldBe Value.Date(it)
        }
        repeat(100) {
            val id = Uuid.random()
            ValueCodec.decode(ValueCodec.encode(Value.Ref(id))) shouldBe Value.Ref(id)
        }
    }

    "the bytes are pinned and carry the type" {
        ValueCodec.encode(Value.Text("hi")).hex() shouldBe "0a026869"
        ValueCodec.encode(Value.Text("")).hex() shouldBe "0a00"
        ValueCodec.encode(Value.Bool(false)).hex() shouldBe "1000"
        ValueCodec.encode(Value.Int64(-1)).hex() shouldBe "1801" // zigzag
        ValueCodec.encode(Value.Date(LocalDate(2026, 10, 14))).hex() shouldBe "2088c402" // 20740 days, zigzag
        ValueCodec.encode(Value.Date(LocalDate(1969, 12, 31))).hex() shouldBe "2001"
        ValueCodec.encode(Value.Ref(Uuid.parse("00112233-4455-6677-8899-aabbccddeeff"))).hex() shouldBe
            "2a10" + "00112233445566778899aabbccddeeff"
    }

    "a value of a type this version doesn't know decodes to null" {
        ValueCodec.decode(byteArrayOf(0x78, 0x01)).shouldBeNull() // field 15
        ValueCodec.decode(byteArrayOf()).shouldBeNull()
    }

    "a date outside the range LocalDate can hold decodes to null" {
        val maxDays = byteArrayOf(0x20, -2, -1, -1, -1, -1, -1, -1, -1, -1, 0x01) // Long.MAX_VALUE, zigzag
        ValueCodec.decode(maxDays).shouldBeNull()
    }

    "a reference that isn't 16 bytes decodes to null" {
        ValueCodec.decode(byteArrayOf(0x2a, 0x02, 0x01, 0x02)).shouldBeNull()
    }
})
