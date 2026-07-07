package com.tripath.ui.health.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val PositiveGreen = Color(0xFF4CAF50)
private val NegativeRed = Color(0xFFE57373)
private val NeutralGrey = Color(0xFF9E9E9E)

/**
 * Compact trend indicator: an arrow (▲ / ▼ / →) plus the signed delta, color-coded.
 *
 * @param delta the change over the selected period; null renders nothing.
 * @param unit unit suffix appended to the value (e.g. "kg", "%").
 * @param lowerIsBetter when true (weight, body fat) a decrease is shown green; otherwise
 *   (fat-free mass, bone) an increase is shown green.
 * @param decimals number of decimal places to show.
 */
@Composable
fun TrendChip(
    delta: Double?,
    unit: String,
    lowerIsBetter: Boolean,
    modifier: Modifier = Modifier,
    decimals: Int = 1
) {
    if (delta == null) return

    val isFlat = abs(delta) < 0.05
    val arrow = when {
        isFlat -> "→"
        delta > 0 -> "▲"
        else -> "▼"
    }
    val color = when {
        isFlat -> NeutralGrey
        (delta < 0) == lowerIsBetter -> PositiveGreen
        else -> NegativeRed
    }
    val sign = if (delta > 0) "+" else ""
    val formatted = "%.${decimals}f".format(delta)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$arrow $sign$formatted $unit".trim(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
