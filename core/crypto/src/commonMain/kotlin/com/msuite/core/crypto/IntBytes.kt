package com.msuite.core.crypto

/** Bytes in the big-endian integers the wire formats carry (decisions 0070, 0072). */
internal const val INT_BYTES = 4

internal const val BYTE_MASK = 0xFF

private const val BITS_PER_BYTE = 8

/** [this] as [INT_BYTES] bytes, most significant first. */
internal fun Int.toBigEndian(): ByteArray =
    ByteArray(INT_BYTES) { (this ushr ((INT_BYTES - 1 - it) * BITS_PER_BYTE)).toByte() }

/** The big-endian int at [offset]. */
internal fun ByteArray.intAt(offset: Int): Int {
    var value = 0
    repeat(INT_BYTES) { value = (value shl BITS_PER_BYTE) or (this[offset + it].toInt() and BYTE_MASK) }
    return value
}
