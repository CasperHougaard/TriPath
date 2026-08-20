package com.tripath.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The app's shared motion vocabulary, ported from LiftPath's `Motion.kt`.
 *
 * Two primitives only — an entrance and a press — because a consistent small set reads as
 * deliberate where a large varied set reads as noise. Timings are LiftPath's, so a card
 * settling in TriPath feels like a card settling in LiftPath.
 *
 * This replaced a private `StaggeredAnimatedItem` that lived inside `DashboardScreen` with its
 * own 50ms/400ms timings. Hoisting it here is the point: one screen's animation constants are
 * not a design system.
 */
object Motion {

    /**
     * Gap between entrance waves. Below ~40ms the stagger stops reading as deliberate; above
     * ~80ms the screen feels slow to settle.
     */
    const val ENTRANCE_STAGGER_MS = 55

    /** How far each element rises into place. */
    val ENTRANCE_RISE = 18.dp

    const val ENTRANCE_FADE_MS = 240

    /** Press feedback. 0.97 is felt but not seen — deeper reads as a toy. */
    const val PRESSED_SCALE = 0.97f
}

/**
 * Fades and spring-rises its content into place, delayed by [index] entrance waves.
 *
 * The rise uses a spring rather than a tween because a decelerate curve arrives and stops
 * dead, whereas a spring settles — and that settle is most of what reads as expensive. The
 * fade stays a tween: springing opacity is not perceptible, only slower.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    staggerMs: Int = Motion.ENTRANCE_STAGGER_MS,
    content: @Composable () -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    val risePx = with(LocalDensity.current) { Motion.ENTRANCE_RISE.roundToPx() }

    LaunchedEffect(Unit) {
        delay((index * staggerMs).toLong())
        state.targetState = true
    }

    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(Motion.ENTRANCE_FADE_MS)) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialOffsetY = { risePx }
        )
    ) {
        content()
    }
}

/**
 * Scales the content to [Motion.PRESSED_SCALE] while [interactionSource] reports a press.
 *
 * Takes the caller's own `InteractionSource` rather than installing a gesture detector, so it
 * composes with `clickable`/`Card(onClick=)` instead of competing with them for the event — the
 * Compose equivalent of LiftPath's touch listener deliberately never consuming the event.
 */
fun Modifier.pressResponse(interactionSource: InteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    scale(scale)
}
