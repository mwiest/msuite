package com.msuite.core.model

import kotlin.random.Random

/**
 * Sort keys for manual order (decisions 0004, 0044): base62 fractional indexing as in
 * rocicorp/fractional-indexing, plus a random suffix so concurrent inserts into one gap differ.
 * Keys compare by plain character order; ties fall back to the item ID.
 */
object FractionalIndex {
    private val SMALLEST_INTEGER = "A" + ZERO.toString().repeat(integerLength('A') - 1)

    /** A key strictly between [a] and [b]; `null` means the start or end of the list. */
    fun keyBetween(a: String?, b: String?, random: Random): String = keyBetween(a, b, randomSuffix(random))

    internal fun keyBetween(a: String?, b: String?, suffix: String): String {
        var key = plainKeyBetween(a, b)
        // The plain key may be a prefix of b, so the suffix could push it past b: move closer to b.
        while (b != null && key + suffix >= b) key = plainKeyBetween(key, b)
        return key + suffix
    }

    fun validate(key: String) {
        require(key != SMALLEST_INTEGER) { "invalid key: $key" }
        require(key.all { it in DIGITS }) { "invalid key: $key" }
        val integer = integerPart(key)
        require(!key.substring(integer.length).endsWith(ZERO)) { "invalid key: $key" }
    }

    internal fun plainKeyBetween(a: String?, b: String?): String {
        a?.let(::validate)
        b?.let(::validate)
        require(a == null || b == null || a < b) { "$a is not below $b" }
        return when {
            a == null && b == null -> "a0"
            a == null -> before(b!!)
            b == null -> after(a)
            else -> between(a, b)
        }
    }

    private fun before(b: String): String {
        val ib = integerPart(b)
        val fb = b.substring(ib.length)
        return when {
            ib == SMALLEST_INTEGER -> ib + midpoint("", fb)
            ib < b -> ib
            else -> requireNotNull(decrementInteger(ib)) { "no key below $b" }
        }
    }

    private fun after(a: String): String {
        val ia = integerPart(a)
        return incrementInteger(ia) ?: (ia + midpoint(a.substring(ia.length), null))
    }

    private fun between(a: String, b: String): String {
        val ia = integerPart(a)
        val ib = integerPart(b)
        val fa = a.substring(ia.length)
        if (ia == ib) return ia + midpoint(fa, b.substring(ib.length))
        val next = requireNotNull(incrementInteger(ia)) { "no key above $a" }
        return if (next < b) next else ia + midpoint(fa, null)
    }

    // Two characters; the last is never '0', so the key keeps no trailing zero.
    private fun randomSuffix(random: Random): String =
        "${DIGITS[random.nextInt(DIGITS.length)]}${DIGITS[random.nextInt(1, DIGITS.length)]}"
}

private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
private const val ZERO = '0'

/** A fraction between [a] and [b] (`null` = 1), both without trailing zeros. */
private fun midpoint(a: String, b: String?): String {
    if (b != null) {
        var n = 0
        while ((a.getOrNull(n) ?: ZERO) == b[n]) n++
        if (n > 0) return b.substring(0, n) + midpoint(a.drop(n), b.substring(n))
    }
    val digitA = if (a.isEmpty()) 0 else DIGITS.indexOf(a[0])
    val digitB = if (b != null) DIGITS.indexOf(b[0]) else DIGITS.length
    return when {
        digitB - digitA > 1 -> DIGITS[(digitA + digitB + 1) / 2].toString()
        b != null && b.length > 1 -> b.substring(0, 1)
        else -> DIGITS[digitA] + midpoint(a.drop(1), null)
    }
}

private fun integerLength(head: Char): Int = when (head) {
    in 'a'..'z' -> head - 'a' + 2
    in 'A'..'Z' -> 'Z' - head + 2
    else -> throw IllegalArgumentException("invalid key head: $head")
}

private fun integerPart(key: String): String {
    require(key.isNotEmpty()) { "empty key" }
    val length = integerLength(key[0])
    require(length <= key.length) { "invalid key: $key" }
    return key.substring(0, length)
}

private fun incrementInteger(x: String): String? {
    val head = x[0]
    val digits = x.substring(1).toCharArray()
    for (i in digits.indices.reversed()) {
        val d = DIGITS.indexOf(digits[i]) + 1
        if (d < DIGITS.length) {
            digits[i] = DIGITS[d]
            return head + digits.concatToString()
        }
        digits[i] = ZERO
    }
    return when (head) {
        'Z' -> "a0"

        'z' -> null

        else -> {
            val next = head + 1
            val body = digits.concatToString()
            next + if (next > 'a') body + ZERO else body.dropLast(1)
        }
    }
}

private fun decrementInteger(x: String): String? {
    val head = x[0]
    val digits = x.substring(1).toCharArray()
    for (i in digits.indices.reversed()) {
        val d = DIGITS.indexOf(digits[i]) - 1
        if (d >= 0) {
            digits[i] = DIGITS[d]
            return head + digits.concatToString()
        }
        digits[i] = DIGITS.last()
    }
    return when (head) {
        'a' -> "Z" + DIGITS.last()

        'A' -> null

        else -> {
            val previous = head - 1
            val body = digits.concatToString()
            previous + if (previous < 'Z') body + DIGITS.last() else body.dropLast(1)
        }
    }
}
