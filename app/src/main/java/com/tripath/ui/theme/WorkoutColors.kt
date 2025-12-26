package com.tripath.ui.theme

import androidx.compose.ui.graphics.Color
import com.tripath.data.model.WorkoutType

/**
 * Extension function to get the appropriate color for each workout type.
 * Makes it easy to reuse colors in graphs and charts later.
 */
fun WorkoutType.toColor(): Color {
    return when (this) {
        WorkoutType.SWIM -> Color(0xFF00B8FF)      // Electric Blue - High contrast
        WorkoutType.RUN -> Color(0xFFFF6B35)       // Safety Orange - High contrast
        WorkoutType.STRENGTH -> Color(0xFF9C27B0)  // Purple - Visually distinct and dominant
        WorkoutType.BIKE -> Color(0xFF1565C0)      // Triathlon Blue
        WorkoutType.OTHER -> Color(0xFF607D8B)     // Blue Grey - Neutral but visible
    }
}

