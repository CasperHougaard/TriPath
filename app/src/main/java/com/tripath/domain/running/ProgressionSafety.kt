package com.tripath.domain.running

enum class ProgressionSafety(val maxWeeklyProgressPercent: Float) {
    CONSERVATIVE(0.08f),
    STANDARD(0.10f),
    AGGRESSIVE(0.15f)
}
