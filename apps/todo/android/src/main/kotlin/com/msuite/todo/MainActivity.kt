package com.msuite.todo

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.msuite.core.relay.RelayPlug
import com.msuite.core.s3.S3Plug
import com.msuite.todo.domain.LiveSyncActions
import com.msuite.todo.domain.RelayOptions
import com.msuite.todo.domain.RelayPlugFactory
import com.msuite.todo.domain.S3Options
import com.msuite.todo.domain.S3PlugFactory
import com.msuite.todo.domain.TODO_APP_ID
import com.msuite.todo.domain.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var liveSync: LiveSyncActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TodoApplication
        // The bar icons must contrast with the app's theme, which may differ from the system's.
        lifecycleScope.launch { app.settings.themeMode.collect { enableEdgeToEdge(it.barStyle(), it.barStyle()) } }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val debug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val shell = ShellServices(
            files = SafSnapshotFiles(this),
            folder = SafFolderPick(this),
            qr = ZxingQrCodes(this),
            clipboard = app.clipboard,
            fileStoreFactory = { uri -> SafFileStore(applicationContext, android.net.Uri.parse(uri)) },
        )
        liveSync = LiveSyncActions(
            replica = app.replica,
            identity = app.identity,
            settings = app.settings,
            folderPick = shell.folder,
            fileStoreFactory = shell.fileStoreFactory,
            scope = lifecycleScope,
            relay = RelayOptions(
                plugFactory = RelayPlugFactory { url, root -> RelayPlug(url, TODO_APP_ID, root, app.httpClient) },
                allowInsecure = debug,
            ),
            s3 = S3Options(
                plugFactory = S3PlugFactory { config, credentials -> S3Plug(config, credentials, app.httpClient) },
                secrets = app.s3Secret,
                allowInsecure = debug,
            ),
        )
        liveSync.startSyncIfConfigured()
        val container = TodoContainer(
            replica = app.replica,
            settings = app.settings,
            files = shell.files,
            identity = app.identity,
            liveSync = liveSync,
        )
        setContent { TodoApp(container, version, shell) }
    }

    override fun onStart() {
        super.onStart()
        liveSync.startPolling()
    }

    override fun onStop() {
        super.onStop()
        // Flush any pending changes before going to background (addresses the 10s debounce gap).
        // Await the flush before stopping polling to avoid race conditions.
        lifecycleScope.launch {
            liveSync.flushPendingChanges()
            liveSync.stopPolling()
        }
    }
}

private fun ThemeMode.barStyle(): SystemBarStyle = when (this) {
    ThemeMode.System -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    ThemeMode.Light -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    ThemeMode.Dark -> SystemBarStyle.dark(Color.TRANSPARENT)
}
