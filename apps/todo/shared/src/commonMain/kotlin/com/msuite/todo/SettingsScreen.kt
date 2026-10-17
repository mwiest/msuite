package com.msuite.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.SyncActions
import com.msuite.todo.domain.SyncResult
import com.msuite.todo.domain.ThemeMode
import com.msuite.todo.domain.TodoSettings
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.about
import com.msuite.todo.resources.app_name
import com.msuite.todo.resources.author
import com.msuite.todo.resources.back
import com.msuite.todo.resources.export
import com.msuite.todo.resources.ic_arrow_back
import com.msuite.todo.resources.import_file
import com.msuite.todo.resources.license
import com.msuite.todo.resources.license_name
import com.msuite.todo.resources.settings
import com.msuite.todo.resources.source_code
import com.msuite.todo.resources.sync
import com.msuite.todo.resources.sync_cancelled
import com.msuite.todo.resources.sync_deferred
import com.msuite.todo.resources.sync_exported
import com.msuite.todo.resources.sync_failed
import com.msuite.todo.resources.sync_imported
import com.msuite.todo.resources.sync_manual_hint
import com.msuite.todo.resources.sync_too_new
import com.msuite.todo.resources.sync_unknown_key
import com.msuite.todo.resources.sync_write_failed
import com.msuite.todo.resources.theme
import com.msuite.todo.resources.theme_dark
import com.msuite.todo.resources.theme_light
import com.msuite.todo.resources.theme_system
import com.msuite.todo.resources.third_party
import com.msuite.todo.resources.third_party_licenses
import com.msuite.todo.resources.version
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val AUTHOR = "Marc Wiest"
private const val SOURCE_URL = "https://github.com/mwiest/msuite"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The theme and the manual plug; step 6 encrypts the export, later steps add live plugs (decision 0064). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: TodoSettings, sync: SyncActions, version: String, onBack: () -> Unit) {
    val theme by settings.themeMode.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(Res.string.theme))
            ThemePicker(theme, settings::setThemeMode)
            HorizontalDivider(Modifier.padding(top = 24.dp))
            SyncSection(sync)
            HorizontalDivider(Modifier.padding(top = 24.dp))
            About(version)
        }
    }
}

/** Export/import of one snapshot file, the manual plug (decision 0066). Plaintext until step 6. */
@Composable
private fun SyncSection(sync: SyncActions) {
    var result by remember { mutableStateOf<SyncResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // One operation at a time: a second launch would replace the pending picker request, and the
    // call waiting on it would never come back.
    fun run(action: suspend () -> SyncResult) {
        scope.launch {
            busy = true
            result = action()
            busy = false
        }
    }
    SectionTitle(stringResource(Res.string.sync))
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Text(
            stringResource(Res.string.sync_manual_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { run(sync::export) }) {
                Text(stringResource(Res.string.export))
            }
            Button(enabled = !busy, onClick = { run(sync::import) }) {
                Text(stringResource(Res.string.import_file))
            }
        }
        result?.let {
            Text(
                stringResource(it.label()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ThemePicker(theme: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == theme, role = Role.RadioButton) { onPick(mode) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == theme, onClick = null)
                Text(stringResource(mode.label()), Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun About(version: String) {
    val uris = LocalUriHandler.current
    SectionTitle(stringResource(Res.string.about))
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium)
        AboutRow(stringResource(Res.string.version), version)
        AboutRow(stringResource(Res.string.author), AUTHOR)
        AboutRow(stringResource(Res.string.license), stringResource(Res.string.license_name)) {
            uris.openUri(LICENSE_URL)
        }
        AboutRow(stringResource(Res.string.source_code), SOURCE_URL.removePrefix("https://")) {
            uris.openUri(SOURCE_URL)
        }
        AboutRow(stringResource(Res.string.third_party), stringResource(Res.string.third_party_licenses))
    }
}

/** A label above its value; with [onClick], the value is a link. */
@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.System -> Res.string.theme_system
    ThemeMode.Light -> Res.string.theme_light
    ThemeMode.Dark -> Res.string.theme_dark
}

private fun SyncResult.label() = when (this) {
    SyncResult.Exported -> Res.string.sync_exported
    SyncResult.Imported -> Res.string.sync_imported
    SyncResult.Cancelled -> Res.string.sync_cancelled
    SyncResult.Deferred -> Res.string.sync_deferred
    SyncResult.TooNew -> Res.string.sync_too_new
    SyncResult.UnknownKey -> Res.string.sync_unknown_key
    SyncResult.WriteFailed -> Res.string.sync_write_failed
    SyncResult.Failed -> Res.string.sync_failed
}
