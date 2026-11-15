package com.msuite.todo.domain

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

enum class ThemeMode { System, Light, Dark }

/** How this device syncs (one backend at a time, plan v1 step 9): none, a folder, or a relay. */
sealed interface SyncBackend {
    data object None : SyncBackend

    /** A folder the user syncs with Syncthing, Dropbox or similar (decision 0042). */
    data class Folder(val uri: String) : SyncBackend

    /** A self-hosted relay; the token comes from the root (decisions 0030, 0079). */
    data class Relay(val url: String) : SyncBackend

    /** An S3-compatible bucket; its secret lives in a [SecretStore], never here (decision 0087). */
    data class S3(
        val endpoint: String,
        val region: String,
        val bucket: String,
        val prefix: String,
        val accessKeyId: String,
    ) : SyncBackend
}

/** The last destination of each backend type, chosen or not; `null` for a type never set up. */
data class RememberedBackends(
    val folder: SyncBackend.Folder? = null,
    val relay: SyncBackend.Relay? = null,
    val s3: SyncBackend.S3? = null,
)

/** Device-local, non-synced preferences (decision 0064). Never backend credentials. */
class TodoSettings(private val settings: Settings) {
    private val theme = MutableStateFlow(
        settings.getStringOrNull(THEME)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.System,
    )

    val themeMode: StateFlow<ThemeMode> = theme.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(THEME, mode.name)
        theme.value = mode
    }

    private val last = MutableStateFlow(settings.getStringOrNull(LAST_LIST)?.let(Uuid::parseOrNull))

    /** The list open when the app was last used, if any; it may since have been deleted. */
    val lastList: StateFlow<Uuid?> = last.asStateFlow()

    fun setLastList(id: Uuid) {
        settings.putString(LAST_LIST, id.toString())
        last.value = id
    }

    private val collapsed = MutableStateFlow(settings.getBoolean(DONE_COLLAPSED, false))

    /** Whether the done tasks are hidden under their header (decision 0065). */
    val doneCollapsed: StateFlow<Boolean> = collapsed.asStateFlow()

    fun setDoneCollapsed(value: Boolean) {
        settings.putBoolean(DONE_COLLAPSED, value)
        collapsed.value = value
    }

    private val saved = MutableStateFlow(loadRemembered())

    /** Each backend type's last destination, kept while another is chosen so switching back is easy. */
    val remembered: StateFlow<RememberedBackends> = saved.asStateFlow()

    private val folderUri = MutableStateFlow(saved.value.folder?.uri)

    /** The remembered folder's URI, chosen or not (decision 0042). */
    val syncedFolderUri: StateFlow<String?> = folderUri.asStateFlow()

    // After the flows that loadBackend's migration writes to.
    private val backend = MutableStateFlow(loadBackend())

    /** The chosen backend; one at a time (plan v1 step 9). */
    val syncBackend: StateFlow<SyncBackend> = backend.asStateFlow()

    /** Chooses [value] and makes it its type's remembered destination. */
    fun setSyncBackend(value: SyncBackend) {
        persist(value)
        backend.value = value
    }

    private fun persist(value: SyncBackend) {
        remember(value)
        when (value) {
            SyncBackend.None -> settings.remove(SYNC_BACKEND)
            is SyncBackend.Folder -> settings.putString(SYNC_BACKEND, FOLDER)
            is SyncBackend.Relay -> settings.putString(SYNC_BACKEND, RELAY)
            is SyncBackend.S3 -> settings.putString(SYNC_BACKEND, S3)
        }
    }

    /** Compatibility accessor for the folder-only UI; sets or clears a Folder backend. */
    fun setSyncedFolderUri(uri: String?) {
        setSyncBackend(if (uri == null) SyncBackend.None else SyncBackend.Folder(uri))
    }

    private fun remember(value: SyncBackend) {
        when (value) {
            SyncBackend.None -> return

            is SyncBackend.Folder -> {
                settings.putString(FOLDER_URI, value.uri)
                saved.value = saved.value.copy(folder = value)
                folderUri.value = value.uri
            }

            is SyncBackend.Relay -> {
                settings.putString(RELAY_URL, value.url)
                saved.value = saved.value.copy(relay = value)
            }

            is SyncBackend.S3 -> {
                settings.putString(S3_ENDPOINT, value.endpoint)
                settings.putString(S3_REGION, value.region)
                settings.putString(S3_BUCKET, value.bucket)
                settings.putString(S3_PREFIX, value.prefix)
                settings.putString(S3_ACCESS_KEY_ID, value.accessKeyId)
                saved.value = saved.value.copy(s3 = value)
            }
        }
    }

    private fun loadRemembered() = RememberedBackends(
        folder = settings.getStringOrNull(FOLDER_URI)?.let(SyncBackend::Folder),
        relay = settings.getStringOrNull(RELAY_URL)?.let(SyncBackend::Relay),
        s3 = loadS3(),
    )

    /** Reads the backend, migrating the step 8 `syncedFolderUri` key and the `folder:`/`relay:` values. */
    private fun loadBackend(): SyncBackend {
        val legacy = settings.getStringOrNull(SYNCED_FOLDER_URI)?.let(SyncBackend::Folder)
            ?: decodeLegacy(settings.getStringOrNull(SYNC_BACKEND))
        if (legacy != null) {
            settings.remove(SYNCED_FOLDER_URI)
            persist(legacy)
            return legacy
        }
        return when (settings.getStringOrNull(SYNC_BACKEND)) {
            FOLDER -> saved.value.folder
            RELAY -> saved.value.relay
            S3 -> saved.value.s3
            else -> null
        } ?: SyncBackend.None
    }

    /** The S3 backend from its own keys, or `null` if one is missing. */
    private fun loadS3(): SyncBackend.S3? = SyncBackend.S3(
        endpoint = settings.getStringOrNull(S3_ENDPOINT) ?: return null,
        region = settings.getStringOrNull(S3_REGION) ?: return null,
        bucket = settings.getStringOrNull(S3_BUCKET) ?: return null,
        prefix = settings.getStringOrNull(S3_PREFIX) ?: return null,
        accessKeyId = settings.getStringOrNull(S3_ACCESS_KEY_ID) ?: return null,
    )

    private companion object {
        const val THEME = "theme"
        const val LAST_LIST = "lastList"
        const val DONE_COLLAPSED = "doneCollapsed"
        const val SYNCED_FOLDER_URI = "syncedFolderUri"
        const val SYNC_BACKEND = "syncBackend"
        const val FOLDER = "folder"
        const val RELAY = "relay"
        const val S3 = "s3"

        const val FOLDER_URI = "folder.uri"
        const val RELAY_URL = "relay.url"
        const val S3_ENDPOINT = "s3.endpoint"
        const val S3_REGION = "s3.region"
        const val S3_BUCKET = "s3.bucket"
        const val S3_PREFIX = "s3.prefix"
        const val S3_ACCESS_KEY_ID = "s3.accessKeyId"
    }

    /** A backend stored with its destination in the value, as before 2026-11-15. */
    private fun decodeLegacy(stored: String?): SyncBackend? = when {
        stored == null -> null
        stored.startsWith("folder:") -> SyncBackend.Folder(stored.removePrefix("folder:"))
        stored.startsWith("relay:") -> SyncBackend.Relay(stored.removePrefix("relay:"))
        else -> null
    }
}
