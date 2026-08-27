package com.tripath.ui.planner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.model.WorkoutType
import com.tripath.domain.running.RunPlanDisplayMetrics
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.navigation.Screen
import com.tripath.ui.theme.freshnessColor
import com.tripath.ui.theme.plannedContentTint
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector

enum class DayTotalDisplayMode {
    TSS,
    MINUTES
}

@Composable
fun WeeklyPlannerScreen(
    navController: NavController,
    viewModel: WeeklyPlannerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedWeeks by remember { mutableStateOf(setOf<Int>()) }
    var dayTotalDisplayMode by rememberSaveable { mutableStateOf(DayTotalDisplayMode.TSS) }

    // Inject CoachViewModel for plan generation
    val coachViewModel: com.tripath.ui.coach.CoachViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ...existing code for header, matrix, etc...
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SectionHeader(
                    title = "Planner",
                    subtitle = if (uiState.isMonthView) "Month overview" else "4-week overview",
                    action = {
                        IconButton(onClick = { navController.navigate(Screen.AutoPlannerSettings.route) }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Auto-planner settings"
                            )
                        }
                    }
                )
                MatrixNavigationHeader(
                    currentMonth = uiState.currentMonth,
                    onPrevMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onGoToCurrent = { viewModel.goToCurrent() }
                )
                // Toggle for including imported activities
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setIncludeImportedActivities(false) }
                        ) {
                            RadioButton(
                                selected = !uiState.includeImportedActivities,
                                onClick = { viewModel.setIncludeImportedActivities(false) }
                            )
                            Text(
                                text = "Planned only",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setIncludeImportedActivities(true) }
                        ) {
                            RadioButton(
                                selected = uiState.includeImportedActivities,
                                onClick = { viewModel.setIncludeImportedActivities(true) }
                            )
                            Text(
                                text = "Include imported",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Toggle for day-cell number and heatmap basis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Number:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { dayTotalDisplayMode = DayTotalDisplayMode.TSS }
                        ) {
                            RadioButton(
                                selected = dayTotalDisplayMode == DayTotalDisplayMode.TSS,
                                onClick = { dayTotalDisplayMode = DayTotalDisplayMode.TSS }
                            )
                            Text(
                                text = "TSS",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { dayTotalDisplayMode = DayTotalDisplayMode.MINUTES }
                        ) {
                            RadioButton(
                                selected = dayTotalDisplayMode == DayTotalDisplayMode.MINUTES,
                                onClick = { dayTotalDisplayMode = DayTotalDisplayMode.MINUTES }
                            )
                            Text(
                                text = "Minutes",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // The Matrix
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // ...existing code for day labels, weekly rows, etc...
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 80.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Spacer(modifier = Modifier.width(24.dp))
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    days.forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                uiState.weeklyRows.forEachIndexed { index, weekRow ->
                    val isExpanded = expandedWeeks.contains(index)
                    WeeklyRow(
                        weekRow = weekRow,
                        isExpanded = isExpanded,
                        includeImported = uiState.includeImportedActivities,
                        onDayClick = { date -> navController.navigate(Screen.DayDetail.createRoute(date)) },
                        onWorkoutClick = { workoutId, isPlanned -> navController.navigate(Screen.WorkoutDetail.createRoute(workoutId, isPlanned)) },
                        onToggleExpand = {
                            expandedWeeks = if (isExpanded) expandedWeeks - index else expandedWeeks + index
                        },
                        onCopyWeek = { viewModel.copyWeek(weekRow.weekStart) },
                        weekNumber = weekRow.weekNumber,
                        thresholdRunPace = uiState.userProfile?.thresholdRunPace,
                        dayTotalDisplayMode = dayTotalDisplayMode,
                        projectedReadiness = uiState.projectedReadiness,
                        planConflicts = uiState.planConflicts
                    )
                }
                // Says what the dots mean. A coloured mark nobody can decode is worse than no mark:
                // it looks like information and carries none.
                if (uiState.projectedReadiness.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(freshnessColor(85))
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(freshnessColor(20))
                        )
                        Text(
                            text = "Projected readiness on planned days, fresh to depleted — from " +
                                "planned training only. A larger dot means the plan stacks a tissue.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (uiState.disciplineDistribution.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    DisciplineDistributionBar(
                        distribution = uiState.disciplineDistribution,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xs)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun WeeklyRow(
    weekRow: WeeklyRowState,
    isExpanded: Boolean,
    includeImported: Boolean,
    onDayClick: (LocalDate) -> Unit,
    onWorkoutClick: (String, Boolean) -> Unit,
    onToggleExpand: () -> Unit,
    onCopyWeek: () -> Unit,
    weekNumber: Int,
    thresholdRunPace: Int? = null,
    dayTotalDisplayMode: DayTotalDisplayMode,
    /** Projected readiness score per upcoming day. Empty until the projection lands. */
    projectedReadiness: Map<LocalDate, Int> = emptyMap(),
    /** Days the plan stacks onto tissue that will not have recovered, keyed to the explanation. */
    planConflicts: Map<LocalDate, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isExpanded) 120.dp else 80.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Week Number Label
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "W$weekNumber",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            
            // 7 Days
            weekRow.days.forEach { day ->
                DayCell(
                    day = day,
                    isExpanded = isExpanded,
                    includeImported = includeImported,
                    onClick = { onDayClick(day.date) },
                    onWorkoutClick = onWorkoutClick,
                    thresholdRunPace = thresholdRunPace,
                    dayTotalDisplayMode = dayTotalDisplayMode,
                    projectedReadiness = projectedReadiness[day.date],
                    hasPlanConflict = planConflicts.containsKey(day.date),
                    modifier = Modifier.weight(1f)
                )
            }

            // Summary Panel
            WeeklySummaryPanel(
                plannedTSS = weekRow.plannedTSS,
                actualTSS = weekRow.actualTSS,
                durationMinutes = weekRow.totalDurationMinutes,
                progress = weekRow.tssCompletionProgress,
                hasWarning = weekRow.hasTssJumpWarning,
                onClick = onToggleExpand,
                modifier = Modifier.width(72.dp)
            )
        }
        
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onCopyWeek,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "Copy to Next Week",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun DayCell(
    day: WeekDay,
    isExpanded: Boolean,
    includeImported: Boolean,
    onClick: () -> Unit,
    onWorkoutClick: (String, Boolean) -> Unit,
    thresholdRunPace: Int? = null,
    dayTotalDisplayMode: DayTotalDisplayMode = DayTotalDisplayMode.TSS,
    /**
     * Readiness projected from planned training, for a future day. Null for today, the past, and
     * until the projection has been computed.
     */
    projectedReadiness: Int? = null,
    /** True when this day's plan lands on tissue the model expects to still be loaded. */
    hasPlanConflict: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Build day totals with replacement rule:
    // completed logs replace matching planned entries (same day+type),
    // unmatched planned remain, unmatched completed are included only when import toggle is on.
    val remainingLogsByType = day.completedLogs
        .groupBy { it.type }
        .mapValues { (_, logs) -> logs.toMutableList() }
        .toMutableMap()

    var totalTSS = 0
    var totalMinutes = 0

    day.workouts.forEach { workout ->
        val matchingLogs = remainingLogsByType[workout.type]
        val matchingLog = if (!matchingLogs.isNullOrEmpty()) {
            matchingLogs.removeAt(0)
        } else {
            null
        }

        if (matchingLog != null) {
            totalTSS += matchingLog.computedTSS ?: 0
            totalMinutes += matchingLog.durationMinutes
        } else {
            val plannedMetrics = RunPlanDisplayMetrics.fromPlan(workout, thresholdRunPace)
            totalTSS += plannedMetrics.tss
            totalMinutes += plannedMetrics.durationMinutes
        }
    }

    val unmatchedCompletedLogs = remainingLogsByType.values.flatten()
    if (includeImported) {
        totalTSS += unmatchedCompletedLogs.sumOf { it.computedTSS ?: 0 }
        totalMinutes += unmatchedCompletedLogs.sumOf { it.durationMinutes }
    }
    

    // Use grey-ish for planned activities, sport color for completed
    val plannedGrey = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val heatmapColor = when (dayTotalDisplayMode) {
        DayTotalDisplayMode.TSS -> when {
            totalTSS == 0 -> plannedGrey.copy(alpha = 0.3f)
            totalTSS <= 20 -> plannedGrey.copy(alpha = 0.4f)
            totalTSS <= 60 -> plannedGrey.copy(alpha = 0.6f)
            totalTSS <= 100 -> plannedGrey.copy(alpha = 0.8f)
            else -> plannedGrey
        }
        DayTotalDisplayMode.MINUTES -> when {
            totalMinutes == 0 -> plannedGrey.copy(alpha = 0.3f)
            totalMinutes <= 30 -> plannedGrey.copy(alpha = 0.4f)
            totalMinutes <= 90 -> plannedGrey.copy(alpha = 0.6f)
            totalMinutes <= 150 -> plannedGrey.copy(alpha = 0.8f)
            else -> plannedGrey
        }
    }

    val backgroundColor = when {
        day.specialPeriods.any { it.type == SpecialPeriodType.INJURY } -> Color(0x33FF0000)
        day.specialPeriods.any { it.type == SpecialPeriodType.HOLIDAY } -> Color(0x33FFD700)
        day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else -> heatmapColor
    }

    val borderColor = if (day.isToday) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .border(
                width = if (day.isToday) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            ),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP: Day Number
                val dayLabel = if (day.date.dayOfMonth == 1) {
                    "${day.date.dayOfMonth} ${day.date.format(DateTimeFormatter.ofPattern("MMM", java.util.Locale.ENGLISH))}"
                } else {
                    day.date.dayOfMonth.toString()
                }
                
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 1.dp)
                )
                
                // MIDDLE: Icons for planned events
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Show unmatched imported logs as extra icons when the toggle is enabled.
                    val importedLogs = if (includeImported) unmatchedCompletedLogs else emptyList()
                    
                    val hasActivities = day.workouts.isNotEmpty() || importedLogs.isNotEmpty()
                    
                    if (hasActivities) {
                        // Show planned workouts in grey
                        day.workouts.forEach { workout ->
                            Icon(
                                imageVector = workout.type.toIcon(),
                                contentDescription = null,
                                tint = workout.plannedContentTint(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier
                                    .size(if (isExpanded) 16.dp else 14.dp)
                            )
                            if (day.workouts.size > 1 || importedLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                        // Show imported logs (with slightly different styling to distinguish)
                        if (includeImported) {
                            importedLogs.forEach { log ->
                                Icon(
                                    imageVector = log.type.toIcon(),
                                    contentDescription = null,
                                    tint = log.type.toColor().copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(if (isExpanded) 14.dp else 12.dp)
                                )
                                if (importedLogs.size > 1) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        )
                    }
                }
                
                // BOTTOM: TSS
                Text(
                    text = if (dayTotalDisplayMode == DayTotalDisplayMode.TSS) "$totalTSS" else "$totalMinutes",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
            
            // Special Period Icons (top-right corner)
            if (day.specialPeriods.isNotEmpty()) {
                val period = day.specialPeriods.first()
                Text(
                    text = if (period.type == SpecialPeriodType.HOLIDAY) "🏖️" else "🩹",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                )
            } else if (projectedReadiness != null && day.workouts.isNotEmpty()) {
                // A projection, and only on days that actually have something planned — a coloured
                // dot on an empty Thursday would be answering a question nobody asked. It goes
                // where the special-period marker goes and yields to it, because an injury is a
                // fact about the day and this is a forecast.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(if (hasPlanConflict) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(freshnessColor(projectedReadiness)),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }
}

@Composable
fun DisciplineDistributionBar(
    distribution: Map<WorkoutType, Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "Discipline Balance",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Sort by type to keep consistent order
            WorkoutType.entries.forEach { type ->
                val percentage = distribution[type] ?: 0f
                if (percentage > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(percentage.coerceAtLeast(0.01f))
                            .background(type.toColor())
                    )
                }
            }
        }
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            distribution.keys.sorted().forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(type.toColor())
                    )
                    Text(
                        text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutType.toIcon(): ImageVector {
    return when (this) {
        WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
        WorkoutType.SWIM -> Icons.Default.Pool
        WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
        WorkoutType.OTHER -> Icons.AutoMirrored.Filled.DirectionsWalk
        WorkoutType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        WorkoutType.HIKE -> Icons.AutoMirrored.Filled.DirectionsWalk
    }
}

@Composable
fun MatrixNavigationHeader(
    currentMonth: LocalDate,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH)
    val dateRangeText = currentMonth.format(monthFormatter)
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month"
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateRangeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onGoToCurrent) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = "Go to current week",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month"
            )
        }
    }
}

@Composable
fun WeeklySummaryPanel(
    plannedTSS: Int,
    actualTSS: Int,
    durationMinutes: Int,
    progress: Float,
    hasWarning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tssColor = if (hasWarning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasWarning) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
            Text(
                text = "$plannedTSS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = tssColor,
                maxLines = 1
            )
            Text(
                text = "$actualTSS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${durationMinutes / 60}h",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        // Vertical Progress Bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .align(Alignment.BottomCenter)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeeklyPlannerScreenPreview() {
    TriPathTheme {
        WeeklyPlannerScreen(navController = rememberNavController())
    }
}
