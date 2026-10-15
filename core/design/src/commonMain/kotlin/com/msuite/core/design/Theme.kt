package com.msuite.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Placeholder Material 3 theme + shared Compose components (Compose Multiplatform).
 * Lives in commonMain so it stays portable to web/desktop later (decision 0012); v1 builds android only.
 */
@Composable
fun MsuiteTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}
