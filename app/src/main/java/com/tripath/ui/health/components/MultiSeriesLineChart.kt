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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A single line series for [MultiSeriesLineChart].
 *
 * @param points (timestampMillis, value) pairs. Values across series may have very different
 *   magnitudes — each series is normalized to its own min/max so trends can be compared.
 */
data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>,
    val visible: Boolean = true
)

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * Overlays multiple trend lines on a shared time axis. Each visible series is normalized to
 * its own value range (0–1) so the *shape* of each trend is comparable even when the absolute
 * magnitudes differ (e.g. weight ~80 kg vs fat mass ~15 kg). Draw a legend / toggles separately.
 */
@Composable
fun MultiSeriesLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val visible = series.filter { it.visible && it.points.size >= 2 }
        if (visible.isEmpty()) return@Canvas

        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 24.dp.toPx()
        val chartLeft = 0f
        val chartRight = size.width

        // Shared time axis across every visible series so lines align in time.
        val allPoints = visible.flatMap { it.points }
        val minTs = allPoints.minOf { it.first }
        val maxTs = allPoints.maxOf { it.first }
        val tsRange = (maxTs - minTs).coerceAtLeast(1L)

        fun xOf(ts: Long): Float =
            chartLeft + ((ts - minTs).toFloat() / tsRange) * (chartRight - chartLeft)

        // Horizontal grid lines.
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = chartTop + (chartBottom - chartTop) * i / gridLines
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Each series normalized to its own range.
        visible.forEach { s ->
            val minVal = s.points.minOf { it.second }
            val maxVal = s.points.maxOf { it.second }
            val range = maxVal - minVal
            val padding = if (range == 0.0) 1.0 else range * 0.1

            fun yOf(value: Double): Float {
                val fraction = ((value - (minVal - padding)) / (range + 2 * padding)).coerceIn(0.0, 1.0)
                return chartBottom - (fraction * (chartBottom - chartTop)).toFloat()
            }

            val path = Path().apply {
                s.points.sortedBy { it.first }.forEachIndexed { index, (ts, value) ->
                    if (index == 0) moveTo(xOf(ts), yOf(value))
                    else lineTo(xOf(ts), yOf(value))
                }
            }
            drawPath(
                path = path,
                color = s.color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Dot at the most recent point.
            val last = s.points.maxByOrNull { it.first }!!
            drawCircle(color = s.color, radius = 3.5.dp.toPx(), center = Offset(xOf(last.first), yOf(last.second)))
        }

        // Shared x-axis date labels (up to 4).
        val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
        val labelTs = listOf(minTs, minTs + tsRange / 3, minTs + 2 * tsRange / 3, maxTs).distinct()
        labelTs.forEach { ts ->
            val label = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dateFormatter)
            val measured = textMeasurer.measure(label, labelStyle)
            val x = (xOf(ts) - measured.size.width / 2).coerceIn(0f, size.width - measured.size.width)
            drawText(measured, topLeft = Offset(x, chartBottom + 4.dp.toPx()))
        }
    }
}
