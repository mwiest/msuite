package com.msuite.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.msuite.core.crypto.BackendType
import com.msuite.todo.domain.JoinPreview
import com.msuite.todo.domain.JoinResult
import com.msuite.todo.domain.PairingActions
import com.msuite.todo.domain.QrCodes
import com.msuite.todo.domain.QrMatrix
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.back
import com.msuite.todo.resources.cancel
import com.msuite.todo.resources.ic_arrow_back
import com.msuite.todo.resources.pair_already
import com.msuite.todo.resources.pair_confirm_body
import com.msuite.todo.resources.pair_confirm_join
import com.msuite.todo.resources.pair_confirm_title
import com.msuite.todo.resources.pair_devices
import com.msuite.todo.resources.pair_join_hint
import com.msuite.todo.resources.pair_joined_file
import com.msuite.todo.resources.pair_joined_unknown
import com.msuite.todo.resources.pair_scan
import com.msuite.todo.resources.pair_show
import com.msuite.todo.resources.pair_show_hint
import com.msuite.todo.resources.pair_unreadable
import com.msuite.todo.resources.pair_unsupported
import com.msuite.todo.resources.pairing_hint
import com.msuite.todo.resources.restore_done
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pairing, both directions: this device shows its code, or scans another's (decisions 0008, 0033,
 * 0072). A code is shown on demand and never stored: whoever photographs it has the group root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(pairing: PairingActions, qr: QrCodes, onBack: () -> Unit) {
    var code by remember { mutableStateOf<ByteArray?>(null) }
    var scanned by remember { mutableStateOf<ByteArray?>(null) }
    var result by remember { mutableStateOf<JoinResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.pair_devices)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.pairing_hint), style = MaterialTheme.typography.bodyMedium)
            ShowCode(code, qr, busy) { scope.launch { code = pairing.code() } }
            HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 16.dp))
            Join(busy, result) {
                scope.launch {
                    busy = true
                    result = null
                    qr.scan()?.let { bytes ->
                        when (val preview = pairing.preview(bytes)) {
                            JoinPreview.Ready -> scanned = bytes
                            else -> result = preview.message()
                        }
                    }
                    busy = false
                }
            }
        }
    }

    ConfirmJoin(scanned, { scanned = null }) { bytes ->
        scanned = null
        scope.launch {
            busy = true
            val joined = pairing.join(bytes)
            // The root may have changed; the old code must not stay on screen.
            if (joined is JoinResult.Joined) code = null
            result = joined
            busy = false
        }
    }
}

/** This device's code, shown only once asked for. */
@Composable
private fun ShowCode(code: ByteArray?, qr: QrCodes, busy: Boolean, onShow: () -> Unit) {
    if (code == null) {
        Button(enabled = !busy, onClick = onShow, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(Res.string.pair_show))
        }
        return
    }
    Text(
        stringResource(Res.string.pair_show_hint),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    // White with a wide margin whatever the theme: a scanner needs dark modules on a light quiet zone.
    Surface(color = Color.White, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(top = 16.dp)) {
        QrCode(qr.encode(code), Modifier.padding(24.dp).fillMaxWidth().aspectRatio(1f))
    }
}

/** The camera side, and the outcome of the last attempt. */
@Composable
private fun Join(busy: Boolean, result: JoinResult?, onScan: () -> Unit) {
    Text(stringResource(Res.string.pair_join_hint), style = MaterialTheme.typography.bodyMedium)
    Button(enabled = !busy, onClick = onScan, modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(Res.string.pair_scan))
    }
    result?.let {
        Text(
            stringResource(it.label()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The data-shared confirmation of decision 0033, before a scanned code is adopted. */
@Composable
private fun ConfirmJoin(code: ByteArray?, onDismiss: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    code ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.pair_confirm_title)) },
        text = { Text(stringResource(Res.string.pair_confirm_body)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }) {
                Text(stringResource(Res.string.pair_confirm_join))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

/** [matrix] drawn from the top left, one rect per dark module. */
@Composable
private fun QrCode(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val module = size.minDimension / matrix.size
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) {
                    drawRect(Color.Black, Offset(x * module, y * module), Size(module, module))
                }
            }
        }
    }
}

/** The same outcome as the matching [JoinResult], so the screens show every message one way. */
internal fun JoinPreview.message(): JoinResult = when (this) {
    JoinPreview.Ready -> error("a ready code is confirmed, not reported")
    JoinPreview.AlreadyThisGroup -> JoinResult.AlreadyThisGroup
    JoinPreview.UnsupportedKeyGen -> JoinResult.UnsupportedKeyGen
    JoinPreview.Unreadable -> JoinResult.Unreadable
}

private fun JoinResult.label() = when (this) {
    is JoinResult.Joined -> when (backend) {
        BackendType.Manual -> Res.string.pair_joined_file
        null -> Res.string.pair_joined_unknown
    }

    JoinResult.Restored -> Res.string.restore_done

    JoinResult.AlreadyThisGroup -> Res.string.pair_already

    JoinResult.UnsupportedKeyGen -> Res.string.pair_unsupported

    JoinResult.Unreadable -> Res.string.pair_unreadable
}
