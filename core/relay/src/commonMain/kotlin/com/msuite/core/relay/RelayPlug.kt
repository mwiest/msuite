package com.msuite.core.relay

import com.msuite.core.sync.BackendPlug
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.io.IOException

/** The relay refused a request for a reason the user can act on (auth, registration or limits). */
class RelayRefused(
    val reason: String,
    val status: Int,
    cause: Throwable? = null,
) : IOException("relay refused ($status): $reason", cause)

/**
 * The [BackendPlug] over the relay's HTTP API (decision 0079). [client] is built by the caller,
 * normally with an injected engine (OkHttp on Android); tests pass Ktor's in-process client. The
 * bearer token is derived from [root] on each request and never stored (decision 0030).
 */
class RelayPlug(
    private val baseUrl: String,
    private val appId: String,
    private val root: ByteArray,
    private val client: HttpClient,
) : BackendPlug {
    constructor(baseUrl: String, appId: String, root: ByteArray, engine: HttpClientEngine) : this(
        baseUrl = baseUrl,
        appId = appId,
        root = root,
        client = HttpClient(engine) { expectSuccess = false },
    )

    override suspend fun put(key: String, bytes: ByteArray) {
        val status = call {
            client.put(url(RelayApi.keyPath(key))) {
                authorize()
                contentType(ContentType.Application.OctetStream)
                setBody(bytes)
            }.status
        }
        if (status != HttpStatusCode.Created && status != HttpStatusCode.OK) throw failure(status)
    }

    override suspend fun list(prefix: String): List<String> {
        val response = call { client.get(url(RelayApi.listingPath(prefix))) { authorize() } }
        if (response.status != HttpStatusCode.OK) throw failure(response.status)
        return try {
            RelayApi.parseListing(prefix, response.bodyAsText())
        } catch (e: IllegalArgumentException) {
            throw IOException("relay returned a malformed listing", e)
        }
    }

    override suspend fun get(key: String): ByteArray? {
        val response = call { client.get(url(RelayApi.keyPath(key))) { authorize() } }
        return when (response.status) {
            HttpStatusCode.OK -> response.bodyAsBytes()
            HttpStatusCode.NotFound -> null
            else -> throw failure(response.status)
        }
    }

    override suspend fun delete(key: String) {
        val status = call { client.delete(url(RelayApi.keyPath(key))) { authorize() }.status }
        if (status != HttpStatusCode.OK && status != HttpStatusCode.NotFound) throw failure(status)
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(RelayApi.AUTHORIZATION_HEADER, RelayApi.authorization(relayToken(root, appId)))
    }

    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    // A client with expectSuccess = true throws before a status can be mapped; map it here too.
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        throw failure(e.response.status, e)
    }

    private fun failure(status: HttpStatusCode, cause: Throwable? = null): IOException = when (status.value) {
        RelayStatus.UNAUTHORIZED -> RelayRefused("unauthorized", status.value, cause)
        RelayStatus.FORBIDDEN -> RelayRefused("forbidden", status.value, cause)
        RelayStatus.TOO_LARGE -> RelayRefused("object too large", status.value, cause)
        RelayStatus.INSUFFICIENT_STORAGE -> RelayRefused("namespace full", status.value, cause)
        else -> IOException("relay answered ${status.value} ${status.description}", cause)
    }
}
