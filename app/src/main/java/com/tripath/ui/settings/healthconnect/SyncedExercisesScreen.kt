package com.tripath.ui.settings.healthconnect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.Spacing
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncedExercisesScreen(
    onNavigateBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    viewModel: SyncedExercisesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Synced Health Data")
                        val ignored = uiState.ignoredCount
                        Text(
                            text = if (ignored > 0)
                                "${uiState.dataPoints.size} items · $ignored ignored"
                            else
                                "${uiState.dataPoints.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.dataPoints.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        "No synced data yet. Sync from Health Connect to see your exercises, sleep and body measurements here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        Text(
                            text = "Everything synced from Health Connect. Turn a data point off to exclude it from analytics, training load and charts — the record is kept and can be turned back on any time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = Spacing.md)
                        )
                    }
                    items(uiState.dataPoints, key = { it.id }) { point ->
                        SyncedDataRow(
                            point = point,
                            onToggleIgnored = { viewModel.setIgnored(point, !point.isIgnored) },
                            onClick = if (point is SyncedDataPoint.Exercise) {
                                { onExerciseClick(point.id) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

private val DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val TIME_FORMATTER = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
private fun SyncedDataRow(
    point: SyncedDataPoint,
    onToggleIgnored: () -> Unit,
    onClick: (() -> Unit)?
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .alpha(if (point.isIgnored) 0.45f else 1f)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = if (point.isIgnored)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.title(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = point.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (point.isIgnored) {
                    Text(
                        text = "Excluded from analytics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val metric = point.metric()
            if (metric != null) {
                Text(
                    text = metric,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Switch(
                checked = !point.isIgnored,
                onCheckedChange = { onToggleIgnored() }
            )
        }
    }
}

// ---- Row content helpers -------------------------------------------------

private fun SyncedDataPoint.title(): String = when (this) {
    is SyncedDataPoint.Exercise -> exerciseName(type)
    is SyncedDataPoint.Sleep -> "Sleep"
    is SyncedDataPoint.Body -> "Body measurement"
}

private fun SyncedDataPoint.subtitle(): String {
    val zoned = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault())
    val date = zoned.format(DATE_FORMATTER)
    return when (this) {
        // WorkoutLog only stores the date, so time-of-day would be meaningless.
        is SyncedDataPoint.Exercise -> date
        else -> "$date at ${zoned.format(TIME_FORMATTER)}"
    }
}

private fun SyncedDataPoint.metric(): String? = when (this) {
    is SyncedDataPoint.Exercise -> formatDuration(durationMinutes)
    is SyncedDataPoint.Sleep -> formatDuration(durationMinutes)
    is SyncedDataPoint.Body -> log.weightKg?.let { "%.1f kg".format(it) }
        ?: log.bodyFatPercent?.let { "%.1f%%".format(it) }
}

private fun formatDuration(totalMinutes: Int): String {
    val duration = Duration.ofMinutes(totalMinutes.toLong())
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun exerciseName(type: WorkoutType): String = when (type) {
    WorkoutType.RUN -> "Running"
    WorkoutType.BIKE -> "Cycling"
    WorkoutType.SWIM -> "Swimming"
    WorkoutType.STRENGTH -> "Strength Training"
    WorkoutType.WALK -> "Walking"
    WorkoutType.HIKE -> "Hiking"
    WorkoutType.OTHER -> "Other Activity"
}
