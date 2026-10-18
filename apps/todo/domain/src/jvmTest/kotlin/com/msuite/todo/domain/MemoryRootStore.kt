package com.msuite.todo.domain

/** An in-memory [RootStore]: JVM tests use a plain root (decision 0039 is device-only). */
internal class MemoryRootStore(var stored: ByteArray? = null) : RootStore {
    var saves = 0
        private set

    override fun load(): ByteArray? = stored

    override fun save(root: ByteArray) {
        stored = root
        saves++
    }
}
