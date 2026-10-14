package com.msuite.todo.domain

import com.msuite.core.sync.HlcClock
import com.msuite.core.sync.testing.NetStep
import com.msuite.core.sync.testing.SimStep
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

private val DAY = HlcClock.MAX_AHEAD.inWholeMilliseconds
private val arbDevice = Arb.int(0..3)
private val arbPick = Arb.int(0..20)

private val arbEdit: Arb<SimStep> = Arb.choice(
    arbDevice.map(TodoStep::CreateList),
    Arb.bind(arbDevice, arbPick, TodoStep::DeleteList),
    Arb.bind(arbDevice, arbPick, TodoStep::CreateTask),
    Arb.bind(arbDevice, arbPick, TodoStep::CreateTask),
    Arb.bind(arbDevice, arbPick, Arb.int(0..99), TodoStep::EditTask),
    Arb.bind(arbDevice, arbPick, arbPick, TodoStep::MoveTask),
    Arb.bind(arbDevice, arbPick, TodoStep::DeleteTask),
    Arb.bind(arbDevice, arbPick, arbPick, TodoStep::Relist),
)

// An edit in two steps of five.
private val arbStep: Arb<SimStep> =
    Arb.choice(arbEdit, arbEdit, NetStep.arb(arbDevice), NetStep.arb(arbDevice), NetStep.arb(arbDevice))

data class TodoScenario(val seed: Long, val offsets: List<Long>, val steps: List<SimStep>)

private val arbScenario: Arb<TodoScenario> = Arb.bind(
    Arb.long(),
    Arb.list(Arb.long(-NetStep.HOUR..NetStep.HOUR), 2..4),
    Arb.choice(Arb.long(0L..0L), Arb.long(DAY + 1..3 * DAY)),
    Arb.list(arbStep, 0..80),
) { seed, offsets, brokenClock, steps ->
    TodoScenario(seed, listOf(offsets.first() + brokenClock) + offsets.drop(1), steps)
}

class TodoSimulatorTest : StringSpec({
    "any schedule keeps the ToDo rules on every device (invariants 14 to 16)" {
        checkAll(PropTestConfig(iterations = 200), arbScenario) { scenario ->
            TodoSimulation(scenario.offsets, scenario.seed).use { sim ->
                sim.firstRun()
                scenario.steps.forEach { sim.run(it) }
                sim.quiesce()
                sim.checkTodoRules()
            }
        }
    }

    "a task of a deleted list is hidden everywhere, and visible again once moved to a live list" {
        TodoSimulation(listOf(0, 0), seed = 1).use { sim ->
            sim.firstRun()
            sim.run(TodoStep.CreateList(device = 0))
            sim.quiesce()
            val list = sim.view(0).lists.single { it.id != TodoIds.MY_TASKS }
            sim.run(TodoStep.CreateTask(device = 0, pick = sim.view(0).lists.indexOf(list)))
            sim.quiesce()
            sim.run(TodoStep.DeleteList(device = 1, pick = 0))
            sim.quiesce()
            sim.checkTodoRules()
            sim.devices.indices.forEach { sim.view(it).tasks(list.id).shouldBeEmpty() }
            sim.run(TodoStep.Relist(device = 0, pickTask = 0, pickList = 0))
            sim.quiesce()
            sim.checkTodoRules()
            sim.devices.indices.forEach { sim.view(it).tasks(TodoIds.MY_TASKS).size shouldBe 1 }
        }
    }

    "a device that worked offline from the start joins with all its data, and one My Tasks" {
        TodoSimulation(listOf(0, 0, 0), seed = 2).use { sim ->
            sim.run(NetStep.Toggle(device = 2))
            sim.firstRun()
            repeat(3) { sim.run(TodoStep.CreateList(device = 2)) }
            repeat(3) { sim.run(TodoStep.CreateTask(device = 2, pick = it)) }
            sim.run(TodoStep.CreateTask(device = 0, pick = 0))
            sim.run(NetStep.Push(device = 0, fails = false))
            sim.run(NetStep.Pull(device = 1, seed = 1))
            val joining = sim.view(2)
            sim.quiesce()
            sim.checkTodoRules()
            sim.devices.indices.forEach { device ->
                val view = sim.view(device)
                view.lists.map { it.id } shouldContainExactlyInAnyOrder sim.view(0).lists.map { it.id }
                joining.lists.forEach { list ->
                    view.tasks(list.id).map { it.id }.toSet().containsAll(joining.tasks(list.id).map { it.id }) shouldBe
                        true
                }
            }
        }
    }
})
