package com.tripath.ui.coach.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.tripath.domain.running.RunningGoalType
import com.tripath.ui.coach.RunningGoalEditorState
import com.tripath.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RunningGoalEditorDialog(
    initialState: RunningGoalEditorState,
    onDismiss: () -> Unit,
    onSave: (RunningGoalEditorState) -> Unit
) {
    var state by remember(initialState) { mutableStateOf(initialState) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Running Goal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                GoalTypeSection(
                    selected = state.goalType,
                    onSelected = { state = state.copy(goalType = it) }
                )

                if (state.goalType == RunningGoalType.COMPLETE_DISTANCE) {
                    OutlinedTextField(
                        value = state.targetDistanceKm,
                        onValueChange = { state = state.copy(targetDistanceKm = it) },
                        label = { Text("Target distance (km)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state.goalType == RunningGoalType.COMPLETE_DISTANCE) {
                            showDatePicker = true
                        }
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target date")
                    Text(
                        text = state.targetDate?.toString() ?: "Select",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = state.runsPerWeek,
                    onValueChange = { state = state.copy(runsPerWeek = it) },
                    label = { Text("Runs per week") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Text("Preferred running days", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in state.preferredDays,
                            onClick = {
                                state = state.copy(
                                    preferredDays = if (day in state.preferredDays) {
                                        state.preferredDays - day
                                    } else {
                                        state.preferredDays + day
                                    }
                                )
                            },
                            label = { Text(day.name.take(3)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = state.baselineLongestRunKm,
                    onValueChange = { state = state.copy(baselineLongestRunKm = it) },
                    label = { Text("Current longest comfortable run (km)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                OutlinedTextField(
                    value = state.baselineWeeklyRunKm,
                    onValueChange = { state = state.copy(baselineWeeklyRunKm = it) },
                    label = { Text("Current weekly running volume (km, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                if (state.goalType == RunningGoalType.COMPLETE_DISTANCE && !state.isValid()) {
                    Text(
                        text = "Distance and target date are required for Complete Distance goals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(state) },
                enabled = state.isValid()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

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
                TextButton(
                    onClick = {
                        state = state.copy(
                            targetDate = datePickerState.selectedDateMillis?.let { millis ->
                                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                        )
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalTypeSection(
    selected: RunningGoalType,
    onSelected: (RunningGoalType) -> Unit
) {
    val options = listOf(
        RunningGoalType.COMPLETE_DISTANCE to "Complete Distance",
        RunningGoalType.CONSISTENCY to "Run Consistently",
        RunningGoalType.ENDURANCE to "Build Endurance"
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Goal type", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            options.forEach { (type, label) ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelected(type) },
                    label = { Text(label) }
                )
            }
        }
    }
}