package com.tripath.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.WorkoutType
import com.tripath.ui.dashboard.components.WeeklyCalendarStrip
import com.tripath.ui.model.FormStatus
import com.tripath.ui.navigation.Screen
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ==================== Staggered Animation Components ====================

@Composable
private fun StaggeredAnimatedItem(
    index: Int,
    baseDelay: Int = 50,
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    
    LaunchedEffect(Unit) {
        delay((index * baseDelay).toLong())
        visibleState.targetState = true
    }
    
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(400)) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialOffsetY = { it / 4 }
        )
    ) {
        content()
    }
}

// ==================== Main Dashboard Screen ====================

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // 1. Header & Status (Combined) - Index 0
            StaggeredAnimatedItem(index = 0) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    DashboardHeader(
                        greeting = uiState.greeting,
                        syncStatus = uiState.syncStatus,
                        onSyncClick = { viewModel.syncData() }
                    )
                    
                    TrainingStatusCard(
                        formStatus = uiState.formStatus,
                        tsb = uiState.tsb,
                        ctl = uiState.ctl,
                        atl = uiState.atl
                    )
                }
            }

            // 2. Today's Focus - Index 1
            StaggeredAnimatedItem(index = 1) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Today's Focus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    DayDetailCard(
                        plan = uiState.selectedDatePlan,
                        isRestDay = uiState.isRestDay,
                        restDayMessage = uiState.restDayMessage,
                        isWorkoutCompleted = uiState.isWorkoutCompleted,
                        onWorkoutClick = { workoutId, isPlanned ->
                            navController.navigate(Screen.WorkoutDetail.createRoute(workoutId, isPlanned))
                        }
                    )
                }
            }

            // 3. Weekly Overview - Index 2
            StaggeredAnimatedItem(index = 2) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "This Week",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.weeklyActualTSS} / ${uiState.weeklyPlannedTSS} / ${uiState.weeklyAllowedTSS} TSS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Weekly Load Progress Chart
                    WeeklyTssChart(
                        actual = uiState.weeklyActualTSS,
                        planned = uiState.weeklyPlannedTSS,
                        allowed = uiState.weeklyAllowedTSS
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    WeeklyCalendarStrip(
                        weekDayStatuses = uiState.weekDayStatuses,
                        onDateSelected = { date -> viewModel.selectDate(date) }
                    )
                }
            }

            // 4. Completed Logs (if any for selected day) - Index 3
            if (uiState.selectedDateLogs.isNotEmpty()) {
                StaggeredAnimatedItem(index = 3) {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = "Completed Activities",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = Spacing.sm)
                        )
                        
                        uiState.selectedDateLogs.forEach { log ->
                            CompletedActivityRow(
                                workout = log,
                                onClick = {
                                    navController.navigate(Screen.WorkoutDetail.createRoute(log.connectId, false))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

// ==================== Components ====================

@Composable
private fun DashboardHeader(
    greeting: String,
    syncStatus: SyncStatus,
    onSyncClick: () -> Unit
) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        SyncButton(syncStatus, onSyncClick)
    }
}

@Composable
private fun TrainingStatusCard(
    formStatus: FormStatus,
    tsb: Double,
    ctl: Double,
    atl: Double
) {
    val statusColor = when (formStatus) {
        FormStatus.FRESHNESS -> Color(0xFF4CAF50)
        FormStatus.OPTIMAL -> MaterialTheme.colorScheme.primary
        FormStatus.OVERREACHING -> Color(0xFFFF5252)
    }
    
    val statusText = when (formStatus) {
        FormStatus.FRESHNESS -> "Fresh"
        FormStatus.OPTIMAL -> "Optimal"
        FormStatus.OVERREACHING -> "Tired"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Status (Left)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "TRAINING STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Text(
                    text = "Form (TSB): ${String.format("%+.0f", tsb)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Metrics Divider
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )

            // Secondary Metrics (Right)
            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .padding(start = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                MetricRow(label = "Fitness", value = String.format("%.0f", ctl), color = MaterialTheme.colorScheme.primary)
                MetricRow(label = "Fatigue", value = String.format("%.0f", atl), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun WeeklyTssChart(actual: Int, planned: Int, allowed: Int) {
    val maxTss = maxOf(actual, planned, allowed, 100).toFloat()
    
    // Animate proportions
    var animatedActual by remember { mutableFloatStateOf(0f) }
    var animatedPlanned by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(actual, planned) {
        delay(200)
        animate(
            initialValue = 0f,
            targetValue = actual.toFloat() / maxTss,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) { value, _ -> animatedActual = value }
    }
    
    LaunchedEffect(planned) {
        delay(300)
        animate(
            initialValue = 0f,
            targetValue = planned.toFloat() / maxTss,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) { value, _ -> animatedPlanned = value }
    }

    val allowedProgress = allowed.toFloat() / maxTss
    
    val actualColor = when {
        actual > allowed -> Color(0xFFFF5252) // Over allowed limit
        else -> MaterialTheme.colorScheme.primary
    }
    
    val plannedColor = when {
        planned > allowed -> Color(0xFFFFB74D) // Warning: planned exceeds allowed
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Allowed TSS Marker (Background bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth(allowedProgress)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )
            
            // Planned TSS Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedPlanned)
                    .fillMaxHeight()
                    .background(plannedColor)
            )
            
            // Actual TSS Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedActual)
                    .fillMaxHeight()
                    .background(actualColor)
            )
        }
        
        // Legend / Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Actual: $actual",
                style = MaterialTheme.typography.labelSmall,
                color = actualColor
            )
            Text(
                text = "Planned: $planned",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Limit: $allowed",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun DayDetailCard(
    plan: TrainingPlan?,
    isRestDay: Boolean,
    restDayMessage: String,
    isWorkoutCompleted: Boolean,
    onWorkoutClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWorkoutCompleted) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        onClick = { if (plan != null) onWorkoutClick(plan.id, true) }
    ) {
        if (isRestDay) {
            RestDayContent(message = restDayMessage)
        } else if (plan != null) {
            WorkoutPlanContent(plan = plan, isCompleted = isWorkoutCompleted)
        }
    }
}

@Composable
private fun RestDayContent(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Column {
            Text(
                text = "Recovery Day",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun WorkoutPlanContent(plan: TrainingPlan, isCompleted: Boolean) {
    val color = plan.type.toColor()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Icon & Title
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = plan.type.toIcon(),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column {
                    Text(
                        text = plan.subType ?: plan.type.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${plan.durationMinutes} min • ${plan.plannedTSS} TSS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Right: Status Icon
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Planned",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // Intensity / Focus details below
        if (plan.type == WorkoutType.STRENGTH && plan.strengthFocus != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(Spacing.md))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailItem(label = "FOCUS", value = formatStrengthFocus(plan.strengthFocus))
                if (plan.intensity != null) {
                    DetailItem(label = "INTENSITY", value = formatIntensity(plan.intensity))
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CompletedActivityRow(
    workout: WorkoutLog,
    onClick: () -> Unit
) {
    val color = workout.type.toColor()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = workout.type.toIcon(),
                contentDescription = null,
                tint = color.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = workout.type.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = "${workout.durationMinutes}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "${workout.computedTSS ?: 0} TSS",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun SyncButton(
    syncStatus: SyncStatus,
    onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "sync_scale"
    )

    IconButton(onClick = onSyncClick) {
        when (syncStatus) {
            SyncStatus.SYNCING -> CircularProgressIndicator(modifier = Modifier.size(24.dp).scale(scale), strokeWidth = 2.dp)
            SyncStatus.SUCCESS -> Icon(Icons.Default.CheckCircle, "Synced", tint = Color(0xFF4CAF50))
            SyncStatus.ERROR -> Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error)
            else -> Icon(Icons.Default.Sync, "Sync", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

// ==================== Helper Functions ====================

private fun WorkoutType.toIcon(): ImageVector {
    return when (this) {
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
        StrengthFocus.UPPER -> "Upper Body"
        StrengthFocus.LOWER -> "Lower Body"
        StrengthFocus.HEAVY -> "Heavy Strength"
        StrengthFocus.STABILITY -> "Core & Stability"
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
fun DashboardScreenPreview() {
    TriPathTheme {
        DashboardScreen(navController = rememberNavController())
    }
}
