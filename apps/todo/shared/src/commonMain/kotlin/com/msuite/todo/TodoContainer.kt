package com.msuite.todo

import com.msuite.core.sync.ManualSync
import com.msuite.core.sync.Replica
import com.msuite.todo.domain.ListCommands
import com.msuite.todo.domain.TaskCommands
import com.msuite.todo.domain.TodoSettings
import com.msuite.todo.domain.TodoState
import com.msuite.todo.domain.todoStates
import kotlinx.coroutines.flow.Flow

/** What the screens need, built once from the shell's [Replica] and [settings] (decision 0019). */
class TodoContainer(replica: Replica, val settings: TodoSettings, files: SnapshotFiles) {
    val lists = ListCommands(replica)
    val tasks = TaskCommands(replica)
    val states: Flow<TodoState> = replica.todoStates()
    val sync = SyncActions(ManualSync(replica), files)
}
