package com.msuite.todo.domain

import com.msuite.core.model.CellKey
import com.msuite.core.model.CoreFields
import com.msuite.core.model.FieldId
import com.msuite.core.model.FractionalIndex
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import com.msuite.core.sync.Replica
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.uuid.Uuid

/** List edits, each one [Replica.write]. [random] is the position jitter (decision 0004), [newId] makes record IDs. */
class ListCommands(
    private val replica: Replica,
    private val random: Random = Random.Default,
    private val newId: () -> Uuid = Uuid::random,
) {
    /**
     * Creates "My Tasks" with [title] (the device language, decision 0055) unless the record has
     * any cell, e.g. from a sync or a delete elsewhere. Returns whether it wrote.
     */
    suspend fun createMyTasksIfMissing(title: String): Boolean {
        if (replica.cells(TodoIds.MY_TASKS).isNotEmpty()) return false
        replica.write(TodoIds.MY_TASKS, newList(title))
        return true
    }

    /** Appends a list at the end. */
    suspend fun create(title: String): Uuid = newId().also { replica.write(it, newList(title)) }

    suspend fun rename(id: Uuid, title: String) =
        replica.edit(id, TodoTypes.LIST, TodoFields.TITLE to Value.Text(title))

    /** Writes only the tombstone; the list's tasks are hidden at read time (decision 0032). */
    suspend fun delete(id: Uuid) {
        require(id != TodoIds.MY_TASKS) { "My Tasks can't be deleted" }
        replica.edit(id, TodoTypes.LIST, CoreFields.DELETED to Value.Bool(true))
    }

    /** Moves the list to [toIndex] in the order shown. */
    suspend fun move(id: Uuid, toIndex: Int) {
        replica.requireType(id, TodoTypes.LIST)
        val others = state().lists.filter { it.id != id }.map { it.position }
        replica.write(id, TodoFields.POSITION to Value.Text(positionAt(others, toIndex, random)))
    }

    private suspend fun newList(title: String): List<Pair<FieldId, Value?>> = listOf(
        CoreFields.TYPE to Value.Int64(TodoTypes.LIST),
        TodoFields.TITLE to Value.Text(title),
        TodoFields.POSITION to Value.Text(positionAt(state().lists.map { it.position }, Int.MAX_VALUE, random)),
    )

    private suspend fun state() = projectTodos(replica.cells())
}

/** Task edits, each one [Replica.write]. [random] is the position jitter (decision 0004), [newId] makes record IDs. */
class TaskCommands(
    private val replica: Replica,
    private val random: Random = Random.Default,
    private val newId: () -> Uuid = Uuid::random,
) {
    /** Creates a task at the top of the live list [listId]. */
    suspend fun create(listId: Uuid, title: String): Uuid {
        val state = projectTodos(replica.cells())
        require(state.lists.any { it.id == listId }) { "list $listId isn't live" }
        val position = positionAt(state.tasks(listId).map { it.position }, 0, random)
        return newId().also {
            replica.write(
                it,
                CoreFields.TYPE to Value.Int64(TodoTypes.TASK),
                TodoFields.LIST to Value.Ref(listId),
                TodoFields.TITLE to Value.Text(title),
                TodoFields.POSITION to Value.Text(position),
            )
        }
    }

    suspend fun setTitle(id: Uuid, title: String) = replica.edit(
        id,
        TodoTypes.TASK,
        TodoFields.TITLE to Value.Text(title),
    )

    suspend fun setNotes(id: Uuid, notes: String) = replica.edit(
        id,
        TodoTypes.TASK,
        TodoFields.NOTES to Value.Text(notes),
    )

    /** `null` clears the due date. */
    suspend fun setDue(id: Uuid, due: LocalDate?) = replica.edit(
        id,
        TodoTypes.TASK,
        TodoFields.DUE to due?.let(Value::Date),
    )

    suspend fun setStarred(id: Uuid, starred: Boolean) = replica.edit(
        id,
        TodoTypes.TASK,
        TodoFields.STARRED to Value.Bool(starred),
    )

    suspend fun setDone(id: Uuid, done: Boolean) = replica.edit(id, TodoTypes.TASK, TodoFields.DONE to Value.Bool(done))

    suspend fun delete(id: Uuid) = replica.edit(id, TodoTypes.TASK, CoreFields.DELETED to Value.Bool(true))

    /** Moves the visible task to [toIndex] within its list, in the order shown. */
    suspend fun move(id: Uuid, toIndex: Int) {
        replica.requireType(id, TodoTypes.TASK)
        val state = projectTodos(replica.cells())
        val task =
            requireNotNull(state.lists.flatMap { state.tasks(it.id) }.find { it.id == id }) { "task $id is hidden" }
        val others = state.tasks(task.listId).filter { it.id != id }.map { it.position }
        replica.write(id, TodoFields.POSITION to Value.Text(positionAt(others, toIndex, random)))
    }
}

private suspend fun Replica.write(id: Uuid, vararg fields: Pair<FieldId, Value?>) = write(id, fields.asList())

/** Writes [fields] to an existing record of [type], hidden or not; any other ID is a caller bug. */
private suspend fun Replica.edit(id: Uuid, type: Long, vararg fields: Pair<FieldId, Value?>) {
    requireType(id, type)
    write(id, fields.asList())
}

private suspend fun Replica.requireType(id: Uuid, type: Long) {
    val typeCell = cells(id).find { it.key.fieldId == CoreFields.TYPE }
    require(typeCell != null && ValueCodec.decode(typeCell.value) == Value.Int64(type)) { "$id isn't of type $type" }
}

private suspend fun Replica.write(id: Uuid, fields: List<Pair<FieldId, Value?>>) {
    write(fields.associate { (field, value) -> CellKey(id, field) to ValueCodec.encode(value) })
}

/** A key that sorts at [index] among [positions] (sorted, without the item being placed). */
private fun positionAt(positions: List<String>, index: Int, random: Random): String {
    val at = index.coerceIn(0, positions.size)
    val before = positions.getOrNull(at - 1)
    val after = positions.getOrNull(at)
    // Equal neighbours (concurrent inserts into one gap) leave no room between them: go after both.
    return if (before != null && after != null && before >= after) {
        FractionalIndex.keyBetween(after, null, random)
    } else {
        FractionalIndex.keyBetween(before, after, random)
    }
}
