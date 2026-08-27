package com.tripath.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.model.TrainingBalance
import com.tripath.data.model.WorkoutType
import com.tripath.ui.coach.components.CoachAssessmentCard
import com.tripath.ui.coach.components.CoachAlertsList
import com.tripath.ui.coach.components.PhaseTimeline
import com.tripath.ui.coach.components.ReadinessBreakdownDialog
import com.tripath.ui.coach.components.ReadinessAssessmentCard
import com.tripath.ui.coach.components.ReadinessCard
import com.tripath.ui.coach.components.SpecialPeriodDialog
import com.tripath.ui.coach.components.SpecialPeriodList
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.components.charts.LineChart
import com.tripath.ui.navigation.Screen
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    navController: NavHostController? = null,
    viewModel: CoachViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readinessState by viewModel.readinessState.collectAsStateWithLifecycle()
    val assessmentState by viewModel.assessmentState.collectAsStateWithLifecycle()
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val isSmartPlanningEnabled by viewModel.isSmartPlanningEnabled.collectAsStateWithLifecycle()
    
    var showSpecialPeriodDialog by remember { mutableStateOf(false) }
    var initialDialogType by remember { mutableStateOf(SpecialPeriodType.INJURY) }
    var showReadinessBreakdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach") }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    // Header
                    SectionHeader(
                        title = "Coach",
                        subtitle = "Strategic Planning & Analysis"
                    )

                    // Readiness Card and Alerts (if smart planning enabled)
                    if (isSmartPlanningEnabled) {
                        // The per-channel assessment when there is enough history for it, otherwise
                        // the older single-score card. Both are never shown at once — two readiness
                        // numbers on one screen is exactly the confusion this model exists to end.
                        if (assessmentState?.strain?.hasData == true) {
                            ReadinessAssessmentCard(
                                assessment = assessmentState,
                                onClick = { navController?.navigate(Screen.ReadinessDetail.route) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            ReadinessCard(
                                readinessStatus = readinessState,
                                onClick = { showReadinessBreakdown = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        if (alertsState.isNotEmpty()) {
                            SectionHeader(
                                title = "Coach Alerts",
                                subtitle = "Readiness Status"
                            )
                            CoachAlertsList(
                                warnings = alertsState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // Placeholder when smart planning is disabled
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "Smart Planning Disabled",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(Spacing.lg)
                            )
                        }
                    }

                    // 1. Lifecycle Progress Timeline
                    PhaseTimeline(
                        currentDate = LocalDate.now(),
                        goalDate = uiState.goalDate,
                        currentPhase = uiState.currentPhase
                    )

                    // 2. Coach's Voice
                    CoachAssessmentCard(
                        assessment = uiState.coachAssessment
                    )

                    // 3. Performance Pulse (CTL/ATL/TSB)
                    SectionHeader(
                        title = "Performance Pulse",
                        subtitle = "Fitness (CTL) vs Fatigue (ATL)"
                    )
                    
                    LineChart(
                        data = uiState.performanceData,
                        modifier = Modifier.fillMaxWidth()
                    )

                    MetricsExplanationCard(
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 5. Manual Intervention
                    SectionHeader(
                        title = "Interventions",
                        subtitle = "Manage exceptions & breaks"
                    )
                    
                    InterventionButtons(
                        onLogInjury = {
                            initialDialogType = SpecialPeriodType.INJURY
                            showSpecialPeriodDialog = true
                        },
                        onAddHoliday = {
                            initialDialogType = SpecialPeriodType.HOLIDAY
                            showSpecialPeriodDialog = true
                        },
                        onRecoveryWeek = {
                            initialDialogType = SpecialPeriodType.RECOVERY_WEEK
                            showSpecialPeriodDialog = true
                        }
                    )
                    
                    // List of existing special periods
                    if (uiState.allSpecialPeriods.isNotEmpty()) {
                        SectionHeader(
                            title = "Active Periods",
                            subtitle = "Manage your logged periods"
                        )
                        SpecialPeriodList(
                            periods = uiState.allSpecialPeriods,
                            onDelete = { id ->
                                viewModel.deleteSpecialPeriod(id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(Spacing.xl))
                }
            }
        }

        if (showSpecialPeriodDialog) {
            SpecialPeriodDialog(
                initialType = initialDialogType,
                onDismiss = { showSpecialPeriodDialog = false },
                onConfirm = { type, start, end, notes ->
                    viewModel.addSpecialPeriod(type, start, end, notes)
                    showSpecialPeriodDialog = false
                }
            )
        }

        val currentReadinessState = readinessState
        if (showReadinessBreakdown && currentReadinessState != null) {
            ReadinessBreakdownDialog(
                readinessStatus = currentReadinessState,
                onDismiss = { showReadinessBreakdown = false }
            )
        }
    }
}

@Composable
fun MetricsExplanationCard(
    modifier: Modifier = Modifier
) {
    val ctlColor = MaterialTheme.colorScheme.primary
    val atlColor = Color(0xFFFF69B4)
    val tsbColor = Color(0xFF4CAF50)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Understanding the metrics",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            MetricExplanationRow(
                dotColor = ctlColor,
                title = "CTL — Fitness",
                description = "Chronic Training Load: your long-term fitness, a 42-day average of training stress. It rises slowly as you train consistently."
            )
            MetricExplanationRow(
                dotColor = atlColor,
                title = "ATL — Fatigue",
                description = "Acute Training Load: your short-term fatigue, a 7-day average of training stress. It spikes quickly after hard sessions and fades with rest."
            )
            MetricExplanationRow(
                dotColor = tsbColor,
                title = "TSB — Form",
                description = "Training Stress Balance (CTL − ATL): how fresh you are. A positive TSB means your fitness outweighs your fatigue, so you've absorbed your training and are rested and race-ready. During build phases a slightly negative TSB is normal and productive — you only want it clearly positive when peaking for a race."
            )
        }
    }
}

@Composable
private fun MetricExplanationRow(
    dotColor: Color,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InterventionButtons(
    onLogInjury: () -> Unit,
    onAddHoliday: () -> Unit,
    onRecoveryWeek: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Button(
            onClick = onLogInjury,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.LocalHospital, contentDescription = null)
            Spacer(modifier = Modifier.padding(Spacing.sm))
            Text("Log Injury / Illness")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onAddHoliday,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Luggage, contentDescription = null)
                Spacer(modifier = Modifier.padding(Spacing.xs))
                Text("Holiday")
            }
            
            OutlinedButton(
                onClick = onRecoveryWeek,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Hotel, contentDescription = null)
                Spacer(modifier = Modifier.padding(Spacing.xs))
                Text("Recovery")
            }
        }
    }
}
