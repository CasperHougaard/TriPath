package com.tripath.ui.coach

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlanningSettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEnabled = uiState.isSmartPlanningEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
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
                // Section 1: Master Control
                SectionHeader(
                    title = "Master Control",
                    subtitle = "Enable or disable smart planning features"
                )

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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Smart Planning",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Master control for AI-powered training plan generation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = Spacing.xs)
                                )
                            }
                            Switch(
                                checked = uiState.isSmartPlanningEnabled,
                                onCheckedChange = { viewModel.setSmartPlanning(enabled = it) }
                            )
                        }
                    }
                }

                // Section 2: Injury Prevention
                SectionHeader(
                    title = "Injury Prevention",
                    subtitle = "The Rules"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        // Allow Consecutive Runs Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow Consecutive Runs",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "High impact risk if enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (isEnabled) 0.7f else 0.4f
                                    )
                                )
                            }
                            Switch(
                                checked = uiState.runConsecutiveAllowed,
                                onCheckedChange = { viewModel.setRunConsecutiveAllowed(allowed = it) },
                                enabled = isEnabled
                            )
                        }

                        // Monitor Mechanical Load Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Monitor Mechanical Load",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Track structural stress score",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (isEnabled) 0.7f else 0.4f
                                    )
                                )
                            }
                            Switch(
                                checked = uiState.mechanicalLoadMonitoring,
                                onCheckedChange = { viewModel.setMechanicalLoadMonitoring(enabled = it) },
                                enabled = isEnabled
                            )
                        }

                        // Max Ramp Rate Slider
                        Column {
                            Text(
                                text = "Max Ramp Rate: ${String.format("%.1f", uiState.rampRateLimit)}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Slider(
                                value = uiState.rampRateLimit,
                                onValueChange = { viewModel.setRampRateLimit(it) },
                                valueRange = 3.0f..8.0f,
                                steps = 9, // 0.5 steps: (8.0 - 3.0) / 0.5 = 10, so steps = 9
                                enabled = isEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "3.0%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    "8.0%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                // Section 3: Schedule Constraints
                SectionHeader(
                    title = "Schedule Constraints",
                    subtitle = "Training schedule configuration"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        // Strength Recovery Hours Slider
                        Column {
                            Text(
                                text = "Strength Recovery: ${uiState.strengthSpacingHours} hours",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Slider(
                                value = uiState.strengthSpacingHours.toFloat(),
                                onValueChange = { value ->
                                    // Snap to nearest valid value: 24, 48, or 72
                                    val snapped = when {
                                        value < 36 -> 24
                                        value < 60 -> 48
                                        else -> 72
                                    }
                                    viewModel.setStrengthSpacingHours(snapped)
                                },
                                valueRange = 24f..72f,
                                steps = 1, // This creates 3 stops: 24, 48, 72
                                enabled = isEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "24h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    "72h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }

                        // Ignore Commute Load Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ignore Commute Load",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Exclude low intensity commutes from recovery rules",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (isEnabled) 0.7f else 0.4f
                                    )
                                )
                            }
                            Switch(
                                checked = uiState.allowCommuteExemption,
                                onCheckedChange = { viewModel.setAllowCommuteExemption(allowed = it) },
                                enabled = isEnabled
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

