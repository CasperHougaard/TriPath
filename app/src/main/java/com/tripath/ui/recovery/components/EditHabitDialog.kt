package com.tripath.ui.recovery.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.model.TaskTriggerType
import com.tripath.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION") // menuAnchor() is deprecated but replacement API not available
@Composable
fun EditHabitDialog(
    task: WellnessTaskDefinition,
    onDismiss: () -> Unit,
    onSave: (String, TaskTriggerType, Int?) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(task.title) }
    var selectedTriggerType by remember { mutableStateOf(task.type) }
    var thresholdMinutes by remember { mutableStateOf((task.triggerThreshold ?: 90).toFloat()) }
    var thresholdTss by remember { mutableStateOf((task.triggerThreshold ?: 100).toFloat()) }

    val hasThreshold = selectedTriggerType == TaskTriggerType.TRIGGER_LONG_DURATION ||
            selectedTriggerType == TaskTriggerType.TRIGGER_HIGH_TSS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Habit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Habit Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Trigger Type Selection
                Text(
                    text = "When should this habit appear?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Option A: Every Day
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTriggerType = TaskTriggerType.DAILY },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTriggerType == TaskTriggerType.DAILY,
                        onClick = { selectedTriggerType = TaskTriggerType.DAILY }
                    )
                    Text(
                        text = "Every Day",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
                }

                // Option B: On Strength Days
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTriggerType = TaskTriggerType.TRIGGER_STRENGTH },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTriggerType == TaskTriggerType.TRIGGER_STRENGTH,
                        onClick = { selectedTriggerType = TaskTriggerType.TRIGGER_STRENGTH }
                    )
                    Text(
                        text = "On Strength Days",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
                }

                // Option C: After Long Workouts
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTriggerType = TaskTriggerType.TRIGGER_LONG_DURATION },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTriggerType == TaskTriggerType.TRIGGER_LONG_DURATION,
                            onClick = { selectedTriggerType = TaskTriggerType.TRIGGER_LONG_DURATION }
                        )
                        Text(
                            text = "After Long Workouts",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = Spacing.xs)
                        )
                    }
                    if (selectedTriggerType == TaskTriggerType.TRIGGER_LONG_DURATION) {
                        Column(
                            modifier = Modifier.padding(start = Spacing.xl + Spacing.xs)
                        ) {
                            Text(
                                text = "Duration: ${thresholdMinutes.roundToInt()} minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Slider(
                                value = thresholdMinutes,
                                onValueChange = { thresholdMinutes = it },
                                valueRange = 30f..300f,
                                steps = 26,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Option D: High Load Days
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTriggerType = TaskTriggerType.TRIGGER_HIGH_TSS },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTriggerType == TaskTriggerType.TRIGGER_HIGH_TSS,
                            onClick = { selectedTriggerType = TaskTriggerType.TRIGGER_HIGH_TSS }
                        )
                        Text(
                            text = "High Load Days",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = Spacing.xs)
                        )
                    }
                    if (selectedTriggerType == TaskTriggerType.TRIGGER_HIGH_TSS) {
                        Column(
                            modifier = Modifier.padding(start = Spacing.xl + Spacing.xs)
                        ) {
                            Text(
                                text = "TSS Threshold: ${thresholdTss.roundToInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Slider(
                                value = thresholdTss,
                                onValueChange = { thresholdTss = it },
                                valueRange = 50f..200f,
                                steps = 14,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(
                    onClick = onDelete,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
                Button(
                    onClick = {
                        val threshold = when (selectedTriggerType) {
                            TaskTriggerType.TRIGGER_LONG_DURATION -> thresholdMinutes.roundToInt()
                            TaskTriggerType.TRIGGER_HIGH_TSS -> thresholdTss.roundToInt()
                            else -> null
                        }
                        onSave(title, selectedTriggerType, threshold)
                    },
                    enabled = title.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

