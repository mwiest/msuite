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

    "done tasks show until collapsed, and the choice is kept" {
        val stored = MapSettings()
        TodoSettings(stored).doneCollapsed.value shouldBe false
        TodoSettings(stored).setDoneCollapsed(true)
        TodoSettings(stored).doneCollapsed.value shouldBe true
    }

    "an unknown stored theme reads as the system's" {
        TodoSettings(MapSettings("theme" to "Sepia")).themeMode.value shouldBe ThemeMode.System
    }

    "a legacy synced folder URI migrates to a Folder backend" {
        val stored = MapSettings("syncedFolderUri" to "content://folder")

        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.Folder("content://folder")
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.Folder("content://folder")
    }

    "a legacy folder or relay value migrates to the remembered destination" {
        val folder = MapSettings("syncBackend" to "folder:content://folder")
        TodoSettings(folder).syncBackend.value shouldBe SyncBackend.Folder("content://folder")
        TodoSettings(folder).remembered.value.folder shouldBe SyncBackend.Folder("content://folder")

        val relay = MapSettings("syncBackend" to "relay:https://relay.example")
        TodoSettings(relay).syncBackend.value shouldBe SyncBackend.Relay("https://relay.example")
        TodoSettings(relay).remembered.value.relay shouldBe SyncBackend.Relay("https://relay.example")
    }

    "the chosen backend round-trips, and the folder URI is the remembered folder's" {
        val stored = MapSettings()
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.None

        val settings = TodoSettings(stored)
        settings.setSyncBackend(SyncBackend.Folder("content://folder"))
        settings.syncedFolderUri.value shouldBe "content://folder"
        settings.setSyncBackend(SyncBackend.Relay("https://relay.example"))
        settings.syncedFolderUri.value shouldBe "content://folder"

        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.Relay("https://relay.example")
        settings.setSyncBackend(SyncBackend.None)
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.None
    }

    "an S3 backend round-trips, without a secret anywhere in the settings" {
        val stored = MapSettings()
        val s3 = SyncBackend.S3("https://acc.r2.cloudflarestorage.com", "auto", "msuite", "todo", "AKID")
        TodoSettings(stored).setSyncBackend(s3)

        TodoSettings(stored).syncBackend.value shouldBe s3
        stored.keys shouldBe
            setOf("syncBackend", "s3.endpoint", "s3.region", "s3.bucket", "s3.prefix", "s3.accessKeyId")
    }

    "switching keeps every type's last destination, also across a restart" {
        val stored = MapSettings()
        val settings = TodoSettings(stored)
        val s3 = SyncBackend.S3("https://e", "r", "b", "", "AKID")
        settings.setSyncBackend(SyncBackend.Folder("content://folder"))
        settings.setSyncBackend(SyncBackend.Relay("https://relay.example"))
        settings.setSyncBackend(s3)
        settings.setSyncBackend(SyncBackend.None)

        val expected = RememberedBackends(
            SyncBackend.Folder("content://folder"),
            SyncBackend.Relay("https://relay.example"),
            s3,
        )
        settings.remembered.value shouldBe expected
        TodoSettings(stored).remembered.value shouldBe expected
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.None
    }

    "choosing a type again replaces only its own destination" {
        val settings = TodoSettings(MapSettings())
        settings.setSyncBackend(SyncBackend.Folder("content://a"))
        settings.setSyncBackend(SyncBackend.Relay("https://relay.example"))
        settings.setSyncBackend(SyncBackend.Folder("content://b"))
        settings.remembered.value shouldBe
            RememberedBackends(SyncBackend.Folder("content://b"), SyncBackend.Relay("https://relay.example"), null)
    }

    "an S3 backend with a field missing reads as none" {
        val stored = MapSettings("syncBackend" to "s3", "s3.endpoint" to "https://e", "s3.region" to "r")
        TodoSettings(stored).syncBackend.value shouldBe SyncBackend.None
    }
})
