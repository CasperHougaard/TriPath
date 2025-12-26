package com.tripath.ui.showcase

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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripath.data.model.WorkoutType
import com.tripath.ui.components.EmptyState
import com.tripath.ui.components.LoadIndicator
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.components.StatCard
import com.tripath.ui.components.SummaryCard
import com.tripath.ui.components.TextBadge
import com.tripath.ui.components.WorkoutBadge
import com.tripath.ui.components.WorkoutCard
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor
import java.time.LocalDate

/**
 * Design Showcase Screen
 * Demonstrates all design system components and patterns.
 * This is a reference implementation showing proper usage of the design system.
 */
@Composable
fun DesignShowcaseScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text(
                text = "Design System Showcase",
                style = MaterialTheme.typography.headlineMedium
            )
            
            // Section: Load Indicators
            SectionHeader(
                title = "Load Indicators",
                subtitle = "Training load progress visualization"
            )
            
            LoadIndicator(
                plannedTSS = 300,
                actualTSS = 240,
                progress = 0.8f
            )
            
            LoadIndicator(
                plannedTSS = 300,
                actualTSS = 350,
                progress = 1.17f
            )
            
            // Section: Stat Cards
            SectionHeader(
                title = "Stat Cards",
                subtitle = "Key metrics display"
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatCard(
                    label = "This Week",
                    value = "240 TSS",
                    icon = Icons.AutoMirrored.Filled.TrendingUp
                )
                StatCard(
                    label = "Total Time",
                    value = "12.5 hrs",
                    icon = Icons.Default.Timer
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatCard(
                    label = "Distance",
                    value = "42.5 km",
                    icon = Icons.Default.Route,
                    onClick = { }
                )
                StatCard(
                    label = "Workouts",
                    value = "8",
                    icon = Icons.Default.FitnessCenter
                )
            }
            
            // Section: Workout Badges
            SectionHeader(
                title = "Workout Badges",
                subtitle = "Sport type indicators"
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                WorkoutBadge(workoutType = WorkoutType.SWIM)
                WorkoutBadge(workoutType = WorkoutType.BIKE)
                WorkoutBadge(workoutType = WorkoutType.RUN)
                WorkoutBadge(workoutType = WorkoutType.STRENGTH)
            }
            
            // Section: Text Badges
            SectionHeader(
                title = "Text Badges",
                subtitle = "Status and category indicators"
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TextBadge(text = "NEW")
                TextBadge(
                    text = "COMPLETED",
                    backgroundColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                )
                TextBadge(
                    text = "12 SETS",
                    backgroundColor = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Section: Summary Cards
            SectionHeader(
                title = "Summary Cards",
                subtitle = "Workout history display"
            )
            
            SummaryCard(
                date = LocalDate.now(),
                title = "Morning Run",
                details = "60 min • 80 TSS • 10.5 km",
                badge = {
                    TextBadge(
                        text = "RUN",
                        backgroundColor = WorkoutType.RUN.toColor()
                    )
                }
            )
            
            SummaryCard(
                date = LocalDate.now().minusDays(1),
                title = "Bike Intervals",
                details = "45 min • 65 TSS • High intensity",
                badge = {
                    WorkoutBadge(workoutType = WorkoutType.BIKE)
                },
                onClick = { }
            )
            
            // Section: Empty State (in a container to show properly)
            SectionHeader(
                title = "Empty States",
                subtitle = "No data display"
            )
            
            // Empty state in a smaller container for showcase
            androidx.compose.material3.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                EmptyState(
                    message = "No workouts planned",
                    description = "Tap + to add your first workout"
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DesignShowcaseScreenPreview() {
    TriPathTheme {
        DesignShowcaseScreen()
    }
}

