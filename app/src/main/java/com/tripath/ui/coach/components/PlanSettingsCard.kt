package com.tripath.ui.coach.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.data.model.TrainingBalance
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun PlanSettingsCard(
    userProfile: UserProfile,
    onUpdateSettings: (Map<DayOfWeek, List<WorkoutType>>, DayOfWeek, Int, TrainingBalance) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Plan Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure your weekly schedule",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Edit")
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(Spacing.md))
                
                // Strength Days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Strength Sessions / Week")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { 
                                val current = userProfile.strengthDays ?: 2
                                if (current > 0) onUpdateSettings(
                                    userProfile.weeklyAvailability ?: emptyMap(),
                                    userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                    current - 1,
                                    userProfile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
                                )
                            }
                        ) { Text("-") }
                        
                        Text("${userProfile.strengthDays ?: 2}")
                        
                        TextButton(
                            onClick = { 
                                val current = userProfile.strengthDays ?: 2
                                if (current < 7) onUpdateSettings(
                                    userProfile.weeklyAvailability ?: emptyMap(),
                                    userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                    current + 1,
                                    userProfile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
                                )
                            }
                        ) { Text("+") }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Long Day Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Long Training Day")
                    // Simple cycler for now
                    TextButton(
                        onClick = {
                            val current = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY
                            val next = DayOfWeek.values()[(current.ordinal + 1) % 7]
                            onUpdateSettings(
                                userProfile.weeklyAvailability ?: emptyMap(),
                                next,
                                userProfile.strengthDays ?: 2,
                                userProfile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
                            )
                        }
                    ) {
                        Text(userProfile.longTrainingDay?.name?.take(3) ?: "SUN")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Text("Training Balance", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(Spacing.xs))
                
                // Balance Presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BalancePresetChip("Ironman", TrainingBalance.IRONMAN_BASE, userProfile, onUpdateSettings)
                    BalancePresetChip("Balanced", TrainingBalance.BALANCED, userProfile, onUpdateSettings)
                    BalancePresetChip("Run Focus", TrainingBalance.RUN_FOCUS, userProfile, onUpdateSettings)
                }
                
                Spacer(modifier = Modifier.height(Spacing.sm))
                
                val balance = userProfile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
                BalanceSlider("Bike", balance.bikePercent, { newVal ->
                    onUpdateSettings(
                        userProfile.weeklyAvailability ?: emptyMap(),
                        userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                        userProfile.strengthDays ?: 2,
                        balance.copy(bikePercent = newVal)
                    )
                })
                BalanceSlider("Run", balance.runPercent, { newVal ->
                    onUpdateSettings(
                        userProfile.weeklyAvailability ?: emptyMap(),
                        userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                        userProfile.strengthDays ?: 2,
                        balance.copy(runPercent = newVal)
                    )
                })
                BalanceSlider("Swim", balance.swimPercent, { newVal ->
                    onUpdateSettings(
                        userProfile.weeklyAvailability ?: emptyMap(),
                        userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                        userProfile.strengthDays ?: 2,
                        balance.copy(swimPercent = newVal)
                    )
                })

                Spacer(modifier = Modifier.height(Spacing.sm))
                Text("Weekly Availability", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(Spacing.xs))

                // Day Availability
                DayOfWeek.values().forEach { day ->
                    val availability = userProfile.weeklyAvailability ?: getDefaultAvailability()
                    val allowedTypes = availability[day] ?: emptyList()
                    
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(day.name.take(3), style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(WorkoutType.SWIM, WorkoutType.BIKE, WorkoutType.RUN, WorkoutType.STRENGTH).forEach { type ->
                                val isSelected = allowedTypes.contains(type)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val currentList = allowedTypes.toMutableList()
                                        if (isSelected) currentList.remove(type) else currentList.add(type)
                                        
                                        val newMap = availability.toMutableMap()
                                        newMap[day] = currentList
                                        
                                        onUpdateSettings(
                                            newMap,
                                            userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                            userProfile.strengthDays ?: 2,
                                            userProfile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
                                        )
                                    },
                                    label = { Text(type.name.take(1)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Temporary local FilterChip if Material3 FilterChip is not available or too verbose
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label
    )
}

@Composable
private fun BalancePresetChip(
    label: String,
    target: TrainingBalance,
    userProfile: UserProfile,
    onUpdate: (Map<DayOfWeek, List<WorkoutType>>, DayOfWeek, Int, TrainingBalance) -> Unit
) {
    AssistChip(
        onClick = {
            onUpdate(
                userProfile.weeklyAvailability ?: emptyMap(),
                userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                userProfile.strengthDays ?: 2,
                target
            )
        },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (userProfile.trainingBalance == target) 
                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun BalanceSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: $value%", 
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun getDefaultAvailability(): Map<DayOfWeek, List<WorkoutType>> {
    return DayOfWeek.values().associateWith { day ->
        when (day) {
            DayOfWeek.MONDAY -> listOf(WorkoutType.SWIM, WorkoutType.STRENGTH)
            DayOfWeek.TUESDAY -> listOf(WorkoutType.BIKE, WorkoutType.RUN)
            DayOfWeek.WEDNESDAY -> listOf(WorkoutType.RUN, WorkoutType.STRENGTH)
            DayOfWeek.THURSDAY -> listOf(WorkoutType.BIKE, WorkoutType.SWIM)
            DayOfWeek.FRIDAY -> listOf(WorkoutType.SWIM, WorkoutType.RUN)
            DayOfWeek.SATURDAY -> listOf(WorkoutType.BIKE, WorkoutType.RUN)
            DayOfWeek.SUNDAY -> listOf(WorkoutType.BIKE, WorkoutType.RUN)
        }
    }
}

