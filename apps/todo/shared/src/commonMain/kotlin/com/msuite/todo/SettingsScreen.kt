package com.msuite.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.ThemeMode
import com.msuite.todo.domain.TodoSettings
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.about
import com.msuite.todo.resources.app_name
import com.msuite.todo.resources.author
import com.msuite.todo.resources.back
import com.msuite.todo.resources.ic_arrow_back
import com.msuite.todo.resources.license
import com.msuite.todo.resources.license_name
import com.msuite.todo.resources.settings
import com.msuite.todo.resources.source_code
import com.msuite.todo.resources.theme
import com.msuite.todo.resources.theme_dark
import com.msuite.todo.resources.theme_light
import com.msuite.todo.resources.theme_system
import com.msuite.todo.resources.third_party
import com.msuite.todo.resources.third_party_licenses
import com.msuite.todo.resources.version
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val AUTHOR = "Marc Wiest"
private const val SOURCE_URL = "https://github.com/mwiest/msuite"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The theme for now; step 5 adds the backend plug (decision 0064). About sits at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: TodoSettings, version: String, onBack: () -> Unit) {
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
            About(version)
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
