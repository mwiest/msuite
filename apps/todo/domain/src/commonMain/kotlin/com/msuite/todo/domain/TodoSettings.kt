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

    private val backend = MutableStateFlow(loadBackend())

    /** The chosen backend; one at a time (plan v1 step 9). */
    val syncBackend: StateFlow<SyncBackend> = backend.asStateFlow()

    private val folderUri = MutableStateFlow((backend.value as? SyncBackend.Folder)?.uri)

    /** The synced folder's URI, or `null` if the backend isn't a folder (decision 0042). */
    val syncedFolderUri: StateFlow<String?> = folderUri.asStateFlow()

    fun setSyncBackend(value: SyncBackend) {
        setPersisted(value)
        backend.value = value
        folderUri.value = (value as? SyncBackend.Folder)?.uri
    }

    /** Compatibility accessor for the folder-only UI; sets or clears a Folder backend. */
    fun setSyncedFolderUri(uri: String?) {
        setSyncBackend(if (uri == null) SyncBackend.None else SyncBackend.Folder(uri))
    }

    /** Reads the backend, migrating the step 8 `syncedFolderUri` key to `syncBackend`. */
    private fun loadBackend(): SyncBackend = decode(settings.getStringOrNull(SYNC_BACKEND))
        ?: settings.getStringOrNull(SYNCED_FOLDER_URI)?.let { legacy ->
            settings.remove(SYNCED_FOLDER_URI)
            SyncBackend.Folder(legacy).also { setPersisted(it) }
        }
        ?: SyncBackend.None

    private fun setPersisted(value: SyncBackend) {
        S3_KEYS.forEach(settings::remove)
        when (value) {
            SyncBackend.None -> settings.remove(SYNC_BACKEND)

            is SyncBackend.Folder -> settings.putString(SYNC_BACKEND, "folder:${value.uri}")

            is SyncBackend.Relay -> settings.putString(SYNC_BACKEND, "relay:${value.url}")

            is SyncBackend.S3 -> {
                settings.putString(SYNC_BACKEND, S3)
                settings.putString(S3_ENDPOINT, value.endpoint)
                settings.putString(S3_REGION, value.region)
                settings.putString(S3_BUCKET, value.bucket)
                settings.putString(S3_PREFIX, value.prefix)
                settings.putString(S3_ACCESS_KEY_ID, value.accessKeyId)
            }
        }
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
        const val SYNCED_FOLDER_URI = "syncedFolderUri"
        const val SYNC_BACKEND = "syncBackend"
        const val S3 = "s3"

        const val S3_ENDPOINT = "s3.endpoint"
        const val S3_REGION = "s3.region"
        const val S3_BUCKET = "s3.bucket"
        const val S3_PREFIX = "s3.prefix"
        const val S3_ACCESS_KEY_ID = "s3.accessKeyId"
        val S3_KEYS = listOf(S3_ENDPOINT, S3_REGION, S3_BUCKET, S3_PREFIX, S3_ACCESS_KEY_ID)
    }

    private fun decode(stored: String?): SyncBackend? = when {
        stored == null -> null
        stored == S3 -> loadS3() ?: SyncBackend.None
        stored == "none" -> SyncBackend.None
        stored.startsWith("folder:") -> SyncBackend.Folder(stored.removePrefix("folder:"))
        stored.startsWith("relay:") -> SyncBackend.Relay(stored.removePrefix("relay:"))
        else -> null
    }
}
