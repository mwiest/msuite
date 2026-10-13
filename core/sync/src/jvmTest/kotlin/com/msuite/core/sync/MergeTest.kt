package com.msuite.core.sync

import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.Stamp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.uuid.Uuid

private val records = List(3) { Uuid.random() }

// A small key, clock and device space, so collisions and exact ties are common.
private val arbCell: Arb<Cell> = arbitrary {
    Cell(
        key = CellKey(Arb.element(records).bind(), FieldId(Arb.int(1..3).bind())),
        value = Arb.byteArray(Arb.int(0..2), Arb.byte()).bind(),
        stamp = Stamp(Hlc.of(Arb.long(0L..3L).bind(), Arb.int(0..1).bind()), DeviceId(Arb.long(-1L..1L).bind())),
    )
}
private val arbCells = Arb.list(arbCell, 0..12)

private fun state(cells: List<Cell>): Map<CellKey, Cell> = emptyMap<CellKey, Cell>().mergedWith(cells)

private fun Map<CellKey, Cell>.mergedWith(remote: List<Cell>): Map<CellKey, Cell> =
    this + merge(this::get, remote).associateBy { it.key }

private fun cell(value: Int, pt: Long, device: Long = 1, c: Int = 0) = Cell(
    key = CellKey(records[0], FieldId(1)),
    value = byteArrayOf(value.toByte()),
    stamp = Stamp(Hlc.of(pt, c), DeviceId(device)),
)

class MergeTest : StringSpec({
    "a remote cell with a newer stamp wins" {
        merge({ cell(1, pt = 1) }, listOf(cell(2, pt = 2))) shouldContainExactly listOf(cell(2, pt = 2))
    }

    "a remote cell with an older stamp loses" {
        merge({ cell(1, pt = 2) }, listOf(cell(2, pt = 1))).shouldBeEmpty()
    }

    "the counter orders cells within one millisecond" {
        merge({ cell(1, pt = 1, c = 0) }, listOf(cell(2, pt = 1, c = 1))) shouldContainExactly
            listOf(cell(2, pt = 1, c = 1))
    }

    "on equal HLCs the higher device ID wins, compared unsigned" {
        merge({ cell(1, pt = 1, device = 1) }, listOf(cell(2, pt = 1, device = -1))) shouldContainExactly
            listOf(cell(2, pt = 1, device = -1))
    }

    "on equal stamps the higher value wins, so both sides agree" {
        val low = cell(1, pt = 1)
        val high = cell(2, pt = 1)
        merge({ low }, listOf(high)) shouldContainExactly listOf(high)
        merge({ high }, listOf(low)).shouldBeEmpty()
    }

    "a remote cell for a new key is taken" {
        merge({ null }, listOf(cell(1, pt = 1))) shouldContainExactly listOf(cell(1, pt = 1))
    }

    "an identical cell is no change" {
        merge({ cell(1, pt = 1) }, listOf(cell(1, pt = 1))).shouldBeEmpty()
    }

    "several remote cells for one key collapse to their winner" {
        merge({ null }, listOf(cell(1, pt = 3), cell(2, pt = 5), cell(3, pt = 4))) shouldContainExactly
            listOf(cell(2, pt = 5))
    }

    "each key's winner is the max (stamp, value) over all inputs" {
        checkAll(arbCells, arbCells) { a, b ->
            val merged = state(a).mergedWith(b)
            val expected = (a + b).groupBy { it.key }.mapValues { (_, cells) ->
                cells.maxWith(LWW_ORDER)
            }
            merged shouldBe expected
        }
    }

    "merge is commutative" {
        checkAll(arbCells, arbCells) { a, b ->
            state(a).mergedWith(b) shouldBe state(b).mergedWith(a)
        }
    }

    "merge is associative" {
        checkAll(arbCells, arbCells, arbCells) { a, b, c ->
            state(a).mergedWith(b).mergedWith(c) shouldBe state(a).mergedWith(state(b).mergedWith(c).values.toList())
        }
    }

    "merging the same cells twice changes nothing" {
        checkAll(arbCells, arbCells) { a, b ->
            val once = state(a).mergedWith(b)
            merge(once::get, b).shouldBeEmpty()
        }
    }

    "the delivery order of remote cells doesn't matter" {
        checkAll(arbCells, arbCells) { a, b ->
            state(a).mergedWith(b) shouldBe state(a).mergedWith(b.reversed())
        }
    }
})
