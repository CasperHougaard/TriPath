package com.tripath.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Local state for form fields
    var ftpBike by remember { mutableStateOf("") }
    var maxHeartRate by remember { mutableStateOf("") }
    var lthr by remember { mutableStateOf("") }
    var cssTimeString by remember { mutableStateOf("") }
    var goalDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Initialize form fields from profile when loaded
    LaunchedEffect(uiState.userProfile) {
        uiState.userProfile?.let { profile ->
            ftpBike = profile.ftpBike?.toString() ?: ""
            maxHeartRate = profile.maxHeartRate?.toString() ?: ""
            lthr = profile.lthr?.toString() ?: ""
            cssTimeString = viewModel.formatSecondsToCssTime(profile.cssSecondsper100m) ?: ""
            goalDate = profile.goalDate
        }
    }

    // Show snackbar messages
    LaunchedEffect(uiState.profileSaveSuccess, uiState.profileSaveError) {
        when {
            uiState.profileSaveSuccess -> {
                snackbarHostState.showSnackbar("Profile saved successfully")
                viewModel.clearMessages()
            }
            uiState.profileSaveError != null -> {
                snackbarHostState.showSnackbar(uiState.profileSaveError ?: "Error saving profile")
                viewModel.clearMessages()
            }
        }
    }

    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "Physiological Metrics",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )

            Text(
                text = "Enter your training thresholds and goals",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            // FTP (Bike) field
            OutlinedTextField(
                value = ftpBike,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        ftpBike = newValue
                    }
                },
                label = { Text("FTP (Bike)") },
                placeholder = { Text("Watts") },
                supportingText = { Text("Functional Threshold Power for cycling") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = ftpBike.isNotEmpty() && ftpBike.toIntOrNull()?.let { it < 0 } == true,
                trailingIcon = {
                    if (ftpBike.isNotEmpty() && ftpBike.toIntOrNull()?.let { it < 0 } == true) {
                        Text(
                            "Must be ≥ 0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            // Max HR field
            OutlinedTextField(
                value = maxHeartRate,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        maxHeartRate = newValue
                    }
                },
                label = { Text("Max HR") },
                placeholder = { Text("bpm") },
                supportingText = { Text("Maximum Heart Rate for TSS calculations") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = maxHeartRate.isNotEmpty() && maxHeartRate.toIntOrNull()?.let { it < 0 } == true,
                trailingIcon = {
                    if (maxHeartRate.isNotEmpty() && maxHeartRate.toIntOrNull()?.let { it < 0 } == true) {
                        Text(
                            "Must be ≥ 0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            // LTHR (Run) field
            OutlinedTextField(
                value = lthr,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        lthr = newValue
                    }
                },
                label = { Text("LTHR (Run)") },
                placeholder = { Text("bpm") },
                supportingText = { Text("Lactate Threshold Heart Rate for running") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = lthr.isNotEmpty() && lthr.toIntOrNull()?.let { it < 0 } == true,
                trailingIcon = {
                    if (lthr.isNotEmpty() && lthr.toIntOrNull()?.let { it < 0 } == true) {
                        Text(
                            "Must be ≥ 0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            // CSS (Swim) field - MM:SS format
            val cssError = cssTimeString.isNotEmpty() && 
                cssTimeString.isNotBlank() && 
                viewModel.parseCssTimeToSeconds(cssTimeString) == null
            OutlinedTextField(
                value = cssTimeString,
                onValueChange = { newValue ->
                    cssTimeString = newValue
                },
                label = { Text("CSS (Swim)") },
                placeholder = { Text("1:45") },
                supportingText = {
                    if (cssError) {
                        Text(
                            "Format: MM:SS (e.g., 1:45 for 1 minute 45 seconds)",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Critical Swim Speed per 100m (MM:SS format)")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = cssError
            )

            // Goal Date field
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = goalDate?.atStartOfDay(
                    java.time.ZoneId.systemDefault()
                )?.toInstant()?.toEpochMilli(),
                yearRange = IntRange(2024, 2030)
            )

            OutlinedTextField(
                value = goalDate?.format(dateFormatter) ?: "",
                onValueChange = { },
                label = { Text("Goal Date") },
                placeholder = { Text("Select target Ironman race date") },
                supportingText = { Text("Target Ironman race date (2027 goal)") },
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(if (goalDate == null) "Select" else "Change")
                    }
                }
            )

            // Date Picker Dialog
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    goalDate = java.time.Instant.ofEpochMilli(millis)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate()
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Save Button
            Button(
                onClick = {
                    val ftp = ftpBike.toIntOrNull()
                    val maxHr = maxHeartRate.toIntOrNull()
                    val lthrValue = lthr.toIntOrNull()
                    viewModel.saveUserProfile(
                        ftpBike = ftp,
                        maxHeartRate = maxHr,
                        lthr = lthrValue,
                        cssTimeString = cssTimeString.takeIf { it.isNotBlank() },
                        goalDate = goalDate
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoadingProfile
            ) {
                if (uiState.isLoadingProfile) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = Spacing.sm)
                            .height(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("Save Profile")
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileEditorScreenPreview() {
    TriPathTheme {
        ProfileEditorScreen()
    }
}

