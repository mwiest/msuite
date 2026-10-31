package com.msuite.core.sync

import co.touchlab.kermit.Logger
import com.msuite.core.crypto.BatchEnvelope
import com.msuite.core.crypto.EnvelopeResult
import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.Batch
import com.msuite.core.model.MalformedBatch
import com.msuite.core.model.Model
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
 * How many of a writer's own deltas may be listed before it publishes a full-state snapshot and
 * prunes the objects the snapshot covers (plan v1 step 9; invariants 18, 19).
 */
const val SNAPSHOT_EVERY = 20

/**
 * Live sync: observes replica changes, debounces, and publishes deltas. Also pulls changes from
 * the backend. Coarse batching (decision 0002): waits [idlePeriod] of idle before publishing, so
 * a burst of edits becomes one batch. Every object is a [BatchEnvelope] sealed under its key
 * minus the hash (decision 0029), so the backend only ever holds ciphertext. A plug that fails
 * (an unreachable folder) never throws out of here: it turns [healthy] false, and a failed
 * publish is retried after the next successful pull.
 */
@Suppress("TooManyFunctions") // One facade: pull, publish, compaction and the guard around them.
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
    private var snapshotPending: String? = null
    private val log = Logger.withTag("LiveSync")
    private val publishLock = Mutex()
    private var retryPublish = false
    private val _healthy = MutableStateFlow(true)

    /** Whether the last plug operation succeeded. */
    val healthy: StateFlow<Boolean> = _healthy.asStateFlow()

    private val _failure = MutableStateFlow<Throwable?>(null)

    /** The last plug failure, or `null` after a successful operation; a relay sets [RelayRefused]. */
    val failure: StateFlow<Throwable?> = _failure.asStateFlow()

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
        val ingested = replica.ingestedKeys(plugId).toMutableSet()
        for ((key, objectKey) in parsed) {
            if (objectKey != null) pullOne(key, objectKey, ingested)
        }
        compact(keys)
        sweepConflictCopies(parsed.filterValues { it != null }.keys, parsed.filterValues { it == null }.keys)
        // Prune bookkeeping: remove entries for keys no longer in the backend (decision 0002).
        replica.prune(plugId, keys.toSet())
        if (replica.confirm(plugId, keys.toSet())) retryPublish = true
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

    private suspend fun pullOne(key: String, parsed: ObjectKey, ingested: MutableSet<String>) {
        if (isCoveredBySnapshot(key, ingested) || replica.isCorrupt(plugId, key)) return
        val bytes = plug.get(key) ?: return
        try {
            val opened = envelope.open(bytes, parsed.prefix.text())
            if (opened is EnvelopeResult.Opened) {
                if (replica.ingest(plugId, key, opened.batch) is IngestResult.Merged) ingested += key
            }
        } catch (_: MalformedBatch) {
            replica.unreadable(plugId, key)
        }
    }

    private suspend fun publish() = publishLock.withLock {
        retryPublish = !guarded("publish") { publishDelta() }
    }

    /**
     * The writer side of compaction (invariants 18, 19): once a listing shows one of this device's
     * snapshots, delete this device's older objects its span covers, then publish a new snapshot
     * after [SNAPSHOT_EVERY] own deltas. Nothing is deleted until the snapshot is listed (0077).
     */
    private suspend fun compact(keys: List<String>) {
        val objects = keys.mapNotNull { key -> parseKey(key)?.let { key to it } }
        if (snapshotPending != null && keys.contains(snapshotPending)) snapshotPending = null
        val latest = latestOwnSnapshot(objects)
        if (latest != null) pruneCovered(objects, latest)
        if (snapshotPending == null && ownDeltasAfter(objects, latest) >= SNAPSHOT_EVERY) publishSnapshot()
    }

    private fun latestOwnSnapshot(objects: List<Pair<String, ObjectKey>>): Pair<String, ObjectKey>? = objects
        .filter { (_, key) -> key.prefix.deviceId == replica.deviceId && key.prefix.type == BatchType.Snapshot }
        .maxByOrNull { (_, key) -> key.prefix.hlcHi }

    private suspend fun pruneCovered(objects: List<Pair<String, ObjectKey>>, latest: Pair<String, ObjectKey>) {
        for ((key, objectKey) in objects) {
            val own = objectKey.prefix.deviceId == replica.deviceId
            if (own && key != latest.first && objectKey.prefix.hlcHi <= latest.second.prefix.hlcHi) {
                plug.delete(key)
            }
        }
    }

    private fun ownDeltasAfter(objects: List<Pair<String, ObjectKey>>, latest: Pair<String, ObjectKey>?): Int =
        objects.count { (_, objectKey) ->
            objectKey.prefix.deviceId == replica.deviceId &&
                objectKey.prefix.type == BatchType.Delta &&
                (latest == null || objectKey.prefix.hlcHi > latest.second.prefix.hlcHi)
        }

    private suspend fun publishSnapshot() = publishLock.withLock {
        if (snapshotPending != null) return@withLock
        val gid = groupId ?: return@withLock
        val cells = replica.cells()
        if (cells.isEmpty()) return@withLock
        val batch = Batch(Model.DATA_MODEL_VERSION, cells)
        val prefix = ObjectKeyPrefix(
            appId = appId,
            groupId = gid,
            type = BatchType.Snapshot,
            deviceId = replica.deviceId,
            hlcLo = cells.minOf { it.stamp.hlc },
            hlcHi = cells.maxOf { it.stamp.hlc },
        )
        val bytes = envelope.seal(batch, prefix.text())
        val key = prefix.withHash(ciphertextHash(bytes)).text()
        plug.put(key, bytes)
        replica.pushed(plugId, batch, key)
        snapshotPending = key
    }

    private fun parseKey(key: String): ObjectKey? = try {
        ObjectKey.parse(key)
    } catch (ignored: IllegalArgumentException) {
        null
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
            _failure.value = e
            false
        }
        if (ok) _failure.value = null
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
        replica.pushed(plugId, delta, key)
    }
}
