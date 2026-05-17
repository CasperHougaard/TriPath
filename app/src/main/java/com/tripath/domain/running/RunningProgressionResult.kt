package com.tripath.domain.running

/**
 * Result of running progression calculation.
 */
data class RunningProgressionResult(
    val weeklyTargets: List<RunningWeekTarget>,
    val warnings: List<RunningProgressionWarning> = emptyList()
)
