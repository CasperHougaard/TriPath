package com.tripath.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy")
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHealthDataScreen(
    onNavigateBack: () -> Unit,
    viewModel: HealthViewModel = hiltViewModel()
) {
    val logs by viewModel.allLogsForManage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Manage Data")
                        Text(
                            text = "${logs.size} measurements",
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
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No measurements yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item { Spacer(modifier = Modifier.height(Spacing.sm)) }
                items(logs, key = { it.id }) { log ->
                    MeasurementRow(
                        log = log,
                        onToggleIgnored = { viewModel.toggleIgnored(log.id, !log.isIgnored) }
                    )
                }
                item { Spacer(modifier = Modifier.height(Spacing.lg)) }
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    log: BodyCompositionLog,
    onToggleIgnored: () -> Unit
) {
    val instant = Instant.ofEpochMilli(log.timestamp)
    val zoned = instant.atZone(ZoneId.systemDefault())
    val dateStr = DATE_FORMATTER.format(zoned)
    val timeStr = TIME_FORMATTER.format(zoned)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (log.isIgnored) 0.45f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isIgnored)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = dateStr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(text = timeStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    log.weightKg?.let { MetricText(label = "Weight", value = "%.1f kg".format(it)) }
                    log.bodyFatPercent?.let { MetricText(label = "Fat", value = "%.1f%%".format(it)) }
                    log.leanMassKg?.let { MetricText(label = "Fat-free", value = "%.1f kg".format(it)) }
                    log.boneMassKg?.let { MetricText(label = "Bone", value = "%.2f kg".format(it)) }
                }
                if (log.isIgnored) {
                    Text(
                        text = "Excluded from charts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = !log.isIgnored,
                onCheckedChange = { onToggleIgnored() }
            )
        }
    }
}

@Composable
private fun MetricText(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
