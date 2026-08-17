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

private val healthyBandColor = Color(0xFF66BB6A)

@Composable
fun BodyMetricChart(
    dataPoints: List<Pair<Long, Double>>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 160.dp,
    showLabels: Boolean = true,
    /**
     * Optional healthy reference band. When set, the chart draws a translucent green band
     * across these values and expands its y-domain so the band is always visible.
     */
    referenceBand: ClosedFloatingPointRange<Double>? = null,
    /**
     * Optional smoothed trend series, aligned 1:1 with [dataPoints] (same order and length).
     * When present, the raw line is de-emphasised and this is drawn on top as the trend.
     */
    smoothed: List<Pair<Long, Double>>? = null,
    /** Indices into [dataPoints] to highlight as likely outliers (hollow ringed markers). */
    outlierIndices: Set<Int> = emptySet(),
    /**
     * Optional fixed y-axis domain. When set, the chart uses exactly this range (no auto-scaling
     * or padding) so all charts of the same metric share a consistent, comparable scale.
     */
    yRange: ClosedFloatingPointRange<Double>? = null
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (dataPoints.size < 2) return@Canvas

        // Only trust a smoothed overlay that lines up 1:1 with the raw points.
        val trend = smoothed?.takeIf { it.size == dataPoints.size }

        // Expand the domain to include the reference band and smoothed values so all render.
        val trendMin = trend?.minOf { it.second } ?: Double.MAX_VALUE
        val trendMax = trend?.maxOf { it.second } ?: Double.MIN_VALUE
        val minVal = yRange?.start
            ?: minOf(dataPoints.minOf { it.second }, referenceBand?.start ?: Double.MAX_VALUE, trendMin)
        val maxVal = yRange?.endInclusive
            ?: maxOf(dataPoints.maxOf { it.second }, referenceBand?.endInclusive ?: Double.MIN_VALUE, trendMax)
        val range = maxVal - minVal
        val padding = when {
            yRange != null -> 0.0
            range == 0.0 -> 1.0
            else -> range * 0.1
        }

        val chartTop = 8.dp.toPx()
        val chartBottom = if (showLabels) size.height - 28.dp.toPx() else size.height - 8.dp.toPx()
        val chartLeft = 0f
        val chartRight = size.width

        val minTs = dataPoints.minOf { it.first }
        val maxTs = dataPoints.maxOf { it.first }
        val tsRange = (maxTs - minTs).coerceAtLeast(1L)

        fun xOf(ts: Long): Float =
            chartLeft + ((ts - minTs).toFloat() / tsRange) * (chartRight - chartLeft)

        fun yOf(value: Double): Float {
            val fraction = ((value - (minVal - padding)) / (range + 2 * padding)).coerceIn(0.0, 1.0)
            return chartBottom - (fraction * (chartBottom - chartTop)).toFloat()
        }

        // Healthy reference band (behind the trend line).
        referenceBand?.let { band ->
            val top = yOf(band.endInclusive)
            val bottom = yOf(band.start)
            drawRect(
                color = healthyBandColor.copy(alpha = 0.14f),
                topLeft = Offset(chartLeft, top),
                size = androidx.compose.ui.geometry.Size(chartRight - chartLeft, bottom - top)
            )
            listOf(top, bottom).forEach { y ->
                drawLine(
                    color = healthyBandColor.copy(alpha = 0.5f),
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                    )
                )
            }
        }

        val rawPoints = dataPoints.map { (ts, value) -> Offset(xOf(ts), yOf(value)) }

        // Draw filled area under the curve
        val fillPath = Path().apply {
            moveTo(rawPoints.first().x, chartBottom)
            lineTo(rawPoints.first().x, rawPoints.first().y)
            curveThrough(rawPoints)
            lineTo(rawPoints.last().x, chartBottom)
            close()
        }
        drawPath(fillPath, color = accentColor.copy(alpha = 0.15f))

        // Draw the raw curve. De-emphasise it when a smoothed trend sits on top.
        val rawAlpha = if (trend != null) 0.35f else 1f
        val rawWidth = if (trend != null) 1.dp.toPx() else 2.dp.toPx()
        val linePath = Path().apply {
            moveTo(rawPoints.first().x, rawPoints.first().y)
            curveThrough(rawPoints)
        }
        drawPath(linePath, color = accentColor.copy(alpha = rawAlpha), style = Stroke(width = rawWidth))

        // Smoothed trend curve on top.
        trend?.let { series ->
            val trendPoints = series.map { (ts, value) -> Offset(xOf(ts), yOf(value)) }
            val trendPath = Path().apply {
                moveTo(trendPoints.first().x, trendPoints.first().y)
                curveThrough(trendPoints)
            }
            drawPath(trendPath, color = accentColor, style = Stroke(width = 2.5.dp.toPx()))
        }

        // Highlight outliers as hollow ringed markers (visible but secondary).
        outlierIndices.filter { it in dataPoints.indices }.forEach { idx ->
            val (ts, value) = dataPoints[idx]
            val center = Offset(xOf(ts), yOf(value))
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = center)
            drawCircle(
                color = accentColor,
                radius = 4.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Draw dot at last point (on the trend endpoint when smoothed).
        val lastPoint = (trend ?: dataPoints).last()
        val lastX = xOf(lastPoint.first)
        val lastY = yOf(lastPoint.second)
        drawCircle(color = accentColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(lastX, lastY))

        if (!showLabels) return@Canvas

        // X-axis date labels (up to 4 evenly spaced across the actual time range)
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
