package com.tripath.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.local.backup.RestoreCoordinator
import com.tripath.data.local.backup.SnapshotMeta
import com.tripath.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudRestoreViewModel @Inject constructor(
    private val restoreCoordinator: RestoreCoordinator
) : ViewModel() {

    val pendingRestore: StateFlow<SnapshotMeta?> = restoreCoordinator.pendingRestore

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)

    /** Message describing the outcome, shown after the dialog closes. */
    val result: StateFlow<String?> = _result.asStateFlow()

    fun accept() {
        viewModelScope.launch {
            _isRestoring.value = true
            val outcome = restoreCoordinator.acceptPendingRestore()
            _isRestoring.value = false
            _result.value = outcome.fold(
                onSuccess = { "Restored ${it.totalRecords} records from your backup" },
                onFailure = { "Restore failed: ${it.message}" }
            )
        }
    }

    fun decline() = restoreCoordinator.declinePendingRestore()

    fun clearResult() {
        _result.value = null
    }
}

/**
 * Offers a cloud backup that Android restored onto this device.
 *
 * Shown once, on the first launch after a restore. Declining keeps the backup on disk so it can
 * still be restored from the My Data screen — this is an offer, not the only chance to take it.
 */
@Composable
fun CloudRestorePrompt(
    onShowMessage: (String) -> Unit = {},
    viewModel: CloudRestoreViewModel = hiltViewModel()
) {
    val pending by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    LaunchedEffect(result) {
        result?.let { message ->
            onShowMessage(message)
            viewModel.clearResult()
        }
    }

    val meta = pending ?: return

    AlertDialog(
        onDismissRequest = {
            // Only via the explicit buttons: dismissing by tapping outside could look like the
            // backup was silently discarded.
        },
        icon = {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
        },
        title = { Text("Restore your data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "A TriPath backup from ${formatEpochMillis(meta.timestampMillis)} was found " +
                        "in your Google account."
                )
                Text(
                    text = describeSnapshot(meta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.accept() }, enabled = !isRestoring) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.decline() }, enabled = !isRestoring) {
                Text("Not now")
            }
        }
    )
}

/**
 * Names the categories with data so the user can recognise the backup as theirs, rather than
 * being asked to trust an opaque record count.
 */
private fun describeSnapshot(meta: SnapshotMeta): String {
    val labels = mapOf(
        "workoutLogs" to "workouts",
        "sleepLogs" to "sleep records",
        "bodyCompositionLogs" to "body scans",
        "nutritionLogs" to "nutrition days",
        "trainingPlans" to "planned sessions"
    )
    val parts = labels.mapNotNull { (key, label) ->
        meta.counts[key]?.takeIf { it > 0 }?.let { "$it $label" }
    }
    return if (parts.isEmpty()) {
        "${meta.totalRecords} records, ${formatBytes(meta.compressedBytes)}"
    } else {
        parts.joinToString(" · ")
    }
}
