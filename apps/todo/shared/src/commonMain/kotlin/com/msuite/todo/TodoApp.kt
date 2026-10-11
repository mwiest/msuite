package com.msuite.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.msuite.core.design.MsuiteTheme

/** The whole ToDo UI; platform shells (apps/todo/android) only host it. */
@Composable
fun TodoApp() {
    MsuiteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("msuite ToDo — skeleton")
            }
        }
    }
}
