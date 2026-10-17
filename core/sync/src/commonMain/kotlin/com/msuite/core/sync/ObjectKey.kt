package com.msuite.core.sync

import com.msuite.core.crypto.hkdf
import com.msuite.core.crypto.sha256
import com.msuite.core.model.DeviceId
import com.msuite.core.model.Hlc

/** The naming-scheme version that starts every name (decision 0029). */
const val KEY_FORMAT_V1 = 1

/** HKDF info that turns the root into the group namespace id (decision 0029). */
const val GROUP_KEY_INFO = "msuite-group-v1"

/** Delta or snapshot; the `{type}` field of a name (decision 0029). */
enum class BatchType(val tag: Char) {
    Delta('d'),
    Snapshot('s'),
    ;

    companion object {
        fun of(tag: Char): BatchType =
            entries.firstOrNull { it.tag == tag } ?: throw IllegalArgumentException("unknown batch type '$tag'")
    }
}

/** The group's namespace id: the first 128 bits of `HKDF-SHA256(root, "msuite-group-v1")` (0029). */
suspend fun groupId(root: ByteArray): String = encodeBase32Hex(hkdf(root, ByteArray(0), GROUP_KEY_INFO, GROUP_BYTES))

/** The `hash` field for a batch: SHA-256 over its ciphertext, uppercase base32hex (decisions 0003, 0029). */
suspend fun ciphertextHash(ciphertext: ByteArray): String = encodeBase32Hex(sha256(ciphertext))

/**
 * The batch object key without its `hash`: the AAD context a publisher seals with and a reader
 * rebuilds from the name it fetched (decisions 0029, 0071).
 */
data class ObjectKeyPrefix(
    val appId: String,
    val groupId: String,
    val type: BatchType,
    val deviceId: DeviceId,
    val hlcLo: Hlc,
    val hlcHi: Hlc,
    val format: Int = KEY_FORMAT_V1,
) {
    /** `{appId}/{groupId}/`; the prefix a reader lists to find one group's objects (decision 0029). */
    fun namespace(): String = "$appId/$groupId/"

    /** The key minus its hash: `{appId}/{groupId}/v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}`. */
    fun text(): String = namespace() + "v$format" +
        "_${type.tag}" +
        "_${encodeBase32Hex(deviceId.bits.toBytes())}" +
        "_${encodeBase32Hex(hlcLo.packed.toBytes())}" +
        "_${encodeBase32Hex(hlcHi.packed.toBytes())}"

    fun withHash(hash: String): ObjectKey = ObjectKey(this, hash)
}

/** A batch's full object key, ordered like its decoded fields so a text sort is a value sort (0029). */
data class ObjectKey(val prefix: ObjectKeyPrefix, val hash: String) : Comparable<ObjectKey> {
    fun text(): String = "${prefix.text()}_$hash"

    override fun compareTo(other: ObjectKey): Int = compareValuesBy(
        this,
        other,
        { it.prefix.format },
        { it.prefix.type.tag },
        { it.prefix.deviceId },
        { it.prefix.hlcLo },
        { it.prefix.hlcHi },
        { it.hash },
    )

    companion object {
        /** Parses [text]; throws [IllegalArgumentException] on anything that isn't a well-formed key. */
        fun parse(text: String): ObjectKey {
            val slashes = text.split('/')
            require(slashes.size == NAMESPACE_SEGMENTS) { "not an object key: $text" }
            val appId = slashes[0]
            val groupId = slashes[1]
            require(appId.isNotEmpty()) { "empty app id: $text" }
            require(groupId.length == charsFor(GROUP_BYTES)) { "bad group id: $text" }

            val fields = slashes[2].split('_')
            require(fields.size == NAME_FIELDS) { "not a name: $text" }
            val format = fields[NAME_FORMAT].removePrefix("v").toIntOrNull()
            require(format != null && fields[NAME_FORMAT].startsWith("v") && format >= 0) { "bad format: $text" }
            require(fields[NAME_TYPE].length == 1) { "bad type: $text" }
            require(fields[NAME_DEVICE].length == charsFor(LONG_BYTES)) { "bad device id width: $text" }
            require(fields[NAME_HLC_LO].length == charsFor(LONG_BYTES)) { "bad hlcLo width: $text" }
            require(fields[NAME_HLC_HI].length == charsFor(LONG_BYTES)) { "bad hlcHi width: $text" }
            require(fields[NAME_HASH].length == charsFor(HASH_BYTES) && fields[NAME_HASH].all { it in ALPHABET }) {
                "bad hash: $text"
            }

            val prefix = ObjectKeyPrefix(
                appId = appId,
                groupId = groupId,
                type = BatchType.of(fields[NAME_TYPE][0]),
                deviceId = DeviceId(decodeBase32Hex(fields[NAME_DEVICE], LONG_BYTES).toLongBits()),
                hlcLo = Hlc.fromPacked(decodeBase32Hex(fields[NAME_HLC_LO], LONG_BYTES).toLongBits()),
                hlcHi = Hlc.fromPacked(decodeBase32Hex(fields[NAME_HLC_HI], LONG_BYTES).toLongBits()),
                format = format,
            )
            return ObjectKey(prefix, fields[NAME_HASH])
        }
    }
}

private const val GROUP_BYTES = 16
private const val HASH_BYTES = 32
private const val LONG_BYTES = 8
private const val BITS_PER_BYTE = 8
private const val BITS_PER_CHAR = 5
private const val CHAR_MASK = 0x1F
private const val BYTE_MASK = 0xFF
private const val NAME_FIELDS = 6
private const val NAME_FORMAT = 0
private const val NAME_TYPE = 1
private const val NAME_DEVICE = 2
private const val NAME_HLC_LO = 3
private const val NAME_HLC_HI = 4
private const val NAME_HASH = 5
private const val NAMESPACE_SEGMENTS = 3

// RFC 4648 base32hex: `0-9A-V` is ASCII-ascending, so fixed-width text sorts in value order (0029).
private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUV"

/** Chars in an unpadded base32hex encoding of [bytes]. */
internal fun charsFor(bytes: Int): Int = (bytes * BITS_PER_BYTE + BITS_PER_CHAR - 1) / BITS_PER_CHAR

internal fun encodeBase32Hex(bytes: ByteArray): String {
    val text = StringBuilder(charsFor(bytes.size))
    var buffer = 0
    var bits = 0
    for (byte in bytes) {
        buffer = (buffer shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK)
        bits += BITS_PER_BYTE
        while (bits >= BITS_PER_CHAR) {
            bits -= BITS_PER_CHAR
            text.append(ALPHABET[(buffer shr bits) and CHAR_MASK])
        }
    }
    if (bits > 0) text.append(ALPHABET[(buffer shl (BITS_PER_CHAR - bits)) and CHAR_MASK])
    return text.toString()
}

internal fun decodeBase32Hex(text: String, byteCount: Int): ByteArray {
    require(text.length == charsFor(byteCount)) { "expected ${charsFor(byteCount)} chars, got ${text.length}" }
    val bytes = ByteArray(byteCount)
    var buffer = 0L
    var bits = 0
    var written = 0
    for (char in text) {
        val value = ALPHABET.indexOf(char)
        require(value >= 0) { "not base32hex: '$char'" }
        buffer = (buffer shl BITS_PER_CHAR) or value.toLong()
        bits += BITS_PER_CHAR
        if (bits >= BITS_PER_BYTE && written < byteCount) {
            bits -= BITS_PER_BYTE
            bytes[written++] = ((buffer shr bits) and BYTE_MASK.toLong()).toByte()
            buffer = buffer and ((1L shl bits) - 1)
        }
    }
    return bytes
}

private fun Long.toBytes(): ByteArray =
    ByteArray(LONG_BYTES) { (this ushr ((LONG_BYTES - 1 - it) * BITS_PER_BYTE)).toByte() }

private fun ByteArray.toLongBits(): Long = fold(0L) { value, byte ->
    (value shl BITS_PER_BYTE) or
        (byte.toLong() and BYTE_MASK.toLong())
}
