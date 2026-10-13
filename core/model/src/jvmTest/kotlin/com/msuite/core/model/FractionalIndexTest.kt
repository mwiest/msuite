package com.msuite.core.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.random.Random

private data class Between(val a: String?, val b: String?, val expected: String)

// Outputs of the reference implementation (rocicorp/fractional-indexing) without the suffix.
private val referenceCases = listOf(
    Between(null, null, "a0"),
    Between(null, "a0", "Zz"),
    Between(null, "Zz", "Zy"),
    Between("a0", null, "a1"),
    Between("a1", null, "a2"),
    Between("a0", "a1", "a0V"),
    Between("a1", "a2", "a1V"),
    Between("a0V", "a1", "a0l"),
    Between("Zz", "a0", "ZzV"),
    Between("Zz", "a1", "a0"),
    Between(null, "Y00", "Xzzz"),
    Between("bzz", null, "c000"),
    Between("a0", "a0V", "a0G"),
    Between("a0", "a0G", "a08"),
    Between("b125", "b129", "b127"),
    Between("a0", "a1V", "a1"),
    Between("Zz", "a01", "a0"),
    Between(null, "a0V", "a0"),
    Between(null, "b999", "b99"),
)

private fun isValid(key: String): Boolean = runCatching { FractionalIndex.validate(key) }.isSuccess

class FractionalIndexTest : StringSpec({
    withData(nameFn = {
        "plain key between ${it.a} and ${it.b} is ${it.expected}"
    }, referenceCases) { (a, b, expected) ->
        FractionalIndex.plainKeyBetween(a, b) shouldBe expected
    }

    "invalid or unordered bounds are rejected" {
        shouldThrow<IllegalArgumentException> { FractionalIndex.keyBetween("a1", "a0", Random(0)) }
        shouldThrow<IllegalArgumentException> { FractionalIndex.keyBetween("a0", "a0", Random(0)) }
        shouldThrow<IllegalArgumentException> { FractionalIndex.keyBetween("a10", null, Random(0)) }
        shouldThrow<IllegalArgumentException> { FractionalIndex.keyBetween("a", null, Random(0)) }
        shouldThrow<IllegalArgumentException> { FractionalIndex.keyBetween(null, "A" + "0".repeat(26), Random(0)) }
        shouldThrow<IllegalArgumentException> { FractionalIndex.keyBetween("a0!", null, Random(0)) }
    }

    "a suffix that would overshoot a bound with the key as prefix still lands inside the gap" {
        // The plain key "a1" is a prefix of "a1V"; "a1" + "zz" would sort after it.
        val key = FractionalIndex.keyBetween("a0", "a1V", suffix = "zz")
        key shouldBeGreaterThan "a0"
        key shouldBeLessThan "a1V"
    }

    "keys from repeated random inserts stay valid, distinct and in order" {
        checkAll(Arb.long(), Arb.list(Arb.int(0..1000), 1..60)) { seed, slots ->
            val random = Random(seed)
            val keys = mutableListOf<String>()
            for (slot in slots) {
                val i = slot % (keys.size + 1)
                val key = FractionalIndex.keyBetween(keys.getOrNull(i - 1), keys.getOrNull(i), random)
                isValid(key) shouldBe true
                keys.add(i, key)
            }
            keys.zipWithNext().forEach { (lo, hi) -> lo shouldBeLessThan hi }
        }
    }

    "appending and prepending stay outside the existing keys" {
        checkAll(Arb.long(), Arb.int(1..100)) { seed, n ->
            val random = Random(seed)
            var first = FractionalIndex.keyBetween(null, null, random)
            var last = first
            repeat(n) {
                val before = FractionalIndex.keyBetween(null, first, random)
                val after = FractionalIndex.keyBetween(last, null, random)
                before shouldBeLessThan first
                after shouldBeGreaterThan last
                first = before
                last = after
            }
        }
    }

    "concurrent inserts into the same gap stay inside it and differ when their suffixes differ" {
        checkAll(Arb.long(), Arb.list(Arb.int(0..1000), 2..20)) { seed, slots ->
            val random = Random(seed)
            val keys = mutableListOf<String>()
            for (slot in slots) {
                val i = slot % (keys.size + 1)
                keys.add(i, FractionalIndex.keyBetween(keys.getOrNull(i - 1), keys.getOrNull(i), random))
            }
            val gap = random.nextInt(keys.size + 1)
            val lo = keys.getOrNull(gap - 1)
            val hi = keys.getOrNull(gap)
            val one = FractionalIndex.keyBetween(lo, hi, suffix = "1x")
            val two = FractionalIndex.keyBetween(lo, hi, suffix = "1y")
            one shouldNotBe two
            for (key in listOf(one, two)) {
                lo?.let { key shouldBeGreaterThan it }
                hi?.let { key shouldBeLessThan it }
            }
        }
    }

    "the suffix comes from the given random source" {
        FractionalIndex.keyBetween("a0", "a1", Random(7)) shouldBe FractionalIndex.keyBetween("a0", "a1", Random(7))
    }
})
