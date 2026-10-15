package com.msuite.todo

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
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
}
