package com.tripath.ui.health.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripath.domain.health.EnergyAvailability
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.health.components.BodyMetricChart
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Which measure the needed-vs-eaten chart is showing. */
enum class FuelMetric(val label: String) {
    ENERGY("Energy"),
    PROTEIN("Protein"),
    CARBS("Carbs")
}

/** Point count above which paired columns become unreadable and the chart switches to lines. */
private const val COLUMN_LIMIT = 31

private val AxisDateFormat = DateTimeFormatter.ofPattern("d MMM")

/**
 * What each day needed against what was eaten, with the training that set the requirement.
 *
 * ## One axis, always
 * Both series are in the same unit, so they share a single scale. Normalising each to its own range
 * — which is what the app's general-purpose overlay chart does, correctly, for comparing the
 * *shapes* of weight and sleep — would draw a 500 kcal shortfall as a perfect match. Two scales on
 * one plot is the single most misleading thing a chart of this kind can do.
 *
 * ## Training sits in its own panel
 * The load bars below share the time axis and nothing else. Load is not kcal and must not be given a
 * second y-axis on the same plot; aligned panels answer "why was this day's requirement high?"
 * without pretending the two measures are commensurable.
 *
 * ## Gaps stay gaps
 * A day with nothing logged breaks the eaten line rather than dropping it to zero. Drawing it at zero
 * would invent a day of starvation, and the eye would read the resulting spike as real.
 */
@Composable
fun FuelHistorySection(
    days: List<FuelHistoryDay>,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var metric by remember { mutableStateOf(FuelMetric.ENERGY) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(
            title = "Needed vs eaten",
            subtitle = "What the training asked for, against what went in"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FuelMetric.entries.forEach { option ->
                FilterChip(
                    selected = metric == option,
                    onClick = { metric = option },
                    label = { Text(option.label) }
                )
            }
        }

        val series = remember(days, metric) { days.toSeries(metric) }

        if (series.needed.none { it != null } && series.eaten.none { it != null }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nothing to compare yet — this fills in as days are logged and the fuel " +
                        "model has a weight and a goal to work from.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg)
                )
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                NeededVsEatenPlot(
                    days = days,
                    series = series,
                    metric = metric,
                    onSelectDay = onSelectDay,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )

                // The load panel: same x, its own y, its own baseline.
                LoadPanel(days = days, modifier = Modifier.fillMaxWidth().height(40.dp))

                Legend(
                    metric = metric,
                    seriesColor = series.color,
                    hasEaten = series.eaten.any { it != null }
                )

                // Historical targets are recomputed from the goal and rate currently set, because
                // neither is stored per day. Worth stating rather than letting the line imply it was
                // the prescription at the time.
                Text(
                    text = "Requirements are recalculated from your current goal and each day's " +
                        "weight.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        EnergyAvailabilityTrend(days)
    }
}

/** Both series plus the shared scale they are drawn against. */
private class MetricSeries(
    val needed: List<Double?>,
    val eaten: List<Double?>,
    val unit: String,
    val color: Color,
    val max: Double
)

private fun List<FuelHistoryDay>.toSeries(metric: FuelMetric): MetricSeries {
    val needed = map {
        when (metric) {
            FuelMetric.ENERGY -> it.neededKcal
            FuelMetric.PROTEIN -> it.neededProteinG
            FuelMetric.CARBS -> it.neededCarbsG
        }
    }
    // Carbohydrate has no eaten counterpart: it is prescribed but not logged. An all-null series is
    // the honest representation of that, and the legend says so rather than drawing a flat zero.
    val eaten = map {
        when (metric) {
            FuelMetric.ENERGY -> it.eatenKcal
            FuelMetric.PROTEIN -> it.eatenProteinG
            FuelMetric.CARBS -> null
        }
    }
    val max = (needed + eaten).filterNotNull().maxOrNull() ?: 0.0
    return MetricSeries(
        needed = needed,
        eaten = eaten,
        unit = if (metric == FuelMetric.ENERGY) "kcal" else "g",
        color = when (metric) {
            FuelMetric.ENERGY -> Color(0xFF26A69A)
            FuelMetric.PROTEIN -> Color(0xFF42A5F5)
            FuelMetric.CARBS -> Color(0xFFFFB74D)
        },
        // Zero-based: these are quantities eaten, and a truncated baseline would exaggerate every
        // difference between the two series.
        max = max * 1.1
    )
}

@Composable
private fun NeededVsEatenPlot(
    days: List<FuelHistoryDay>,
    series: MetricSeries,
    metric: FuelMetric,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val over = TriPathTheme.colors.positive
    val under = TriPathTheme.colors.negative
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = axisColor, fontSize = 9.sp)

    Canvas(
        modifier = modifier.pointerInput(days) {
            detectTapGestures { offset ->
                if (days.isEmpty()) return@detectTapGestures
                val slot = size.width.toFloat() / days.size
                val index = (offset.x / slot).toInt().coerceIn(0, days.lastIndex)
                onSelectDay(days[index].date)
            }
        }
    ) {
        if (days.isEmpty() || series.max <= 0.0) return@Canvas

        val top = 4.dp.toPx()
        val bottom = size.height - 14.dp.toPx()
        val plotHeight = bottom - top
        val slot = size.width / days.size

        fun y(value: Double): Float = bottom - (value / series.max).toFloat() * plotHeight
        fun centreX(index: Int): Float = slot * (index + 0.5f)

        // Grid at quarters of the shared scale, so both series are read against the same rule.
        for (i in 0..4) {
            val gridY = top + plotHeight * i / 4f
            drawLine(gridColor, Offset(0f, gridY), Offset(size.width, gridY), strokeWidth = 1.dp.toPx())
        }

        // The scale's ceiling, labelled once. A number on every point is noise.
        textMeasurer.measure("%,.0f ${series.unit}".format(series.max), labelStyle).let {
            drawText(it, topLeft = Offset(0f, 0f))
        }

        if (days.size <= COLUMN_LIMIT) {
            drawColumns(days, series, slot, ::y, bottom, under, over)
        } else {
            drawLines(days, series, ::centreX, ::y, under, over)
        }

        // Date bookends only — enough to place the window without crowding the axis.
        val first = textMeasurer.measure(AxisDateFormat.format(days.first().date), labelStyle)
        drawText(first, topLeft = Offset(0f, bottom + 2.dp.toPx()))
        val last = textMeasurer.measure(AxisDateFormat.format(days.last().date), labelStyle)
        drawText(last, topLeft = Offset(size.width - last.size.width, bottom + 2.dp.toPx()))
    }
}

/**
 * Paired columns, needed behind and eaten in front.
 *
 * The eaten column is drawn narrower and inset rather than side by side, so the pair reads as one
 * day measured against its requirement instead of two unrelated bars competing for the same slot.
 */
private fun DrawScope.drawColumns(
    days: List<FuelHistoryDay>,
    series: MetricSeries,
    slot: Float,
    y: (Double) -> Float,
    baseline: Float,
    under: Color,
    over: Color
) {
    val gap = 2.dp.toPx()
    val neededWidth = (slot - gap).coerceAtLeast(1f)
    val eatenWidth = (neededWidth * 0.55f).coerceAtLeast(1f)

    days.indices.forEach { i ->
        val left = slot * i + gap / 2f
        series.needed[i]?.let { needed ->
            val topY = y(needed)
            drawRect(
                color = series.color.copy(alpha = 0.22f),
                topLeft = Offset(left, topY),
                size = Size(neededWidth, baseline - topY)
            )
        }
        series.eaten[i]?.let { eaten ->
            val topY = y(eaten)
            val needed = series.needed[i]
            drawRect(
                // Colour carries the day's polarity — short or over — which is what the athlete is
                // actually looking for. Identity is carried by width and position, and by the legend.
                color = when {
                    needed == null -> series.color
                    eaten < needed -> under
                    else -> over
                },
                topLeft = Offset(left + (neededWidth - eatenWidth) / 2f, topY),
                size = Size(eatenWidth, baseline - topY)
            )
        }
    }
}

/**
 * Two lines with the space between them shaded by sign.
 *
 * Each contiguous run of logged days is drawn as its own path, so an unlogged day leaves a visible
 * break instead of a straight line implying the model knows what happened.
 */
private fun DrawScope.drawLines(
    days: List<FuelHistoryDay>,
    series: MetricSeries,
    x: (Int) -> Float,
    y: (Double) -> Float,
    under: Color,
    over: Color
) {
    // Shade the gap first, run by run, so the lines sit on top of it.
    var runStart = -1
    fun flushGap(endExclusive: Int) {
        if (runStart < 0 || endExclusive - runStart < 2) { runStart = -1; return }
        val path = Path()
        for (i in runStart until endExclusive) {
            val eaten = y(series.eaten[i]!!)
            if (i == runStart) path.moveTo(x(i), eaten) else path.lineTo(x(i), eaten)
        }
        for (i in endExclusive - 1 downTo runStart) path.lineTo(x(i), y(series.needed[i]!!))
        path.close()
        // One fill per run, coloured by whether the run was mostly short or mostly over. Signed
        // shading per segment would produce confetti on a week that hovers around its target.
        val shortfall = (runStart until endExclusive).count { series.eaten[it]!! < series.needed[it]!! }
        val tint = if (shortfall * 2 >= endExclusive - runStart) under else over
        drawPath(path, color = tint.copy(alpha = 0.18f))
        runStart = -1
    }

    days.indices.forEach { i ->
        val paired = series.eaten[i] != null && series.needed[i] != null
        if (paired && runStart < 0) runStart = i
        if (!paired) flushGap(i)
    }
    flushGap(days.size)

    drawSeries(days.indices, series.needed, x, y, series.color.copy(alpha = 0.55f), dashed = true)
    drawSeries(days.indices, series.eaten, x, y, series.color, dashed = false)
}

/**
 * One series as a stroked path per contiguous run of present values.
 *
 * A run of exactly one day gets a dot: a single-point path strokes to nothing, and an isolated
 * logged day disappearing off the chart is the kind of silent omission that makes a reader trust the
 * rest of it less.
 */
private fun DrawScope.drawSeries(
    indices: IntRange,
    values: List<Double?>,
    x: (Int) -> Float,
    y: (Double) -> Float,
    color: Color,
    dashed: Boolean
) {
    val stroke = Stroke(
        width = 2.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
    )

    var path: Path? = null
    var runLength = 0
    var runStart = 0

    fun flush() {
        val current = path ?: return
        if (runLength == 1) {
            values[runStart]?.let { drawCircle(color, radius = 2.dp.toPx(), center = Offset(x(runStart), y(it))) }
        } else {
            drawPath(current, color = color, style = stroke)
        }
        path = null
        runLength = 0
    }

    for (i in indices) {
        val value = values[i]
        if (value == null) {
            flush()
            continue
        }
        val current = path
        if (current == null) {
            path = Path().apply { moveTo(x(i), y(value)) }
            runStart = i
            runLength = 1
        } else {
            current.lineTo(x(i), y(value))
            runLength++
        }
    }
    flush()
}

/** Training load, on its own baseline and its own scale, sharing only the time axis. */
@Composable
private fun LoadPanel(days: List<FuelHistoryDay>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val maxTss = days.maxOfOrNull { it.tss }?.takeIf { it > 0 } ?: return@Canvas
        val slot = size.width / days.size
        val gap = 2.dp.toPx()
        val width = (slot - gap).coerceAtLeast(1f)
        val label = textMeasurer.measure(
            "Training load",
            TextStyle(color = labelColor, fontSize = 9.sp)
        )
        drawText(label, topLeft = Offset(0f, 0f))

        val top = label.size.height.toFloat() + 2.dp.toPx()
        val height = (size.height - top).coerceAtLeast(1f)
        days.forEachIndexed { i, day ->
            if (day.tss <= 0) return@forEachIndexed
            val barHeight = height * (day.tss.toFloat() / maxTss)
            drawRect(
                color = barColor,
                topLeft = Offset(slot * i + gap / 2f, size.height - barHeight),
                size = Size(width, barHeight)
            )
        }
    }
}

/**
 * Always present, because two series are always described.
 *
 * The swatches carry the colour and the words stay in ink: a legend that tints its own text is
 * unreadable at small sizes and stops working the moment the reader is colourblind.
 */
@Composable
private fun Legend(metric: FuelMetric, seriesColor: Color, hasEaten: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendKey(seriesColor.copy(alpha = 0.22f), "Needed")
        if (hasEaten) {
            LegendKey(TriPathTheme.colors.negative, "Eaten — short")
            LegendKey(TriPathTheme.colors.positive, "over")
        } else {
            Text(
                text = "${metric.label} isn't logged — target only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Energy availability per day against the two reference points.
 *
 * Reuses [BodyMetricChart]'s reference band rather than inventing another chart. The band is drawn
 * from the adequate reference point upward, and the caption carries the caveat: the thresholds are
 * soft, derived largely from studies in women, and this is screening rather than a finding.
 */
@Composable
private fun EnergyAvailabilityTrend(days: List<FuelHistoryDay>) {
    val points = days.mapNotNull { day ->
        day.energyAvailability?.let {
            day.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to it
        }
    }
    if (points.size < 2) return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(
            title = "Energy availability",
            subtitle = "Energy left over after training, per kg of lean mass"
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                BodyMetricChart(
                    dataPoints = points,
                    accentColor = Color(0xFF7E57C2),
                    referenceBand = EnergyAvailability.ADEQUATE_KCAL_PER_KG_FFM..
                        (points.maxOf { it.second }.coerceAtLeast(EnergyAvailability.ADEQUATE_KCAL_PER_KG_FFM) + 5.0),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Shaded band is at or above ${EnergyAvailability.ADEQUATE_KCAL_PER_KG_FFM.toInt()} " +
                        "kcal/kg; ${EnergyAvailability.LOW_SIGNAL_KCAL_PER_KG_FFM.toInt()} is the usual " +
                        "screening reference. Single days mean little — the trend is the signal, and " +
                        "the thresholds are softer than they are usually quoted, especially for men.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
