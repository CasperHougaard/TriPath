package com.tripath.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.IconSize
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor

@Composable
fun WorkoutCard(
    workout: TrainingPlan,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val workoutColor = workout.type.toColor()
    val icon = getWorkoutIcon(workout.type)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = workout.type.name,
                tint = workoutColor,
                modifier = Modifier.size(IconSize.large)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = workout.type.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = workoutColor
                )

                if (workout.type == WorkoutType.STRENGTH) {
                    workout.strengthFocus?.let { focus ->
                        workout.intensity?.let { intensity ->
                            Text(
                                text = "${formatStrengthFocus(focus)} • ${formatIntensity(intensity)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                workout.subType?.let { subType ->
                    Text(
                        text = subType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Text(
                        text = "${workout.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${workout.plannedTSS} TSS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun getWorkoutIcon(type: WorkoutType): ImageVector {
    return when (type) {
        WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
        WorkoutType.SWIM -> Icons.Default.Pool
        WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
        WorkoutType.OTHER -> Icons.AutoMirrored.Filled.DirectionsWalk
    }
}

private fun formatStrengthFocus(focus: StrengthFocus): String {
    return when (focus) {
        StrengthFocus.FULL_BODY -> "Full Body"
        StrengthFocus.UPPER -> "Upper"
        StrengthFocus.LOWER -> "Lower"
        StrengthFocus.HEAVY -> "Heavy Strength"
        StrengthFocus.STABILITY -> "Stability & Core"
    }
}

private fun formatIntensity(intensity: Intensity): String {
    return when (intensity) {
        Intensity.LIGHT, Intensity.LOW -> "Light"
        Intensity.HEAVY, Intensity.HIGH -> "Heavy"
        Intensity.MODERATE -> "Moderate"
    }
}

@Preview(showBackground = true)
@Composable
fun WorkoutCardPreview() {
    TriPathTheme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            WorkoutCard(
                workout = TrainingPlan(
                    date = java.time.LocalDate.now(),
                    type = WorkoutType.STRENGTH,
                    durationMinutes = 45,
                    plannedTSS = 60,
                    strengthFocus = StrengthFocus.UPPER,
                    intensity = Intensity.HEAVY
                )
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            WorkoutCard(
                workout = TrainingPlan(
                    date = java.time.LocalDate.now(),
                    type = WorkoutType.RUN,
                    durationMinutes = 60,
                    plannedTSS = 80,
                    subType = "Tempo Run"
                )
            )
        }
    }
}

