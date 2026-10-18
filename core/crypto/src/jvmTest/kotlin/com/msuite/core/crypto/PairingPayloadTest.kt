package com.msuite.core.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val KEY_GEN = 1
private const val CHECKSUM_BYTES = 4

/** The bytes one QR takes with an empty backend section: the layout of record 0072. */
private const val PAYLOAD_BYTES = 1 + 4 + SECRET_SIZE + 1 + CHECKSUM_BYTES

/** The payload of record 0072, built here from the spec: an independent expression of its layout. */
private suspend fun payload(
    keyGen: Int = KEY_GEN,
    root: ByteArray = root(),
    backendType: Byte = 0,
    section: ByteArray = ByteArray(0),
): ByteArray {
    val prefix = byteArrayOf(1) + keyGen.bigEndian() + root + byteArrayOf(backendType) + section
    return prefix + sha256(prefix).copyOf(CHECKSUM_BYTES)
}

private fun root() = ByteArray(SECRET_SIZE) { it.toByte() }

private fun Int.bigEndian(): ByteArray = ByteArray(4) { (this ushr (8 * (3 - it))).toByte() }

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

class PairingPayloadTest : StringSpec({
    "a payload round-trips" {
        val bytes = PairingPayload.build(KEY_GEN, root(), BackendType.Manual)
        bytes.size shouldBe PAYLOAD_BYTES
        val parsed = PairingPayload.parse(bytes)
        parsed?.keyGen shouldBe KEY_GEN
        parsed?.root?.hex() shouldBe root().hex()
        parsed?.backendType shouldBe BackendType.Manual.id
        parsed?.backend shouldBe BackendType.Manual
        parsed?.backendSection?.size shouldBe 0
    }

    "the same root and backend always give the same QR bytes" {
        PairingPayload.build(KEY_GEN, root(), BackendType.Manual).hex() shouldBe
            PairingPayload.build(KEY_GEN, root(), BackendType.Manual).hex()
    }

    "the layout is pinned" {
        PairingPayload.build(KEY_GEN, root(), BackendType.Manual).hex() shouldBe
            "01" + // payload format
            "00000001" + // keyGen
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" + // root
            "00" + // backend type: manual
            "fc825b69" // SHA-256 over the 38 bytes above, first 4
    }

    "every single-bit flip is rejected" {
        val bytes = PairingPayload.build(KEY_GEN, root(), BackendType.Manual)
        for (index in bytes.indices) {
            for (bit in 0 until 8) {
                val flipped = bytes.copyOf()
                flipped[index] = (flipped[index].toInt() xor (1 shl bit)).toByte()
                PairingPayload.parse(flipped) shouldBe null
            }
        }
    }

    "a well-checksummed payload from a newer format is rejected, not misread" {
        val prefix = payload().copyOfRange(0, PAYLOAD_BYTES - CHECKSUM_BYTES)
        prefix[0] = 2
        PairingPayload.parse(prefix + prefix.checksum()) shouldBe null
    }

    "an unknown backend type still yields the identity" {
        val parsed = PairingPayload.parse(payload(backendType = 200.toByte(), section = byteArrayOf(1, 2, 3)))
        parsed?.keyGen shouldBe KEY_GEN
        parsed?.root?.hex() shouldBe root().hex()
        parsed?.backendType shouldBe 200
        parsed?.backend shouldBe null
        parsed?.backendSection?.hex() shouldBe "010203"
    }

    "a known backend type with a section it doesn't have is rejected" {
        PairingPayload.parse(payload(backendType = BackendType.Manual.id.toByte(), section = byteArrayOf(1))) shouldBe
            null
    }

    "bytes that aren't a payload are rejected" {
        PairingPayload.parse(ByteArray(0)) shouldBe null
        PairingPayload.parse(ByteArray(41)) shouldBe null
        PairingPayload.parse(ByteArray(42)) shouldBe null
        PairingPayload.parse("https://example.com/".encodeToByteArray()) shouldBe null
    }

    "build refuses a negative keyGen and a root that isn't 256 bits" {
        shouldThrow<IllegalArgumentException> { PairingPayload.build(-1, root(), BackendType.Manual) }
        shouldThrow<IllegalArgumentException> { PairingPayload.build(KEY_GEN, ByteArray(16), BackendType.Manual) }
    }
})

private suspend fun ByteArray.checksum(): ByteArray = sha256(this).copyOf(CHECKSUM_BYTES)
