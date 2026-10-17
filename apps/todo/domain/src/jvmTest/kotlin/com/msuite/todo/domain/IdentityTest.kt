package com.msuite.todo.domain

import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.SECRET_SIZE
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** An in-memory [RootStore]: JVM tests use a plain root (decision 0039 is device-only). */
private class MemoryRootStore(var stored: ByteArray? = null) : RootStore {
    var saves = 0
        private set

    override fun load(): ByteArray? = stored

    override fun save(root: ByteArray) {
        stored = root
        saves++
    }
}

class IdentityTest : StringSpec({
    "the root is generated once on first use and stored" {
        val store = MemoryRootStore()
        val identity = StoredIdentity(store)
        val root = identity.root(KEY_GEN_V1).shouldNotBeNull()
        root.size shouldBe SECRET_SIZE
        store.saves shouldBe 1
        store.stored?.toList() shouldBe root.toList()
        identity.root(KEY_GEN_V1)?.toList() shouldBe root.toList()
        store.saves shouldBe 1
    }

    "a stored root is used as is, not regenerated" {
        val stored = ByteArray(SECRET_SIZE) { it.toByte() }
        val store = MemoryRootStore(stored)
        StoredIdentity(store).root(KEY_GEN_V1)?.toList() shouldBe stored.toList()
        store.saves shouldBe 0
    }

    "an unknown keyGen has no root" {
        StoredIdentity(MemoryRootStore()).root(KEY_GEN_V1 + 1).shouldBeNull()
    }

    "the same root comes back across instances over one store" {
        val store = MemoryRootStore()
        val first = StoredIdentity(store).root(KEY_GEN_V1).shouldNotBeNull()
        StoredIdentity(store).root(KEY_GEN_V1)?.toList() shouldBe first.toList()
    }
})
