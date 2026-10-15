package com.msuite.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.Task
import com.msuite.todo.domain.TodoList
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.app_title
import com.msuite.todo.resources.ic_add
import com.msuite.todo.resources.ic_star
import com.msuite.todo.resources.ic_star_border
import com.msuite.todo.resources.new_task
import com.msuite.todo.resources.no_lists
import com.msuite.todo.resources.no_tasks
import com.msuite.todo.resources.save
import com.msuite.todo.resources.star
import com.msuite.todo.resources.task_title
import com.msuite.todo.resources.unstar
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/** The lists as tabs (decision 0061) and the selected list's tasks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(app: TodoContainer) {
    val state = remember(app) { app.states }.collectAsState(initial = null).value ?: return
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.selected(selectedId?.let(Uuid::parse))
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.app_title)) }) },
        floatingActionButton = {
            if (selected != null) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(painterResource(Res.drawable.ic_add), stringResource(Res.string.new_task))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (selected == null) {
                Centered(stringResource(Res.string.no_lists))
                return@Column
            }
            ListTabs(state.lists, selected) { selectedId = it.id.toString() }
            val tasks = state.tasks(selected.id)
            if (tasks.isEmpty()) {
                Centered(stringResource(Res.string.no_tasks))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(tasks, key = { it.id.toString() }) { task ->
                        TaskRow(
                            task,
                            onDone = { scope.launch { app.tasks.setDone(task.id, it) } },
                            onStar = { scope.launch { app.tasks.setStarred(task.id, it) } },
                        )
                    }
                }
            }
        }
    }

    if (adding && selected != null) {
        NewTaskSheet(
            onSave = { title ->
                adding = false
                if (title.isNotBlank()) scope.launch { app.tasks.create(selected.id, title.trim()) }
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun ListTabs(lists: List<TodoList>, selected: TodoList, onSelect: (TodoList) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = lists.indexOf(selected), edgePadding = 16.dp) {
        lists.forEach { list ->
            Tab(selected = list == selected, onClick = { onSelect(list) }, text = { Text(list.title) })
        }
    }
}

@Composable
private fun TaskRow(task: Task, onDone: (Boolean) -> Unit, onStar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onDone(!task.done) }.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = onDone)
        Text(
            task.title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
        )
        IconButton(onClick = { onStar(!task.starred) }) {
            if (task.starred) {
                Icon(
                    painterResource(Res.drawable.ic_star),
                    stringResource(Res.string.unstar),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(painterResource(Res.drawable.ic_star_border), stringResource(Res.string.star))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskSheet(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp).imePadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                label = { Text(stringResource(Res.string.task_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(title) }),
            )
            TextButton(onClick = { onSave(title) }) { Text(stringResource(Res.string.save)) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
