package com.msuite.core.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

class ValueCodecTest : StringSpec({
    "values survive encoding and decoding, defaults included" {
        checkAll(Arb.string()) { ValueCodec.decode(ValueCodec.encode(Value.Text(it))) shouldBe Value.Text(it) }
        checkAll(Arb.boolean()) { ValueCodec.decode(ValueCodec.encode(Value.Bool(it))) shouldBe Value.Bool(it) }
        checkAll(Arb.long()) { ValueCodec.decode(ValueCodec.encode(Value.Int64(it))) shouldBe Value.Int64(it) }
    }

    "the bytes are pinned and carry the type" {
        ValueCodec.encode(Value.Text("hi")).hex() shouldBe "0a026869"
        ValueCodec.encode(Value.Text("")).hex() shouldBe "0a00"
        ValueCodec.encode(Value.Bool(false)).hex() shouldBe "1000"
        ValueCodec.encode(Value.Int64(-1)).hex() shouldBe "1801" // zigzag
    }

    "a value of a type this version doesn't know decodes to null" {
        ValueCodec.decode(byteArrayOf(0x78, 0x01)).shouldBeNull() // field 15
        ValueCodec.decode(byteArrayOf()).shouldBeNull()
    }
})
