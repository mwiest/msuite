package com.msuite.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.msuite.todo.domain.ListCommands
import com.msuite.todo.domain.TodoList
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.cancel
import com.msuite.todo.resources.create
import com.msuite.todo.resources.delete
import com.msuite.todo.resources.delete_list
import com.msuite.todo.resources.delete_list_tasks
import com.msuite.todo.resources.delete_list_title
import com.msuite.todo.resources.ic_more_vert
import com.msuite.todo.resources.list_name
import com.msuite.todo.resources.more
import com.msuite.todo.resources.move_left
import com.msuite.todo.resources.move_right
import com.msuite.todo.resources.new_list
import com.msuite.todo.resources.rename_list
import com.msuite.todo.resources.save
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Which list dialog is open; an enum so it survives recreation. */
enum class ListDialog { Create, Rename, Delete }

/** The top bar's overflow menu for the [list] at [index] of [count] (decision 0063). */
@Composable
fun ListMenu(list: TodoList, index: Int, count: Int, onDialog: (ListDialog) -> Unit, onMove: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    fun item(text: StringResource, action: () -> Unit): @Composable () -> Unit = {
        DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = {
            open = false
            action()
        })
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), stringResource(Res.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            item(Res.string.rename_list) { onDialog(ListDialog.Rename) }()
            if (index > 0) item(Res.string.move_left) { onMove(index - 1) }()
            if (index < count - 1) item(Res.string.move_right) { onMove(index + 1) }()
            if (list.deletable) item(Res.string.delete_list) { onDialog(ListDialog.Delete) }()
        }
    }
}

/**
 * Shows the rename or delete [dialog] for the [selected] list, which has [taskCount] tasks. [run]
 * performs a list command off the composition, e.g. in the screen's scope.
 */
@Composable
fun ListDialogHost(
    dialog: ListDialog,
    selected: TodoList?,
    taskCount: Int,
    run: (suspend ListCommands.() -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    when {
        selected == null -> LaunchedEffect(Unit) { onClose() }

        dialog == ListDialog.Rename -> ListNameDialog(selected.title, onDismiss = onClose, onSave = { name ->
            onClose()
            run { rename(selected.id, name) }
        })

        selected.deletable -> DeleteListDialog(selected, taskCount, onDismiss = onClose, onDelete = {
            onClose()
            run { delete(selected.id) }
        })

        else -> LaunchedEffect(Unit) { onClose() }
    }
}

/** Asks for a list name; [initial] is empty for a new list. Blank names are not saved. */
@Composable
fun ListNameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val save = { if (name.isNotBlank()) onSave(name.trim()) else onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.isEmpty()) Res.string.new_list else Res.string.rename_list)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.focusRequester(focus),
                label = { Text(stringResource(Res.string.list_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = {
            TextButton(onClick = save) {
                Text(stringResource(if (initial.isEmpty()) Res.string.create else Res.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Confirms deleting [list], which hides its [taskCount] tasks with it (decision 0032). */
@Composable
fun DeleteListDialog(list: TodoList, taskCount: Int, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_list_title, list.title)) },
        text = if (taskCount > 0) {
            { Text(pluralStringResource(Res.plurals.delete_list_tasks, taskCount, taskCount)) }
        } else {
            null
        },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(Res.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
