package com.msuite.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.FolderSyncActions
import com.msuite.todo.domain.FolderSyncStatus
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.cancel
import com.msuite.todo.resources.folder_clear
import com.msuite.todo.resources.folder_last_sync
import com.msuite.todo.resources.folder_never_synced
import com.msuite.todo.resources.folder_pick
import com.msuite.todo.resources.folder_pick_continue
import com.msuite.todo.resources.folder_pick_hint
import com.msuite.todo.resources.folder_pick_title
import com.msuite.todo.resources.folder_sync
import com.msuite.todo.resources.folder_sync_configured
import com.msuite.todo.resources.folder_sync_failing
import com.msuite.todo.resources.folder_sync_not_configured
import com.msuite.todo.resources.folder_sync_now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The folder plug (slice 12): pick a synced folder, show status, manual sync. */
@Composable
fun FolderPlugSection(folder: FolderSyncActions) {
    val status by folder.status.collectAsState()
    val folderUri by folder.folderUri.collectAsState()
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf(false) }
    val showPickDialog = remember { mutableStateOf(false) }

    Text(
        stringResource(Res.string.folder_sync),
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        // Folder info
        if (folderUri == null) {
            Text(
                stringResource(Res.string.folder_sync_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Text(
                stringResource(Res.string.folder_sync_configured),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                folderUri!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        SyncStatusLines(status)

        // Actions
        FolderActionsRow(folder, folderUri, busy, scope, showPickDialog)
    }

    // Show a one-off explanation before opening the SAF picker, so the user knows to pick their
    // cloud-sync root (Syncthing, Nextcloud, ...) rather than a random folder.
    if (showPickDialog.value) {
        FolderPickDialog(showPickDialog, folder, busy, scope)
    }
}

@Composable
private fun SyncStatusLines(status: FolderSyncStatus) {
    Text(
        status.lastSync?.let { stringResource(Res.string.folder_last_sync, it.toString()) }
            ?: stringResource(Res.string.folder_never_synced),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    if (status.failing) {
        Text(
            stringResource(Res.string.folder_sync_failing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun FolderPickDialog(
    showPickDialog: MutableState<Boolean>,
    folder: FolderSyncActions,
    busy: MutableState<Boolean>,
    scope: CoroutineScope,
) {
    AlertDialog(
        onDismissRequest = { showPickDialog.value = false },
        title = { Text(stringResource(Res.string.folder_pick_title)) },
        text = { Text(stringResource(Res.string.folder_pick_hint)) },
        confirmButton = {
            Button(onClick = {
                showPickDialog.value = false
                scope.launch {
                    busy.value = true
                    folder.pickFolder()
                    busy.value = false
                }
            }) {
                Text(stringResource(Res.string.folder_pick_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = { showPickDialog.value = false }) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun FolderActionsRow(
    folder: FolderSyncActions,
    folderUri: String?,
    busy: MutableState<Boolean>,
    scope: CoroutineScope,
    showPickDialog: MutableState<Boolean>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy.value, onClick = { showPickDialog.value = true }) {
            Text(stringResource(Res.string.folder_pick))
        }
        if (folderUri != null) {
            Button(enabled = !busy.value, onClick = { folder.clearFolder() }) {
                Text(stringResource(Res.string.folder_clear))
            }
            Button(enabled = !busy.value, onClick = {
                scope.launch {
                    busy.value = true
                    folder.syncNow()
                    busy.value = false
                }
            }) {
                Text(stringResource(Res.string.folder_sync_now))
            }
        }
    }
}
