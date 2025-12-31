package com.tripath.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Slider
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
import com.tripath.data.model.TrainingBalance
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.coach.components.WeeklyScheduleSection
import com.tripath.ui.theme.Spacing
import java.time.DayOfWeek
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlanningSettingsViewModel = hiltViewModel(),
    coachViewModel: CoachViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coachUiState by coachViewModel.uiState.collectAsStateWithLifecycle()
    val isEnabled = uiState.isSmartPlanningEnabled
    val isGenerating by coachViewModel.isGenerating.collectAsStateWithLifecycle()
    val generationError by coachViewModel.generationError.collectAsStateWithLifecycle()
    val generationSuccess by coachViewModel.generationSuccess.collectAsStateWithLifecycle()
    val userProfile = coachUiState.userProfile

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach Settings") },
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
                // Section 1: Master Control
                SectionHeader(
                    title = "Master Control",
                    subtitle = "Enable or disable smart planning features"
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
                                    text = "Master control for AI-powered training plan generation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = Spacing.xs)
                                )
                            }
                            Switch(
                                checked = uiState.isSmartPlanningEnabled,
                                onCheckedChange = { viewModel.setSmartPlanning(enabled = it) }
                            )
                        }
                    }
                }

                // Section 2: Injury Prevention
                SectionHeader(
                    title = "Injury Prevention",
                    subtitle = "The Rules"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        // Allow Consecutive Runs Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow Consecutive Runs",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "High impact risk if enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (isEnabled) 0.7f else 0.4f
                                    )
                                )
                            }
                            Switch(
                                checked = uiState.runConsecutiveAllowed,
                                onCheckedChange = { viewModel.setRunConsecutiveAllowed(allowed = it) },
                                enabled = isEnabled
                            )
                        }

                        // Monitor Mechanical Load Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Monitor Mechanical Load",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Track structural stress score",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (isEnabled) 0.7f else 0.4f
                                    )
                                )
                            }
                            Switch(
                                checked = uiState.mechanicalLoadMonitoring,
                                onCheckedChange = { viewModel.setMechanicalLoadMonitoring(enabled = it) },
                                enabled = isEnabled
                            )
                        }

                        // Max Ramp Rate Slider
                        Column {
                            Text(
                                text = "Max Ramp Rate: ${String.format("%.1f", uiState.rampRateLimit)}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Slider(
                                value = uiState.rampRateLimit,
                                onValueChange = { viewModel.setRampRateLimit(it) },
                                valueRange = 3.0f..8.0f,
                                steps = 9, // 0.5 steps: (8.0 - 3.0) / 0.5 = 10, so steps = 9
                                enabled = isEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "3.0%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    "8.0%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                // Section 3: Schedule Constraints
                SectionHeader(
                    title = "Schedule Constraints",
                    subtitle = "Training schedule configuration"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        // Strength Recovery Hours Slider
                        Column {
                            Text(
                                text = "Strength Recovery: ${uiState.strengthSpacingHours} hours",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Slider(
                                value = uiState.strengthSpacingHours.toFloat(),
                                onValueChange = { value ->
                                    // Snap to nearest valid value: 24, 48, or 72
                                    val snapped = when {
                                        value < 36 -> 24
                                        value < 60 -> 48
                                        else -> 72
                                    }
                                    viewModel.setStrengthSpacingHours(snapped)
                                },
                                valueRange = 24f..72f,
                                steps = 1, // This creates 3 stops: 24, 48, 72
                                enabled = isEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "24h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    "72h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }

                        // Ignore Commute Load Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ignore Commute Load",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Exclude low intensity commutes from recovery rules",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (isEnabled) 0.7f else 0.4f
                                    )
                                )
                            }
                            Switch(
                                checked = uiState.allowCommuteExemption,
                                onCheckedChange = { viewModel.setAllowCommuteExemption(allowed = it) },
                                enabled = isEnabled
                            )
                        }
                    }
                }

                // Section 4: Training Balance
                SectionHeader(
                    title = "Training Balance",
                    subtitle = "Distribution of training stress across disciplines"
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
                        if (userProfile == null) {
                            Text(
                                text = "Please complete your user profile first",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        } else {
                            val balance = userProfile.trainingBalance ?: TrainingBalance.IRONMAN_BASE
                            
                            // Balance Presets
                            Text(
                                text = "Presets",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TrainingBalancePresetChip(
                                    label = "Ironman",
                                    target = TrainingBalance.IRONMAN_BASE,
                                    currentBalance = balance,
                                    enabled = isEnabled,
                                    onClick = {
                                        coachViewModel.updateAvailability(
                                            weeklyAvailability = userProfile.weeklyAvailability ?: emptyMap(),
                                            longTrainingDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                            strengthDays = userProfile.strengthDays ?: 2,
                                            trainingBalance = TrainingBalance.IRONMAN_BASE
                                        )
                                    }
                                )
                                TrainingBalancePresetChip(
                                    label = "Balanced",
                                    target = TrainingBalance.BALANCED,
                                    currentBalance = balance,
                                    enabled = isEnabled,
                                    onClick = {
                                        coachViewModel.updateAvailability(
                                            weeklyAvailability = userProfile.weeklyAvailability ?: emptyMap(),
                                            longTrainingDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                            strengthDays = userProfile.strengthDays ?: 2,
                                            trainingBalance = TrainingBalance.BALANCED
                                        )
                                    }
                                )
                                TrainingBalancePresetChip(
                                    label = "Run Focus",
                                    target = TrainingBalance.RUN_FOCUS,
                                    currentBalance = balance,
                                    enabled = isEnabled,
                                    onClick = {
                                        coachViewModel.updateAvailability(
                                            weeklyAvailability = userProfile.weeklyAvailability ?: emptyMap(),
                                            longTrainingDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                            strengthDays = userProfile.strengthDays ?: 2,
                                            trainingBalance = TrainingBalance.RUN_FOCUS
                                        )
                                    }
                                )
                                TrainingBalancePresetChip(
                                    label = "Bike Focus",
                                    target = TrainingBalance.BIKE_FOCUS,
                                    currentBalance = balance,
                                    enabled = isEnabled,
                                    onClick = {
                                        coachViewModel.updateAvailability(
                                            weeklyAvailability = userProfile.weeklyAvailability ?: emptyMap(),
                                            longTrainingDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                            strengthDays = userProfile.strengthDays ?: 2,
                                            trainingBalance = TrainingBalance.BIKE_FOCUS
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm))

                            // Custom Sliders
                            Text(
                                text = "Custom Distribution",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            
                            TrainingBalanceSlider(
                                label = "Bike",
                                value = balance.bikePercent,
                                enabled = isEnabled,
                                onValueChange = { newValue ->
                                    val remaining = 100 - newValue
                                    val otherSum = balance.runPercent + balance.swimPercent
                                    val adjustedBalance = if (remaining > 0 && otherSum > 0) {
                                        val runRatio = balance.runPercent.toFloat() / otherSum
                                        val swimRatio = balance.swimPercent.toFloat() / otherSum
                                        TrainingBalance(
                                            bikePercent = newValue,
                                            runPercent = (remaining * runRatio).roundToInt(),
                                            swimPercent = remaining - (remaining * runRatio).roundToInt() // Ensure exact sum
                                        )
                                    } else if (remaining > 0) {
                                        // If other sum is 0, split remaining equally
                                        TrainingBalance(
                                            bikePercent = newValue,
                                            runPercent = remaining / 2,
                                            swimPercent = remaining - (remaining / 2)
                                        )
                                    } else {
                                        TrainingBalance(bikePercent = newValue, runPercent = 0, swimPercent = 0)
                                    }
                                    coachViewModel.updateAvailability(
                                        weeklyAvailability = userProfile.weeklyAvailability ?: emptyMap(),
                                        longTrainingDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                        strengthDays = userProfile.strengthDays ?: 2,
                                        trainingBalance = adjustedBalance
                                    )
                                }
                            )
                            
                            TrainingBalanceSlider(
                                label = "Run",
                                value = balance.runPercent,
                                enabled = isEnabled,
                                onValueChange = { newValue ->
                                    val remaining = 100 - newValue
                                    val otherSum = balance.bikePercent + balance.swimPercent
                                    val adjustedBalance = if (remaining > 0 && otherSum > 0) {
                                        val bikeRatio = balance.bikePercent.toFloat() / otherSum
                                        val swimRatio = balance.swimPercent.toFloat() / otherSum
                                        TrainingBalance(
                                            bikePercent = (remaining * bikeRatio).roundToInt(),
                                            runPercent = newValue,
                                            swimPercent = remaining - (remaining * bikeRatio).roundToInt() // Ensure exact sum
                                        )
                                    } else if (remaining > 0) {
                                        // If other sum is 0, split remaining equally
                                        TrainingBalance(
                                            bikePercent = remaining / 2,
                                            runPercent = newValue,
                                            swimPercent = remaining - (remaining / 2)
                                        )
                                    } else {
                                        TrainingBalance(bikePercent = 0, runPercent = newValue, swimPercent = 0)
                                    }
                                    coachViewModel.updateAvailability(
                                        weeklyAvailability = userProfile.weeklyAvailability ?: emptyMap(),
                                        longTrainingDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                        strengthDays = userProfile.strengthDays ?: 2,
                                        trainingBalance = adjustedBalance
                                    )
                                }
                            )
                            
                            TrainingBalanceSlider(
                                label = "Swim",
                                value = balance.swimPercent,
                                enabled = isEnabled,
                                onValueChange = { newValue ->
                                    val remaining = 100 - newValue
                                    val otherSum = balance.bikePercent + balance.runPercent
                                    val adjustedBalance = if (remaining > 0 && otherSum > 0) {
                                        val bikeRatio = balance.bikePercent.toFloat() / otherSum
                                        val runRatio = balance.runPercent.toFloat() / otherSum
                                        TrainingBalance(
                                            bikePercent = (remaining * bikeRatio).roundToInt(),
                                            runPercent = remaining - (remaining * bikeRatio).roundToInt(), // Ensure exact sum
                                            swimPercent = newValue
                                        )
                                    } else if (remaining > 0) {
                                        // If other sum is 0, split remaining equally
                                        TrainingBalance(
                                            bikePercent = remaining / 2,
                                            runPercent = remaining - (remaining / 2),
                                            swimPercent = newValue
                                        )
                                    } else {
                                        TrainingBalance(bikePercent = 0, runPercent = 0, swimPercent = newValue)
                                    }
                                    coachViewModel.updateAvailability(
                                        weeklyAvailability = userProfile.weeklyAvailability ?: emptyMap(),
                                        longTrainingDay = userProfile.longTrainingDay ?: DayOfWeek.SUNDAY,
                                        strengthDays = userProfile.strengthDays ?: 2,
                                        trainingBalance = adjustedBalance
                                    )
                                }
                            )
                            
                            // Show total percentage
                            val total = balance.bikePercent + balance.runPercent + balance.swimPercent
                            Text(
                                text = "Total: $total%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (total == 100) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }

                // Section 5: Weekly Schedule Anchors
                SectionHeader(
                    title = "Weekly Schedule Anchors",
                    subtitle = "Define your weekly training structure"
                )

                WeeklyScheduleSection(
                    userProfile = userProfile,
                    onDayAnchorChanged = { day, anchorType ->
                        coachViewModel.updateDayAnchor(day, anchorType)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Section 6: Auto-Pilot Generation
                SectionHeader(
                    title = "Auto-Pilot Generation",
                    subtitle = "Generate training plan using Iron Brain"
                )

                AutoPilotGenerationCard(
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
fun AutoPilotGenerationCard(
    isGenerating: Boolean,
    generationError: String?,
    generationSuccess: Int?,
    onGenerate: (Int) -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMonths by remember { mutableIntStateOf(3) }
    
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
                text = "Generate Training Plan",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "Automatically generate a training plan based on your profile, current fitness, and Iron Brain rules.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            // Months selector slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Duration:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (selectedMonths == 1) "$selectedMonths Month" else "$selectedMonths Months",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = selectedMonths.toFloat(),
                    onValueChange = { selectedMonths = it.toInt() },
                    valueRange = 1f..6f,
                    steps = 4, // Creates stops at 1, 2, 3, 4, 5, 6
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "1",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        "6",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Button(
                onClick = { onGenerate(selectedMonths) },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.padding(Spacing.sm))
                    Text("Generating...")
                } else {
                    Text("Generate ${selectedMonths}-Month Plan")
                }
            }
            
            // Show error if any
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
            
            // Show success if any
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
private fun TrainingBalancePresetChip(
    label: String,
    target: TrainingBalance,
    currentBalance: TrainingBalance,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { 
            Text(
                label, 
                style = MaterialTheme.typography.bodySmall
            ) 
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (currentBalance == target && enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    )
}

@Composable
private fun TrainingBalanceSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: $value%",
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

