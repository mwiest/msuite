package com.msuite.core.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

private val arbPt = Arb.long(0L..Hlc.MAX_PT)
private val arbC = Arb.int(0..Hlc.MAX_C)

class HlcTest : StringSpec({
    "pt and c round-trip through the packed form" {
        checkAll(arbPt, arbC) { pt, c ->
            val hlc = Hlc.of(pt, c)
            hlc.pt shouldBe pt
            hlc.c shouldBe c
            Hlc.fromPacked(hlc.packed) shouldBe hlc
        }
    }

    "order is lexicographic on (pt, c), including pts above 2^47" {
        checkAll(arbPt, arbC, arbPt, arbC) { pt1, c1, pt2, c2 ->
            val expected = compareValuesBy(pt1 to c1, pt2 to c2, { it.first }, { it.second })
            Hlc.of(pt1, c1).compareTo(Hlc.of(pt2, c2)).coerceIn(-1, 1) shouldBe expected.coerceIn(-1, 1)
        }
    }

    "increment bumps the counter" {
        Hlc.of(5, 7).increment() shouldBe Hlc.of(5, 8)
    }

    "increment carries a full counter into pt" {
        Hlc.of(5, Hlc.MAX_C).increment() shouldBe Hlc.of(6, 0)
    }

    "values out of range are rejected" {
        shouldThrow<IllegalArgumentException> { Hlc.of(-1, 0) }
        shouldThrow<IllegalArgumentException> { Hlc.of(Hlc.MAX_PT + 1, 0) }
        shouldThrow<IllegalArgumentException> { Hlc.of(0, -1) }
        shouldThrow<IllegalArgumentException> { Hlc.of(0, Hlc.MAX_C + 1) }
        shouldThrow<IllegalArgumentException> { Hlc.of(Hlc.MAX_PT, Hlc.MAX_C).increment() }
    }
})
