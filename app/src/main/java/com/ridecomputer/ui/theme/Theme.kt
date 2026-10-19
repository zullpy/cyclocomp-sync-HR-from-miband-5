package com.ridecomputer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    secondary = AccentAmber,
    background = DashboardBackground,
    surface = DashboardSurface,
    onPrimary = DashboardBackground,
    onSecondary = DashboardBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = AccentRed,
)

@Composable
fun CyclocompTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
