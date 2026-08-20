package com.tripath.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.tripath.data.model.Intensity
import com.tripath.data.model.WorkoutType

/**
 * Discipline and intensity colour, resolved from the active palette.
 *
 * These used to return literals (`Color(0xFF00B8FF)` for SWIM, and so on), which is why the app
 * could not be themed: a literal cannot follow a user's palette choice. The hues are now roles
 * on [TriPathColors], seeded from each palette's chart series so they stay mutually separable
 * in all eight palettes and both modes.
 *
 * WALK and HIKE share [TriPathColors.disciplineOther] with OTHER. They previously had their own
 * green and brown, but they are deliberately classified as OTHER in the training engine so they
 * cannot pollute running pace stats — giving them distinct colours implied a distinction the
 * data model does not make.
 */
@Composable
@ReadOnlyComposable
fun WorkoutType.toColor(): Color = toColor(TriPathTheme.colors)

/**
 * Non-composable overload, for the charts and canvas code that resolve their colours once and
 * pass them into a `DrawScope`.
 */
fun WorkoutType.toColor(colors: TriPathColors): Color = when (this) {
    WorkoutType.SWIM -> colors.disciplineSwim
    WorkoutType.BIKE -> colors.disciplineBike
    WorkoutType.RUN -> colors.disciplineRun
    WorkoutType.STRENGTH -> colors.disciplineStrength
    WorkoutType.OTHER, WorkoutType.WALK, WorkoutType.HIKE -> colors.disciplineOther
}

/**
 * Intensity colour: red reads as hard and green as easy without a legend.
 *
 * [Intensity] carries five values but only three levels — LIGHT maps to LOW and HEAVY to HIGH,
 * as the enum itself documents — so this collapses to the three [TriPathColors] intensity roles
 * rather than inventing two more.
 */
@Composable
@ReadOnlyComposable
fun Intensity.toColor(): Color = toColor(TriPathTheme.colors)

fun Intensity.toColor(colors: TriPathColors): Color = when (this) {
    Intensity.HIGH, Intensity.HEAVY -> colors.intensityHigh
    Intensity.MODERATE -> colors.intensityModerate
    Intensity.LOW, Intensity.LIGHT -> colors.intensityLow
}
