package com.tripath.ui.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripath.ui.stats.VolumeDataPoint
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor

@Composable
fun VolumeChart(
    data: List<VolumeDataPoint>,
    colorByDiscipline: Boolean = false,
    targetAverageHours: Double? = null,
    currentAverageHours: Double? = null,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.height(200.dp), contentAlignment = Alignment.Center) {
            Text("No data available", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val groupedData = data.groupBy { it.date }.toSortedMap()
    val chartMax = groupedData.values
        .maxOfOrNull { points -> points.sumOf { it.durationHours } }
        ?.coerceAtLeast(targetAverageHours ?: 0.0)
        ?.coerceAtLeast(currentAverageHours ?: 0.0)
        ?.coerceAtLeast(1.0)
        ?.toFloat()
        ?: 1f
    val targetFraction = targetAverageHours
        ?.let { (it / chartMax).coerceIn(0.0, 1.0).toFloat() }
    val currentFraction = currentAverageHours
        ?.let { (it / chartMax).coerceIn(0.0, 1.0).toFloat() }
    val targetLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val currentLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            if (targetFraction != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * (1f - targetFraction)
                    val dash = 8.dp.toPx()
                    val gap = 6.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = targetLineColor,
                            start = androidx.compose.ui.geometry.Offset(x, y),
                            end = androidx.compose.ui.geometry.Offset((x + dash).coerceAtMost(size.width), y),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        x += dash + gap
                    }
                }
            }

            if (currentFraction != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * (1f - currentFraction)
                    val dash = 14.dp.toPx()
                    val gap = 5.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = currentLineColor,
                            start = androidx.compose.ui.geometry.Offset(x, y),
                            end = androidx.compose.ui.geometry.Offset((x + dash).coerceAtMost(size.width), y),
                            strokeWidth = 2.dp.toPx()
                        )
                        x += dash + gap
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                groupedData.values.forEach { points ->
                    val totalHours = points.sumOf { it.durationHours }
                    val barHeightFraction = (totalHours / chartMax).coerceIn(0.0, 1.0).toFloat()
                    val segments = points
                        .filter { it.durationHours > 0 }
                        .sortedBy { it.type?.ordinal ?: Int.MAX_VALUE }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (totalHours > 0) {
                            Text(
                                text = String.format("%.1f", totalHours),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        if (totalHours > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .fillMaxWidth(0.6f)
                                    .fillMaxHeight(barHeightFraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            ) {
                                if (colorByDiscipline) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        segments.forEachIndexed { index, point ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(point.durationHours.toFloat())
                                                    .background(point.type?.toColor() ?: MaterialTheme.colorScheme.secondary)
                                            )

                                            if (index < segments.lastIndex) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(MaterialTheme.colorScheme.surface)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            groupedData.values.forEach { points ->
                Text(
                    text = points.first().label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

