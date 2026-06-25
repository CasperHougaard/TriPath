package com.tripath.ui.health.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun BodyMetricChart(
    dataPoints: List<Pair<Long, Double>>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        if (dataPoints.size < 2) return@Canvas

        val minVal = dataPoints.minOf { it.second }
        val maxVal = dataPoints.maxOf { it.second }
        val range = maxVal - minVal
        val padding = if (range == 0.0) 1.0 else range * 0.1

        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 28.dp.toPx()
        val chartLeft = 0f
        val chartRight = size.width

        fun xOf(index: Int): Float {
            val fraction = if (dataPoints.size > 1) index.toFloat() / (dataPoints.size - 1) else 0.5f
            return chartLeft + fraction * (chartRight - chartLeft)
        }

        fun yOf(value: Double): Float {
            val fraction = ((value - (minVal - padding)) / (range + 2 * padding)).coerceIn(0.0, 1.0)
            return chartBottom - (fraction * (chartBottom - chartTop)).toFloat()
        }

        // Draw filled area under the line
        val fillPath = Path().apply {
            moveTo(xOf(0), chartBottom)
            dataPoints.forEachIndexed { index, (_, value) ->
                lineTo(xOf(index), yOf(value))
            }
            lineTo(xOf(dataPoints.lastIndex), chartBottom)
            close()
        }
        drawPath(fillPath, color = accentColor.copy(alpha = 0.15f))

        // Draw the line
        val linePath = Path().apply {
            dataPoints.forEachIndexed { index, (_, value) ->
                if (index == 0) moveTo(xOf(0), yOf(value))
                else lineTo(xOf(index), yOf(value))
            }
        }
        drawPath(linePath, color = accentColor, style = Stroke(width = 2.dp.toPx()))

        // Draw dot at last point
        val lastX = xOf(dataPoints.lastIndex)
        val lastY = yOf(dataPoints.last().second)
        drawCircle(color = accentColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(lastX, lastY))

        // X-axis date labels (up to 4 evenly spaced)
        val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
        val labelIndices = when {
            dataPoints.size <= 4 -> dataPoints.indices.toList()
            else -> listOf(0, dataPoints.size / 3, 2 * dataPoints.size / 3, dataPoints.lastIndex)
        }.distinct()

        labelIndices.forEach { idx ->
            val ts = dataPoints[idx].first
            val label = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dateFormatter)
            val measured = textMeasurer.measure(label, labelStyle)
            val x = (xOf(idx) - measured.size.width / 2).coerceIn(0f, size.width - measured.size.width)
            drawText(measured, topLeft = Offset(x, chartBottom + 4.dp.toPx()))
        }
    }
}
