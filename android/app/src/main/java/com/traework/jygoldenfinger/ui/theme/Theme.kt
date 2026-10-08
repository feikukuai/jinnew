package com.traework.jygoldenfinger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFE8C46A)
val GoldDim = Color(0xFF9C8340)
val Crimson = Color(0xFFB23A48)
val Jade = Color(0xFF3E8E7E)
val Ink = Color(0xFF12121A)
val Ink2 = Color(0xFF1C1C26)
val Ink3 = Color(0xFF262634)
val Parchment = Color(0xFFEDE7DA)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2A2000),
    primaryContainer = GoldDim,
    onPrimaryContainer = Color(0xFF1A1400),
    secondary = Jade,
    onSecondary = Color(0xFF00201A),
    tertiary = Crimson,
    onTertiary = Color(0xFFFFFFFF),
    background = Ink,
    onBackground = Parchment,
    surface = Ink2,
    onSurface = Parchment,
    surfaceVariant = Ink3,
    onSurfaceVariant = Color(0xFFC9C2B4),
    outline = Color(0xFF5A5648),
    error = Color(0xFFEF5350),
    onError = Color(0xFF2A0000)
)

@Composable
fun JygfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}