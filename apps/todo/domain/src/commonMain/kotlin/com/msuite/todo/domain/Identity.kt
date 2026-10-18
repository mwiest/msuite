package com.msuite.todo.domain

import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.SECRET_SIZE
import com.msuite.core.crypto.randomSecret
import kotlin.concurrent.Volatile

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
    // A lazy per read, so [adopt] can swap in one that is already resolved; the first one is
    // synchronized, so two threads can never generate two different roots.
    @Volatile
    private var secret: Lazy<ByteArray> = lazy { store.load() ?: randomSecret().also(store::save) }

    override fun root(keyGen: Int): ByteArray? = secret.value.takeIf { keyGen == KEY_GEN_V1 }

    /**
     * Takes [root] as the group's (decision 0033): saved at rest and read from then on, by the sync
     * actions already holding this instance. The `deviceId` and the stored data are untouched —
     * they were never keyed by the root.
     */
    fun adopt(root: ByteArray) {
        require(root.size == SECRET_SIZE) { "the root takes $SECRET_SIZE bytes, got ${root.size}" }
        store.save(root)
        secret = lazyOf(root)
    }
}
