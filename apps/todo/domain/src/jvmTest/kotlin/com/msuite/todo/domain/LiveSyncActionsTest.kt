package com.msuite.todo.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.crypto.BackendType
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.relay.RelayRefusal
import com.msuite.core.relay.RelayRefused
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.BackendPlug
import com.msuite.core.sync.FileStore
import com.msuite.core.sync.Replica
import com.msuite.core.sync.testing.MemoryPlug
import com.msuite.core.sync.testing.PlainFileStore
import com.msuite.core.sync.testing.TestClock
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.Properties
import kotlin.uuid.Uuid

/** A folder that throws like an unreachable provider while [down] is set. */
private class FlakyFileStore(private val inner: FileStore) : FileStore {
    var down = true
    var lists = 0

    private fun check() {
        if (down) throw IOException("cannot create directory")
    }

    override suspend fun read(path: String) = check().let { inner.read(path) }

    override suspend fun write(path: String, bytes: ByteArray) = check().let { inner.write(path, bytes) }

    override suspend fun list(directory: String) = check().let { inner.list(directory) }.also { lists++ }

    override suspend fun delete(path: String) = check().let { inner.delete(path) }

    override suspend fun mkdir(path: String) = check().let { inner.mkdir(path) }

    override suspend fun rename(from: String, to: String) = check().let { inner.rename(from, to) }

    override suspend fun exists(path: String) = check().let { inner.exists(path) }
}

private object NoPick : FolderPick {
    override suspend fun pick(): String? = null
}

private class CountingPlug(private val inner: BackendPlug = MemoryPlug()) : BackendPlug {
    var lists = 0

    override suspend fun put(key: String, bytes: ByteArray) = inner.put(key, bytes)

    override suspend fun list(prefix: String): List<String> = inner.list(prefix).also { lists++ }

    override suspend fun get(key: String): ByteArray? = inner.get(key)

    override suspend fun delete(key: String) = inner.delete(key)
}

private class ThrowingPlug(private val error: Exception) : BackendPlug {
    override suspend fun put(key: String, bytes: ByteArray) = throw error

    override suspend fun list(prefix: String): List<String> = throw error

    override suspend fun get(key: String): ByteArray? = throw error

    override suspend fun delete(key: String) = throw error
}

private fun replica(): Replica = Replica(
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema),
    DeviceId(1),
    TestClock(1_000),
)

class LiveSyncActionsTest : StringSpec({
    "a backend that empties after syncing shows as looking empty until confirmed" {
        val plug = MemoryPlug()
        val replica = replica()
        val settings = TodoSettings(MapSettings())

        coroutineScope {
            val live = LiveSyncActions(
                replica = replica,
                identity = StoredIdentity(MemoryRootStore()),
                settings = settings,
                folderPick = NoPick,
                fileStoreFactory = { error("no folder") },
                scope = this,
                relay = RelayOptions(plugFactory = { _, _ -> plug }),
            )
            live.setBackend(SyncBackend.Relay("https://relay.example"))
            yield()
            replica.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to byteArrayOf(1)))
            live.flushPendingChanges()
            live.syncNow()
            live.status.value.looksEmpty shouldBe false

            plug.list("todo/").forEach { plug.delete(it) }
            live.syncNow()
            live.status.value.looksEmpty shouldBe true
            plug.list("todo/") shouldBe emptyList()

            live.confirmEmpty()
            live.status.value.looksEmpty shouldBe false
            plug.list("todo/").size shouldBe 1

            live.setBackend(SyncBackend.None)
        }
    }

    "an unreachable folder shows as failing until a sync gets through" {
        val store = FlakyFileStore(PlainFileStore(tempdir()))
        val settings = TodoSettings(MapSettings()).apply { setSyncedFolderUri("content://folder") }

        coroutineScope {
            val live = LiveSyncActions(
                replica = replica(),
                identity = StoredIdentity(MemoryRootStore()),
                settings = settings,
                folderPick = NoPick,
                fileStoreFactory = { store },
                scope = this,
            )
            live.startSyncIfConfigured()
            yield()

            live.syncNow()
            live.status.value.failing shouldBe true
            live.status.value.lastSync.shouldBeNull()

            store.down = false
            live.syncNow()
            live.status.value.failing shouldBe false
            live.status.value.lastSync.shouldNotBeNull()

            live.clearFolder()
        }
    }

    "switching backend stops the old sync" {
        val store = FlakyFileStore(PlainFileStore(tempdir())).apply { down = false }
        val settings = TodoSettings(MapSettings()).apply { setSyncedFolderUri("content://folder") }
        val relay = CountingPlug()

        coroutineScope {
            val live = LiveSyncActions(
                replica = replica(),
                identity = StoredIdentity(MemoryRootStore()),
                settings = settings,
                folderPick = NoPick,
                fileStoreFactory = { store },
                scope = this,
                relay = RelayOptions(plugFactory = { _, _ -> relay }),
            )
            live.startSyncIfConfigured()
            yield()
            live.syncNow()
            val folderLists = store.lists
            folderLists shouldBeGreaterThan 0

            live.setBackend(SyncBackend.Relay("https://relay.example"))
            yield()
            live.syncNow()

            store.lists shouldBe folderLists
            relay.lists shouldBeGreaterThan 0
            live.backend.value shouldBe SyncBackend.Relay("https://relay.example")
            live.setBackend(SyncBackend.None)
        }
    }

    "only https relay URLs are allowed unless the debug build opts in" {
        isAllowedServerUrl("https://relay.example", allowInsecure = false) shouldBe true
        isAllowedServerUrl("http://127.0.0.1:8080", allowInsecure = false) shouldBe false
        isAllowedServerUrl("http://127.0.0.1:8080", allowInsecure = true) shouldBe true
        isAllowedServerUrl("https://", allowInsecure = true) shouldBe false
        isAllowedServerUrl("relay.example", allowInsecure = true) shouldBe false
    }

    "a joined relay URL becomes the chosen backend" {
        val settings = TodoSettings(MapSettings())
        coroutineScope {
            val live = LiveSyncActions(
                replica = replica(),
                identity = StoredIdentity(MemoryRootStore()),
                settings = settings,
                folderPick = NoPick,
                fileStoreFactory = { FlakyFileStore(PlainFileStore(tempdir())) },
                scope = this,
                relay = RelayOptions(plugFactory = { _, _ -> CountingPlug() }),
            )

            live.applyJoinedBackend(BackendType.Relay, "https://relay.example".encodeToByteArray())
            yield()
            live.backend.value shouldBe SyncBackend.Relay("https://relay.example")

            live.applyJoinedBackend(BackendType.Relay, "http://relay.example".encodeToByteArray())
            live.backend.value shouldBe SyncBackend.None

            live.applySwitchedBackend(BackendType.Relay, "https://relay.example".encodeToByteArray()) shouldBe true
            yield()
            live.backend.value shouldBe SyncBackend.Relay("https://relay.example")
            // A refused switch keeps the backend the device had; a join would have cleared it.
            live.applySwitchedBackend(BackendType.Relay, "http://relay.example".encodeToByteArray()) shouldBe false
            live.applySwitchedBackend(BackendType.Folder, ByteArray(0)) shouldBe false
            live.backend.value shouldBe SyncBackend.Relay("https://relay.example")

            live.setBackend(SyncBackend.None)
        }
    }

    "polling follows a backend chosen after the screen became visible" {
        val settings = TodoSettings(MapSettings())
        val relay = CountingPlug()

        coroutineScope {
            val live = LiveSyncActions(
                replica = replica(),
                identity = StoredIdentity(MemoryRootStore()),
                settings = settings,
                folderPick = NoPick,
                fileStoreFactory = { FlakyFileStore(PlainFileStore(tempdir())) },
                scope = this,
                relay = RelayOptions(plugFactory = { _, _ -> relay }),
            )
            live.startPolling() // no backend yet, common at first onStart
            live.setBackend(SyncBackend.Relay("https://relay.example"))
            withTimeout(2_000) {
                while (relay.lists == 0) yield()
            }
            relay.lists shouldBeGreaterThan 0
            live.stopPolling()
            live.setBackend(SyncBackend.None)
        }
    }

    "relay connect checks the URL and lists first; refused or unreachable, it keeps the backend" {
        val settings = TodoSettings(MapSettings())
        var plug: BackendPlug = CountingPlug()

        coroutineScope {
            val live = LiveSyncActions(
                replica = replica(),
                identity = StoredIdentity(MemoryRootStore()),
                settings = settings,
                folderPick = NoPick,
                fileStoreFactory = { error("no folder") },
                scope = this,
                relay = RelayOptions(plugFactory = { _, _ -> plug }),
            )
            live.connectRelay("relay.example") shouldBe RelaySetupError.Invalid
            live.connectRelay("http://relay.example") shouldBe RelaySetupError.Insecure
            live.connectRelay(" https://relay.example/ ").shouldBeNull()
            live.backend.value shouldBe SyncBackend.Relay("https://relay.example")

            plug = ThrowingPlug(RelayRefused(RelayRefusal.Unauthorized, 401))
            live.connectRelay("https://other.example") shouldBe RelaySetupError.Refused(RelayRefusal.Unauthorized)
            plug = ThrowingPlug(IOException("no route"))
            live.connectRelay("https://other.example") shouldBe RelaySetupError.Unreachable
            live.backend.value shouldBe SyncBackend.Relay("https://relay.example")
            live.setBackend(SyncBackend.None)
        }
    }

    "without a relay plug, connect says so" {
        coroutineScope {
            val live = LiveSyncActions(
                replica = replica(),
                identity = StoredIdentity(MemoryRootStore()),
                settings = TodoSettings(MapSettings()),
                folderPick = NoPick,
                fileStoreFactory = { error("no folder") },
                scope = this,
            )
            live.connectRelay("https://relay.example") shouldBe RelaySetupError.Unsupported
            live.backend.value shouldBe SyncBackend.None
        }
    }

    "a relay refusal is carried on the status" {
        val refusal = RelayRefused(RelayRefusal.Unauthorized, 401)
        val settings = TodoSettings(MapSettings())

        coroutineScope {
            val live = LiveSyncActions(
                replica = replica(),
                identity = StoredIdentity(MemoryRootStore()),
                settings = settings,
                folderPick = NoPick,
                fileStoreFactory = { FlakyFileStore(PlainFileStore(tempdir())) },
                scope = this,
                relay = RelayOptions(plugFactory = { _, _ -> ThrowingPlug(refusal) }),
            )
            live.setBackend(SyncBackend.Relay("https://relay.example"))
            yield()
            live.syncNow()

            live.status.value.failing shouldBe true
            live.status.value.refused?.refusal shouldBe RelayRefusal.Unauthorized
            live.status.value.refused?.status shouldBe 401
            live.setBackend(SyncBackend.None)
        }
    }
})
