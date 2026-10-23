package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val OmniDarkColorScheme = darkColorScheme(
    primary = SkyCyanGlow,
    onPrimary = ObsidianBackground,
    primaryContainer = SkyCyanDeep,
    onPrimaryContainer = TextPrimaryLight,
    secondary = EmeraldGlow,
    onSecondary = ObsidianBackground,
    secondaryContainer = EmeraldDeep,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = AmberHighlight,
    onTertiary = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = TextPrimaryLight,
    surface = DeepSlateSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = ElevatedGlassSurface,
    onSurfaceVariant = TextSecondarySlate,
    outline = GlassBorderCyan,
    error = CoralMedical,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = OmniDarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
