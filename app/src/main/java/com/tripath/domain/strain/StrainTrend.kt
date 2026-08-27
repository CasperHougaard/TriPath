package com.tripath.domain.strain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One day on the strain timeline: what was added that day, and what the athlete was carrying by
 * the end of it.
 *
 * [input] is the raw, undecayed load the day's sessions contributed — zero on a rest day. [state]
 * is the whole decayed picture as of that evening, exactly as [StrainTimeline.stateAt] would report
 * it if today were [date].
 */
data class StrainTrendDay(
    val date: LocalDate,
    val input: StrainVector,
    val state: StrainState
)

/**
 * The strain model run over a window of dates rather than at a single point.
 *
 * ## Why this exists
 * [StrainTimeline.stateAt] answers "where do I stand now", which is all the freshness bars ever
 * needed. A chart needs the same answer for every day in a window, and the callers that want it
 * (the freshness trend, the muscle map's day scrubber, the dashboard sparkline) should not each
 * re-derive the loop — nor pay for the sleep, HRV and fuelling work that
 * [ReadinessService.readinessHistory] does, none of which strain depends on.
 *
 * ## Every day is present, including rest days
 * [StrainAnalytics] only emits dates that had training, which is right for a decay calculation and
 * wrong for a chart: a stacked area over a sparse date list silently closes the gaps and draws a
 * rest week as if it were compressed into an afternoon. So [build] walks the calendar and fills
 * untrained days with [StrainVector.ZERO].
 */
data class StrainTrend(val days: List<StrainTrendDay> = emptyList()) {

    val hasData: Boolean get() = days.any { it.state.hasData }

    /** The window's last day — "today", for the callers that only want a headline. */
    val latest: StrainTrendDay? get() = days.lastOrNull()

    fun on(date: LocalDate): StrainTrendDay? = days.firstOrNull { it.date == date }

    /**
     * Freshness 0–100 per day for one channel, skipping days with no history behind them at all
     * (rather than reporting those as 100% fresh, which would draw a flat confident line across a
     * window the model knows nothing about).
     */
    fun freshnessSeries(channel: StrainChannel): List<Pair<LocalDate, Int>> =
        days.mapNotNull { day -> day.state[channel]?.let { day.date to it.freshness } }

    /** Undecayed load added per day for one channel. Continuous, so rest days read as zero. */
    fun inputSeries(channel: StrainChannel): List<Pair<LocalDate, Double>> =
        days.map { it.date to it.input[channel] }

    /** True when at least one day in the window had training on it. */
    val hasInput: Boolean get() = days.any { !it.input.isEmpty }

    /**
     * The trailing [count] days of this trend.
     *
     * Lets a screen load one long window and let the user narrow it without a round trip — the
     * expensive part is [build], and re-running it to drop days already computed would be waste.
     */
    fun lastDays(count: Int): StrainTrend =
        if (count >= days.size) this else StrainTrend(days.takeLast(count))

    companion object {

        /**
         * Runs the model once per day across `[from, to]`.
         *
         * Deliberately a plain loop over [StrainTimeline.stateAt] rather than a rolling
         * incremental pass. `stateAt` re-derives the 42-day baseline on every call, so this is
         * quadratic-ish in window length — about 270k multiply-adds for a 90-day window, which is
         * a few milliseconds and not worth trading the guarantee that a charted day and a queried
         * day are computed by the identical code path. If a window ever needs to be much longer
         * than a season, rewrite `baselines` to roll rather than special-casing it here.
         */
        fun build(history: StrainHistory, from: LocalDate, to: LocalDate): StrainTrend {
            if (to.isBefore(from)) return StrainTrend()

            val inputByDate = history.days.associate { it.date to it.strain }
            val dayCount = ChronoUnit.DAYS.between(from, to)

            return StrainTrend(
                (0..dayCount).map { offset ->
                    val date = from.plusDays(offset)
                    StrainTrendDay(
                        date = date,
                        input = inputByDate[date] ?: StrainVector.ZERO,
                        state = StrainTimeline.stateAt(history.days, date, history.muscleByDate)
                    )
                }
            )
        }
    }
}
