package com.msuite.core.s3

import com.msuite.core.crypto.sha256
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.decodeURLPart
import kotlin.time.Instant

/** How the fake treats `If-None-Match: *` on a `PUT`. */
enum class Conditional { Supported, Rejects501, Rejects400 }

/**
 * An in-memory S3 bucket behind Ktor's [MockEngine]. Like a real server it rebuilds the canonical
 * request from what it received, so a URL encoded differently from what was signed fails with
 * `SignatureDoesNotMatch`. [pageSize] caps one listing page, to exercise continuation tokens.
 */
// Early returns mirror a server rejecting a request at the first failed check.
@Suppress("ReturnCount")
class FakeS3(
    private val credentials: S3Credentials,
    private val region: String,
    private val bucket: String,
    private val virtualHostedHost: String? = null,
    private val pageSize: Int = 1000,
    private val conditional: Conditional = Conditional.Supported,
) {
    val objects = sortedMapOf<String, ByteArray>()

    /** Every request seen, as `METHOD encodedPath?query` plus whether it sent `If-None-Match`. */
    val requests = mutableListOf<Pair<String, Boolean>>()

    val client = HttpClient(MockEngine { handle(it) }) { expectSuccess = false }

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val ifNoneMatch = request.headers["If-None-Match"]
        requests += "${request.method.value} ${request.url.encodedPathAndQuery}" to (ifNoneMatch != null)
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes() ?: ByteArray(0)
        signatureError(request, body)?.let { return error(HttpStatusCode.Forbidden, it) }
        val key = objectKey(request) ?: return error(HttpStatusCode.NotFound, "NoSuchBucket")
        return when (request.method) {
            HttpMethod.Put -> put(key, body, ifNoneMatch)

            HttpMethod.Get -> if (key.isEmpty()) list(request) else get(key)

            HttpMethod.Delete -> {
                objects.remove(key)
                respond(ByteArray(0), HttpStatusCode.NoContent)
            }

            else -> error(HttpStatusCode.MethodNotAllowed, "MethodNotAllowed")
        }
    }

    private fun MockRequestHandleScope.put(key: String, body: ByteArray, ifNoneMatch: String?): HttpResponseData {
        if (ifNoneMatch != null) {
            when (conditional) {
                Conditional.Rejects501 -> return error(HttpStatusCode.NotImplemented, "NotImplemented")

                Conditional.Rejects400 -> return error(HttpStatusCode.BadRequest, "InvalidArgument")

                Conditional.Supported -> if (key in objects) {
                    return error(HttpStatusCode.PreconditionFailed, "PreconditionFailed")
                }
            }
        }
        objects[key] = body.copyOf()
        return respond(ByteArray(0), HttpStatusCode.OK)
    }

    private fun MockRequestHandleScope.get(key: String): HttpResponseData =
        objects[key]?.let { respond(it.copyOf(), HttpStatusCode.OK) } ?: error(HttpStatusCode.NotFound, "NoSuchKey")

    private fun MockRequestHandleScope.list(request: HttpRequestData): HttpResponseData {
        val params = request.url.parameters
        if (params["list-type"] != "2") return error(HttpStatusCode.BadRequest, "InvalidArgument")
        val prefix = params["prefix"].orEmpty()
        val start = params["continuation-token"]?.toInt() ?: 0
        val matching = objects.keys.filter { it.startsWith(prefix) }
        val page = matching.drop(start).take(pageSize)
        val truncated = start + page.size < matching.size
        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">""")
            append("<Name>$bucket</Name><Prefix>${prefix.escaped()}</Prefix><KeyCount>${page.size}</KeyCount>")
            append("<MaxKeys>$pageSize</MaxKeys><IsTruncated>$truncated</IsTruncated>")
            page.forEach {
                append("<Contents><Key>${it.escaped()}</Key><Size>${objects.getValue(it).size}</Size></Contents>")
            }
            if (truncated) append("<NextContinuationToken>${start + page.size}</NextContinuationToken>")
            append("</ListBucketResult>")
        }
        return respond(xml, HttpStatusCode.OK)
    }

    /** The object key the request addresses: `""` for the bucket itself, `null` for another bucket. */
    private fun objectKey(request: HttpRequestData): String? {
        val path = request.url.encodedPath.decodeURLPart()
        return if (virtualHostedHost != null) {
            path.removePrefix("/").takeIf { request.url.host == "$bucket.$virtualHostedHost" }
        } else {
            val rest = path.removePrefix("/")
            when {
                rest == bucket -> ""
                rest.startsWith("$bucket/") -> rest.removePrefix("$bucket/")
                else -> null
            }
        }
    }

    /** The S3 error code if the request isn't signed by [credentials] exactly as received. */
    private suspend fun signatureError(request: HttpRequestData, body: ByteArray): String? {
        val authorization = request.headers["Authorization"] ?: return "AccessDenied"
        val accessKey = authorization.substringAfter("Credential=").substringBefore('/')
        if (accessKey != credentials.accessKeyId) return "InvalidAccessKeyId"
        if (request.headers["x-amz-content-sha256"] != sha256(body).toHexString()) return "XAmzContentSHA256Mismatch"
        val encodedPath = request.url.encodedPath
        val path = encodedPath.decodeURLPart()
        val query = request.url.parameters.entries().flatMap { (key, values) -> values.map { key to it } }
        // A server signs the URI it received: the plug must send exactly the SigV4 encoding.
        if (encodedPath != SigV4.uriEncode(path, keepSlash = true)) return "SignatureDoesNotMatch"
        val rawQuery = request.url.encodedQuery.split('&').filter { it.isNotEmpty() }.toSet()
        val expectedQuery = query.map { (k, v) -> "${SigV4.uriEncode(k, false)}=${SigV4.uriEncode(v, false)}" }.toSet()
        if (rawQuery != expectedQuery) return "SignatureDoesNotMatch"

        val signedNames = authorization.substringAfter("SignedHeaders=").substringBefore(',').split(';')
        val host = request.url.hostWithPortIfSpecified()
        val headers = signedNames.filter { it != "x-amz-date" }.associateWith { name ->
            if (name == "host") host else request.headers[name].orEmpty()
        }
        val amzDate = request.headers["x-amz-date"] ?: return "AccessDenied"
        val time = Instant.parse(
            "${amzDate.substring(0, 4)}-${amzDate.substring(4, 6)}-${amzDate.substring(6, 8)}T" +
                "${amzDate.substring(9, 11)}:${amzDate.substring(11, 13)}:${amzDate.substring(13, 15)}Z",
        )
        val expected = SigV4.sign(
            SigV4Request(request.method.value, path, query, headers, sha256(body).toHexString()),
            credentials,
            SigV4Scope(region),
            time,
        )["Authorization"]
        return if (expected == authorization) null else "SignatureDoesNotMatch"
    }

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String): HttpResponseData = respond(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>$code</Code><Message>m</Message></Error>",
        status,
    )
}

private fun io.ktor.http.Url.hostWithPortIfSpecified(): String =
    if (specifiedPort == 0 || specifiedPort == protocol.defaultPort) host else "$host:$specifiedPort"

private fun String.escaped(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
