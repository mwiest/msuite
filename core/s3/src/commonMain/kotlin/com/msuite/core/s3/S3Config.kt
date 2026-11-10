package com.msuite.core.s3

import io.ktor.http.Url

/**
 * Where the objects live: an S3-compatible [endpoint] (`https://host[:port]`, no path), its
 * [region] (`auto` on R2), the [bucket], and an optional [prefix] the plug puts in front of every
 * key. Not secret; the credentials travel separately.
 */
data class S3Config(val endpoint: String, val region: String, val bucket: String, val prefix: String = "") {
    private val url = Url(endpoint)

    init {
        require(url.protocol.name == "https" || url.protocol.name == "http") { "the endpoint must be http(s)" }
        require(url.host.isNotEmpty()) { "the endpoint needs a host" }
        require(url.encodedPath.isEmpty() || url.encodedPath == "/") { "the endpoint must not have a path" }
        require(region.isNotBlank()) { "the region must not be blank" }
        require(bucket.isNotBlank() && bucket.none { it == '/' || it.isWhitespace() }) { "not a bucket name: $bucket" }
    }

    /** [prefix] without stray slashes, ending in `/` unless empty. */
    val keyPrefix: String = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }

    /** AWS wants the bucket in the host name; every other store takes it in the path (plan decision). */
    internal val virtualHosted: Boolean = url.host.endsWith(".amazonaws.com")

    /** The `Host` header value, as the HTTP client sends it: the port only when not the default. */
    internal val host: String = run {
        val name = if (virtualHosted) "$bucket.${url.host}" else url.host
        val port = url.specifiedPort
        if (port == 0 || port == url.protocol.defaultPort) name else "$name:$port"
    }

    internal val scheme: String = url.protocol.name

    /** The raw path of [key] (already prefixed); `""` addresses the bucket itself. */
    internal fun path(key: String): String = when {
        virtualHosted -> "/$key"
        key.isEmpty() -> "/$bucket"
        else -> "/$bucket/$key"
    }
}
