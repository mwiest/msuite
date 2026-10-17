package com.msuite.core.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.crypto.BatchEnvelope
import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.Batch
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.MalformedBatch
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.testing.TestClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.util.Properties
import kotlin.uuid.Uuid

private val record = Uuid.random()
private val dayMs = HlcClock.MAX_AHEAD.inWholeMilliseconds
private val root = ByteArray(32) { it.toByte() }
private val otherRoot = ByteArray(32) { (it + 1).toByte() }

private fun key(field: Int) = CellKey(record, FieldId(field))

private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema)

private fun identity(secret: ByteArray = root) =
    IdentityProvider { keyGen -> if (keyGen == KEY_GEN_V1) secret else null }

private fun sync(replica: Replica, secret: ByteArray = root) = ManualSync(replica, identity(secret))

class ManualSyncTest : StringSpec({
    "two replicas converge through an exported snapshot, both ways" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val b = Replica(driver(), DeviceId(2), TestClock(2_000))
        a.write(mapOf(key(1) to byteArrayOf(1), key(2) to byteArrayOf(1)))
        b.write(mapOf(key(2) to byteArrayOf(2), key(3) to byteArrayOf(2)))

        sync(b).import(sync(a).export())
        sync(a).import(sync(b).export())

        a.cells() shouldContainExactlyInAnyOrder b.cells()
        a.cells().single { it.key == key(2) }.stamp.deviceId shouldBe DeviceId(2)
    }

    "importing the same snapshot twice changes nothing the second time" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val b = Replica(driver(), DeviceId(2), TestClock(1_000))
        a.write(mapOf(key(1) to byteArrayOf(1)))
        val snapshot = sync(a).export()
        val sync = sync(b)
        val first = sync.import(snapshot) as IngestResult.Merged
        first.changes shouldContainExactlyInAnyOrder a.cells()
        sync.import(snapshot) shouldBe IngestResult.Merged(emptyList())
    }

    "a snapshot from a clock far ahead is deferred and writes nothing" {
        val ahead = Replica(driver(), DeviceId(1), TestClock(1_000 + dayMs + 1))
        val behind = Replica(driver(), DeviceId(2), TestClock(1_000))
        ahead.write(mapOf(key(1) to byteArrayOf(1)))
        sync(behind).import(sync(ahead).export()) shouldBe IngestResult.Deferred
        behind.cells().shouldBeEmpty()
    }

    "bytes that are not an envelope are rejected" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        shouldThrow<MalformedBatch> { sync(a).import(byteArrayOf(0x08)) }
    }

    "the export is ciphertext, holding no plaintext" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val text = ValueCodec.encode(Value.Text("top secret title"))
        a.write(mapOf(key(1) to text))
        sync(a).export().toList().windowed(text.size).none { it == text.toList() } shouldBe true
    }

    "a too-new header is held back, not merged" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val b = Replica(driver(), DeviceId(2), TestClock(1_000))
        a.write(mapOf(key(1) to byteArrayOf(1)))
        val sealed = BatchEnvelope(ProtoBatchEncoder, { root }).seal(
            Batch(Model.DATA_MODEL_VERSION + 1, a.cells()),
            MANUAL_AAD_CONTEXT,
        )
        sync(b).import(sealed) shouldBe IngestResult.TooNew
        b.cells().shouldBeEmpty()
    }

    "a header under an unknown keyGen is skipped, not merged" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val b = Replica(driver(), DeviceId(2), TestClock(1_000))
        a.write(mapOf(key(1) to byteArrayOf(1)))
        val sealed = BatchEnvelope(ProtoBatchEncoder, { root }, KEY_GEN_V1 + 1).seal(
            Batch(Model.DATA_MODEL_VERSION, a.cells()),
            MANUAL_AAD_CONTEXT,
        )
        sync(b).import(sealed) shouldBe IngestResult.UnknownKey
        b.cells().shouldBeEmpty()
    }

    "a file from a foreign root fails to open" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val b = Replica(driver(), DeviceId(2), TestClock(1_000))
        a.write(mapOf(key(1) to byteArrayOf(1)))
        shouldThrow<MalformedBatch> { sync(b, otherRoot).import(sync(a, root).export()) }
    }
})
