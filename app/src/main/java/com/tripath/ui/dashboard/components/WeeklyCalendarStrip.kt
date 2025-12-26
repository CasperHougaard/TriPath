package com.tripath.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripath.ui.dashboard.DayStatus
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeeklyCalendarStrip(
    weekDayStatuses: List<DayStatus>,
    onDateSelected: (java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekDayStatuses.forEach { status ->
            DayStatusItem(
                status = status,
                onClick = { onDateSelected(status.date) }
            )
        }
    }
}

@Composable
fun DayStatusItem(
    status: DayStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        // Day Label (M, T, W...)
        Text(
            text = status.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
            style = MaterialTheme.typography.bodySmall,
            color = if (status.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = if (status.isToday) FontWeight.Bold else FontWeight.Normal
        )

        // Status Circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        status.isSelected -> MaterialTheme.colorScheme.primary // Filled for selected
                        status.isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) // Faded fill for completed
                        status.isToday && !status.isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else -> Color.Transparent
                    }
                )
                .border(
                    width = 2.dp,
                    color = when {
                        status.isSelected -> MaterialTheme.colorScheme.primary
                        status.isCompleted -> MaterialTheme.colorScheme.primary
                        status.hasPlan -> MaterialTheme.colorScheme.primary
                        status.isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = status.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = when {
                    status.isSelected -> MaterialTheme.colorScheme.onPrimary
                    status.isCompleted -> MaterialTheme.colorScheme.onPrimary // White text on filled circle
                    status.isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )
        }
    }
}
