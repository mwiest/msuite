package com.msuite.core.sync.testing

import com.msuite.core.crypto.BatchEnvelope
import com.msuite.core.crypto.EnvelopeResult
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.Batch
import com.msuite.core.model.BatchEncoder
import com.msuite.core.model.MalformedBatch
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.sync.BackendPlug
import com.msuite.core.sync.BatchType
import com.msuite.core.sync.ObjectKey
import com.msuite.core.sync.ObjectKeyPrefix
import com.msuite.core.sync.ciphertextHash
import com.msuite.core.sync.groupId
import kotlinx.coroutines.runBlocking

/**
 * Layer 3 (decisions 0057, 0029; docs/testing.md): a [SimStore] over a real [BackendPlug] and the
 * batch envelope, with the real object keys. The hash is over the ciphertext and the AAD context is
 * the key minus its hash (0071), so a renamed, moved or foreign-root object fails to open. The
 * ciphertext of a batch is cached, so a re-publish is the same object under the same key.
 */
class EnvelopePlugStore(
    private val plug: BackendPlug,
    private val appId: String,
    roots: (Int) -> ByteArray?,
    keyGen: Int = KEY_GEN_V1,
    private val type: BatchType = BatchType.Snapshot,
    private val encoder: BatchEncoder = ProtoBatchEncoder,
) : SimStore {
    private val envelope = BatchEnvelope(encoder, roots, keyGen)
    private val namespaceId = runBlocking { groupId(requireNotNull(roots(keyGen))) }
    private val namespace = "$appId/$namespaceId/"
    private val published = mutableMapOf<CacheKey, String>()

    override fun put(batch: Batch): String {
        val prefix = prefixFor(batch)
        val cacheKey = CacheKey(prefix.text(), encoder.encode(batch).toList())
        published[cacheKey]?.let { return it }
        return runBlocking {
            val bytes = envelope.seal(batch, prefix.text())
            val key = prefix.withHash(ciphertextHash(bytes)).text()
            plug.put(key, bytes)
            published[cacheKey] = key
            key
        }
    }

    override fun keys(): List<String> = runBlocking { plug.list(namespace) }.sorted()

    override fun get(key: String): Fetch? {
        val bytes = runBlocking { plug.get(key) } ?: return null
        return try {
            when (val result = runBlocking { envelope.open(bytes, ObjectKey.parse(key).prefix.text()) }) {
                is EnvelopeResult.Opened -> Fetch.Read(result.batch)
                EnvelopeResult.TooNew, EnvelopeResult.UnknownKeyGen -> Fetch.Skip
            }
        } catch (ignored: MalformedBatch) {
            Fetch.Unreadable
        } catch (ignored: IllegalArgumentException) {
            Fetch.Unreadable
        }
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

    private data class CacheKey(val prefix: String, val body: List<Byte>)
}
