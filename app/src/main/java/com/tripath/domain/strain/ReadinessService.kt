package com.tripath.domain.strain

import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.model.ProjectionMode
import com.tripath.data.model.UserProfile
import com.tripath.data.model.WorkoutType
import com.tripath.domain.ProjectionSource
import com.tripath.domain.TrainingMetricsCalculator
import com.tripath.domain.coach.PlanConflict
import com.tripath.domain.coach.PlannedStrainAdvisor
import com.tripath.domain.health.CombinedAnalytics
import com.tripath.domain.health.FuelAnalytics
import com.tripath.domain.health.PlannedLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One day's lifting, summarised for the readiness detail screen rather than for scoring — the
 * score only ever sees [StrainMapper]'s aggregated vectors, never this.
 */
data class LiftContributionDay(
    val date: LocalDate,
    val workingSets: Int,
    val exercises: List<String>,
    /** Display muscle groups this day loaded, ordered by how much load each carried. */
    val muscleGroups: List<String>
)

/**
 * The one place readiness is computed.
 *
 * Both the Coach screen and the LiftPath bridge read this rather than assembling their own inputs,
 * for the same reason [ProjectionSource] exists: two callers deriving "am I ready" separately is
 * how the phone and the watch end up disagreeing about the same morning.
 *
 * Everything it touches is already stored; nothing here writes.
 */
@Singleton
class ReadinessService @Inject constructor(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager
) {

    /**
     * History fed to the fuel and strain models. Long enough for the strain baseline (42 days) plus
     * its decay run-up, and for the adaptive expenditure ladder to reach its top rung.
     */
    private val analysisWindowDays = 120L

    /**
     * [source] narrows which data sources the strain model reads, for the freshness detail screen's
     * toggle. It touches nothing else: TSB, sleep, HRV and fuelling are whole-athlete signals with no
     * per-app provenance to filter on, so a restricted score differs from the full one only in its
     * regional-load component and its discipline verdicts.
     */
    suspend fun currentReadiness(
        today: LocalDate = LocalDate.now(),
        source: StrainSource = StrainSource.BOTH
    ): ReadinessAssessment {
        val profile = preferencesManager.getUserProfile()
        val strain = currentStrain(today, source)

        val allWorkouts = database.workoutLogDao().getAllOnce()
        val workouts = allWorkouts.filter { !it.isIgnored }
        val tsb = TrainingMetricsCalculator
            .calculatePerformanceMetrics(workouts, today)
            .tsb

        val fuel = CombinedAnalytics.build(
            allWorkouts = allWorkouts,
            nutrition = database.nutritionLogDao().getAllOnce(),
            sleep = database.sleepLogDao().getAllOnce(),
            bodyComposition = database.bodyCompositionDao().getAllOnce(),
            profile = profile,
            periodDays = analysisWindowDays,
            today = today,
            dailyActivity = database.dailyActivityDao().getAllOnce(),
            // The fuelling driver has to be judged against what the athlete needs *today*, which
            // depends on today's session and tomorrow's. Without this it is measured against a
            // target sized as though every day were a rest day.
            plannedLoad = PlannedLoad.forHorizon(
                mode = profile?.effectiveProjectionMode ?: ProjectionMode.DEFAULT,
                completedWorkouts = workouts,
                plans = database.trainingPlanDao().getAllOnce(),
                today = today,
                horizonEnd = FuelAnalytics.horizonEnd(today)
            )
        ).fuel

        val dailyTss = workouts.groupBy { it.date }
            .mapValues { (_, logs) -> logs.sumOf { it.computedTSS ?: 0 } }

        val inputs = ReadinessAnalytics.buildInputs(
            strain = strain,
            tsb = tsb,
            sleep = database.sleepLogDao().getAllOnce(),
            wellness = database.wellnessDao().getAllLogsOnce(),
            dailyActivity = database.dailyActivityDao().getAllOnce(),
            fuel = fuel,
            sleepNeedMinutes = profile?.effectiveSleepNeedMinutes
                ?: UserProfile.DEFAULT_SLEEP_NEED_MINUTES,
            today = today,
            weeklyLoadRampPct = ReadinessAnalytics.weeklyRamp(dailyTss, today)
        )

        return ReadinessModel.assess(inputs)
    }

    /**
     * Readiness for a future day, from projected training only.
     *
     * Sleep, HRV and fuelling are not projected — the app has no idea what Friday's night will be,
     * and a forecast that quietly assumed one would be a fabrication wearing a number.
     *
     * [date] must be after [today]; anything else is not a projection, and returns an empty
     * assessment rather than quietly reinterpreting the question as "how ready were you then".
     * [currentReadiness] answers that one properly.
     */
    suspend fun projectedReadiness(
        date: LocalDate,
        today: LocalDate = LocalDate.now()
    ): ReadinessAssessment = projectedReadinessSeries(from = date, to = date, today = today)[date]
        ?: ReadinessModel.projected(StrainState(), null)

    /**
     * Projected readiness for every day in [from]..[to] — one entry per future day, for a planner
     * that wants to colour a whole grid.
     *
     * Loads once and then walks the range. Calling [projectedReadiness] per cell instead would
     * re-read four tables and rebuild the same projection twenty-eight times over for one screen.
     *
     * The projection itself always starts the day after [today], never at [from], because Friday's
     * legs depend on Wednesday's session: starting mid-range would silently drop the strain the
     * intervening days contribute. [from] only narrows what is *returned*.
     */
    suspend fun projectedReadinessSeries(
        from: LocalDate,
        to: LocalDate,
        today: LocalDate = LocalDate.now()
    ): Map<LocalDate, ReadinessAssessment> {
        val start = maxOf(from, today.plusDays(1))
        if (start.isAfter(to)) return emptyMap()

        val profile = preferencesManager.getUserProfile()
        val workouts = database.workoutLogDao().getAllOnce().filter { !it.isIgnored }
        val plans = database.trainingPlanDao().getAllOnce()

        val projection = ProjectionSource.project(
            mode = profile?.effectiveProjectionMode ?: ProjectionMode.DEFAULT,
            completedWorkouts = workouts,
            plans = plans,
            from = today.plusDays(1),
            to = to,
            today = today
        )

        // Today's strain decayed forward, with the projected sessions layered on as they arrive.
        val history = strainHistory(today).days + projection.days.values.map { projected ->
            DailyStrain(projected.date, projectedStrain(projected))
        }

        val tsbByDate = TrainingMetricsCalculator.calculatePerformanceSeries(
            logs = workouts,
            plannedTssByDate = projection.tssByDate(),
            seriesStart = start,
            seriesEnd = to,
            actualUntil = today
        ).associate { (date, metrics) -> date to metrics.tsb }

        // Pure arithmetic over data already in memory from here on.
        return withContext(Dispatchers.Default) {
            buildMap {
                var cursor = start
                while (!cursor.isAfter(to)) {
                    put(
                        cursor,
                        ReadinessModel.projected(
                            StrainTimeline.stateAt(history, cursor),
                            tsbByDate[cursor]
                        )
                    )
                    cursor = cursor.plusDays(1)
                }
            }
        }
    }

    /**
     * A 0-100 readiness score per day over the trailing [days], for the dashboard's condensed
     * trend graph.
     *
     * Nothing persists past readiness, so this recomputes every day from the same history
     * [currentReadiness] reads — loaded once here rather than once per day, since the workout,
     * sleep, wellness and fuel history is identical for every date in the window.
     */
    suspend fun readinessHistory(days: Long = 14, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> {
        val profile = preferencesManager.getUserProfile()
        val workouts = database.workoutLogDao().getAllOnce().filter { !it.isIgnored }
        val sleep = database.sleepLogDao().getAllOnce()
        val wellness = database.wellnessDao().getAllLogsOnce()
        val dailyActivity = database.dailyActivityDao().getAllOnce()
        val fuel = CombinedAnalytics.build(
            allWorkouts = database.workoutLogDao().getAllOnce(),
            nutrition = database.nutritionLogDao().getAllOnce(),
            sleep = sleep,
            bodyComposition = database.bodyCompositionDao().getAllOnce(),
            profile = profile,
            periodDays = analysisWindowDays,
            today = today,
            dailyActivity = dailyActivity
        ).fuel
        val dailyTss = workouts.groupBy { it.date }
            .mapValues { (_, logs) -> logs.sumOf { it.computedTSS ?: 0 } }
        val history = strainHistory(today)

        val tsbByDate = TrainingMetricsCalculator.calculatePerformanceSeries(
            logs = workouts,
            plannedTssByDate = emptyMap(),
            seriesStart = today.minusDays(days - 1),
            seriesEnd = today,
            actualUntil = today
        ).associate { (date, metrics) -> date to metrics.tsb }

        val sleepNeed = profile?.effectiveSleepNeedMinutes ?: UserProfile.DEFAULT_SLEEP_NEED_MINUTES

        return (0 until days).map { offset ->
            val date = today.minusDays(days - 1 - offset)
            val strain = StrainTimeline.stateAt(history.days, date, history.muscleByDate)
            val inputs = ReadinessAnalytics.buildInputs(
                strain = strain,
                tsb = tsbByDate[date],
                sleep = sleep,
                wellness = wellness,
                dailyActivity = dailyActivity,
                fuel = fuel,
                sleepNeedMinutes = sleepNeed,
                today = date,
                weeklyLoadRampPct = ReadinessAnalytics.weeklyRamp(dailyTss, date)
            )
            date to ReadinessModel.assess(inputs).score
        }
    }

    suspend fun currentStrain(
        today: LocalDate = LocalDate.now(),
        source: StrainSource = StrainSource.BOTH
    ): StrainState {
        val history = strainHistory(today, source)
        return StrainTimeline.stateAt(history.days, today, history.muscleByDate)
    }

    /**
     * Places in the coming plan where a session lands on tissue that will not have recovered.
     *
     * Lives here rather than at the call site so that [PlannedStrainAdvisor] is seeded with real
     * strain history: run over the plan alone, the first days of the week would start from a clean
     * slate and miss exactly the collisions that matter — the ones with what the athlete has just
     * done.
     */
    suspend fun planConflicts(
        today: LocalDate = LocalDate.now(),
        through: LocalDate = today.plusDays(DEFAULT_PLAN_HORIZON_DAYS)
    ): List<PlanConflict> {
        val plans = database.trainingPlanDao().getAllOnce()
            .filter { !it.date.isBefore(today) && !it.date.isAfter(through) }
        if (plans.isEmpty()) return emptyList()

        val history = strainHistory(today).days.filter { it.date.isBefore(today) }
        return withContext(Dispatchers.Default) {
            PlannedStrainAdvisor.findConflicts(plans, history)
        }
    }

    /**
     * The strain model run day by day over the trailing [days], for the charts.
     *
     * Deliberately *not* built on [readinessHistory], which assembles fuel, sleep, HRV and a TSB
     * series that strain does not depend on. Strain needs only the workout and LiftPath history
     * [strainHistory] already loads, so this is a fraction of the work — which matters, because
     * unlike a readiness score a strain chart is worth showing on a 90-day window.
     *
     * The per-day loop is pure arithmetic over data already in memory, so it runs on
     * [Dispatchers.Default] rather than holding an IO thread.
     */
    suspend fun strainTrend(
        days: Long = DEFAULT_TREND_DAYS,
        today: LocalDate = LocalDate.now(),
        source: StrainSource = StrainSource.BOTH
    ): StrainTrend {
        val history = strainHistory(today, source)
        return withContext(Dispatchers.Default) {
            StrainTrend.build(history, from = today.minusDays(days - 1), to = today)
        }
    }

    /**
     * What a single logged session cost, per channel.
     *
     * Applies the same deduplication rule [StrainAnalytics] does: a strength session with LiftPath
     * sets behind it is scored from the sets, not from its TSS. Without that, opening a lifting
     * session that LiftPath had detailed would report zero strain — [StrainMapper.forWorkout]
     * returns [StrainVector.ZERO] for exactly that case, because the aggregate path scores it
     * elsewhere.
     */
    suspend fun sessionStrain(log: WorkoutLog): SessionStrain {
        val liftSessions = database.liftPathDao().getAllSessionsOnce()
        val liftSets = database.liftPathDao().getAllSetsOnce()
        val hasLiftDetail = log.type == WorkoutType.STRENGTH &&
            log.date in StrainAnalytics.datesWithLiftDetail(liftSessions, liftSets)

        if (!hasLiftDetail) {
            return SessionStrain(date = log.date, added = StrainMapper.forWorkout(log))
        }

        val sessionIdsOnDate = liftSessions.filter { it.date == log.date }.map { it.id }.toSet()
        val catalog = database.liftPathDao().getAllExercisesOnce().associateBy { it.id }
        return SessionStrain(
            date = log.date,
            added = StrainMapper.forLiftSession(
                sets = liftSets.filter { it.sessionId in sessionIdsOnDate },
                catalog = catalog
            ),
            fromLiftDetail = true
        )
    }

    private suspend fun strainHistory(
        today: LocalDate,
        source: StrainSource = StrainSource.BOTH
    ): StrainHistory {
        val from = today.minusDays(analysisWindowDays)
        return StrainAnalytics.build(
            workouts = database.workoutLogDao().getAllOnce(),
            liftSessions = database.liftPathDao().getAllSessionsOnce(),
            liftSets = database.liftPathDao().getAllSetsOnce(),
            catalog = database.liftPathDao().getAllExercisesOnce(),
            from = from,
            to = today,
            source = source
        )
    }

    /**
     * Strain for a day that has not happened, from its projected discipline mix.
     *
     * Uses the same per-discipline vectors as a real session, so a projected Sunday ride loads the
     * legs exactly as an actual one would — only the confidence differs, and that is carried by
     * [com.tripath.domain.ProjectionConfidence] rather than by fudging the numbers.
     */
    private fun projectedStrain(day: com.tripath.domain.ProjectedDay): StrainVector =
        day.byDiscipline.entries.fold(StrainVector.ZERO) { acc, (type, tss) ->
            acc + StrainMapper.disciplineVector(type) * tss.toDouble()
        }

    /** Days of history behind the current assessment — for "still settling" copy in the UI. */
    suspend fun daysOfHistory(today: LocalDate = LocalDate.now()): Long {
        val earliest = database.workoutLogDao().getAllOnce().minOfOrNull { it.date } ?: return 0
        return ChronoUnit.DAYS.between(earliest, today).coerceAtLeast(0)
    }

    /**
     * LiftPath sessions still recent enough to be moving today's regional-load reading, newest
     * first.
     *
     * [LOOKBACK_DAYS] rather than the full strain window: even [StrainChannel.LOWER_IMPACT]'s
     * 96-hour tau has decayed to a few percent of its original cost by then, so anything older is
     * background noise on the detail screen rather than a driver of it.
     */
    suspend fun recentLiftContributions(today: LocalDate = LocalDate.now()): List<LiftContributionDay> {
        val from = today.minusDays(LOOKBACK_DAYS)
        val sessions = database.liftPathDao().getAllSessionsOnce()
            .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        if (sessions.isEmpty()) return emptyList()

        val sessionIds = sessions.map { it.id }.toSet()
        val sets = database.liftPathDao().getAllSetsOnce()
            .filter { it.sessionId in sessionIds && !it.isWarmup }
        val catalog = database.liftPathDao().getAllExercisesOnce().associateBy { it.id }
        val setsBySession = sets.groupBy { it.sessionId }

        return sessions.groupBy { it.date }.map { (date, daySessions) ->
            val daySets = daySessions.flatMap { setsBySession[it.id].orEmpty() }
            val muscleLoad = StrainMapper.muscleGroupLoad(daySets, catalog)
            LiftContributionDay(
                date = date,
                workingSets = daySets.size,
                exercises = daySets.mapNotNull { catalog[it.exerciseId]?.name }.distinct(),
                muscleGroups = muscleLoad.entries.sortedByDescending { it.value }.map { it.key }
            )
        }.sortedByDescending { it.date }
    }

    private companion object {
        const val LOOKBACK_DAYS = 14L

        /**
         * Default trend window. A season rather than a month, because the point of a strain chart
         * is to show a training block's shape — and comfortably inside the 120-day
         * [analysisWindowDays] the history load already covers.
         */
        const val DEFAULT_TREND_DAYS = 90L

        /**
         * How far ahead [planConflicts] looks. A week: far enough to catch the collision the athlete
         * can still do something about, near enough that the projection is worth acting on — a
         * warning about a session three weeks out is noise, since the plan will have changed.
         */
        const val DEFAULT_PLAN_HORIZON_DAYS = 7L
    }
}
