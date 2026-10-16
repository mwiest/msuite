package com.msuite.core.sync

/**
 * A backend as the client sees it (decision 0003): append-only, create-only and atomic. The client
 * is the only place that merges (decision 0028), so a plug just moves opaque bytes under keys it
 * never interprets. The key grammar (decision 0029) is the caller's business, so a folder, a bucket
 * or the relay all fit this one interface.
 *
 * The manual plug is *not* one of these: it has no list and no names, and is a snapshot
 * export/import instead (decision 0066).
 */
interface BackendPlug {
    /**
     * Writes [bytes] under [key]. Create-only: an existing [key] is left untouched, never clobbered
     * (decision 0003). Callers key objects by content (decision 0029), so a repeated put carries the
     * same bytes and a caller treats an existing key as success.
     */
    suspend fun put(key: String, bytes: ByteArray)

    /**
     * The keys under [prefix], names only, in no guaranteed order. A key can briefly point at
     * incomplete bytes on some backends, so a caller tolerates an object it can't read (0068).
     */
    suspend fun list(prefix: String): List<String>

    /** The bytes under [key], or `null` if it is absent. */
    suspend fun get(key: String): ByteArray?
}
