package com.msuite.todo.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.crypto.BatchEnvelope
import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.Batch
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.HlcClock
import com.msuite.core.sync.MANUAL_AAD_CONTEXT
import com.msuite.core.sync.ManualSync
import com.msuite.core.sync.Replica
import com.msuite.core.sync.testing.TestClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.util.Properties
import kotlin.uuid.Uuid

private val record = Uuid.random()
private val root = ByteArray(32) { it.toByte() }

private fun key(field: Int) = CellKey(record, FieldId(field))

private fun replica(device: Long, wallMillis: Long) = Replica(
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema),
    DeviceId(device),
    TestClock(wallMillis),
)

private fun sync(replica: Replica, secret: ByteArray = root) =
    ManualSync(replica, IdentityProvider { keyGen -> if (keyGen == KEY_GEN_V1) secret else null })

/** A [SnapshotFiles] handing one outcome back; [beforeWrite] runs between the pick and the write. */
private class FakeFiles(var outcome: SnapshotFiles.Outcome) : SnapshotFiles {
    var beforeWrite: suspend () -> Unit = {}
    var exported: ByteArray? = null

    override suspend fun export(bytes: suspend () -> ByteArray): SnapshotFiles.Outcome {
        if (outcome is SnapshotFiles.Outcome.Written) {
            beforeWrite()
            exported = bytes()
        }
        return outcome
    }

    override suspend fun import(): SnapshotFiles.Outcome = outcome
}

class SyncActionsTest : StringSpec({
    "a cancelled or failed export says which it was" {
        val sync = SyncActions(sync(replica(1, 1_000)), FakeFiles(SnapshotFiles.Outcome.Cancelled))
        sync.export() shouldBe SyncResult.Cancelled
        val files = FakeFiles(SnapshotFiles.Outcome.Failed)
        SyncActions(sync(replica(1, 1_000)), files).export() shouldBe SyncResult.WriteFailed
    }

    "the snapshot is taken when the file is written, not before the pick" {
        val a = replica(1, 1_000)
        a.write(mapOf(key(1) to byteArrayOf(1)))
        val files = FakeFiles(SnapshotFiles.Outcome.Written)
        // The user keeps editing while the picker is open; those edits belong in the file.
        files.beforeWrite = { a.write(mapOf(key(2) to byteArrayOf(2))) }
        SyncActions(sync(a), files).export() shouldBe SyncResult.Exported
        val b = replica(2, 1_000)
        sync(b).import(files.exported!!)
        b.cells() shouldContainExactlyInAnyOrder a.cells()
    }

    "an imported snapshot merges and says imported" {
        val a = replica(1, 1_000)
        a.write(mapOf(key(1) to byteArrayOf(1)))
        val bytes = sync(a).export()
        val b = replica(2, 1_000)
        SyncActions(sync(b), FakeFiles(SnapshotFiles.Outcome.Read(bytes))).import() shouldBe SyncResult.Imported
        b.cells() shouldBe a.cells()
    }

    "a cancelled, unreadable or malformed file says what happened" {
        val b = replica(2, 1_000)
        SyncActions(sync(b), FakeFiles(SnapshotFiles.Outcome.Cancelled)).import() shouldBe SyncResult.Cancelled
        SyncActions(sync(b), FakeFiles(SnapshotFiles.Outcome.Failed)).import() shouldBe SyncResult.Failed
        SyncActions(sync(b), FakeFiles(SnapshotFiles.Outcome.Read(byteArrayOf(0x08)))).import() shouldBe
            SyncResult.Failed
    }

    "a far-future, newer-version or unknown-key file is said as such" {
        val ahead = replica(1, HlcClock.MAX_AHEAD.inWholeMilliseconds + 10_000)
        ahead.write(mapOf(key(1) to byteArrayOf(1)))
        val b = replica(2, 1_000)
        SyncActions(sync(b), FakeFiles(SnapshotFiles.Outcome.Read(sync(ahead).export())))
            .import() shouldBe SyncResult.Deferred

        val newer = BatchEnvelope(ProtoBatchEncoder, { root }).seal(
            Batch(Model.DATA_MODEL_VERSION + 1, ahead.cells()),
            MANUAL_AAD_CONTEXT,
        )
        SyncActions(sync(b), FakeFiles(SnapshotFiles.Outcome.Read(newer))).import() shouldBe SyncResult.TooNew

        val unknownKey = BatchEnvelope(ProtoBatchEncoder, { root }, KEY_GEN_V1 + 1).seal(
            Batch(Model.DATA_MODEL_VERSION, ahead.cells()),
            MANUAL_AAD_CONTEXT,
        )
        SyncActions(sync(b), FakeFiles(SnapshotFiles.Outcome.Read(unknownKey))).import() shouldBe
            SyncResult.UnknownKey
    }

    "a file from a foreign root fails" {
        val a = replica(1, 1_000)
        a.write(mapOf(key(1) to byteArrayOf(1)))
        val b = replica(2, 1_000)
        SyncActions(sync(b, secret = ByteArray(32)), FakeFiles(SnapshotFiles.Outcome.Read(sync(a).export())))
            .import() shouldBe SyncResult.Failed
    }
})
