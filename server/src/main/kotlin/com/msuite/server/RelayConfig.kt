package com.msuite.server

import com.msuite.server.storage.BlobLimits
import java.nio.file.Path

/**
 * The relay's programmatic configuration (decision 0017), read once from the environment in
 * [main]. The port falls back to the `PORT` that PaaS hosts inject (decision 0081). Sizes default
 * to 16 MiB per object and 1 GiB per namespace; an allowlist limits who may register a namespace
 * (decision 0030), not who may use one already registered.
 */
data class RelayConfig(
    val port: Int = DEFAULT_PORT,
    val dataDir: Path = Path.of(DEFAULT_DATA_DIR),
    val allowedGroups: Set<String>? = null,
    val maxObjectBytes: Long = DEFAULT_MAX_OBJECT_BYTES,
    val maxNamespaceBytes: Long = DEFAULT_MAX_NAMESPACE_BYTES,
) {
    val limits: BlobLimits get() = BlobLimits(maxObjectBytes, maxNamespaceBytes)

    /** Whether a new namespace for [groupId] may be registered; a registered one is always usable. */
    fun mayRegister(groupId: String): Boolean = allowedGroups == null || groupId in allowedGroups

    companion object {
        const val PORT = "MSUITE_RELAY_PORT"
        const val HOST_PORT = "PORT"
        const val DATA = "MSUITE_RELAY_DATA"
        const val ALLOW_GROUPS = "MSUITE_RELAY_ALLOW_GROUPS"
        const val MAX_OBJECT_BYTES = "MSUITE_RELAY_MAX_OBJECT_BYTES"
        const val MAX_NAMESPACE_BYTES = "MSUITE_RELAY_MAX_NAMESPACE_BYTES"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_DATA_DIR = "relay-data"
        const val DEFAULT_MAX_OBJECT_BYTES = 16L * 1024 * 1024
        const val DEFAULT_MAX_NAMESPACE_BYTES = 1024L * 1024 * 1024

        /** Reads [env], throwing [IllegalArgumentException] with the offending variable on bad input. */
        fun fromEnv(env: Map<String, String> = System.getenv()): RelayConfig = RelayConfig(
            port = env.int(PORT, env.int(HOST_PORT, DEFAULT_PORT)).also {
                require(it in 0..MAX_PORT) { "The port must be 0..$MAX_PORT, got $it" }
            },
            dataDir = Path.of(env[DATA] ?: DEFAULT_DATA_DIR),
            allowedGroups = env[ALLOW_GROUPS]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet(),
            maxObjectBytes = env.long(MAX_OBJECT_BYTES, DEFAULT_MAX_OBJECT_BYTES).also {
                require(it > 0) { "$MAX_OBJECT_BYTES must be positive, got $it" }
            },
            maxNamespaceBytes = env.long(MAX_NAMESPACE_BYTES, DEFAULT_MAX_NAMESPACE_BYTES).also {
                require(it > 0) { "$MAX_NAMESPACE_BYTES must be positive, got $it" }
            },
        )

        private const val MAX_PORT = 65_535

        private fun Map<String, String>.int(name: String, default: Int): Int =
            this[name]?.let { requireNotNull(it.toIntOrNull()) { "$name is not a number" } } ?: default

        private fun Map<String, String>.long(name: String, default: Long): Long =
            this[name]?.let { requireNotNull(it.toLongOrNull()) { "$name is not a number" } } ?: default
    }
}
