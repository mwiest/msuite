package com.msuite.todo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.msuite.todo.domain.FolderSyncActions
import com.msuite.todo.domain.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var folderSync: FolderSyncActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TodoApplication
        // The bar icons must contrast with the app's theme, which may differ from the system's.
        lifecycleScope.launch { app.settings.themeMode.collect { enableEdgeToEdge(it.barStyle(), it.barStyle()) } }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val shell = ShellServices(
            files = SafSnapshotFiles(this),
            folder = SafFolderPick(this),
            qr = ZxingQrCodes(this),
            clipboard = app.clipboard,
            fileStoreFactory = { uri -> SafFileStore(applicationContext, android.net.Uri.parse(uri)) },
        )
        folderSync = FolderSyncActions(
            replica = app.replica,
            identity = app.identity,
            settings = app.settings,
            folderPick = shell.folder,
            fileStoreFactory = shell.fileStoreFactory,
            scope = lifecycleScope,
        )
        folderSync.startSyncIfConfigured()
        setContent { TodoApp(app.replica, app.settings, version, shell, app.identity, folderSync) }
    }

    override fun onStart() {
        super.onStart()
        folderSync.startPolling()
    }

    override fun onStop() {
        super.onStop()
        // Flush any pending changes before going to background (addresses the 10s debounce gap).
        // Await the flush before stopping polling to avoid race conditions.
        lifecycleScope.launch {
            folderSync.flushPendingChanges()
            folderSync.stopPolling()
        }
    }
}

private fun ThemeMode.barStyle(): SystemBarStyle = when (this) {
    ThemeMode.System -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    ThemeMode.Light -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    ThemeMode.Dark -> SystemBarStyle.dark(Color.TRANSPARENT)
}
