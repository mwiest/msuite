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
import com.msuite.core.sync.testing.MemoryPlug
import com.msuite.core.sync.testing.TestClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope
import java.util.Properties
import kotlin.uuid.Uuid

private val local = DeviceId(1)
private val peer = DeviceId(2)
private val plugId = PlugId("folder")
private val root = ByteArray(32) { it.toByte() }

private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema)

class LiveSyncTest : StringSpec({
    "a published key names the local device, even when a peer's cell won the merge" {
        val record = Uuid.random()
        val replica = Replica(driver(), local, TestClock(1_000))
        val plug = MemoryPlug()
        replica.write(mapOf(CellKey(record, FieldId(1)) to byteArrayOf(1)))
        val peerCell = Cell(CellKey(record, FieldId(1)), byteArrayOf(2), Stamp(Hlc.of(2_000, 0), peer))
        replica.ingest(plugId, "peer-batch", Batch(Model.DATA_MODEL_VERSION, listOf(peerCell)))

        coroutineScope {
            val sync = LiveSync(replica, plug, plugId, "todo", root, this)
            sync.start()
            sync.flush()
            sync.stop()
        }

        val keys = plug.list("todo/")
        keys shouldHaveSize 1
        ObjectKey.parse(keys.single()).prefix.deviceId shouldBe local
    }
})
