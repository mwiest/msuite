package com.msuite.todo

import com.msuite.todo.domain.RootStore

/** The root at rest (decision 0039): wrapped under the KeyStore in its own file. */
class KeyStoreRootStore(private val sealed: KeyStoreSealedFile) : RootStore {
    override fun load(): ByteArray? = sealed.read()

    override fun save(root: ByteArray) = sealed.write(root)
}
