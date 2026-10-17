package com.msuite.core.crypto

/**
 * The group's root secrets, by key generation (decisions 0008, 0039). The app layer implements this
 * — the root is generated on first use and wrapped at rest — and `core` only reads from it.
 */
fun interface IdentityProvider {
    /** The root for [keyGen], or `null` when this device holds none for that generation. */
    fun root(keyGen: Int): ByteArray?
}
