package com.tripath.ui.planner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
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
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.navigation.Screen
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun WeeklyPlannerScreen(
    navController: NavController,
    viewModel: WeeklyPlannerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedWeeks by remember { mutableStateOf(setOf<Int>()) }

    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SectionHeader(
                    title = "Planner",
                    subtitle = if (uiState.isMonthView) "Month overview" else "4-week overview"
                )

                MatrixNavigationHeader(
                    startDate = uiState.startDate,
                    weekCount = uiState.weeklyRows.size,
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
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // The Matrix
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Day Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 80.dp), // Match summary panel width
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
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
                    
                    if (weekRow.monthLabel != null) {
                        Text(
                            text = weekRow.monthLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.sm, bottom = 2.dp, start = 2.dp)
                        )
                    }

                    WeeklyRow(
                        weekRow = weekRow,
                        isExpanded = isExpanded,
                        includeImported = uiState.includeImportedActivities,
                        onDayClick = { date -> 
                            navController.navigate(Screen.DayDetail.createRoute(date))
                        },
                        onWorkoutClick = { workoutId ->
                            navController.navigate(Screen.WorkoutDetail.createRoute(workoutId, true))
                        },
                        onToggleExpand = {
                            expandedWeeks = if (isExpanded) {
                                expandedWeeks - index
                            } else {
                                expandedWeeks + index
                            }
                        },
                        onCopyWeek = { viewModel.copyWeek(weekRow.weekStart) }
                    )
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
    onWorkoutClick: (String) -> Unit,
    onToggleExpand: () -> Unit,
    onCopyWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isExpanded) 120.dp else 80.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // 7 Days
            weekRow.days.forEach { day ->
                DayCell(
                    day = day,
                    isExpanded = isExpanded,
                    includeImported = includeImported,
                    onClick = { onDayClick(day.date) },
                    onWorkoutClick = onWorkoutClick,
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
    onWorkoutClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate total TSS for heatmap (planned + imported if toggle is on)
    val importedLogsForHeatmap = if (includeImported) {
        day.completedLogs.filter { log ->
            !day.workouts.any { plan ->
                plan.date == log.date && plan.type == log.type
            }
        }
    } else {
        emptyList()
    }
    
    val totalPlannedTSS = day.workouts.sumOf { it.plannedTSS }
    val totalImportedTSS = if (includeImported) {
        importedLogsForHeatmap.sumOf { (it.computedTSS ?: 0) }
    } else {
        0
    }
    val totalTSS = totalPlannedTSS + totalImportedTSS
    
    val heatmapColor = when {
        totalTSS == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        totalTSS <= 20 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        totalTSS <= 60 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        totalTSS <= 100 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
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
                verticalArrangement = if (isExpanded) Arrangement.Top else Arrangement.Center
            ) {
                val dayLabel = if (day.date.dayOfMonth == 1) {
                    "${day.date.dayOfMonth} ${day.date.format(DateTimeFormatter.ofPattern("MMM"))}"
                } else {
                    day.date.dayOfMonth.toString()
                }
                
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 1.dp)
                )
                
                // Get imported logs that don't match a plan (when toggle is enabled)
                val importedLogs = if (includeImported) {
                    day.completedLogs.filter { log ->
                        !day.workouts.any { plan ->
                            plan.date == log.date && plan.type == log.type
                        }
                    }
                } else {
                    emptyList()
                }
                
                val hasActivities = day.workouts.isNotEmpty() || importedLogs.isNotEmpty()
                
                if (hasActivities) {
                    // Show planned workouts
                    day.workouts.forEach { workout ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = workout.type.toIcon(),
                                contentDescription = null,
                                tint = workout.type.toColor(),
                                modifier = Modifier.size(if (isExpanded) 14.dp else 12.dp)
                            )
                            if (isExpanded) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${workout.durationMinutes}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    // Show imported logs (with slightly different styling to distinguish)
                    if (includeImported) {
                        importedLogs.forEach { log ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = log.type.toIcon(),
                                    contentDescription = null,
                                    tint = log.type.toColor().copy(alpha = 0.7f),
                                    modifier = Modifier.size(if (isExpanded) 12.dp else 10.dp)
                                )
                                if (isExpanded) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${log.durationMinutes}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    
                    if (!isExpanded) {
                        val totalDuration = day.workouts.sumOf { it.durationMinutes } + 
                            if (includeImported) importedLogs.sumOf { it.durationMinutes } else 0
                        Text(
                            text = "$totalDuration",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold
                        )
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
            
            // Special Period Icons
            if (day.specialPeriods.isNotEmpty()) {
                val period = day.specialPeriods.first()
                Text(
                    text = if (period.type == SpecialPeriodType.HOLIDAY) "🏖️" else "🩹",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                )
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

private fun WorkoutType.toIcon(): ImageVector {
    return when (this) {
        WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutType.BIKE -> Icons.Default.PedalBike
        WorkoutType.SWIM -> Icons.Default.Pool
        WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
        WorkoutType.OTHER -> Icons.AutoMirrored.Filled.DirectionsWalk
    }
}

@Composable
fun MatrixNavigationHeader(
    startDate: LocalDate,
    weekCount: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val endDate = startDate.plusWeeks(weekCount.toLong()).minusDays(1)
    val startMonth = startDate.format(DateTimeFormatter.ofPattern("MMMM"))
    val endMonth = endDate.format(DateTimeFormatter.ofPattern("MMMM"))
    val year = startDate.year
    
    val dateRangeText = if (startMonth == endMonth) {
        "$startMonth $year"
    } else {
        "$startMonth - $endMonth $year"
    }
    
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
