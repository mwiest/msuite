package com.msuite.core.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.uuid.Uuid

class CellTest : StringSpec({
    val key = CellKey(Uuid.random(), FieldId(1))
    val stamp = Stamp(Hlc.of(1, 0), DeviceId(1))

    "cells compare their value by content" {
        Cell(key, byteArrayOf(1, 2), stamp) shouldBe Cell(key, byteArrayOf(1, 2), stamp)
        Cell(key, byteArrayOf(1, 2), stamp).hashCode() shouldBe Cell(key, byteArrayOf(1, 2), stamp).hashCode()
        Cell(key, byteArrayOf(1, 2), stamp) shouldNotBe Cell(key, byteArrayOf(1, 3), stamp)
    }

    "stamps order by HLC, then by device ID" {
        (Stamp(Hlc.of(1, 0), DeviceId(-1)) < Stamp(Hlc.of(1, 1), DeviceId(0))) shouldBe true
        (Stamp(Hlc.of(1, 0), DeviceId(1)) < Stamp(Hlc.of(1, 0), DeviceId(-1))) shouldBe true
    }
})
