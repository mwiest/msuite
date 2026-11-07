package com.msuite.todo.domain

import com.msuite.core.crypto.BackendType
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.PairingPayload
import com.msuite.core.crypto.RecoveryWords
import com.msuite.core.crypto.SECRET_SIZE
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private val group = ByteArray(SECRET_SIZE) { it.toByte() }
private val other = ByteArray(SECRET_SIZE) { (it + 9).toByte() }

/** Actions over a device that holds [stored] (or generates one when `null`). */
private fun actions(stored: ByteArray? = null) =
    PairingActions(StoredIdentity(MemoryRootStore(stored)), backend = { PairingBackend(BackendType.Manual) })

class PairingTest : StringSpec({
    "a device's own code carries its root and the manual transport" {
        val payload = PairingPayload.parse(actions(group).code().shouldNotBeNull()).shouldNotBeNull()

        payload.root.toList() shouldBe group.toList()
        payload.keyGen shouldBe KEY_GEN_V1
        payload.backend shouldBe BackendType.Manual
    }

    "a relay's code carries its URL and joining reports it" {
        val url = "https://relay.example"
        val source = PairingActions(
            StoredIdentity(MemoryRootStore(group)),
            { PairingBackend(BackendType.Relay, url.encodeToByteArray()) },
        )
        val code = source.code().shouldNotBeNull()
        val payload = PairingPayload.parse(code).shouldNotBeNull()
        payload.backend shouldBe BackendType.Relay
        payload.backendSection.decodeToString() shouldBe url

        var joinedType: BackendType? = null
        var joinedSection: ByteArray? = null
        val identity = StoredIdentity(MemoryRootStore(other))
        val target = PairingActions(
            identity,
            { PairingBackend(BackendType.Manual) },
            onJoined = { type, section ->
                joinedType = type
                joinedSection = section
            },
        )

        target.join(code) shouldBe JoinResult.Joined(BackendType.Relay, url.encodeToByteArray())
        joinedType shouldBe BackendType.Relay
        joinedSection?.decodeToString() shouldBe url
        identity.root(KEY_GEN_V1)?.toList() shouldBe group.toList()
    }

    "joining adopts the scanned root" {
        val identity = StoredIdentity(MemoryRootStore(other))
        identity.root(KEY_GEN_V1)?.toList() shouldBe other.toList()

        PairingActions(identity, backend = {
            PairingBackend(BackendType.Manual)
        }).join(actions(group).code().shouldNotBeNull()) shouldBe
            JoinResult.Joined(BackendType.Manual)

        identity.root(KEY_GEN_V1)?.toList() shouldBe group.toList()
    }

    "a preview reads another group's code without adopting anything" {
        val identity = StoredIdentity(MemoryRootStore(other))
        val pairing = PairingActions(identity, backend = { PairingBackend(BackendType.Manual) })
        val code = actions(group).code().shouldNotBeNull()

        pairing.preview(code) shouldBe JoinPreview.Ready

        identity.root(KEY_GEN_V1)?.toList() shouldBe other.toList()
    }

    "a preview reports the codes that join would refuse" {
        val pairing = actions(group)

        pairing.preview(pairing.code().shouldNotBeNull()) shouldBe JoinPreview.AlreadyThisGroup
        pairing.preview(PairingPayload.build(KEY_GEN_V1 + 1, other, BackendType.Manual)) shouldBe
            JoinPreview.UnsupportedKeyGen
        pairing.preview(byteArrayOf(1, 2, 3)) shouldBe JoinPreview.Unreadable
    }

    "a code of this group with another relay offers to switch to it, without touching the root" {
        val url = "https://relay.example"
        val code = PairingPayload.build(KEY_GEN_V1, group, BackendType.Relay, url.encodeToByteArray())
        val store = MemoryRootStore(group)
        var switched: String? = null
        val pairing = PairingActions(
            StoredIdentity(store),
            backend = { PairingBackend(BackendType.Manual) },
            onSwitch = { type, section ->
                switched = "$type ${section.decodeToString()}"
                true
            },
        )
        val saves = store.saves

        pairing.preview(code) shouldBe JoinPreview.SwitchBackend(url)
        pairing.switchBackend(code) shouldBe JoinResult.SwitchedBackend(BackendType.Relay)

        switched shouldBe "Relay $url"
        store.saves shouldBe saves
    }

    "a code of this group with the same relay, or none, is just this group" {
        val url = "https://relay.example".encodeToByteArray()
        val onRelay = PairingActions(
            StoredIdentity(MemoryRootStore(group)),
            backend = { PairingBackend(BackendType.Relay, url) },
            onSwitch = { _, _ -> error("nothing to switch") },
        )

        onRelay.preview(PairingPayload.build(KEY_GEN_V1, group, BackendType.Relay, url)) shouldBe
            JoinPreview.AlreadyThisGroup
        onRelay.preview(PairingPayload.build(KEY_GEN_V1, group, BackendType.Folder)) shouldBe
            JoinPreview.AlreadyThisGroup
    }

    "a switch the backend refuses, or a code of another group, changes nothing" {
        val relayCode = PairingPayload.build(KEY_GEN_V1, group, BackendType.Relay, "http://relay".encodeToByteArray())
        val pairing = PairingActions(
            StoredIdentity(MemoryRootStore(group)),
            backend = { PairingBackend(BackendType.Manual) },
            onSwitch = { _, _ -> false },
        )

        pairing.switchBackend(relayCode) shouldBe JoinResult.AlreadyThisGroup
        pairing.switchBackend(
            PairingPayload.build(KEY_GEN_V1, other, BackendType.Relay, "https://r".encodeToByteArray()),
        ) shouldBe
            JoinResult.Unreadable
    }

    "scanning one's own code changes nothing and writes nothing" {
        val store = MemoryRootStore()
        val pairing = PairingActions(StoredIdentity(store), backend = { PairingBackend(BackendType.Manual) })
        val code = pairing.code().shouldNotBeNull()
        val saves = store.saves

        pairing.join(code) shouldBe JoinResult.AlreadyThisGroup

        store.saves shouldBe saves
    }

    "a code from a newer app, or from nothing at all, is refused" {
        val pairing = actions(group)

        pairing.join(PairingPayload.build(KEY_GEN_V1 + 1, other, BackendType.Manual)) shouldBe
            JoinResult.UnsupportedKeyGen
        pairing.join(byteArrayOf(1, 2, 3)) shouldBe JoinResult.Unreadable
    }

    "a device shows its root as the 24 recovery words" {
        actions(group).words() shouldBe RecoveryWords.encode(group)
    }

    "the words restore the root a code would have carried" {
        val identity = StoredIdentity(MemoryRootStore(other))

        PairingActions(identity, backend = {
            PairingBackend(BackendType.Manual)
        }).restore(RecoveryWords.encode(group)) shouldBe
            JoinResult.Restored

        identity.root(KEY_GEN_V1)?.toList() shouldBe group.toList()
    }

    "restoring on a device with no data adopts the words' root" {
        val identity = StoredIdentity(MemoryRootStore())

        PairingActions(identity, backend = {
            PairingBackend(BackendType.Manual)
        }).restore(RecoveryWords.encode(group)) shouldBe
            JoinResult.Restored

        identity.root(KEY_GEN_V1)?.toList() shouldBe group.toList()
    }

    "restoring one's own words changes nothing" {
        val store = MemoryRootStore()
        val pairing = PairingActions(StoredIdentity(store), backend = { PairingBackend(BackendType.Manual) })

        pairing.restore(RecoveryWords.encode(group)) shouldBe JoinResult.Restored
        val saves = store.saves
        pairing.restore(RecoveryWords.encode(group)) shouldBe JoinResult.AlreadyThisGroup

        store.saves shouldBe saves
    }

    "typed words are previewed before they are restored" {
        val identity = StoredIdentity(MemoryRootStore(other))
        val pairing = PairingActions(identity, backend = { PairingBackend(BackendType.Manual) })

        pairing.preview(RecoveryWords.encode(group)) shouldBe JoinPreview.Ready
        pairing.preview(RecoveryWords.encode(other)) shouldBe JoinPreview.AlreadyThisGroup
        pairing.preview(listOf("abandon")) shouldBe JoinPreview.Unreadable

        identity.root(KEY_GEN_V1)?.toList() shouldBe other.toList()
    }

    "words that don't encode a root are refused" {
        val pairing = actions(group)

        pairing.restore(listOf("abandon")) shouldBe JoinResult.Unreadable
        pairing.restore(RecoveryWords.encode(group).map { "notaword" }) shouldBe JoinResult.Unreadable
        pairing.restore(List(24) { "abandon" }) shouldBe JoinResult.Unreadable
    }
})
