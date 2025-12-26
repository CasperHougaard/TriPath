package com.tripath.ui.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tripath.ui.components.StatCard
import com.tripath.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun KeyMetricsGrid(
    totalTSS: Int,
    totalWorkouts: Int,
    totalDistance: Double,
    totalHours: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Row 1: TSS & Workouts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            StatCard(
                label = "Total TSS",
                value = "$totalTSS",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Workouts",
                value = "$totalWorkouts",
                icon = Icons.Default.FitnessCenter,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Row 2: Distance & Hours
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            StatCard(
                label = "Distance",
                value = formatDistance(totalDistance),
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Training Hours",
                value = String.format("%.1f", totalHours),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatDistance(meters: Double): String {
    return when {
        meters >= 1000 -> "${(meters / 1000).let { if (it % 1.0 == 0.0) it.toInt() else String.format("%.1f", it) }} km"
        else -> "${meters.roundToInt()} m"
    }
}

