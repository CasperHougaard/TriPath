package com.tripath.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripath.data.model.WorkoutType
import com.tripath.ui.components.toIcon
import com.tripath.ui.dashboard.DashboardActivityCellState
import com.tripath.ui.dashboard.DashboardActivityDisplayState
import com.tripath.ui.dashboard.DashboardDayColumnState
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor
import java.time.LocalDate

private val matrixRowTypes = listOf(
    WorkoutType.STRENGTH,
    WorkoutType.RUN,
    WorkoutType.BIKE,
    WorkoutType.HIKE,
    WorkoutType.WALK,
    WorkoutType.SWIM,
    WorkoutType.OTHER
)

@Composable
fun WeeklyActivityMatrix(
    dayColumns: List<DashboardDayColumnState>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (dayColumns.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Matrix rows (no left labels)
            matrixRowTypes.forEachIndexed { rowIdx, type ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dayColumns.forEach { dayColumn ->
                        val cell = dayColumn.cells[rowIdx]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when (cell.displayState) {
                                        DashboardActivityDisplayState.NONE -> Color.Transparent
                                        DashboardActivityDisplayState.PLANNED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        DashboardActivityDisplayState.COMPLETED, DashboardActivityDisplayState.MIXED -> cell.workoutType.toColor().copy(alpha = 0.14f)
                                    }
                                )
                                .clickable { onDayClick(dayColumn.date) }
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cell.displayState != DashboardActivityDisplayState.NONE) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = cell.workoutType.toIcon(),
                                        contentDescription = cell.workoutType.name,
                                        tint = when (cell.displayState) {
                                            DashboardActivityDisplayState.PLANNED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                            DashboardActivityDisplayState.COMPLETED, DashboardActivityDisplayState.MIXED -> cell.workoutType.toColor()
                                            DashboardActivityDisplayState.NONE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = cell.displayMinutes.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = when (cell.displayState) {
                                            DashboardActivityDisplayState.PLANNED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                            DashboardActivityDisplayState.COMPLETED, DashboardActivityDisplayState.MIXED -> cell.workoutType.toColor()
                                            DashboardActivityDisplayState.NONE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                                        },
                                        fontWeight = if (cell.displayState == DashboardActivityDisplayState.PLANNED) FontWeight.Medium else FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(4.dp))
                            }
                        }
                    }
                }
            }

            // Total row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dayColumns.forEach { dayColumn ->
                    val total = dayColumn.cells.sumOf { it.displayMinutes }
                    Text(
                        text = total.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Removed MatrixRowLabel: no left labels

@Composable
private fun MatrixActivityCell(
    cell: DashboardActivityCellState,
    isToday: Boolean,
    isSelected: Boolean
) {
    val workoutColor = cell.workoutType.toColor()
    val isActive = cell.displayState != DashboardActivityDisplayState.NONE
    val backgroundColor = when (cell.displayState) {
        DashboardActivityDisplayState.NONE -> Color.Transparent
        DashboardActivityDisplayState.PLANNED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        DashboardActivityDisplayState.COMPLETED,
        DashboardActivityDisplayState.MIXED -> workoutColor.copy(alpha = 0.14f)
    }
    val contentColor = when (cell.displayState) {
        DashboardActivityDisplayState.NONE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
        DashboardActivityDisplayState.PLANNED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        DashboardActivityDisplayState.COMPLETED,
        DashboardActivityDisplayState.MIXED -> workoutColor
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = cell.workoutType.toIcon(),
                    contentDescription = cell.workoutType.name,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = cell.displayMinutes.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    color = contentColor,
                    fontWeight = if (cell.displayState == DashboardActivityDisplayState.PLANNED) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Bold
                    },
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Spacer(
                modifier = Modifier
                    .size(4.dp)
                    .alpha(if (isToday || isSelected) 0.6f else 1f)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isToday || isSelected) 0.14f else 0.08f),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

private fun WorkoutType.toMatrixLabel(): String {
    return when (this) {
        WorkoutType.STRENGTH -> "STR"
        WorkoutType.RUN -> "RUN"
        WorkoutType.BIKE -> "BIKE"
        WorkoutType.HIKE -> "HIKE"
        WorkoutType.WALK -> "WALK"
        WorkoutType.SWIM -> "SWIM"
        WorkoutType.OTHER -> "OTHER"
    }
}