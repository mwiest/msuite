package com.msuite.core.sync.testing

import com.msuite.core.sync.BackendPlug
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * An in-memory [BackendPlug] for tests and the simulator (decision 0057). Create-only, and it
 * copies its bytes, so a caller can't mutate what is stored through the array it passed. It lists
 * keys in a shuffled order on purpose: no plug owes the caller an order, so nothing may depend on
 * one (see `assertPlugContract`).
 */
class MemoryPlug(private val seed: Long = 0) : BackendPlug {
    private val objects = linkedMapOf<String, ByteArray>()

    /** How often [get] was called; the simulator asserts a corrupt key stops being fetched (0069). */
    var gets = 0
        private set

    override suspend fun put(key: String, bytes: ByteArray) {
        objects.putIfAbsent(key, bytes.copyOf())
    }

    override suspend fun list(prefix: String): List<String> =
        objects.keys.filter { it.startsWith(prefix) }.shuffled(Random(seed))

    override suspend fun get(key: String): ByteArray? = objects[key]?.also { gets++ }?.copyOf()

    override suspend fun delete(key: String) {
        objects.remove(key)
    }
}
