package com.msuite.todo

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.Replica
import com.msuite.todo.domain.SensitiveClipboard
import com.msuite.todo.domain.StoredIdentity
import com.msuite.todo.domain.TodoSettings
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.time.Clock

/** The composition root: one [Replica] per process, so activity recreation never opens a second. */
class TodoApplication : Application() {
    // Survives activity recreation: the clipboard timer must outlive the screen (decision 0041).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val replica: Replica by lazy {
        Replica.open(AndroidSqliteDriver(LocalStore.Schema, this, "todo.db"), Clock.System)
    }

    val settings: TodoSettings by lazy {
        TodoSettings(SharedPreferencesSettings(getSharedPreferences("todo", MODE_PRIVATE)))
    }

    /** The group root, generated on first use and wrapped at rest (decisions 0008, 0039). */
    val identity: StoredIdentity by lazy {
        StoredIdentity(KeyStoreRootStore(File(filesDir, ROOT_FILE)))
    }

    /** Copying the recovery key (decision 0041). */
    val clipboard: SensitiveClipboard by lazy { AndroidSensitiveClipboard(this, appScope) }

    private companion object {
        const val ROOT_FILE = "identity.root"
    }
}
