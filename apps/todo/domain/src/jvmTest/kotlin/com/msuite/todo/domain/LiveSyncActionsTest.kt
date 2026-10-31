package com.msuite.todo.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.crypto.BackendType
import com.msuite.core.model.DeviceId
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
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.Properties

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
        isAllowedRelayUrl("https://relay.example", allowInsecure = false) shouldBe true
        isAllowedRelayUrl("http://127.0.0.1:8080", allowInsecure = false) shouldBe false
        isAllowedRelayUrl("http://127.0.0.1:8080", allowInsecure = true) shouldBe true
        isAllowedRelayUrl("https://", allowInsecure = true) shouldBe false
        isAllowedRelayUrl("relay.example", allowInsecure = true) shouldBe false
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

            live.setBackend(SyncBackend.None)
        }
    }

    "a relay refusal is carried on the status" {
        val refusal = RelayRefused("unauthorized", 401)
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
            live.status.value.refused?.reason shouldBe "unauthorized"
            live.status.value.refused?.status shouldBe 401
            live.setBackend(SyncBackend.None)
        }
    }
})
