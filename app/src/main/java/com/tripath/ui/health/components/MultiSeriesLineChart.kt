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
 * Overlays multiple trend lines on a shared time axis.
 *
 * By default each visible series is normalized to its own value range (0–1) so the *shape* of each
 * trend is comparable even when the absolute magnitudes differ (e.g. weight ~80 kg vs fat mass
 * ~15 kg). Draw a legend / toggles separately.
 *
 * Pass [valueRange] when the series are already measured on one scale and their relative heights
 * are the point rather than a distraction — four freshness channels all reading 0–100, say, where
 * self-normalizing would flatten every channel onto the same band and destroy the comparison the
 * chart exists to make. [valueLabel] then labels the y-axis gridlines; supplying it reserves a left
 * gutter for the text, so leave it null to keep the full width.
 */
@Composable
fun MultiSeriesLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp,
    valueRange: ClosedFloatingPointRange<Double>? = null,
    valueLabel: ((Double) -> String)? = null
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

        val labelStyleY = TextStyle(fontSize = 10.sp, color = onSurfaceVariant)
        // Widest tick decides the gutter, so a "100" never clips the way a measured "0" would allow.
        // A label with no range to label is meaningless, so both have to be present.
        val gutter = if (valueLabel != null && valueRange != null) {
            val widest = (0..GRID_LINES).maxOf { i ->
                val value = valueRange.endInclusive -
                    (valueRange.endInclusive - valueRange.start) * i / GRID_LINES
                textMeasurer.measure(valueLabel(value), labelStyleY).size.width
            }
            widest + 8.dp.toPx()
        } else {
            0f
        }

        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 24.dp.toPx()
        val chartLeft = gutter
        val chartRight = size.width

        // Shared time axis across every visible series so lines align in time.
        val allPoints = visible.flatMap { it.points }
        val minTs = allPoints.minOf { it.first }
        val maxTs = allPoints.maxOf { it.first }
        val tsRange = (maxTs - minTs).coerceAtLeast(1L)

        fun xOf(ts: Long): Float =
            chartLeft + ((ts - minTs).toFloat() / tsRange) * (chartRight - chartLeft)

        // Horizontal grid lines, labelled with their value when a shared scale makes that meaningful.
        for (i in 0..GRID_LINES) {
            val y = chartTop + (chartBottom - chartTop) * i / GRID_LINES
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.dp.toPx()
            )
            if (valueLabel != null && valueRange != null) {
                val value = valueRange.endInclusive -
                    (valueRange.endInclusive - valueRange.start) * i / GRID_LINES
                val measured = textMeasurer.measure(valueLabel(value), labelStyleY)
                drawText(
                    measured,
                    topLeft = Offset(
                        chartLeft - measured.size.width - 8.dp.toPx(),
                        y - measured.size.height / 2
                    )
                )
            }
        }

        // Each series against the shared scale when one was given, otherwise its own range.
        visible.forEach { s ->
            val minVal = valueRange?.start ?: s.points.minOf { it.second }
            val maxVal = valueRange?.endInclusive ?: s.points.maxOf { it.second }
            val range = maxVal - minVal
            // No headroom padding on a shared scale: 0 and 100 are the meaningful ends of a
            // freshness axis, and insetting them would put the gridline labels next to the wrong
            // gridlines.
            val padding = when {
                valueRange != null -> 0.0
                range == 0.0 -> 1.0
                else -> range * 0.1
            }

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
        val labelTs = listOf(minTs, minTs + tsRange / 3, minTs + 2 * tsRange / 3, maxTs).distinct()
        labelTs.forEach { ts ->
            val label = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(dateFormatter)
            val measured = textMeasurer.measure(label, labelStyleY)
            val x = (xOf(ts) - measured.size.width / 2)
                .coerceIn(chartLeft, (size.width - measured.size.width).coerceAtLeast(chartLeft))
            drawText(measured, topLeft = Offset(x, chartBottom + 4.dp.toPx()))
        }
    }
}

/** Horizontal gridlines, and therefore y-axis ticks when [MultiSeriesLineChart] labels them. */
private const val GRID_LINES = 4
