package com.msuite.core.sync.testing

import com.msuite.core.model.Batch
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.decodeOrNull
import com.msuite.core.sync.BackendPlug
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

/**
 * A [SimStore] over a real [BackendPlug] and the real codec (decision 0057): Layer 2 pushes encoded
 * bytes through the plug and reads them back, so the contract and `decodeOrNull` are exercised.
 * Keys are the batch's SHA-256, like the content-addressed keys the live plugs will use (decision
 * 0029): a repeated put carries the same bytes, and two stores over one plug can't clash and drop
 * each other's batches. The order of `list` is no plug's contract, so keys come back sorted.
 */
class PlugStore(private val plug: BackendPlug) : SimStore {
    override fun put(batch: Batch): String {
        val bytes = ProtoBatchEncoder.encode(batch)
        val key = PREFIX + bytes.sha256()
        runBlocking { plug.put(key, bytes) }
        return key
    }

    override fun keys(): List<String> = runBlocking { plug.list(PREFIX) }.sorted()

    override fun get(key: String): Batch? = runBlocking { plug.get(key)?.let { ProtoBatchEncoder.decodeOrNull(it) } }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { it.toUByte().toString(HEX_RADIX).padStart(HEX_DIGITS_PER_BYTE, '0') }

    private companion object {
        const val PREFIX = "sim/"
        const val HEX_RADIX = 16
        const val HEX_DIGITS_PER_BYTE = 2
    }
}
