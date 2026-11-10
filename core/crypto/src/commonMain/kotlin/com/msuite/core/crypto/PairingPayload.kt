package com.msuite.core.crypto

/** The payload format that starts every pairing QR; a bump means the layout below changed (0072). */
private const val PAYLOAD_FORMAT = 1

/** Width of the checksum: the leading bytes of the SHA-256 over everything before it (0072). */
private const val CHECKSUM_BYTES = 4

private const val FORMAT_INDEX = 0
private const val KEY_GEN_INDEX = FORMAT_INDEX + 1
private const val ROOT_INDEX = KEY_GEN_INDEX + INT_BYTES
private const val BACKEND_INDEX = ROOT_INDEX + SECRET_SIZE

/** Everything before the checksum; a type's section extends it. */
private const val BODY_BYTES = BACKEND_INDEX + 1
private const val MIN_BYTES = BODY_BYTES + CHECKSUM_BYTES

/** A backend the apps know how to configure, as a pairing QR names it (record 0072). */
// The ids are the QR's wire format, not calculations.
@Suppress("MagicNumber")
enum class BackendType(val id: Int) {
    /** The manual snapshot file: the only plug so far, and one with nothing to carry (decision 0066). */
    Manual(0),

    /** The synced-folder plug: no config carried, the joining device picks its own folder (0042, 0072). */
    Folder(1),

    /** The relay: its section is the relay URL in UTF-8 (record 0080). */
    Relay(2),

    /** S3-compatible object storage: its section is an [S3Section], credentials included. */
    S3(3),
}

/**
 * What one pairing QR carries (decisions 0008, 0072): the group root, the `keyGen` it belongs to,
 * and the group's backend descriptor — a type, plus a section whose layout that type fixes. Identity
 * sits at fixed offsets, so a build that doesn't know the type still reads the root and pairs.
 *
 * Unlike an envelope there is no salt: the same root and backend must give the same QR. The checksum
 * only catches a misread code — whoever sees the QR has the root anyway (decision 0008).
 */
class PairingPayload private constructor(
    val keyGen: Int,
    val root: ByteArray,
    val backendType: Int,
    val backendSection: ByteArray,
) {
    /** The backend when this build knows [backendType], `null` when a newer app chose it (0072). */
    val backend: BackendType? get() = BackendType.entries.firstOrNull { it.id == backendType }

    companion object {
        /** The bytes for one QR: [root] under [keyGen], the backend descriptor, and the checksum. */
        suspend fun build(
            keyGen: Int,
            root: ByteArray,
            backend: BackendType,
            section: ByteArray = ByteArray(0),
        ): ByteArray {
            require(keyGen >= 0) { "keyGen must not be negative: $keyGen" }
            require(root.size == SECRET_SIZE) { "the root takes $SECRET_SIZE bytes, got ${root.size}" }
            require(sectionFits(backend, section)) { "not a ${backend.name} backend section" }
            val bytes = ByteArray(BODY_BYTES + section.size)
            bytes[FORMAT_INDEX] = PAYLOAD_FORMAT.toByte()
            keyGen.toBigEndian().copyInto(bytes, KEY_GEN_INDEX)
            root.copyInto(bytes, ROOT_INDEX)
            bytes[BACKEND_INDEX] = backend.id.toByte()
            section.copyInto(bytes, BODY_BYTES)
            return bytes.checksummed()
        }

        /**
         * Reads a payload, or `null` when [bytes] are not one this format wrote: a wrong format byte,
         * a failed checksum, a negative `keyGen`, or a section a known type doesn't have. A backend
         * type this build doesn't know is kept, not failed — the join is about the root (0072).
         */
        suspend fun parse(bytes: ByteArray): PairingPayload? {
            if (bytes.size < MIN_BYTES || bytes[FORMAT_INDEX].toInt() != PAYLOAD_FORMAT) return null
            val covered = bytes.copyOf(bytes.size - CHECKSUM_BYTES)
            val checksum = bytes.copyOfRange(covered.size, bytes.size)
            val verified = sha256(covered).copyOf(CHECKSUM_BYTES).contentEquals(checksum)
            return if (verified) decoded(covered) else null
        }

        /** The fields of a checksum-verified payload, or `null` when they don't fit the format. */
        private fun decoded(covered: ByteArray): PairingPayload? {
            val keyGen = covered.intAt(KEY_GEN_INDEX)
            val backendType = covered[BACKEND_INDEX].toInt() and BYTE_MASK
            val section = covered.copyOfRange(BACKEND_INDEX + 1, covered.size)
            // We never build a negative keyGen, and a known type must have exactly its section.
            val known = BackendType.entries.firstOrNull { it.id == backendType }
            val sectionValid = known == null || sectionFits(known, section)
            return if (keyGen >= 0 && sectionValid) {
                PairingPayload(keyGen, covered.copyOfRange(ROOT_INDEX, BACKEND_INDEX), backendType, section)
            } else {
                null
            }
        }

        /** Whether [section] is the one [type] carries: a relay URL (0080), an S3 section, or none. */
        private fun sectionFits(type: BackendType, section: ByteArray): Boolean = when (type) {
            BackendType.Manual, BackendType.Folder -> section.isEmpty()
            BackendType.Relay -> relayUrl(section) != null
            BackendType.S3 -> s3Section(section) != null
        }

        /** The S3 configuration [section] encodes, or `null` when it isn't a usable one. */
        fun s3Section(section: ByteArray): S3Section? = S3Section.decode(section)

        /** The relay URL [section] encodes, or `null` when it isn't a UTF-8 http(s) URL. */
        fun relayUrl(section: ByteArray): String? {
            val text = runCatching { section.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
            return text?.takeIf {
                (it.startsWith("https://") && it.length > "https://".length) ||
                    (it.startsWith("http://") && it.length > "http://".length)
            }
        }

        private suspend fun ByteArray.checksummed(): ByteArray = this + sha256(this).copyOf(CHECKSUM_BYTES)
    }
}
