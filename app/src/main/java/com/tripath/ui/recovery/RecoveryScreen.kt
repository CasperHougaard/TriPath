package com.tripath.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.TaskTriggerType
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.navigation.Screen
import com.tripath.ui.recovery.components.AddHabitDialog
import com.tripath.ui.recovery.components.DaySelector
import com.tripath.ui.recovery.components.EditHabitDialog
import com.tripath.ui.theme.IconSize
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import kotlin.math.roundToInt
import java.time.LocalDate

@Composable
fun RecoveryScreen(
    navController: NavController? = null,
    viewModel: RecoveryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<com.tripath.data.local.database.entities.WellnessTaskDefinition?>(null) }
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Header
            SectionHeader(
                title = "Recovery Hub",
                subtitle = "Track wellness and fueling",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        // Reset button
                        IconButton(
                            onClick = { showResetDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Reset day",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        // History button
                        navController?.let {
                            IconButton(
                                onClick = { navController.navigate(Screen.RecoveryHistory.route) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = "View history",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            )

        // Day Selector
        DaySelector(
            selectedDate = selectedDate,
            today = today,
            onPreviousDay = { viewModel.previousDay() },
            onNextDay = { viewModel.nextDay() },
            onJumpToToday = { viewModel.jumpToToday() },
            modifier = Modifier.fillMaxWidth()
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
                currentWeight = uiState.currentLog?.morningWeight ?: uiState.suggestedWeight,
                onWeightChange = { weight ->
                    viewModel.updateWeight(weight)
                }
            )

            // Section 3: Protocol (Tasks)
            val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
            ProtocolSection(
                allTasks = allTasks,
                activeTaskIds = uiState.activeTasks.map { it.task.id }.toSet(),
                completedTaskIds = uiState.currentLog?.completedTaskIds.orEmpty().toSet(),
                onTaskToggle = { taskId, isChecked ->
                    viewModel.toggleTask(taskId, isChecked)
                },
                onAddHabit = { showAddHabitDialog = true },
                onEditHabit = { task ->
                    taskToEdit = task
                }
            )

            // Log Day Button
            Button(
                onClick = {
                    viewModel.logDay()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Recovery logged for ${selectedDate.format(dateFormatter)}!")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.medium)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "Log Recovery Status",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
        }

        // Reset confirmation dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Day") },
                text = {
                    Text("Reset all data for ${selectedDate.format(dateFormatter)}? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetDay()
                            showResetDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Day reset")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Add Habit Dialog
        if (showAddHabitDialog) {
            AddHabitDialog(
                onDismiss = { showAddHabitDialog = false },
                onConfirm = { title, type, threshold ->
                    viewModel.addNewHabit(title, type, threshold)
                    showAddHabitDialog = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Habit added: $title")
                    }
                }
            )
        }

        // Edit Habit Dialog
        taskToEdit?.let { task ->
            EditHabitDialog(
                task = task,
                onDismiss = { taskToEdit = null },
                onSave = { title, type, threshold ->
                    viewModel.updateHabit(task.id, title, type, threshold)
                    taskToEdit = null
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Habit updated: $title")
                    }
                },
                onDelete = {
                    viewModel.deleteHabit(task.id)
                    taskToEdit = null
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Habit deleted")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "Soreness: ${currentLog?.sorenessIndex ?: "Not set"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text("Din subjektive oplevelse af muskelømhed. 1 = ingen ømhed, 10 = ekstrem ømhed")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Soreness info",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "Mood: ${currentLog?.moodIndex ?: "Not set"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text("Dit generelle humør og energiniveau. 1 = meget lavt, 10 = meget højt")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Mood info",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
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
            AllergySeverityChipsRow(
                selectedSeverity = currentLog?.allergySeverity,
                onSeveritySelected = { severity ->
                    // Convert NONE to null for storage
                    onAllergyChange(if (severity == AllergySeverity.NONE) null else severity)
                }
            )

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
private fun AllergySeverityChipsRow(
    selectedSeverity: AllergySeverity?,
    onSeveritySelected: (AllergySeverity) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use uniform text style (labelSmall) that fits all texts
    val uniformTextStyle = MaterialTheme.typography.labelSmall.copy(
        textAlign = TextAlign.Center
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AllergySeverity.values().forEach { severity ->
            val isSelected = selectedSeverity == severity || 
                            (selectedSeverity == null && severity == AllergySeverity.NONE)
            
            FilterChip(
                selected = isSelected,
                onClick = { onSeveritySelected(severity) },
                label = {
                    Text(
                        text = severity.name,
                        style = uniformTextStyle,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                },
                modifier = Modifier.weight(1f)
            )
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
    allTasks: List<WellnessTaskDefinition>,
    activeTaskIds: Set<Long>,
    completedTaskIds: Set<Long>,
    onTaskToggle: (Long, Boolean) -> Unit,
    onAddHabit: () -> Unit,
    onEditHabit: (WellnessTaskDefinition) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recovery Protocol",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddHabit) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add habit"
                    )
                }
            }

            if (allTasks.isEmpty()) {
                Text(
                    text = "No habits defined. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                allTasks.forEach { task ->
                    val isActive = activeTaskIds.contains(task.id)
                    val isCompleted = completedTaskIds.contains(task.id)
                    
                    HabitRow(
                        task = task,
                        isActive = isActive,
                        isCompleted = isCompleted,
                        onToggle = { isChecked ->
                            onTaskToggle(task.id, isChecked)
                        },
                        onClick = {
                            onEditHabit(task)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitRow(
    task: WellnessTaskDefinition,
    isActive: Boolean,
    isCompleted: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTriggered = task.type != TaskTriggerType.DAILY
    val triggerIcon = if (isTriggered) Icons.Default.CheckCircle else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Checkbox(
            checked = isCompleted && isActive,
            onCheckedChange = { onToggle(it) },
            enabled = isActive
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isTriggered) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (triggerIcon != null && isActive) {
                    androidx.compose.material3.Icon(
                        imageVector = triggerIcon,
                        contentDescription = "Triggered",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            task.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (!isActive && isTriggered) {
                Text(
                    text = "Not active today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
