package com.msuite.core.s3

import com.msuite.core.crypto.hmacSha256
import com.msuite.core.crypto.sha256
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** An access key pair for one bucket. */
class S3Credentials(val accessKeyId: String, val secretAccessKey: String) {
    override fun toString(): String = "S3Credentials($accessKeyId, secret redacted)"
}

/** Where a signature is valid: a region and a service (`s3` for every S3-compatible store). */
data class SigV4Scope(val region: String, val service: String = "s3")

/**
 * One request to sign. [path] and [query] are raw, not percent-encoded: the signer encodes them,
 * and the caller must send them encoded by [SigV4.uriEncode] so the two match. [headers] must hold
 * `Host`; every header given is signed. [payloadHash] is the lowercase hex SHA-256 of the body.
 */
class SigV4Request(
    val method: String,
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val headers: Map<String, String>,
    val payloadHash: String,
)

/**
 * AWS Signature Version 4 in its S3 form: the path is encoded once and never normalized (other AWS
 * services encode twice). Composed from cryptography-kotlin's SHA-256 and HMAC only.
 */
object SigV4 {
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val TERMINATOR = "aws4_request"
    private const val DATE_HEADER = "x-amz-date"

    /** The headers to add to [request]: `x-amz-date` and `Authorization`, both for [time]. */
    suspend fun sign(
        request: SigV4Request,
        credentials: S3Credentials,
        scope: SigV4Scope,
        time: Instant,
    ): Map<String, String> {
        val amzDate = amzDate(time)
        val canonical = canonicalRequest(request, amzDate)
        val toSign = stringToSign(canonical, scope, amzDate)
        val signature = hmacSha256(
            signingKey(credentials, scope, amzDate.take(DATE_LENGTH)),
            toSign.utf8(),
        ).toHexString()
        val authorization = "$ALGORITHM Credential=${credentials.accessKeyId}/${scopeString(scope, amzDate)}, " +
            "SignedHeaders=${signedHeaders(request, amzDate)}, Signature=$signature"
        return mapOf(DATE_HEADER to amzDate, "Authorization" to authorization)
    }

    /**
     * [value] percent-encoded the SigV4 way: everything but `A-Z a-z 0-9 - _ . ~` as uppercase
     * `%XX` of its UTF-8 bytes, and `/` too unless [keepSlash] (a path keeps it, a query value not).
     */
    fun uriEncode(value: String, keepSlash: Boolean): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val char = (byte.toInt() and BYTE_MASK).toChar()
            if (char.isUnreserved() || (keepSlash && char == '/')) {
                append(char)
            } else {
                append('%').append((byte.toInt() and BYTE_MASK).toString(HEX_RADIX).uppercase().padStart(2, '0'))
            }
        }
    }

    internal fun canonicalRequest(request: SigV4Request, amzDate: String): String {
        val headers = canonicalHeaders(request, amzDate)
        return listOf(
            request.method,
            uriEncode(request.path, keepSlash = true),
            canonicalQuery(request.query),
            headers.joinToString("") { (name, value) -> "$name:$value\n" },
            headers.joinToString(";") { it.first },
            request.payloadHash,
        ).joinToString("\n")
    }

    internal suspend fun stringToSign(canonicalRequest: String, scope: SigV4Scope, amzDate: String): String =
        listOf(ALGORITHM, amzDate, scopeString(scope, amzDate), sha256(canonicalRequest.utf8()).toHexString())
            .joinToString("\n")

    private fun canonicalQuery(query: List<Pair<String, String>>): String = query
        .map { (key, value) -> uriEncode(key, keepSlash = false) to uriEncode(value, keepSlash = false) }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .joinToString("&") { (key, value) -> "$key=$value" }

    private fun canonicalHeaders(request: SigV4Request, amzDate: String): List<Pair<String, String>> =
        (request.headers.entries.map { (name, value) -> name.lowercase() to value } + (DATE_HEADER to amzDate))
            .map { (name, value) -> name to value.trim().replace(SPACES, " ") }
            .sortedBy { it.first }

    private fun signedHeaders(request: SigV4Request, amzDate: String): String =
        canonicalHeaders(request, amzDate).joinToString(";") { it.first }

    private fun scopeString(scope: SigV4Scope, amzDate: String): String =
        "${amzDate.take(DATE_LENGTH)}/${scope.region}/${scope.service}/$TERMINATOR"

    private suspend fun signingKey(credentials: S3Credentials, scope: SigV4Scope, date: String): ByteArray {
        val dateKey = hmacSha256("AWS4${credentials.secretAccessKey}".utf8(), date.utf8())
        val regionKey = hmacSha256(dateKey, scope.region.utf8())
        val serviceKey = hmacSha256(regionKey, scope.service.utf8())
        return hmacSha256(serviceKey, TERMINATOR.utf8())
    }

    /** [time] as `yyyyMMdd'T'HHmmss'Z'` in UTC, the form SigV4 signs. */
    private fun amzDate(time: Instant): String {
        val utc = time.toLocalDateTime(TimeZone.UTC)
        return "${utc.year.pad(YEAR_DIGITS)}${utc.month.ordinal.plus(1).pad()}${utc.day.pad()}" +
            "T${utc.hour.pad()}${utc.minute.pad()}${utc.second.pad()}Z"
    }

    private val SPACES = Regex(" {2,}")
    private const val DATE_LENGTH = 8
    private const val YEAR_DIGITS = 4
    private const val BYTE_MASK = 0xFF
    private const val HEX_RADIX = 16
}

private fun Char.isUnreserved(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' || this == '_' || this == '.' || this == '~'

private fun Int.pad(digits: Int = 2): String = toString().padStart(digits, '0')

private fun String.utf8(): ByteArray = encodeToByteArray()
