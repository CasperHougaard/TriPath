package com.tripath.ui.coach.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tripath.domain.strain.StrainChannel
import com.tripath.domain.strain.StrainTrend
import com.tripath.ui.components.charts.ChartLegend
import com.tripath.ui.components.charts.ChartLegendEntry
import com.tripath.ui.health.components.ChartSeries
import com.tripath.ui.health.components.MultiSeriesLineChart
import com.tripath.ui.health.components.StackSeries
import com.tripath.ui.health.components.StackedAreaChart
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * How far back the strain charts look.
 *
 * Capped at a season because that is the useful limit of the model rather than of the data: the
 * chronic baseline every freshness figure is scored against only averages 42 days, so a year-long
 * window would draw two thirds of its length against a baseline built from outside it.
 */
enum class StrainTrendWindow(val days: Int, val label: String) {
    MONTH(30, "30d"),
    TWO_MONTHS(60, "60d"),
    SEASON(90, "90d")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrainWindowSelector(
    selected: StrainTrendWindow,
    onSelect: (StrainTrendWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        StrainTrendWindow.entries.forEach { window ->
            FilterChip(
                selected = selected == window,
                onClick = { onSelect(window) },
                label = { Text(window.label) }
            )
        }
    }
}

/**
 * Freshness per channel over time, all four on one 0–100 axis.
 *
 * ## Why the lines and the bars both belong on the screen
 * The bars answer "where am I now", which is what a decision needs. They cannot answer "is my
 * impact channel trending down across this block", which is what a *plan* needs — and that question
 * is the whole reason a four-channel model is worth having.
 *
 * ## The flat top is real, not a bug
 * [com.tripath.domain.strain.StrainTimeline.freshness] clips at 100 once residual falls below
 * `FRESH_THRESHOLD × baseline`, so a consistently-training athlete's easy weeks sit pinned along the
 * top of the chart. The signal lives in the dips; the copy says so rather than letting the shape
 * imply the model has stopped responding.
 */
@Composable
fun ChannelFreshnessCard(
    trend: StrainTrend,
    modifier: Modifier = Modifier
) {
    if (!trend.hasData) {
        StrainEmptyCard("Not enough training logged to chart a freshness trend yet.", modifier)
        return
    }

    val hidden = remember { mutableStateListOf<StrainChannel>() }
    val series = StrainChannel.entries.map { channel ->
        ChartSeries(
            label = channel.label,
            color = channel.toColor(),
            points = trend.freshnessSeries(channel).map { (date, freshness) ->
                date.toEpochMillis() to freshness.toDouble()
            },
            visible = channel !in hidden
        )
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            MultiSeriesLineChart(
                series = series,
                height = 200.dp,
                // Shared axis, not per-series: the point is that impact sits below systemic, and
                // self-normalizing would slide every channel onto the same band.
                valueRange = 0.0..100.0,
                valueLabel = { "${it.roundToInt()}%" }
            )

            ChartLegend(
                entries = StrainChannel.entries.map { channel ->
                    ChartLegendEntry(
                        label = channel.label,
                        color = channel.toColor(),
                        visible = channel !in hidden,
                        onClick = {
                            if (channel in hidden) hidden.remove(channel) else hidden.add(channel)
                        }
                    )
                }
            )

            Text(
                text = "100% is at or below your habitual load, so easy weeks flatten against the " +
                    "top — the dips are where the load was.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Daily load per channel, stacked so the top edge is the day's total.
 *
 * The counterpart to [ChannelFreshnessCard]: this is what the athlete *did*, that is what they are
 * still carrying. Reading them together is how a spike on Tuesday explains a hole on Thursday.
 *
 * No y-axis labels on purpose. Strain units are arbitrary — 300 on the legs means nothing without
 * knowing whether this athlete habitually carries 100 or 500 — so a number here would invite a
 * comparison it cannot support. The composition and the rhythm are the readable parts.
 */
@Composable
fun DailyLoadCard(
    trend: StrainTrend,
    modifier: Modifier = Modifier
) {
    if (!trend.hasInput) {
        StrainEmptyCard("No training logged in this window.", modifier)
        return
    }

    val series = StrainChannel.entries.map { channel ->
        StackSeries(
            label = channel.label,
            color = channel.toColor(),
            points = trend.inputSeries(channel).map { (date, load) -> date.toEpochMillis() to load }
        )
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            StackedAreaChart(
                series = series,
                height = 180.dp,
                // Daily events, not a measured quantity: a spline through 0 → 180 → 0 would dip
                // below the axis and draw load onto a rest day.
                smooth = false
            )

            ChartLegend(
                entries = StrainChannel.entries.map { channel ->
                    ChartLegendEntry(label = channel.label, color = channel.toColor())
                }
            )

            Text(
                text = "Undecayed cost of each day's sessions, split by tissue. Heights are in the " +
                    "model's own units — compare days to each other, not to a target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StrainEmptyCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg)
        )
    }
}

/** The chart components share a millisecond time axis; a strain day is a date at local midnight. */
internal fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
