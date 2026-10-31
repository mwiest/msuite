package com.msuite.core.sync

/**
 * Whether [key] is a delta whose `hlcLo`..`hlcHi` span lies inside a snapshot from the same writer
 * that the reader already ingested (decision 0002; the key grammar of 0029). Such an object adds
 * nothing to the reader's state, so a pull can skip fetching it. A key that isn't a well-formed
 * object key (a sync client's copy, a Layer 1 test key) is never covered.
 */
fun isCoveredBySnapshot(key: String, ingested: Set<String>): Boolean = parseOrNull(key)
    ?.takeIf { it.prefix.type == BatchType.Delta }
    ?.let { delta ->
        ingested.any { ingestedKey ->
            val snapshot = parseOrNull(ingestedKey) ?: return@any false
            snapshot.prefix.type == BatchType.Snapshot &&
                snapshot.prefix.deviceId == delta.prefix.deviceId &&
                snapshot.prefix.hlcLo <= delta.prefix.hlcLo &&
                delta.prefix.hlcHi <= snapshot.prefix.hlcHi
        }
    }
    ?: false

@Suppress("SwallowedException") // A key that isn't an object key is simply not covered.
private fun parseOrNull(key: String): ObjectKey? =
    try {
        ObjectKey.parse(key)
    } catch (ignored: IllegalArgumentException) {
        null
    }
