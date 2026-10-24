package com.msuite.todo.domain

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

enum class ThemeMode { System, Light, Dark }

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

    private val folderUri = MutableStateFlow(settings.getStringOrNull(SYNCED_FOLDER_URI))

    /** The synced folder's URI, or `null` if not configured (decision 0042). */
    val syncedFolderUri: StateFlow<String?> = folderUri.asStateFlow()

    fun setSyncedFolderUri(uri: String?) {
        if (uri != null) {
            settings.putString(SYNCED_FOLDER_URI, uri)
        } else {
            settings.remove(SYNCED_FOLDER_URI)
        }
        folderUri.value = uri
    }

    private companion object {
        const val THEME = "theme"
        const val LAST_LIST = "lastList"
        const val SYNCED_FOLDER_URI = "syncedFolderUri"
    }
}
