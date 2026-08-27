package com.tripath.domain.health

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.ProjectionMode
import com.tripath.domain.ProjectionSource
import java.time.LocalDate

/**
 * Planned training load per date, for the days a fuel target is allowed to look ahead to.
 *
 * [FuelAnalytics] stays deliberately ignorant of where this came from — it receives two maps and
 * uses the larger of planned and actual. The judgement about *which* future sessions are firm enough
 * to fuel for lives here, in one place, so the Nutrition screen and the readiness model cannot
 * disagree about what tomorrow holds.
 */
data class PlannedLoad(
    val tssByDate: Map<LocalDate, Int> = emptyMap(),
    val minutesByDate: Map<LocalDate, Int> = emptyMap()
) {
    companion object {

        val NONE = PlannedLoad()

        /**
         * Planned load from today through [horizonEnd].
         *
         * **Today is taken from scheduled plans only, never from the projection.** In
         * [ProjectionMode.RECENT_PATTERN] the projection *infers* a session from the last eight
         * weeks' rhythm, which is a reasonable way to shape a forecast and a bad way to size the
         * number the athlete is about to eat to. A real plan is a claim someone made; a pattern is
         * an average.
         *
         * From tomorrow onward the projection fills the gaps, and a real plan still wins over an
         * inferred one on the same date.
         */
        fun forHorizon(
            mode: ProjectionMode,
            completedWorkouts: List<WorkoutLog>,
            plans: List<TrainingPlan>,
            today: LocalDate,
            horizonEnd: LocalDate
        ): PlannedLoad {
            val scheduled = plans
                .filter { !it.date.isBefore(today) && !it.date.isAfter(horizonEnd) }
                .groupBy { it.date }

            val from = today.plusDays(1)
            val projected = if (from.isAfter(horizonEnd)) {
                emptyMap()
            } else {
                ProjectionSource.project(
                    mode = mode,
                    completedWorkouts = completedWorkouts,
                    plans = plans,
                    from = from,
                    to = horizonEnd,
                    today = today
                ).tssByDate()
            }

            return PlannedLoad(
                tssByDate = projected + scheduled.mapValues { (_, p) -> p.sumOf { it.plannedTSS } },
                // Only scheduled sessions carry a duration; a projected day has a TSS and nothing
                // else. That is fine — duration only breaks ties TSS cannot.
                minutesByDate = scheduled.mapValues { (_, p) -> p.sumOf { it.durationMinutes } }
            )
        }
    }
}
