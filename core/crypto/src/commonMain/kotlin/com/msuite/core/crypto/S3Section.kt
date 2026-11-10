package com.msuite.core.crypto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * The S3 backend's section of a pairing QR (plan `v1-s3-plug.md`): the whole configuration, the
 * secret included, since whoever sees the QR has the root anyway. ProtoBuf, so a later field is
 * skipped by an older reader. The field numbers are pinned by a test and can never change.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class S3Section(
    @ProtoNumber(1) val endpoint: String,
    @ProtoNumber(2) val region: String,
    @ProtoNumber(3) val bucket: String,
    @ProtoNumber(4) val prefix: String = "",
    @ProtoNumber(5) val accessKeyId: String,
    @ProtoNumber(6) val secretAccessKey: String,
) {
    @OptIn(ExperimentalSerializationApi::class)
    fun encode(): ByteArray = ProtoBuf.encodeToByteArray(this)

    override fun toString(): String = "S3Section($endpoint, $region, $bucket, $prefix, $accessKeyId, secret redacted)"

    /** Whether a plug could be built from this; the plug's config checks the endpoint's shape in full. */
    private fun usable(): Boolean = (endpoint.startsWith("https://") || endpoint.startsWith("http://")) &&
        listOf(region, bucket, accessKeyId, secretAccessKey).none { it.isBlank() }

    companion object {
        /** The section in [bytes], or `null` if they don't decode to a usable one. */
        @OptIn(ExperimentalSerializationApi::class)
        fun decode(bytes: ByteArray): S3Section? = try {
            ProtoBuf.decodeFromByteArray<S3Section>(bytes).takeIf { it.usable() }
        } catch (ignored: SerializationException) {
            null
        }
    }
}
