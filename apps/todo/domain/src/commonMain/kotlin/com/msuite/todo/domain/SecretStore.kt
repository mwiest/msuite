package com.msuite.todo.domain

/**
 * Where a backend credential is kept at rest: the S3 secret (decision 0087). Like the root
 * (decision 0039), the shell wraps it with the Android KeyStore; it never goes into [TodoSettings].
 */
interface SecretStore {
    /** The stored secret, or `null` if none is stored or it can no longer be unwrapped. */
    fun load(): String?

    /** Persists [secret], wrapped, replacing any stored one. */
    fun save(secret: String)

    /** Removes the stored secret; a no-op when there is none. */
    fun clear()
}
