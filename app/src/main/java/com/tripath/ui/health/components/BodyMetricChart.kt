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

        fun xOf(index: Int): Float {
            val fraction = if (dataPoints.size > 1) index.toFloat() / (dataPoints.size - 1) else 0.5f
            return chartLeft + fraction * (chartRight - chartLeft)
        }

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

        // Draw the raw line. De-emphasise it when a smoothed trend sits on top.
        val rawAlpha = if (trend != null) 0.35f else 1f
        val rawWidth = if (trend != null) 1.dp.toPx() else 2.dp.toPx()
        val linePath = Path().apply {
            dataPoints.forEachIndexed { index, (_, value) ->
                if (index == 0) moveTo(xOf(0), yOf(value))
                else lineTo(xOf(index), yOf(value))
            }
        }
        drawPath(linePath, color = accentColor.copy(alpha = rawAlpha), style = Stroke(width = rawWidth))

        // Smoothed trend line on top.
        trend?.let { series ->
            val trendPath = Path().apply {
                series.forEachIndexed { index, (_, value) ->
                    if (index == 0) moveTo(xOf(0), yOf(value))
                    else lineTo(xOf(index), yOf(value))
                }
            }
            drawPath(trendPath, color = accentColor, style = Stroke(width = 2.5.dp.toPx()))
        }

        // Highlight outliers as hollow ringed markers (visible but secondary).
        outlierIndices.filter { it in dataPoints.indices }.forEach { idx ->
            val center = Offset(xOf(idx), yOf(dataPoints[idx].second))
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = center)
            drawCircle(
                color = accentColor,
                radius = 4.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Draw dot at last point (on the trend endpoint when smoothed).
        val lastX = xOf(dataPoints.lastIndex)
        val lastY = yOf((trend ?: dataPoints).last().second)
        drawCircle(color = accentColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(lastX, lastY))

        if (!showLabels) return@Canvas

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
