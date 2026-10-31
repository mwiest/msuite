package com.msuite.core.relay

import com.msuite.core.crypto.hkdf
import com.msuite.core.sync.ObjectKey
import com.msuite.core.sync.encodeBase32Hex

/** HKDF info prefix that binds the relay bearer token to an app (decision 0030). */
const val RELAY_AUTH_INFO_PREFIX = "msuite-relay-auth-v1:"

/**
 * The bearer token for one namespace: `HKDF-SHA256(root, "msuite-relay-auth-v1:" + appId)`, sent
 * as uppercase base32hex. It is derived on demand and never stored (decision 0030).
 */
suspend fun relayToken(root: ByteArray, appId: String): String {
    require(isValidAppId(appId)) { "not an app id: $appId" }
    return encodeBase32Hex(hkdf(root, ByteArray(0), RELAY_AUTH_INFO_PREFIX + appId))
}

/** One key or namespace as the relay's paths see it (decision 0029). A null [name] is a listing. */
data class RelayPath(val appId: String, val groupId: String, val name: String? = null) {
    /** `{appId}/{groupId}/`, the prefix a client lists. */
    val namespace: String get() = "$appId/$groupId/"

    /** `{appId}/{groupId}/{name}`; meaningful only when [name] is present. */
    val key: String get() = namespace + name.orEmpty()
}

/** The HTTP wire contract of the relay: paths, headers, status codes and name rules. */
object RelayApi {
    const val VERSION = "v1"
    const val API_PREFIX = "/$VERSION/"
    const val HEALTH_PATH = "/health"
    const val AUTHORIZATION_HEADER = "Authorization"
    const val BEARER_PREFIX = "Bearer "
    const val LISTING_CONTENT_TYPE = "text/plain"

    /** The value of [AUTHORIZATION_HEADER] for [token]. */
    fun authorization(token: String): String = BEARER_PREFIX + token

    /** The token in an [AUTHORIZATION_HEADER] value, or `null` if it isn't a bearer value. */
    fun tokenFromAuthorization(header: String?): String? =
        header?.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)

    /** `/v1/{appId}/{groupId}/{name}` for a well-formed [key]. */
    fun keyPath(key: String): String {
        require(isValidKey(key)) { "not a relay object key: $key" }
        return API_PREFIX + key
    }

    /** `/v1/{appId}/{groupId}/` for a well-formed [namespace] (`{appId}/{groupId}` with optional slash). */
    fun listingPath(namespace: String): String {
        val canonical = namespace.trimEnd('/')
        require(isValidNamespace(canonical)) { "not a relay namespace: $namespace" }
        return API_PREFIX + canonical + "/"
    }

    /**
     * Parses a relay path into its key or namespace, or `null` when it isn't one. A listing accepts
     * both `/v1/{app}/{group}` and `/v1/{app}/{group}/`; objects require the full 0029 grammar.
     */
    fun parsePath(path: String): RelayPath? = path
        .takeIf { it.startsWith(API_PREFIX) }
        ?.removePrefix(API_PREFIX)
        ?.removeSuffix("/")
        ?.takeIf { it.isNotEmpty() }
        ?.let { body ->
            val parts = body.split('/')
            when (parts.size) {
                NAMESPACE_SEGMENTS -> if (isValidNamespace(body)) RelayPath(parts[0], parts[1]) else null
                KEY_SEGMENTS -> if (isValidKey(body)) RelayPath(parts[0], parts[1], parts[2]) else null
                else -> null
            }
        }

    /**
     * The full keys for a listing body (`text/plain`, one name per line), or throws when a line
     * isn't a well-formed object name. Blank lines, including a trailing one, are ignored.
     */
    fun parseListing(namespace: String, body: String): List<String> {
        val canonical = namespace.trimEnd('/')
        require(isValidNamespace(canonical)) { "not a relay namespace: $namespace" }
        val prefix = "$canonical/"
        return body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { name ->
                require(isValidKey(prefix + name)) { "not a relay object name: $name" }
                prefix + name
            }
            .toList()
    }

    /** Whether [key] is a complete object key with the safe app/group segments and the 0029 grammar. */
    fun isValidKey(key: String): Boolean {
        val parts = key.split('/')
        if (parts.size != KEY_SEGMENTS || !isValidAppId(parts[0]) || !isValidGroupId(parts[1])) return false
        return runCatching { ObjectKey.parse(key) }.isSuccess
    }

    private fun isValidNamespace(namespace: String): Boolean {
        val parts = namespace.split('/')
        return parts.size == NAMESPACE_SEGMENTS && isValidAppId(parts[0]) && isValidGroupId(parts[1])
    }

    private const val KEY_SEGMENTS = 3
    private const val NAMESPACE_SEGMENTS = 2
}

private const val GROUP_ID_CHARS = 26
private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz0123456789"
private const val GROUP_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUV"

private fun isValidAppId(appId: String): Boolean = appId.isNotEmpty() && appId.all { it in LOWERCASE }

private fun isValidGroupId(groupId: String): Boolean =
    groupId.length == GROUP_ID_CHARS && groupId.all { it in GROUP_CHARS }

/** The status codes of the relay's wire contract (plan v1 step 9). */
object RelayStatus {
    /** A new object was stored. */
    const val CREATED = 201

    /** An object was read, or a create-only put found the key already present. */
    const val OK = 200

    /** A malformed name, or a body whose SHA-256 doesn't match the key. */
    const val BAD_REQUEST = 400

    /** A missing or wrong bearer token. */
    const val UNAUTHORIZED = 401

    /** Registration of a new namespace is not allowed. */
    const val FORBIDDEN = 403

    /** The object or namespace is absent. */
    const val NOT_FOUND = 404

    /** The object exceeds the relay's per-object limit. */
    const val TOO_LARGE = 413

    /** The namespace exceeds the relay's size limit. */
    const val INSUFFICIENT_STORAGE = 507
}
