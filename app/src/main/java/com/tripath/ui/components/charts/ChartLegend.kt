package com.tripath.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One entry in a [ChartLegend]. [onClick] non-null makes it a visibility toggle. */
data class ChartLegendEntry(
    val label: String,
    val color: Color,
    val visible: Boolean = true,
    val onClick: (() -> Unit)? = null
)

/**
 * The swatch-and-label row that goes under a multi-series chart.
 *
 * The chart composables draw only the plot area — they have no idea what a series *means*, and a
 * legend inside a `Canvas` cannot be tapped. Wrapping in [FlowRow] so a fourth or fifth series
 * wraps rather than truncating.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(
    entries: List<ChartLegendEntry>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        entries.forEach { entry ->
            val row = Modifier
                .clip(RoundedCornerShape(50))
                .let { base -> entry.onClick?.let { base.clickable(onClick = it) } ?: base }
                .padding(horizontal = 8.dp, vertical = 4.dp)
            Row(
                modifier = row,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (entry.visible) entry.color else entry.color.copy(alpha = 0.25f)
                        )
                )
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (entry.visible) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
