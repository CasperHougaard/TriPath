package com.tripath.domain.running

enum class RunWorkoutStepType {
    WARM_UP,
    EASY,
    STEADY,
    TEMPO,
    THRESHOLD,
    INTERVAL,
    RECOVERY_JOG,
    VERY_EASY,
    COMFORTABLY_HARD,
    RACE_PACE,
    LONG_AEROBIC,
    COOL_DOWN
}

enum class RunStepDurationType {
    TIME,
    DISTANCE
}

enum class RunStepTargetType {
    NONE,
    PACE,
    EFFORT,
    HEART_RATE
}

data class RunWorkoutStep(
    val order: Int,
    val type: RunWorkoutStepType,
    val durationType: RunStepDurationType,
    val durationValue: Int,
    val targetType: RunStepTargetType,
    val targetLow: Int? = null,
    val targetHigh: Int? = null,
    val description: String
)

data class StructuredRunWorkout(
    val title: String,
    val sessionType: RunningSessionType,
    val totalDistanceMeters: Int? = null,
    val estimatedDurationMinutes: Int,
    val steps: List<RunWorkoutStep>,
    val summaryText: String
)