package com.msuite.todo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.msuite.todo.domain.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TodoApplication
        // The bar icons must contrast with the app's theme, which may differ from the system's.
        lifecycleScope.launch { app.settings.themeMode.collect { enableEdgeToEdge(it.barStyle(), it.barStyle()) } }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val shell = ShellServices(SafSnapshotFiles(this), ZxingQrCodes())
        setContent { TodoApp(app.replica, app.settings, version, shell, app.identity) }
    }
}

private fun ThemeMode.barStyle(): SystemBarStyle = when (this) {
    ThemeMode.System -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    ThemeMode.Light -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    ThemeMode.Dark -> SystemBarStyle.dark(Color.TRANSPARENT)
}
