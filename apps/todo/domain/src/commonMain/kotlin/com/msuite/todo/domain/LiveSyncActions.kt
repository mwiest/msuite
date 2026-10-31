package com.msuite.todo.domain

import com.msuite.core.crypto.BackendType
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.PairingPayload
import com.msuite.core.model.PlugId
import com.msuite.core.relay.RelayRefused
import com.msuite.core.sync.BackendPlug
import com.msuite.core.sync.FileStore
import com.msuite.core.sync.FolderPlug
import com.msuite.core.sync.LiveSync
import com.msuite.core.sync.Replica
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Builds the relay plug for [url] under the group [root]; the shell injects the HTTP engine (0079). */
fun interface RelayPlugFactory {
    fun create(url: String, root: ByteArray): BackendPlug
}

/** The relay half of [LiveSyncActions]: the shell's plug factory and whether http is allowed. */
data class RelayOptions(
    val plugFactory: RelayPlugFactory? = null,
    val allowInsecure: Boolean = false,
)

/**
 * Live sync over the chosen backend: last sync, pending changes, and whether it is failing. A relay
 * refusal carries the [RelayRefused] so Settings can say why (auth, registration, limits).
 */
data class SyncStatus(
    val lastSync: Instant?,
    val pendingChanges: Int,
    val failing: Boolean = false,
    val refused: RelayRefused? = null,
)

/** Whether [url] is a relay address this build may use: https, or http for a debug LAN/emulator. */
fun isAllowedRelayUrl(url: String, allowInsecure: Boolean): Boolean =
    (url.startsWith("https://") && url.length > "https://".length) ||
        (allowInsecure && url.startsWith("http://") && url.length > "http://".length)

/**
 * The live plug wired to the settings and replica (decision 0037): one backend at a time, chosen in
 * [TodoSettings]. Polling, flush and status are backend-neutral; a switch stops the old sync before
 * the new one starts. The manual snapshot export/import stays in [SyncActions] (decision 0066).
 */
class LiveSyncActions(
    private val replica: Replica,
    private val identity: StoredIdentity,
    private val settings: TodoSettings,
    private val folderPick: FolderPick,
    private val fileStoreFactory: (String) -> FileStore,
    private val scope: CoroutineScope,
    private val relay: RelayOptions = RelayOptions(),
) {
    private val clock: Clock = Clock.System
    private val _status = MutableStateFlow(SyncStatus(lastSync = null, pendingChanges = 0))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** The chosen backend, exposed for Settings. */
    val backend: StateFlow<SyncBackend> = settings.syncBackend

    /** The configured folder URI, exposed for the step 8 folder UI. */
    val folderUri: StateFlow<String?> = settings.syncedFolderUri

    private var liveSync: LiveSync? = null
    private var startJob: Job? = null
    private var pollingJob: Job? = null

    /** Opens the folder picker, switches to the folder, and starts its sync. */
    suspend fun pickFolder() {
        val uri = folderPick.pick() ?: return
        setBackend(SyncBackend.Folder(uri))
    }

    /** Clears the backend and stops the sync. */
    fun clearFolder() = setBackend(SyncBackend.None)

    /** Chooses one backend, stopping the previous one first (plan v1 step 9). */
    fun setBackend(value: SyncBackend) {
        val normalized = when (value) {
            is SyncBackend.Relay -> value.copy(url = value.url.trimEnd('/'))
            else -> value
        }
        val changed = settings.syncBackend.value != normalized
        settings.setSyncBackend(normalized)
        if (!changed && liveSync != null) return
        stopLiveSync()
        _status.value = SyncStatus(lastSync = null, pendingChanges = 0)
        startSyncIfConfigured()
    }

    /** Starts the chosen sync if there is one. Called on app startup. */
    fun startSyncIfConfigured() {
        if (liveSync != null) return
        identity.root(KEY_GEN_V1)
            ?.let(::connectionFor)
            ?.let { (plug, plugId) ->
                val sync = LiveSync(replica, plug, plugId, "todo", identity, scope, 10.seconds)
                liveSync = sync
                startJob = scope.launch { sync.start() }
            }
    }

    private fun stopLiveSync() {
        startJob?.cancel()
        startJob = null
        liveSync?.stop()
        liveSync = null
    }

    private fun connectionFor(root: ByteArray): Pair<BackendPlug, PlugId>? =
        when (val chosen = settings.syncBackend.value) {
            SyncBackend.None -> null

            is SyncBackend.Folder ->
                FolderPlug(fileStoreFactory(chosen.uri)) to PlugId("folder:${chosen.uri}")

            is SyncBackend.Relay ->
                if (!isAllowedRelayUrl(chosen.url, relay.allowInsecure)) {
                    null
                } else {
                    relay.plugFactory?.let { it.create(chosen.url, root) to PlugId("relay:${chosen.url}") }
                }
        }

    /** The group ID an operator's relay allowlist names; computed from the root, never stored. */
    suspend fun groupId(): String? = identity.root(KEY_GEN_V1)?.let { com.msuite.core.sync.groupId(it) }

    /**
     * Applies the backend descriptor of a just-joined group (record 0080): a relay URL becomes the
     * chosen backend, any other type clears a backend left over from a previous group.
     */
    fun applyJoinedBackend(type: BackendType?, section: ByteArray) {
        val relayUrl = if (type == BackendType.Relay) PairingPayload.relayUrl(section) else null
        setBackend(
            if (relayUrl != null && isAllowedRelayUrl(relayUrl, relay.allowInsecure)) {
                SyncBackend.Relay(relayUrl)
            } else {
                SyncBackend.None
            },
        )
    }

    /** Manually triggers a sync (pull from the backend). */
    suspend fun syncNow() {
        val sync = liveSync ?: return
        sync.pull()
        report(sync)
    }

    /** Starts polling for changes every 10 seconds (foreground only). */
    fun startPolling() {
        if (pollingJob != null || liveSync == null) return
        pollingJob = scope.launch {
            while (isActive) {
                liveSync?.let {
                    it.pull()
                    report(it)
                }
                delay(10.seconds)
            }
        }
    }

    private fun report(sync: LiveSync) {
        val healthy = sync.healthy.value
        val current = _status.value
        _status.value = current.copy(
            lastSync = if (healthy) clock.now() else current.lastSync,
            failing = !healthy,
            refused = sync.failure.value as? RelayRefused,
        )
    }

    /** Stops the polling job. */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Immediately publishes any pending changes, bypassing the debounce. Called when the app goes
     * to background to ensure edits are synced before suspension.
     */
    suspend fun flushPendingChanges() {
        liveSync?.flush()
    }
}
