package com.tripath.ui.health.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.domain.health.AnalysisMetric
import com.tripath.domain.health.CombinedAnalysis
import com.tripath.ui.components.EmptyState
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.health.HealthTimePeriod
import com.tripath.ui.health.formatDuration
import com.tripath.ui.theme.Spacing
import kotlin.math.roundToInt

private val PositiveGreen = Color(0xFF4CAF50)
private val WarnRed = Color(0xFFE57373)

private val METRIC_ORDER = listOf(
    AnalysisMetric.LOAD,
    AnalysisMetric.INTAKE,
    AnalysisMetric.WEIGHT,
    AnalysisMetric.SLEEP
)

private val AnalysisMetric.label: String
    get() = when (this) {
        AnalysisMetric.LOAD -> "Load"
        AnalysisMetric.INTAKE -> "Calories"
        AnalysisMetric.WEIGHT -> "Weight"
        AnalysisMetric.SLEEP -> "Sleep"
    }

private val AnalysisMetric.color: Color
    get() = when (this) {
        AnalysisMetric.LOAD -> Color(0xFFFF6B35)
        AnalysisMetric.INTAKE -> Color(0xFF26A69A)
        AnalysisMetric.WEIGHT -> Color(0xFF5C6BC0)
        AnalysisMetric.SLEEP -> Color(0xFF7E57C2)
    }

/**
 * Cross-domain analysis block for the Health tab: a normalized overlay of training load,
 * calories, weight and sleep on one shared timeline (toggle series in the legend), plus
 * absolute insight cards for fuelling, protein, sleep and weight. Charts + numbers only —
 * no generated prose. The period chips reuse the tab's shared [HealthTimePeriod] selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CombinedAnalysisSection(
    analysis: CombinedAnalysis,
    selectedPeriod: HealthTimePeriod,
    onSelectPeriod: (HealthTimePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        SectionHeader(
            title = "Analysis",
            subtitle = "How training, fuel, weight & sleep connect"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HealthTimePeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onSelectPeriod(period) },
                    label = { Text(period.label) }
                )
            }
        }

        if (!analysis.hasData) {
            EmptyState(
                message = "Not enough data yet",
                description = "Log nutrition and sync workouts, sleep & weight to see how they connect over time."
            )
            return@Column
        }

        val hidden = remember { mutableStateListOf<AnalysisMetric>() }
        val present = METRIC_ORDER.filter { analysis.series.containsKey(it) }
        val chartSeries = present.map { metric ->
            ChartSeries(
                label = metric.label,
                color = metric.color,
                points = analysis.series.getValue(metric),
                visible = metric !in hidden
            )
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                MultiSeriesLineChart(series = chartSeries, height = 220.dp)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    present.forEach { metric ->
                        LegendToggle(
                            label = metric.label,
                            color = metric.color,
                            visible = metric !in hidden,
                            onClick = {
                                if (metric in hidden) hidden.remove(metric) else hidden.add(metric)
                            }
                        )
                    }
                }

                Text(
                    text = "Each line is scaled to its own range — compare the shapes, not the heights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FuelBalanceCard(analysis, Modifier.weight(1f))
            ProteinCard(analysis, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SleepCard(analysis, Modifier.weight(1f))
            WeightCard(analysis, selectedPeriod, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LegendToggle(
    label: String,
    color: Color,
    visible: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (visible) color else color.copy(alpha = 0.25f))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (visible) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnalysisCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FuelBalanceCard(analysis: CombinedAnalysis, modifier: Modifier) {
    val balance = analysis.avgBalanceKcal
    val (value, subtitle) = when {
        !analysis.canComputeBalance ->
            "—" to "Add height, birth date & sex to your profile to estimate this"
        balance == null ->
            "—" to "Log daily calories to compare against your burn"
        else -> {
            val rounded = balance.roundToInt()
            val sign = if (rounded > 0) "+" else ""
            "$sign$rounded kcal/day" to
                "avg vs burn · ${analysis.underFueledDays}/${analysis.balanceDaysCounted} days under-fuelled"
        }
    }
    val accent = when {
        balance == null || !analysis.canComputeBalance -> MaterialTheme.colorScheme.onSurface
        balance >= 0 -> PositiveGreen
        balance < -300 -> WarnRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    AnalysisCard("Fuel balance", value, subtitle, accent, modifier)
}

@Composable
private fun ProteinCard(analysis: CombinedAnalysis, modifier: Modifier) {
    val avg = analysis.avgProteinG
    val target = analysis.proteinTargetG
    val value = avg?.let { "${it.roundToInt()} g" } ?: "—"
    val subtitle = when {
        avg == null -> "No protein logged this period"
        target != null -> "target ${target.roundToInt()} g · met ${analysis.proteinDaysMet}/${analysis.proteinDaysLogged} days"
        else -> "${analysis.proteinDaysLogged} days logged"
    }
    val accent = if (avg != null && target != null && avg >= target) PositiveGreen else Color(0xFF26A69A)
    AnalysisCard("Protein", value, subtitle, accent, modifier)
}

@Composable
private fun SleepCard(analysis: CombinedAnalysis, modifier: Modifier) {
    val mins = analysis.avgSleepMinutes
    val value = mins?.let { formatDuration(it.roundToInt()) } ?: "—"
    val subtitle = when {
        mins == null -> "No sleep data — sync Health Connect"
        analysis.avgSleepScore != null ->
            "avg · score ${analysis.avgSleepScore.roundToInt()} · ${analysis.nightsLogged} nights"
        else -> "avg over ${analysis.nightsLogged} nights"
    }
    AnalysisCard("Sleep", value, subtitle, Color(0xFF7E57C2), modifier)
}

@Composable
private fun WeightCard(
    analysis: CombinedAnalysis,
    period: HealthTimePeriod,
    modifier: Modifier
) {
    val weight = analysis.latestWeightKg
    val delta = analysis.weightDeltaKg
    val value = weight?.let { "%.1f kg".format(it) } ?: "—"
    val subtitle = when {
        weight == null -> "Connect a smart scale"
        delta == null -> "latest · not enough readings for a trend"
        else -> "%+.1f kg over %s".format(delta, period.label)
    }
    AnalysisCard("Weight", value, subtitle, Color(0xFF5C6BC0), modifier)
}
