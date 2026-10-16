package com.msuite.core.sync

import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.CoreFields
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.FractionalIndex
import com.msuite.core.model.Hlc
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.Stamp
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import com.msuite.core.sync.testing.BatchStore
import com.msuite.core.sync.testing.Cluster
import com.msuite.core.sync.testing.MemoryState
import com.msuite.core.sync.testing.NetStep
import com.msuite.core.sync.testing.SimBackend
import com.msuite.core.sync.testing.SimDevice
import com.msuite.core.sync.testing.SimStep
import com.msuite.core.sync.testing.SimStore
import com.msuite.core.sync.testing.TestClock
import com.msuite.core.sync.testing.batches
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.uuid.Uuid

/** Writes a [field] of a [record], both taken modulo their counts, with a value derived from [seed]. */
data class Write(override val device: Int, val record: Int, val field: Int, val seed: Long) : SimStep

/** The fields of the simulated records, each with its own kind of value. */
enum class SimField(val id: FieldId) {
    TITLE(FieldId(10)),
    DONE(FieldId(11)),
    POSITION(FieldId(12)),
    DELETED(CoreFields.DELETED),
}

/**
 * The Layer 1 merge/HLC simulator (decision 0007, docs/testing.md): generic [Write]s on a [Cluster],
 * whose devices keep their state in memory or in a [Replica] over SQLite ([backend]).
 */
class Simulation(
    wallOffsets: List<Long>,
    seed: Long,
    recordCount: Int = 4,
    backend: SimBackend = ::MemoryState,
    store: SimStore = BatchStore(),
    snapshots: Boolean = false,
) : Cluster(wallOffsets, seed, backend, store, snapshots) {
    val records = List(recordCount) { Uuid.fromLongs(seed, it.toLong()) }

    /** Every cell ever written, the oracle for the LWW invariant. */
    val writes = mutableListOf<Cell>()

    fun run(step: SimStep) {
        when (step) {
            is NetStep -> run(step)
            is Write -> write(device(step), records[step.record.mod(records.size)], step.field, step.seed)
            else -> error("unknown step $step")
        }
        checkClockBound(device(step))
    }

    fun write(device: SimDevice, record: Uuid, field: Int, seed: Long) {
        val simField = SimField.entries[field.mod(SimField.entries.size)]
        val seen = device.cells().values
        val value = when (simField) {
            SimField.TITLE -> Value.Text("t$seed")
            SimField.DONE, SimField.DELETED -> Value.Bool(seed % 2 == 0L)
            SimField.POSITION -> Value.Text(newPosition(device, record, seed))
        }
        val cell = device.state.write(CellKey(record, simField.id), ValueCodec.encode(value))
        // Invariant 5: a write orders after everything the device has seen.
        seen.forEach { cell.stamp.hlc shouldBeGreaterThan it.stamp.hlc }
        writes += cell
    }

    fun value(device: Int, record: Int, field: SimField): Value? =
        devices[device].cells()[CellKey(records[record], field.id)]?.let { ValueCodec.decode(it.value) }

    /** The records with a position, as every device sorts them: by `(position, recordId)`. */
    fun order(device: SimDevice): List<Uuid> = device.cells().values
        .filter { it.key.fieldId == SimField.POSITION.id }
        .sortedWith(compareBy<Cell> { (ValueCodec.decode(it.value) as Value.Text).value }.thenBy { it.key.recordId })
        .map { it.key.recordId }

    private fun newPosition(device: SimDevice, record: Uuid, seed: Long): String {
        val others = order(device).filter { it != record }.map { positionOf(device, it) }
        val slot = seed.mod(others.size + 1)
        val before = others.getOrNull(slot - 1)
        val after = others.getOrNull(slot)
        // Equal keys from concurrent inserts leave no gap between them: append instead.
        return if (before != null && after != null && before >= after) {
            FractionalIndex.keyBetween(others.last(), null, device.jitter)
        } else {
            FractionalIndex.keyBetween(before, after, device.jitter)
        }
    }

    private fun positionOf(device: SimDevice, record: Uuid): String =
        (ValueCodec.decode(device.cells().getValue(CellKey(record, SimField.POSITION.id)).value) as Value.Text).value

    /** Invariants 1, 4, 6, 8, 9 and 10 after [quiesce]. */
    fun checkConverged() {
        checkIdentical()
        val expected = writes.groupBy { it.key }.mapValues { (_, cells) -> cells.maxWith(LWW_ORDER) }
        withClue("invariants 4, 6, 10: each field holds its LWW winner, and only written fields exist") {
            devices.forEach { it.cells() shouldBe expected }
        }
        val positioned = writes.filter { it.key.fieldId == SimField.POSITION.id }.map { it.key.recordId }.toSet()
        withClue("invariants 8, 9: every positioned item once, in the same order everywhere") {
            devices.forEach {
                order(it).sorted() shouldBe positioned.sorted()
                order(it) shouldBe order(devices.first())
            }
        }
    }

    /** Invariant 11: a snapshot stands in for the deltas it folds, for a fresh device and for [earlier] state. */
    fun checkSnapshots(earlier: Map<CellKey, Cell>) {
        val final = devices.first().cells()
        val snapshot = devices.first().snapshot()
        fun ingestAll(start: Map<CellKey, Cell>, batches: List<Batch>): Map<CellKey, Cell> {
            val state = start.toMutableMap()
            val clock = HlcClock(TestClock(devices.first().wall.millis))
            batches.forEach { batch ->
                val result = ingest(batch, state::get, clock) as IngestResult.Merged
                result.changes.forEach { state[it.key] = it }
            }
            return state
        }
        ingestAll(emptyMap(), store.batches()) shouldBe final
        ingestAll(emptyMap(), listOf(snapshot)) shouldBe final
        ingestAll(earlier, listOf(snapshot)) shouldBe ingestAll(earlier, store.batches())
    }
}
