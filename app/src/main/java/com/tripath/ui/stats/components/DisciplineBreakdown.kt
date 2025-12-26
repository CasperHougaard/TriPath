package com.tripath.ui.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.data.model.WorkoutType
import com.tripath.ui.stats.WorkoutTypeStats
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor

@Composable
fun DisciplineBreakdown(
    stats: List<WorkoutTypeStats>,
    totalWorkouts: Int,
    modifier: Modifier = Modifier
) {
    val sortedStats = stats.sortedByDescending { it.count }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        sortedStats.forEach { stat ->
            DisciplineRow(
                type = stat.type,
                count = stat.count,
                percentage = if (totalWorkouts > 0) stat.count.toFloat() / totalWorkouts else 0f,
                duration = stat.totalDuration
            )
        }
    }
}

@Composable
fun DisciplineRow(
    type: WorkoutType,
    count: Int,
    percentage: Float,
    duration: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = type.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = type.toColor()
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "$count sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Text(
                text = "${(duration / 60.0).let { String.format("%.1f", it) }} hrs",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = type.toColor(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

