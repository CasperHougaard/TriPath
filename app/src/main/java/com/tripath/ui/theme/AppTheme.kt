package com.tripath.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tripath.data.local.preferences.PreferencesManager

/**
 * [TriPathTheme] wired to the stored appearance preference, plus the system-bar contrast that
 * has to follow it.
 *
 * Both `MainActivity` and `HealthConnectPrivacyPolicyActivity` previously carried an identical
 * 20-line `DisposableEffect` for the status and navigation bar icons. Two copies of the same
 * block is how they drift, and the palette work would have needed editing both — so it lives
 * here once and each activity is a single call.
 *
 * The bars are drawn transparent and only their *icon* contrast is set, matching LiftPath. The
 * previous code painted the navigation bar opaque white in light mode, which cut a hard band
 * across the bottom of every screen; the content already applies navigation-bar insets in
 * `MainScreen`, so transparent is both correct and edge-to-edge.
 */
@Composable
fun TriPathAppTheme(
    preferencesManager: PreferencesManager,
    content: @Composable () -> Unit
) {
    val mode by preferencesManager.appearanceModeFlow
        .collectAsState(initial = AppearanceMode.DEFAULT)
    val lightPalette by preferencesManager.lightPaletteFlow
        .collectAsState(initial = TriPathPalette.DEFAULT)
    val darkPalette by preferencesManager.darkPaletteFlow
        .collectAsState(initial = TriPathPalette.DEFAULT)

    TriPathTheme(
        mode = mode,
        lightPalette = lightPalette,
        darkPalette = darkPalette
    ) {
        val view = LocalView.current
        // Read from the resolved palette rather than from `mode`, so AppearanceMode.SYSTEM gets
        // the right contrast without repeating the system-dark check.
        val isDark = TriPathTheme.colors.isDark

        if (!view.isInEditMode) {
            DisposableEffect(isDark) {
                val window = (view.context as? android.app.Activity)?.window
                if (window != null) {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
                onDispose { }
            }
        }

        content()
    }
}
