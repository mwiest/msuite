package com.msuite.core.sync

import com.msuite.core.model.Hlc
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The device's HLC (decision 0005). Never runs more than [MAX_AHEAD] beyond [wallClock]
 * (decision 0040): ingest checks [accepts] and defers batches that fail it.
 *
 * Not thread-safe; callers serialize access.
 */
class HlcClock(private val wallClock: Clock, initial: Hlc = Hlc.ZERO) {
    private var last: Hlc = initial

    /** A fresh HLC for a local write, above every HLC issued or observed so far. */
    fun now(): Hlc {
        val wall = wallMillis()
        last = if (wall > last.pt) Hlc.of(wall, 0) else last.increment()
        return last
    }

    fun accepts(remote: Hlc): Boolean = remote.pt <= wallMillis() + MAX_AHEAD.inWholeMilliseconds

    /** Takes in a merged remote HLC, so later local writes order after it. */
    fun observe(remote: Hlc) {
        require(accepts(remote)) { "$remote is more than $MAX_AHEAD ahead of the wall clock" }
        if (remote > last) last = remote
    }

    private fun wallMillis(): Long = wallClock.now().toEpochMilliseconds().coerceIn(0, Hlc.MAX_PT)

    companion object {
        val MAX_AHEAD: Duration = 24.hours
    }
}
