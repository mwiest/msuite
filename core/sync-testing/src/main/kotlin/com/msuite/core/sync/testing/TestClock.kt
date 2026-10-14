package com.msuite.core.sync.testing

import kotlin.time.Clock
import kotlin.time.Instant

/** A wall clock the test sets by hand. */
class TestClock(var millis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}
