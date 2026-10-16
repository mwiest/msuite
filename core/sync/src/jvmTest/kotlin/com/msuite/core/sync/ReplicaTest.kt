package com.msuite.core.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import com.msuite.core.sync.testing.TestClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val record = Uuid.random()
private val deviceA = DeviceId(1)
private val deviceB = DeviceId(2)
private val dayMs = HlcClock.MAX_AHEAD.inWholeMilliseconds

private fun key(field: Int) = CellKey(record, FieldId(field))

private fun driver(url: String = JdbcSqliteDriver.IN_MEMORY) = JdbcSqliteDriver(url, Properties(), LocalStore.Schema)

private fun remote(field: Int, pt: Long, value: Byte = 9) =
    Cell(key(field), byteArrayOf(value), Stamp(Hlc.of(pt, 0), deviceB))

private val folder = PlugId("folder")
private val relay = PlugId("relay")

class ReplicaTest : StringSpec({
    "a local write stamps its cells with one fresh HLC and stores them" {
        val driver = driver()
        val store = LocalStore(driver)
        val replica = Replica(driver, deviceA, TestClock(1_000))
        val written = replica.write(mapOf(key(1) to byteArrayOf(1), key(2) to byteArrayOf(2)))
        written.map { it.stamp }.distinct() shouldContainExactly listOf(Stamp(Hlc.of(1_000, 0), deviceA))
        store.cells() shouldContainExactlyInAnyOrder written
    }

    "an opened replica stamps with a device ID it keeps across restarts" {
        val url = "jdbc:sqlite:${tempfile(suffix = ".db").absolutePath}"
        val first = Replica.open(driver(url), TestClock(1_000), Random(1)).use {
            it.write(mapOf(key(1) to byteArrayOf(1)))
        }
        val again = Replica.open(driver(url), TestClock(2_000), Random(2)).use {
            it.write(mapOf(key(2) to byteArrayOf(1)))
        }
        first.single().stamp.deviceId shouldBe DeviceId.random(Random(1))
        again.single().stamp.deviceId shouldBe first.single().stamp.deviceId
    }

    "operations run in the replica's coroutine context, off the caller's thread" {
        val threads = mutableListOf<String>()
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000).also {
                threads += Thread.currentThread().name
            }
        }
        Executors.newSingleThreadExecutor { Thread(it, "replica-io") }.asCoroutineDispatcher().use { io ->
            Replica.open(driver(), clock, context = io).use { it.write(mapOf(key(1) to byteArrayOf(1))) }
        }
        threads.single() shouldStartWith "replica-io"
    }

    "after a restart with the wall clock set back, new writes still order after stored ones" {
        val url = "jdbc:sqlite:${tempfile(suffix = ".db").absolutePath}"
        val before = Replica(driver(url), deviceA, TestClock(5_000)).use { it.write(mapOf(key(1) to byteArrayOf(1))) }
        val after = Replica(driver(url), deviceA, TestClock(1_000)).use { it.write(mapOf(key(1) to byteArrayOf(2))) }
        after.single().stamp.hlc shouldBeGreaterThan before.single().stamp.hlc
    }

    "after a restart, new writes order after ingested remote cells" {
        val url = "jdbc:sqlite:${tempfile(suffix = ".db").absolutePath}"
        val ahead = remote(1, 1_000 + dayMs)
        Replica(driver(url), deviceA, TestClock(1_000)).use {
            it.ingest(folder, "k1", Batch(Model.DATA_MODEL_VERSION, listOf(ahead)))
        }
        val after = Replica(driver(url), deviceA, TestClock(1_000)).use { it.write(mapOf(key(2) to byteArrayOf(1))) }
        after.single().stamp.hlc shouldBeGreaterThan ahead.stamp.hlc
    }

    "ingest writes the winning cells and marks the batch ingested" {
        val driver = driver()
        val store = LocalStore(driver)
        val replica = Replica(driver, deviceA, TestClock(1_000))
        val local = replica.write(mapOf(key(1) to byteArrayOf(1))).single()
        val batch = Batch(Model.DATA_MODEL_VERSION, listOf(remote(1, 500), remote(2, 500)))
        replica.ingest(folder, "k1", batch) shouldBe IngestResult.Merged(listOf(remote(2, 500)))
        store.cells() shouldContainExactlyInAnyOrder listOf(local, remote(2, 500))
        store.plugs.isIngested(folder, "k1") shouldBe true
    }

    "a batch already ingested from a plug is skipped" {
        val driver = driver()
        val store = LocalStore(driver)
        val replica = Replica(driver, deviceA, TestClock(1_000))
        replica.ingest(folder, "k1", Batch(Model.DATA_MODEL_VERSION, listOf(remote(1, 500, value = 1))))
        store.write(listOf(remote(1, 400, value = 2)))
        replica.ingest(folder, "k1", Batch(Model.DATA_MODEL_VERSION, listOf(remote(1, 500, value = 1)))) shouldBe
            IngestResult.Merged(emptyList())
        store.cell(key(1)) shouldBe remote(1, 400, value = 2)
    }

    "a deferred batch writes nothing and stays unmarked" {
        val driver = driver()
        val store = LocalStore(driver)
        val replica = Replica(driver, deviceA, TestClock(1_000))
        val batch = Batch(Model.DATA_MODEL_VERSION, listOf(remote(1, 1_000 + dayMs + 1)))
        replica.ingest(folder, "k1", batch) shouldBe IngestResult.Deferred
        store.cells().shouldBeEmpty()
        store.plugs.isIngested(folder, "k1") shouldBe false
    }

    "a batch from a newer app version is held back and stays unmarked" {
        val driver = driver()
        val store = LocalStore(driver)
        val replica = Replica(driver, deviceA, TestClock(1_000))
        val batch = Batch(Model.DATA_MODEL_VERSION + 1, listOf(remote(1, 500)))
        replica.ingest(folder, "k1", batch) shouldBe IngestResult.TooNew
        store.cells().shouldBeEmpty()
        store.plugs.isIngested(folder, "k1") shouldBe false
    }

    "a delta holds what wasn't pushed to that plug yet" {
        val replica = Replica(driver(), deviceA, TestClock(1_000))
        val first = replica.write(mapOf(key(1) to byteArrayOf(1)))
        val delta = replica.delta(folder).shouldNotBeNull()
        delta.cells shouldContainExactly first
        replica.pushed(folder, delta)
        replica.delta(folder).shouldBeNull()
        val second = replica.write(mapOf(key(2) to byteArrayOf(2)))
        replica.delta(folder).shouldNotBeNull().cells shouldContainExactly second
        replica.delta(relay).shouldNotBeNull().cells shouldContainExactlyInAnyOrder first + second
    }

    "two replicas converge through their databases" {
        val a = Replica(driver(), deviceA, TestClock(1_000))
        val b = Replica(driver(), deviceB, TestClock(2_000))
        a.write(mapOf(key(1) to byteArrayOf(1), key(2) to byteArrayOf(1)))
        b.write(mapOf(key(2) to byteArrayOf(2), key(3) to byteArrayOf(2)))
        val fromA = a.delta(folder).shouldNotBeNull()
        val fromB = b.delta(folder).shouldNotBeNull()
        a.ingest(folder, "b1", fromB)
        b.ingest(folder, "a1", fromA)
        a.cells() shouldContainExactlyInAnyOrder b.cells()
        a.cells().single { it.key == key(2) }.stamp.deviceId shouldBe deviceB
    }

    "concurrent writes get distinct HLCs" {
        val replica = Replica(driver(), deviceA, TestClock(1_000))
        val written = coroutineScope {
            (1..50).map { field ->
                async(Dispatchers.Default) { replica.write(mapOf(key(field) to byteArrayOf(1))).single() }
            }.awaitAll()
        }
        written.map { it.stamp.hlc }.distinct() shouldHaveSize 50
        replica.cells() shouldContainExactlyInAnyOrder written
    }

    "local writes and ingests that change state tick, nothing else does" {
        val replica = Replica(driver(), deviceA, TestClock(1_000))
        replica.changes.value shouldBe 0
        replica.write(mapOf(key(1) to byteArrayOf(1)))
        replica.changes.value shouldBe 1
        replica.ingest(folder, "k1", Batch(Model.DATA_MODEL_VERSION, listOf(remote(2, 500))))
        replica.changes.value shouldBe 2
        replica.ingest(folder, "k2", Batch(Model.DATA_MODEL_VERSION, listOf(remote(1, 500))))
        replica.ingest(folder, "k1", Batch(Model.DATA_MODEL_VERSION, listOf(remote(3, 500))))
        replica.ingest(folder, "k3", Batch(Model.DATA_MODEL_VERSION, listOf(remote(3, 1_000 + dayMs + 1))))
        replica.write(emptyMap())
        replica.pushed(folder, replica.delta(folder).shouldNotBeNull())
        replica.changes.value shouldBe 2
    }

    "a record's cells read back alone" {
        val replica = Replica(driver(), deviceA, TestClock(1_000))
        val written = replica.write(
            mapOf(key(1) to byteArrayOf(1), CellKey(Uuid.random(), FieldId(1)) to byteArrayOf(2)),
        )
        replica.cells(record) shouldContainExactly written.filter { it.key.recordId == record }
    }
})
