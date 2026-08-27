package com.tripath.domain.strain

import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSessionLog
import com.tripath.data.local.database.entities.LiftSetLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import java.time.LocalDate

/** Day-by-day strain, plus the per-muscle-group breakdown of the muscular part of it. */
data class StrainHistory(
    val days: List<DailyStrain> = emptyList(),
    val muscleByDate: List<Pair<LocalDate, Map<String, Double>>> = emptyList()
)

/**
 * Assembles strain history from what the database holds, joining endurance sessions with LiftPath's
 * set-level detail.
 *
 * Pure, like [com.tripath.domain.health.FuelAnalytics] — the strain model is derived at read time
 * and never persisted, so its constants can be retuned without a migration or a re-sync.
 *
 * ## The deduplication rule
 * A lifting session usually arrives twice: once as a Health Connect `STRENGTH` record (a duration
 * and nothing else) and once as LiftPath sets. Counting both would double every lifting day, so
 * where LiftPath has detail for a date, the Health Connect record for that date is dropped and the
 * set-level version wins — it knows which muscles did the work, which is the entire point.
 *
 * Matching is by **date** rather than by overlapping timestamps because LiftPath does not record a
 * wall-clock start; a session there is a date and a duration. Two separate lifting sessions on one
 * day therefore merge into that day's set list, which is the correct outcome anyway.
 *
 * ## Restricting to one source
 * [StrainSource] narrows which of the two inputs is read, for the freshness detail screen's source
 * toggle. It needs no second copy of the dedup rule: excluding LiftPath leaves nothing to be
 * superseded *by*, and excluding the workout log leaves nothing to be superseded. See [StrainSource]
 * for why that means `BOTH` is not the sum of the other two on a lifting day.
 */
object StrainAnalytics {

    fun build(
        workouts: List<WorkoutLog>,
        liftSessions: List<LiftSessionLog>,
        liftSets: List<LiftSetLog>,
        catalog: List<LiftExerciseCatalogEntry>,
        from: LocalDate,
        to: LocalDate,
        source: StrainSource = StrainSource.BOTH
    ): StrainHistory {
        val catalogById = catalog.associateBy { it.id }
        val setsBySession = liftSets.groupBy { it.sessionId }

        val liftSetsByDate: Map<LocalDate, List<LiftSetLog>> =
            if (!source.includesLiftDetail) emptyMap() else liftSessions
                .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
                .groupBy { it.date }
                .mapValues { (_, sessions) -> sessions.flatMap { setsBySession[it.id].orEmpty() } }
                .filterValues { it.isNotEmpty() }

        val strainByDate = mutableMapOf<LocalDate, StrainVector>()
        val muscleByDate = mutableMapOf<LocalDate, MutableMap<String, Double>>()

        fun addMuscleLoad(date: LocalDate, load: Map<String, Double>) {
            if (load.isEmpty()) return
            val day = muscleByDate.getOrPut(date) { mutableMapOf() }
            load.forEach { (group, value) -> day[group] = (day[group] ?: 0.0) + value }
        }

        if (source.includesWorkouts) {
            workouts.asSequence()
                .filter { !it.isIgnored && !it.date.isBefore(from) && !it.date.isAfter(to) }
                .forEach { log ->
                    val supersededByLiftPath = log.type == WorkoutType.STRENGTH &&
                        liftSetsByDate.containsKey(log.date)
                    val strain = StrainMapper.forWorkout(log, hasLiftDetail = supersededByLiftPath)
                    if (!strain.isEmpty) {
                        strainByDate[log.date] = (strainByDate[log.date] ?: StrainVector.ZERO) + strain
                    }
                    // Same log, same supersede flag: the diagram is a finer view of the strain that
                    // was just counted, never a second helping of it.
                    addMuscleLoad(log.date, StrainMapper.muscleGroupLoad(log, supersededByLiftPath))
                }
        }

        liftSetsByDate.forEach { (date, sets) ->
            val strain = StrainMapper.forLiftSession(sets, catalogById)
            if (!strain.isEmpty) {
                strainByDate[date] = (strainByDate[date] ?: StrainVector.ZERO) + strain
            }
            addMuscleLoad(date, StrainMapper.muscleGroupLoad(sets, catalogById))
        }

        return StrainHistory(
            days = strainByDate.map { (date, strain) -> DailyStrain(date, strain) }.sortedBy { it.date },
            muscleByDate = muscleByDate
                .filterValues { it.isNotEmpty() }
                .map { (date, byGroup) -> date to byGroup.toMap() }
                .sortedBy { it.first }
        )
    }

    /** Dates on which LiftPath supplied set-level detail — what the dedup rule keys on. */
    fun datesWithLiftDetail(
        liftSessions: List<LiftSessionLog>,
        liftSets: List<LiftSetLog>
    ): Set<LocalDate> {
        val sessionsWithSets = liftSets.map { it.sessionId }.toSet()
        return liftSessions.filter { it.id in sessionsWithSets }.map { it.date }.toSet()
    }
}
