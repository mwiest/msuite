package com.msuite.todo.domain

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TodoSettingsTest : StringSpec({
    "the theme follows the system until set" {
        TodoSettings(MapSettings()).themeMode.value shouldBe ThemeMode.System
    }

    "a set theme is kept" {
        val stored = MapSettings()
        val settings = TodoSettings(stored)
        settings.setThemeMode(ThemeMode.Dark)
        settings.themeMode.value shouldBe ThemeMode.Dark
        TodoSettings(stored).themeMode.value shouldBe ThemeMode.Dark
    }

    "an unknown stored theme reads as the system's" {
        TodoSettings(MapSettings("theme" to "Sepia")).themeMode.value shouldBe ThemeMode.System
    }
})
