package com.msuite.todo.domain

import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

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

    "the last open list is kept; none or a malformed one reads as null" {
        val stored = MapSettings()
        TodoSettings(stored).lastList.value shouldBe null
        val list = Uuid.random()
        TodoSettings(stored).setLastList(list)
        TodoSettings(stored).lastList.value shouldBe list
        TodoSettings(MapSettings("lastList" to "not a uuid")).lastList.value shouldBe null
    }

    "an unknown stored theme reads as the system's" {
        TodoSettings(MapSettings("theme" to "Sepia")).themeMode.value shouldBe ThemeMode.System
    }
})
