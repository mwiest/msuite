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

    "a legacy synced folder URI migrates to a Folder backend" {
        val stored = MapSettings("syncedFolderUri" to "content://folder")

        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.Folder("content://folder")
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.Folder("content://folder")
    }

    "the chosen backend round-trips and drives the folder URI" {
        val stored = MapSettings()
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.None

        val settings = TodoSettings(stored)
        settings.setSyncBackend(SyncBackend.Folder("content://folder"))
        settings.syncedFolderUri.value shouldBe "content://folder"
        settings.setSyncBackend(SyncBackend.Relay("https://relay.example"))
        settings.syncedFolderUri.value shouldBe null

        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.Relay("https://relay.example")
        settings.setSyncBackend(SyncBackend.None)
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.None
    }
})
