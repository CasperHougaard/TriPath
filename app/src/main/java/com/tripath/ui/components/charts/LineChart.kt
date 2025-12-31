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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripath.ui.model.FormStatus
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
    val tsbValues = data.map { it.tsb }
    
    // Get current and previous TSB values for trend calculation
    val currentTSB = data.lastOrNull()?.tsb ?: 0.0
    val previousTSB = if (data.size >= 2) data[data.size - 2].tsb else currentTSB
    val tsbTrend = currentTSB - previousTSB
    
    // Calculate 7-day and 30-day trends
    val sevenDaysAgoIndex = (data.size - 8).coerceAtLeast(0)
    val thirtyDaysAgoIndex = (data.size - 31).coerceAtLeast(0)
    val tsb7DaysAgo = if (data.size >= 8) data[sevenDaysAgoIndex].tsb else currentTSB
    val tsb30DaysAgo = if (data.size >= 31) data[thirtyDaysAgoIndex].tsb else currentTSB
    val tsb7DayChange = currentTSB - tsb7DaysAgo
    val tsb30DayChange = currentTSB - tsb30DaysAgo
    
    // Determine form status for color coding
    val formStatus = when {
        currentTSB > 5.0 -> FormStatus.FRESHNESS
        currentTSB < -30.0 -> FormStatus.OVERREACHING
        else -> FormStatus.OPTIMAL
    }
    
    val minValue = minOf(ctlValues.minOrNull() ?: 0.0, atlValues.minOrNull() ?: 0.0)
    val maxValue = maxOf(ctlValues.maxOrNull() ?: 100.0, atlValues.maxOrNull() ?: 100.0)
    
    // Add some padding to the range
    val valueRange = (maxValue - minValue).coerceAtLeast(10.0)
    val chartMin = minValue - valueRange * 0.1
    val chartMax = maxValue + valueRange * 0.1
    val chartRange = chartMax - chartMin

    val ctlColor = MaterialTheme.colorScheme.primary // Blue
    val atlColor = Color(0xFFFF69B4) // Pink - good contrast in dark mode
    
    // TSB area fill color (subtle, based on whether CTL > ATL)
    val tsbFillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    
    // TSB text color based on form status
    val tsbTextColor = when (formStatus) {
        FormStatus.FRESHNESS -> Color(0xFF4CAF50) // Green
        FormStatus.OPTIMAL -> MaterialTheme.colorScheme.primary // Blue
        FormStatus.OVERREACHING -> Color(0xFFF44336) // Red
    }
    
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

                // Draw shaded area between CTL and ATL (represents TSB visually)
                if (data.isNotEmpty()) {
                    val fillPath = Path()
                    data.forEachIndexed { index, point ->
                        val x = chartLeft + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
                        val normalizedCtl = ((point.ctl - chartMin) / chartRange).coerceIn(0.0, 1.0)
                        val normalizedAtl = ((point.atl - chartMin) / chartRange).coerceIn(0.0, 1.0)
                        val yCtl = chartBottom - (chartHeight * normalizedCtl.toFloat())
                        val yAtl = chartBottom - (chartHeight * normalizedAtl.toFloat())
                        
                        if (index == 0) {
                            fillPath.moveTo(x, yCtl)
                        } else {
                            fillPath.lineTo(x, yCtl)
                        }
                    }
                    // Complete the path by going back along ATL
                    for (index in data.size - 1 downTo 0) {
                        val x = chartLeft + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
                        val normalizedAtl = ((data[index].atl - chartMin) / chartRange).coerceIn(0.0, 1.0)
                        val yAtl = chartBottom - (chartHeight * normalizedAtl.toFloat())
                        fillPath.lineTo(x, yAtl)
                    }
                    fillPath.close()
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                tsbFillColor.copy(alpha = 0.1f),
                                tsbFillColor.copy(alpha = 0.2f)
                            ),
                            startY = chartTop,
                            endY = chartBottom
                        ),
                        style = Fill
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

        // Legend with TSB sparkline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                
                Spacer(modifier = Modifier.width(Spacing.md))
                
                // TSB indicator with color coding
                val trendSymbol = when {
                    tsbTrend > 0.5 -> "↑"
                    tsbTrend < -0.5 -> "↓"
                    else -> "→"
                }
                Text(
                    text = "TSB: ${String.format("%+.1f", currentTSB)} $trendSymbol",
                    style = MaterialTheme.typography.labelSmall,
                    color = tsbTextColor
                )
            }
            
            // TSB mini sparkline and trend info
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini TSB sparkline (last 14 days or available data)
                val sparklineData = data.takeLast(14)
                if (sparklineData.size >= 2) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(24.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sparklineWidth = size.width
                            val sparklineHeight = size.height
                            val sparklineMin = sparklineData.minOfOrNull { it.tsb } ?: 0.0
                            val sparklineMax = sparklineData.maxOfOrNull { it.tsb } ?: 0.0
                            val sparklineRange = (sparklineMax - sparklineMin).coerceAtLeast(10.0)
                            
                            val sparklinePath = Path()
                            sparklineData.forEachIndexed { index, point ->
                                val x = (sparklineWidth / (sparklineData.size - 1).coerceAtLeast(1)) * index
                                val normalized = ((point.tsb - sparklineMin) / sparklineRange).coerceIn(0.0, 1.0)
                                val y = sparklineHeight - (sparklineHeight * normalized.toFloat())
                                
                                if (index == 0) {
                                    sparklinePath.moveTo(x, y)
                                } else {
                                    sparklinePath.lineTo(x, y)
                                }
                            }
                            
                            drawPath(
                                path = sparklinePath,
                                color = tsbTextColor.copy(alpha = 0.8f),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
                
                // Trend indicators
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "7d: ${String.format("%+.1f", tsb7DayChange)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "30d: ${String.format("%+.1f", tsb30DayChange)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

