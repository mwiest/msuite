package com.msuite.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.msuite.todo.domain.PairingActions
import com.msuite.todo.domain.QrCodes
import com.msuite.todo.domain.QrMatrix
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.back
import com.msuite.todo.resources.ic_arrow_back
import com.msuite.todo.resources.pair_devices
import com.msuite.todo.resources.pair_show
import com.msuite.todo.resources.pair_show_hint
import com.msuite.todo.resources.pairing_hint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The code another device scans (decisions 0008, 0072). It is shown on demand, never stored: whoever
 * photographs it has the group root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(pairing: PairingActions, qr: QrCodes, onBack: () -> Unit) {
    var code by remember { mutableStateOf<ByteArray?>(null) }
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
            val shown = code
            if (shown == null) {
                Button(onClick = { scope.launch { code = pairing.code() } }, Modifier.padding(top = 16.dp)) {
                    Text(stringResource(Res.string.pair_show))
                }
            } else {
                Text(
                    stringResource(Res.string.pair_show_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                // White with a wide margin whatever the theme: a scanner needs dark modules on a
                // light quiet zone.
                Surface(
                    color = Color.White,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    QrCode(qr.encode(shown), Modifier.padding(24.dp).fillMaxWidth().aspectRatio(1f))
                }
            }
        }
    }
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
