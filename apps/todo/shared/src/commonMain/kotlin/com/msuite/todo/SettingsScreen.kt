package com.msuite.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.ThemeMode
import com.msuite.todo.domain.TodoSettings
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.back
import com.msuite.todo.resources.ic_arrow_back
import com.msuite.todo.resources.settings
import com.msuite.todo.resources.theme
import com.msuite.todo.resources.theme_dark
import com.msuite.todo.resources.theme_light
import com.msuite.todo.resources.theme_system
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The theme for now; step 5 adds the backend plug (decision 0064). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: TodoSettings, onBack: () -> Unit) {
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
        Column(Modifier.padding(padding).selectableGroup()) {
            Text(
                stringResource(Res.string.theme),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = mode == theme, role = Role.RadioButton) { settings.setThemeMode(mode) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == theme, onClick = null)
                    Text(stringResource(mode.label()), Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.System -> Res.string.theme_system
    ThemeMode.Light -> Res.string.theme_light
    ThemeMode.Dark -> Res.string.theme_dark
}
