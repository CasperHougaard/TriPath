package com.tripath.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.tripath.ui.theme.Spacing

/**
 * Standard colors for training zones.
 */
object ZoneColors {
    val Z1 = Color(0xFF9E9E9E) // Gray
    val Z2 = Color(0xFF2196F3) // Blue
    val Z3 = Color(0xFF4CAF50) // Green
    val Z4 = Color(0xFFFF9800) // Orange
    val Z5 = Color(0xFFF44336) // Red
    
    fun getForZone(zoneName: String): Color {
        return when (zoneName) {
            "Z1" -> Z1
            "Z2" -> Z2
            "Z3" -> Z3
            "Z4" -> Z4
            "Z5" -> Z5
            else -> Color.Gray
        }
    }
}

/**
 * A histogram-style chart showing time distribution across intensity zones.
 * Displays both raw time and percentage of total workout.
 *
 * @param distribution Map of zone names to total seconds spent in that zone.
 * @param modifier Modifier for the container.
 */
@Composable
fun ZoneDistributionChart(
    distribution: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val totalSeconds = distribution.values.sum().coerceAtLeast(1)
    val maxSeconds = distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    val zones = listOf("Z5", "Z4", "Z3", "Z2", "Z1")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        zones.forEach { zoneName ->
            val seconds = distribution[zoneName] ?: 0
            val percentage = (seconds.toFloat() / totalSeconds.toFloat()) * 100f
            val ratioToMax = if (maxSeconds > 0) seconds.toFloat() / maxSeconds.toFloat() else 0f
            
            ZoneRow(
                zoneName = zoneName,
                seconds = seconds,
                percentage = percentage.toInt(),
                ratioToMax = ratioToMax,
                color = ZoneColors.getForZone(zoneName)
            )
        }
    }
}

@Composable
private fun ZoneRow(
    zoneName: String,
    seconds: Int,
    percentage: Int,
    ratioToMax: Float,
    color: Color
) {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    val timeLabel = if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Zone Label
        Text(
            text = zoneName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )

        // Bar and Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // The Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratioToMax.coerceIn(0.01f, 1f))
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            
            // Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

