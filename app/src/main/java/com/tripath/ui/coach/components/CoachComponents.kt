package com.tripath.ui.coach.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.domain.coach.CoachWarning
import com.tripath.domain.coach.ReadinessColor
import com.tripath.domain.coach.ReadinessStatus
import com.tripath.domain.coach.WarningType
import com.tripath.ui.theme.Spacing

/**
 * Readiness Card displaying the readiness score as a circular progress indicator with traffic light colors.
 * 
 * @param readinessStatus The readiness status to display, or null to show a neutral state
 * @param onClick Callback when the card is clicked to show detailed breakdown
 */
@Composable
fun ReadinessCard(
    readinessStatus: ReadinessStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (readinessStatus != null) {
                // Circular Progress Indicator with score
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    val progress = readinessStatus.score / 100f
                    val color = when (readinessStatus.color) {
                        ReadinessColor.GREEN -> Color(0xFF4CAF50) // Material Green 500
                        ReadinessColor.YELLOW -> Color(0xFFFBC02D) // Material Amber 600
                        ReadinessColor.RED -> Color(0xFFE53935) // Material Red 600
                    }
                    
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxSize(),
                        color = color,
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Text(
                        text = "${readinessStatus.score}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Footer text with breakdown
                Text(
                    text = if (readinessStatus.score > 75) {
                        "All Systems Go"
                    } else {
                        readinessStatus.breakdown
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                // Neutral state when no readiness data
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    CircularProgressIndicator(
                        progress = 0f,
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                Text(
                    text = "Log Recovery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Dialog showing detailed readiness breakdown.
 */
@Composable
fun ReadinessBreakdownDialog(
    readinessStatus: ReadinessStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Readiness Breakdown")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Score: ${readinessStatus.score}/100",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = readinessStatus.breakdown,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (readinessStatus.allergyPenalty > 0) {
                    Text(
                        text = "Allergy Penalty: -${readinessStatus.allergyPenalty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

/**
 * List of coach warnings/alerts displayed as cards.
 * 
 * @param warnings List of coach warnings to display
 */
@Composable
fun CoachAlertsList(
    warnings: List<CoachWarning>,
    modifier: Modifier = Modifier
) {
    if (warnings.isEmpty()) {
        return
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        warnings.forEach { warning ->
            CoachAlertCard(warning = warning)
        }
    }
}

/**
 * Individual alert card for a single coach warning.
 */
@Composable
private fun CoachAlertCard(
    warning: CoachWarning,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        warning.type == WarningType.INJURY_RISK || 
        (warning.type == WarningType.RULE_VIOLATION && warning.isBlocker) -> 
            MaterialTheme.colorScheme.errorContainer
        else -> 
            MaterialTheme.colorScheme.tertiaryContainer
    }
    
    val icon = when {
        warning.type == WarningType.INJURY_RISK || 
        (warning.type == WarningType.RULE_VIOLATION && warning.isBlocker) -> 
            Icons.Default.Warning
        else -> 
            Icons.Default.Info
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer.takeIf { 
                    containerColor == MaterialTheme.colorScheme.errorContainer 
                } ?: MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = warning.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (containerColor == MaterialTheme.colorScheme.errorContainer) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (containerColor == MaterialTheme.colorScheme.errorContainer) {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    }
                )
            }
        }
    }
}


