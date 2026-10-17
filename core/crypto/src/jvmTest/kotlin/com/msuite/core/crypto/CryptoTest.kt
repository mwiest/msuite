package com.msuite.core.crypto

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private const val INFO = "msuite-batch-v1"

class CryptoTest : StringSpec({
    "sha256 matches the known digest of abc" {
        sha256("abc".encodeToByteArray()).hex() shouldBe
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }

    "hkdf matches RFC 5869 test case 1" {
        hkdf(
            input = ByteArray(22) { 0x0b },
            salt = "000102030405060708090a0b0c".hexBytes(),
            info = "f0f1f2f3f4f5f6f7f8f9".hexBytes(),
            size = 42,
        ).hex() shouldBe
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
    }

    "hkdf is deterministic and separates by salt and info" {
        val input = ByteArray(8) { 1 }
        val salt = ByteArray(8) { 2 }
        hkdf(input, salt, "a") shouldBe hkdf(input, salt, "a")
        hkdf(input, salt, "a") shouldNotBe hkdf(input, salt, "b")
        hkdf(input, salt, "a") shouldNotBe hkdf(input, ByteArray(8) { 3 }, "a")
    }

    "AES-256-GCM seals and opens under a fresh key" {
        val arbBytes = Arb.byteArray(Arb.int(0..64), Arb.byte())
        checkAll(arbBytes, arbBytes) { plaintext, aad ->
            runBlocking {
                val key = randomSecret()
                val ciphertext = AesGcm.seal(key, plaintext, aad)
                AesGcm.open(key, ciphertext, aad)?.toList() shouldBe plaintext.toList()
            }
        }
    }

    "a tampered ciphertext fails to open" {
        val key = randomSecret()
        val ciphertext = AesGcm.seal(key, byteArrayOf(1, 2, 3), byteArrayOf(9))
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        AesGcm.open(key, ciphertext, byteArrayOf(9)).shouldBeNull()
    }

    "the wrong associated data fails to open" {
        val key = randomSecret()
        val ciphertext = AesGcm.seal(key, byteArrayOf(1, 2, 3), byteArrayOf(9))
        AesGcm.open(key, ciphertext, byteArrayOf(8)).shouldBeNull()
    }

    "the same salt derives the same key, so a cached seal stays stable" {
        val root = randomSecret()
        val salt = randomSecret()
        hkdf(root, salt, "msuite-batch-v1") shouldBe hkdf(root, salt, "msuite-batch-v1")
    }

    "fresh salts give distinct ciphertexts" {
        val plaintext = byteArrayOf(1, 2, 3)
        val saltA = randomSecret()
        val saltB = randomSecret()
        AesGcm.seal(hkdf(randomSecret(), saltA, INFO), plaintext, byteArrayOf()) shouldNotBe
            AesGcm.seal(hkdf(randomSecret(), saltB, INFO), plaintext, byteArrayOf())
    }

    "randomSecret returns 256-bit secrets that differ" {
        val secrets = List(8) { randomSecret() }
        secrets.forEach { it.size shouldBe SECRET_SIZE }
        secrets.distinct().size shouldBe secrets.size
        randomSecret(size = 16).size shouldBe 16
    }
})
