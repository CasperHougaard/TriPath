package com.tripath.ui.data

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.local.backup.ImportMode
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.theme.IconSize
import com.tripath.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * "My Data": a complete account of what TriPath stores about the user, plus the state of their
 * backup and the controls to export, back up or restore it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDataScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategory: (DataCategory) -> Unit,
    viewModel: MyDataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val exportJsonState = remember { mutableStateOf<String?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showCloudRestoreDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportUri ->
            exportJsonState.value?.let { jsonString ->
                coroutineScope.launch {
                    try {
                        writeTextToUri(context, exportUri, jsonString)
                        snackbarHostState.showSnackbar("Data exported")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Could not save file: ${e.message}")
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
                    pendingImportJson = readTextFromUri(context, importUri)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Could not read file: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        val text = uiState.errorMessage ?: uiState.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Data")
                        Text(
                            text = if (uiState.isLoadingCounts) {
                                "Counting records…"
                            } else {
                                "${uiState.totalRecords} records stored"
                            },
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))

            BackupStatusCard(
                state = uiState,
                onBackUpNow = { viewModel.backUpNow() },
                onExport = {
                    coroutineScope.launch {
                        viewModel.exportJson().fold(
                            onSuccess = { json ->
                                exportJsonState.value = json
                                exportLauncher.launch("tripath_backup.json")
                            },
                            onFailure = { error ->
                                snackbarHostState.showSnackbar("Export failed: ${error.message}")
                            }
                        )
                    }
                },
                onImportFromFile = { importLauncher.launch(arrayOf("application/json")) },
                onRestoreCloudSnapshot = { showCloudRestoreDialog = true }
            )

            SectionHeader(
                title = "Stored data",
                subtitle = "Everything in this list is included in your backup"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    DataCategory.entries.forEachIndexed { index, category ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                        CategoryRow(
                            category = category,
                            count = uiState.counts[category],
                            onClick = { onNavigateToCategory(category) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }

    pendingImportJson?.let { json ->
        ImportModeDialog(
            title = "Restore from file",
            onDismiss = { pendingImportJson = null },
            onConfirm = { mode ->
                viewModel.importFromJson(json, mode)
                pendingImportJson = null
            }
        )
    }

    if (showCloudRestoreDialog) {
        ImportModeDialog(
            title = "Restore last backup",
            supportingText = uiState.snapshot?.let {
                "Prepared ${formatEpochMillis(it.timestampMillis)} · ${it.totalRecords} records"
            },
            onDismiss = { showCloudRestoreDialog = false },
            onConfirm = { mode ->
                viewModel.restoreFromCloudSnapshot(mode)
                showCloudRestoreDialog = false
            }
        )
    }
}

@Composable
private fun BackupStatusCard(
    state: MyDataUiState,
    onBackUpNow: () -> Unit,
    onExport: () -> Unit,
    onImportFromFile: () -> Unit,
    onRestoreCloudSnapshot: () -> Unit
) {
    val snapshot = state.snapshot
    val busy = state.isBackingUp || state.isRestoring

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (snapshot != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.large),
                    tint = if (snapshot != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (snapshot != null) "Backup ready" else "No backup yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (snapshot != null) {
                            "Prepared ${formatEpochMillis(snapshot.timestampMillis)}"
                        } else {
                            "Tap \"Back up now\" to prepare one"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                // Deliberately does not claim the upload has happened: Android exposes no API for
                // "last successful Google backup", so promising more than we can verify would be
                // the one thing worse than no backup — a false sense of one.
                text = "Your data is included in this phone's Google backup and restored when you " +
                    "set up a new phone or reinstall TriPath. Android uploads it in the " +
                    "background, usually overnight while charging on Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (snapshot != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    MetaStat(label = "Backup size", value = formatBytes(snapshot.compressedBytes))
                    MetaStat(label = "Records", value = "${snapshot.totalRecords}")
                    MetaStat(label = "On device", value = formatBytes(state.databaseBytes))
                }
            }

            Button(
                onClick = onBackUpNow,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.small)
                )
                Spacer(modifier = Modifier.size(Spacing.sm))
                Text("Back up now")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.small)
                    )
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text("Export file")
                }
                OutlinedButton(
                    onClick = onImportFromFile,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.small)
                    )
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text("Import file")
                }
            }

            if (snapshot != null) {
                TextButton(
                    onClick = onRestoreCloudSnapshot,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore last backup on this device")
                }
            }
        }
    }
}

@Composable
private fun MetaStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryRow(
    category: DataCategory,
    count: Int?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = category.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = count?.toString() ?: "…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (count == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Asks whether a restore should merge or replace.
 *
 * Merge is the primary action because it cannot lose data: importing an old backup adds and
 * overwrites, but never deletes anything logged since. Replace is offered as the destructive
 * secondary, with an explicit warning.
 */
@Composable
private fun ImportModeDialog(
    title: String,
    supportingText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (ImportMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                supportingText?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Merge keeps everything already on this phone and adds the backup on top. " +
                        "Replace deletes all current data first, so anything logged since the " +
                        "backup was made is lost."
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(ImportMode.MERGE) }) {
                Text("Merge")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onConfirm(ImportMode.REPLACE_ALL) }) {
                    Text(
                        text = "Replace all",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

