package com.notiondb.widgets.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fixed brand palette. We deliberately do NOT use Material You dynamic colors:
// on devices whose wallpaper/theme-style yields a monochrome (greyscale) scheme,
// the dynamic `primary`/`secondaryContainer` collapse to low-chroma greys and the
// filled buttons + selected rows render washed-out and low-contrast. A fixed,
// Notion-flavored blue palette guarantees the same contrast on every device.

private val Blue = Color(0xFF2383E2)        // Notion accent blue
private val BlueDark = Color(0xFF0A6CC9)
private val OnBlue = Color(0xFFFFFFFF)
private val BlueContainer = Color(0xFFD6E8FB)
private val OnBlueContainer = Color(0xFF062E52)

val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = OnBlue,
    primaryContainer = BlueContainer,
    onPrimaryContainer = OnBlueContainer,
    secondary = Color(0xFF4A5A6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E3F4),
    onSecondaryContainer = Color(0xFF101C28),
    tertiary = Color(0xFF6750A4),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2E6),
    onSurfaceVariant = Color(0xFF44474A),
    outline = Color(0xFF74777B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC3F5),
    onPrimary = Color(0xFF003258),
    primaryContainer = BlueDark,
    onPrimaryContainer = Color(0xFFD6E8FB),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF24323F),
    secondaryContainer = Color(0xFF3A4856),
    onSecondaryContainer = Color(0xFFD3E3F4),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF381E72),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474A),
    onSurfaceVariant = Color(0xFFC4C7CB),
    outline = Color(0xFF8E9194),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
