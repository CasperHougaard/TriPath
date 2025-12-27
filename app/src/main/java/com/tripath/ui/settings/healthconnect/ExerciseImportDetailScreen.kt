package com.tripath.ui.settings.healthconnect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseImportDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    viewModel: ExerciseImportDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        viewModel.loadDetails(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Details") },
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
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.exercise != null) {
                val exercise = uiState.exercise!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    DetailSection("Session Metadata") {
                        MetadataRow("ID", exercise.metadata.id)
                        MetadataRow("Client ID", exercise.metadata.clientRecordId ?: "N/A")
                        MetadataRow("Last Modified", exercise.metadata.lastModifiedTime.toString())
                        MetadataRow("Data Origin", exercise.metadata.dataOrigin.packageName)
                    }

                    DetailSection("Exercise Info") {
                        MetadataRow("Title", exercise.title ?: "N/A")
                        MetadataRow("Type", getExerciseName(exercise.exerciseType))
                        MetadataRow("Start", exercise.startTime.atZone(ZoneId.systemDefault()).toString())
                        MetadataRow("End", exercise.endTime.atZone(ZoneId.systemDefault()).toString())
                        MetadataRow("Duration", java.time.Duration.between(exercise.startTime, exercise.endTime).toString())
                    }

                    DetailSection("Imported Raw Data") {
                        uiState.rawData.forEach { (key, value) ->
                            RawDataRow(key, value)
                        }
                    }

                    DetailSection("GPX/Route Data") {
                        val routeInfo = uiState.routeData
                        if (routeInfo != null) {
                            if (routeInfo.hasRoute) {
                                MetadataRow("Status", "✓ Available")
                                MetadataRow("GPS Points", "${routeInfo.pointCount} points")
                                if (routeInfo.routeJson != null) {
                                    MetadataRow("Data Size", "${routeInfo.routeJson.length / 1024} KB")
                                    MetadataRow("Storage", "Stored locally")
                                } else {
                                    MetadataRow("Storage", "Available in Health Connect (not stored)")
                                }
                            } else {
                                MetadataRow("Status", "No route data")
                                MetadataRow("Note", "Source app may not provide GPS route data to Health Connect")
                            }
                        } else {
                            MetadataRow("Status", "Checking...")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.xs)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun RawDataRow(label: String, value: Any?) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        val valueString = when (value) {
            is List<*> -> {
                if (value.isEmpty()) "Empty List"
                else "${value.size} samples"
            }
            null -> "No data"
            else -> value.toString()
        }
        Text(
            text = valueString,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // If it's a list, maybe show a bit more detail
        if (value is List<*> && value.isNotEmpty()) {
            Text(
                text = "First few samples: " + value.take(3).joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun getExerciseName(exerciseType: Int): String {
    return when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill Running"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Biking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Stationary Biking"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open Water Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        else -> "Exercise (Type $exerciseType)"
    }
}

