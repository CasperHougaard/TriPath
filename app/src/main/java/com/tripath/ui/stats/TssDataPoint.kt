package com.tripath.ui.stats

import java.time.LocalDate
import com.tripath.data.model.WorkoutType

/**
 * Data point for TSS trend chart, supporting discipline split.
 */
data class TssDataPoint(
    val label: String,
    val tss: Int,
    val date: LocalDate,
    val type: WorkoutType? = null
)
