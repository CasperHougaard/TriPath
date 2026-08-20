package com.tripath.domain

import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.ProjectionMode
import com.tripath.data.model.WorkoutType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How much a projected figure should be believed. */
enum class ProjectionConfidence(val label: String) {
    HIGH("Likely"),
    MEDIUM("Roughly"),
    LOW("Rough guess"),
    NONE("No basis");

    /** The weaker of two confidences — a chain is only as good as its worst link. */
    fun coerceDownTo(other: ProjectionConfidence): ProjectionConfidence =
        if (other.ordinal > ordinal) other else this
}

/** One projected future day: expected load, where it comes from, and how firm it is. */
data class ProjectedDay(
    val date: LocalDate,
    val tss: Int,
    val byDiscipline: Map<WorkoutType, Int>,
    val confidence: ProjectionConfidence
)

/**
 * The future, as one object. Everything forward-looking reads this rather than deciding for itself.
 */
data class Projection(
    val mode: ProjectionMode,
    val days: Map<LocalDate, ProjectedDay> = emptyMap(),
    val confidence: ProjectionConfidence = ProjectionConfidence.NONE
) {
    /** The shape [TrainingMetricsCalculator.calculatePerformanceSeries] expects. */
    fun tssByDate(): Map<LocalDate, Int> = days.mapValues { (_, day) -> day.tss }

    fun forDate(date: LocalDate): ProjectedDay? = days[date]
}

/**
 * The single answer to "what training is coming?".
 *
 * CTL/ATL projection, carbohydrate preloading and projected readiness all need this, and letting
 * each of them work it out separately is how the Coach screen, the Fuel screen and the Planner end
 * up believing three different things about next week. One seam, one answer.
 *
 * ## The two modes
 * [ProjectionMode.PLANNED] reads the planner and is exactly as good as the plan is filled in.
 * [ProjectionMode.RECENT_PATTERN] assumes training carries on as it has been — the default while
 * the planner is incomplete, because projecting from an empty plan predicts a fitness collapse
 * that is not going to happen.
 *
 * ## Why the pattern mode shrinks
 * A per-weekday average goes false-precise very fast. Three Sundays with a long ride in an eight
 * week window is a real signal, but it is not the same claim as a scheduled session, and rendering
 * it as "87 TSS" invites it to be read as one. Each weekday × discipline cell is therefore pulled
 * toward that discipline's overall daily average in proportion to how rarely it actually occurred,
 * and carries the resulting [ProjectionConfidence] with it.
 */
object ProjectionSource {

    /** Trailing weeks the pattern is learned from. Long enough to contain a full training rhythm. */
    const val PATTERN_WINDOW_WEEKS = 8L

    /**
     * Pseudo-observations of the "this discipline is spread evenly" prior. A cell seen 3 times in
     * 8 weeks lands at `3/(3+2)` = 60% its own average and 40% the discipline's general level.
     */
    private const val PRIOR_STRENGTH = 2.0

    private const val MIN_HISTORY_DAYS_FOR_PATTERN = 14L

    fun project(
        mode: ProjectionMode,
        completedWorkouts: List<WorkoutLog>,
        plans: List<TrainingPlan>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate = LocalDate.now()
    ): Projection = when (mode) {
        ProjectionMode.PLANNED -> fromPlans(plans, from, to)
        ProjectionMode.RECENT_PATTERN -> fromRecentPattern(completedWorkouts, from, to, today)
    }

    // ---- Planned -------------------------------------------------------------------------------

    private fun fromPlans(plans: List<TrainingPlan>, from: LocalDate, to: LocalDate): Projection {
        val inRange = plans.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val days = inRange.groupBy { it.date }.mapValues { (date, dayPlans) ->
            ProjectedDay(
                date = date,
                tss = dayPlans.sumOf { it.plannedTSS },
                byDiscipline = dayPlans.groupBy { it.type }
                    .mapValues { (_, p) -> p.sumOf { it.plannedTSS } },
                // A scheduled session is the firmest claim available about the future. It may not
                // happen, but nothing else here knows better.
                confidence = ProjectionConfidence.HIGH
            )
        }
        return Projection(
            mode = ProjectionMode.PLANNED,
            days = days,
            confidence = if (days.isEmpty()) ProjectionConfidence.NONE else ProjectionConfidence.HIGH
        )
    }

    // ---- Recent pattern ------------------------------------------------------------------------

    private fun fromRecentPattern(
        completedWorkouts: List<WorkoutLog>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): Projection {
        val windowStart = today.minusWeeks(PATTERN_WINDOW_WEEKS)
        val window = completedWorkouts.filter {
            !it.isIgnored && !it.date.isBefore(windowStart) && !it.date.isAfter(today)
        }
        if (window.isEmpty()) return Projection(ProjectionMode.RECENT_PATTERN)

        val earliest = window.minOf { it.date }
        val historyDays = ChronoUnit.DAYS.between(earliest, today) + 1
        if (historyDays < MIN_HISTORY_DAYS_FOR_PATTERN) {
            return Projection(ProjectionMode.RECENT_PATTERN)
        }

        // Every day in the window counts, including rest days — a discipline done three times in
        // eight weeks must average out to a small daily number, not to its session size.
        val windowDays = ChronoUnit.DAYS.between(windowStart, today) + 1
        val weekdayCounts = (0 until windowDays).map { windowStart.plusDays(it).dayOfWeek }
            .groupingBy { it }.eachCount()

        val disciplines = window.map { it.type }.distinct()
        val cells = mutableMapOf<Pair<java.time.DayOfWeek, WorkoutType>, Cell>()

        disciplines.forEach { type ->
            val ofType = window.filter { it.type == type }
            val disciplineDailyMean = ofType.sumOf { it.computedTSS ?: 0 }.toDouble() / windowDays

            weekdayCounts.forEach { (weekday, weekdayCount) ->
                val onWeekday = ofType.filter { it.date.dayOfWeek == weekday }
                val cellMean = onWeekday.sumOf { it.computedTSS ?: 0 }.toDouble() / weekdayCount
                // How many of those weekdays actually carried this discipline. This — not the
                // arithmetic mean — is what says whether the pattern is real.
                val occurrences = onWeekday.map { it.date }.distinct().size

                val k = occurrences / (occurrences + PRIOR_STRENGTH)
                cells[weekday to type] = Cell(
                    tss = k * cellMean + (1 - k) * disciplineDailyMean,
                    confidence = confidenceFor(occurrences)
                )
            }
        }

        val days = mutableMapOf<LocalDate, ProjectedDay>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val byDiscipline = disciplines.mapNotNull { type ->
                val cell = cells[cursor.dayOfWeek to type] ?: return@mapNotNull null
                val tss = cell.tss.roundToInt()
                if (tss <= 0) null else type to tss
            }.toMap()

            if (byDiscipline.isNotEmpty()) {
                // The day is only as firm as the discipline doing most of the work on it.
                val dominant = byDiscipline.maxByOrNull { it.value }!!.key
                days[cursor] = ProjectedDay(
                    date = cursor,
                    tss = byDiscipline.values.sum(),
                    byDiscipline = byDiscipline,
                    confidence = cells[cursor.dayOfWeek to dominant]?.confidence
                        ?: ProjectionConfidence.LOW
                )
            }
            cursor = cursor.plusDays(1)
        }

        return Projection(
            mode = ProjectionMode.RECENT_PATTERN,
            days = days,
            confidence = overallConfidence(historyDays, days.values)
        )
    }

    private data class Cell(val tss: Double, val confidence: ProjectionConfidence)

    /** How often a weekday × discipline cell actually fired across the window. */
    private fun confidenceFor(occurrences: Int): ProjectionConfidence = when {
        occurrences >= 6 -> ProjectionConfidence.HIGH
        occurrences >= 3 -> ProjectionConfidence.MEDIUM
        occurrences >= 1 -> ProjectionConfidence.LOW
        else -> ProjectionConfidence.NONE
    }

    /**
     * A projection can be no better than its history is long, nor than its own days are firm — a
     * full eight weeks of erratic training still projects erratically.
     */
    private fun overallConfidence(
        historyDays: Long,
        days: Collection<ProjectedDay>
    ): ProjectionConfidence {
        if (days.isEmpty()) return ProjectionConfidence.NONE
        val fromHistory = when {
            historyDays >= 56 -> ProjectionConfidence.HIGH
            historyDays >= 28 -> ProjectionConfidence.MEDIUM
            else -> ProjectionConfidence.LOW
        }
        val best = days.minByOrNull { it.confidence.ordinal }?.confidence ?: ProjectionConfidence.NONE
        return fromHistory.coerceDownTo(best)
    }
}
