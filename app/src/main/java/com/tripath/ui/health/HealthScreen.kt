package com.tripath.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.health.components.BodyMetricChart
import com.tripath.ui.theme.Spacing

@Composable
fun HealthScreen(
    viewModel: HealthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                SectionHeader(
                    title = "Health",
                    subtitle = "Body composition from Withings",
                    action = {
                        if (state.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.sync() }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync from Health Connect")
                            }
                        }
                    }
                )

                // Period selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HealthTimePeriod.entries.forEach { period ->
                        FilterChip(
                            selected = state.selectedPeriod == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(period.label) }
                        )
                    }
                }

                if (state.logs.isEmpty()) {
                    EmptyStateCard(onSync = { viewModel.sync() }, isSyncing = state.isSyncing)
                } else {
                    // Latest snapshot
                    SectionHeader(title = "Current Metrics")
                    MetricsGrid(state)

                    // Weight chart
                    val weightPoints = state.filteredLogs.mapNotNull { log ->
                        log.weightKg?.let { log.timestamp to it }
                    }
                    if (weightPoints.size >= 2) {
                        SectionHeader(title = "Weight", subtitle = "kg")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BodyMetricChart(
                                dataPoints = weightPoints,
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(Spacing.md)
                            )
                        }
                    }

                    // Body fat chart
                    val fatPoints = state.filteredLogs.mapNotNull { log ->
                        log.bodyFatPercent?.let { log.timestamp to it }
                    }
                    if (fatPoints.size >= 2) {
                        SectionHeader(title = "Body Fat", subtitle = "%")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BodyMetricChart(
                                dataPoints = fatPoints,
                                accentColor = Color(0xFFE57373),
                                modifier = Modifier.padding(Spacing.md)
                            )
                        }
                    }

                    // Muscle mass chart
                    val leanPoints = state.filteredLogs.mapNotNull { log ->
                        log.leanMassKg?.let { log.timestamp to it }
                    }
                    if (leanPoints.size >= 2) {
                        SectionHeader(title = "Muscle Mass", subtitle = "kg")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BodyMetricChart(
                                dataPoints = leanPoints,
                                accentColor = Color(0xFF81C784),
                                modifier = Modifier.padding(Spacing.md)
                            )
                        }
                    }

                    // Bone mass chart
                    val bonePoints = state.filteredLogs.mapNotNull { log ->
                        log.boneMassKg?.let { log.timestamp to it }
                    }
                    if (bonePoints.size >= 2) {
                        SectionHeader(title = "Bone Mass", subtitle = "kg")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BodyMetricChart(
                                dataPoints = bonePoints,
                                accentColor = Color(0xFFFFB74D),
                                modifier = Modifier.padding(Spacing.md)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun MetricsGrid(state: HealthUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MetricCard(
            label = "Weight",
            value = state.latestWeight?.let { "%.1f kg".format(it) } ?: "—",
            delta = state.weightDelta,
            unit = "kg",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Body Fat",
            value = state.latestFatPercent?.let { "%.1f%%".format(it) } ?: "—",
            delta = state.fatPercentDelta,
            unit = "%",
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MetricCard(
            label = "Muscle Mass",
            value = state.latestLeanMass?.let { "%.1f kg".format(it) } ?: "—",
            delta = state.leanMassDelta,
            unit = "kg",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Bone Mass",
            value = state.latestBoneMass?.let { "%.2f kg".format(it) } ?: "—",
            delta = state.boneMassDelta,
            unit = "kg",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    delta: Double?,
    unit: String,
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
            if (delta != null) {
                val sign = if (delta >= 0) "+" else ""
                val color = when {
                    label == "Body Fat" || label == "Weight" -> if (delta < 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                    else -> if (delta > 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                }
                Text(
                    text = "$sign${"%.1f".format(delta)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(onSync: () -> Unit, isSyncing: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "No body composition data yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Connect your Withings scale and tap sync to import your history from Health Connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onSync, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sync from Health Connect")
                }
            }
        }
    }
}
