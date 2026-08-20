package com.tripath.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tripath.R

/**
 * The type scale, ported from LiftPath's `lp_type.xml`.
 *
 * This replaced the unmodified Material 3 scale — 15 styles on Roboto at default sizes, which
 * is most of why the app read as a template. SIX sizes for prose/chrome plus FOUR metric
 * sizes. If a new design seems to need a seventh, it almost certainly wants an existing size
 * at a different weight or colour.
 *
 * Two faces, strictly divided:
 *   Archivo         — everything the user reads
 *   JetBrains Mono  — every number that CHANGES ([TriPathTypography.metricXl] and friends)
 *
 * Fixed numbers that never animate (a date, a count inside a sentence) stay in Archivo. Mono
 * signals that a value is live, not that it is a digit.
 *
 * Colour is deliberately NOT set on these styles, unlike the XML original: in Compose a
 * `TextStyle` carrying a colour would freeze it at composition of this file, before any
 * palette is known. Pass the role at the call site — `color = TriPathTheme.colors.inkSecondary`.
 */

val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold)
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

/**
 * letterSpacing is in `em` to match the XML original — large text needs negative tracking to
 * stop looking loose, small uppercase needs positive tracking to stay legible.
 */
@Immutable
data class TriPathTypography(

    // ================= PROSE + CHROME (Archivo) =================

    /** Screen titles. One per screen, ideally. */
    val display: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.022).em
    ),

    /** Section headings that need to carry weight. */
    val title: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.016).em
    ),

    /** Card titles, list-row primary text. */
    val heading: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.01).em
    ),

    /** Body copy, explanatory text. */
    val body: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.em
    ),

    /** Tile labels, tabs, list secondary. */
    val label: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.004.em
    ),

    /**
     * The "eyebrow": a small label above a metric. Wide tracking is what makes a stat tile
     * read as considered rather than cramped. Uppercase is applied at the call site with
     * `text.uppercase()` — Compose has no `textAllCaps`, and `TextGeometricTransform` would
     * only scale glyphs, not case them.
     */
    val caption: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.07.em
    ),

    /** Buttons. Sentence case, not Material's default ALL CAPS. */
    val button: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.005.em
    ),

    /** Text field input + hint. */
    val input: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.em
    ),

    /** Bottom-nav labels. At 11sp five destination names fit a narrow phone untruncated. */
    val navLabel: TextStyle = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.01.em
    ),

    // ================= METRICS (JetBrains Mono) =================

    /**
     * Hero number: the one figure a screen exists to show. 72sp rather than the ~44sp this
     * scale would otherwise suggest, because its consumers are read at arm's length — a
     * timer or a headline reading, not phone-in-hand text.
     */
    val metricXl: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 72.sp,
        lineHeight = 76.sp,
        letterSpacing = (-0.04).em
    ),

    /** Stat-tile values: weekly TSS, volume, body-scan readings. */
    val metricL: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.02).em
    ),

    /** Inline values sitting next to a larger metric. */
    val metricM: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.01).em
    ),

    /** Small deltas, units, chart axis labels. Mono so +0.4 and -0.4 occupy the same width. */
    val metricS: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.em
    )
)

val LocalTriPathTypography = staticCompositionLocalOf { TriPathTypography() }

/**
 * The Material 3 [Typography] derived from the scale above.
 *
 * This exists for the same reason LiftPath's overlays bind `colorPrimary` as well as the `lp*`
 * tokens: TriPath is built on stock M3 components, and `Button`, `TextField`, `TopAppBar`,
 * `AlertDialog` and friends resolve their face from the theme. Binding it here means every one
 * of them picks up Archivo without a single call-site edit.
 *
 * Screens should still prefer the explicit roles — `TriPathTheme.type.heading` says what it is,
 * where `titleMedium` says only how big it is.
 */
internal fun materialTypography(t: TriPathTypography): Typography = Typography(
    displayLarge = t.display,
    displayMedium = t.display,
    displaySmall = t.title,
    headlineLarge = t.display,
    headlineMedium = t.title,
    headlineSmall = t.title,
    titleLarge = t.title,
    titleMedium = t.heading,
    titleSmall = t.label,
    bodyLarge = t.input,
    bodyMedium = t.body,
    bodySmall = t.label,
    labelLarge = t.button,
    labelMedium = t.label,
    labelSmall = t.caption
)
