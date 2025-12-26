package com.tripath.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.components.WorkoutCard
import com.tripath.ui.dashboard.components.ActivitySummaryRow
import com.tripath.ui.dashboard.components.WeeklyCalendarStrip
import com.tripath.ui.navigation.Screen
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
        // FAB removed as requested
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Header Section
            DashboardHeader(
                greeting = uiState.greeting,
                syncStatus = uiState.syncStatus,
                onSyncClick = { viewModel.syncData() }
            )

            // Weekly Calendar Strip
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                WeeklyCalendarStrip(
                    weekDayStatuses = uiState.weekDayStatuses,
                    onDateSelected = { date -> viewModel.selectDate(date) }
                )
            }

            // Day Detail Section (Unified Plan + Logs)
            val isToday = uiState.selectedDate == LocalDate.now()
            val sectionTitle = if (isToday) "Today's Focus" else uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEEE"))

            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SectionHeader(
                    title = sectionTitle,
                    subtitle = if (uiState.isWorkoutCompleted) "Completed ✓" else null
                )

                // 1. The Plan (if any) or Rest Day status
                DayDetailCard(
                    plan = uiState.selectedDatePlan,
                    isRestDay = uiState.isRestDay,
                    restDayMessage = uiState.restDayMessage,
                    isWorkoutCompleted = uiState.isWorkoutCompleted,
                    onWorkoutClick = { workoutId, isPlanned ->
                        navController.navigate(Screen.WorkoutDetail.createRoute(workoutId, isPlanned))
                    }
                )

                // 2. Completed Logs (if any)
                if (uiState.selectedDateLogs.isNotEmpty()) {
                    Text(
                        text = "Completed Activities",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    
                    uiState.selectedDateLogs.forEach { log ->
                        ActivitySummaryRow(
                            workout = log,
                            onClick = {
                                navController.navigate(Screen.WorkoutDetail.createRoute(log.connectId, false))
                            }
                        )
                    }
                }
            }

            // Bottom Spacer
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
fun DashboardHeader(
    greeting: String,
    syncStatus: SyncStatus,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        IconButton(onClick = onSyncClick) {
            when (syncStatus) {
                SyncStatus.SYNCING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                SyncStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Synced",
                        tint = Color(0xFF4CAF50) // Green
                    )
                }
                SyncStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Sync Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Now",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun DayDetailCard(
    plan: TrainingPlan?,
    isRestDay: Boolean,
    restDayMessage: String,
    isWorkoutCompleted: Boolean,
    onWorkoutClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWorkoutCompleted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (isRestDay) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Recovery Day",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = restDayMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else if (plan != null) {
                if (plan.type == WorkoutType.STRENGTH) {
                    plan.strengthFocus?.let { focus ->
                        plan.intensity?.let { intensity ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                Text(
                                    text = "Focus: ${formatStrengthFocus(focus)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Intensity: ${formatIntensity(intensity)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                } else {
                    WorkoutCard(
                        workout = plan,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onWorkoutClick(plan.id, true)
                        }
                    )
                }
            }
        }
    }
}

private fun formatStrengthFocus(focus: StrengthFocus): String {
    return when (focus) {
        StrengthFocus.FULL_BODY -> "Full Body"
        StrengthFocus.UPPER -> "Upper"
        StrengthFocus.LOWER -> "Lower"
    }
}

private fun formatIntensity(intensity: Intensity): String {
    return when (intensity) {
        Intensity.LIGHT -> "Light"
        Intensity.HEAVY -> "Heavy"
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    TriPathTheme {
        DashboardScreen(navController = rememberNavController())
    }
}
