package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ProfessionalDarkPrimary,
    onPrimary = ProfessionalDarkOnPrimary,
    primaryContainer = ProfessionalDarkPrimaryContainer,
    onPrimaryContainer = ProfessionalDarkOnPrimaryContainer,
    secondary = ProfessionalDarkSecondary,
    onSecondary = ProfessionalDarkOnSecondary,
    secondaryContainer = ProfessionalDarkSecondaryContainer,
    onSecondaryContainer = ProfessionalDarkOnSecondaryContainer,
    tertiary = TelemetryCyan,
    onTertiary = Color.White,
    background = ProfessionalDarkBackground,
    onBackground = ProfessionalDarkTextPrimary,
    surface = ProfessionalDarkSurface,
    onSurface = ProfessionalDarkTextPrimary,
    surfaceVariant = ProfessionalDarkSurfaceVariant,
    onSurfaceVariant = ProfessionalDarkTextSecondary,
    outline = ProfessionalDarkOutline,
    outlineVariant = ProfessionalDarkOutlineVariant,
    error = ProfessionalError,
    onError = ProfessionalOnError
)

private val LightColorScheme = lightColorScheme(
    primary = ProfessionalPrimary,
    onPrimary = ProfessionalOnPrimary,
    primaryContainer = ProfessionalPrimaryContainer,
    onPrimaryContainer = ProfessionalOnPrimaryContainer,
    secondary = ProfessionalSecondary,
    onSecondary = ProfessionalOnSecondary,
    secondaryContainer = ProfessionalSecondaryContainer,
    onSecondaryContainer = ProfessionalOnSecondaryContainer,
    tertiary = ProfessionalTertiary,
    onTertiary = ProfessionalOnTertiary,
    tertiaryContainer = ProfessionalTertiaryContainer,
    onTertiaryContainer = ProfessionalOnTertiaryContainer,
    background = ProfessionalBackground,
    onBackground = ProfessionalTextPrimary,
    surface = ProfessionalSurface,
    onSurface = ProfessionalTextPrimary,
    surfaceVariant = ProfessionalSurfaceVariant,
    onSurfaceVariant = ProfessionalTextSecondary,
    outline = ProfessionalOutline,
    outlineVariant = ProfessionalOutlineVariant,
    error = ProfessionalError,
    onError = ProfessionalOnError,
    errorContainer = ProfessionalErrorContainer,
    onErrorContainer = ProfessionalOnErrorContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Clean Professional Polish light theme by default
    dynamicColor: Boolean = false, // Keep intentional Professional Polish branding
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


