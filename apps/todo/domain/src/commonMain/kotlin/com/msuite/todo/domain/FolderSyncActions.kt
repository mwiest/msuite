package com.msuite.todo.domain

import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.PlugId
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

/**
 * The folder plug's sync status, exposed to the UI (slice 12). Shows when the last sync got
 * through, how many changes are pending publish, and whether the folder is [failing] (unreachable,
 * retried on the next poll).
 */
data class FolderSyncStatus(
    val lastSync: Instant?,
    val pendingChanges: Int,
    val failing: Boolean = false,
)

/**
 * The folder plug wired to the settings and replica (slice 12). Manages the synced folder
 * configuration and provides manual sync operations. The actual live sync (background polling) is
 * slice 13.
 */
class FolderSyncActions(
    private val replica: Replica,
    private val identity: StoredIdentity,
    private val settings: TodoSettings,
    private val folderPick: FolderPick,
    private val fileStoreFactory: (String) -> FileStore,
    private val scope: CoroutineScope,
) {
    private val clock: Clock = Clock.System
    private val _status = MutableStateFlow(FolderSyncStatus(lastSync = null, pendingChanges = 0))
    val status: StateFlow<FolderSyncStatus> = _status.asStateFlow()

    /** The configured folder URI, exposed for the UI. */
    val folderUri: StateFlow<String?> = settings.syncedFolderUri

    private var liveSync: LiveSync? = null
    private var pollingJob: Job? = null

    /** Opens the folder picker and stores the result in settings. */
    suspend fun pickFolder() {
        val uri = folderPick.pick() ?: return
        settings.setSyncedFolderUri(uri)
        startSyncIfConfigured()
    }

    /** Clears the folder configuration and stops the sync. */
    fun clearFolder() {
        settings.setSyncedFolderUri(null)
        stopPolling()
        liveSync?.stop()
        liveSync = null
        _status.value = FolderSyncStatus(lastSync = null, pendingChanges = 0)
    }

    /** Starts the folder sync if a folder is configured. Called on app startup. */
    fun startSyncIfConfigured() {
        val uri = settings.syncedFolderUri.value
        if (uri == null || liveSync != null) return
        if (identity.root(KEY_GEN_V1) == null) return
        val store = fileStoreFactory(uri)
        val plug = FolderPlug(store)
        val plugId = PlugId(uri)
        liveSync = LiveSync(replica, plug, plugId, "todo", identity, scope, 10.seconds).also {
            scope.launch { it.start() }
        }
    }

    /** Manually triggers a sync (pull from the folder). */
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
