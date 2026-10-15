package com.msuite.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.Task
import com.msuite.todo.domain.dueDateFromPickerMillis
import com.msuite.todo.domain.toPickerMillis
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.add_date
import com.msuite.todo.resources.cancel
import com.msuite.todo.resources.clear_date
import com.msuite.todo.resources.delete
import com.msuite.todo.resources.due_date
import com.msuite.todo.resources.ic_close
import com.msuite.todo.resources.ic_event
import com.msuite.todo.resources.ic_more_vert
import com.msuite.todo.resources.ic_star
import com.msuite.todo.resources.ic_star_border
import com.msuite.todo.resources.mark_done
import com.msuite.todo.resources.mark_undone
import com.msuite.todo.resources.months
import com.msuite.todo.resources.more
import com.msuite.todo.resources.notes
import com.msuite.todo.resources.ok
import com.msuite.todo.resources.star
import com.msuite.todo.resources.task_title
import com.msuite.todo.resources.unstar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * Edits [task] in a sheet (decision 0062): title and notes are written when their field loses
 * focus or the sheet closes, the rest at once. Writes run in [scope], so they outlive the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(task: Task, app: TodoContainer, scope: CoroutineScope, onClose: () -> Unit) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }
    var picking by rememberSaveable(task.id) { mutableStateOf(false) }

    fun saveText() {
        if (title != task.title) scope.launch { app.tasks.setTitle(task.id, title) }
        if (notes != task.notes) scope.launch { app.tasks.setNotes(task.id, notes) }
    }

    ModalBottomSheet(onDismissRequest = {
        saveText()
        onClose()
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 16.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) saveText() },
                    textStyle = MaterialTheme.typography.titleLarge,
                    placeholder = { Text(stringResource(Res.string.task_title)) },
                    colors = transparentFieldColors(),
                )
                StarButton(task.starred) { scope.launch { app.tasks.setStarred(task.id, it) } }
                DeleteMenu {
                    scope.launch { app.tasks.delete(task.id) }
                    onClose()
                }
            }
            TextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) saveText() },
                placeholder = { Text(stringResource(Res.string.notes)) },
                colors = transparentFieldColors(),
            )
            DueDateChip(task.due, onPick = { picking = true }) { scope.launch { app.tasks.setDue(task.id, null) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    saveText()
                    scope.launch { app.tasks.setDone(task.id, !task.done) }
                    onClose()
                }) {
                    Text(stringResource(if (task.done) Res.string.mark_undone else Res.string.mark_done))
                }
            }
        }
    }

    if (picking) {
        DueDatePicker(task.due, onDismiss = { picking = false }) { date ->
            picking = false
            scope.launch { app.tasks.setDue(task.id, date) }
        }
    }
}

/** A due date as shown, with month names from the resources. */
@Composable
fun dueDateText(date: LocalDate): String {
    val months = stringArrayResource(Res.array.months)
    return stringResource(Res.string.due_date, months[date.month.ordinal], date.day, date.year)
}

@Composable
fun StarButton(starred: Boolean, onStar: (Boolean) -> Unit) {
    IconButton(onClick = { onStar(!starred) }) {
        if (starred) {
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

@Composable
private fun DeleteMenu(onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), stringResource(Res.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.delete)) }, onClick = {
                open = false
                onDelete()
            })
        }
    }
}

@Composable
private fun DueDateChip(due: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    AssistChip(
        onClick = onPick,
        modifier = Modifier.padding(horizontal = 8.dp),
        label = { Text(due?.let { dueDateText(it) } ?: stringResource(Res.string.add_date)) },
        leadingIcon = { Icon(painterResource(Res.drawable.ic_event), null, Modifier.size(18.dp)) },
        trailingIcon = due?.let {
            {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painterResource(Res.drawable.ic_close),
                        stringResource(Res.string.clear_date),
                        Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(due: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = due?.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(dueDateFromPickerMillis(it)) } ?: onDismiss() },
            ) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) {
        DatePicker(state)
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
