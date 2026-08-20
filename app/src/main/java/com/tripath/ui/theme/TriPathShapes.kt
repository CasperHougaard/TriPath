package com.tripath.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Shape scale, ported from LiftPath's `lp_dimens.xml`.
 *
 * There are THREE radii and the rule for picking is size-based, not taste-based — a 48dp
 * control with a 20dp radius looks like a pill by accident. LiftPath's layouts shipped seven
 * (8/10/12/14/16/20/24dp), frequently two different ones on nested surfaces of the same card,
 * which is what the reduction to three fixed.
 */
@Immutable
data class TriPathShapes(
    /** Controls under ~56dp: chips, inputs, small tiles, icon buttons. */
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    /** The default. Every card. */
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp),
    /** Full-bleed surfaces: bottom sheets, dialogs, the hero. */
    val large: RoundedCornerShape = RoundedCornerShape(28.dp)
)

val LocalTriPathShapes = staticCompositionLocalOf { TriPathShapes() }

/**
 * The M3 [Shapes] derived from the scale, so stock components — `Card`, `AlertDialog`,
 * `ModalBottomSheet`, `Menu` — round the same way without a call-site edit.
 *
 * `extraSmall` and `extraLarge` deliberately collapse onto `small` and `large` rather than
 * introducing a fourth and fifth radius through the back door.
 */
internal fun materialShapes(s: TriPathShapes): Shapes = Shapes(
    extraSmall = s.small,
    small = s.small,
    medium = s.medium,
    large = s.large,
    extraLarge = s.large
)
