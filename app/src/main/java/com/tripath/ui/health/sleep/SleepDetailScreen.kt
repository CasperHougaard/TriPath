package com.tripath.ui.health.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.health.HealthTimePeriod
import com.tripath.ui.health.components.BodyMetricChart
import com.tripath.ui.health.formatDuration
import com.tripath.ui.theme.Spacing

private val SleepColor = Color(0xFF7E57C2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SleepViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    Text(
                        text = "No sleep sessions in this period. Sync from Health Connect on the Health tab.",
                        modifier = Modifier.padding(Spacing.lg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            // Averages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                StatCard("Avg duration", state.avgDurationMinutes?.let { formatDuration(it) } ?: "—", Modifier.weight(1f))
                StatCard("Avg score", state.avgScore?.let { "$it" } ?: "—", Modifier.weight(1f))
            }

            // Sleep score trend
            val scorePoints = state.logs
                .mapNotNull { log -> log.sleepScore?.let { log.startTimeMillis to it.toDouble() } }
                .sortedBy { it.first }
            if (scorePoints.size >= 2) {
                SectionHeader(title = "Sleep score", subtitle = "trend")
                Card(modifier = Modifier.fillMaxWidth()) {
                    BodyMetricChart(
                        dataPoints = scorePoints,
                        accentColor = SleepColor,
                        modifier = Modifier.padding(Spacing.md),
                        yRange = 0.0..100.0
                    )
                }
            }

            // Duration trend
            val durationPoints = state.logs
                .map { it.startTimeMillis to (it.durationMinutes / 60.0) }
                .sortedBy { it.first }
            if (durationPoints.size >= 2) {
                SectionHeader(title = "Duration", subtitle = "hours")
                Card(modifier = Modifier.fillMaxWidth()) {
                    BodyMetricChart(
                        dataPoints = durationPoints,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(Spacing.md),
                        yRange = 4.0..12.0
                    )
                }
            }

            SectionHeader(title = "Nights", subtitle = "${state.logs.size} sessions")
            state.logs.forEach { log -> SleepNightRow(log) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SleepNightRow(log: SleepLog) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(log.date.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(formatDuration(log.durationMinutes), style = MaterialTheme.typography.titleMedium, color = SleepColor)
            }
            val stages = listOfNotNull(
                log.deepSleepMinutes?.let { "Deep ${formatDuration(it)}" },
                log.remSleepMinutes?.let { "REM ${formatDuration(it)}" },
                log.lightSleepMinutes?.let { "Light ${formatDuration(it)}" },
                log.awakeMinutes?.let { "Awake ${formatDuration(it)}" }
            )
            if (stages.isNotEmpty()) {
                Text(
                    text = stages.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            log.sleepScore?.let {
                Text("Score $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
