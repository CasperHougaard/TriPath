package com.tripath.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.TriPathTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutBottomSheet(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (TrainingPlan) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf<WorkoutType?>(null) }
    var selectedFocus by remember { mutableStateOf<StrengthFocus?>(null) }
    var selectedIntensity by remember { mutableStateOf<Intensity?>(null) }
    var duration by remember { mutableIntStateOf(30) }
    var plannedTSS by remember { mutableIntStateOf(50) }
    var subType by remember { mutableStateOf("") }

    // Update TSS when intensity changes for STRENGTH
    if (selectedType == WorkoutType.STRENGTH && selectedIntensity != null) {
        plannedTSS = when (selectedIntensity) {
            Intensity.LIGHT -> 30
            Intensity.HEAVY -> 60
            null -> 50
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add Workout",
                style = MaterialTheme.typography.titleLarge
            )

            // Workout Type Selection
            Text(
                text = "Workout Type",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkoutType.values().forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            if (type != WorkoutType.STRENGTH) {
                                selectedFocus = null
                                selectedIntensity = null
                            }
                        },
                        label = { Text(type.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Strength-specific fields
            if (selectedType == WorkoutType.STRENGTH) {
                // Strength Focus
                Text(
                    text = "Focus",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrengthFocus.values().forEach { focus ->
                        FilterChip(
                            selected = selectedFocus == focus,
                            onClick = { selectedFocus = focus },
                            label = {
                                Text(
                                    when (focus) {
                                        StrengthFocus.FULL_BODY -> "Full"
                                        StrengthFocus.UPPER -> "Upper"
                                        StrengthFocus.LOWER -> "Lower"
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Intensity
                Text(
                    text = "Intensity",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Intensity.values().forEach { intensity ->
                        FilterChip(
                            selected = selectedIntensity == intensity,
                            onClick = { selectedIntensity = intensity },
                            label = { Text(intensity.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Duration Slider
            Text(
                text = "Duration: $duration minutes",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = duration.toFloat(),
                onValueChange = { duration = it.toInt() },
                valueRange = 5f..180f,
                steps = 34, // 5-minute steps: (180-5)/5 = 35 steps, but steps is exclusive
                modifier = Modifier.fillMaxWidth()
            )

            // Planned TSS
            Text(
                text = "Planned TSS: $plannedTSS",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = plannedTSS.toFloat(),
                onValueChange = { plannedTSS = it.toInt() },
                valueRange = 10f..200f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )

            // Optional Sub-type
            TextField(
                value = subType,
                onValueChange = { subType = it },
                label = { Text("Sub-type (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Save Button
            val canSave = selectedType != null &&
                    (selectedType != WorkoutType.STRENGTH || (selectedFocus != null && selectedIntensity != null))

            Button(
                onClick = {
                    if (canSave && selectedType != null) {
                        val workout = TrainingPlan(
                            date = selectedDate,
                            type = selectedType!!,
                            durationMinutes = duration,
                            plannedTSS = plannedTSS,
                            subType = subType.takeIf { it.isNotBlank() },
                            strengthFocus = selectedFocus,
                            intensity = selectedIntensity
                        )
                        onSave(workout)
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddWorkoutBottomSheetPreview() {
    TriPathTheme {
        AddWorkoutBottomSheet(
            selectedDate = LocalDate.now(),
            onDismiss = {},
            onSave = {}
        )
    }
}

