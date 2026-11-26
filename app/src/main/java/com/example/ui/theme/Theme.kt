package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LinkoraLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = IndigoDeep,
    secondary = AquaAccent,
    onSecondary = Color.White,
    secondaryContainer = AquaContainer,
    onSecondaryContainer = IndigoDeep,
    tertiary = CoralWarm,
    onTertiary = Color.White,
    tertiaryContainer = CoralSoft,
    onTertiaryContainer = IndigoDeep,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE9EEF5),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = MissedRed,
    onError = Color.White
)

private val LinkoraDarkColorScheme = darkColorScheme(
    primary = CobaltLight,
    onPrimary = Color(0xFF0A0F1F),
    primaryContainer = Color(0xFF232C5C),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = AquaBright,
    onSecondary = Color(0xFF061320),
    secondaryContainer = Color(0xFF0E3A53),
    onSecondaryContainer = Color(0xFFD8F3FF),
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF1F0D02),
    tertiaryContainer = Color(0xFF5C280C),
    onTertiaryContainer = CoralSoft,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = Color(0xFF080D1A),
    surfaceContainerLow = Color(0xFF0F172A),
    surfaceContainer = Color(0xFF131C31),
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = Color(0xFF24314B),
    outline = DarkOutline,
    outlineVariant = Color(0xFF28354C),
    error = DarkErrorRed,
    onError = Color(0xFF1A0606)
)

val LinkoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LinkoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScaleMultiplier: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LinkoraDarkColorScheme else LinkoraLightColorScheme
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = (currentDensity.fontScale * fontScaleMultiplier).coerceIn(0.85f, 1.45f)
    )
    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LinkoraTypography,
            shapes = LinkoraShapes,
            content = content
        )
    }
}
