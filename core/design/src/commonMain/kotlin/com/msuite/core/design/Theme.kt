package com.msuite.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Placeholder Material 3 theme + shared Compose components (Compose Multiplatform).
 * Lives in commonMain so it stays portable to web/desktop later (OQ-12); v1 builds android only.
 */
@Composable
fun MsuiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
