package com.msuite.todo

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.msuite.todo.domain.RootStore
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The root at rest (decision 0039), plain JCA: a non-exportable AES-256-GCM key in the Android
 * KeyStore wraps the root, and the wrapped bytes (IV length, IV, ciphertext) go to a private file.
 * A device test checks that the root survives a restart; there is no JVM equivalent.
 */
class KeyStoreRootStore(private val file: File) : RootStore {
    override fun load(): ByteArray? {
        if (!file.exists()) return null
        return try {
            unwrap(file.readBytes())
        } catch (ignored: IOException) {
            null
        } catch (ignored: GeneralSecurityException) {
            null
        }
    }

    override fun save(root: ByteArray) {
        file.writeBytes(wrap(root))
    }

    private fun wrap(root: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(root)
    }

    private fun unwrap(wrapped: ByteArray): ByteArray {
        if (wrapped.isEmpty()) throw GeneralSecurityException("empty wrapped root")
        val ivSize = wrapped[IV_SIZE_INDEX].toInt()
        val iv = wrapped.copyOfRange(IV_SIZE_INDEX + 1, IV_SIZE_INDEX + 1 + ivSize)
        val ciphertext = wrapped.copyOfRange(IV_SIZE_INDEX + 1 + ivSize, wrapped.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /** The wrapping key, created on first use: non-exportable, AES-256-GCM (decision 0039). */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "msuite-root"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_SIZE_INDEX = 0
    }
}
