package com.msuite.todo.domain

import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.CoreFields
import com.msuite.core.model.FieldId
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import com.msuite.core.sync.testing.Cluster
import com.msuite.core.sync.testing.NetStep
import com.msuite.core.sync.testing.ReplicaState
import com.msuite.core.sync.testing.SimDevice
import com.msuite.core.sync.testing.SimStep
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.uuid.Uuid

/** ToDo edits made through the real commands. Picks are indices into the device's current view, taken modulo. */
sealed interface TodoStep : SimStep {
    data class CreateList(override val device: Int) : TodoStep

    data class DeleteList(override val device: Int, val pick: Int) : TodoStep

    data class CreateTask(override val device: Int, val pick: Int) : TodoStep

    /** Sets done, starred or the title of a visible task, by [seed]. */
    data class EditTask(override val device: Int, val pick: Int, val seed: Int) : TodoStep

    data class MoveTask(override val device: Int, val pick: Int, val toIndex: Int) : TodoStep

    data class DeleteTask(override val device: Int, val pick: Int) : TodoStep

    /** Points any task, hidden or not, at a live list: a raw write, as a future "move to list" would. */
    data class Relist(override val device: Int, val pickTask: Int, val pickList: Int) : TodoStep
}

val FIRST_RUN_TITLES = listOf("My Tasks", "Meine Aufgaben")

/**
 * The ToDo simulator (invariants 14 to 16, docs/testing.md): the real commands and projection on a
 * [Cluster] of [Replica][com.msuite.core.sync.Replica]s. Every device runs its first-run setup,
 * alternating languages.
 */
class TodoSimulation(wallOffsets: List<Long>, seed: Long) : Cluster(wallOffsets, seed, ::ReplicaState) {
    private val replicas = devices.map { (it.state as ReplicaState).replica }
    private val lists = replicas.mapIndexed { i, r -> ListCommands(r, Random(seed + i), ids(seed, i)) }
    private val tasks = replicas.mapIndexed { i, r -> TaskCommands(r, Random(seed + i + 100), ids(seed, i)) }

    /** Every list and task any device created: invariant 16 says none is lost. */
    val created = mutableSetOf<Uuid>()

    suspend fun firstRun() = lists.forEachIndexed { i, commands ->
        commands.createMyTasksIfMissing(FIRST_RUN_TITLES[i % FIRST_RUN_TITLES.size])
    }

    suspend fun run(step: SimStep) {
        when (step) {
            is NetStep -> run(step)
            is TodoStep -> edit(step)
            else -> error("unknown step $step")
        }
        checkClockBound(device(step))
    }

    fun view(device: SimDevice): TodoState = projectTodos(device.cells().values)

    fun view(device: Int): TodoState = view(devices[device])

    private suspend fun edit(step: TodoStep) {
        val i = step.device.mod(devices.size)
        val view = view(devices[i])
        val visible = view.lists.flatMap { view.tasks(it.id) }
        when (step) {
            is TodoStep.CreateList -> created += lists[i].create("list")

            is TodoStep.DeleteList -> view.lists.filter { it.id != TodoIds.MY_TASKS }.pick(step.pick)
                ?.let { lists[i].delete(it.id) }

            is TodoStep.CreateTask -> view.lists.pick(step.pick)?.let { created += tasks[i].create(it.id, "task") }

            is TodoStep.EditTask -> visible.pick(step.pick)?.let { edit(tasks[i], it, step.seed) }

            is TodoStep.MoveTask -> visible.pick(step.pick)?.let { tasks[i].move(it.id, step.toIndex) }

            is TodoStep.DeleteTask -> visible.pick(step.pick)?.let { tasks[i].delete(it.id) }

            is TodoStep.Relist -> relist(i, step, view)
        }
    }

    private suspend fun relist(device: Int, step: TodoStep.Relist, view: TodoState) {
        val task = allTasks(devices[device]).pick(step.pickTask) ?: return
        val list = view.lists.pick(step.pickList) ?: return
        replicas[device].write(mapOf(CellKey(task, TodoFields.LIST) to ValueCodec.encode(Value.Ref(list.id))))
    }

    private suspend fun edit(commands: TaskCommands, task: Task, seed: Int) = when (seed.mod(3)) {
        0 -> commands.setDone(task.id, !task.done)
        1 -> commands.setStarred(task.id, !task.starred)
        else -> commands.setTitle(task.id, "t$seed")
    }

    /** Invariants 14 to 16 after [quiesce]. */
    fun checkTodoRules() {
        checkIdentical()
        devices.forEach { device ->
            val view = view(device)
            withClue("invariant 14: a task is visible exactly when its list is live") {
                visibleByOracle(device.cells().values) shouldBe
                    view.lists.associate { list -> list.id to view.tasks(list.id).map { it.id }.toSet() }
            }
            withClue("invariant 15: exactly one My Tasks, titled by one device's first run") {
                view.lists.count { it.id == TodoIds.MY_TASKS } shouldBe 1
                FIRST_RUN_TITLES shouldContain view.lists.single { it.id == TodoIds.MY_TASKS }.title
            }
            withClue("invariant 16: every record any device created is kept") {
                val ids = device.cells().keys.map { it.recordId }.toSet()
                created.filterNot { it in ids } shouldBe emptyList()
            }
        }
    }

    private fun allTasks(device: SimDevice): List<Uuid> =
        records(device.cells().values).filterValues { it.type == TodoTypes.TASK }.keys.sorted()

    private fun <T> List<T>.pick(index: Int): T? = if (isEmpty()) null else this[index.mod(size)]

    private companion object {
        fun ids(seed: Long, device: Int): () -> Uuid {
            var next = 0L
            return { Uuid.fromLongs(seed * 31 + device, next++) }
        }
    }
}

private class RawRecord(val type: Long?, val deleted: Boolean, val list: Uuid?)

/** The visibility rule recomputed from raw cells, without the projection: live list ID to its visible task IDs. */
private fun visibleByOracle(cells: Collection<Cell>): Map<Uuid, Set<Uuid>> {
    val records = records(cells)
    val live = records.filterValues { it.type == TodoTypes.LIST && !it.deleted }.keys
    val tasks = records.filterValues { it.type == TodoTypes.TASK && !it.deleted && it.list in live }
    return live.associateWith { list -> tasks.filterValues { it.list == list }.keys }
}

private fun records(cells: Collection<Cell>): Map<Uuid, RawRecord> =
    cells.groupBy { it.key.recordId }.mapValues { (_, fields) ->
        fun value(field: FieldId) = fields.find { it.key.fieldId == field }?.let { ValueCodec.decode(it.value) }
        RawRecord(
            type = (value(CoreFields.TYPE) as? Value.Int64)?.value,
            deleted = (value(CoreFields.DELETED) as? Value.Bool)?.value == true,
            list = (value(TodoFields.LIST) as? Value.Ref)?.value,
        )
    }
