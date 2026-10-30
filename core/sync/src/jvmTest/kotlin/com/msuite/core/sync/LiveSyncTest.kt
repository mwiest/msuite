package com.msuite.core.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.Model
import com.msuite.core.model.PlugId
import com.msuite.core.model.Stamp
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.testing.MemoryPlug
import com.msuite.core.sync.testing.TestClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.coroutineScope
import java.util.Properties
import kotlin.uuid.Uuid

private val local = DeviceId(1)
private val peer = DeviceId(2)
private val plugId = PlugId("folder")
private val root = ByteArray(32) { it.toByte() }
private val otherRoot = ByteArray(32) { (it + 1).toByte() }
private val title = "secret todo title".encodeToByteArray()

private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema)

private fun identity(secret: ByteArray = root) = IdentityProvider { keyGen -> secret.takeIf { keyGen == KEY_GEN_V1 } }

private suspend fun publish(replica: Replica, plug: BackendPlug, secret: ByteArray = root) = coroutineScope {
    val sync = LiveSync(replica, plug, plugId, "todo", identity(secret), this)
    sync.start()
    sync.flush()
    sync.stop()
}

private suspend fun pull(replica: Replica, plug: BackendPlug, secret: ByteArray = root) = coroutineScope {
    val sync = LiveSync(replica, plug, plugId, "todo", identity(secret), this)
    sync.start()
    sync.pull()
    sync.stop()
}

private fun ByteArray.contains(part: ByteArray): Boolean =
    (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

/** A plug that throws like an unreachable folder while [down] is set. */
private class FlakyPlug(private val inner: BackendPlug = MemoryPlug()) : BackendPlug {
    var down = true

    private fun check() {
        if (down) throw java.io.IOException("cannot create directory")
    }

    override suspend fun put(key: String, bytes: ByteArray) = check().let { inner.put(key, bytes) }

    override suspend fun list(prefix: String): List<String> = check().let { inner.list(prefix) }

    override suspend fun get(key: String): ByteArray? = check().let { inner.get(key) }

    override suspend fun delete(key: String) = check().let { inner.delete(key) }
}

/** A plug whose first put reports success but lands where the listing never shows it (a twin folder). */
private class LossyPlug(private val inner: BackendPlug = MemoryPlug()) : BackendPlug by inner {
    var lose = 1

    override suspend fun put(key: String, bytes: ByteArray) {
        if (lose > 0) lose-- else inner.put(key, bytes)
    }
}

class LiveSyncTest : StringSpec({
    "a published key names the local device, even when a peer's cell won the merge" {
        val record = Uuid.random()
        val replica = Replica(driver(), local, TestClock(1_000))
        val plug = MemoryPlug()
        replica.write(mapOf(CellKey(record, FieldId(1)) to byteArrayOf(1)))
        val peerCell = Cell(CellKey(record, FieldId(1)), byteArrayOf(2), Stamp(Hlc.of(2_000, 0), peer))
        replica.ingest(plugId, "peer-batch", Batch(Model.DATA_MODEL_VERSION, listOf(peerCell)))

        publish(replica, plug)

        val keys = plug.list("todo/")
        keys shouldHaveSize 1
        ObjectKey.parse(keys.single()).prefix.deviceId shouldBe local
    }

    "a published batch is ciphertext, addressed by the hash of its bytes" {
        val replica = Replica(driver(), local, TestClock(1_000))
        val plug = MemoryPlug()
        replica.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))

        publish(replica, plug)

        val key = plug.list("todo/").single()
        val bytes = plug.get(key)!!
        bytes.contains(title) shouldBe false
        ObjectKey.parse(key).hash shouldBe ciphertextHash(bytes)
    }

    "a peer holding the same root reads what was published" {
        val writer = Replica(driver(), local, TestClock(1_000))
        val reader = Replica(driver(), peer, TestClock(1_000))
        val plug = MemoryPlug()
        val written = writer.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))

        publish(writer, plug)
        pull(reader, plug)

        reader.cells() shouldContainExactlyInAnyOrder written
    }

    "a batch under a foreign root is not merged and counts as unreadable" {
        val writer = Replica(driver(), local, TestClock(1_000))
        val reader = Replica(driver(), peer, TestClock(1_000))
        val plug = MemoryPlug()
        writer.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))

        publish(writer, plug, secret = otherRoot)
        // Same namespace, so the reader lists the foreign object: put it under the reader's group.
        val foreignKey = plug.list("todo/").single()
        val readerKey = foreignKey.replace(groupId(otherRoot), groupId(root))
        plug.put(readerKey, plug.get(foreignKey)!!)

        repeat(MAX_DECODE_ATTEMPTS) { pull(reader, plug) }

        reader.cells().shouldBeEmpty()
        reader.isCorrupt(plugId, readerKey) shouldBe true
    }

    "a batch moved under another key fails to open" {
        val writer = Replica(driver(), local, TestClock(1_000))
        val reader = Replica(driver(), peer, TestClock(1_000))
        val plug = MemoryPlug()
        writer.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))
        publish(writer, plug)
        val key = plug.list("todo/").single()
        val moved = key.replace("_d_", "_s_")
        moved shouldNotBe key
        plug.put(moved, plug.get(key)!!)
        plug.delete(key)

        pull(reader, plug)

        reader.cells().shouldBeEmpty()
    }

    "names that are not object keys, like a sync client's conflict copies, are skipped" {
        val writer = Replica(driver(), local, TestClock(1_000))
        val reader = Replica(driver(), peer, TestClock(1_000))
        val plug = MemoryPlug()
        val written = writer.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))
        publish(writer, plug)
        val key = plug.list("todo/").single()
        plug.put("$key (1)", ByteArray(0))
        plug.put("${key}_5110404403298289004", plug.get(key)!!)

        pull(reader, plug)

        reader.cells() shouldContainExactlyInAnyOrder written
        reader.isCorrupt(plugId, "$key (1)") shouldBe false
    }

    "a pull deletes conflict copies of a listed key, and nothing else" {
        val writer = Replica(driver(), local, TestClock(1_000))
        val plug = MemoryPlug()
        writer.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))
        publish(writer, plug)
        val key = plug.list("todo/").single()
        val absent = key.dropLast(1) + if (key.last() == 'A') 'B' else 'A'
        val namespace = key.substringBeforeLast('/')
        plug.put("$key (1)", ByteArray(0))
        plug.put("$key (12)", ByteArray(0))
        plug.put("${key}_5110404403298289004", plug.get(key)!!)
        plug.put("$absent (1)", ByteArray(0))
        plug.put("$namespace/notes.txt", byteArrayOf(1))
        plug.put("$key (copy)", ByteArray(0))

        pull(Replica(driver(), peer, TestClock(1_000)), plug)

        plug.list("todo/") shouldContainExactlyInAnyOrder
            listOf(key, "$absent (1)", "$namespace/notes.txt", "$key (copy)")
    }

    "a publish to an unreachable plug doesn't throw, and the next pull retries it" {
        val replica = Replica(driver(), local, TestClock(1_000))
        val plug = FlakyPlug()
        val written = replica.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))

        coroutineScope {
            val sync = LiveSync(replica, plug, plugId, "todo", identity(), this)
            sync.start()
            sync.flush()
            sync.healthy.value shouldBe false

            plug.down = false
            sync.pull()
            sync.healthy.value shouldBe true
            sync.stop()
        }

        val reader = Replica(driver(), peer, TestClock(1_000))
        pull(reader, plug)
        reader.cells() shouldContainExactlyInAnyOrder written
    }

    "a pull from an unreachable plug doesn't throw and reports the plug unhealthy" {
        val replica = Replica(driver(), local, TestClock(1_000))
        coroutineScope {
            val sync = LiveSync(replica, FlakyPlug(), plugId, "todo", identity(), this)
            sync.start()
            sync.pull()
            sync.healthy.value shouldBe false
            sync.stop()
        }
    }

    "a publish the plug never lists is sent again after a few pulls" {
        val replica = Replica(driver(), local, TestClock(1_000))
        val plug = LossyPlug()
        val written = replica.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))

        coroutineScope {
            val sync = LiveSync(replica, plug, plugId, "todo", identity(), this)
            sync.start()
            sync.flush()
            plug.list("todo/").shouldBeEmpty()
            repeat(MAX_CONFIRM_MISSES) { sync.pull() }
            sync.stop()
        }

        plug.list("todo/") shouldHaveSize 1
        val reader = Replica(driver(), peer, TestClock(1_000))
        pull(reader, plug)
        reader.cells() shouldContainExactlyInAnyOrder written
    }

    "a publish the plug lists is not sent again" {
        val replica = Replica(driver(), local, TestClock(1_000))
        val plug = MemoryPlug()
        replica.write(mapOf(CellKey(Uuid.random(), FieldId(1)) to title))

        coroutineScope {
            val sync = LiveSync(replica, plug, plugId, "todo", identity(), this)
            sync.start()
            sync.flush()
            repeat(MAX_CONFIRM_MISSES + 1) { sync.pull() }
            sync.flush()
            sync.stop()
        }

        plug.list("todo/") shouldHaveSize 1
    }
})
