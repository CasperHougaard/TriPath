package com.tripath.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colour ROLES.
 *
 * Ported from LiftPath's `lp_attrs.xml`, which expresses the same system as theme attributes
 * because XML layouts have no other way to vary colour at runtime. Compose does, so the roles
 * live here as a plain data class handed down through [LocalTriPathColors].
 *
 * Rules of use:
 *  - Reference a role, never a literal. `TriPathTheme.colors.ink`, not `Color(0xFF191813)`.
 *    A literal silently pins that composable to one palette forever; the 83 literals this file
 *    replaced are exactly how the app ended up un-themeable.
 *  - Adding a colour means adding a ROLE here and binding it in all sixteen palettes in
 *    [Palettes.kt]. **Every field is deliberately without a default**, so a new role is a
 *    compile error until all sixteen bind it. LiftPath renders an unbound token magenta to
 *    catch this at runtime; a data class with no defaults catches it at build time instead.
 *
 * Day/night is handled one level down: each palette ships a light and a dark instance, so
 * there are sixteen palette objects rather than eight plus a mode branch.
 */
@Immutable
data class TriPathColors(

    // ============ SURFACES ============
    /** Page background. */
    val canvas: Color,
    /** Recessed areas: track grooves, empty states, inset wells. */
    val canvasSunken: Color,
    /** Cards. Sits ABOVE the canvas, so it is the brighter surface in light mode. */
    val surface: Color,
    /** Secondary panels nested inside a card. */
    val surfaceAlt: Color,

    // ============ HAIRLINES ============
    /** Cards are defined by a 1dp hairline, not a shadow. */
    val hairline: Color,
    /** Dividers that must actually separate rather than whisper. */
    val hairlineStrong: Color,

    // ============ INK (text + icons) ============
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    /** For content sitting on an [ink] or [accent] fill. */
    val inkInverse: Color,
    val inkInverseSecondary: Color,

    // ============ SIGNATURE ACCENT ============
    /**
     * Used sparingly — roughly three appearances per screen. Past that it stops being a
     * signature and becomes decoration.
     */
    val accent: Color,
    val accentPressed: Color,
    /** Tinted fill behind accented content. */
    val accentWash: Color,

    // ============ STATE / SEMANTIC ============
    val positive: Color,
    val negative: Color,
    val neutral: Color,

    // ============ INTENSITY ============
    /**
     * TriPath's analogue of LiftPath's four set-intent roles. Three rather than four because
     * [com.tripath.data.model.Intensity] collapses to three real levels — LIGHT/LOW,
     * MODERATE, HEAVY/HIGH — and LiftPath's fourth (warmup) is just [neutral] here.
     *
     * Red reads as hard and green as easy without a legend, which is why these are bound to
     * the palette's fatigue and flush hues rather than to arbitrary steps of the accent.
     */
    val intensityHigh: Color,
    val intensityModerate: Color,
    val intensityLow: Color,

    // ============ DISCIPLINE ============
    /**
     * The five roles LiftPath has no equivalent for, because it only trains one discipline.
     *
     * Seeded from each palette's chart series, which are already hand-tuned to stay mutually
     * separable within that palette and in both modes — precisely the constraint discipline
     * colour needs. WALK and HIKE map onto [disciplineOther]: they are deliberately
     * [com.tripath.data.model.WorkoutType.OTHER] so they cannot pollute running pace stats.
     *
     * [disciplineStrength] is the palette's fatigue clay in every palette, which is also
     * LiftPath's `intentStrength` — so a strength session is the same hue in both apps.
     * [disciplineRun] is always a WARM hue even in palettes whose accent is cool, because
     * running is TriPath's dominant discipline and warm is its established identity.
     */
    val disciplineSwim: Color,
    val disciplineBike: Color,
    val disciplineRun: Color,
    val disciplineStrength: Color,
    val disciplineOther: Color,

    // ============ CHARTS ============
    /** Four series that must remain separable in every palette and in both modes. */
    val chartVolume: Color,
    val chartLoad: Color,
    val chartTime: Color,
    val chartFatigue: Color,
    val chartGrid: Color,

    // ============ INTERACTION ============
    val ripple: Color,
    val rippleInverse: Color,

    /** Whether this instance is a dark-mode palette. Drives system-bar icon contrast. */
    val isDark: Boolean
)

/**
 * No default value on purpose: a composable reading colours outside [TriPathTheme] is a bug,
 * and failing loudly beats silently rendering a stand-in palette that looks almost right.
 */
val LocalTriPathColors = staticCompositionLocalOf<TriPathColors> {
    error("No TriPathColors provided — wrap this content in TriPathTheme { }")
}
