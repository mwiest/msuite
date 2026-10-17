package com.msuite.core.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/** 256 bits in bytes: the root secret and a per-batch salt (decisions 0008, 0010). */
const val SECRET_SIZE = 32

// cryptography-kotlin composes vetted platform providers (JCA on android/jvm); decision 0010.
private val provider = CryptographyProvider.Default

/** The SHA-256 digest of [bytes]: content addressing and the `groupId` (decisions 0003, 0029). */
suspend fun sha256(bytes: ByteArray): ByteArray = provider.get(SHA256).hasher().hash(bytes)

/** HKDF-SHA256 (decision 0010): [size] bytes from [input], salted with [salt], bound to [info]. */
suspend fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray, size: Int = SECRET_SIZE): ByteArray =
    provider.get(HKDF)
        .secretDerivation(
            digest = SHA256,
            outputSize = (size * 8).bits,
            salt = salt,
            info = info,
        )
        .deriveSecretToByteArray(input)

/** [hkdf] with a UTF-8 [info] string, the form 0029's domain-separation labels take. */
suspend fun hkdf(input: ByteArray, salt: ByteArray, info: String, size: Int = SECRET_SIZE): ByteArray =
    hkdf(input, salt, info.encodeToByteArray(), size)

/** A fresh [size]-byte secret from the platform RNG: a group root or a per-batch salt (0008, 0010). */
fun randomSecret(size: Int = SECRET_SIZE): ByteArray = CryptographyRandom.nextBytes(size)

/**
 * AES-256-GCM under a per-batch key, with a fixed zero nonce: each key seals exactly one batch, so
 * the nonce can never repeat (decision 0010). [associatedData] is authenticated, not encrypted.
 */
// A fixed IV is normally a footgun; here the per-batch HKDF key is the guard (decision 0010).
@OptIn(DelicateCryptographyApi::class)
object AesGcm {
    private const val NONCE_SIZE = 12
    private val algorithm = provider.get(AES.GCM)
    private val nonce = ByteArray(NONCE_SIZE)

    /** [ciphertext | tag] for [plaintext], authenticated against [associatedData]. */
    suspend fun seal(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        algorithm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher()
            .encryptWithIv(iv = nonce, plaintext = plaintext, associatedData = associatedData)

    /** [plaintext], or `null` when the tag doesn't verify: torn, tampered, or the wrong key. */
    // A tag mismatch surfaces as the platform's own exception type (JCA's AEADBadTagException).
    @Suppress("TooGenericExceptionCaught")
    suspend fun open(key: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray? = try {
        algorithm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher()
            .decryptWithIv(iv = nonce, ciphertext = ciphertext, associatedData = associatedData)
    } catch (ignored: Exception) {
        null
    }
}
