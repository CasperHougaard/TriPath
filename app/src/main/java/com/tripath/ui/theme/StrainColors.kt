package com.tripath.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.tripath.domain.strain.StrainChannel

/**
 * Colour for the strain model's four channels and its freshness scale.
 *
 * Channels are not disciplines, so they cannot borrow [WorkoutType.toColor] — a run loads three of
 * them at once. They map instead onto the palette's four chart series, which exist precisely
 * because they are hand-tuned to stay mutually separable in every palette and both modes, and
 * there are exactly four of them.
 *
 * The assignment is not arbitrary: impact takes the palette's fatigue hue because it is the
 * channel that punishes, and systemic takes the volume hue because it tracks total work.
 */
@Composable
@ReadOnlyComposable
fun StrainChannel.toColor(): Color = toColor(TriPathTheme.colors)

/** Non-composable overload, for canvas code that resolves colours once and draws with them. */
fun StrainChannel.toColor(colors: TriPathColors): Color = when (this) {
    StrainChannel.LOWER_IMPACT -> colors.chartFatigue
    StrainChannel.LOWER_MUSCULAR -> colors.chartLoad
    StrainChannel.UPPER_MUSCULAR -> colors.chartTime
    StrainChannel.SYSTEMIC -> colors.chartVolume
}

/**
 * Freshness 0–100 as a colour, on the palette's three intensity steps plus [TriPathColors.positive]
 * at the top.
 *
 * The bands match the ones the freshness bars have always used (80 / 55 / 35), so a bar and a chart
 * point at the same value read as the same colour. Green at the top and red at the bottom means the
 * scale needs no legend — which is the same reason [Intensity.toColor] is bound to those roles.
 */
fun freshnessColor(freshness: Int, colors: TriPathColors): Color = when {
    freshness >= FRESH_BAND -> colors.positive
    freshness >= READY_BAND -> colors.intensityLow
    freshness >= COMPROMISED_BAND -> colors.intensityModerate
    else -> colors.intensityHigh
}

@Composable
@ReadOnlyComposable
fun freshnessColor(freshness: Int): Color = freshnessColor(freshness, TriPathTheme.colors)

/**
 * Load — the inverse of freshness — as a tint strength, for the muscle map.
 *
 * A single hue ramped by alpha rather than a green-to-red gradient: on a body diagram the question
 * is "how hard was this worked", which is one dimension, and a hue ramp across ten small shapes is
 * far harder to read than one that just gets darker.
 *
 * ## Clear and never-logged deliberately look the same
 * A null [freshness] means no LiftPath session has ever named that group, and 100 means it is
 * carrying nothing right now. Those are different facts, but they have the same answer to the only
 * question the diagram asks — is there load on this muscle — so both get the neutral fill. Telling
 * them apart is what the numbered list under the map is for.
 *
 * ## Why there is a threshold as well as a floor
 * The floor keeps one light session visible instead of near-transparent. On its own it would also
 * make 1% load jump straight to a clearly-tinted muscle, so anything under
 * [MIN_VISIBLE_LOAD] reads as clear — below that there is genuinely nothing to show.
 */
fun muscleLoadColor(freshness: Int?, colors: TriPathColors): Color {
    if (freshness == null) return colors.hairlineStrong
    val load = (100 - freshness.coerceIn(0, 100)) / 100f
    if (load < MIN_VISIBLE_LOAD) return colors.hairlineStrong
    val scaled = (load - MIN_VISIBLE_LOAD) / (1f - MIN_VISIBLE_LOAD)
    return colors.intensityHigh.copy(alpha = MIN_LOAD_ALPHA + scaled * (1f - MIN_LOAD_ALPHA))
}

private const val FRESH_BAND = 80
private const val READY_BAND = 55
private const val COMPROMISED_BAND = 35

/** ~27%, the same floor LiftPath's muscle map uses so one light set stays visible. */
private const val MIN_LOAD_ALPHA = 0.27f

/** Under 5% of a group's own baseline is rounding, not fatigue. */
private const val MIN_VISIBLE_LOAD = 0.05f
