package com.msuite.todo.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.model.CellKey
import com.msuite.core.model.CoreFields
import com.msuite.core.model.DeviceId
import com.msuite.core.model.Value
import com.msuite.core.model.ValueCodec
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.Replica
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.util.Properties
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A wall clock that moves 1 ms per read, so every write gets a later HLC. */
private class SteppingClock : Clock {
    private var millis = 1_000L

    override fun now(): Instant = Instant.fromEpochMilliseconds(millis++)
}

private class Todo {
    val replica = Replica(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema),
        DeviceId(1),
        SteppingClock(),
    )
    val lists = ListCommands(replica, Random(1))
    val tasks = TaskCommands(replica, Random(2))

    suspend fun state() = projectTodos(replica.cells())
}

class CommandsTest : StringSpec({
    "My Tasks is created on first run only" {
        val todo = Todo()
        todo.lists.createMyTasksIfMissing("My Tasks") shouldBe true
        todo.lists.createMyTasksIfMissing("Meine Aufgaben") shouldBe false
        todo.state().lists.map { it.id to it.title } shouldContainExactly listOf(TodoIds.MY_TASKS to "My Tasks")
    }

    "My Tasks deleted elsewhere is not silently recreated (decision 0055)" {
        val todo = Todo()
        todo.lists.createMyTasksIfMissing("My Tasks")
        todo.replica.write(mapOf(CellKey(TodoIds.MY_TASKS, CoreFields.DELETED) to ValueCodec.encode(Value.Bool(true))))
        todo.lists.createMyTasksIfMissing("My Tasks") shouldBe false
        todo.state().lists.shouldBeEmpty()
    }

    "lists are appended, renamed and deleted" {
        val todo = Todo()
        todo.lists.createMyTasksIfMissing("My Tasks")
        val home = todo.lists.create("Home")
        val work = todo.lists.create("Work")
        todo.lists.rename(home, "House")
        todo.lists.delete(work)
        todo.state().lists.map { it.title } shouldContainExactly listOf("My Tasks", "House")
    }

    "My Tasks can't be deleted from the app" {
        val todo = Todo()
        todo.lists.createMyTasksIfMissing("My Tasks")
        shouldThrow<IllegalArgumentException> { todo.lists.delete(TodoIds.MY_TASKS) }
    }

    "a list moves to any index" {
        val todo = Todo()
        val (a, b, c) = listOf("A", "B", "C").map { todo.lists.create(it) }
        todo.lists.move(c, 0)
        todo.state().lists.map { it.id } shouldContainExactly listOf(c, a, b)
        todo.lists.move(c, 1)
        todo.state().lists.map { it.id } shouldContainExactly listOf(a, c, b)
        todo.lists.move(a, 2)
        todo.state().lists.map { it.id } shouldContainExactly listOf(c, b, a)
    }

    "a new task goes to the top of its list, with defaults" {
        val todo = Todo()
        val list = todo.lists.create("L")
        val first = todo.tasks.create(list, "first")
        val second = todo.tasks.create(list, "second")
        val tasks = todo.state().tasks(list)
        tasks.map { it.id } shouldContainExactly listOf(second, first)
        tasks.last().run {
            listId shouldBe list
            title shouldBe "first"
            notes shouldBe ""
            due shouldBe null
            starred shouldBe false
            done shouldBe false
        }
    }

    "a task can't be created in a list that isn't live" {
        val todo = Todo()
        val list = todo.lists.create("L")
        todo.lists.delete(list)
        shouldThrow<IllegalArgumentException> { todo.tasks.create(list, "t") }
        shouldThrow<IllegalArgumentException> { todo.tasks.create(Uuid.random(), "t") }
    }

    "task fields are edited, and a due date can be cleared" {
        val todo = Todo()
        val list = todo.lists.create("L")
        val id = todo.tasks.create(list, "t")
        todo.tasks.setTitle(id, "title")
        todo.tasks.setNotes(id, "notes")
        todo.tasks.setDue(id, LocalDate(2026, 10, 20))
        todo.tasks.setStarred(id, true)
        todo.tasks.setDone(id, true)
        todo.state().tasks(list).single().copy(position = "") shouldBe
            Task(id, list, "title", "notes", LocalDate(2026, 10, 20), starred = true, done = true, position = "")
        todo.tasks.setDue(id, null)
        todo.state().tasks(list).single().due shouldBe null
    }

    "a deleted task is hidden" {
        val todo = Todo()
        val list = todo.lists.create("L")
        val id = todo.tasks.create(list, "t")
        todo.tasks.delete(id)
        todo.state().tasks(list).shouldBeEmpty()
    }

    "a task moves to any index within its list" {
        val todo = Todo()
        val list = todo.lists.create("L")
        val (c, b, a) = listOf("c", "b", "a").map { todo.tasks.create(list, it) }
        todo.state().tasks(list).map { it.id } shouldContainExactly listOf(a, b, c)
        todo.tasks.move(a, 2)
        todo.state().tasks(list).map { it.id } shouldContainExactly listOf(b, c, a)
        todo.tasks.move(a, 0)
        todo.state().tasks(list).map { it.id } shouldContainExactly listOf(a, b, c)
    }
})
