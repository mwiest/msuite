package com.msuite.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.JoinPreview
import com.msuite.todo.domain.JoinResult
import com.msuite.todo.domain.PairingActions
import com.msuite.todo.domain.SensitiveClipboard
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.back
import com.msuite.todo.resources.cancel
import com.msuite.todo.resources.copy_words
import com.msuite.todo.resources.ic_arrow_back
import com.msuite.todo.resources.pair_already
import com.msuite.todo.resources.pair_confirm_body
import com.msuite.todo.resources.pair_unsupported
import com.msuite.todo.resources.recovery
import com.msuite.todo.resources.recovery_words_hint
import com.msuite.todo.resources.restore
import com.msuite.todo.resources.restore_confirm_title
import com.msuite.todo.resources.restore_done
import com.msuite.todo.resources.restore_hint
import com.msuite.todo.resources.restore_unreadable
import com.msuite.todo.resources.restore_words_label
import com.msuite.todo.resources.show_words
import com.msuite.todo.resources.words_copied
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The recovery key (decisions 0011, 0041, record 0073): this device's 24 words, copied as a
 * sensitive clip, and the camera-less way back in on a fresh install.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(pairing: PairingActions, clipboard: SensitiveClipboard, onBack: () -> Unit) {
    var words by remember { mutableStateOf<List<String>?>(null) }
    var copied by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<String>?>(null) }
    var result by remember { mutableStateOf<JoinResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.recovery)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            WordsSection(words, copied, busy, {
                scope.launch { words = pairing.words() }
            }) {
                words?.let {
                    clipboard.copy(it.joinToString(" "))
                    copied = true
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            RestoreSection(typed, busy, result, { typed = it }) {
                scope.launch {
                    busy = true
                    result = null
                    val entered = typed.splitWords()
                    when (val preview = pairing.preview(entered)) {
                        JoinPreview.Ready -> pending = entered
                        else -> result = preview.message()
                    }
                    busy = false
                }
            }
        }
    }

    ConfirmRestore(pending, { pending = null }) { entered ->
        pending = null
        scope.launch {
            busy = true
            result = pairing.restore(entered)
            if (result == JoinResult.Restored || result == JoinResult.AlreadyThisGroup) {
                // The words were the key: do not leave them on screen.
                typed = ""
                words = null
            }
            busy = false
        }
    }
}

/** This device's words, shown only on demand. */
@Composable
private fun WordsSection(
    words: List<String>?,
    copied: Boolean,
    busy: Boolean,
    onShow: () -> Unit,
    onCopy: () -> Unit,
) {
    Text(stringResource(Res.string.recovery_words_hint), style = MaterialTheme.typography.bodyMedium)
    if (words == null) {
        Button(enabled = !busy, onClick = onShow, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(Res.string.show_words))
        }
        return
    }
    WordGrid(words, Modifier.padding(top = 16.dp))
    Button(enabled = !busy, onClick = onCopy, modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(Res.string.copy_words))
    }
    if (copied) {
        Text(
            stringResource(Res.string.words_copied),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** [words] numbered, two to a row, in a monospace face. */
@Composable
private fun WordGrid(words: List<String>, modifier: Modifier = Modifier) {
    Column(modifier) {
        words.chunked(COLUMNS).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                pair.forEachIndexed { column, word ->
                    Text(
                        "${row * COLUMNS + column + 1}. $word",
                        Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/** Typed words and their outcome (0073). */
@Composable
private fun RestoreSection(
    typed: String,
    busy: Boolean,
    result: JoinResult?,
    onTyped: (String) -> Unit,
    onRestore: () -> Unit,
) {
    Text(stringResource(Res.string.restore_hint), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = typed,
        onValueChange = onTyped,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(stringResource(Res.string.restore_words_label)) },
        minLines = 3,
        enabled = !busy,
    )
    Button(enabled = !busy && typed.isNotBlank(), onClick = onRestore, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(Res.string.restore))
    }
    result?.let {
        Text(
            stringResource(it.restoreLabel()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The same data-shared confirmation as QR pairing (decision 0033). */
@Composable
private fun ConfirmRestore(entered: List<String>?, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    entered ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.restore_confirm_title)) },
        text = { Text(stringResource(Res.string.pair_confirm_body)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(entered) }) {
                Text(stringResource(Res.string.restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

/** Words are typed in any case and spacing; they are read as BIP39's lowercase English (0073). */
private fun String.splitWords(): List<String> = trim().split(Regex("\\s+")).filter { it.isNotBlank() }

private fun JoinResult.restoreLabel() = when (this) {
    JoinResult.Restored -> Res.string.restore_done

    JoinResult.AlreadyThisGroup -> Res.string.pair_already

    JoinResult.Unreadable -> Res.string.restore_unreadable

    JoinResult.UnsupportedKeyGen -> Res.string.pair_unsupported

    // Words never carry a backend; kept for exhaustiveness.
    is JoinResult.Joined -> Res.string.restore_done
}

private const val COLUMNS = 2
