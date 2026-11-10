package com.msuite.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.msuite.core.relay.RelayRefusal
import com.msuite.core.s3.S3Refusal
import com.msuite.todo.domain.LiveSyncActions
import com.msuite.todo.domain.SyncBackend
import com.msuite.todo.domain.SyncStatus
import com.msuite.todo.domain.isAllowedServerUrl
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.cancel
import com.msuite.todo.resources.folder_clear
import com.msuite.todo.resources.folder_looks_empty
import com.msuite.todo.resources.folder_pick
import com.msuite.todo.resources.folder_pick_continue
import com.msuite.todo.resources.folder_pick_hint
import com.msuite.todo.resources.folder_pick_title
import com.msuite.todo.resources.folder_sync_configured
import com.msuite.todo.resources.folder_sync_failing
import com.msuite.todo.resources.relay_forbidden
import com.msuite.todo.resources.relay_full
import com.msuite.todo.resources.relay_group_id
import com.msuite.todo.resources.relay_group_id_hint
import com.msuite.todo.resources.relay_looks_empty
import com.msuite.todo.resources.relay_save
import com.msuite.todo.resources.relay_too_large
import com.msuite.todo.resources.relay_unauthorized
import com.msuite.todo.resources.relay_unreachable
import com.msuite.todo.resources.relay_url
import com.msuite.todo.resources.relay_url_hint
import com.msuite.todo.resources.relay_url_insecure
import com.msuite.todo.resources.relay_url_invalid
import com.msuite.todo.resources.s3_bad_credentials
import com.msuite.todo.resources.s3_clock_skew
import com.msuite.todo.resources.s3_forbidden
import com.msuite.todo.resources.s3_looks_empty
import com.msuite.todo.resources.s3_no_bucket
import com.msuite.todo.resources.s3_unreachable
import com.msuite.todo.resources.s3_wrong_region
import com.msuite.todo.resources.sync_backend
import com.msuite.todo.resources.sync_backend_folder
import com.msuite.todo.resources.sync_backend_none
import com.msuite.todo.resources.sync_backend_none_hint
import com.msuite.todo.resources.sync_backend_relay
import com.msuite.todo.resources.sync_backend_relay_hint
import com.msuite.todo.resources.sync_backend_s3
import com.msuite.todo.resources.sync_confirm_empty
import com.msuite.todo.resources.sync_last_sync
import com.msuite.todo.resources.sync_never_synced
import com.msuite.todo.resources.sync_now
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class RelayUrlError { Invalid, Insecure }

/**
 * The live-sync backend chooser (plan v1 step 9): off, a synced folder, a self-hosted relay, or an
 * S3 bucket. A relay or bucket is only accepted over https unless this is a debug build; the group
 * ID is shown for the operator's allowlist.
 */
@Suppress("LongMethod") // One screen section; the parts below are already split out.
@Composable
fun SyncBackendSection(live: LiveSyncActions, allowInsecureRelay: Boolean) {
    val backend by live.backend.collectAsState()
    val status by live.status.collectAsState()
    val folderUri by live.folderUri.collectAsState()
    var choice by remember(backend) { mutableStateOf(backend) }
    var relayUrl by remember(choice) { mutableStateOf((choice as? SyncBackend.Relay)?.url.orEmpty()) }
    var relayError by remember { mutableStateOf<RelayUrlError?>(null) }
    var showPickDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val groupId by produceState<String?>(initialValue = null, key1 = live) { value = live.groupId() }

    Text(
        stringResource(Res.string.sync_backend),
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    BackendChooser(
        choice = choice,
        onNone = {
            choice = SyncBackend.None
            live.setBackend(SyncBackend.None)
        },
        onFolder = {
            val uri = folderUri
            if (uri == null) {
                showPickDialog = true
            } else {
                choice = SyncBackend.Folder(uri)
                live.setBackend(SyncBackend.Folder(uri))
            }
        },
        onRelay = {
            relayError = null
            choice = SyncBackend.Relay((backend as? SyncBackend.Relay)?.url.orEmpty())
        },
        onS3 = { choice = backend.s3Draft() },
    )

    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        when (val selected = choice) {
            SyncBackend.None -> Text(
                stringResource(Res.string.sync_backend_none_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is SyncBackend.Folder -> FolderDetails(
                folder = selected,
                busy = busy,
                onPick = { showPickDialog = true },
                onClear = {
                    choice = SyncBackend.None
                    live.setBackend(SyncBackend.None)
                },
                onSync = {
                    scope.launch {
                        busy = true
                        live.syncNow()
                        busy = false
                    }
                },
            )

            is SyncBackend.Relay -> RelayDetails(
                url = relayUrl,
                error = relayError,
                groupId = groupId,
                busy = busy,
                active = backend is SyncBackend.Relay,
                onUrlChange = {
                    relayUrl = it
                    relayError = null
                },
                onConnect = {
                    val url = relayUrl.trim()
                    relayError = when {
                        !isAllowedServerUrl(url, allowInsecureRelay) ->
                            if (url.startsWith("http://")) RelayUrlError.Insecure else RelayUrlError.Invalid

                        else -> null
                    }
                    if (relayError == null) live.setBackend(SyncBackend.Relay(url))
                },
                onSync = {
                    scope.launch {
                        busy = true
                        live.syncNow()
                        busy = false
                    }
                },
            )

            is SyncBackend.S3 -> S3Details(
                initial = selected,
                backend = backend,
                live = live,
                busy = busy,
                onSync = {
                    scope.launch {
                        busy = true
                        live.syncNow()
                        busy = false
                    }
                },
            )
        }

        if (choice == backend) SyncStatusLines(status, choice) { scope.launch { live.confirmEmpty() } }
    }

    if (showPickDialog) {
        FolderPickDialog(
            onDismiss = {
                showPickDialog = false
                if (live.backend.value !is SyncBackend.Folder) choice = live.backend.value
            },
            onConfirm = {
                showPickDialog = false
                scope.launch {
                    busy = true
                    live.pickFolder()
                    busy = false
                    if (live.backend.value !is SyncBackend.Folder) choice = live.backend.value
                }
            },
        )
    }
}

@Composable
private fun BackendChooser(
    choice: SyncBackend,
    onNone: () -> Unit,
    onFolder: () -> Unit,
    onRelay: () -> Unit,
    onS3: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).selectableGroup()) {
        BackendOption(Res.string.sync_backend_none, choice is SyncBackend.None, onNone)
        BackendOption(Res.string.sync_backend_folder, choice is SyncBackend.Folder, onFolder)
        BackendOption(Res.string.sync_backend_relay, choice is SyncBackend.Relay, onRelay)
        BackendOption(Res.string.sync_backend_s3, choice is SyncBackend.S3, onS3)
    }
}

@Composable
private fun BackendOption(label: StringResource, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FolderDetails(
    folder: SyncBackend.Folder,
    busy: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onSync: () -> Unit,
) {
    Text(stringResource(Res.string.folder_sync_configured), style = MaterialTheme.typography.bodyMedium)
    if (folder.uri.isNotEmpty()) {
        Text(
            folder.uri,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    Row {
        Button(enabled = !busy, onClick = onPick) {
            Text(stringResource(Res.string.folder_pick))
        }
        if (folder.uri.isNotEmpty()) {
            TextButton(enabled = !busy, onClick = onClear) {
                Text(stringResource(Res.string.folder_clear))
            }
            Button(enabled = !busy, onClick = onSync) {
                Text(stringResource(Res.string.sync_now))
            }
        }
    }
}

@Suppress("LongParameterList") // A UI field has one parameter per value and callback.
@Composable
private fun RelayDetails(
    url: String,
    error: RelayUrlError?,
    groupId: String?,
    busy: Boolean,
    active: Boolean,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSync: () -> Unit,
) {
    Text(
        stringResource(Res.string.sync_backend_relay_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(Res.string.relay_url)) },
        supportingText = {
            Text(
                stringResource(
                    when (error) {
                        RelayUrlError.Invalid -> Res.string.relay_url_invalid
                        RelayUrlError.Insecure -> Res.string.relay_url_insecure
                        null -> Res.string.relay_url_hint
                    },
                ),
            )
        },
        isError = error != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(enabled = !busy, onClick = onConnect) {
            Text(stringResource(Res.string.relay_save))
        }
        if (active) {
            TextButton(enabled = !busy, onClick = onSync) {
                Text(stringResource(Res.string.sync_now))
            }
        }
    }
    if (groupId != null) {
        Text(
            stringResource(Res.string.relay_group_id, groupId),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(Res.string.relay_group_id_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderPickDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.folder_pick_title)) },
        text = { Text(stringResource(Res.string.folder_pick_hint)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(Res.string.folder_pick_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun SyncStatusLines(status: SyncStatus, backend: SyncBackend, onConfirmEmpty: () -> Unit) {
    Text(
        status.lastSync?.let { stringResource(Res.string.sync_last_sync, it.toString()) }
            ?: stringResource(Res.string.sync_never_synced),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    statusMessage(status, backend)?.let { message ->
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (status.looksEmpty) {
        Text(
            stringResource(
                when (backend) {
                    is SyncBackend.Relay -> Res.string.relay_looks_empty
                    is SyncBackend.S3 -> Res.string.s3_looks_empty
                    else -> Res.string.folder_looks_empty
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onConfirmEmpty) {
            Text(stringResource(Res.string.sync_confirm_empty))
        }
    }
}

private fun statusMessage(status: SyncStatus, backend: SyncBackend): StringResource? =
    status.refused?.refusal?.message()
        ?: status.s3Refused?.refusal?.message()
        ?: when {
            !status.failing -> null
            backend is SyncBackend.Relay -> Res.string.relay_unreachable
            backend is SyncBackend.S3 -> Res.string.s3_unreachable
            else -> Res.string.folder_sync_failing
        }

private fun RelayRefusal.message(): StringResource = when (this) {
    RelayRefusal.Unauthorized -> Res.string.relay_unauthorized
    RelayRefusal.Forbidden -> Res.string.relay_forbidden
    RelayRefusal.NamespaceFull -> Res.string.relay_full
    RelayRefusal.ObjectTooLarge -> Res.string.relay_too_large
}

private fun S3Refusal.message(): StringResource = when (this) {
    S3Refusal.BadCredentials -> Res.string.s3_bad_credentials
    S3Refusal.Forbidden -> Res.string.s3_forbidden
    S3Refusal.NoSuchBucket -> Res.string.s3_no_bucket
    S3Refusal.WrongRegion -> Res.string.s3_wrong_region
    S3Refusal.ClockSkew -> Res.string.s3_clock_skew
}
