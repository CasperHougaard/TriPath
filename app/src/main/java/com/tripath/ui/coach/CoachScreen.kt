package com.tripath.ui.coach

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tripath.ui.coach.components.ReadinessCard
import com.tripath.ui.coach.components.SpecialPeriodDialog
import com.tripath.ui.coach.components.SpecialPeriodList
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.components.charts.LineChart
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
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val isSmartPlanningEnabled by viewModel.isSmartPlanningEnabled.collectAsStateWithLifecycle()
    
    var showSpecialPeriodDialog by remember { mutableStateOf(false) }
    var initialDialogType by remember { mutableStateOf(SpecialPeriodType.INJURY) }
    var showReadinessBreakdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach") },
                actions = {
                    navController?.let {
                        IconButton(
                            onClick = { it.navigate(com.tripath.ui.navigation.Screen.PlanningSettings.route) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Coach Settings"
                            )
                        }
                    }
                }
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
                        ReadinessCard(
                            readinessStatus = readinessState,
                            onClick = { showReadinessBreakdown = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
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
