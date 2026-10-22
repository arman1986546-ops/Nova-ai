package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NovaPrimaryDark,
    onPrimary = NovaOnPrimaryDark,
    primaryContainer = NovaPrimaryContainerDark,
    onPrimaryContainer = NovaOnPrimaryContainerDark,
    secondary = NovaSecondaryDark,
    onSecondary = NovaOnSecondaryDark,
    secondaryContainer = NovaSecondaryContainerDark,
    onSecondaryContainer = NovaOnSecondaryContainerDark,
    tertiary = NovaTertiaryDark,
    onTertiary = NovaOnTertiaryDark,
    tertiaryContainer = NovaTertiaryContainerDark,
    onTertiaryContainer = NovaOnTertiaryContainerDark,
    background = NovaBackgroundDark,
    onBackground = NovaOnBackgroundDark,
    surface = NovaSurfaceDark,
    onSurface = NovaOnSurfaceDark,
    surfaceVariant = NovaSurfaceVariantDark,
    onSurfaceVariant = NovaOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = NovaPrimaryLight,
    onPrimary = NovaOnPrimaryLight,
    primaryContainer = NovaPrimaryContainerLight,
    onPrimaryContainer = NovaOnPrimaryContainerLight,
    secondary = NovaSecondaryLight,
    onSecondary = NovaOnSecondaryLight,
    secondaryContainer = NovaSecondaryContainerLight,
    onSecondaryContainer = NovaOnSecondaryContainerLight,
    tertiary = NovaTertiaryLight,
    onTertiary = NovaOnTertiaryLight,
    tertiaryContainer = NovaTertiaryContainerLight,
    onTertiaryContainer = NovaOnTertiaryContainerLight,
    background = NovaBackgroundLight,
    onBackground = NovaOnBackgroundLight,
    surface = NovaSurfaceLight,
    onSurface = NovaOnSurfaceLight,
    surfaceVariant = NovaSurfaceVariantLight,
    onSurfaceVariant = NovaOnSurfaceVariantLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep consistent futuristic AI branding across devices
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
