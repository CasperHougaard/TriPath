package com.tripath.ui.coach.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.data.model.AnchorType
import com.tripath.data.model.UserProfile
import com.tripath.ui.theme.Spacing
import java.time.DayOfWeek

/**
 * Composable for selecting an anchor type for a specific day.
 */
@Composable
fun DayRow(
    day: DayOfWeek,
    currentAnchor: AnchorType,
    onAnchorSelected: (AnchorType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day name
        Text(
            text = day.name.take(3),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        
        // Anchor type selector - horizontal scrollable chips
        Box(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AnchorType.values().forEach { anchorType ->
                    val displayName = when (anchorType) {
                        AnchorType.NONE -> "Rest"
                        AnchorType.RUN -> "Run"
                        AnchorType.BIKE -> "Bike"
                        AnchorType.SWIM -> "Swim"
                        AnchorType.STRENGTH -> "Strength"
                        AnchorType.LONG_RUN -> "Long Run"
                        AnchorType.LONG_BIKE -> "Long Bike"
                    }
                    
                    FilterChip(
                        selected = currentAnchor == anchorType,
                        onClick = { onAnchorSelected(anchorType) },
                        label = { 
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Weekly schedule section displaying all 7 days with anchor selection.
 */
@Composable
fun WeeklyScheduleSection(
    userProfile: UserProfile?,
    onDayAnchorChanged: (DayOfWeek, AnchorType) -> Unit,
    modifier: Modifier = Modifier
) {
    val schedule = userProfile?.weeklySchedule ?: UserProfile.DEFAULT_WEEKLY_SCHEDULE
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Weekly Schedule Anchors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Define your weekly training structure",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(Spacing.md))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                DayOfWeek.values().forEach { day ->
                    DayRow(
                        day = day,
                        currentAnchor = schedule[day] ?: AnchorType.NONE,
                        onAnchorSelected = { anchorType ->
                            onDayAnchorChanged(day, anchorType)
                        }
                    )
                }
            }
        }
    }
}

