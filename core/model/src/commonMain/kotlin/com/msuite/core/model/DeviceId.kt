package com.msuite.core.model

import kotlin.jvm.JvmInline
import kotlin.random.Random

/** A device's random 64-bit ID (decision 0043): the single writer of its batches and the HLC tie-break. */
@JvmInline
value class DeviceId(val bits: Long) : Comparable<DeviceId> {
    // Unsigned, to match the order of the base32hex key field (decision 0029).
    override fun compareTo(other: DeviceId): Int = bits.toULong().compareTo(other.bits.toULong())

    companion object {
        fun random(random: Random = Random.Default): DeviceId = DeviceId(random.nextLong())
    }
}
