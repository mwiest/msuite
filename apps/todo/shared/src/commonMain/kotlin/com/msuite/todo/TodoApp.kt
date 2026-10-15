package com.msuite.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.msuite.core.design.MsuiteTheme
import com.msuite.core.sync.Replica
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.my_tasks
import org.jetbrains.compose.resources.stringResource

/** The whole ToDo UI over [replica]; platform shells (apps/todo/android) only host it. */
@Composable
fun TodoApp(replica: Replica) {
    val app = remember(replica) { TodoContainer(replica) }
    val myTasks = stringResource(Res.string.my_tasks)
    LaunchedEffect(app) { app.lists.createMyTasksIfMissing(myTasks) }

    MsuiteTheme {
        TasksScreen(app)
    }
}
