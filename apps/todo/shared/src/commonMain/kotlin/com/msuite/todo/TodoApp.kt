package com.msuite.todo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.msuite.core.design.MsuiteTheme
import com.msuite.core.sync.Replica
import com.msuite.todo.domain.FolderSyncActions
import com.msuite.todo.domain.StoredIdentity
import com.msuite.todo.domain.ThemeMode
import com.msuite.todo.domain.TodoSettings
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
 * The whole ToDo UI over [replica] and [settings]; [version] is shown in About, [shell] is the
 * platform half, and [identity] is the group root. Platform shells (apps/todo/android) only host it.
 */
@Composable
fun TodoApp(
    replica: Replica,
    settings: TodoSettings,
    version: String,
    shell: ShellServices,
    identity: StoredIdentity,
) {
    val scope = rememberCoroutineScope()
    val folderSync = remember(replica, identity, settings, shell, scope) {
        FolderSyncActions(
            replica = replica,
            identity = identity,
            settings = settings,
            folderPick = shell.folder,
            fileStoreFactory = shell.fileStoreFactory,
            scope = scope,
        )
    }
    val app = remember(replica, settings, shell, identity, folderSync) {
        TodoContainer(replica, settings, shell.files, identity, folderSync)
    }
    val myTasks = stringResource(Res.string.my_tasks)
    LaunchedEffect(app) { app.lists.createMyTasksIfMissing(myTasks) }
    LaunchedEffect(folderSync) { folderSync.startSyncIfConfigured() }
    val theme by settings.themeMode.collectAsState()
    val dark = when (theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    MsuiteTheme(dark) {
        val nav = rememberNavController()
        NavHost(nav, startDestination = TasksRoute) {
            composable<TasksRoute> { TasksScreen(app) { nav.navigate(SettingsRoute) } }
            composable<SettingsRoute> {
                SettingsScreen(app.settings, app.sync, app.folder, version, { nav.popBackStack() }) {
                    nav.navigate(
                        when (it) {
                            SettingsDestination.Pairing -> PairingRoute
                            SettingsDestination.Recovery -> RecoveryRoute
                        },
                    )
                }
            }
            composable<PairingRoute> { PairingScreen(app.pairing, shell.qr) { nav.popBackStack() } }
            composable<RecoveryRoute> {
                RecoveryScreen(app.pairing, shell.clipboard) { nav.popBackStack() }
            }
        }
    }
}
