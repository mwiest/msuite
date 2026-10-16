package com.msuite.core.sync.testing

import com.msuite.core.model.Batch
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.decodeOrNull
import com.msuite.core.sync.BackendPlug
import kotlinx.coroutines.runBlocking

/**
 * A [SimStore] over a real [BackendPlug] and the real codec (decision 0057): Layer 2 pushes encoded
 * bytes through the plug and reads them back, so the contract and `decodeOrNull` are exercised.
 * Keys are fixed-width so they keep publish order.
 */
class PlugStore(private val plug: BackendPlug) : SimStore {
    private var next = 0

    override fun put(batch: Batch): String {
        val key = PREFIX + next++.toString().padStart(WIDTH, '0')
        runBlocking { plug.put(key, ProtoBatchEncoder.encode(batch)) }
        return key
    }

    override fun keys(): List<String> = runBlocking { plug.list(PREFIX) }

    override fun get(key: String): Batch? = runBlocking { plug.get(key)?.let { ProtoBatchEncoder.decodeOrNull(it) } }

    private companion object {
        const val PREFIX = "sim/"
        const val WIDTH = 6
    }
}
