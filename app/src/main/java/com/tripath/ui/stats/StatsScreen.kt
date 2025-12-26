package com.tripath.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.stats.components.DisciplineBreakdown
import com.tripath.ui.stats.components.KeyMetricsGrid
import com.tripath.ui.stats.components.PeriodSelector
import com.tripath.ui.stats.components.TssTrendChart
import com.tripath.ui.stats.components.VolumeChart
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

                // 1. Performance Overview
                SectionHeader(title = "Performance")
                KeyMetricsGrid(
                    totalTSS = uiState.totalTSS,
                    totalWorkouts = uiState.totalWorkouts,
                    totalDistance = uiState.totalDistance,
                    totalHours = uiState.totalHours
                )

                // 2. Training Load (TSS)
                SectionHeader(
                    title = "Training Load",
                    subtitle = "TSS Trend & Fatigue"
                )
                TssTrendChart(
                    data = uiState.tssTrendData,
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
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatsScreenPreview() {
    TriPathTheme {
        StatsScreen()
    }
}
