package com.tripath.domain.strain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.roundToInt

/** One day's raw strain, before any decay or normalisation. */
data class DailyStrain(val date: LocalDate, val strain: StrainVector)

/**
 * How one channel stands right now.
 *
 * [residual] is what is still sitting on the tissue after decay; [baseline] is what this athlete
 * habitually carries. [freshness] is the pair expressed as 0–100, and it is the only one of the
 * three worth showing a user — the raw numbers are in arbitrary units and mean nothing on their own.
 */
data class ChannelState(
    val channel: StrainChannel,
    val residual: Double,
    val baseline: Double,
    /** 0 = maximally loaded for this athlete, 100 = completely fresh. */
    val freshness: Int,
    /** Hours until this channel decays back to its baseline, or null if already there. */
    val hoursToFresh: Int?
) {
    val isFresh: Boolean get() = hoursToFresh == null
}

/** The whole picture across all four channels, plus the per-muscle detail behind the lifting part. */
data class StrainState(
    val channels: Map<StrainChannel, ChannelState> = emptyMap(),
    /** Freshness 0–100 per display muscle group, for the map handed back to LiftPath. */
    val muscleFreshness: Map<String, Int> = emptyMap(),
    val hasData: Boolean = false
) {
    operator fun get(channel: StrainChannel): ChannelState? = channels[channel]

    /** The channel in the deepest hole — what a single headline number would have to be about. */
    val mostLoaded: ChannelState?
        get() = channels.values.minByOrNull { it.freshness }
}

/**
 * Decays each channel on its own clock and reports where the athlete stands relative to their
 * own norm.
 *
 * ## Why normalise against a personal baseline
 * The strain units are arbitrary. 300 units on the legs is meaningless without knowing whether this
 * athlete habitually carries 100 or 500 — the first is a hole, the second is Tuesday. So every
 * channel is scored against its own chronic average, and "high" always means high *for you*. This
 * is the same reasoning behind chronic training load, applied per tissue.
 *
 * ## Why not one exponential for everything
 * Each channel decays with its own [StrainChannel.tauHours]. Bone and tendon hold loading for days
 * after the muscular soreness has gone, which is exactly the distinction that lets the app say
 * "your legs are still taking a pounding, but you can swim".
 *
 * Pure and derived at read time — nothing here is persisted, so the constants can be retuned
 * without a re-sync or a migration.
 */
object StrainTimeline {

    /** Days of history the chronic baseline averages over. Matches the fitness time constant. */
    const val BASELINE_DAYS = 42L

    /**
     * Extra history kept before the baseline window so day one of that window has a settled
     * residual. 30 days is well past the point where even the 96-hour channel still contributes —
     * `e^(-30·24/96)` is about 0.0006.
     */
    const val DECAY_HORIZON_DAYS = 30L

    /**
     * Residual at or below this fraction of baseline counts as fully fresh.
     *
     * Deliberately *below* 1.0. Set at the baseline itself, a consistently training athlete — whose
     * residual by definition hovers around their own average — reads 100% fresh essentially every
     * day, and the bars become decoration. Sitting the top of the scale at roughly a taper's worth
     * of load means "at your normal load" lands near 79, which leaves room to show the difference
     * between a normal Tuesday and the morning after a hard block.
     *
     * Waiting for a true zero would be worse still: an exponential only approaches it, so nobody
     * would ever be fresh.
     */
    const val FRESH_THRESHOLD = 0.6

    /** Residual at this multiple of baseline (or beyond) scores zero freshness. */
    const val FULLY_LOADED_MULTIPLE = 2.5

    /**
     * Baseline floor, in strain units. Without it, an athlete returning from a layoff has a
     * baseline near zero, every ratio explodes, and the app declares them wrecked after one jog.
     */
    const val MIN_BASELINE = 25.0

    /**
     * The same floor for a single muscle group, which works in much smaller numbers.
     *
     * A session's load lands on one or two channels but is split across half a dozen muscle groups,
     * so a group carries roughly a sixth of what a channel does. Reusing [MIN_BASELINE] here floors
     * every group so far above its real load that the ratio never leaves the fresh band — on real
     * data that pinned all ten groups at 100% the morning after a hard session, which is worse than
     * showing nothing.
     */
    const val MIN_GROUP_BASELINE = 4.0

    /**
     * Builds the current state from a day-by-day strain history.
     *
     * [asOf] is treated as the end of its day, which is the convention the rest of the app uses for
     * a session with no wall-clock time.
     */
    fun stateAt(
        history: List<DailyStrain>,
        asOf: LocalDate,
        muscleHistory: List<Pair<LocalDate, Map<String, Double>>> = emptyList()
    ): StrainState {
        // Bounded on purpose: the baseline pass is quadratic in history length, and after 30 days
        // even the slowest channel has decayed to under a thousandth of its original value, so
        // older sessions cannot move any figure here. Without this, a multi-year log would make
        // every screen build walk its whole history.
        val horizon = asOf.minusDays(BASELINE_DAYS + DECAY_HORIZON_DAYS)
        val relevant = history.filter { !it.date.isAfter(asOf) && !it.date.isBefore(horizon) }
        if (relevant.isEmpty()) return StrainState()

        val residual = residualAt(relevant, asOf)
        val baselines = baselines(relevant, asOf)

        val channels = StrainChannel.entries.associateWith { channel ->
            val r = residual[channel]
            val b = baselines[channel] ?: MIN_BASELINE
            ChannelState(
                channel = channel,
                residual = r,
                baseline = b,
                freshness = freshness(r, b),
                hoursToFresh = hoursToFresh(r, b, channel)
            )
        }

        return StrainState(
            channels = channels,
            muscleFreshness = muscleFreshness(muscleHistory, asOf),
            hasData = true
        )
    }

    /**
     * Summed residual strain, each day's contribution decayed by how long ago it was on that
     * channel's own clock.
     */
    internal fun residualAt(history: List<DailyStrain>, asOf: LocalDate): StrainVector {
        var total = StrainVector.ZERO
        history.forEach { day ->
            val daysAgo = ChronoUnit.DAYS.between(day.date, asOf)
            if (daysAgo < 0) return@forEach
            val hoursAgo = daysAgo * 24.0
            total += day.strain.scaledPerChannel { channel ->
                exp(-hoursAgo / channel.tauHours)
            }
        }
        return total
    }

    /**
     * What this athlete habitually carries on each channel: the mean *residual* over the baseline
     * window, not the mean daily input.
     *
     * Comparing today's residual against a mean of daily inputs would compare two different things
     * — a residual accumulates several days of decaying load, so it sits well above any single
     * day's figure and every channel would read as permanently overloaded.
     */
    internal fun baselines(history: List<DailyStrain>, asOf: LocalDate): Map<StrainChannel, Double> {
        val windowStart = asOf.minusDays(BASELINE_DAYS)
        val samples = mutableMapOf<StrainChannel, MutableList<Double>>()

        var cursor = windowStart
        while (!cursor.isAfter(asOf)) {
            val residual = residualAt(history.filter { !it.date.isAfter(cursor) }, cursor)
            StrainChannel.entries.forEach { channel ->
                samples.getOrPut(channel) { mutableListOf() }.add(residual[channel])
            }
            cursor = cursor.plusDays(1)
        }

        return StrainChannel.entries.associateWith { channel ->
            val values = samples[channel].orEmpty()
            val mean = if (values.isEmpty()) 0.0 else values.average()
            maxOf(mean, MIN_BASELINE)
        }
    }

    /**
     * 0–100, where 100 means at or below the athlete's habitual load and 0 means
     * [FULLY_LOADED_MULTIPLE] times it or worse.
     */
    internal fun freshness(residual: Double, baseline: Double): Int {
        if (baseline <= 0.0) return 100
        val ratio = residual / baseline
        if (ratio <= FRESH_THRESHOLD) return 100
        if (ratio >= FULLY_LOADED_MULTIPLE) return 0
        val span = FULLY_LOADED_MULTIPLE - FRESH_THRESHOLD
        return (100.0 * (1.0 - (ratio - FRESH_THRESHOLD) / span)).roundToInt().coerceIn(0, 100)
    }

    /**
     * Hours for [residual] to decay back to [baseline] on this channel's clock, or null when it is
     * already there.
     *
     * Solved directly rather than simulated: `r · e^(-t/tau) = b  ⇒  t = tau · ln(r/b)`.
     */
    internal fun hoursToFresh(residual: Double, baseline: Double, channel: StrainChannel): Int? {
        if (baseline <= 0.0 || residual <= baseline) return null
        val hours = channel.tauHours * kotlin.math.ln(residual / baseline)
        return hours.roundToInt().coerceAtLeast(1)
    }

    /**
     * Per-muscle-group freshness, decayed on the muscular clock.
     *
     * Muscle groups are all contractile tissue, so they share [StrainChannel.LOWER_MUSCULAR]'s
     * constant rather than each carrying their own — the distinction that earns a separate clock is
     * impact versus muscular, not quad versus lat.
     */
    internal fun muscleFreshness(
        history: List<Pair<LocalDate, Map<String, Double>>>,
        asOf: LocalDate
    ): Map<String, Int> {
        if (history.isEmpty()) return emptyMap()
        val horizon = asOf.minusDays(BASELINE_DAYS + DECAY_HORIZON_DAYS)
        val relevant = history.filter { !it.first.isAfter(asOf) && !it.first.isBefore(horizon) }
        if (relevant.isEmpty()) return emptyMap()

        val residual = groupResidualAt(relevant, asOf)
        if (residual.isEmpty()) return emptyMap()

        // Each group is scored against *its own* habitual load over the baseline window, the same
        // way channels are.
        //
        // An earlier version compared each group to the average of the others on the same day. That
        // is fine for spotting an imbalance but useless for recovery, which is what this is for: a
        // balanced full-body session leaves every group equally loaded, every group equals the
        // average, and the whole map reads 100% fresh the morning after being hammered.
        val baselines = groupBaselines(relevant, asOf)
        return residual.mapValues { (group, value) ->
            freshness(value, baselines[group] ?: MIN_GROUP_BASELINE)
        }
    }

    /** Decayed per-group load as of [asOf], on the muscular clock. */
    private fun groupResidualAt(
        history: List<Pair<LocalDate, Map<String, Double>>>,
        asOf: LocalDate
    ): Map<String, Double> {
        val tau = StrainChannel.LOWER_MUSCULAR.tauHours
        val residual = mutableMapOf<String, Double>()
        history.forEach { (date, byGroup) ->
            val daysAgo = ChronoUnit.DAYS.between(date, asOf)
            if (daysAgo < 0) return@forEach
            val decay = exp(-(daysAgo * 24.0) / tau)
            byGroup.forEach { (group, load) ->
                residual[group] = (residual[group] ?: 0.0) + load * decay
            }
        }
        return residual
    }

    /**
     * Mean residual per muscle group across the baseline window — the same construction
     * [baselines] uses for channels, and for the same reason: today's residual has to be compared
     * against something of the same kind.
     */
    private fun groupBaselines(
        history: List<Pair<LocalDate, Map<String, Double>>>,
        asOf: LocalDate
    ): Map<String, Double> {
        val windowStart = asOf.minusDays(BASELINE_DAYS)
        val samples = mutableMapOf<String, MutableList<Double>>()
        val groups = history.flatMap { it.second.keys }.toSet()

        var cursor = windowStart
        while (!cursor.isAfter(asOf)) {
            val residual = groupResidualAt(history.filter { !it.first.isAfter(cursor) }, cursor)
            groups.forEach { group ->
                samples.getOrPut(group) { mutableListOf() }.add(residual[group] ?: 0.0)
            }
            cursor = cursor.plusDays(1)
        }

        return groups.associateWith { group ->
            val values = samples[group].orEmpty()
            maxOf(if (values.isEmpty()) 0.0 else values.average(), MIN_GROUP_BASELINE)
        }
    }
}
