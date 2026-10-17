package com.msuite.core.sync

import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

private val GROUP_ID = encodeBase32Hex(ByteArray(16) { it.toByte() })
private val HASH = "0".repeat(52)

private val arbGroupId = Arb.byteArray(Arb.constant(16), Arb.byte()).map(::encodeBase32Hex)
private val arbHash = Arb.byteArray(Arb.constant(32), Arb.byte()).map(::encodeBase32Hex)
private val arbDeviceId = Arb.long().map(::DeviceId)
private val arbHlc = Arb.long().map(Hlc::fromPacked)

private val arbPrefix: Arb<ObjectKeyPrefix> = arbitrary {
    ObjectKeyPrefix(
        appId = Arb.element(listOf("todo", "notes")).bind(),
        groupId = arbGroupId.bind(),
        type = Arb.element(BatchType.entries).bind(),
        deviceId = arbDeviceId.bind(),
        hlcLo = arbHlc.bind(),
        hlcHi = arbHlc.bind(),
    )
}
private val arbKey: Arb<ObjectKey> = arbitrary { arbPrefix.bind().withHash(arbHash.bind()) }

// One namespace and one format, the scope a reader lists in: there text order must be value order.
private val arbSameNamespaceKey: Arb<ObjectKey> = arbitrary {
    ObjectKeyPrefix(
        "todo",
        GROUP_ID,
        Arb.element(BatchType.entries).bind(),
        arbDeviceId.bind(),
        arbHlc.bind(),
        arbHlc.bind(),
    )
        .withHash(arbHash.bind())
}

class ObjectKeyTest : StringSpec({
    "a key round-trips through its text" {
        checkAll(arbKey) { key -> ObjectKey.parse(key.text()) shouldBe key }
    }

    "the text has fixed widths and only the base32hex charset" {
        checkAll(arbKey) { key ->
            key.text() shouldMatch
                Regex("[a-z]+/[0-9A-V]{26}/v[0-9]+_[ds]_[0-9A-V]{13}_[0-9A-V]{13}_[0-9A-V]{13}_[0-9A-V]{52}")
        }
    }

    "lexicographic order of the text equals the value order" {
        checkAll(Arb.list(arbSameNamespaceKey, 0..24)) { keys ->
            keys.sortedBy { it.text() } shouldBe keys.sorted()
        }
    }

    "the prefix is the key minus the hash and the AAD context" {
        val key = ObjectKeyPrefix("todo", GROUP_ID, BatchType.Delta, DeviceId(1), Hlc.of(1, 0), Hlc.of(2, 0))
            .withHash(HASH)
        key.prefix.text() shouldBe key.text().substringBeforeLast('_')
        key.prefix.namespace() shouldBe "todo/$GROUP_ID/"
    }

    "groupId is 26 chars, deterministic and rooted in the secret" {
        val root = ByteArray(32) { it.toByte() }
        val id = groupId(root)
        id.length shouldBe 26
        groupId(root) shouldBe id
        groupId(ByteArray(32) { (it + 1).toByte() }) shouldNotBe id
    }

    "parsing rejects malformed keys" {
        val text = ObjectKeyPrefix("todo", GROUP_ID, BatchType.Snapshot, DeviceId(3), Hlc.of(4, 0), Hlc.of(5, 0))
            .withHash(HASH)
            .text()
        shouldThrow<IllegalArgumentException> { ObjectKey.parse("todo/$GROUP_ID") }
        shouldThrow<IllegalArgumentException> { ObjectKey.parse(text.dropLast(1)) }
        shouldThrow<IllegalArgumentException> { ObjectKey.parse(text.dropLast(1) + "W") }
        shouldThrow<IllegalArgumentException> { ObjectKey.parse(text.replace("_s_", "_x_")) }
    }
})
