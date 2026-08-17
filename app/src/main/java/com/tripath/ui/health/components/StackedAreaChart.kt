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
 * One layer of a [StackedAreaChart]. [points] are (timestampMillis, value) pairs measured on a
 * shared absolute axis (e.g. kilograms). Layers are drawn bottom-to-top in list order so they
 * sum visually into a total.
 */
data class StackSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>
)

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * Stacked area chart on a shared, absolute value axis. Unlike [MultiSeriesLineChart] (which
 * normalizes each series independently to compare *shape*), this preserves magnitude and stacks
 * layers so the top edge represents their sum — ideal for body-composition breakdown where
 * fat + lean + bone add up to (roughly) total weight.
 *
 * Only timestamps present across all series are stacked, so every layer aligns cleanly.
 */
@Composable
fun StackedAreaChart(
    series: List<StackSeries>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textMeasurer = rememberTextMeasurer()

    // Only timestamps present in every layer can be stacked meaningfully.
    val commonTs = series
        .map { s -> s.points.map { it.first }.toSet() }
        .reduceOrNull { acc, set -> acc intersect set }
        ?.sorted()
        .orEmpty()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (series.isEmpty() || commonTs.size < 2) return@Canvas

        val valueAt: (StackSeries, Long) -> Double = { s, ts ->
            s.points.firstOrNull { it.first == ts }?.second ?: 0.0
        }

        // Peak stacked total sets the y-scale.
        val maxTotal = commonTs.maxOf { ts -> series.sumOf { valueAt(it, ts) } }
        if (maxTotal <= 0.0) return@Canvas

        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 24.dp.toPx()
        val chartLeft = 0f
        val chartRight = size.width

        val minTs = commonTs.first()
        val maxTs = commonTs.last()
        val tsRange = (maxTs - minTs).coerceAtLeast(1L)

        fun xOf(ts: Long): Float =
            chartLeft + ((ts - minTs).toFloat() / tsRange) * (chartRight - chartLeft)

        fun yOf(cumulative: Double): Float =
            chartBottom - ((cumulative / maxTotal).toFloat() * (chartBottom - chartTop))

        // Horizontal grid lines.
        for (i in 0..4) {
            val y = chartTop + (chartBottom - chartTop) * i / 4
            drawLine(gridColor, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1.dp.toPx())
        }

        // Running cumulative height per timestamp, filled layer by layer.
        val cumulative = DoubleArray(commonTs.size)
        series.forEach { s ->
            val lower = commonTs.mapIndexed { i, _ -> cumulative[i] }
            val upper = commonTs.mapIndexed { i, ts -> cumulative[i] + valueAt(s, ts) }

            val upperPoints = commonTs.mapIndexed { i, ts -> Offset(xOf(ts), yOf(upper[i])) }
            val lowerPoints = commonTs.mapIndexed { i, ts -> Offset(xOf(ts), yOf(lower[i])) }

            val area = Path().apply {
                // Up the top edge...
                moveTo(upperPoints.first().x, upperPoints.first().y)
                curveThrough(upperPoints)
                // ...back along the lower edge.
                lineTo(lowerPoints.last().x, lowerPoints.last().y)
                curveThrough(lowerPoints.reversed())
                close()
            }
            drawPath(area, color = s.color.copy(alpha = 0.55f))

            // Crisp top border for the layer.
            val border = Path().apply {
                moveTo(upperPoints.first().x, upperPoints.first().y)
                curveThrough(upperPoints)
            }
            drawPath(
                border,
                color = s.color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            commonTs.forEachIndexed { i, _ -> cumulative[i] = upper[i] }
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
