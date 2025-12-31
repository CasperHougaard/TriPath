package com.tripath.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tripath.data.model.UserProfile
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun SettingsScreen(
    navController: NavHostController? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val exportJsonState = remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { _: Set<String> ->
        viewModel.onPermissionsResult()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportUri ->
            exportJsonState.value?.let { jsonString ->
                coroutineScope.launch {
                    try {
                        writeJsonToUri(context, exportUri, jsonString)
                        snackbarHostState.showSnackbar("Data exported successfully")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to save file: ${e.message}")
                    }
                }
            }
        }
        exportJsonState.value = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importUri ->
            coroutineScope.launch {
                try {
                    val jsonString = readJsonFromUri(context, importUri)
                    viewModel.importData(jsonString)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to read file: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHealthConnectStatus()
    }

    LaunchedEffect(uiState.importSuccess, uiState.resetSuccess, uiState.errorMessage, uiState.lastSyncResult) {
        when {
            uiState.resetSuccess -> {
                snackbarHostState.showSnackbar("All data has been reset")
                viewModel.clearMessages()
            }
            uiState.importSuccess -> {
                val summary = uiState.importSummary
                val message = summary?.let {
                    buildString {
                        append("Imported ${it.trainingPlansImported} plans, ${it.workoutLogsImported} logs")
                        if (it.specialPeriodsImported > 0) {
                            append(", ${it.specialPeriodsImported} special periods")
                        }
                        if (it.wellnessLogsImported > 0) {
                            append(", ${it.wellnessLogsImported} wellness logs")
                        }
                        if (it.wellnessTasksImported > 0) {
                            append(", ${it.wellnessTasksImported} wellness tasks")
                        }
                        if (it.profileImported) {
                            append(", and profile")
                        }
                    }
                } ?: "Data imported successfully"
                snackbarHostState.showSnackbar(message)
                viewModel.clearMessages()
            }
            uiState.errorMessage != null -> {
                snackbarHostState.showSnackbar(uiState.errorMessage ?: "An error occurred")
                viewModel.clearMessages()
            }
            uiState.lastSyncResult != null -> {
                snackbarHostState.showSnackbar(uiState.lastSyncResult ?: "")
                viewModel.clearMessages()
            }
        }
    }

    fun handleExportClick() {
        coroutineScope.launch {
            val result = viewModel.exportData()
            result.fold(
                onSuccess = { jsonString ->
                    exportJsonState.value = jsonString
                    exportLauncher.launch("tripath_backup.json")
                },
                onFailure = { }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                SectionHeader(
                    title = "Settings",
                    subtitle = "Customize your experience"
                )

                // User Profile Section
                SectionHeader(
                    title = "User Profile",
                    subtitle = "Physiological metrics and goals"
                )

                ProfileCard(
                    profile = uiState.userProfile,
                    onEditClick = {
                        navController?.navigate(com.tripath.ui.navigation.Screen.ProfileEditor.route)
                    }
                )

                // Appearance Section
                SectionHeader(title = "Appearance")

                SettingsToggleCard(
                    icon = if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    title = "Dark Theme",
                    subtitle = if (uiState.isDarkTheme) "Enabled" else "Disabled",
                    checked = uiState.isDarkTheme,
                    onCheckedChange = { viewModel.toggleTheme() }
                )

                // Health Connect Section
                SectionHeader(
                    title = "Health Connect",
                    subtitle = "Sync workouts from wearables"
                )

                HealthConnectCard(
                    status = uiState.healthConnectStatus,
                    isSyncing = uiState.isSyncing,
                    syncDaysBack = uiState.syncDaysBack,
                    syncedWorkoutsCount = uiState.syncedWorkouts.size,
                    onSyncDaysChange = { viewModel.setSyncDays(it) },
                    onConnectClick = {
                        healthConnectPermissionLauncher.launch(viewModel.healthConnectPermissions)
                    },
                    onSyncClick = { viewModel.syncHealthConnect() },
                    onReprocessClick = { viewModel.reprocessWorkouts() },
                    onViewSyncedClick = {
                        navController?.navigate(com.tripath.ui.navigation.Screen.SyncedExercises.route)
                    }
                )

                // Backup Section
                SectionHeader(
                    title = "Backup & Restore",
                    subtitle = "Export and import your data"
                )

                BackupCard(
                    isLoading = uiState.isLoading,
                    onExportClick = { handleExportClick() },
                    onImportClick = { importLauncher.launch(arrayOf("application/json")) },
                    onResetClick = { showResetDialog = true }
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }

        // Reset confirmation dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset All Data") },
                text = {
                    Text("Are you sure you want to delete all data? This will permanently delete all training plans, workout logs, special periods, and your user profile. This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            viewModel.resetData()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private val syncPeriodOptions = listOf(
    7 to "7 days",
    14 to "14 days",
    30 to "30 days",
    60 to "60 days (max)"
)

@Composable
private fun HealthConnectCard(
    status: HealthConnectStatus,
    isSyncing: Boolean,
    syncDaysBack: Int,
    syncedWorkoutsCount: Int,
    onSyncDaysChange: (Int) -> Unit,
    onConnectClick: () -> Unit,
    onSyncClick: () -> Unit,
    onReprocessClick: () -> Unit,
    onViewSyncedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                StatusChip(status = status)
            }

            when (status) {
                HealthConnectStatus.NOT_AVAILABLE -> {
                    Text(
                        text = "Health Connect is not available on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                HealthConnectStatus.NOT_CONNECTED -> {
                    Text(
                        text = "Grant permissions to import workouts from your fitness apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(onClick = onConnectClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect")
                    }
                }
                HealthConnectStatus.CONNECTED -> {
                    // Period Selector
                    var expanded by remember { mutableStateOf(false) }
                    val selectedLabel = syncPeriodOptions.find { it.first == syncDaysBack }?.second ?: "$syncDaysBack days"

                    Text(
                        text = "Sync Period",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSyncing) { expanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = selectedLabel, style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            syncPeriodOptions.forEach { (days, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontWeight = if (days == syncDaysBack) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        onSyncDaysChange(days)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Button(
                            onClick = onSyncClick,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Sync")
                        }
                        
                        OutlinedButton(
                            onClick = onReprocessClick,
                            modifier = Modifier.weight(1f),
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Reprocess")
                        }
                    }

                    Text(
                        text = "Reprocess recalculates all workouts using your current FTP and Heart Rate settings without needing Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )

                    OutlinedButton(
                        onClick = onViewSyncedClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("View Synced Exercises")
                    }

                    if (syncedWorkoutsCount > 0) {
                        Text(
                            text = "$syncedWorkoutsCount workouts synced to database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: HealthConnectStatus) {
    val (bgColor, textColor, text, icon) = when (status) {
        HealthConnectStatus.NOT_AVAILABLE -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Unavailable",
            Icons.Default.Close
        )
        HealthConnectStatus.NOT_CONNECTED -> listOf(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Not Connected",
            Icons.Default.Close
        )
        HealthConnectStatus.CONNECTED -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Connected",
            Icons.Default.Check
        )
    }

    Card(colors = CardDefaults.cardColors(containerColor = bgColor as androidx.compose.ui.graphics.Color)) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = textColor as androidx.compose.ui.graphics.Color
            )
            Text(
                text = text as String,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        text = "Training Metrics",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (profile == null) {
                        Text(
                            text = "Not configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    } else {
                        val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                        Column(
                            modifier = Modifier.padding(top = Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            profile.ftpBike?.let {
                                Text(
                                    text = "FTP: ${it}W",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            profile.maxHeartRate?.let {
                                Text(
                                    text = "Max HR: ${it} bpm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            profile.lthr?.let {
                                Text(
                                    text = "LTHR: ${it} bpm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            profile.cssSecondsper100m?.let { seconds ->
                                val minutes = seconds / 60
                                val secs = seconds % 60
                                Text(
                                    text = "CSS: $minutes:${secs.toString().padStart(2, '0')} / 100m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            profile.goalDate?.let {
                                Text(
                                    text = "Goal: ${it.format(dateFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                Button(onClick = onEditClick) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun BackupCard(
    isLoading: Boolean,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Back up your training plans and workout logs to restore later or transfer to another device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = onExportClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text("Export")
                }
                Button(
                    onClick = onImportClick,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text("Import")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

            OutlinedButton(
                onClick = onResetClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(Spacing.xs))
                Text("Reset All Data")
            }
        }
    }
}

private suspend fun writeJsonToUri(context: Context, uri: Uri, jsonString: String) {
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write(jsonString)
            }
        } ?: throw IOException("Failed to open output stream for URI: $uri")
    }
}

private suspend fun readJsonFromUri(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        } ?: throw IOException("Failed to open input stream for URI: $uri")
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    TriPathTheme {
        SettingsScreen()
    }
}
