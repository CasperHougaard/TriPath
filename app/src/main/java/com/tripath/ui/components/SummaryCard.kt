package com.tripath.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Summary card component for displaying workout session summaries.
 * Used in workout history and logs to show completed workout details.
 *
 * @param date Date of the workout
 * @param title Title of the workout
 * @param details Details text (e.g., "60 min • 80 TSS")
 * @param badge Optional badge composable for workout type indicator
 * @param onClick Optional click handler
 * @param modifier Modifier for the card
 */
@Composable
fun SummaryCard(
    date: LocalDate,
    title: String,
    details: String,
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    
    val cardModifier = modifier.fillMaxWidth()
    val card: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                badge?.invoke()
            }
            
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
    
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            card()
        }
    } else {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            card()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SummaryCardPreview() {
    TriPathTheme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
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
                details = "45 min • 65 TSS",
                badge = {
                    WorkoutBadge(workoutType = WorkoutType.BIKE)
                },
                onClick = { }
            )
        }
    }
}

