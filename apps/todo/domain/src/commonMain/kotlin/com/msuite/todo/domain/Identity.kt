package com.msuite.todo.domain

import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.randomSecret

/**
 * Where the group root is kept at rest (decision 0039). The shell wraps it with a non-exportable
 * Android KeyStore key; a web build would use its own implementation (decision 0012). `core` never
 * sees the platform code.
 */
interface RootStore {
    /** The stored root, or `null` before the first run. */
    fun load(): ByteArray?

    /** Persists the root, wrapped (decision 0039). */
    fun save(root: ByteArray)
}

/**
 * The group's root, generated on first use and read from [store] (decisions 0008, 0039). One root
 * per device for v1; rotation adds generations later (decision 0008).
 */
class StoredIdentity(private val store: RootStore) : IdentityProvider {
    private val secret: ByteArray by lazy { store.load() ?: randomSecret().also(store::save) }

    override fun root(keyGen: Int): ByteArray? = secret.takeIf { keyGen == KEY_GEN_V1 }
}
