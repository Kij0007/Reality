package com.reality.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.reality.android.data.repository.ThemePreference

private val LightColors = lightColorScheme(
    primary = Color(0xFF245D49), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EBDD), onPrimaryContainer = Color(0xFF163C2D),
    secondary = Color(0xFF586B48), secondaryContainer = Color(0xFFEBF0DC),
    background = Color(0xFFF7FAF7), surface = Color(0xFFF7FAF7),
    surfaceContainer = Color(0xFFEEF3EE), error = Color(0xFFB3261E)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED4B5), onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF1B513D), onPrimaryContainer = Color(0xFFBAF0CF),
    secondary = Color(0xFFC0CEA8), secondaryContainer = Color(0xFF3F4E32),
    background = Color(0xFF101C17), surface = Color(0xFF101C17),
    surfaceContainer = Color(0xFF1D2B23), error = Color(0xFFFFB4AB)
)

@Composable
fun RealityTheme(preference: ThemePreference? = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
}
