package com.msuite.core.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.random.Random

class DeviceIdTest : StringSpec({
    "order is unsigned, so it matches the order of the base32hex key field" {
        checkAll(Arb.long(), Arb.long()) { a, b ->
            DeviceId(a).compareTo(DeviceId(b)).coerceIn(-1, 1) shouldBe
                a.toULong().compareTo(b.toULong()).coerceIn(-1, 1)
        }
        DeviceId(1) shouldBeLessThan DeviceId(-1)
    }

    "random draws from the given source" {
        DeviceId.random(Random(42)) shouldBe DeviceId.random(Random(42))
    }
})
