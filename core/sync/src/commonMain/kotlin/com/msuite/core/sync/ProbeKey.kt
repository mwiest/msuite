package com.msuite.core.sync

import kotlin.random.Random

/**
 * The one name besides the 0029 keys a backend holds: `{appId}/{groupId}/probe_{32 hex}`, written
 * and deleted to check a backend's rights before it is chosen (decision 0086). It never parses as
 * an [ObjectKey], so sync skips it, and the relay takes any body under it.
 */
object ProbeKey {
    private const val PREFIX = "probe_"
    private const val BYTES = 16
    private const val HEX = "0123456789abcdef"

    /** A fresh probe key in the namespace of [appId] and [groupId]. */
    fun random(appId: String, groupId: String): String =
        "$appId/$groupId/$PREFIX" + Random.nextBytes(BYTES).toHexString()

    /** Whether [name], the part after the namespace, is a probe name. */
    fun isProbeName(name: String): Boolean = name.length == PREFIX.length + 2 * BYTES && name.startsWith(PREFIX) &&
        name.drop(PREFIX.length).all { it in HEX }
}
