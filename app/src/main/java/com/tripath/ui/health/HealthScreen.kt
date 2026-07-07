package com.tripath.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.health.components.SummaryTile
import com.tripath.ui.theme.Spacing

private val BodyScanColor = Color(0xFF5C6BC0)
private val SleepColor = Color(0xFF7E57C2)
private val NutritionColor = Color(0xFF26A69A)

@Composable
fun HealthScreen(
    onNavigateToBodyScanDetail: () -> Unit = {},
    onNavigateToSleepDetail: () -> Unit = {},
    onNavigateToNutritionDetail: () -> Unit = {},
    viewModel: HealthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sleepLogs by viewModel.sleepLogs.collectAsStateWithLifecycle()
    val nutritionLogs by viewModel.nutritionLogs.collectAsStateWithLifecycle()

    // Auto-read data on open (syncs only if stale / permissions granted).
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

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
                    subtitle = "Body scan, sleep & nutrition"
                )

                BodyScanSummaryTile(state, onNavigateToBodyScanDetail)
                SleepSummaryTile(sleepLogs, onNavigateToSleepDetail)
                NutritionSummaryTile(nutritionLogs, onNavigateToNutritionDetail)

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun BodyScanSummaryTile(state: HealthUiState, onClick: () -> Unit) {
    SummaryTile(
        title = "Body Scan",
        value = state.latestWeight?.let { "%.1f kg".format(it) },
        subtitle = buildString {
            state.latestFatPercent?.let { append("%.1f%% fat".format(it)) }
            state.latestLeanMass?.let {
                if (isNotEmpty()) append(" · ")
                append("%.1f kg fat-free".format(it))
            }
        }.ifEmpty { null },
        icon = Icons.Default.MonitorWeight,
        accent = BodyScanColor,
        onClick = onClick,
        emptyMessage = "Connect a smart scale to see body composition"
    )
}

@Composable
private fun SleepSummaryTile(sleepLogs: List<SleepLog>, onClick: () -> Unit) {
    val latest = sleepLogs.firstOrNull()
    SummaryTile(
        title = "Sleep",
        value = latest?.let { formatDuration(it.durationMinutes) },
        subtitle = latest?.let { log ->
            buildString {
                log.sleepScore?.let { append("Score $it") }
                if (log.sleepScore != null) append(" · ")
                append(log.date.toString())
            }
        },
        icon = Icons.Default.Bedtime,
        accent = SleepColor,
        onClick = onClick,
        emptyMessage = "No sleep data yet — sync Health Connect"
    )
}

@Composable
private fun NutritionSummaryTile(nutritionLogs: List<NutritionLog>, onClick: () -> Unit) {
    val latest = nutritionLogs.firstOrNull()
    SummaryTile(
        title = "Nutrition",
        value = latest?.energyKcal?.let { "%,.0f kcal".format(it) },
        subtitle = latest?.let { log ->
            listOfNotNull(
                log.proteinG?.let { "P %.0fg".format(it) },
                if (log.creatineTaken) "Creatine ✓" else null
            ).joinToString(" · ").ifEmpty { log.date.toString() }
        },
        icon = Icons.Default.Restaurant,
        accent = NutritionColor,
        onClick = onClick,
        emptyMessage = "Tap to log calories, protein & creatine"
    )
}

internal fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
