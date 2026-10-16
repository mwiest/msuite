package com.msuite.core.sync.testing

import com.msuite.core.model.Batch

/**
 * The shared medium the simulated devices sync through (decision 0057). Layer 1 keeps whole batches
 * in memory ([BatchStore]); Layer 2 pushes encoded bytes through a real plug ([PlugStore]). A key
 * identifies a batch; a device's bookkeeping is the set of keys it has ingested.
 */
interface SimStore {
    /** Publishes [batch]; returns the key it landed under. */
    fun put(batch: Batch): String

    /** Every key, in a deterministic order: merges are commutative, so the order only has to be stable. */
    fun keys(): List<String>

    /** The batch under [key], or `null` if its bytes can't be read (decisions 0068, 0069: skip, retry, then flag). */
    fun get(key: String): Batch?
}

/** Every published batch, oldest first; unreadable keys are skipped (decision 0068). */
fun SimStore.batches(): List<Batch> = keys().mapNotNull(::get)

/** The default store: whole batches in memory, keyed by position. */
class BatchStore : SimStore {
    private val batches = mutableListOf<Batch>()

    override fun put(batch: Batch): String = batches.size.also { batches += batch }.toString()

    override fun keys(): List<String> = batches.indices.map(Int::toString)

    override fun get(key: String): Batch? = batches.getOrNull(key.toInt())
}
