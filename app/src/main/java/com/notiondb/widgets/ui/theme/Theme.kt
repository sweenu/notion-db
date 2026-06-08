package com.notiondb.widgets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun NotionDbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Fixed brand palette rather than Material You dynamic color: a monochrome
    // wallpaper/theme-style on the device otherwise drains the buttons and
    // selected rows to low-contrast grey (see Color.kt).
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
