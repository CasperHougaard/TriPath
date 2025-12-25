package com.tripath.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Triathlon-inspired color palette with high-contrast accent colors
private val TriBlue = Color(0xFF1565C0)        // Cycling
private val ElectricBlue = Color(0xFF00B8FF)   // Swimming - High contrast
private val SafetyOrange = Color(0xFFFF6B35)   // Running - High contrast
private val StrengthPurple = Color(0xFF9C27B0) // Strength - Visually dominant for off-season
private val TriDarkBlue = Color(0xFF0D47A1)
private val TriLightBlue = Color(0xFF42A5F5)

private val DarkColorScheme = darkColorScheme(
    primary = TriLightBlue,
    secondary = SafetyOrange,
    tertiary = ElectricBlue,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TriBlue,
    secondary = SafetyOrange,
    tertiary = ElectricBlue,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun TriPathTheme(
    darkTheme: Boolean = true, // Force dark theme by default
    // Dynamic color disabled to use our high-contrast colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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

