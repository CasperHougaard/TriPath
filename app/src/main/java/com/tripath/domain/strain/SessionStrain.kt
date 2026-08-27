package com.tripath.domain.strain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp

/**
 * What one session cost, and how much of it is left.
 *
 * The four freshness bars answer "what am I carrying" across everything the athlete has done. This
 * answers the narrower question they actually ask while looking at a single workout: *what did
 * this one do to me, and when will it be gone.*
 *
 * [added] is the undecayed cost at the moment the session ended — the same vector
 * [StrainAnalytics] feeds into the timeline, so the figures here and the ones behind the bars
 * cannot disagree.
 */
data class SessionStrain(
    val date: LocalDate,
    val added: StrainVector,
    /**
     * True when the numbers came from LiftPath's set-level detail rather than from the session's
     * TSS. Worth saying out loud in the UI: a lifting session scored from sets knows which muscles
     * did the work, and one scored from a Health Connect duration does not.
     */
    val fromLiftDetail: Boolean = false
) {

    val isEmpty: Boolean get() = added.isEmpty

    /** Channels this session actually loaded, heaviest first. */
    val loadedChannels: List<StrainChannel>
        get() = StrainChannel.entries
            .filter { added[it] > 0.0 }
            .sortedByDescending { added[it] }

    /** How much of the session is still on the tissue [daysLater] days after it. */
    fun residualAfter(daysLater: Double): StrainVector =
        added.scaledPerChannel { channel -> exp(-(daysLater * HOURS_PER_DAY) / channel.tauHours) }

    /**
     * The decay curve, sampled from the session itself out to [days] days later.
     *
     * Sub-day steps because the fastest channel ([StrainChannel.SYSTEMIC], 36 hours) loses a third
     * of itself inside the first day, and a once-per-day sample draws that as a straight line.
     */
    fun decayCurve(days: Int = DEFAULT_CURVE_DAYS, stepDays: Double = 0.25): List<Pair<Double, StrainVector>> {
        val steps = (days / stepDays).toInt()
        return (0..steps).map { step ->
            val offset = step * stepDays
            offset to residualAfter(offset)
        }
    }

    /**
     * Days until [channel]'s share of *this* session has decayed to [fraction] of what it started
     * at, or null when the session never loaded that channel.
     *
     * Solved rather than simulated: `e^(-t/tau) = f  ⇒  t = -tau · ln(f)`. Note this is about the
     * session in isolation — it is not [ChannelState.hoursToFresh], which is about everything the
     * athlete is carrying measured against their own baseline.
     */
    fun daysUntilSpent(channel: StrainChannel, fraction: Double = SPENT_FRACTION): Double? {
        if (added[channel] <= 0.0) return null
        return channel.tauHours * -kotlin.math.ln(fraction) / HOURS_PER_DAY
    }

    /** How much of this session is still on the athlete as of [today], as a 0–1 fraction. */
    fun remainingFraction(channel: StrainChannel, today: LocalDate): Double {
        if (added[channel] <= 0.0) return 0.0
        val daysAgo = ChronoUnit.DAYS.between(date, today).coerceAtLeast(0L).toDouble()
        return exp(-(daysAgo * HOURS_PER_DAY) / channel.tauHours)
    }

    companion object {
        private const val HOURS_PER_DAY = 24.0

        /** Below a tenth of its original cost, a session is background noise rather than fatigue. */
        const val SPENT_FRACTION = 0.10

        /**
         * Ten days covers the slowest channel to well under a tenth of its cost
         * (`e^(-10·24/96)` ≈ 0.08), so the curve always shows the whole story.
         */
        const val DEFAULT_CURVE_DAYS = 10
    }
}
