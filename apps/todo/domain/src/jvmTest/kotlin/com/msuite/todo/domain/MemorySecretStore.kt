package com.msuite.todo.domain

/** An in-memory [SecretStore]: JVM tests keep the secret plain (the KeyStore is device-only). */
internal class MemorySecretStore(var stored: String? = null) : SecretStore {
    override fun load(): String? = stored

    override fun save(secret: String) {
        stored = secret
    }

    override fun clear() {
        stored = null
    }
}
