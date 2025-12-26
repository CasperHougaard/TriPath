package com.tripath.ui.planner

import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import java.time.LocalDate

data class WeekDay(
    val date: LocalDate,
    val dayName: String,
    val workouts: List<TrainingPlan> = emptyList(),
    val completedLogs: List<WorkoutLog> = emptyList(),
    val specialPeriods: List<SpecialPeriod> = emptyList(),
    val isToday: Boolean = false
)
