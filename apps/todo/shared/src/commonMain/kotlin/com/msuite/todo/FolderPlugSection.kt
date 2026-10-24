package com.msuite.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.folder_clear
import com.msuite.todo.resources.folder_last_sync
import com.msuite.todo.resources.folder_never_synced
import com.msuite.todo.resources.folder_pick
import com.msuite.todo.resources.folder_sync
import com.msuite.todo.resources.folder_sync_configured
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

        // Sync status
        if (status.lastSync != null) {
            Text(
                stringResource(Res.string.folder_last_sync, status.lastSync.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Text(
                stringResource(Res.string.folder_never_synced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Actions
        FolderActionsRow(folder, folderUri, busy, scope)
    }
}

@Composable
private fun FolderActionsRow(
    folder: FolderSyncActions,
    folderUri: String?,
    busy: MutableState<Boolean>,
    scope: CoroutineScope,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy.value, onClick = {
            scope.launch {
                busy.value = true
                folder.pickFolder()
                busy.value = false
            }
        }) {
            Text(stringResource(Res.string.folder_pick))
        }
        if (folderUri != null) {
            Button(enabled = !busy.value, onClick = { folder.clearFolder() }) {
                Text(stringResource(Res.string.folder_clear))
            }
            Button(enabled = !busy.value && folderUri != null, onClick = {
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
