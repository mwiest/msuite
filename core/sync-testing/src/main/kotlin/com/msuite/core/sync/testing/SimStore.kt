package com.msuite.core.sync.testing

import com.msuite.core.model.Batch

/**
 * The shared medium the simulated devices sync through (decision 0057). Layer 1 keeps whole batches
 * in memory ([BatchStore]); Layer 2 pushes encoded bytes through a real plug ([PlugStore]); Layer 3
 * pushes envelope ciphertext under real object keys ([EnvelopePlugStore]). A key identifies a batch;
 * a device's bookkeeping is the set of keys it has ingested.
 */
interface SimStore {
    /** Publishes [batch]; returns the key it landed under. */
    fun put(batch: Batch): String

    /** Every key, in a deterministic order: merges are commutative, so the order only has to be stable. */
    fun keys(): List<String>

    /**
     * How the bytes under [key] read, or `null` when the key is absent. An absent key is a list/get
     * race, not a failed decode: it is skipped without counting an attempt toward the corrupt flag
     * (decision 0069). A key that is present and won't decode is [Fetch.Unreadable].
     */
    fun get(key: String): Fetch?

    /** Deletes the batch under [key]. A no-op if the key is absent. Used for writer-side pruning. */
    fun delete(key: String)
}

/** What a fetch of one key yielded (decisions 0067, 0068, 0069). */
sealed interface Fetch {
    data class Read(val batch: Batch) : Fetch

    /** A header-level skip — TooNew (0067) or an unknown `keyGen` (0008): not a decode failure. */
    data object Skip : Fetch

    /** Present but unreadable (torn, tampered, renamed, foreign root): counts toward corrupt (0069). */
    data object Unreadable : Fetch
}

/** Every published batch, oldest first; absent and unreadable keys are skipped (decision 0068). */
fun SimStore.batches(): List<Batch> = keys().mapNotNull { (get(it) as? Fetch.Read)?.batch }

/** The default store: whole batches in memory, keyed by position. */
class BatchStore : SimStore {
    private val batches = mutableListOf<Batch>()

    override fun put(batch: Batch): String = batches.size.also { batches += batch }.toString()

    override fun keys(): List<String> = batches.indices
        .filter { batches[it] !== Batch.EMPTY }
        .map(Int::toString)

    override fun get(key: String): Fetch? {
        val batch = batches.getOrNull(key.toInt()) ?: return null
        if (batch === Batch.EMPTY) return null // Deleted
        return Fetch.Read(batch)
    }

    override fun delete(key: String) {
        val index = key.toIntOrNull() ?: return
        if (index in batches.indices) {
            batches[index] = Batch.EMPTY // Mark as deleted
        }
    }
}
