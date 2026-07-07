package com.tripath.domain.running

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.model.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

object RunGoalTrainingPlanMapper {
    private const val CONSERVATIVE_MINUTES_PER_KM = 7.0
    private const val MIN_RUN_DURATION_MINUTES = 20
    private const val LONG_RUN_TSS_PER_MINUTE = 1.2
    private val orderedWeekDays = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )
    private val defaultPatterns = mapOf(
        1 to listOf(DayOfWeek.SUNDAY),
        2 to listOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
        3 to listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
        4 to listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
        5 to listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
        6 to listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        7 to orderedWeekDays
    )

    /**
     * @param planStartDate the start of the first counting week (a Monday); progression weeks are laid
     *   out from here.
     * @param earliestRunDate the first day a run may be placed on. When it falls before [planStartDate]
     *   (i.e. the plan starts mid-week), a partial lead-in week mirroring week 1 is prepended on the
     *   remaining days. This lead-in does not count toward the progression — it just lets the athlete
     *   start now instead of waiting for the week boundary.
     */
    fun mapToTrainingPlans(
        goal: RunningGoal,
        progressionResult: RunningProgressionResult,
        preferredRunningDays: List<DayOfWeek>?,
        planStartDate: LocalDate,
        strengthDates: List<LocalDate> = emptyList(),
        earliestRunDate: LocalDate = planStartDate
    ): List<TrainingPlan> {
        val preferredDays = (preferredRunningDays ?: goal.preferredDays).orEmpty()

        fun plansForWeek(weeklyTarget: RunningWeekTarget, weekStartDate: LocalDate): List<TrainingPlan> {
            val weekEndExclusive = weekStartDate.plusDays(7)
            val strengthWeekdays = strengthDates
                .filter { !it.isBefore(weekStartDate) && it.isBefore(weekEndExclusive) }
                .map { it.dayOfWeek }
                .toSet()
            return mapWeek(
                weeklyTarget = weeklyTarget,
                weekStartDate = weekStartDate,
                preferredDays = preferredDays,
                strengthWeekdays = strengthWeekdays
            )
        }

        val plans = mutableListOf<TrainingPlan>()

        // Non-counting partial lead-in week: when the plan starts mid-week, mirror week 1 on the days
        // that remain before the first counting week so the athlete can start immediately.
        val firstTarget = progressionResult.weeklyTargets.firstOrNull()
        if (firstTarget != null && earliestRunDate.isBefore(planStartDate)) {
            val leadInWeekStart = planStartDate.minusWeeks(1)
            plans += plansForWeek(firstTarget, leadInWeekStart)
                .filter { !it.date.isBefore(earliestRunDate) }
        }

        plans += progressionResult.weeklyTargets.flatMap { weeklyTarget ->
            val weekStartDate = planStartDate.plusWeeks((weeklyTarget.weekIndex - 1).toLong())
            plansForWeek(weeklyTarget, weekStartDate)
        }
        return plans
    }

    private fun mapWeek(
        weeklyTarget: RunningWeekTarget,
        weekStartDate: LocalDate,
        preferredDays: List<DayOfWeek>,
        strengthWeekdays: Set<DayOfWeek>
    ): List<TrainingPlan> {
        val sessionDistances = weeklyTarget.sessionDistancesMeters
        if (sessionDistances.isEmpty()) return emptyList()

        val scheduledDates = resolveDates(
            weekStartDate = weekStartDate,
            runsPerWeek = sessionDistances.size,
            preferredDays = preferredDays,
            strengthWeekdays = strengthWeekdays
        )

        val sessionTypes = weeklyTarget.sessionTypes.takeIf { it.size == sessionDistances.size }
            ?: defaultSessionTypes(sessionDistances.size)
        val sessions = sessionDistances.zip(sessionTypes)
        val longRunDate = selectLongRunDate(scheduledDates)
        val longRunSession = sessions.lastOrNull { (_, type) -> type == RunningSessionType.LONG_RUN }
            ?: sessions.maxBy { it.first }
        val otherDates = scheduledDates.filter { it != longRunDate }
        val otherSessions = sessions.toMutableList().apply {
            remove(longRunSession)
        }
        val sessionsByDate = buildMap<LocalDate, Pair<Int, RunningSessionType>> {
            otherDates.zip(otherSessions).forEach { (date, session) ->
                put(date, session)
            }
            put(longRunDate, longRunSession)
        }

        return scheduledDates.map { date ->
            val (distanceMeters, sessionType) = checkNotNull(sessionsByDate[date])
            val durationMinutes = estimateDurationMinutes(distanceMeters)
            TrainingPlan(
                date = date,
                type = WorkoutType.RUN,
                subType = sessionType.toPlanSubTypeLabel(),
                durationMinutes = durationMinutes,
                plannedTSS = estimatePlannedTss(durationMinutes, sessionType),
                plannedDistanceMeters = distanceMeters
            )
        }
    }

    private fun defaultSessionTypes(runsPerWeek: Int): List<RunningSessionType> {
        return if (runsPerWeek <= 1) {
            listOf(RunningSessionType.LONG_RUN)
        } else {
            List(runsPerWeek - 1) { RunningSessionType.EASY } + RunningSessionType.LONG_RUN
        }
    }

    private fun resolveDates(
        weekStartDate: LocalDate,
        runsPerWeek: Int,
        preferredDays: List<DayOfWeek>,
        strengthWeekdays: Set<DayOfWeek>
    ): List<LocalDate> {
        val weekDates = (0..6).map { weekStartDate.plusDays(it.toLong()) }
        val chosenDays = selectRunDays(
            runsPerWeek = runsPerWeek,
            preferredRunningDays = preferredDays.toSet(),
            strengthWeekdays = strengthWeekdays
        ).toSet()

        return weekDates.filter { it.dayOfWeek in chosenDays }
    }

    private fun selectRunDays(
        runsPerWeek: Int,
        preferredRunningDays: Set<DayOfWeek>,
        strengthWeekdays: Set<DayOfWeek> = emptySet()
    ): List<DayOfWeek> {
        val targetRuns = runsPerWeek.coerceIn(1, orderedWeekDays.size)
        val defaultPattern = defaultPatterns.getValue(targetRuns)
        val preferredDays = preferredRunningDays.intersect(orderedWeekDays.toSet())

        if (preferredDays.isEmpty() && strengthWeekdays.isEmpty()) {
            return defaultPattern
        }

        return generateDayCombinations(targetRuns)
            .maxWithOrNull(compareBy<List<DayOfWeek>>(
                // Hard rule: never run on a strength day.
                { it.none { day -> day in strengthWeekdays } },
                // Soft preference: run the day immediately before a strength day.
                { it.count { day -> day.plus(1L) in strengthWeekdays } },
                { countConsecutiveRuns(it) == 0 },
                { preferredOverlap(it, preferredDays) },
                { longRunPreferenceScore(it) },
                { minimumCircularGap(it) },
                { -gapSpread(it) },
                { defaultOverlap(it, defaultPattern) }
            ))
            ?: defaultPattern
    }

    private fun generateDayCombinations(targetRuns: Int): List<List<DayOfWeek>> {
        val combinations = mutableListOf<List<DayOfWeek>>()

        fun build(startIndex: Int, selected: List<DayOfWeek>) {
            if (selected.size == targetRuns) {
                combinations += selected
                return
            }

            for (index in startIndex..orderedWeekDays.size - (targetRuns - selected.size)) {
                build(index + 1, selected + orderedWeekDays[index])
            }
        }

        build(startIndex = 0, selected = emptyList())
        return combinations
    }

    private fun countConsecutiveRuns(days: List<DayOfWeek>): Int =
        circularGaps(days).count { it == 1 }

    private fun preferredOverlap(days: List<DayOfWeek>, preferredDays: Set<DayOfWeek>): Int =
        days.count { it in preferredDays }

    private fun defaultOverlap(days: List<DayOfWeek>, defaultPattern: List<DayOfWeek>): Int =
        days.count { it in defaultPattern }

    private fun longRunPreferenceScore(days: List<DayOfWeek>): Int {
        val longRunDay = selectLongRunDay(days)
        return when (longRunDay) {
            DayOfWeek.SUNDAY -> 2
            DayOfWeek.SATURDAY -> 1
            else -> 0
        }
    }

    private fun minimumCircularGap(days: List<DayOfWeek>): Int =
        circularGaps(days).minOrNull() ?: 0

    private fun gapSpread(days: List<DayOfWeek>): Int {
        val gaps = circularGaps(days)
        return (gaps.maxOrNull() ?: 0) - (gaps.minOrNull() ?: 0)
    }

    private fun circularGaps(days: List<DayOfWeek>): List<Int> {
        if (days.size <= 1) return listOf(7)

        val indexes = days.map { orderedWeekDays.indexOf(it) }.sorted()
        return indexes.indices.map { index ->
            val current = indexes[index]
            val next = indexes[(index + 1) % indexes.size]
            if (index == indexes.lastIndex) {
                (next + orderedWeekDays.size) - current
            } else {
                next - current
            }
        }
    }

    private fun selectLongRunDate(scheduledDates: List<LocalDate>): LocalDate {
        val longRunDay = selectLongRunDay(scheduledDates.map { it.dayOfWeek })
        return scheduledDates.firstOrNull { it.dayOfWeek == longRunDay }
            ?: scheduledDates.max()
    }

    private fun selectLongRunDay(days: List<DayOfWeek>): DayOfWeek {
        return when {
            DayOfWeek.SUNDAY in days -> DayOfWeek.SUNDAY
            DayOfWeek.SATURDAY in days -> DayOfWeek.SATURDAY
            else -> days.maxBy { orderedWeekDays.indexOf(it) }
        }
    }

    private fun estimateDurationMinutes(distanceMeters: Int): Int {
        val estimated = ceil((distanceMeters / 1000.0) * CONSERVATIVE_MINUTES_PER_KM).toInt()
        return estimated.coerceAtLeast(MIN_RUN_DURATION_MINUTES)
    }

    private fun estimatePlannedTss(durationMinutes: Int, sessionType: RunningSessionType): Int {
        val multiplier = when (sessionType) {
            RunningSessionType.EASY -> 1.0
            RunningSessionType.RECOVERY -> 0.85
            RunningSessionType.LONG_RUN -> LONG_RUN_TSS_PER_MINUTE
            RunningSessionType.TEMPO -> 1.1
            RunningSessionType.THRESHOLD -> 1.15
            RunningSessionType.INTERVALS -> 1.2
            RunningSessionType.PROGRESSION -> 1.1
            RunningSessionType.RACE_PACE -> 1.15
        }
        return (durationMinutes * multiplier).roundToInt()
    }
}