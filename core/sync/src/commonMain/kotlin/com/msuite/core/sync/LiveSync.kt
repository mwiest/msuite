package com.msuite.core.sync

import co.touchlab.kermit.Logger
import com.msuite.core.crypto.BatchEnvelope
import com.msuite.core.crypto.EnvelopeResult
import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.MalformedBatch
import com.msuite.core.model.PlugId
import com.msuite.core.model.ProtoBatchEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Live sync: observes replica changes, debounces, and publishes deltas. Also pulls changes from
 * the backend. Coarse batching (decision 0002): waits [idlePeriod] of idle before publishing, so
 * a burst of edits becomes one batch. Every object is a [BatchEnvelope] sealed under its key
 * minus the hash (decision 0029), so the backend only ever holds ciphertext. A plug that fails
 * (an unreachable folder) never throws out of here: it turns [healthy] false, and a failed
 * publish is retried after the next successful pull.
 */
@OptIn(FlowPreview::class)
class LiveSync(
    private val replica: Replica,
    private val plug: BackendPlug,
    private val plugId: PlugId,
    private val appId: String,
    private val identity: IdentityProvider,
    private val scope: CoroutineScope,
    private val idlePeriod: Duration = 10.seconds,
) {
    private val envelope = BatchEnvelope(ProtoBatchEncoder, identity::root, KEY_GEN_V1)
    private var groupId: String? = null
    private var publishJob: Job? = null
    private val log = Logger.withTag("LiveSync")
    private val publishLock = Mutex()
    private var retryPublish = false
    private val _healthy = MutableStateFlow(true)

    /** Whether the last plug operation succeeded. */
    val healthy: StateFlow<Boolean> = _healthy.asStateFlow()

    suspend fun start() {
        groupId = groupId(requireNotNull(identity.root(KEY_GEN_V1)) { "no root for keyGen $KEY_GEN_V1" })
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
        if (guarded("pull") { pullKeys() } && retryPublish) publish()
    }

    private suspend fun pullKeys() {
        val gid = groupId ?: return
        val prefix = "$appId/$gid/"
        val keys = plug.list(prefix)
        val parsed = keys.associateWith { runCatching { ObjectKey.parse(it) }.getOrNull() }
        for ((key, objectKey) in parsed) {
            if (objectKey != null) pullOne(key, objectKey)
        }
        sweepConflictCopies(parsed.filterValues { it != null }.keys, parsed.filterValues { it == null }.keys)
        // Prune bookkeeping: remove entries for keys no longer in the backend (decision 0002).
        replica.prune(plugId, keys.toSet())
    }

    /**
     * Deletes a sync client's conflict copies (`key (N)`, `key_<digits>`) of a key that is still
     * listed; Dropbox makes them seconds after some writes. Copies of an absent key are kept.
     */
    private suspend fun sweepConflictCopies(keys: Set<String>, others: Set<String>) {
        for (name in others) {
            val original = CONFLICT_COPY.matchEntire(name)?.groupValues?.get(1)
            if (original != null && original in keys) plug.delete(name)
        }
    }

    private companion object {
        val CONFLICT_COPY = Regex("""^(.+)(?: \(\d+\)|_\d+)$""")
    }

    private suspend fun pullOne(key: String, parsed: ObjectKey) {
        if (replica.isCorrupt(plugId, key)) return
        val bytes = plug.get(key) ?: return
        try {
            val opened = envelope.open(bytes, parsed.prefix.text())
            if (opened is EnvelopeResult.Opened) replica.ingest(plugId, key, opened.batch)
        } catch (_: MalformedBatch) {
            replica.unreadable(plugId, key)
        }
    }

    private suspend fun publish() = publishLock.withLock {
        retryPublish = !guarded("publish") { publishDelta() }
    }

    // Plugs throw platform exceptions (IOException, SecurityException), so catch broadly.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun guarded(operation: String, block: suspend () -> Unit): Boolean {
        val ok = try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "$operation failed, retrying later" }
            false
        }
        _healthy.value = ok
        return ok
    }

    private suspend fun publishDelta() {
        val gid = groupId ?: return
        val delta = replica.delta(plugId)
        if (delta == null || delta.cells.isEmpty()) return

        val prefix = ObjectKeyPrefix(
            appId = appId,
            groupId = gid,
            type = BatchType.Delta,
            deviceId = replica.deviceId,
            hlcLo = delta.cells.minOf { it.stamp.hlc },
            hlcHi = delta.cells.maxOf { it.stamp.hlc },
        )
        val bytes = envelope.seal(delta, prefix.text())
        val key = prefix.withHash(ciphertextHash(bytes)).text()
        plug.put(key, bytes)
        replica.pushed(plugId, delta)
    }
}
