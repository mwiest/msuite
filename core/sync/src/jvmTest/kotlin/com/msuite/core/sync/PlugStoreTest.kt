package com.msuite.core.sync

import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.Model
import com.msuite.core.model.Stamp
import com.msuite.core.sync.testing.Fetch
import com.msuite.core.sync.testing.MemoryPlug
import com.msuite.core.sync.testing.PlugStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid

private fun batch(value: Byte, record: Long = 0x1111) = Batch(
    Model.DATA_MODEL_VERSION,
    listOf(Cell(CellKey(Uuid.fromLongs(record, 0), FieldId(1)), byteArrayOf(value), Stamp(Hlc.of(1, 0), DeviceId(1)))),
)

class PlugStoreTest : StringSpec({
    "two stores over one plug publish without clashing" {
        val plug = MemoryPlug()
        val first = PlugStore(plug)
        val second = PlugStore(plug)
        val keyOne = first.put(batch(1))
        val keyTwo = second.put(batch(2))
        keyOne shouldNotBe keyTwo
        (first.get(keyOne) as Fetch.Read).batch.cells shouldBe batch(1).cells
        (second.get(keyTwo) as Fetch.Read).batch.cells shouldBe batch(2).cells
        runBlocking { plug.list("sim/") } shouldContainExactlyInAnyOrder listOf(keyOne, keyTwo)
    }

    "publishing the same batch twice is one object under one key" {
        val store = PlugStore(MemoryPlug())
        store.put(batch(1)) shouldBe store.put(batch(1))
        store.keys() shouldContainExactly listOf(store.put(batch(1)))
    }

    "keys come back sorted even though the plug lists them jumbled" {
        val store = PlugStore(MemoryPlug(seed = 7))
        val keys = (0..9).map { store.put(batch(it.toByte(), record = it.toLong())) }
        store.keys() shouldBe keys.sorted()
        store.keys().toSet() shouldBe keys.toSet()
    }
})
