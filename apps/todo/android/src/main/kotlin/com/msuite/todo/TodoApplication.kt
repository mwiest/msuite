package com.msuite.todo

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.randomSecret
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.Replica
import com.msuite.todo.domain.TodoSettings
import com.russhwolf.settings.SharedPreferencesSettings
import kotlin.time.Clock

/** The composition root: one [Replica] per process, so activity recreation never opens a second. */
class TodoApplication : Application() {
    val replica: Replica by lazy {
        Replica.open(AndroidSqliteDriver(LocalStore.Schema, this, "todo.db"), Clock.System)
    }

    val settings: TodoSettings by lazy {
        TodoSettings(SharedPreferencesSettings(getSharedPreferences("todo", MODE_PRIVATE)))
    }

    // A process-lifetime root for now; step 6 slice 4 persists it wrapped at rest (decision 0039).
    private val root: ByteArray by lazy { randomSecret() }

    val identity: IdentityProvider = IdentityProvider { keyGen -> if (keyGen == KEY_GEN_V1) root else null }
}
