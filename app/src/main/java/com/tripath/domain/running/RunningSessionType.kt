package com.tripath.domain.running

enum class RunningSessionType {
    EASY,
    RECOVERY,
    LONG_RUN,
    TEMPO,
    THRESHOLD,
    INTERVALS,
    PROGRESSION,
    RACE_PACE
}

fun RunningSessionType.toPlanSubTypeLabel(): String {
    return when (this) {
        RunningSessionType.EASY -> "Easy Run"
        RunningSessionType.RECOVERY -> "Recovery Run"
        RunningSessionType.LONG_RUN -> "Long Run"
        RunningSessionType.TEMPO -> "Tempo Run"
        RunningSessionType.THRESHOLD -> "Threshold Run"
        RunningSessionType.INTERVALS -> "Intervals"
        RunningSessionType.PROGRESSION -> "Progression Run"
        RunningSessionType.RACE_PACE -> "Race Pace Run"
    }
}

fun runningSessionTypeFromPlanSubType(subType: String?): RunningSessionType? {
    return when (subType?.trim()?.lowercase()) {
        "easy run" -> RunningSessionType.EASY
        "recovery run" -> RunningSessionType.RECOVERY
        "long run" -> RunningSessionType.LONG_RUN
        "tempo run" -> RunningSessionType.TEMPO
        "threshold run" -> RunningSessionType.THRESHOLD
        "intervals", "interval run", "interval workout" -> RunningSessionType.INTERVALS
        "progression run" -> RunningSessionType.PROGRESSION
        "race pace run" -> RunningSessionType.RACE_PACE
        else -> null
    }
}