package com.msuite.core.s3

import com.msuite.core.crypto.sha256
import com.msuite.core.sync.BackendPlug
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import kotlinx.io.IOException
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/** Why the store refused a request, for a reason the user can act on; Settings maps each to a message. */
enum class S3Refusal { BadCredentials, Forbidden, NoSuchBucket, WrongRegion, ClockSkew }

/** The store refused a request: [code] is S3's error code, when the body carried one. */
class S3Refused(val refusal: S3Refusal, val status: Int, val code: String?) :
    IOException("S3 refused ($status ${code.orEmpty()}): $refusal")

/**
 * The [BackendPlug] over S3-compatible object storage (decision 0087): `PUT` with
 * `If-None-Match: *`, `GET`, `DELETE` and `ListObjectsV2`, each signed by [SigV4]. [client] is the
 * caller's (OkHttp on Android, Ktor's mock in tests); [clock] dates the signatures.
 */
class S3Plug(
    private val config: S3Config,
    private val credentials: S3Credentials,
    private val client: HttpClient,
    private val clock: Clock = Clock.System,
) : BackendPlug {
    // Cleared once a store refuses the header (B2), for this plug's lifetime: one sync session.
    // Content-addressed keys keep an overwrite harmless.
    @Volatile
    private var conditionalPuts = true

    override suspend fun put(key: String, bytes: ByteArray) {
        if (conditionalPuts) {
            val response = send(HttpMethod.Put, config.path(config.keyPrefix + key), body = bytes, ifNoneMatch = true)
            val status = response.status.value
            if (status in SUCCESS || status == PRECONDITION_FAILED) return
            // Only an unexplained 400 or a 501 may be the header itself; a known refusal is not.
            val failure = failure(response)
            if ((status != BAD_REQUEST && status != NOT_IMPLEMENTED) || failure is S3Refused) throw failure
        }
        val response = send(HttpMethod.Put, config.path(config.keyPrefix + key), body = bytes)
        if (response.status.value !in SUCCESS) throw failure(response)
        conditionalPuts = false
    }

    override suspend fun list(prefix: String): List<String> {
        val keys = mutableListOf<String>()
        var token: String? = null
        do {
            val query = listOf("list-type" to "2", "prefix" to config.keyPrefix + prefix) +
                listOfNotNull(token?.let { "continuation-token" to it })
            val response = send(HttpMethod.Get, config.path(""), query)
            if (response.status.value !in SUCCESS) throw failure(response)
            val page = try {
                S3Xml.listPage(response.bodyAsText())
            } catch (e: IllegalArgumentException) {
                throw IOException("S3 returned a malformed listing", e)
            }
            page.keys.mapTo(keys) { it.removePrefix(config.keyPrefix) }
            token = page.nextToken
        } while (token != null)
        return keys
    }

    override suspend fun get(key: String): ByteArray? {
        val response = send(HttpMethod.Get, config.path(config.keyPrefix + key))
        return when (response.status.value) {
            in SUCCESS -> response.bodyAsBytes()
            NOT_FOUND -> missingOrFailure(response)
            else -> throw failure(response)
        }
    }

    override suspend fun delete(key: String) {
        val response = send(HttpMethod.Delete, config.path(config.keyPrefix + key))
        when (response.status.value) {
            in SUCCESS -> Unit
            NOT_FOUND -> missingOrFailure(response)
            else -> throw failure(response)
        }
    }

    /** `null` for a 404 about the key; a missing bucket is a refusal, not an empty one. */
    private suspend fun missingOrFailure(response: HttpResponse): Nothing? {
        val code = errorCode(response)
        if (code == "NoSuchBucket") throw S3Refused(S3Refusal.NoSuchBucket, NOT_FOUND, code)
        return null
    }

    private suspend fun send(
        method: HttpMethod,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: ByteArray = ByteArray(0),
        ifNoneMatch: Boolean = false,
    ): HttpResponse {
        val signed = buildMap {
            put("Host", config.host)
            put("x-amz-content-sha256", sha256(body).toHexString())
            if (ifNoneMatch) put("If-None-Match", "*")
        }
        val auth = SigV4.sign(
            SigV4Request(method.value, path, query, signed, signed.getValue("x-amz-content-sha256")),
            credentials,
            SigV4Scope(config.region),
            clock.now(),
        )
        val url = "${config.scheme}://${config.host}${SigV4.uriEncode(path, keepSlash = true)}" +
            query.joinToString("&", prefix = if (query.isEmpty()) "" else "?") { (name, value) ->
                "${SigV4.uriEncode(name, keepSlash = false)}=${SigV4.uriEncode(value, keepSlash = false)}"
            }
        return try {
            client.request(url) {
                this.method = method
                (signed - "Host" + auth).forEach { (name, value) -> header(name, value) }
                if (method == HttpMethod.Put) setBody(ByteArrayContent(body, ContentType.Application.OctetStream))
            }
        } catch (e: ResponseException) {
            // A client with expectSuccess = true throws before a status can be mapped; map it here too.
            e.response
        }
    }

    private suspend fun failure(response: HttpResponse): IOException {
        val status = response.status.value
        val code = errorCode(response)
        val refusal = when {
            code == "RequestTimeTooSkewed" -> S3Refusal.ClockSkew

            code == "InvalidAccessKeyId" || code == "SignatureDoesNotMatch" || status == UNAUTHORIZED ->
                S3Refusal.BadCredentials

            code == "NoSuchBucket" -> S3Refusal.NoSuchBucket

            code in WRONG_REGION_CODES -> S3Refusal.WrongRegion

            status == FORBIDDEN -> S3Refusal.Forbidden

            else -> null
        }
        return refusal?.let { S3Refused(it, status, code) }
            ?: IOException("S3 answered $status${code?.let { " ($it)" }.orEmpty()}")
    }

    private suspend fun errorCode(response: HttpResponse): String? = try {
        S3Xml.errorCode(response.bodyAsText())
    } catch (ignored: IOException) {
        null
    }

    private companion object {
        val SUCCESS = 200..299
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val PRECONDITION_FAILED = 412
        const val NOT_IMPLEMENTED = 501

        /** Answers to a region that doesn't match the bucket or the endpoint: AWS's two, then R2's. */
        val WRONG_REGION_CODES = setOf("AuthorizationHeaderMalformed", "PermanentRedirect", "InvalidRegionName")
    }
}
