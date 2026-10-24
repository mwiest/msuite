package com.msuite.todo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.msuite.core.design.MsuiteTheme
import com.msuite.todo.domain.ThemeMode
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.my_tasks
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable
private data object TasksRoute

@Serializable
private data object SettingsRoute

@Serializable
private data object PairingRoute

@Serializable
private data object RecoveryRoute

/**
 * The whole ToDo UI over [container]; [version] is shown in About, [shell] is the platform half.
 * Platform shells (apps/todo/android) only host it.
 */
@Composable
fun TodoApp(container: TodoContainer, version: String, shell: ShellServices) {
    val myTasks = stringResource(Res.string.my_tasks)
    LaunchedEffect(container) { container.lists.createMyTasksIfMissing(myTasks) }
    val theme by container.settings.themeMode.collectAsState()
    val dark = when (theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    MsuiteTheme(dark) {
        val nav = rememberNavController()
        NavHost(nav, startDestination = TasksRoute) {
            composable<TasksRoute> { TasksScreen(container) { nav.navigate(SettingsRoute) } }
            composable<SettingsRoute> {
                SettingsScreen(container, version, { nav.popBackStack() }) {
                    nav.navigate(
                        when (it) {
                            SettingsDestination.Pairing -> PairingRoute
                            SettingsDestination.Recovery -> RecoveryRoute
                        },
                    )
                }
            }
            composable<PairingRoute> { PairingScreen(container.pairing, shell.qr) { nav.popBackStack() } }
            composable<RecoveryRoute> {
                RecoveryScreen(container.pairing, shell.clipboard) { nav.popBackStack() }
            }
        }
    }
}
