package com.msuite.todo.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.model.DeviceId
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.FileStore
import com.msuite.core.sync.Replica
import com.msuite.core.sync.testing.PlainFileStore
import com.msuite.core.sync.testing.TestClock
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
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

    private fun check() {
        if (down) throw IOException("cannot create directory")
    }

    override suspend fun read(path: String) = check().let { inner.read(path) }

    override suspend fun write(path: String, bytes: ByteArray) = check().let { inner.write(path, bytes) }

    override suspend fun list(directory: String) = check().let { inner.list(directory) }

    override suspend fun delete(path: String) = check().let { inner.delete(path) }

    override suspend fun mkdir(path: String) = check().let { inner.mkdir(path) }

    override suspend fun rename(from: String, to: String) = check().let { inner.rename(from, to) }

    override suspend fun exists(path: String) = check().let { inner.exists(path) }
}

private object NoPick : FolderPick {
    override suspend fun pick(): String? = null
}

class FolderSyncActionsTest : StringSpec({
    "an unreachable folder shows as failing until a sync gets through" {
        val store = FlakyFileStore(PlainFileStore(tempdir()))
        val settings = TodoSettings(MapSettings()).apply { setSyncedFolderUri("content://folder") }
        val replica = Replica(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema),
            DeviceId(1),
            TestClock(1_000),
        )

        coroutineScope {
            val folder =
                FolderSyncActions(replica, StoredIdentity(MemoryRootStore()), settings, NoPick, { store }, this)
            folder.startSyncIfConfigured()
            yield()

            folder.syncNow()
            folder.status.value.failing shouldBe true
            folder.status.value.lastSync.shouldBeNull()

            store.down = false
            folder.syncNow()
            folder.status.value.failing shouldBe false
            folder.status.value.lastSync.shouldNotBeNull()

            folder.clearFolder()
        }
    }
})
