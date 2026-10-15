package com.msuite.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.msuite.core.design.MsuiteTheme
import com.msuite.core.sync.Replica
import com.msuite.todo.domain.ListCommands
import com.msuite.todo.domain.todoStates
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.my_tasks
import org.jetbrains.compose.resources.stringResource

/** The whole ToDo UI over [replica]; platform shells (apps/todo/android) only host it. */
@Composable
fun TodoApp(replica: Replica) {
    val lists = remember(replica) { ListCommands(replica) }
    val myTasks = stringResource(Res.string.my_tasks)
    LaunchedEffect(replica) { lists.createMyTasksIfMissing(myTasks) }
    val state by remember(replica) { replica.todoStates() }.collectAsState(initial = null)

    MsuiteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
                state?.lists?.forEach { list ->
                    Text(list.title, style = MaterialTheme.typography.titleMedium)
                    state?.tasks(list.id)?.forEach { Text(it.title) }
                }
            }
        }
    }
}
