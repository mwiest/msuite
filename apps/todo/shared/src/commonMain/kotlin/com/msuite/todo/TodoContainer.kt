package com.msuite.todo

import com.msuite.core.sync.Replica
import com.msuite.todo.domain.ListCommands
import com.msuite.todo.domain.TaskCommands
import com.msuite.todo.domain.TodoState
import com.msuite.todo.domain.todoStates
import kotlinx.coroutines.flow.Flow

/** What the screens need, built once from the shell's [Replica] (decision 0019). */
class TodoContainer(replica: Replica) {
    val lists = ListCommands(replica)
    val tasks = TaskCommands(replica)
    val states: Flow<TodoState> = replica.todoStates()
}
