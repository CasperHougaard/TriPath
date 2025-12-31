package com.tripath.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.IconSize
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor
import java.time.LocalDate

@Composable
fun WorkoutCard(
    workout: TrainingPlan,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isPlanned: Boolean = workout.date >= LocalDate.now()
) {
    val workoutColor = workout.type.toColor()
    val icon = getWorkoutIcon(workout.type)
    
    // For planned workouts, use outlined style; for completed, use solid style with sport color background
    val containerColor = if (isPlanned) {
        MaterialTheme.colorScheme.surface // Surface background for planned workouts
    } else {
        workoutColor // Solid sport color background for completed workouts
    }
    
    val contentAlpha = if (isPlanned) 0.8f else 1.0f
    val textColor = if (isPlanned) {
        workoutColor.copy(alpha = contentAlpha) // Sport color text for planned
    } else {
        Color.White.copy(alpha = contentAlpha) // White text for completed
    }
    
    val iconTint = if (isPlanned) {
        workoutColor.copy(alpha = contentAlpha) // Sport color icon for planned
    } else {
        Color.White.copy(alpha = contentAlpha) // White icon for completed
    }
    
    val secondaryTextColor = if (isPlanned) {
        workoutColor.copy(alpha = 0.7f * contentAlpha) // Sport color (lighter) for planned
    } else {
        Color.White.copy(alpha = 0.7f * contentAlpha) // White (lighter) for completed
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = if (isPlanned) {
            BorderStroke(
                width = 2.dp,
                color = workoutColor.copy(alpha = contentAlpha)
            )
        } else {
            null
        },
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
                tint = iconTint,
                modifier = Modifier.size(IconSize.large)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = workout.type.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )

                if (workout.type == WorkoutType.STRENGTH) {
                    workout.strengthFocus?.let { focus ->
                        workout.intensity?.let { intensity ->
                            Text(
                                text = "${formatStrengthFocus(focus)} • ${formatIntensity(intensity)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        }
                    }
                }

                workout.subType?.let { subType ->
                    Text(
                        text = subType,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor.copy(alpha = 0.85f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Text(
                        text = "${workout.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                    Text(
                        text = "${workout.plannedTSS} TSS",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
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
            // Completed workout (solid style)
            WorkoutCard(
                workout = TrainingPlan(
                    date = LocalDate.now().minusDays(1),
                    type = WorkoutType.STRENGTH,
                    durationMinutes = 45,
                    plannedTSS = 60,
                    strengthFocus = StrengthFocus.UPPER,
                    intensity = Intensity.HEAVY
                ),
                isPlanned = false
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            // Planned workout (outlined style)
            WorkoutCard(
                workout = TrainingPlan(
                    date = LocalDate.now().plusDays(1),
                    type = WorkoutType.RUN,
                    durationMinutes = 60,
                    plannedTSS = 80,
                    subType = "Tempo Run"
                ),
                isPlanned = true
            )
        }
    }
}

