package com.msuite.core.sync

import com.msuite.core.model.PlugId
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.decodeOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Live sync: observes replica changes, debounces, and publishes deltas. Also pulls changes from
 * the backend. Coarse batching (decision 0002): waits [idlePeriod] of idle before publishing, so
 * a burst of edits becomes one batch.
 */
@OptIn(FlowPreview::class)
class LiveSync(
    private val replica: Replica,
    private val plug: BackendPlug,
    private val plugId: PlugId,
    private val appId: String,
    private val root: ByteArray,
    private val scope: CoroutineScope,
    private val idlePeriod: Duration = 10.seconds,
) {
    private var groupId: String? = null
    private var publishJob: Job? = null

    suspend fun start() {
        groupId = groupId(root)
        publishJob = scope.launch {
            replica.changes
                .debounce(idlePeriod)
                .collect { publish() }
        }
    }

    fun stop() {
        publishJob?.cancel()
        publishJob = null
    }

    /**
     * Immediately publishes any pending changes, bypassing the debounce. Called when the app goes
     * to background to ensure edits are synced before suspension.
     */
    suspend fun flush() {
        publish()
    }

    suspend fun pull() {
        val gid = groupId ?: return
        val prefix = "$appId/$gid/"
        val keys = plug.list(prefix)
        for (key in keys) {
            if (replica.isCorrupt(plugId, key)) continue
            val bytes = plug.get(key)
            if (bytes != null) {
                val batch = ProtoBatchEncoder.decodeOrNull(bytes)
                if (batch != null) {
                    replica.ingest(plugId, key, batch)
                } else {
                    replica.unreadable(plugId, key)
                }
            }
        }
        // Prune bookkeeping: remove entries for keys no longer in the backend (decision 0002).
        replica.prune(plugId, keys.toSet())
    }

    private suspend fun publish() {
        val gid = groupId ?: return
        val delta = replica.delta(plugId)
        if (delta == null || delta.cells.isEmpty()) return

        val bytes = ProtoBatchEncoder.encode(delta)
        val hash = ciphertextHash(bytes)
        val prefix = ObjectKeyPrefix(
            appId = appId,
            groupId = gid,
            type = BatchType.Delta,
            deviceId = delta.cells.first().stamp.deviceId,
            hlcLo = delta.cells.minOf { it.stamp.hlc },
            hlcHi = delta.cells.maxOf { it.stamp.hlc },
        )
        val key = prefix.withHash(hash).text()
        plug.put(key, bytes)
        replica.pushed(plugId, delta)
    }
}
