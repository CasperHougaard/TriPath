package com.tripath.ui.model

import java.time.LocalDate

enum class FormStatus {
    FRESHNESS,      // TSB > +5 (Green)
    OPTIMAL,        // TSB between -10 and -30 (Blue)
    OVERREACHING    // TSB < -30 (Red/Warning)
}

data class PerformanceDataPoint(
    val date: LocalDate,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val label: String, // Date label for x-axis
    /**
     * True when this point is a forecast derived from planned workouts (dates after today)
     * rather than from completed workouts. Used by the chart to render projected values distinctly.
     */
    val isProjected: Boolean = false
)

