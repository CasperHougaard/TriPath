package com.tripath.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripath.data.model.WorkoutType
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.toColor

/**
 * Get the appropriate icon for each workout type.
 */
fun WorkoutType.toIcon(): ImageVector {
    return when (this) {
        WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
        WorkoutType.SWIM -> Icons.Default.Pool
        WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
        WorkoutType.OTHER -> Icons.AutoMirrored.Filled.DirectionsWalk
    }
}

/**
 * Sport badge component displaying workout type with icon and color coding.
 * Used in workout cards, calendar views, and charts for quick type identification.
 *
 * @param workoutType Type of workout to display
 * @param size Size of the badge (default 32dp)
 * @param modifier Modifier for the badge
 */
@Composable
fun WorkoutBadge(
    workoutType: WorkoutType,
    size: Int = 32,
    modifier: Modifier = Modifier
) {
    val badgeSize = size.dp
    val color = workoutType.toColor()
    val icon = workoutType.toIcon()
    val iconSize = (size * 0.55f).dp
    
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = workoutType.name,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Text badge for displaying categories, counts, or status.
 * 
 * @param text Text to display in badge
 * @param backgroundColor Background color (defaults to primary)
 * @param contentColor Text color (defaults to onPrimary)
 * @param modifier Modifier for the badge
 */
@Composable
fun TextBadge(
    text: String,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BadgePreview() {
    TriPathTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.lg)
        ) {
            WorkoutBadge(workoutType = WorkoutType.SWIM)
            WorkoutBadge(workoutType = WorkoutType.BIKE)
            WorkoutBadge(workoutType = WorkoutType.RUN)
            WorkoutBadge(workoutType = WorkoutType.STRENGTH)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextBadgePreview() {
    TriPathTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
        ) {
            TextBadge(text = "NEW")
            TextBadge(
                text = "COMPLETED",
                backgroundColor = Color(0xFF4CAF50)
            )
            TextBadge(
                text = "12",
                backgroundColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

