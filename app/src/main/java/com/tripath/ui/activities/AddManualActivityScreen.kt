package com.tripath.ui.activities

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualActivityScreen(
    prefillDate: LocalDate = LocalDate.now(),
    onNavigateBack: () -> Unit,
    viewModel: AddManualActivityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(prefillDate) {
        viewModel.initDate(prefillDate)
    }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Activity") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                SectionLabel("Date")
                Card(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.md)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                SectionLabel("Discipline")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // 3 chips on first row, 2 on second
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        disciplineOptions.take(3).forEach { (type, icon, label) ->
                            DisciplineChip(
                                type = type,
                                icon = icon,
                                label = label,
                                selected = uiState.type == type,
                                onClick = { viewModel.setType(type) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        disciplineOptions.drop(3).forEach { (type, icon, label) ->
                            DisciplineChip(
                                type = type,
                                icon = icon,
                                label = label,
                                selected = uiState.type == type,
                                onClick = { viewModel.setType(type) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Spacer to keep chips same width as top row
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionLabel("Duration")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    DurationField(
                        value = uiState.durationHours,
                        label = "Hours",
                        range = 0..23,
                        onValueChange = viewModel::setDurationHours,
                        modifier = Modifier.weight(1f)
                    )
                    DurationField(
                        value = uiState.durationMinutes,
                        label = "Minutes",
                        range = 0..59,
                        onValueChange = viewModel::setDurationMinutes,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionLabel("Training Zone")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // 3 chips on first row, 2 on second
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        zoneOptions.take(3).forEach { (zone, label, color) ->
                            ZoneChip(
                                zone = zone,
                                label = label,
                                color = color,
                                selected = uiState.zone == zone,
                                onClick = { viewModel.setZone(zone) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        zoneOptions.drop(3).forEach { (zone, label, color) ->
                            ZoneChip(
                                zone = zone,
                                label = label,
                                color = color,
                                selected = uiState.zone == zone,
                                onClick = { viewModel.setZone(zone) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Spacer to keep chips same width as top row
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(Spacing.md)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Estimated TSS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${uiState.computedTss}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                uiState.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                val totalMinutes = uiState.durationHours * 60 + uiState.durationMinutes
                Button(
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving && totalMinutes > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Log Activity")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        viewModel.setDate(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = Spacing.xs)
    )
}

@Composable
private fun DurationField(
    value: Int,
    label: String,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { raw ->
            val parsed = raw.trimStart('0').toIntOrNull() ?: 0
            onValueChange(parsed.coerceIn(range))
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(Spacing.md)
    )
}

@Composable
private fun ZoneChip(
    zone: Int,
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.25f),
            selectedLabelColor = color
        )
    )
}

@Composable
private fun DisciplineChip(
    type: WorkoutType,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = type.toColor().copy(alpha = 0.2f),
            selectedLabelColor = type.toColor(),
            selectedLeadingIconColor = type.toColor()
        )
    )
}

private data class DisciplineOption(val type: WorkoutType, val icon: ImageVector, val label: String)

private val disciplineOptions = listOf(
    DisciplineOption(WorkoutType.RUN, Icons.AutoMirrored.Filled.DirectionsRun, "Run"),
    DisciplineOption(WorkoutType.BIKE, Icons.AutoMirrored.Filled.DirectionsBike, "Bike"),
    DisciplineOption(WorkoutType.SWIM, Icons.Default.Pool, "Swim"),
    DisciplineOption(WorkoutType.STRENGTH, Icons.Default.FitnessCenter, "Strength"),
    DisciplineOption(WorkoutType.OTHER, Icons.AutoMirrored.Filled.DirectionsWalk, "Other")
)

private data class ZoneOption(val zone: Int, val label: String, val color: Color)

private val zoneOptions = listOf(
    ZoneOption(1, "Z1 Recovery",   Color(0xFF43A047)),
    ZoneOption(2, "Z2 Endurance",  Color(0xFF7CB342)),
    ZoneOption(3, "Z3 Tempo",      Color(0xFFFDD835)),
    ZoneOption(4, "Z4 Threshold",  Color(0xFFEF6C00)),
    ZoneOption(5, "Z5 Max",        Color(0xFFD32F2F))
)
