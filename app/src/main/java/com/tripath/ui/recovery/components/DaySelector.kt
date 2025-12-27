package com.tripath.ui.recovery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale


@Composable
fun DaySelector(
    selectedDate: LocalDate,
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxFutureDate = today.plusDays(7)
    val canGoNext = !selectedDate.isAfter(maxFutureDate)
    
    // Format date based on locale
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
    
    // Format for relative day names
    val dayNameFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    
    val dateText = when {
        selectedDate == today -> "Today, ${selectedDate.format(shortDateFormatter)}"
        selectedDate == today.minusDays(1) -> "Yesterday, ${selectedDate.format(shortDateFormatter)}"
        selectedDate == today.plusDays(1) -> "Tomorrow, ${selectedDate.format(shortDateFormatter)}"
        else -> "${selectedDate.format(dayNameFormatter)}, ${selectedDate.format(shortDateFormatter)}"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousDay,
                modifier = Modifier
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous day"
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Show "Jump to Today" button if not already on today
                if (selectedDate != today) {
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    TextButton(
                        onClick = onJumpToToday,
                        modifier = Modifier.padding(start = Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Jump to today",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(Spacing.xs))
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            IconButton(
                onClick = onNextDay,
                enabled = canGoNext,
                modifier = Modifier
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day",
                    tint = if (canGoNext) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

