package com.msuite.core.crypto

import com.msuite.core.model.Batch
import com.msuite.core.model.BatchEncoder
import com.msuite.core.model.MalformedBatch
import com.msuite.core.model.Model

/** The first root generation; rotation adds more keyed by this id (decision 0008). */
const val KEY_GEN_V1 = 1

/** HKDF info that turns the root and a batch's salt into its AES key (decisions 0010, 0029). */
const val BATCH_KEY_INFO = "msuite-batch-v1"

/** What reading an envelope yielded. Unreadable bytes throw [MalformedBatch] instead (0068, 0069). */
sealed interface EnvelopeResult {
    data class Opened(val batch: Batch) : EnvelopeResult

    /** Sealed under a newer data-model version (decision 0067): skip, don't count as corrupt. */
    data object TooNew : EnvelopeResult

    /** Sealed under a root generation this device doesn't hold (decision 0008): skip it. */
    data object UnknownKeyGen : EnvelopeResult
}

/**
 * The encrypted batch envelope (decisions 0010, 0029): a cleartext header — envelope format, random
 * 256-bit salt, data-model version, `keyGen` — then AES-256-GCM over the encoded batch under
 * `HKDF-SHA256(root, salt, "msuite-batch-v1")`. The header is authenticated as AAD together with an
 * [aadContext] (a plug object's key minus its hash, or a constant for the manual file), so bytes
 * renamed, moved or replayed into another key fail to open.
 *
 * The ciphertext is deterministic for one key, so a caller seals a batch once and caches the bytes
 * rather than re-sealing (a fresh key per salt rules out nonce reuse; decision 0010).
 */
class BatchEnvelope(
    private val encoder: BatchEncoder,
    private val roots: (keyGen: Int) -> ByteArray?,
    private val keyGen: Int = KEY_GEN_V1,
) {
    /** Seals [batch]; the fresh [salt] gives a fresh key, so the result is used once. */
    suspend fun seal(batch: Batch, aadContext: String, salt: ByteArray = randomSecret()): ByteArray {
        val root = requireNotNull(roots(keyGen)) { "no root for keyGen $keyGen" }
        val header = header(salt, batch.dataModelVersion, keyGen)
        val key = hkdf(root, salt, BATCH_KEY_INFO)
        return header + AesGcm.seal(key, encoder.encode(batch), header + aadContext.encodeToByteArray())
    }

    /**
     * Opens [bytes] under [aadContext]. A too-new version or an unknown `keyGen` is reported before
     * any decryption and is not a decode failure (0069); a header that won't parse, or a GCM open
     * that fails (torn, tampered, a foreign root), throws [MalformedBatch].
     */
    suspend fun open(bytes: ByteArray, aadContext: String): EnvelopeResult {
        val header = parse(bytes) ?: throw MalformedBatch()
        if (header.dataModelVersion > Model.DATA_MODEL_VERSION) return EnvelopeResult.TooNew
        return roots(header.keyGen)?.let { decrypt(bytes, header, it, aadContext) } ?: EnvelopeResult.UnknownKeyGen
    }

    private suspend fun decrypt(bytes: ByteArray, header: Header, root: ByteArray, aadContext: String): EnvelopeResult {
        val key = hkdf(root, header.salt, BATCH_KEY_INFO)
        val plaintext = AesGcm.open(
            key = key,
            ciphertext = bytes.copyOfRange(HEADER_SIZE, bytes.size),
            associatedData = bytes.copyOfRange(0, HEADER_SIZE) + aadContext.encodeToByteArray(),
        ) ?: throw MalformedBatch()
        return EnvelopeResult.Opened(Batch(header.dataModelVersion, encoder.decode(plaintext).cells))
    }

    private class Header(val salt: ByteArray, val dataModelVersion: Int, val keyGen: Int)

    private fun header(salt: ByteArray, dataModelVersion: Int, keyGen: Int): ByteArray {
        require(salt.size == SECRET_SIZE) { "salt must be $SECRET_SIZE bytes" }
        val bytes = ByteArray(HEADER_SIZE)
        bytes[FORMAT_INDEX] = FORMAT.toByte()
        salt.copyInto(bytes, SALT_INDEX)
        dataModelVersion.toBigEndian().copyInto(bytes, VERSION_INDEX)
        keyGen.toBigEndian().copyInto(bytes, KEY_GEN_INDEX)
        return bytes
    }

    /** The header, or `null` if [bytes] don't start with one this format wrote. */
    private fun parse(bytes: ByteArray): Header? = when {
        bytes.size < HEADER_SIZE -> null

        bytes[FORMAT_INDEX].toInt() != FORMAT -> null

        bytes.intAt(VERSION_INDEX) < 0 || bytes.intAt(KEY_GEN_INDEX) < 0 -> null

        else -> Header(
            bytes.copyOfRange(SALT_INDEX, VERSION_INDEX),
            bytes.intAt(VERSION_INDEX),
            bytes.intAt(KEY_GEN_INDEX),
        )
    }

    private companion object {
        const val FORMAT = 1
        const val FORMAT_INDEX = 0
        const val SALT_INDEX = FORMAT_INDEX + 1
        const val VERSION_INDEX = SALT_INDEX + SECRET_SIZE
        const val KEY_GEN_INDEX = VERSION_INDEX + INT_BYTES
        const val HEADER_SIZE = KEY_GEN_INDEX + INT_BYTES
    }
}
