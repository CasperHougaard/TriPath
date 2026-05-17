package com.tripath.domain.running

import java.time.LocalDate

data class RunningWeekTarget(
    val weekIndex: Int, // 1-based
    val weekStartDate: LocalDate,
    val isRecoveryWeek: Boolean,
    val runsPerWeek: Int,
    val sessionDistancesMeters: List<Int> = emptyList(),
    val sessionTypes: List<RunningSessionType> = emptyList(),
    val progressionMode: RunningProgressionMode = RunningProgressionMode.DISTANCE_BUILDING
)
