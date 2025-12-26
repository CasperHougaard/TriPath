package com.tripath.ui.coach.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripath.domain.TrainingPhase
import com.tripath.ui.theme.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun PhaseTimeline(
    currentDate: LocalDate,
    goalDate: LocalDate?,
    currentPhase: TrainingPhase?,
    modifier: Modifier = Modifier
) {
    if (goalDate == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Set a goal date to see your training timeline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    // Determine timeline range: Start 2.5 years before goal, or at least include today
    // To show "2.5 year journey", we fix start at Goal - 2.5 years (approx 130 weeks)
    // But if today is before that (unlikely for 2027 goal), stretch.
    // Let's set timeline start to: min(Today, Goal - 2.5 years)
    val timelineStart = if (currentDate.isBefore(goalDate.minusMonths(30))) {
        currentDate
    } else {
        goalDate.minusMonths(30) // 2.5 years
    }
    
    val timelineEnd = goalDate.plusWeeks(4) // Include transition
    
    val totalDays = ChronoUnit.DAYS.between(timelineStart, timelineEnd).toFloat()
    
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 10.sp
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Road to Ironman",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                val width = size.width
                val height = 20.dp.toPx()
                val topY = 20.dp.toPx()
                
                // Draw background track
                drawRect(
                    color = Color.DarkGray.copy(alpha = 0.3f),
                    topLeft = Offset(0f, topY),
                    size = Size(width, height)
                )
                
                // Draw phases
                // We define phases by their end offset from goal date
                // Transition: Goal to Goal+4w
                // Taper: Goal-3w to Goal
                // Peak: Goal-9w to Goal-3w
                // Build: Goal-21w to Goal-9w
                // Base: Start to Goal-21w
                // OffSeason: > 6 months out (dynamic based on today, but for timeline we visualize structural phases)
                // Let's visualize the "ideal" macro cycle phases relative to goal
                
                val phases = listOf(
                    Triple(TrainingPhase.Transition, 0, 4), // 0 to +4 weeks (relative to goal)
                    Triple(TrainingPhase.Taper, -3, 0), // -3 to 0 weeks
                    Triple(TrainingPhase.Peak, -9, -3), // -9 to -3 weeks
                    Triple(TrainingPhase.Build, -21, -9), // -21 to -9 weeks
                    Triple(TrainingPhase.Base, -52, -21) // -52 to -21 weeks (Year out)
                    // Everything before -52 we can treat as "Base" or "OffSeason" 
                    // Let's just draw Base extending back
                )

                // Draw Base extending from start to Build
                val baseEndWeeks = -21L
                val baseEndDate = goalDate.plusWeeks(baseEndWeeks)
                drawPhaseSegment(
                    start = timelineStart,
                    end = baseEndDate,
                    timelineStart = timelineStart,
                    totalDays = totalDays,
                    color = getPhaseColor(TrainingPhase.Base),
                    label = "Base",
                    width = width,
                    topY = topY,
                    height = height
                )
                
                // Draw specific phases
                // Note: We need to handle the case where "OffSeason" logic overrides Base dynamically
                // But for the TIMELINE visualization, standard periodization blocks are cleaner.
                // We can overlay "OffSeason" if today is far out.
                
                phases.forEach { (phase, startWeeks, endWeeks) ->
                    if (phase == TrainingPhase.Base) return@forEach // Handled above
                    
                    val pStart = goalDate.plusWeeks(startWeeks.toLong())
                    val pEnd = goalDate.plusWeeks(endWeeks.toLong())
                    
                    drawPhaseSegment(
                        start = pStart,
                        end = pEnd,
                        timelineStart = timelineStart,
                        totalDays = totalDays,
                        color = getPhaseColor(phase),
                        label = phase.displayName,
                        width = width,
                        topY = topY,
                        height = height
                    )
                }

                // Draw Today Marker
                val daysFromStart = ChronoUnit.DAYS.between(timelineStart, currentDate).toFloat()
                val todayX = (daysFromStart / totalDays) * width
                
                drawLine(
                    color = Color.White,
                    start = Offset(todayX, 0f),
                    end = Offset(todayX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                
                val todayText = "Today"
                val textLayout = textMeasurer.measure(todayText, labelStyle)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(todayX - textLayout.size.width / 2, 0f)
                )

                // Draw Goal Marker
                val goalDaysFromStart = ChronoUnit.DAYS.between(timelineStart, goalDate).toFloat()
                val goalX = (goalDaysFromStart / totalDays) * width
                 drawLine(
                    color = Color(0xFFFFD700), // Gold
                    start = Offset(goalX, 0f),
                    end = Offset(goalX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        // Current Phase Indicator (Text below)
        if (currentPhase != null) {
            Text(
                text = "Current Focus: ${currentPhase.displayName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

private fun DrawScope.drawPhaseSegment(
    start: LocalDate,
    end: LocalDate,
    timelineStart: LocalDate,
    totalDays: Float,
    color: Color,
    label: String,
    width: Float,
    topY: Float,
    height: Float
) {
    val startDays = ChronoUnit.DAYS.between(timelineStart, start).toFloat()
    val endDays = ChronoUnit.DAYS.between(timelineStart, end).toFloat()
    
    val startX = (startDays / totalDays) * width
    val endX = (endDays / totalDays) * width
    val segmentWidth = endX - startX
    
    if (segmentWidth > 0) {
        drawRect(
            color = color,
            topLeft = Offset(startX, topY),
            size = Size(segmentWidth, height)
        )
    }
}

private fun getPhaseColor(phase: TrainingPhase): Color {
    return when (phase) {
        TrainingPhase.Transition -> Color.Gray
        TrainingPhase.Taper -> Color(0xFFFFD700) // Gold
        TrainingPhase.Peak -> Color(0xFFFF4500) // OrangeRed
        TrainingPhase.Build -> Color(0xFFFF8C00) // DarkOrange
        TrainingPhase.Base -> Color(0xFF4CAF50) // Green
        TrainingPhase.OffSeason -> Color(0xFF2196F3) // Blue
    }
}

