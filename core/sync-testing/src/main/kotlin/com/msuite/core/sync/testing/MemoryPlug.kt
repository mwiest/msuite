package com.msuite.core.sync.testing

import com.msuite.core.sync.BackendPlug

/**
 * An in-memory [BackendPlug] for tests and the simulator (decision 0057). Create-only, and it
 * copies its bytes, so a caller can't mutate what is stored through the array it passed.
 */
class MemoryPlug : BackendPlug {
    private val objects = linkedMapOf<String, ByteArray>()

    override suspend fun put(key: String, bytes: ByteArray) {
        objects.putIfAbsent(key, bytes.copyOf())
    }

    override suspend fun list(prefix: String): List<String> = objects.keys.filter { it.startsWith(prefix) }

    override suspend fun get(key: String): ByteArray? = objects[key]?.copyOf()
}
