package com.tripath.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import com.tripath.ui.components.LoadIndicator
import com.tripath.ui.components.WorkoutCard
import com.tripath.ui.theme.TriPathTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Weekly Load Progress
            LoadIndicator(
                plannedTSS = uiState.weeklyPlannedTSS,
                actualTSS = uiState.weeklyActualTSS,
                progress = uiState.weeklyLoadProgress,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Today's Focus Card
            TodaysFocusCard(
                todaysPlan = uiState.todaysPlan,
                isRestDay = uiState.isRestDay,
                restDayMessage = uiState.restDayMessage,
                isWorkoutCompleted = uiState.isWorkoutCompleted,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TodaysFocusCard(
    todaysPlan: com.tripath.data.local.database.entities.TrainingPlan?,
    isRestDay: Boolean,
    restDayMessage: String,
    isWorkoutCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isWorkoutCompleted) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isWorkoutCompleted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.height(24.dp)
                    )
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Text(
                text = "Today's Focus",
                style = MaterialTheme.typography.titleLarge
            )

            if (isRestDay) {
                Text(
                    text = restDayMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else if (todaysPlan != null) {
                if (todaysPlan.type == WorkoutType.STRENGTH) {
                    todaysPlan.strengthFocus?.let { focus ->
                        todaysPlan.intensity?.let { intensity ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        workout = todaysPlan,
                        modifier = Modifier.fillMaxWidth()
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
        DashboardScreen()
    }
}

