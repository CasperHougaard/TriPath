package com.tripath.ui.coach.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SpecialPeriodList(
    periods: List<SpecialPeriod>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var periodToDelete by remember { mutableStateOf<SpecialPeriod?>(null) }

    if (periods.isEmpty()) {
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        periods.forEach { period ->
            SpecialPeriodItem(
                period = period,
                onDelete = { periodToDelete = period },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Confirmation dialog
    periodToDelete?.let { period ->
        AlertDialog(
            onDismissRequest = { periodToDelete = null },
            title = { Text("Delete ${period.type.name.replace("_", " ")}?") },
            text = {
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                val dateRange = if (period.startDate == period.endDate) {
                    period.startDate.format(formatter)
                } else {
                    "${period.startDate.format(formatter)} - ${period.endDate.format(formatter)}"
                }
                Text("Are you sure you want to delete this period?\n\n$dateRange")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(period.id)
                        periodToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { periodToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SpecialPeriodItem(
    period: SpecialPeriod,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val dateRange = if (period.startDate == period.endDate) {
        period.startDate.format(formatter)
    } else {
        "${period.startDate.format(formatter)} - ${period.endDate.format(formatter)}"
    }

    val (icon, label, containerColor) = when (period.type) {
        SpecialPeriodType.INJURY -> Triple(
            Icons.Default.LocalHospital,
            "Injury / Illness",
            MaterialTheme.colorScheme.errorContainer
        )
        SpecialPeriodType.HOLIDAY -> Triple(
            Icons.Default.Luggage,
            "Holiday",
            MaterialTheme.colorScheme.tertiaryContainer
        )
        SpecialPeriodType.RECOVERY_WEEK -> Triple(
            Icons.Default.Hotel,
            "Recovery Week",
            MaterialTheme.colorScheme.secondaryContainer
        )
    }

    val isActive = LocalDate.now().let { today ->
        today >= period.startDate && today <= period.endDate
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor.copy(alpha = if (isActive) 1f else 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) MaterialTheme.typography.bodyMedium.fontWeight else null
                    )
                    Text(
                        text = dateRange,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!period.notes.isNullOrBlank()) {
                        Text(
                            text = period.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

