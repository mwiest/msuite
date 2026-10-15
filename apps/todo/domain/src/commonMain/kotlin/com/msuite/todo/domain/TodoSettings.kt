package com.msuite.todo.domain

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private companion object {
        const val THEME = "theme"
    }
}
