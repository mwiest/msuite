package com.msuite.core.sync

import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey

/**
 * Per-cell last-writer-wins (decision 0005): returns the cells of [remote] that win over [local],
 * one per key. Writing them into local state gives the merged state.
 */
fun merge(local: (CellKey) -> Cell?, remote: Iterable<Cell>): List<Cell> {
    val winners = mutableMapOf<CellKey, Cell>()
    for (cell in remote) {
        val current = winners[cell.key]
        if (current == null || cell.beats(current)) winners[cell.key] = cell
    }
    return winners.values.filter { cell -> local(cell.key)?.let { cell.beats(it) } ?: true }
}

/**
 * Higher stamp wins. An equal stamp with a different value only happens if a device reuses an HLC;
 * the higher value wins then, so every device still picks the same cell.
 */
private fun Cell.beats(other: Cell): Boolean {
    val byStamp = stamp.compareTo(other.stamp)
    return byStamp > 0 || (byStamp == 0 && compareUnsigned(value, other.value) > 0)
}

private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val diff = a[i].toUByte().compareTo(b[i].toUByte())
        if (diff != 0) return diff
    }
    return a.size - b.size
}
