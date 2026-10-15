package com.msuite.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val replica = (application as TodoApplication).replica
        setContent { TodoApp(replica) }
    }
}
