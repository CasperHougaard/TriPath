package com.tripath.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Resolves the appearance choice — a [AppearanceMode] plus one [TriPathPalette] for light and
 * one for dark — into the token layer, and hands it down.
 *
 * The palettes are user preferences, which is why colour cannot be a `val` in this file the way
 * the Compose template had it. Everything below reads its colour from [LocalTriPathColors].
 *
 * Note what is NOT here: no `Application.onActivityPreCreated` hook, no `recreate()`, no theme
 * overlays. LiftPath needs all three because `Activity.setTheme` merges rather than replaces
 * and XML resolves `?attr/` once at inflation. Recomposition handles it here, so switching
 * palette is just a state change.
 */
@Composable
fun TriPathTheme(
    mode: AppearanceMode = AppearanceMode.DEFAULT,
    lightPalette: TriPathPalette = TriPathPalette.DEFAULT,
    darkPalette: TriPathPalette = TriPathPalette.DEFAULT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

    val palette = if (dark) darkPalette else lightPalette
    val colors = palette.colors(dark)

    val typography = remember { TriPathTypography() }
    val shapes = remember { TriPathShapes() }

    CompositionLocalProvider(
        LocalTriPathColors provides colors,
        LocalTriPathTypography provides typography,
        LocalTriPathShapes provides shapes
    ) {
        MaterialTheme(
            colorScheme = remember(colors) { colors.toMaterialScheme() },
            typography = remember(typography) { materialTypography(typography) },
            shapes = remember(shapes) { materialShapes(shapes) },
            content = content
        )
    }
}

/**
 * Accessors for the token layer.
 *
 * Named `TriPathTheme` so a call site reads `TriPathTheme.colors.ink` — deliberately parallel
 * to `MaterialTheme.colorScheme.onSurface`, so the migration is a find-and-replace in shape as
 * well as in meaning.
 */
object TriPathTheme {
    val colors: TriPathColors
        @Composable @ReadOnlyComposable get() = LocalTriPathColors.current

    val type: TriPathTypography
        @Composable @ReadOnlyComposable get() = LocalTriPathTypography.current

    val shapes: TriPathShapes
        @Composable @ReadOnlyComposable get() = LocalTriPathShapes.current
}

/**
 * Projects the roles onto Material 3's colour vocabulary.
 *
 * **This is the single highest-leverage part of the port.** TriPath makes ~450
 * `MaterialTheme.colorScheme.*` references and 189 `Card(` calls; binding the scheme means all
 * of them follow the selected palette immediately, with no call-site edits. It is the same move
 * LiftPath makes when its overlays bind `colorPrimary` alongside the `lp*` tokens.
 *
 * Two mappings deserve their reasoning, because they are where M3's vocabulary and this one
 * genuinely disagree:
 *
 *  - **`surfaceVariant` → `surfaceAlt`, `onSurfaceVariant` → `inkSecondary`.** M3 uses
 *    `onSurfaceVariant` for both "secondary text" and "tertiary text"; TriPath calls those
 *    different roles. Binding it to `inkSecondary` is the safe half — the screen waves promote
 *    the genuinely tertiary cases to `inkTertiary` explicitly, which is a readability
 *    improvement M3's palette cannot express.
 *  - **the `*Container` roles → `accentWash` and friends.** M3 derives containers by tonal
 *    elevation from a seed. There is no seed here; each palette's wash is hand-tuned. So the
 *    containers bind to the wash rather than to a computed tone, which is why
 *    `surfaceColorAtElevation` is never used and every card is elevation 0.
 */
private fun TriPathColors.toMaterialScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = inkInverse,
        primaryContainer = accentWash,
        onPrimaryContainer = accent,
        inversePrimary = accentPressed,

        secondary = accent,
        onSecondary = inkInverse,
        secondaryContainer = surfaceAlt,
        onSecondaryContainer = ink,

        tertiary = disciplineSwim,
        onTertiary = inkInverse,
        tertiaryContainer = surfaceAlt,
        onTertiaryContainer = ink,

        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = inkSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = inkInverse,

        surfaceContainerLowest = canvasSunken,
        surfaceContainerLow = canvas,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        surfaceBright = surfaceAlt,
        surfaceDim = canvasSunken,

        error = negative,
        onError = inkInverse,
        errorContainer = surfaceAlt,
        onErrorContainer = negative,

        outline = hairlineStrong,
        outlineVariant = hairline,
        scrim = Color.Black
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = inkInverse,
        primaryContainer = accentWash,
        onPrimaryContainer = accent,
        inversePrimary = accentPressed,

        secondary = accent,
        onSecondary = inkInverse,
        secondaryContainer = surfaceAlt,
        onSecondaryContainer = ink,

        tertiary = disciplineSwim,
        onTertiary = inkInverse,
        tertiaryContainer = surfaceAlt,
        onTertiaryContainer = ink,

        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = inkSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = inkInverse,

        surfaceContainerLowest = surface,
        surfaceContainerLow = canvas,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        surfaceBright = surface,
        surfaceDim = canvasSunken,

        error = negative,
        onError = inkInverse,
        errorContainer = surfaceAlt,
        onErrorContainer = negative,

        outline = hairlineStrong,
        outlineVariant = hairline,
        scrim = Color.Black
    )
}
