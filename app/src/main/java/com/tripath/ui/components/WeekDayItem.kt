package com.tripath.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import com.tripath.ui.planner.WeekDay
import com.tripath.ui.theme.TriPathTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeekDayItem(
    weekDay: WeekDay,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = weekDay.dayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = weekDay.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Button(onClick = onAddClick) {
                    Text("Add")
                }
            }

            if (weekDay.workouts.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    weekDay.workouts.forEach { workout ->
                        WorkoutCard(
                            workout = workout,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Text(
                    text = "No workouts planned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeekDayItemPreview() {
    TriPathTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeekDayItem(
                weekDay = WeekDay(
                    date = LocalDate.now(),
                    dayName = "Monday",
                    workouts = listOf(
                        TrainingPlan(
                            date = LocalDate.now(),
                            type = WorkoutType.RUN,
                            durationMinutes = 60,
                            plannedTSS = 80
                        )
                    )
                ),
                onAddClick = {}
            )
            Spacer(modifier = Modifier.height(8.dp))
            WeekDayItem(
                weekDay = WeekDay(
                    date = LocalDate.now().plusDays(1),
                    dayName = "Tuesday",
                    workouts = emptyList()
                ),
                onAddClick = {}
            )
        }
    }
}

