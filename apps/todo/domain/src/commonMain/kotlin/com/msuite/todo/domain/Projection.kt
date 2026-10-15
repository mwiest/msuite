package com.msuite.todo.domain

import com.msuite.core.model.Cell
import com.msuite.core.model.CoreFields
import com.msuite.core.model.FieldId
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import com.msuite.core.sync.Replica
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

data class TodoList(val id: Uuid, val title: String, val position: String) {
    /** My Tasks can't be deleted (decision 0031). */
    val deletable: Boolean get() = id != TodoIds.MY_TASKS
}

data class Task(
    val id: Uuid,
    val listId: Uuid,
    val title: String,
    val notes: String,
    val due: LocalDate?,
    val starred: Boolean,
    val done: Boolean,
    val position: String,
)

/** What the UI shows: live lists and their visible tasks, each sorted by `(position, id)` (decision 0004). */
class TodoState(val lists: List<TodoList>, private val tasksByList: Map<Uuid, List<Task>>) {
    fun tasks(listId: Uuid): List<Task> = tasksByList[listId].orEmpty()

    /** The list [id] if it's live, else the first list, e.g. after the selected one was deleted elsewhere. */
    fun selected(id: Uuid?): TodoList? = lists.firstOrNull { it.id == id } ?: lists.firstOrNull()
}

/** The current state, then a re-read after each change (decision 0049). */
fun Replica.todoStates(): Flow<TodoState> = changes.map { projectTodos(cells()) }

/**
 * Reads `field_state` cells as lists and tasks. Hidden: deleted records, records whose type is
 * missing or unknown (e.g. their creation hasn't synced yet), and tasks whose list isn't live
 * (decision 0032). A value of an unexpected type reads as absent.
 */
fun projectTodos(cells: Iterable<Cell>): TodoState {
    val records = cells.groupBy { it.key.recordId }.mapValues { (_, fields) -> Fields(fields) }
        .filterValues { !it.bool(CoreFields.DELETED) }
    val lists = records.mapNotNull { (id, f) ->
        if (f.type() != TodoTypes.LIST) null else TodoList(id, f.text(TodoFields.TITLE), f.text(TodoFields.POSITION))
    }.sortedWith(compareBy<TodoList> { it.position }.thenBy { it.id })
    val live = lists.mapTo(HashSet()) { it.id }
    val tasks = records.mapNotNull { (id, f) ->
        val listId = f.ref(TodoFields.LIST)
        if (f.type() != TodoTypes.TASK || listId == null || listId !in live) return@mapNotNull null
        Task(
            id = id,
            listId = listId,
            title = f.text(TodoFields.TITLE),
            notes = f.text(TodoFields.NOTES),
            due = f.date(TodoFields.DUE),
            starred = f.bool(TodoFields.STARRED),
            done = f.bool(TodoFields.DONE),
            position = f.text(TodoFields.POSITION),
        )
    }.sortedWith(compareBy<Task> { it.position }.thenBy { it.id })
    return TodoState(lists, tasks.groupBy { it.listId })
}

private class Fields(cells: List<Cell>) {
    private val values: Map<FieldId, Value?> = cells.associate { it.key.fieldId to ValueCodec.decode(it.value) }

    fun type(): Long? = (values[CoreFields.TYPE] as? Value.Int64)?.value

    fun text(field: FieldId): String = (values[field] as? Value.Text)?.value.orEmpty()

    fun bool(field: FieldId): Boolean = (values[field] as? Value.Bool)?.value ?: false

    fun date(field: FieldId): LocalDate? = (values[field] as? Value.Date)?.value

    fun ref(field: FieldId): Uuid? = (values[field] as? Value.Ref)?.value
}
