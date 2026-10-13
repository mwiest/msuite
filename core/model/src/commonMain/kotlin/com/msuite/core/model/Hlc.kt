package com.msuite.core.model

import kotlin.jvm.JvmInline

/**
 * Hybrid Logical Clock timestamp: 48-bit millisecond [pt], 16-bit counter [c], packed into 64 bits
 * (decisions 0005, 0029). Ordered by `(pt, c)`; ties between devices are broken by [DeviceId].
 */
@JvmInline
value class Hlc private constructor(val packed: Long) : Comparable<Hlc> {
    val pt: Long get() = packed ushr C_BITS
    val c: Int get() = (packed and MAX_C.toLong()).toInt()

    // Unsigned: pt may use all 48 bits, so the packed sign bit is part of the value.
    override fun compareTo(other: Hlc): Int = packed.toULong().compareTo(other.packed.toULong())

    /** The next HLC; a full counter carries into [pt] (decision 0043). */
    fun increment(): Hlc {
        require(packed != MAX.packed) { "HLC overflow" }
        return Hlc(packed + 1)
    }

    override fun toString(): String = "Hlc($pt, $c)"

    companion object {
        private const val C_BITS = 16
        const val MAX_PT: Long = (1L shl 48) - 1
        const val MAX_C: Int = 0xFFFF

        val ZERO = Hlc(0)
        private val MAX = of(MAX_PT, MAX_C)

        fun of(pt: Long, c: Int): Hlc {
            require(pt in 0..MAX_PT) { "pt out of range: $pt" }
            require(c in 0..MAX_C) { "c out of range: $c" }
            return Hlc((pt shl C_BITS) or c.toLong())
        }

        fun fromPacked(packed: Long): Hlc = Hlc(packed)
    }
}
