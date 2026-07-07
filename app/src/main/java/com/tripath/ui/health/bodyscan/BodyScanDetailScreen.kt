package com.tripath.ui.health.bodyscan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.domain.health.BodyCompositionAnalytics
import com.tripath.domain.health.BodyCompositionAnalytics.BodyCompositionStats
import com.tripath.domain.health.BodyCompositionAnalytics.Confidence
import com.tripath.domain.health.BodyCompositionAnalytics.Insight
import com.tripath.domain.health.BodyCompositionAnalytics.InsightTone
import com.tripath.domain.health.BodyCompositionAnalytics.RecompositionVerdict
import com.tripath.domain.health.HealthReference
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.health.HealthTimePeriod
import com.tripath.ui.health.components.BodyMetricChart
import com.tripath.ui.health.components.StackSeries
import com.tripath.ui.health.components.StackedAreaChart
import com.tripath.ui.health.components.TrendChip
import com.tripath.ui.health.fatFreeMassKg
import com.tripath.ui.health.fatMassKg
import com.tripath.ui.theme.Spacing

private val FatColor = Color(0xFFE57373)
private val LeanColor = Color(0xFF64B5F6)
private val BoneColor = Color(0xFFFFB74D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScanDetailScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToManageData: () -> Unit = {},
    viewModel: BodyScanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Body Scan") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToManageData) {
                        Icon(Icons.Default.List, contentDescription = "Manage data")
                    }
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.sync() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync from Health Connect")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HealthTimePeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(period.label) }
                    )
                }
            }

            if (state.logs.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("No body scan data yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Connect your smart scale (e.g. Withings) to Health Connect, then sync from the Health tab. Weight, body fat, fat-free and bone mass will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            HeroCard(state)
            state.stats?.let { stats ->
                InsightCards(stats)
                RecompositionSummary(stats)
            }
            MetricsGrid(state)
            state.stats?.let { DataQualityCard(it) }
            WeightCompositionCard(state)
            WeightSection(state)
            BodyFatSection(state)
            BmiSection(state)

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun HeroCard(state: BodyScanUiState) {
    val weightPoints = state.filteredLogs.mapNotNull { log -> log.weightKg?.let { log.timestamp to it } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Latest weight",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.latestWeight?.let { "%.1f kg".format(it) } ?: "—",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                TrendChip(delta = state.weightDelta, unit = "kg", lowerIsBetter = true)
            }
            if (weightPoints.size >= 2) {
                BodyMetricChart(
                    dataPoints = weightPoints,
                    accentColor = MaterialTheme.colorScheme.primary,
                    height = 88.dp,
                    showLabels = false
                )
            }
        }
    }
}

@Composable
private fun MetricsGrid(state: BodyScanUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MetricCard(
            label = "Weight",
            value = state.latestWeight?.let { "%.1f kg".format(it) } ?: "—",
            secondary = state.latestBmi?.let { "BMI %.1f".format(it) },
            delta = state.weightDelta,
            unit = "kg",
            lowerIsBetter = true,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Body Fat",
            value = state.latestFatPercent?.let { "%.1f%%".format(it) } ?: "—",
            secondary = state.bodyFatCategory ?: state.latestFatMassKg?.let { "%.1f kg fat".format(it) },
            delta = state.fatPercentDelta,
            unit = "%",
            lowerIsBetter = true,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(Spacing.md))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MetricCard(
            label = "Fat-free mass",
            value = state.latestLeanMass?.let { "%.1f kg".format(it) } ?: "—",
            secondary = state.stats?.ffmi?.let { "FFMI %.1f".format(it) },
            delta = state.leanMassDelta,
            unit = "kg",
            lowerIsBetter = false,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Bone Mass",
            value = state.latestBoneMass?.let { "%.2f kg".format(it) } ?: "—",
            secondary = null,
            delta = state.boneMassDelta,
            unit = "kg",
            lowerIsBetter = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    secondary: String?,
    delta: Double?,
    unit: String,
    lowerIsBetter: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            secondary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TrendChip(delta = delta, unit = unit, lowerIsBetter = lowerIsBetter)
        }
    }
}

/**
 * Weight split into estimated fat mass + fat-free mass. The two layers sum to body weight, so
 * the chart never overshoots the scale reading or double-counts bone. Only logs that carry both
 * weight and body-fat% contribute (fat-free mass = weight − fat mass).
 */
@Composable
private fun WeightCompositionCard(state: BodyScanUiState) {
    val fat = state.filteredLogs.mapNotNull { l -> l.fatMassKg?.let { l.timestamp to it } }
    val fatFree = state.filteredLogs.mapNotNull { l -> l.fatFreeMassKg?.let { l.timestamp to it } }

    // Need at least two scans that carry both components to plot a composition trend.
    if (fat.size < 2 || fatFree.size < 2) return

    // Legend uses the same derived endpoints the chart plots, so numbers match the layers.
    val latestFat = fat.last().second
    val latestFatFree = fatFree.last().second

    SectionHeader(title = "Weight composition", subtitle = "Fat vs fat-free mass · kg")

    // When confidence is low, the composition estimate is too noisy to draw honestly.
    if (state.stats?.confidence == Confidence.LOW) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    LegendItem("Fat mass", FatColor, latestFat)
                    LegendItem("Fat-free", LeanColor, latestFatFree)
                }
                Text(
                    text = "Not enough consistent scans to draw a reliable composition trend yet. The numbers above show the latest estimate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LegendItem("Fat mass", FatColor, latestFat)
                LegendItem("Fat-free", LeanColor, latestFatFree)
            }
            StackedAreaChart(
                series = listOf(
                    StackSeries("Fat mass", FatColor, fat),
                    StackSeries("Fat-free", LeanColor, fatFree)
                )
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, value: Double?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color = color) }
        val v = value?.let { " %.1f".format(it) } ?: ""
        Text("$label$v", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BodyFatSection(state: BodyScanUiState) {
    val fatPoints = state.filteredLogs.mapNotNull { log ->
        log.bodyFatPercent?.let { log.timestamp to it }
    }
    val band = state.bodyFatBand
    val subtitle = buildString {
        append("%")
        band?.let { append(" · healthy ${"%.0f".format(it.min)}–${"%.0f".format(it.max)}%") }
    }
    SectionHeader(
        title = "Body Fat",
        subtitle = subtitle,
        action = state.bodyFatCategory?.let { { CategoryChip(it) } }
    )
    if (band == null) {
        MissingProfileHint("Add your sex and age in Settings ▸ Profile to see the healthy body-fat range.")
    }
    if (fatPoints.size < 2) {
        LowDataHint()
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        BodyMetricChart(
            dataPoints = fatPoints,
            accentColor = FatColor,
            modifier = Modifier.padding(Spacing.md),
            referenceBand = band?.range,
            smoothed = BodyCompositionAnalytics.smoothed(fatPoints)
        )
    }
}

@Composable
private fun BmiSection(state: BodyScanUiState) {
    val height = state.heightCm
    val bmiPoints = state.filteredLogs.mapNotNull { log ->
        HealthReference.bmi(log.weightKg, height)?.let { log.timestamp to it }
    }
    if (height == null) {
        SectionHeader(title = "BMI", subtitle = "kg/m²")
        MissingProfileHint("Add your height in Settings ▸ Profile to track BMI.")
        return
    }
    SectionHeader(
        title = "BMI",
        subtitle = "kg/m² · healthy 18.5–24.9",
        action = state.bmiCategory?.let { { CategoryChip(it) } }
    )
    if (bmiPoints.size < 2) {
        LowDataHint()
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        BodyMetricChart(
            dataPoints = bmiPoints,
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(Spacing.md),
            referenceBand = HealthReference.bmiHealthyBand.range,
            smoothed = BodyCompositionAnalytics.smoothed(bmiPoints)
        )
    }
}

@Composable
private fun WeightSection(state: BodyScanUiState) {
    val weightLogs = state.filteredLogs.filter { it.weightKg != null }
    val weightPoints = weightLogs.map { it.timestamp to it.weightKg!! }
    SectionHeader(title = "Weight", subtitle = "kg")
    if (weightPoints.size < 2) {
        LowDataHint()
        return
    }
    val outlierIds = state.stats?.outlierIds.orEmpty()
    val outlierIndices = weightLogs.mapIndexedNotNull { i, l -> if (l.id in outlierIds) i else null }.toSet()
    Card(modifier = Modifier.fillMaxWidth()) {
        BodyMetricChart(
            dataPoints = weightPoints,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(Spacing.md),
            smoothed = BodyCompositionAnalytics.smoothed(weightPoints),
            outlierIndices = outlierIndices
        )
    }
}

/** Shown in place of a chart when a metric has fewer than two scans in the period. */
@Composable
private fun LowDataHint() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Need at least two scans in this period to show a trend.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.md)
        )
    }
}

/** Concise, neutral read-outs of the current trend. Horizontally scrollable when they overflow. */
@Composable
private fun InsightCards(stats: BodyCompositionStats) {
    if (stats.insights.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        stats.insights.forEach { insight -> InsightCard(insight) }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val accent = insight.tone.color()
    Card(
        modifier = Modifier.width(200.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Conservative recomposition read for the period, plus the confidence it rests on. */
@Composable
private fun RecompositionSummary(stats: BodyCompositionStats) {
    val (headline, detail) = when (stats.recomposition) {
        RecompositionVerdict.FAT_LOSS_LEAN_MAINTAINED ->
            "Fat loss, fat-free mass maintained" to "Fat mass is trending down while fat-free mass holds — the athletic recomposition pattern."
        RecompositionVerdict.WEIGHT_GAIN_LEAN_UP ->
            "Weight up, fat-free mass up" to "Weight and fat-free mass are both increasing this period."
        RecompositionVerdict.WEIGHT_LOSS_LEAN_DOWN ->
            "Weight down, fat-free mass down" to "Weight is dropping and some fat-free mass is coming with it."
        RecompositionVerdict.MOSTLY_STABLE ->
            "Mostly stable" to "No clear directional change in fat or fat-free mass this period."
        RecompositionVerdict.INSUFFICIENT_DATA ->
            "Not enough data yet" to "Not enough consistent scans to read recomposition. Keep weighing regularly."
    }
    SectionHeader(title = "Recomposition")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                CategoryChip("Confidence: ${stats.confidence.label()}")
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Measurement-consistency helper so the user can judge how much to trust the trends. */
@Composable
private fun DataQualityCard(stats: BodyCompositionStats) {
    SectionHeader(title = "Data quality")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QualityRow("Valid scans", "${stats.validCount}")
            QualityRow(
                "Average gap",
                stats.avgGapDays?.let { "%.1f days".format(it) } ?: "—"
            )
            val missing = stats.missingMetric.values.sum()
            QualityRow("Missing metrics", if (missing == 0) "None" else "$missing readings")
            QualityRow(
                "Possible outliers",
                if (stats.outlierIds.isEmpty()) "None" else "${stats.outlierIds.size}"
            )
            Text(
                text = "For the best trend quality, weigh under similar conditions — same time of day and similar hydration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}

@Composable
private fun QualityRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InsightTone.color(): Color = when (this) {
    InsightTone.POSITIVE -> Color(0xFF4CAF50)
    InsightTone.CAUTION -> Color(0xFFFFB74D)
    InsightTone.NEUTRAL -> MaterialTheme.colorScheme.primary
}

private fun Confidence.label(): String = when (this) {
    Confidence.HIGH -> "High"
    Confidence.MEDIUM -> "Medium"
    Confidence.LOW -> "Low"
}

@Composable
private fun CategoryChip(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

@Composable
private fun MissingProfileHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
