package com.tripath.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripath.ui.model.PerformanceDataPoint
import com.tripath.ui.theme.Spacing

@Composable
fun LineChart(
    data: List<PerformanceDataPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val ctlValues = data.map { it.ctl }
    val atlValues = data.map { it.atl }
    
    val minValue = minOf(ctlValues.minOrNull() ?: 0.0, atlValues.minOrNull() ?: 0.0)
    val maxValue = maxOf(ctlValues.maxOrNull() ?: 100.0, atlValues.maxOrNull() ?: 100.0)
    
    // Add some padding to the range
    val valueRange = (maxValue - minValue).coerceAtLeast(10.0)
    val chartMin = minValue - valueRange * 0.1
    val chartMax = maxValue + valueRange * 0.1
    val chartRange = chartMax - chartMin

    val ctlColor = MaterialTheme.colorScheme.primary // Blue
    val atlColor = Color(0xFFFF69B4) // Pink - good contrast in dark mode
    
    // Read colors in @Composable context
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val yAxisTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val xAxisTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        // Chart area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 40.dp.toPx()
                val chartWidth = size.width - padding * 2
                val chartHeight = size.height - padding * 2
                val chartLeft = padding
                val chartTop = padding
                val chartBottom = chartTop + chartHeight

                // Draw grid lines (subtle)
                val gridLineStroke = 1.dp.toPx()
                
                // Horizontal grid lines (5 lines)
                for (i in 0..4) {
                    val y = chartTop + (chartHeight / 4) * i
                    drawLine(
                        color = gridLineColor,
                        start = Offset(chartLeft, y),
                        end = Offset(chartLeft + chartWidth, y),
                        strokeWidth = gridLineStroke
                    )
                }

                // Draw Y-axis labels
                val yAxisTextStyle = TextStyle(
                    color = yAxisTextColor,
                    fontSize = 10.sp
                )
                for (i in 0..4) {
                    val value = chartMax - (chartRange / 4) * i
                    val y = chartTop + (chartHeight / 4) * i
                    val text = String.format("%.0f", value)
                    val textLayoutResult = textMeasurer.measure(text, yAxisTextStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            chartLeft - textLayoutResult.size.width - 8.dp.toPx(),
                            y - textLayoutResult.size.height / 2
                        )
                    )
                }

                // Draw CTL line
                if (data.isNotEmpty()) {
                    val ctlPath = Path()
                    data.forEachIndexed { index, point ->
                        val x = chartLeft + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
                        val normalizedCtl = ((point.ctl - chartMin) / chartRange).coerceIn(0.0, 1.0)
                        val y = chartBottom - (chartHeight * normalizedCtl.toFloat())
                        
                        if (index == 0) {
                            ctlPath.moveTo(x, y)
                        } else {
                            ctlPath.lineTo(x, y)
                        }
                    }
                    
                    drawPath(
                        path = ctlPath,
                        color = ctlColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Draw ATL line
                if (data.isNotEmpty()) {
                    val atlPath = Path()
                    data.forEachIndexed { index, point ->
                        val x = chartLeft + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
                        val normalizedAtl = ((point.atl - chartMin) / chartRange).coerceIn(0.0, 1.0)
                        val y = chartBottom - (chartHeight * normalizedAtl.toFloat())
                        
                        if (index == 0) {
                            atlPath.moveTo(x, y)
                        } else {
                            atlPath.lineTo(x, y)
                        }
                    }
                    
                    drawPath(
                        path = atlPath,
                        color = atlColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Draw X-axis labels (only for points with non-empty labels)
                val xAxisTextStyle = TextStyle(
                    color = xAxisTextColor,
                    fontSize = 10.sp
                )
                data.forEachIndexed { index, point ->
                    if (point.label.isNotEmpty()) {
                        val x = chartLeft + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
                        val textLayoutResult = textMeasurer.measure(point.label, xAxisTextStyle)
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x - textLayoutResult.size.width / 2,
                                chartBottom + 8.dp.toPx()
                            )
                        )
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, top = Spacing.md, end = Spacing.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CTL legend
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .background(ctlColor)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "CTL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.width(Spacing.md))
            
            // ATL legend
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .background(atlColor)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "ATL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

