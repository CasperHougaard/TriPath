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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.ui.coach.components.CoachAssessmentCard
import com.tripath.ui.coach.components.PhaseTimeline
import com.tripath.ui.coach.components.SpecialPeriodDialog
import com.tripath.ui.coach.components.SpecialPeriodList
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.components.charts.LineChart
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.LocalDate

@Composable
fun CoachScreen(
    viewModel: CoachViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var showSpecialPeriodDialog by remember { mutableStateOf(false) }
    var initialDialogType by remember { mutableStateOf(SpecialPeriodType.INJURY) }

    Scaffold(modifier = modifier) { paddingValues ->
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

                    // 4. Manual Intervention
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

