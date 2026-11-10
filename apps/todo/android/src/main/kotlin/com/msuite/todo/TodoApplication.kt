package com.msuite.todo

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.Replica
import com.msuite.todo.domain.SensitiveClipboard
import com.msuite.todo.domain.StoredIdentity
import com.msuite.todo.domain.TodoSettings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
        StoredIdentity(KeyStoreRootStore(KeyStoreSealedFile(File(filesDir, ROOT_FILE))))
    }

    /** Copying the recovery key (decision 0041). */
    val clipboard: SensitiveClipboard by lazy { AndroidSensitiveClipboard(this, appScope) }

    /** The one HTTP client behind the relay plug; OkHttp is the injected engine (decision 0079). */
    val relayClient: HttpClient by lazy {
        HttpClient(OkHttp) { expectSuccess = false }
    }

    private companion object {
        const val ROOT_FILE = "identity.root"
    }
}
