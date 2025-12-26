package com.tripath.ui.planner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.ui.components.WeekDayItem
import com.tripath.ui.theme.TriPathTheme

@Composable
fun WeeklyPlannerScreen(
    viewModel: WeeklyPlannerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.weekDays) { weekDay ->
                WeekDayItem(
                    weekDay = weekDay,
                    onAddClick = { viewModel.openAddWorkoutSheet(weekDay.date) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Bottom Sheet
    if (uiState.showBottomSheet && uiState.selectedDate != null) {
        AddWorkoutBottomSheet(
            selectedDate = uiState.selectedDate!!,
            onDismiss = { viewModel.closeBottomSheet() },
            onSave = { workout -> viewModel.addWorkout(workout) }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeeklyPlannerScreenPreview() {
    TriPathTheme {
        WeeklyPlannerScreen()
    }
}

