package com.tripath.ui.planner

import com.tripath.data.local.database.entities.TrainingPlan
import java.time.LocalDate

data class WeekDay(
    val date: LocalDate,
    val dayName: String,
    val workouts: List<TrainingPlan>
)

