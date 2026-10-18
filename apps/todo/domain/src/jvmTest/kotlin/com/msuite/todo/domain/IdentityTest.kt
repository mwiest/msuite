package com.msuite.todo.domain

import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.SECRET_SIZE
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

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

    "adopting a root saves it and reads it back, before and after a restart" {
        val store = MemoryRootStore()
        val identity = StoredIdentity(store)
        identity.root(KEY_GEN_V1).shouldNotBeNull()
        val group = ByteArray(SECRET_SIZE) { (it + 7).toByte() }

        identity.adopt(group)

        identity.root(KEY_GEN_V1)?.toList() shouldBe group.toList()
        store.stored?.toList() shouldBe group.toList()
        StoredIdentity(store).root(KEY_GEN_V1)?.toList() shouldBe group.toList()
    }

    "adopting refuses a root that isn't 256 bits" {
        val identity = StoredIdentity(MemoryRootStore())
        shouldThrow<IllegalArgumentException> { identity.adopt(ByteArray(16)) }
    }
})
