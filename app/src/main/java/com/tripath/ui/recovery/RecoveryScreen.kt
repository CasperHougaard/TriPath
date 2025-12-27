package com.tripath.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.TaskTriggerType
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import kotlin.math.roundToInt

@Composable
fun RecoveryScreen(
    viewModel: RecoveryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Header
        SectionHeader(
            title = "Recovery Hub",
            subtitle = "Track wellness and fueling"
        )

        if (uiState.isLoading) {
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            // Section 1: Status Card
            StatusCard(
                currentLog = uiState.currentLog,
                coachAdvice = uiState.coachAdvice,
                onSorenessChange = { value ->
                    viewModel.updateSubjectiveMetrics(
                        soreness = value,
                        mood = uiState.currentLog?.moodIndex,
                        allergy = uiState.currentLog?.allergySeverity
                    )
                },
                onMoodChange = { value ->
                    viewModel.updateSubjectiveMetrics(
                        soreness = uiState.currentLog?.sorenessIndex,
                        mood = value,
                        allergy = uiState.currentLog?.allergySeverity
                    )
                },
                onAllergyChange = { severity ->
                    viewModel.updateSubjectiveMetrics(
                        soreness = uiState.currentLog?.sorenessIndex,
                        mood = uiState.currentLog?.moodIndex,
                        allergy = severity
                    )
                }
            )

            // Section 2: Fueling
            FuelingSection(
                nutritionTargets = uiState.nutritionTargets,
                currentWeight = uiState.currentLog?.morningWeight,
                onWeightChange = { weight ->
                    viewModel.updateWeight(weight)
                }
            )

            // Section 3: Protocol (Tasks)
            ProtocolSection(
                activeTasks = uiState.activeTasks,
                onTaskToggle = { taskId, isChecked ->
                    viewModel.toggleTask(taskId, isChecked)
                }
            )

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun StatusCard(
    currentLog: com.tripath.data.local.database.entities.DailyWellnessLog?,
    coachAdvice: String,
    onSorenessChange: (Int) -> Unit,
    onMoodChange: (Int) -> Unit,
    onAllergyChange: (AllergySeverity?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "How are you feeling?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Soreness Slider
            Column {
                Text(
                    text = "Soreness: ${currentLog?.sorenessIndex ?: "Not set"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = (currentLog?.sorenessIndex ?: 5).toFloat(),
                    onValueChange = { onSorenessChange(it.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1", style = MaterialTheme.typography.labelSmall)
                    Text("10", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Mood Slider
            Column {
                Text(
                    text = "Mood: ${currentLog?.moodIndex ?: "Not set"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = (currentLog?.moodIndex ?: 5).toFloat(),
                    onValueChange = { onMoodChange(it.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1", style = MaterialTheme.typography.labelSmall)
                    Text("10", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Allergy Severity Chips
            Text(
                text = "Allergy Severity",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Add "None" option to clear selection
                FilterChip(
                    selected = currentLog?.allergySeverity == null,
                    onClick = { onAllergyChange(null) },
                    label = { Text("None") },
                    modifier = Modifier.weight(1f)
                )
                AllergySeverity.values().forEach { severity ->
                    FilterChip(
                        selected = currentLog?.allergySeverity == severity,
                        onClick = { onAllergyChange(severity) },
                        label = { Text(severity.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Coach Advice
            if (coachAdvice.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = coachAdvice,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.md),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelingSection(
    nutritionTargets: com.tripath.domain.NutritionTargets?,
    currentWeight: Double?,
    onWeightChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Fueling",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Weight Input
            Column {
                Text(
                    text = "Morning Weight (kg): ${currentWeight?.let { String.format("%.1f", it) } ?: "Not set"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = (currentWeight ?: 70.0).toFloat(),
                    onValueChange = { onWeightChange(it.toDouble()) },
                    valueRange = 40f..150f,
                    steps = 219, // 0.5kg steps: (150-40)/0.5 = 220 steps
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Nutrition Targets
            if (nutritionTargets != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NutritionTargetItem("Protein", "${nutritionTargets.proteinGrams.roundToInt()}g")
                    NutritionTargetItem("Carbs", "${nutritionTargets.carbGrams.roundToInt()}g")
                    NutritionTargetItem("Fat", "${nutritionTargets.fatGrams.roundToInt()}g")
                }
            } else {
                Text(
                    text = "Set morning weight to see nutrition targets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun NutritionTargetItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProtocolSection(
    activeTasks: List<TaskItem>,
    onTaskToggle: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Recovery Protocol",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (activeTasks.isEmpty()) {
                Text(
                    text = "No active tasks today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                activeTasks.forEach { taskItem ->
                    TaskRow(
                        taskItem = taskItem,
                        onToggle = { isChecked ->
                            onTaskToggle(taskItem.task.id, isChecked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    taskItem: TaskItem,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTriggered = taskItem.task.type != TaskTriggerType.DAILY
    val triggerIcon = if (isTriggered) Icons.Default.CheckCircle else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Checkbox(
            checked = taskItem.isCompleted,
            onCheckedChange = onToggle
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = taskItem.task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isTriggered) FontWeight.SemiBold else FontWeight.Normal
                )
                if (triggerIcon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = triggerIcon,
                        contentDescription = "Triggered",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
                }
            }
            taskItem.task.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveryScreenPreview() {
    TriPathTheme {
        RecoveryScreen()
    }
}
