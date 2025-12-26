package com.tripath.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    isPlanned: Boolean,
    navController: NavController,
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Load workout data
    LaunchedEffect(workoutId, isPlanned) {
        viewModel.loadWorkout(workoutId, isPlanned)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Edit action only for planned workouts
                    if (isPlanned) {
                        IconButton(onClick = { /* TODO: Navigate to edit screen */ }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                    // Delete action for both
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: "Unknown error",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    if (isPlanned && uiState.trainingPlan != null) {
                        PlannedWorkoutContent(
                            plan = uiState.trainingPlan!!,
                            viewModel = viewModel
                        )
                    } else if (!isPlanned && uiState.workoutLog != null) {
                        CompletedWorkoutContent(
                            log = uiState.workoutLog!!,
                            linkedPlan = uiState.linkedPlan,
                            userProfile = uiState.userProfile,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Workout") },
            text = { Text("Are you sure you want to delete this workout? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteWorkout {
                            navController.popBackStack()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlannedWorkoutContent(
    plan: TrainingPlan,
    viewModel: WorkoutDetailViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Header Section
        WorkoutHeader(
            workoutType = plan.type,
            date = plan.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
            tss = plan.plannedTSS
        )

        // Metrics Grid
        PlannedMetricsGrid(plan = plan)

        // Strength Details (if applicable)
        if (plan.type == WorkoutType.STRENGTH) {
            StrengthDetailsSection(
                strengthFocus = plan.strengthFocus,
                intensity = plan.intensity
            )
        }

        // Notes Section
        if (!plan.subType.isNullOrBlank()) {
            NotesSection(notes = plan.subType)
        }

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

@Composable
private fun CompletedWorkoutContent(
    log: WorkoutLog,
    linkedPlan: TrainingPlan?,
    userProfile: UserProfile?,
    viewModel: WorkoutDetailViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Header Section
        WorkoutHeader(
            workoutType = log.type,
            date = log.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
            tss = log.computedTSS ?: 0
        )

        // Metrics Grid
        CompletedMetricsGrid(log = log, linkedPlan = linkedPlan)

        // TSS Analysis (if linked plan exists)
        if (linkedPlan != null) {
            TssAnalysisCard(
                plannedTSS = linkedPlan.plannedTSS,
                actualTSS = log.computedTSS ?: 0
            )
        }

        // HR Zone Distribution (if HR data available)
        if (log.avgHeartRate != null && userProfile?.maxHeartRate != null) {
            HrZoneDistributionCard(
                avgHeartRate = log.avgHeartRate,
                maxHeartRate = userProfile.maxHeartRate,
                viewModel = viewModel
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

@Composable
private fun WorkoutHeader(
    workoutType: WorkoutType,
    date: String,
    tss: Int,
    modifier: Modifier = Modifier
) {
    val color = workoutType.toColor()
    val icon = getWorkoutIcon(workoutType)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = workoutType.name,
                tint = color,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = workoutType.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Text(
                text = date,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // TSS Badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "$tss TSS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun PlannedMetricsGrid(
    plan: TrainingPlan,
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
                text = "Planned Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            MetricRow(
                label = "Duration",
                value = "${plan.durationMinutes} min"
            )
            
            MetricRow(
                label = "Planned TSS",
                value = "${plan.plannedTSS}"
            )
        }
    }
}

@Composable
private fun CompletedMetricsGrid(
    log: WorkoutLog,
    linkedPlan: TrainingPlan?,
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
                text = "Workout Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${log.durationMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (linkedPlan != null) {
                        val delta = log.durationMinutes - linkedPlan.durationMinutes
                        Text(
                            text = "(${if (delta > 0) "+" else ""}$delta min)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (delta >= 0) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Distance
            if (log.distanceMeters != null) {
                MetricRow(
                    label = "Distance",
                    value = formatDistance(log.distanceMeters, log.type)
                )
            }
            
            // Avg HR
            if (log.avgHeartRate != null) {
                MetricRow(
                    label = "Avg Heart Rate",
                    value = "${log.avgHeartRate} bpm"
                )
            }
            
            // Avg Power
            if (log.avgPowerWatts != null) {
                MetricRow(
                    label = "Avg Power",
                    value = "${log.avgPowerWatts} W"
                )
            }
            
            // Pace/Speed
            if (log.distanceMeters != null && log.durationMinutes > 0) {
                val pace = calculatePace(log.distanceMeters, log.durationMinutes, log.type)
                if (pace != null) {
                    MetricRow(
                        label = if (log.type == WorkoutType.RUN) "Pace" else "Speed",
                        value = pace
                    )
                }
            }
            
            // Calories
            if (log.calories != null) {
                MetricRow(
                    label = "Calories",
                    value = "${log.calories} kcal"
                )
            }
        }
    }
}

@Composable
private fun TssAnalysisCard(
    plannedTSS: Int,
    actualTSS: Int,
    modifier: Modifier = Modifier
) {
    val delta = actualTSS - plannedTSS
    val percentage = if (plannedTSS > 0) (actualTSS.toFloat() / plannedTSS.toFloat()) else 0f
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "TSS Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$plannedTSS",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Planned",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$actualTSS",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Actual",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${if (delta > 0) "+" else ""}$delta",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (delta >= 0) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Delta",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            LinearProgressIndicator(
                progress = { percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )
            
            Text(
                text = "${(percentage * 100).toInt()}% of planned TSS",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HrZoneDistributionCard(
    avgHeartRate: Int,
    maxHeartRate: Int,
    viewModel: WorkoutDetailViewModel,
    modifier: Modifier = Modifier
) {
    val percentage = viewModel.calculateHrZonePercentage(avgHeartRate, maxHeartRate)
    val zoneLabel = viewModel.getHrZoneLabel(avgHeartRate, maxHeartRate)
    
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
                text = "Heart Rate Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$avgHeartRate bpm",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Average HR",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = zoneLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(percentage * 100).toInt()}% of max",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun StrengthDetailsSection(
    strengthFocus: StrengthFocus?,
    intensity: Intensity?,
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
                text = "Strength Training Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (strengthFocus != null) {
                MetricRow(
                    label = "Focus",
                    value = formatStrengthFocus(strengthFocus)
                )
            }
            
            if (intensity != null) {
                MetricRow(
                    label = "Intensity",
                    value = formatIntensity(intensity)
                )
            }
        }
    }
}

@Composable
private fun NotesSection(
    notes: String,
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
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

// Helper functions

private fun getWorkoutIcon(type: WorkoutType): ImageVector {
    return when (type) {
        WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutType.BIKE -> Icons.Default.PedalBike
        WorkoutType.SWIM -> Icons.Default.Pool
        WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
        WorkoutType.OTHER -> Icons.AutoMirrored.Filled.DirectionsWalk
    }
}

private fun formatDistance(meters: Double, type: WorkoutType): String {
    return when (type) {
        WorkoutType.SWIM -> "${meters.toInt()} m"
        else -> String.format("%.2f km", meters / 1000.0)
    }
}

private fun calculatePace(meters: Double, minutes: Int, type: WorkoutType): String? {
    if (meters <= 0 || minutes <= 0) return null
    
    return when (type) {
        WorkoutType.RUN -> {
            // Calculate min/km
            val minPerKm = (minutes.toDouble() / (meters / 1000.0))
            val mins = minPerKm.toInt()
            val secs = ((minPerKm - mins) * 60).toInt()
            String.format("%d:%02d /km", mins, secs)
        }
        WorkoutType.BIKE, WorkoutType.SWIM -> {
            // Calculate km/h or m/h
            val kmh = (meters / 1000.0) / (minutes / 60.0)
            String.format("%.1f km/h", kmh)
        }
        else -> null
    }
}

private fun formatStrengthFocus(focus: StrengthFocus): String {
    return when (focus) {
        StrengthFocus.FULL_BODY -> "Full Body"
        StrengthFocus.UPPER -> "Upper Body"
        StrengthFocus.LOWER -> "Lower Body"
    }
}

private fun formatIntensity(intensity: Intensity): String {
    return when (intensity) {
        Intensity.LIGHT -> "Light"
        Intensity.HEAVY -> "Heavy"
    }
}

