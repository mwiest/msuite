package com.msuite.core.sync

import com.msuite.core.model.Hlc
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.time.Clock
import kotlin.time.Instant

private class FakeClock(var millis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

private val MAX_AHEAD_MS = HlcClock.MAX_AHEAD.inWholeMilliseconds

class HlcClockTest : StringSpec({
    "now follows the wall clock" {
        val clock = HlcClock(FakeClock(1_000))
        clock.now() shouldBe Hlc.of(1_000, 0)
    }

    "now is strictly monotonic and never behind the wall clock, even when the wall clock jumps back" {
        checkAll(Arb.list(Arb.long(0L..10_000L), 1..50)) { walls ->
            val wall = FakeClock(0)
            val clock = HlcClock(wall)
            var previous: Hlc? = null
            for (w in walls) {
                wall.millis = w
                val hlc = clock.now()
                hlc.pt shouldBeGreaterThanOrEqualTo w
                previous?.let { hlc shouldBeGreaterThan it }
                previous = hlc
            }
        }
    }

    "now after observing a remote HLC is above it" {
        checkAll(Arb.long(0L..10_000L), Arb.long(0L..10_000L)) { local, remotePt ->
            val clock = HlcClock(FakeClock(local))
            val remote = Hlc.of(remotePt, 3)
            clock.observe(remote)
            clock.now() shouldBeGreaterThan remote
        }
    }

    "observing an older HLC doesn't move the clock back" {
        val clock = HlcClock(FakeClock(5_000))
        val first = clock.now()
        clock.observe(Hlc.of(1_000, 0))
        clock.now() shouldBeGreaterThan first
    }

    "a full counter carries into pt" {
        val clock = HlcClock(FakeClock(1_000), initial = Hlc.of(1_000, Hlc.MAX_C - 1))
        clock.now() shouldBe Hlc.of(1_000, Hlc.MAX_C)
        clock.now() shouldBe Hlc.of(1_001, 0)
    }

    "accepts HLCs up to 24 h ahead of the wall clock, and no further" {
        val clock = HlcClock(FakeClock(1_000))
        clock.accepts(Hlc.of(1_000 + MAX_AHEAD_MS, Hlc.MAX_C)).shouldBeTrue()
        clock.accepts(Hlc.of(1_000 + MAX_AHEAD_MS + 1, 0)).shouldBeFalse()
    }

    "observe rejects an HLC beyond the bound and leaves the clock unchanged" {
        val clock = HlcClock(FakeClock(1_000))
        shouldThrow<IllegalArgumentException> { clock.observe(Hlc.of(1_000 + MAX_AHEAD_MS + 1, 0)) }
        clock.now() shouldBe Hlc.of(1_000, 0)
    }

    "an HLC beyond the bound is accepted once the wall clock catches up" {
        val wall = FakeClock(0)
        val clock = HlcClock(wall)
        val future = Hlc.of(MAX_AHEAD_MS + 10, 0)
        clock.accepts(future).shouldBeFalse()
        wall.millis = 10
        clock.accepts(future).shouldBeTrue()
    }
})
