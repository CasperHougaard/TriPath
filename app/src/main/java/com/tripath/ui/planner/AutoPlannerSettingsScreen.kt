package com.tripath.ui.planner

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.ui.coach.CoachViewModel
import com.tripath.ui.theme.Spacing
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlannerSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGoalEditor: () -> Unit,
    viewModel: AutoPlannerSettingsViewModel = hiltViewModel(),
    coachViewModel: CoachViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isGenerating by coachViewModel.isGenerating.collectAsStateWithLifecycle()
    val generationError by coachViewModel.generationError.collectAsStateWithLifecycle()
    val generationSuccess by coachViewModel.generationSuccess.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-planner Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                SettingsGroupHeader(
                    title = "General",
                    subtitle = "Run planner"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Smart Planning",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Enable plan generation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = Spacing.xs)
                                )
                            }
                            Switch(
                                checked = uiState.isAutoPlannerEnabled,
                                onCheckedChange = { viewModel.setAutoPlannerEnabled(enabled = it) }
                            )
                        }
                    }
                }

                SettingsGroupHeader(
                    title = "Running Goal",
                    subtitle = "Required for generation"
                )

                RunningGoalCard(
                    activeGoal = uiState.activeRunningGoal,
                    onCreateOrEdit = onNavigateToGoalEditor,
                    onClear = { viewModel.clearActiveRunningGoal() },
                    modifier = Modifier.fillMaxWidth()
                )

                SettingsGroupHeader(
                    title = "Generate Plan",
                    subtitle = "Plan length"
                )

                AutoPilotGenerationCard(
                    isSmartPlanningEnabled = uiState.isAutoPlannerEnabled,
                    hasActiveRunningGoal = uiState.activeRunningGoal != null,
                    isGenerating = isGenerating,
                    generationError = generationError,
                    generationSuccess = generationSuccess,
                    onGenerate = { months -> coachViewModel.generateSeasonPlan(months = months) },
                    onDismissError = { coachViewModel.clearGenerationError() },
                    onDismissSuccess = { coachViewModel.clearGenerationSuccess() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun AutoPilotGenerationCard(
    isSmartPlanningEnabled: Boolean,
    hasActiveRunningGoal: Boolean,
    isGenerating: Boolean,
    generationError: String?,
    generationSuccess: Int?,
    onGenerate: (Int) -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthOptions = listOf(1, 2, 3, 4, 5, 6)
    var selectedMonths by remember { mutableIntStateOf(3) }
    val canGenerate = !isGenerating && isSmartPlanningEnabled && hasActiveRunningGoal

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Generate Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = when {
                    !isSmartPlanningEnabled -> "Enable Smart Planning to generate a plan."
                    hasActiveRunningGoal -> "Generate a running plan from the saved goal."
                    else -> "A running goal is required before generation."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Column {
                Text(
                    text = "Plan Length",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    monthOptions.forEach { month ->
                        FilterChip(
                            selected = selectedMonths == month,
                            onClick = { selectedMonths = month },
                            enabled = !isGenerating,
                            label = { Text(if (month == 1) "1 mo" else "$month mo") },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
            }

            Button(
                onClick = { onGenerate(selectedMonths) },
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Generating...")
                } else {
                    Text(
                        when {
                            !isSmartPlanningEnabled -> "Turn On Smart Planning First"
                            !hasActiveRunningGoal -> "Create Running Goal First"
                            else -> "Generate ${selectedMonths}-Month Running Plan"
                        }
                    )
                }
            }

            generationError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        TextButton(
                            onClick = onDismissError,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            generationSuccess?.let { count ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Successfully generated $count training plans!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissSuccess) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningGoalCard(
    activeGoal: com.tripath.domain.running.RunningGoal?,
    onCreateOrEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Goal Setup",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (activeGoal == null) {
                Text(
                    text = "No running goal is saved yet. Set one up to unlock generation and give the planner a clear target.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Button(
                    onClick = onCreateOrEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Running Goal")
                }
            } else {
                Text(
                    text = when (activeGoal.type) {
                        com.tripath.domain.running.RunningGoalType.COMPLETE_DISTANCE -> "Complete Distance"
                        com.tripath.domain.running.RunningGoalType.CONSISTENCY -> "Run Consistently"
                        com.tripath.domain.running.RunningGoalType.ENDURANCE -> "Build Endurance"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            activeGoal.targetDistanceMeters?.let {
                                GoalMetricChip("Target ${(it / 1000.0)} km")
                            }
                            activeGoal.runsPerWeek?.let {
                                GoalMetricChip("$it runs/week")
                            }
                        }
                    }
                }
                activeGoal.targetDate?.let {
                    GoalDetailRow(label = "Target date", value = it.format(formatter))
                }
                if (!activeGoal.preferredDays.isNullOrEmpty()) {
                    GoalDetailRow(
                        label = "Preferred days",
                        value = activeGoal.preferredDays.joinToString { it.name.take(3) }
                    )
                }
                activeGoal.baselineLongestRunMeters?.let {
                    GoalDetailRow(label = "Current longest run", value = "${it / 1000.0} km")
                }
                activeGoal.baselineWeeklyRunMeters?.let {
                    GoalDetailRow(label = "Current weekly volume", value = "${it / 1000.0} km")
                }
                Text(
                    text = "This goal now drives the run planner. Edit it whenever your target changes or your current baseline shifts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(
                        onClick = onCreateOrEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit Goal")
                    }
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Goal")
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalMetricChip(
    text: String,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun GoalDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

