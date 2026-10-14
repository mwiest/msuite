package com.msuite.todo.domain

import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.CoreFields
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.Stamp
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private val stamp = Stamp(Hlc.of(1, 0), DeviceId(1))

private fun cells(id: Uuid, vararg fields: Pair<FieldId, Value>): List<Cell> =
    fields.map { (field, value) -> Cell(CellKey(id, field), ValueCodec.encode(value), stamp) }

private fun list(id: Uuid, title: String, position: String, vararg more: Pair<FieldId, Value>) = cells(
    id,
    CoreFields.TYPE to Value.Int64(TodoTypes.LIST),
    TodoFields.TITLE to Value.Text(title),
    TodoFields.POSITION to Value.Text(position),
    *more,
)

private fun task(id: Uuid, listId: Uuid, title: String, position: String, vararg more: Pair<FieldId, Value>) = cells(
    id,
    CoreFields.TYPE to Value.Int64(TodoTypes.TASK),
    TodoFields.LIST to Value.Ref(listId),
    TodoFields.TITLE to Value.Text(title),
    TodoFields.POSITION to Value.Text(position),
    *more,
)

private val deleted = CoreFields.DELETED to Value.Bool(true)

private fun id(n: Int) = Uuid.fromLongs(0, n.toLong())

class ProjectionTest : StringSpec({
    "no cells, no lists" {
        projectTodos(emptyList()).lists.shouldBeEmpty()
    }

    "a list and its task read back typed, absent fields as defaults" {
        val state = projectTodos(
            list(id(1), "Home", "a") +
                task(
                    id(2),
                    id(1),
                    "Buy milk",
                    "a",
                    TodoFields.NOTES to Value.Text("2 l"),
                    TodoFields.DUE to Value.Date(LocalDate(2026, 10, 20)),
                    TodoFields.STARRED to Value.Bool(true),
                    TodoFields.DONE to Value.Bool(true),
                ) +
                task(id(3), id(1), "Call", "b"),
        )
        state.lists shouldContainExactly listOf(TodoList(id(1), "Home", "a"))
        state.tasks(id(1)) shouldContainExactly listOf(
            Task(id(2), id(1), "Buy milk", "2 l", LocalDate(2026, 10, 20), starred = true, done = true, "a"),
            Task(id(3), id(1), "Call", "", due = null, starred = false, done = false, "b"),
        )
    }

    "lists and tasks sort by position, then ID" {
        val state = projectTodos(
            list(id(3), "C", "b") + list(id(2), "B", "a") + list(id(1), "A", "b") +
                task(id(6), id(1), "z", "a") + task(id(5), id(1), "y", "a") + task(id(4), id(1), "x", "0"),
        )
        state.lists.map { it.id } shouldContainExactly listOf(id(2), id(1), id(3))
        state.tasks(id(1)).map { it.id } shouldContainExactly listOf(id(4), id(5), id(6))
    }

    "positions compare bytewise, not by locale or case" {
        val state = projectTodos(list(id(1), "a", "a") + list(id(2), "B", "B") + list(id(3), "Z", "Z"))
        state.lists.map { it.title } shouldContainExactly listOf("B", "Z", "a")
    }

    "deleted tasks are hidden" {
        projectTodos(list(id(1), "L", "a") + task(id(2), id(1), "t", "a", deleted)).tasks(id(1)).shouldBeEmpty()
    }

    "a deleted list is hidden with its tasks; a task moved to a live list stays visible (decision 0032)" {
        val state = projectTodos(
            list(id(1), "Gone", "a", deleted) + list(id(2), "Live", "b") +
                task(id(3), id(1), "hidden", "a") + task(id(4), id(2), "moved", "a"),
        )
        state.lists.map { it.id } shouldContainExactly listOf(id(2))
        state.tasks(id(1)).shouldBeEmpty()
        state.tasks(id(2)).map { it.id } shouldContainExactly listOf(id(4))
    }

    "My Tasks has a fixed ID, and deleting it is honoured like any list" {
        TodoIds.MY_TASKS shouldBe Uuid.parse("fe8696e3-4e7f-4d53-a464-6d0da4f50a4f")
        projectTodos(list(TodoIds.MY_TASKS, "My Tasks", "a", deleted)).lists.shouldBeEmpty()
    }

    "records without a known type, and tasks without a live list, are not shown" {
        val state = projectTodos(
            list(id(1), "L", "a") +
                cells(id(2), TodoFields.TITLE to Value.Text("no type yet")) +
                cells(id(3), CoreFields.TYPE to Value.Int64(99), TodoFields.TITLE to Value.Text("future type")) +
                task(id(4), id(9), "unknown list", "a") +
                cells(id(5), CoreFields.TYPE to Value.Int64(TodoTypes.TASK), TodoFields.TITLE to Value.Text("no list")),
        )
        state.lists.map { it.id } shouldContainExactly listOf(id(1))
        state.tasks(id(1)).shouldBeEmpty()
        state.tasks(id(9)).shouldBeEmpty()
    }

    "a value of an unexpected type reads as absent" {
        val state = projectTodos(
            list(id(1), "L", "a") + task(id(2), id(1), "t", "a", TodoFields.DONE to Value.Text("yes")),
        )
        state.tasks(id(1)).single().done shouldBe false
    }
})
