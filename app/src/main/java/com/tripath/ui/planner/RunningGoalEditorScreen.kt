package com.tripath.ui.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.domain.running.ProgressionSafety
import com.tripath.domain.running.RunningGoalType
import com.tripath.ui.coach.RunningGoalEditorState
import com.tripath.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningGoalEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: RunningGoalEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var state by remember(uiState.editorState) { mutableStateOf(uiState.editorState) }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Running Goal") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.save(state)
                            onNavigateBack()
                        },
                        enabled = state.isValid(),
                        modifier = Modifier.padding(end = Spacing.sm)
                    ) {
                        Text("Save")
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
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Spacer(modifier = Modifier.height(Spacing.xs))

            // ── Goal Type ──────────────────────────────────────────────────
            GoalTypeSection(
                selected = state.goalType,
                onSelected = { state = state.copy(goalType = it) }
            )

            HorizontalDivider()

            // ── Target ─────────────────────────────────────────────────────
            AnimatedVisibility(visible = state.goalType != RunningGoalType.CONSISTENCY) {
                TargetSection(
                    state = state,
                    onStateChange = { state = it },
                    onOpenDatePicker = { showDatePicker = true }
                )
            }

            // ── Weekly Structure ───────────────────────────────────────────
            WeeklyStructureSection(
                state = state,
                onStateChange = { state = it }
            )

            HorizontalDivider()

            // ── Current Baseline ───────────────────────────────────────────
            BaselineSection(
                state = state,
                suggestedLongestRunKm = uiState.suggestedLongestRunKm,
                suggestedWeeklyVolumeKm = uiState.suggestedWeeklyVolumeKm,
                onStateChange = { state = it }
            )

            HorizontalDivider()

            // ── Progression Safety ─────────────────────────────────────────
            ProgressionSafetySection(
                selected = state.progressionSafety,
                onSelected = { state = state.copy(progressionSafety = it) }
            )

            // ── Validation error ───────────────────────────────────────────
            if (!state.isValid() && state.goalType == RunningGoalType.COMPLETE_DISTANCE) {
                Text(
                    text = "A target distance and date are required for a Race or Event goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.targetDate
                ?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state = state.copy(
                        targetDate = datePickerState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                    )
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Goal Type Section
// ─────────────────────────────────────────────────────────────────────────────

private data class GoalTypeOption(
    val type: RunningGoalType,
    val title: String,
    val subtitle: String
)

private val goalTypeOptions = listOf(
    GoalTypeOption(
        RunningGoalType.ENDURANCE,
        "Ironman Journey",
        "Steady long-term build — protecting joints while growing aerobic capacity"
    ),
    GoalTypeOption(
        RunningGoalType.COMPLETE_DISTANCE,
        "Race or Event",
        "Train toward a specific distance by a target date"
    ),
    GoalTypeOption(
        RunningGoalType.CONSISTENCY,
        "Running Habit",
        "Build regular weekly sessions without a fixed distance target"
    )
)

@Composable
private fun GoalTypeSection(
    selected: RunningGoalType,
    onSelected: (RunningGoalType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Goal type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        goalTypeOptions.forEach { option ->
            val isSelected = selected == option.type
            OutlinedCard(
                onClick = { onSelected(option.type) },
                modifier = Modifier.fillMaxWidth(),
                border = CardDefaults.outlinedCardBorder().let { defaultBorder ->
                    if (isSelected) {
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.primary
                        )
                    } else defaultBorder
                },
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = option.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Target Section
// ─────────────────────────────────────────────────────────────────────────────

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private val distancePresets = listOf(5.0, 10.0, 21.1, 42.2)

@Composable
private fun TargetSection(
    state: RunningGoalEditorState,
    onStateChange: (RunningGoalEditorState) -> Unit,
    onOpenDatePicker: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (state.goalType == RunningGoalType.COMPLETE_DISTANCE) {
            // Distance presets
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                distancePresets.forEach { km ->
                    val label = if (km % 1.0 == 0.0) "${km.toInt()} km" else "$km km"
                    SuggestionChip(
                        onClick = {
                            onStateChange(state.copy(targetDistanceKm = if (km % 1.0 == 0.0) km.toInt().toString() else km.toString()))
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            OutlinedTextField(
                value = state.targetDistanceKm,
                onValueChange = { onStateChange(state.copy(targetDistanceKm = it)) },
                label = { Text("Target distance (km)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        // Date field (both COMPLETE_DISTANCE and ENDURANCE)
        val dateLabel = if (state.goalType == RunningGoalType.ENDURANCE) "Target horizon (optional)" else "Target date"
        OutlinedTextField(
            value = state.targetDate?.format(dateFormatter) ?: "",
            onValueChange = {},
            label = { Text(dateLabel) },
            placeholder = { Text("Tap to select") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = true,
            trailingIcon = {
                TextButton(onClick = onOpenDatePicker) { Text("Pick") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weekly Structure Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklyStructureSection(
    state: RunningGoalEditorState,
    onStateChange: (RunningGoalEditorState) -> Unit
) {
    val runsPerWeekInt = state.runsPerWeek.toIntOrNull() ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Weekly structure", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Runs per week stepper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Runs per week", style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                TextButton(
                    onClick = { if (runsPerWeekInt > 1) onStateChange(state.copy(runsPerWeek = (runsPerWeekInt - 1).toString())) },
                    enabled = runsPerWeekInt > 1
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(
                    text = if (state.runsPerWeek.isBlank()) "—" else state.runsPerWeek,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm)
                )
                TextButton(
                    onClick = { if (runsPerWeekInt < 7) onStateChange(state.copy(runsPerWeek = (runsPerWeekInt + 1).toString())) },
                    enabled = runsPerWeekInt < 7
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }

        // Preferred days strip
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Preferred running days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in state.preferredDays,
                        onClick = {
                            val newDays = if (day in state.preferredDays)
                                state.preferredDays - day
                            else
                                state.preferredDays + day
                            onStateChange(state.copy(preferredDays = newDays))
                        },
                        label = {
                            Text(
                                day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Baseline Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BaselineSection(
    state: RunningGoalEditorState,
    suggestedLongestRunKm: Float?,
    suggestedWeeklyVolumeKm: Float?,
    onStateChange: (RunningGoalEditorState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Current baseline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = state.baselineLongestRunKm,
            onValueChange = { onStateChange(state.copy(baselineLongestRunKm = it)) },
            label = { Text("Longest comfortable run (km)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = {
                Text(
                    "Your longest run distance in the past few months",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        )

        if (suggestedLongestRunKm != null && state.baselineLongestRunKm.isBlank()) {
            val displayKm = (suggestedLongestRunKm * 10).roundToInt() / 10f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detected from history: $displayKm km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = {
                    onStateChange(state.copy(baselineLongestRunKm = displayKm.toString()))
                }) { Text("Use") }
            }
        }

        OutlinedTextField(
            value = state.baselineWeeklyRunKm,
            onValueChange = { onStateChange(state.copy(baselineWeeklyRunKm = it)) },
            label = { Text("Current weekly volume (km, optional)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = {
                Text(
                    "Typical total running distance per week",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        )

        if (suggestedWeeklyVolumeKm != null && state.baselineWeeklyRunKm.isBlank()) {
            val displayKm = (suggestedWeeklyVolumeKm * 10).roundToInt() / 10f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Avg from last 4 weeks: $displayKm km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = {
                    onStateChange(state.copy(baselineWeeklyRunKm = displayKm.toString()))
                }) { Text("Use") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Progression Safety Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProgressionSafetySection(
    selected: ProgressionSafety,
    onSelected: (ProgressionSafety) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Progression safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Controls how fast your weekly distance grows. The 10% rule is widely cited in sports science to prevent overuse injuries — especially important for protecting fragile feet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            listOf(
                ProgressionSafety.CONSERVATIVE to "Conservative\n8%/week",
                ProgressionSafety.STANDARD to "Standard\n10%/week",
                ProgressionSafety.AGGRESSIVE to "Aggressive\n15%/week"
            ).forEach { (safety, label) ->
                FilterChip(
                    selected = selected == safety,
                    onClick = { onSelected(safety) },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
