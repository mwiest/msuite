package com.msuite.todo

import com.msuite.todo.domain.SecretStore

/** The S3 secret at rest (decision 0087): wrapped under the KeyStore, like the root. */
class KeyStoreSecretStore(private val sealed: KeyStoreSealedFile) : SecretStore {
    override fun load(): String? = sealed.read()?.decodeToString()

    override fun save(secret: String) = sealed.write(secret.encodeToByteArray())

    override fun clear() = sealed.delete()
}
