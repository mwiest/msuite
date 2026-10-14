package com.msuite.core.sync

import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.Value
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

private const val HOUR = 3_600_000L
private val DAY = HlcClock.MAX_AHEAD.inWholeMilliseconds

private val arbDevice = Arb.int(0..3)
private val arbStep: Arb<Step> = Arb.choice(
    Arb.bind(arbDevice, Arb.int(0..3), Arb.int(0..3), Arb.long(0L..99L), Step::Write),
    Arb.bind(arbDevice, Arb.int(0..3), Arb.int(0..3), Arb.long(0L..99L), Step::Write),
    Arb.bind(arbDevice, Arb.long(0L..5_000L), Step::Tick),
    Arb.bind(arbDevice, Arb.long(-2 * HOUR..2 * HOUR), Step::Jump),
    Arb.bind(arbDevice, Arb.boolean(), Step::Push),
    Arb.bind(arbDevice, Arb.long(), Step::Pull),
    Arb.bind(arbDevice, Arb.long(), Step::Pull),
    arbDevice.map(Step::Toggle),
)

/** A generated run: device clock offsets (the first may be a broken clock days ahead) and a schedule. */
data class Scenario(val seed: Long, val offsets: List<Long>, val steps: List<Step>, val captureAt: Int)

private val arbScenario: Arb<Scenario> = Arb.bind(
    Arb.long(),
    Arb.list(Arb.long(-HOUR..HOUR), 2..4),
    Arb.choice(Arb.long(0L..0L), Arb.long(DAY + 1..3 * DAY)),
    Arb.list(arbStep, 0..120),
    Arb.int(0..120),
) { seed, offsets, brokenClock, steps, captureAt ->
    Scenario(seed, listOf(offsets.first() + brokenClock) + offsets.drop(1), steps, captureAt)
}

private fun Scenario.check(backend: SimBackend) = Simulation(offsets, seed, backend = backend).use { sim ->
    var earlier = emptyMap<CellKey, Cell>()
    steps.forEachIndexed { i, step ->
        if (i == captureAt) earlier = sim.devices.first().cells()
        sim.run(step)
    }
    sim.quiesce()
    sim.checkConverged()
    sim.checkSnapshots(earlier)
}

private fun Simulation.title(device: Int, record: Int) = (value(device, record, SimField.TITLE) as Value.Text?)?.value

class SimulatorTest : StringSpec({
    "any schedule converges to the LWW winners (invariants 1 to 11 and 13)" {
        checkAll(PropTestConfig(iterations = 1_000), arbScenario) { it.check(::MemoryState) }
    }

    "any schedule converges the same through Replica and SQLite" {
        checkAll(PropTestConfig(iterations = 300), arbScenario) { it.check(::ReplicaState) }
    }

    "concurrent edits of one field: the faster clock wins, everywhere" {
        val sim = Simulation(listOf(HOUR, 0), seed = 1)
        sim.run(Step.Write(device = 0, record = 0, field = 0, seed = 1))
        sim.run(Step.Write(device = 1, record = 0, field = 0, seed = 2))
        sim.quiesce()
        sim.checkConverged()
        sim.title(0, 0) shouldBe "t1"
        sim.title(1, 0) shouldBe "t1"
    }

    "an edit made after seeing another wins, even on a slower clock" {
        val sim = Simulation(listOf(HOUR, 0), seed = 2)
        sim.run(Step.Write(device = 0, record = 0, field = 0, seed = 1))
        sim.run(Step.Push(device = 0, fails = false))
        sim.pullAll(sim.devices[1])
        sim.title(1, 0) shouldBe "t1"
        sim.write(sim.devices[1], sim.records[0], field = 0, seed = 2)
        sim.quiesce()
        sim.title(0, 0) shouldBe "t2"
    }

    "a delete isn't undone by an older edit that arrives later" {
        val sim = Simulation(listOf(0, HOUR), seed = 3)
        sim.run(Step.Write(device = 0, record = 0, field = 3, seed = 1)) // deleted = false, older
        sim.run(Step.Write(device = 1, record = 0, field = 3, seed = 0)) // deleted = true, newer
        sim.run(Step.Push(device = 1, fails = false))
        sim.quiesce()
        sim.checkConverged()
        sim.value(0, 0, SimField.DELETED) shouldBe Value.Bool(true)
    }

    "a batch from a clock days ahead is held back, then merged once the clocks catch up" {
        val sim = Simulation(listOf(2 * DAY, 0), seed = 4)
        sim.run(Step.Write(device = 0, record = 0, field = 0, seed = 1))
        sim.run(Step.Push(device = 0, fails = false))
        sim.pullAll(sim.devices[1])
        sim.title(1, 0).shouldBeNull()
        sim.run(Step.Tick(device = 1, millis = DAY + 1))
        sim.pullAll(sim.devices[1])
        sim.title(1, 0) shouldBe "t1"
    }
})
