package com.tripath.ui.stats.components

import androidx.compose.foundation.background
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
import com.tripath.ui.stats.TssDataPoint
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor

@Composable
fun TssTrendChart(
    data: List<TssDataPoint>,
    colorByDiscipline: Boolean = false,
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
        .maxOfOrNull { points -> points.sumOf { it.tss } }
        ?.coerceAtLeast(50)
        ?.toFloat()
        ?: 50f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            groupedData.values.forEach { points ->
                val totalTss = points.sumOf { it.tss }
                val barHeightFraction = (totalTss / chartMax).coerceIn(0f, 1f)
                val segments = points
                    .filter { it.tss > 0 }
                    .sortedBy { it.type?.ordinal ?: Int.MAX_VALUE }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (totalTss > 0) {
                        Text(
                            text = "$totalTss",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    if (totalTss > 0) {
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
                                                .weight(point.tss.toFloat())
                                                .background(point.type?.toColor() ?: MaterialTheme.colorScheme.primary)
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
                                        .background(MaterialTheme.colorScheme.primary)
                                )
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

