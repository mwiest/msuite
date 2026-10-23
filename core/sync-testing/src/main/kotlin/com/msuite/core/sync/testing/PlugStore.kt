package com.msuite.core.sync.testing

import com.msuite.core.model.Batch
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.decodeOrNull
import com.msuite.core.sync.BackendPlug
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.ObjectKeyPrefix
import com.msuite.core.sync.ciphertextHash
import com.msuite.core.sync.groupId
import kotlinx.coroutines.runBlocking

/**
 * A [SimStore] over a real [BackendPlug] and the real codec (decision 0057): Layer 2 pushes encoded
 * bytes through the plug and reads them back, so the contract and `decodeOrNull` are exercised.
 * Keys use the real object key grammar (decision 0029): `{appId}/{groupId}/v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}`,
 * so spans, types and pruning are exercised. The hash is over the encoded bytes.
 */
class PlugStore(
    private val plug: BackendPlug,
    private val appId: String,
    root: ByteArray,
    private val type: BatchType = BatchType.Snapshot,
) : SimStore {
    private val namespaceId = runBlocking { groupId(root) }
    private val namespace = "$appId/$namespaceId/"

    override fun put(batch: Batch): String = runBlocking {
        val bytes = ProtoBatchEncoder.encode(batch)
        val prefix = prefixFor(batch)
        val key = prefix.withHash(ciphertextHash(bytes)).text()
        plug.put(key, bytes)
        key
    }

    override fun keys(): List<String> = runBlocking { plug.list(namespace) }.sorted()

    override fun get(key: String): Fetch? = runBlocking {
        plug.get(key)?.let { bytes -> ProtoBatchEncoder.decodeOrNull(bytes)?.let(Fetch::Read) ?: Fetch.Unreadable }
    }

    override fun delete(key: String) = runBlocking {
        plug.delete(key)
    }

    private fun prefixFor(batch: Batch): ObjectKeyPrefix {
        val cells = batch.cells
        require(cells.isNotEmpty()) { "an empty batch has no writer or span" }
        return ObjectKeyPrefix(
            appId = appId,
            groupId = namespaceId,
            type = type,
            deviceId = cells.first().stamp.deviceId,
            hlcLo = cells.minOf { it.stamp.hlc },
            hlcHi = cells.maxOf { it.stamp.hlc },
        )
    }
}
