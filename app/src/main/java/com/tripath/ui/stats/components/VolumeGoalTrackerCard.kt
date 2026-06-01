package com.tripath.ui.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.ui.stats.VolumeGoalProgress
import com.tripath.ui.theme.Spacing
import kotlin.math.abs

@Composable
fun VolumeGoalTrackerCard(
    annualGoalHours: Float?,
    progress: List<VolumeGoalProgress>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (annualGoalHours == null) {
                return@Column
            }

            progress.forEach { item ->
                VolumeGoalProgressRow(item)
            }
        }
    }
}

@Composable
private fun VolumeGoalProgressRow(progress: VolumeGoalProgress) {
    val onTrack = progress.deltaHours >= 0
    val accent = if (onTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = progress.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (abs(progress.deltaHours) < 0.05) {
                    "On pace"
                } else if (onTrack) {
                    "+${formatHours(progress.deltaHours)}h ahead"
                } else {
                    "-${formatHours(abs(progress.deltaHours))}h behind"
                },
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }

        VolumeGoalBar(
            actualFraction = progress.progressFraction,
            expectedFraction = progress.expectedFraction,
            fillColor = accent
        )

        Text(
            text = "${formatHours(progress.actualHours)}h done · ${formatHours(progress.expectedHoursToDate)}h expected by now · ${formatHours(progress.goalHours)}h total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun VolumeGoalBar(
    actualFraction: Float,
    expectedFraction: Float,
    fillColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val radius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        drawRoundRect(
            color = trackColor,
            cornerRadius = radius
        )

        drawRoundRect(
            color = fillColor,
            size = size.copy(width = size.width * actualFraction.coerceIn(0f, 1f)),
            cornerRadius = radius
        )

        val markerX = size.width * expectedFraction.coerceIn(0f, 1f)
        drawLine(
            color = markerColor,
            start = Offset(markerX, 0f),
            end = Offset(markerX, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

private fun formatHours(hours: Double?): String {
    if (hours == null) return "0"
    return if (hours >= 100 || hours % 1.0 == 0.0) {
        hours.toInt().toString()
    } else {
        String.format("%.1f", hours)
    }
}