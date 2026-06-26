package com.tripath.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.stats.components.DisciplineBreakdown
import com.tripath.ui.stats.components.KeyMetricsGrid
import com.tripath.ui.stats.components.PeriodSelector
import com.tripath.ui.stats.components.TssTrendChart
import com.tripath.ui.stats.components.VolumeGoalTrackerCard
import com.tripath.ui.stats.components.VolumeChart
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme

import androidx.compose.ui.unit.dp

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colorByDiscipline by viewModel.colorByDiscipline.collectAsStateWithLifecycle()
    var showVolumeGoalDialog by remember { mutableStateOf(false) }
    var annualGoalInput by remember(uiState.annualVolumeGoalHours, showVolumeGoalDialog) {
        mutableStateOf(uiState.annualVolumeGoalHours?.let { formatHours(it.toDouble()) }.orEmpty())
    }

    Scaffold(modifier = modifier) { paddingValues ->
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
                // Header & Period Selector
                SectionHeader(
                    title = "Training Statistics",
                    subtitle = "Analyze your performance"
                )

                PeriodSelector(
                    selectedPeriod = uiState.selectedPeriod,
                    onPeriodSelected = { viewModel.selectPeriod(it) }
                )

                // Toggle at the top
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Color by discipline", modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = colorByDiscipline, onCheckedChange = { viewModel.setColorByDiscipline(it) })
                }

                // 1. Performance Overview
                SectionHeader(title = "Performance")
                KeyMetricsGrid(
                    totalTSS = uiState.totalTSS,
                    totalWorkouts = uiState.totalWorkouts,
                    totalDistance = uiState.totalDistance,
                    totalHours = uiState.totalHours
                )

                SectionHeader(
                    title = "Volume Goal",
                    subtitle = "Track year, month, and week pacing",
                    action = {
                        val targetText = uiState.annualVolumeGoalHours?.let {
                            "${formatHours(it.toDouble())}h target"
                        } ?: "Set target"
                        TextButton(onClick = { showVolumeGoalDialog = true }) {
                            Text(targetText)
                        }
                    }
                )
                VolumeGoalTrackerCard(
                    annualGoalHours = uiState.annualVolumeGoalHours,
                    progress = uiState.volumeGoalProgress
                )

                // 2. Training Load (TSS)
                SectionHeader(
                    title = "Training Load",
                    subtitle = "TSS Trend & Fatigue"
                )
                TssTrendChart(
                    data = uiState.tssTrendData,
                    colorByDiscipline = colorByDiscipline,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Discipline Split
                SectionHeader(
                    title = "Discipline Split",
                    subtitle = "Breakdown by sport"
                )
                DisciplineBreakdown(
                    stats = uiState.workoutTypeStats.values.toList(),
                    totalWorkouts = uiState.totalWorkouts
                )

                // 4. Volume History
                SectionHeader(
                    title = "Volume History",
                    subtitle = "Hours spent training"
                )
                VolumeChart(
                    data = uiState.volumeTrendData,
                    colorByDiscipline = colorByDiscipline,
                    targetAverageHours = uiState.volumeGoalAveragePerBucket,
                    currentAverageHours = uiState.volumeCurrentAveragePerBucket,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }

        if (showVolumeGoalDialog) {
            VolumeGoalSettingsDialog(
                value = annualGoalInput,
                onValueChange = { annualGoalInput = it },
                onDismiss = { showVolumeGoalDialog = false },
                onSave = {
                    val parsed = annualGoalInput.toFloatOrNull()?.takeIf { it > 0f }
                    viewModel.saveAnnualVolumeGoal(parsed)
                    showVolumeGoalDialog = false
                }
            )
        }
    }
}

@Composable
private fun VolumeGoalSettingsDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val isValid = value.isBlank() || value.toFloatOrNull()?.let { it > 0f } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volume Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Set a yearly hours target. The monthly and weekly pace is derived automatically.")
                OutlinedTextField(
                    value = value,
                    onValueChange = { next ->
                        if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d*$"))) {
                            onValueChange(next)
                        }
                    },
                    label = { Text("Annual hours") },
                    placeholder = { Text("e.g. 450") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !isValid
                )
                Text(
                    text = "Leave blank to remove the goal.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = isValid) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatHours(hours: Double): String {
    return if (hours >= 100 || hours % 1.0 == 0.0) {
        hours.toInt().toString()
    } else {
        String.format("%.1f", hours)
    }
}

@Preview(showBackground = true)
@Composable
fun StatsScreenPreview() {
    TriPathTheme {
        StatsScreen()
    }
}
