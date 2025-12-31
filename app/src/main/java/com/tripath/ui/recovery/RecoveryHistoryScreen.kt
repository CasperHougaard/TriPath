package com.tripath.ui.recovery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.TaskTriggerType
import com.tripath.ui.recovery.RecoveryTimeRange
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun RecoveryHistoryScreen(
    navController: NavController,
    viewModel: RecoveryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val timeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    
    var showReadinessInfo by remember { mutableStateOf(false) }
    var showBiologicalCostInfo by remember { mutableStateOf(false) }
    var showSleepScoreInfo by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Recovery Trends",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        // Time Range Selector
        @OptIn(ExperimentalMaterial3Api::class)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            RecoveryTimeRange.values().forEachIndexed { index, range ->
                SegmentedButton(
                    selected = timeRange == range,
                    onClick = { viewModel.setTimeRange(range) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = RecoveryTimeRange.values().size
                    ),
                    label = {
                        Text(
                            text = range.name.lowercase().replaceFirstChar { it.uppercase() }
                        )
                    }
                )
            }
        }

        // Chart 1: Readiness vs Load
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Readiness vs Load",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showReadinessInfo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                ReadinessVsLoadChart(
                    historyData = historyState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
                // Legend
                ReadinessChartLegend()
            }
        }

        // Chart 2: Biological Cost
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Biological Cost",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showBiologicalCostInfo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                BiologicalCostChart(
                    historyData = historyState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
                // Legend
                BiologicalCostLegend()
            }
        }

        // Chart 3: Weight Trend
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    text = "Weight Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                WeightTrendChart(
                    historyData = historyState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }

        // Chart 4: Sleep Score Trend
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sleep Score Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showSleepScoreInfo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                SleepScoreChart(
                    historyData = historyState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }

        // Habit Consistency Grid
        HabitConsistencyCard(
            historyState = historyState,
            viewModel = viewModel,
            timeRange = timeRange
        )

        Spacer(modifier = Modifier.height(Spacing.xl))
    }

    // Info Dialogs
    if (showReadinessInfo) {
        AlertDialog(
            onDismissRequest = { showReadinessInfo = false },
            title = { Text("Readiness vs Load") },
            text = {
                Text("Correlates your subjective Mood & Soreness against objective Training Stress (TSS).")
            },
            confirmButton = {
                TextButton(onClick = { showReadinessInfo = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showBiologicalCostInfo) {
        AlertDialog(
            onDismissRequest = { showBiologicalCostInfo = false },
            title = { Text("Biological Cost") },
            text = {
                Text("Tracks systemic stress factors like Allergy Severity. High cost = reduced training capacity.")
            },
            confirmButton = {
                TextButton(onClick = { showBiologicalCostInfo = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showSleepScoreInfo) {
        AlertDialog(
            onDismissRequest = { showSleepScoreInfo = false },
            title = { Text("Sleep Score") },
            text = {
                Text(
                    "Sleep Score (1-100) based on duration, stage quality, efficiency, and awakenings. " +
                    "For strength training, deep sleep is weighted higher for muscle recovery. " +
                    "Scores are extracted from Garmin when available, or calculated from sleep data."
                )
            },
            confirmButton = {
                TextButton(onClick = { showSleepScoreInfo = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ReadinessChartLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mood Line
        LegendItem(
            color = Color(0xFF00B8FF),
            label = "Mood",
            isLine = true
        )
        // Soreness Line
        LegendItem(
            color = Color(0xFFFF6B35),
            label = "Soreness",
            isLine = true
        )
        // TSS Bar
        LegendItem(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            label = "TSS Load",
            isLine = false
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isLine: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (isLine) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(color = color)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color = color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun BiologicalCostLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendColorItem(color = Color(0xFF00FF00), label = "None")
        LegendColorItem(color = Color(0xFFFFFF00), label = "Mild")
        LegendColorItem(color = Color(0xFFFFA500), label = "Mod")
        LegendColorItem(color = Color(0xFFFF0000), label = "Severe")
    }
}

@Composable
private fun LegendColorItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color = color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun HabitConsistencyCard(
    historyState: List<RecoveryHistoryDay>,
    viewModel: RecoveryViewModel,
    timeRange: RecoveryTimeRange,
    modifier: Modifier = Modifier
) {
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val dailyTasks = allTasks.filter { it.type == TaskTriggerType.DAILY }
    
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
                text = "Habit Consistency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (dailyTasks.isEmpty()) {
                Text(
                    text = "No daily tasks defined",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else if (historyState.isEmpty()) {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                // For YEAR range, show simplified compliance % instead of dots
                if (timeRange == RecoveryTimeRange.YEAR) {
                    HabitConsistencyYearView(
                        historyState = historyState,
                        dailyTasks = dailyTasks
                    )
                } else {
                    HabitConsistencyGridView(
                        historyState = historyState,
                        dailyTasks = dailyTasks
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitConsistencyGridView(
    historyState: List<RecoveryHistoryDay>,
    dailyTasks: List<WellnessTaskDefinition>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        dailyTasks.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(80.dp)
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    historyState.forEach { day ->
                        val isCompleted = day.wellnessLog?.completedTaskIds?.contains(task.id) == true
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    color = if (isCompleted) Color.Green else Color.LightGray
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitConsistencyYearView(
    historyState: List<RecoveryHistoryDay>,
    dailyTasks: List<WellnessTaskDefinition>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        dailyTasks.forEach { task ->
            val totalDays = historyState.size
            val completedDays = historyState.count { day ->
                day.wellnessLog?.completedTaskIds?.contains(task.id) == true
            }
            val compliancePercent = if (totalDays > 0) {
                (completedDays.toFloat() / totalDays.toFloat() * 100f).toInt()
            } else 0
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "$compliancePercent%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ReadinessVsLoadChart(
    historyData: List<RecoveryHistoryDay>,
    modifier: Modifier = Modifier
) {
    if (historyData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val tssValues = historyData.map { it.dailyTss.toFloat() }
    val sorenessValues = historyData.mapNotNull { it.wellnessLog?.sorenessIndex?.toFloat() }
    val moodValues = historyData.mapNotNull { it.wellnessLog?.moodIndex?.toFloat() }

    val maxTss = maxOf(tssValues.maxOrNull() ?: 100f, 100f)
    val maxSoreness = sorenessValues.maxOrNull() ?: 10f
    val maxMood = moodValues.maxOrNull() ?: 10f

    val textMeasurer = rememberTextMeasurer()
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val tssColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val sorenessColor = Color(0xFFFF6B35) // Safety Orange
    val moodColor = Color(0xFF00B8FF) // Electric Blue

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 50.dp.toPx()
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2
            val chartLeft = padding
            val chartTop = padding
            val chartBottom = chartTop + chartHeight

            // Draw grid lines
            for (i in 0..4) {
                val y = chartTop + (chartHeight / 4) * i
                drawLine(
                    color = gridLineColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartLeft + chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw Y-axis labels (TSS on left)
            val yAxisTextStyle = TextStyle(color = textColor, fontSize = 10.sp)
            for (i in 0..4) {
                val value = maxTss - (maxTss / 4) * i
                val y = chartTop + (chartHeight / 4) * i
                val text = value.toInt().toString()
                val textLayoutResult = textMeasurer.measure(text, yAxisTextStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        chartLeft - textLayoutResult.size.width - 8.dp.toPx(),
                        y - textLayoutResult.size.height / 2
                    )
                )
            }

            // Draw Y-axis labels (Soreness/Mood on right)
            for (i in 0..4) {
                val value = 10 - (10f / 4) * i
                val y = chartTop + (chartHeight / 4) * i
                val text = value.toInt().toString()
                val textLayoutResult = textMeasurer.measure(text, yAxisTextStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        chartLeft + chartWidth + 8.dp.toPx(),
                        y - textLayoutResult.size.height / 2
                    )
                )
            }

            val barWidth = (chartWidth / historyData.size) * 0.7f
            val spacing = (chartWidth / historyData.size) * 0.3f

            // Draw TSS bars
            historyData.forEachIndexed { index, day ->
                val x = chartLeft + (chartWidth / historyData.size) * index + spacing / 2
                val barHeight = (day.dailyTss / maxTss) * chartHeight
                drawRect(
                    color = tssColor,
                    topLeft = Offset(x, chartBottom - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
            }

            // Draw Soreness line
            val sorenessPoints = mutableListOf<Offset>()
            historyData.forEachIndexed { index, day ->
                day.wellnessLog?.sorenessIndex?.let { soreness ->
                    val x = chartLeft + (chartWidth / historyData.size) * index + (chartWidth / historyData.size) / 2
                    val normalizedSoreness = (soreness / maxSoreness).coerceIn(0f, 1f)
                    val y = chartBottom - (chartHeight * normalizedSoreness)
                    sorenessPoints.add(Offset(x, y))
                }
            }
            if (sorenessPoints.isNotEmpty()) {
                val sorenessPath = Path()
                sorenessPath.moveTo(sorenessPoints[0].x, sorenessPoints[0].y)
                sorenessPoints.drop(1).forEach { point ->
                    sorenessPath.lineTo(point.x, point.y)
                }
                drawPath(
                    path = sorenessPath,
                    color = sorenessColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw Mood line
            val moodPoints = mutableListOf<Offset>()
            historyData.forEachIndexed { index, day ->
                day.wellnessLog?.moodIndex?.let { mood ->
                    val x = chartLeft + (chartWidth / historyData.size) * index + (chartWidth / historyData.size) / 2
                    val normalizedMood = (mood / maxMood).coerceIn(0f, 1f)
                    val y = chartBottom - (chartHeight * normalizedMood)
                    moodPoints.add(Offset(x, y))
                }
            }
            if (moodPoints.isNotEmpty()) {
                val moodPath = Path()
                moodPath.moveTo(moodPoints[0].x, moodPoints[0].y)
                moodPoints.drop(1).forEach { point ->
                    moodPath.lineTo(point.x, point.y)
                }
                drawPath(
                    path = moodPath,
                    color = moodColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw X-axis labels (every 5 days for week/month, more sparse for year)
            val xAxisTextStyle = TextStyle(color = textColor, fontSize = 9.sp)
            val labelInterval = when {
                historyData.size <= 7 -> 1 // Week: label every day
                historyData.size <= 31 -> 5 // Month: every 5 days
                else -> maxOf(1, historyData.size / 12) // Year: ~12 labels
            }
            historyData.forEachIndexed { index, day ->
                if (index % labelInterval == 0 || index == historyData.size - 1) {
                    val x = chartLeft + (chartWidth / historyData.size) * index + (chartWidth / historyData.size) / 2
                    val formatter = when {
                        historyData.size <= 7 -> DateTimeFormatter.ofPattern("EEE")
                        historyData.size <= 31 -> DateTimeFormatter.ofPattern("MMM d")
                        else -> DateTimeFormatter.ofPattern("MMM")
                    }
                    val text = day.date.format(formatter)
                    val textLayoutResult = textMeasurer.measure(text, xAxisTextStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x - textLayoutResult.size.width / 2,
                            chartBottom + 8.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BiologicalCostChart(
    historyData: List<RecoveryHistoryDay>,
    modifier: Modifier = Modifier
) {
    if (historyData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 10.dp.toPx()
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2
            val chartLeft = padding
            val chartTop = padding
            val barHeight = chartHeight
            val barWidth = chartWidth / historyData.size

            historyData.forEachIndexed { index, day ->
                val x = chartLeft + barWidth * index
                val color = when (day.wellnessLog?.allergySeverity) {
                    AllergySeverity.SEVERE -> Color(0xFFFF0000) // Red
                    AllergySeverity.MODERATE -> Color(0xFFFFA500) // Orange
                    AllergySeverity.MILD -> Color(0xFFFFFF00) // Yellow
                    AllergySeverity.NONE, null -> Color(0xFF00FF00) // Green
                }
                drawRect(
                    color = color,
                    topLeft = Offset(x, chartTop),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
            }
        }
    }
}

@Composable
private fun WeightTrendChart(
    historyData: List<RecoveryHistoryDay>,
    modifier: Modifier = Modifier
) {
    val weightValues = historyData.mapNotNull { it.wellnessLog?.morningWeight }

    if (weightValues.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No weight data available",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val minWeight = weightValues.minOrNull() ?: 60.0
    val maxWeight = weightValues.maxOrNull() ?: 100.0
    val weightRange = maxOf(maxWeight - minWeight, 5.0) // Ensure minimum range of 5kg
    val chartMin = minWeight - weightRange * 0.1
    val chartMax = maxWeight + weightRange * 0.1
    val chartRange = chartMax - chartMin

    val textMeasurer = rememberTextMeasurer()
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val weightColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 50.dp.toPx()
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2
            val chartLeft = padding
            val chartTop = padding
            val chartBottom = chartTop + chartHeight

            // Draw grid lines
            for (i in 0..4) {
                val y = chartTop + (chartHeight / 4) * i
                drawLine(
                    color = gridLineColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartLeft + chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw Y-axis labels
            val yAxisTextStyle = TextStyle(color = textColor, fontSize = 10.sp)
            for (i in 0..4) {
                val value = chartMax - (chartRange / 4) * i
                val y = chartTop + (chartHeight / 4) * i
                val text = String.format("%.1f", value)
                val textLayoutResult = textMeasurer.measure(text, yAxisTextStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        chartLeft - textLayoutResult.size.width - 8.dp.toPx(),
                        y - textLayoutResult.size.height / 2
                    )
                )
            }

            // Draw weight line
            val weightPoints = mutableListOf<Offset>()
            historyData.forEachIndexed { index, day ->
                day.wellnessLog?.morningWeight?.let { weight ->
                    val x = chartLeft + (chartWidth / (historyData.size - 1).coerceAtLeast(1)) * index
                    val normalizedWeight = ((weight - chartMin) / chartRange).coerceIn(0.0, 1.0)
                    val y = chartBottom - (chartHeight * normalizedWeight.toFloat())
                    weightPoints.add(Offset(x, y))
                }
            }
            if (weightPoints.isNotEmpty()) {
                val weightPath = Path()
                weightPath.moveTo(weightPoints[0].x, weightPoints[0].y)
                weightPoints.drop(1).forEach { point ->
                    weightPath.lineTo(point.x, point.y)
                }
                drawPath(
                    path = weightPath,
                    color = weightColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw X-axis labels
            val xAxisTextStyle = TextStyle(color = textColor, fontSize = 9.sp)
            val labelInterval = when {
                historyData.size <= 7 -> 1
                historyData.size <= 31 -> 5
                else -> maxOf(1, historyData.size / 12)
            }
            historyData.forEachIndexed { index, day ->
                if (index % labelInterval == 0 || index == historyData.size - 1) {
                    val x = chartLeft + (chartWidth / (historyData.size - 1).coerceAtLeast(1)) * index
                    val formatter = when {
                        historyData.size <= 7 -> DateTimeFormatter.ofPattern("EEE")
                        historyData.size <= 31 -> DateTimeFormatter.ofPattern("MMM d")
                        else -> DateTimeFormatter.ofPattern("MMM")
                    }
                    val text = day.date.format(formatter)
                    val textLayoutResult = textMeasurer.measure(text, xAxisTextStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x - textLayoutResult.size.width / 2,
                            chartBottom + 8.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepScoreChart(
    historyData: List<RecoveryHistoryDay>,
    modifier: Modifier = Modifier
) {
    val sleepScores = historyData.mapNotNull { it.sleepLog?.sleepScore?.toFloat() }

    if (sleepScores.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No sleep data available",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val sleepScoreColor = Color(0xFF6B9BD2) // Soft blue

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 50.dp.toPx()
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2
            val chartLeft = padding
            val chartTop = padding
            val chartBottom = chartTop + chartHeight

            // Draw grid lines
            for (i in 0..4) {
                val y = chartTop + (chartHeight / 4) * i
                drawLine(
                    color = gridLineColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartLeft + chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw Y-axis labels (0-100 scale)
            val yAxisTextStyle = TextStyle(color = textColor, fontSize = 10.sp)
            for (i in 0..4) {
                val value = 100 - (100 / 4) * i
                val y = chartTop + (chartHeight / 4) * i
                val text = value.toString()
                val textLayoutResult = textMeasurer.measure(text, yAxisTextStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        chartLeft - textLayoutResult.size.width - 8.dp.toPx(),
                        y - textLayoutResult.size.height / 2
                    )
                )
            }

            // Draw sleep score line
            val sleepPoints = mutableListOf<Offset>()
            historyData.forEachIndexed { index, day ->
                day.sleepLog?.sleepScore?.let { score ->
                    val x = chartLeft + (chartWidth / (historyData.size - 1).coerceAtLeast(1)) * index
                    val normalizedScore = (score / 100f).coerceIn(0f, 1f)
                    val y = chartBottom - (chartHeight * normalizedScore)
                    sleepPoints.add(Offset(x, y))
                }
            }
            
            if (sleepPoints.isNotEmpty()) {
                val sleepPath = Path()
                sleepPath.moveTo(sleepPoints[0].x, sleepPoints[0].y)
                sleepPoints.drop(1).forEach { point ->
                    sleepPath.lineTo(point.x, point.y)
                }
                drawPath(
                    path = sleepPath,
                    color = sleepScoreColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                // Draw data points (small circles)
                sleepPoints.forEach { point ->
                    drawCircle(
                        color = sleepScoreColor,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                }
            }

            // Draw X-axis labels
            val xAxisTextStyle = TextStyle(color = textColor, fontSize = 9.sp)
            val labelInterval = when {
                historyData.size <= 7 -> 1
                historyData.size <= 31 -> 5
                else -> maxOf(1, historyData.size / 12)
            }
            historyData.forEachIndexed { index, day ->
                if (index % labelInterval == 0 || index == historyData.size - 1) {
                    val x = chartLeft + (chartWidth / (historyData.size - 1).coerceAtLeast(1)) * index
                    val formatter = when {
                        historyData.size <= 7 -> DateTimeFormatter.ofPattern("EEE")
                        historyData.size <= 31 -> DateTimeFormatter.ofPattern("MMM d")
                        else -> DateTimeFormatter.ofPattern("MMM")
                    }
                    val text = day.date.format(formatter)
                    val textLayoutResult = textMeasurer.measure(text, xAxisTextStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x - textLayoutResult.size.width / 2,
                            chartBottom + 8.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveryHistoryScreenPreview() {
    TriPathTheme {
        // Preview with mock data
    }
}
