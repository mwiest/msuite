package com.msuite.core.sync

import com.msuite.core.model.Cell

/** The LWW winner, computed independently of [merge]: higher stamp, then higher value bytes. */
val LWW_ORDER: Comparator<Cell> = compareBy<Cell> {
    it.stamp
}.thenComparator { a, b -> compareUnsigned(a.value, b.value) }

private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val diff = a[i].toUByte().compareTo(b[i].toUByte())
        if (diff != 0) return diff
    }
    return a.size - b.size
}
