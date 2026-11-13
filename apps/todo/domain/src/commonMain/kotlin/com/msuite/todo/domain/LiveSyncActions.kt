package com.msuite.todo.domain

import com.msuite.core.crypto.BackendType
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.PairingPayload
import com.msuite.core.crypto.S3Section
import com.msuite.core.model.PlugId
import com.msuite.core.relay.RelayRefusal
import com.msuite.core.relay.RelayRefused
import com.msuite.core.s3.S3Config
import com.msuite.core.s3.S3Credentials
import com.msuite.core.s3.S3Refusal
import com.msuite.core.s3.S3Refused
import com.msuite.core.s3.credentials
import com.msuite.core.s3.toConfig
import com.msuite.core.sync.BackendPlug
import com.msuite.core.sync.FileStore
import com.msuite.core.sync.FolderPlug
import com.msuite.core.sync.LiveSync
import com.msuite.core.sync.ProbeKey
import com.msuite.core.sync.Replica
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The app segment of every object key (decision 0029) and of the relay token (0030). */
const val TODO_APP_ID = "todo"

private val PROBE_BYTES = "msuite probe".encodeToByteArray()

/** Builds the relay plug for [url] under the group [root]; the shell injects the HTTP engine (0079). */
fun interface RelayPlugFactory {
    fun create(url: String, root: ByteArray): BackendPlug
}

/** The relay half of [LiveSyncActions]: the shell's plug factory and whether http is allowed. */
data class RelayOptions(
    val plugFactory: RelayPlugFactory? = null,
    val allowInsecure: Boolean = false,
)

/** Builds the S3 plug; the shell injects the HTTP engine, like the relay's (plan `v1-s3-plug.md`). */
fun interface S3PlugFactory {
    fun create(config: S3Config, credentials: S3Credentials): BackendPlug
}

/** The S3 half of [LiveSyncActions]: the plug factory, where the secret lives, and whether http is allowed. */
data class S3Options(
    val plugFactory: S3PlugFactory,
    val secrets: SecretStore,
    val allowInsecure: Boolean = false,
)

/** Why [LiveSyncActions.connectS3] refused a configuration. */
sealed interface S3SetupError {
    /** A field is blank, or the endpoint is not an `http(s)://host[:port]`. */
    data object Invalid : S3SetupError

    /** An `http://` endpoint in a release build (as for the relay, decision 0079). */
    data object Insecure : S3SetupError

    /** No secret typed and none stored. */
    data object MissingSecret : S3SetupError

    /** This build has no S3 plug. */
    data object Unsupported : S3SetupError

    /** The bucket refused the listing made before saving. */
    data class Refused(val refusal: S3Refusal) : S3SetupError

    /** The listing made before saving failed without an answer from the bucket. */
    data object Unreachable : S3SetupError
}

/** Why [LiveSyncActions.connectRelay] refused a relay address. */
sealed interface RelaySetupError {
    /** Not an `http(s)://` address. */
    data object Invalid : RelaySetupError

    /** An `http://` address in a release build (decision 0079). */
    data object Insecure : RelaySetupError

    /** This build has no relay plug. */
    data object Unsupported : RelaySetupError

    /** The relay refused the listing made before saving. */
    data class Refused(val refusal: RelayRefusal) : RelaySetupError

    /** The listing made before saving failed without an answer from the relay. */
    data object Unreachable : RelaySetupError
}

/**
 * Live sync over the chosen backend: last sync, pending changes, and whether it is failing. A relay
 * or S3 refusal carries the [RelayRefused] or [S3Refused] so Settings can say why.
 * [looksEmpty]: the backend listed nothing after syncing before, and waits for [LiveSyncActions.confirmEmpty].
 */
data class SyncStatus(
    val lastSync: Instant?,
    val pendingChanges: Int,
    val failing: Boolean = false,
    val refused: RelayRefused? = null,
    val looksEmpty: Boolean = false,
    val s3Refused: S3Refused? = null,
)

/** Whether [url] is a relay or S3 address this build may use: https, or http for a debug LAN/emulator. */
fun isAllowedServerUrl(url: String, allowInsecure: Boolean): Boolean =
    (url.startsWith("https://") && url.length > "https://".length) ||
        (allowInsecure && url.startsWith("http://") && url.length > "http://".length)

/**
 * The live plug wired to the settings and replica (decision 0037): one backend at a time, chosen in
 * [TodoSettings]. Polling, flush and status are backend-neutral; a switch stops the old sync before
 * the new one starts. The manual snapshot export/import stays in [SyncActions] (decision 0066).
 */
// One facade over the backend lifecycle (choose, join, switch, poll, report), with one options object per backend.
@Suppress("TooManyFunctions", "LongParameterList")
class LiveSyncActions(
    private val replica: Replica,
    private val identity: StoredIdentity,
    private val settings: TodoSettings,
    private val folderPick: FolderPick,
    private val fileStoreFactory: (String) -> FileStore,
    private val scope: CoroutineScope,
    private val relay: RelayOptions = RelayOptions(),
    private val s3: S3Options? = null,
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
    private var pollingWanted = false

    /** Opens the folder picker, switches to the folder, and starts its sync. */
    suspend fun pickFolder() {
        val uri = folderPick.pick() ?: return
        setBackend(SyncBackend.Folder(uri))
    }

    /** Clears the backend and stops the sync. */
    fun clearFolder() = setBackend(SyncBackend.None)

    /**
     * Chooses one backend, stopping the previous one first (plan v1 step 9). Leaving S3 deletes its
     * stored secret, so no credential outlives its backend.
     */
    fun setBackend(value: SyncBackend) = switchTo(value, restart = false)

    /** Chooses the relay at [url] after listing this group's objects there; a refusal keeps the current backend. */
    suspend fun connectRelay(url: String): RelaySetupError? {
        val address = url.trim().trimEnd('/')
        val factory = relay.plugFactory
        val root = identity.root(KEY_GEN_V1)
        val error = when {
            factory == null || root == null -> RelaySetupError.Unsupported

            !isAllowedServerUrl(address, relay.allowInsecure) ->
                if (address.startsWith("http://")) RelaySetupError.Insecure else RelaySetupError.Invalid

            else -> when (val failure = probe(factory.create(address, root))) {
                null -> null
                is RelayRefused -> RelaySetupError.Refused(failure.refusal)
                else -> RelaySetupError.Unreachable
            }
        }
        if (error == null) setBackend(SyncBackend.Relay(address))
        return error
    }

    /**
     * Chooses the S3 bucket [backend] with [secret], or the stored secret when [secret] is blank,
     * after listing this group's objects with them: a refusal keeps the current backend and secret.
     */
    suspend fun connectS3(backend: SyncBackend.S3, secret: String): S3SetupError? {
        val typed = secret.trim()
        val section = s3?.let { backend.section(typed.ifEmpty { it.secrets.load().orEmpty() }) }
        return checkS3(section) ?: probeS3(requireNotNull(section)) ?: run {
            commitS3(backend, typed)
            null
        }
    }

    /** Chooses an S3 bucket from a pairing code without listing it: the code came from a working device. */
    private fun connectS3Unchecked(section: S3Section): Boolean {
        val usable = checkS3(section) == null
        if (usable) commitS3(section.backend(), section.secretAccessKey.trim())
        return usable
    }

    private fun checkS3(section: S3Section?): S3SetupError? {
        val options = s3
        val wellFormed = section?.toConfig() != null &&
            PairingPayload.s3Section(section.copy(secretAccessKey = "-").encode()) != null
        return when {
            options == null || section == null -> S3SetupError.Unsupported
            !wellFormed -> S3SetupError.Invalid
            !isAllowedServerUrl(section.endpoint, options.allowInsecure) -> S3SetupError.Insecure
            section.secretAccessKey.isBlank() -> S3SetupError.MissingSecret
            else -> null
        }
    }

    private suspend fun probeS3(section: S3Section): S3SetupError? {
        val plug = requireNotNull(s3).plugFactory.create(requireNotNull(section.toConfig()), section.credentials())
        return when (val failure = probe(plug)) {
            null -> null
            is S3Refused -> S3SetupError.Refused(failure.refusal)
            else -> S3SetupError.Unreachable
        }
    }

    /**
     * Lists this group's objects through [plug], then writes and deletes a [ProbeKey] there, so a
     * key without write or delete rights is refused here; what it threw, or `null` if all worked.
     */
    // Plugs throw platform exceptions (IOException and others), so catch broadly.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun probe(plug: BackendPlug): Exception? = try {
        val group = requireNotNull(groupId()) { "no root" }
        plug.list("$TODO_APP_ID/$group/")
        val probe = ProbeKey.random(TODO_APP_ID, group)
        plug.put(probe, PROBE_BYTES)
        plug.delete(probe)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    /** The secret is saved before the backend is chosen, so S3 is never chosen without one. */
    private fun commitS3(backend: SyncBackend.S3, typedSecret: String) {
        if (typedSecret.isNotEmpty()) requireNotNull(s3).secrets.save(typedSecret)
        switchTo(backend, restart = true)
    }

    /** Whether the chosen S3 backend lacks its secret (lost with the KeyStore key): Settings asks for it. */
    fun needsS3Secret(): Boolean = settings.syncBackend.value is SyncBackend.S3 && s3?.secrets?.load() == null

    /** What this device's pairing code carries (record 0072); S3 without its secret carries none. */
    fun pairingBackend(): PairingBackend = when (val chosen = settings.syncBackend.value) {
        SyncBackend.None -> PairingBackend(BackendType.Manual)

        is SyncBackend.Folder -> PairingBackend(BackendType.Folder)

        is SyncBackend.Relay -> PairingBackend(BackendType.Relay, chosen.url.encodeToByteArray())

        is SyncBackend.S3 -> s3?.secrets?.load()
            ?.let { PairingBackend(BackendType.S3, chosen.section(it).encode()) }
            ?: PairingBackend(BackendType.Manual)
    }

    private fun switchTo(value: SyncBackend, restart: Boolean) {
        val normalized = when (value) {
            is SyncBackend.Relay -> value.copy(url = value.url.trimEnd('/'))
            else -> value
        }
        if (normalized !is SyncBackend.S3) s3?.secrets?.clear()
        val changed = settings.syncBackend.value != normalized
        settings.setSyncBackend(normalized)
        if (!changed && !restart && liveSync != null) return
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
                val sync = LiveSync(replica, plug, plugId, TODO_APP_ID, identity, scope, 10.seconds)
                liveSync = sync
                startJob = scope.launch { sync.start() }
                if (pollingWanted) startPolling()
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
                if (!isAllowedServerUrl(chosen.url, relay.allowInsecure)) {
                    null
                } else {
                    relay.plugFactory?.let { it.create(chosen.url, root) to PlugId("relay:${chosen.url}") }
                }

            is SyncBackend.S3 -> s3Connection(chosen)
        }

    /** The S3 plug, or `null` without a secret (lost with the KeyStore key) or with a refused endpoint. */
    private fun s3Connection(chosen: SyncBackend.S3): Pair<BackendPlug, PlugId>? {
        val options = s3 ?: return null
        val section = options.secrets.load()?.let(chosen::section)
        val config = section?.toConfig()?.takeIf { isAllowedServerUrl(chosen.endpoint, options.allowInsecure) }
        return if (section == null || config == null) {
            null
        } else {
            options.plugFactory.create(config, section.credentials()) to
                PlugId("s3:${chosen.endpoint}/${chosen.bucket}/${config.keyPrefix}")
        }
    }

    /** The group ID an operator's relay allowlist names; computed from the root, never stored. */
    suspend fun groupId(): String? = identity.root(KEY_GEN_V1)?.let { com.msuite.core.sync.groupId(it) }

    /**
     * Applies the backend descriptor of a just-joined group (records 0080, 0085): a relay URL or an
     * S3 bucket becomes the chosen backend, any other type clears one left from a previous group.
     */
    fun applyJoinedBackend(type: BackendType?, section: ByteArray) {
        if (type == null || !applySwitchedBackend(type, section)) setBackend(SyncBackend.None)
    }

    /**
     * Switches a device already in the group to the relay or bucket a code names. Unlike a join, a
     * section this build refuses keeps the current backend; returns whether it switched.
     */
    fun applySwitchedBackend(type: BackendType, section: ByteArray): Boolean = when (type) {
        BackendType.Relay -> {
            val url = PairingPayload.relayUrl(section)?.takeIf { isAllowedServerUrl(it, relay.allowInsecure) }
            url?.let { setBackend(SyncBackend.Relay(it)) }
            url != null
        }

        BackendType.S3 -> PairingPayload.s3Section(section)?.let(::connectS3Unchecked) ?: false

        BackendType.Manual, BackendType.Folder -> false
    }

    /** Manually triggers a sync (pull from the backend). */
    suspend fun syncNow() {
        val sync = liveSync ?: return
        sync.pull()
        report(sync)
    }

    /** Starts polling for changes every 10 seconds (foreground only). */
    fun startPolling() {
        pollingWanted = true
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
            looksEmpty = sync.emptyHeld.value,
            s3Refused = sync.failure.value as? S3Refused,
        )
    }

    /** The user confirms the backend really is empty: sync prunes and republishes (decision 0082). */
    suspend fun confirmEmpty() {
        liveSync?.acceptEmpty()
        syncNow()
    }

    /** Stops the polling job. */
    fun stopPolling() {
        pollingWanted = false
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

/** The pairing section for this backend under [secret] (decision 0085). */
fun SyncBackend.S3.section(secret: String): S3Section = S3Section(endpoint, region, bucket, prefix, accessKeyId, secret)

/** The settings half of a pairing section: everything but the secret. */
fun S3Section.backend(): SyncBackend.S3 = SyncBackend.S3(endpoint, region, bucket, prefix, accessKeyId)
