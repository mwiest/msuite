package com.msuite.core.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.MalformedBatch
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

private fun key(field: Int) = CellKey(record, FieldId(field))

private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema)

class ManualSyncTest : StringSpec({
    "two replicas converge through an exported snapshot, both ways" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val b = Replica(driver(), DeviceId(2), TestClock(2_000))
        a.write(mapOf(key(1) to byteArrayOf(1), key(2) to byteArrayOf(1)))
        b.write(mapOf(key(2) to byteArrayOf(2), key(3) to byteArrayOf(2)))

        ManualSync(b).import(ManualSync(a).export())
        ManualSync(a).import(ManualSync(b).export())

        a.cells() shouldContainExactlyInAnyOrder b.cells()
        a.cells().single { it.key == key(2) }.stamp.deviceId shouldBe DeviceId(2)
    }

    "importing the same snapshot twice changes nothing the second time" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        val b = Replica(driver(), DeviceId(2), TestClock(1_000))
        a.write(mapOf(key(1) to byteArrayOf(1)))
        val snapshot = ManualSync(a).export()
        val sync = ManualSync(b)
        val first = sync.import(snapshot) as IngestResult.Merged
        first.changes shouldContainExactlyInAnyOrder a.cells()
        sync.import(snapshot) shouldBe IngestResult.Merged(emptyList())
    }

    "a snapshot from a clock far ahead is deferred and writes nothing" {
        val ahead = Replica(driver(), DeviceId(1), TestClock(1_000 + dayMs + 1))
        val behind = Replica(driver(), DeviceId(2), TestClock(1_000))
        ahead.write(mapOf(key(1) to byteArrayOf(1)))
        ManualSync(behind).import(ManualSync(ahead).export()) shouldBe IngestResult.Deferred
        behind.cells().shouldBeEmpty()
    }

    "bytes that are not a snapshot are rejected" {
        val a = Replica(driver(), DeviceId(1), TestClock(1_000))
        shouldThrow<MalformedBatch> { ManualSync(a).import(byteArrayOf(0x08)) }
    }
})
