package com.msuite.core.crypto

private const val BITS_PER_BYTE = 8
private const val BITS_PER_WORD = 11
private const val ENTROPY_BITS = SECRET_SIZE * BITS_PER_BYTE

/** BIP39's checksum: the leading `ENT / 32` bits of the SHA-256 over the entropy (decision 0011). */
private const val CHECKSUM_BITS = ENTROPY_BITS / 32
private const val WORD_COUNT = (ENTROPY_BITS + CHECKSUM_BITS) / BITS_PER_WORD

/**
 * The group root as BIP39 words, and back (decision 0011, record 0073). Only BIP39's entropy
 * encoding is used, never its PBKDF2 words-to-seed step: the root itself is the secret being
 * carried, so there is no wallet seed to derive and no passphrase to ask for.
 */
object RecoveryWords {
    /** The [WORD_COUNT] words for [root]; the same root always gives the same words. */
    suspend fun encode(root: ByteArray): List<String> {
        require(root.size == SECRET_SIZE) { "the root takes $SECRET_SIZE bytes, got ${root.size}" }
        val bits = root.toBits() + sha256(root).toBits().take(CHECKSUM_BITS)
        return bits.chunked(BITS_PER_WORD).map { BIP39_ENGLISH[it.toInt(2)] }
    }

    /** The root [words] encode, or `null` when a word is unknown or the checksum doesn't match. */
    suspend fun decode(words: List<String>): ByteArray? {
        val indices = words.map { BIP39_ENGLISH.indexOf(it.trim().lowercase()) }
        if (indices.size != WORD_COUNT || indices.any { it < 0 }) return null
        val bits = indices.joinToString("") { it.toString(2).padStart(BITS_PER_WORD, '0') }
        val root = bits.take(ENTROPY_BITS).chunked(BITS_PER_BYTE).map { it.toInt(2).toByte() }.toByteArray()
        return root.takeIf { bits.drop(ENTROPY_BITS) == sha256(it).toBits().take(CHECKSUM_BITS) }
    }
}

private fun ByteArray.toBits(): String =
    joinToString("") { byte -> (byte.toInt() and BYTE_MASK).toString(2).padStart(BITS_PER_BYTE, '0') }
