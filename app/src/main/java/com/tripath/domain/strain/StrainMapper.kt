package com.tripath.domain.strain

import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSetLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Turns sessions into per-channel strain.
 *
 * Two very different inputs have to end up on one scale: endurance sessions, which arrive as a
 * Training Stress Score, and lifting, which arrives as individual sets. [STRESS_UNIT_TO_TSS] is
 * the bridge, and the calibration behind it is written down there rather than left implicit.
 *
 * ## Intensity is counted exactly once
 * `computedTSS` already contains intensity whenever it was derived from power or heart rate —
 * heart-rate TSS is literally `hours × (avgHr/maxHr)² × 100`. Multiplying that by a second
 * zone-based "intensity kicker" would price the same hard session twice, so the kicker only applies
 * to sessions whose TSS is a flat per-hour fallback (swims, strength, and anything logged without a
 * heart-rate trace). See [carriesIntensity].
 *
 * ## Impact is added, not multiplied
 * Mechanical loading does not scale with metabolic cost — a five-hour easy ride is enormous
 * metabolically and nearly free for the tendons, while a fast 10 km is the reverse. So distance-
 * driven impact is a separate additive term on [StrainChannel.LOWER_IMPACT] rather than a
 * multiplier on TSS.
 *
 * ## Lifting is scored by hard sets, not tonnage
 * `kg × reps` makes a leg press dwarf a set of lateral raises while being proportional to neither
 * local muscular fatigue nor systemic cost. The stimulus that matters is how close to failure each
 * set went, on what kind of movement — see [effortFromRpe].
 */
object StrainMapper {

    // ---- Cardio ------------------------------------------------------------------------------

    /**
     * How each discipline's metabolic load lands on the body, as fractions of the session's TSS.
     *
     * The numbers encode the obvious physical facts: swimming is nearly all arms, cycling is legs
     * with almost no impact, running is legs *and* pounding.
     */
    internal fun disciplineVector(type: WorkoutType): StrainVector = when (type) {
        WorkoutType.RUN -> StrainVector(0.90, 0.60, 0.05, 1.00)
        WorkoutType.HIKE -> StrainVector(0.60, 0.50, 0.10, 0.80)
        WorkoutType.WALK -> StrainVector(0.25, 0.20, 0.00, 0.30)
        WorkoutType.BIKE -> StrainVector(0.05, 0.85, 0.05, 0.90)
        WorkoutType.SWIM -> StrainVector(0.05, 0.15, 0.90, 0.85)
        // Only reached for a strength session with no LiftPath detail behind it: a Health Connect
        // record that says "weightlifting, 50 minutes" and nothing more. Spread evenly, because
        // guessing a region from nothing would be worse than admitting we do not know.
        WorkoutType.STRENGTH -> StrainVector(0.40, 0.40, 0.40, 0.80)
        WorkoutType.OTHER -> StrainVector(0.30, 0.35, 0.35, 0.70)
    }

    /**
     * Whether this session's TSS already reflects how hard it was.
     *
     * True when TriPath computed it from power or heart rate; false for the flat per-hour fallbacks
     * (see `TrainingMetricsCalculator.calculateTSS`), where an hour is an hour however it felt.
     */
    internal fun carriesIntensity(log: WorkoutLog): Boolean = when (log.type) {
        WorkoutType.BIKE -> log.avgPowerWatts != null || log.avgHeartRate != null
        WorkoutType.RUN, WorkoutType.WALK, WorkoutType.HIKE, WorkoutType.OTHER ->
            log.avgHeartRate != null
        // Swim TSS is always the profile's flat per-hour figure; strength TSS likewise.
        WorkoutType.SWIM, WorkoutType.STRENGTH -> false
    }

    /**
     * Extra systemic cost for time spent near threshold, for sessions whose TSS cannot see it.
     * Ranges 1.0 (all easy) to 1.4 (entirely Z4/Z5).
     */
    internal fun intensityKicker(log: WorkoutLog): Double {
        if (carriesIntensity(log)) return 1.0
        val zones = log.hrZoneDistribution ?: return 1.0
        val total = zones.values.sum()
        if (total <= 0) return 1.0
        val hard = zones.filterKeys { it.contains("4") || it.contains("5") }.values.sum()
        return 1.0 + MAX_INTENSITY_BONUS * (hard.toDouble() / total)
    }

    /**
     * Mechanical loading from covering ground, in TSS-equivalent units.
     *
     * Built on distance × pace band — the structural analogue of the Structural Stress Score idea:
     * ground contacts are what load bone and tendon, and they scale with distance, with faster
     * running loading each contact harder.
     *
     * Elevation would sharpen this considerably (descending is the most damaging thing a runner
     * does), but `WorkoutLog` carries no elevation, so distance is what there is.
     */
    internal fun impactLoad(log: WorkoutLog): Double {
        val impactFactor = when (log.type) {
            WorkoutType.RUN -> 1.0
            WorkoutType.HIKE -> 0.6
            WorkoutType.WALK -> 0.2
            else -> 0.0
        }
        if (impactFactor == 0.0) return 0.0
        val km = (log.distanceMeters ?: 0.0) / 1000.0
        if (km <= 0.0) return 0.0
        return km * (1.0 + averageZone(log) * 0.2) * impactFactor * IMPACT_PER_KM_UNIT
    }

    /** Time-weighted mean heart-rate zone, falling back to a TSS-per-hour estimate. */
    internal fun averageZone(log: WorkoutLog): Double {
        val zones = log.hrZoneDistribution
        if (zones != null && zones.isNotEmpty()) {
            var totalTime = 0
            var weighted = 0.0
            zones.forEach { (name, seconds) ->
                val zone = name.uppercase().removePrefix("Z").toIntOrNull()
                if (zone != null && zone in 1..5) {
                    totalTime += seconds
                    weighted += zone * seconds
                }
            }
            if (totalTime > 0) return (weighted / totalTime).coerceIn(1.0, 5.0)
        }
        val hours = log.durationMinutes / 60.0
        if (hours <= 0.0) return 2.0
        return when ((log.computedTSS ?: 0) / hours) {
            in 0.0..35.0 -> 1.0
            in 35.0..60.0 -> 2.0
            in 60.0..85.0 -> 3.0
            in 85.0..110.0 -> 4.0
            else -> 5.0
        }
    }

    /**
     * Strain from one endurance session. Returns [StrainVector.ZERO] for a strength session that
     * has LiftPath detail behind it — [forLiftSession] scores those, and counting both would double
     * every lifting day.
     */
    fun forWorkout(log: WorkoutLog, hasLiftDetail: Boolean = false): StrainVector {
        if (log.isIgnored) return StrainVector.ZERO
        if (log.type == WorkoutType.STRENGTH && hasLiftDetail) return StrainVector.ZERO

        val tss = (log.computedTSS ?: 0).toDouble()
        val metabolic = disciplineVector(log.type) * (tss * intensityKicker(log))
        return metabolic + StrainVector(lowerImpact = impactLoad(log))
    }

    // ---- Lifting -----------------------------------------------------------------------------

    /**
     * How much a set costs, from how close to failure it went.
     *
     * Deliberately convex in reps-in-reserve: the last couple of reps before failure carry far more
     * stimulus — and far more fatigue — than the ones before them, which is why a set taken to RPE
     * 10 is not merely 25% harder than one stopped at RPE 8. Anchored so RPE 10 is 1.0 and RPE 6 is
     * roughly a third of that.
     *
     * Anything under RPE 4 is treated as trivial: at that point the set is a warm-up in all but
     * name, whatever it was labelled.
     */
    internal fun effortFromRpe(rpe: Float?): Double {
        val value = rpe?.toDouble() ?: DEFAULT_RPE
        if (value < TRIVIAL_RPE) return TRIVIAL_EFFORT
        val repsInReserve = (10.0 - value).coerceAtLeast(0.0)
        return exp(-EFFORT_DECAY_PER_RIR * repsInReserve)
    }

    /** T1 main lifts cost most, accessories least. The same weights LiftPath's own model uses. */
    internal fun tierWeight(tier: String?): Double = when (tier?.uppercase()) {
        "TIER_1" -> 1.5
        "TIER_2" -> 1.2
        "TIER_3" -> 0.8
        else -> 1.0
    }

    /**
     * Share of a set's cost that lands systemically rather than locally.
     *
     * Heavy, low-rep work is expensive centrally and comparatively cheap locally; long high-rep
     * sets are the reverse — most of the damage is in the muscle that did the work.
     */
    internal fun systemicShare(reps: Int): Double = when {
        reps <= 0 -> 0.35        // an isometric hold; treat as mid-range
        reps <= 5 -> 0.50
        reps <= 12 -> 0.35
        else -> 0.25
    }

    /**
     * Whether a movement loads the skeleton axially enough to count as impact.
     *
     * A heavy squat or deadlift compresses the spine and loads tendon through a long eccentric;
     * a leg extension does neither. Only the main-lift tiers qualify — the same pattern done for
     * high-rep accessory work is a muscular cost, not a structural one.
     */
    internal fun isAxiallyLoaded(pattern: String?, tier: String?): Boolean {
        val axial = pattern?.uppercase() in setOf("SQUAT", "HINGE", "LUNGE", "CARRY")
        val heavyTier = tier?.uppercase() in setOf("TIER_1", "TIER_2")
        return axial && heavyTier
    }

    /**
     * Strain from one lifting session's sets, using the exercise catalog to decide which muscles —
     * and therefore which channels — each set loaded.
     *
     * Warm-ups are excluded outright. LiftPath resolves the `isWarmup` flag before sending it,
     * including its legacy "RPE 6 used to mean warm-up" rule, so that history is not re-derived here.
     */
    fun forLiftSession(
        sets: List<LiftSetLog>,
        catalog: Map<Int, LiftExerciseCatalogEntry>
    ): StrainVector {
        var total = StrainVector.ZERO
        sets.asSequence()
            .filterNot { it.isWarmup }
            .forEach { set ->
                val exercise = catalog[set.exerciseId]
                val stress = effortFromRpe(set.rpe) *
                    tierWeight(exercise?.tier) *
                    STRESS_UNIT_TO_TSS

                val systemicPart = stress * systemicShare(set.reps)
                val localPart = stress - systemicPart

                total += StrainVector(systemic = systemicPart)
                total += allocateLocally(localPart, exercise)

                if (isAxiallyLoaded(exercise?.pattern, exercise?.tier)) {
                    total += StrainVector(lowerImpact = stress * AXIAL_IMPACT_SHARE)
                }
            }
        return total
    }

    /**
     * Splits a set's local cost across the muscles it worked, then onto their channels.
     *
     * Secondary targets take a fraction of a primary's share: a bench press does load the triceps,
     * just not the way it loads the chest. When the catalog has no target information at all the
     * fallback is the exercise's body region, and failing that the cost is split evenly rather than
     * silently dropped.
     */
    private fun allocateLocally(
        amount: Double,
        exercise: LiftExerciseCatalogEntry?
    ): StrainVector {
        if (amount <= 0.0) return StrainVector.ZERO

        val primaries = exercise?.primaryTargets.toMuscleList()
        val secondaries = exercise?.secondaryTargets.toMuscleList()

        val weights = mutableMapOf<String, Double>()
        primaries.forEach { weights[it] = (weights[it] ?: 0.0) + 1.0 }
        secondaries.forEach { weights[it] = (weights[it] ?: 0.0) + SECONDARY_TARGET_WEIGHT }

        if (weights.isEmpty()) return allocateByRegion(amount, exercise?.region)

        val totalWeight = weights.values.sum()
        var result = StrainVector.ZERO
        weights.forEach { (muscle, weight) ->
            val share = amount * (weight / totalWeight)
            result += when (MuscleGroups.channelFor(muscle)) {
                StrainChannel.LOWER_IMPACT -> StrainVector(lowerImpact = share)
                StrainChannel.LOWER_MUSCULAR -> StrainVector(lowerMuscular = share)
                StrainChannel.UPPER_MUSCULAR -> StrainVector(upperMuscular = share)
                // Core work has no limb to attribute to; it reads as trunk/systemic cost.
                StrainChannel.SYSTEMIC -> StrainVector(systemic = share)
            }
        }
        return result
    }

    private fun allocateByRegion(amount: Double, region: String?): StrainVector =
        when (region?.uppercase()) {
            "LOWER" -> StrainVector(lowerMuscular = amount)
            "UPPER" -> StrainVector(upperMuscular = amount)
            "CORE" -> StrainVector(systemic = amount)
            "FULL" -> StrainVector(lowerMuscular = amount * 0.5, upperMuscular = amount * 0.5)
            else -> StrainVector(lowerMuscular = amount * 0.4, upperMuscular = amount * 0.4, systemic = amount * 0.2)
        }

    /**
     * Per-muscle-group cost for a session, for the freshness map handed back to LiftPath. Uses the
     * same stress units as [forLiftSession] so the two views of a session cannot disagree.
     */
    fun muscleGroupLoad(
        sets: List<LiftSetLog>,
        catalog: Map<Int, LiftExerciseCatalogEntry>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        sets.asSequence()
            .filterNot { it.isWarmup }
            .forEach { set ->
                val exercise = catalog[set.exerciseId]
                val stress = effortFromRpe(set.rpe) * tierWeight(exercise?.tier) * STRESS_UNIT_TO_TSS
                val local = stress * (1.0 - systemicShare(set.reps))

                val weights = mutableMapOf<String, Double>()
                exercise?.primaryTargets.toMuscleList().forEach { weights[it] = (weights[it] ?: 0.0) + 1.0 }
                exercise?.secondaryTargets.toMuscleList()
                    .forEach { weights[it] = (weights[it] ?: 0.0) + SECONDARY_TARGET_WEIGHT }
                if (weights.isEmpty()) return@forEach

                val totalWeight = weights.values.sum()
                weights.forEach { (muscle, weight) ->
                    val group = MuscleGroups.groupFor(muscle)
                    result[group] = (result[group] ?: 0.0) + local * (weight / totalWeight)
                }
            }
        return result
    }

    /**
     * Per-muscle-group cost for an endurance session, so the body diagram is not blind to the three
     * disciplines the athlete spends most of their week on.
     *
     * ## What gets distributed
     * The pool is the session's **muscular** channels only — `lowerMuscular + upperMuscular`, read
     * off [forWorkout] rather than recomputed. Reusing [forWorkout] is what stops the diagram and the
     * channel bars disagreeing about a session, and it is also what makes the superseded-strength
     * case free: a lifting day LiftPath has detailed returns [StrainVector.ZERO], and therefore an
     * empty map, without this function needing to know the dedup rule.
     *
     * Impact is deliberately left out: bone and tendon are not a muscle group, and
     * `StrainTimeline.groupResidualAt` decays every group on the muscular clock, so folding impact in
     * would have it clearing three times faster here than in its own channel. Systemic is left out
     * for the reason it exists — there is no limb to attribute it to.
     *
     * ## The one imprecision, accepted on purpose
     * A single pool means the sum of a discipline's upper-body groups is not exactly its
     * `upperMuscular` channel value — the weights below are their own approximation of the same
     * physiology, one level finer. The map and the bars are different views at different
     * granularities, and reconciling them to the decimal would buy nothing an athlete can see.
     */
    fun muscleGroupLoad(log: WorkoutLog, hasLiftDetail: Boolean = false): Map<String, Double> {
        val strain = forWorkout(log, hasLiftDetail)
        val pool = strain.lowerMuscular + strain.upperMuscular
        if (pool <= 0.0) return emptyMap()

        val weights = disciplineMuscleWeights(log.type)
        val totalWeight = weights.values.sum()
        if (totalWeight <= 0.0) return emptyMap()

        return weights.mapValues { (_, weight) -> pool * (weight / totalWeight) }
    }

    /**
     * How each discipline's muscular cost lands on the display groups.
     *
     * Sums to 1.0 per discipline, and encodes only what is uncontroversial about each: cycling is a
     * quad-dominant push with the posterior chain assisting, running spreads across the whole leg
     * and beats up the calves, swimming is lats and shoulders with the trunk holding position.
     *
     * Display-only, so these can be retuned freely — nothing downstream is calibrated against them,
     * and per-group freshness is scored against each group's own baseline in any case.
     */
    internal fun disciplineMuscleWeights(type: WorkoutType): Map<String, Double> = when (type) {
        WorkoutType.RUN -> mapOf(
            MuscleGroups.QUADS to 0.30,
            MuscleGroups.HAMSTRINGS_GLUTES to 0.30,
            MuscleGroups.CALVES to 0.25,
            MuscleGroups.HIPS to 0.10,
            MuscleGroups.CORE to 0.05
        )
        // Hiking and walking are running's shape with more hip work carrying the stride and less
        // quad: the pace never asks the knee extensors for much.
        WorkoutType.HIKE -> mapOf(
            MuscleGroups.QUADS to 0.28,
            MuscleGroups.HAMSTRINGS_GLUTES to 0.32,
            MuscleGroups.CALVES to 0.20,
            MuscleGroups.HIPS to 0.15,
            MuscleGroups.CORE to 0.05
        )
        WorkoutType.WALK -> mapOf(
            MuscleGroups.QUADS to 0.25,
            MuscleGroups.HAMSTRINGS_GLUTES to 0.30,
            MuscleGroups.CALVES to 0.25,
            MuscleGroups.HIPS to 0.15,
            MuscleGroups.CORE to 0.05
        )
        WorkoutType.BIKE -> mapOf(
            MuscleGroups.QUADS to 0.50,
            MuscleGroups.HAMSTRINGS_GLUTES to 0.30,
            MuscleGroups.CALVES to 0.12,
            MuscleGroups.HIPS to 0.05,
            MuscleGroups.CORE to 0.03
        )
        WorkoutType.SWIM -> mapOf(
            MuscleGroups.BACK to 0.35,
            MuscleGroups.SHOULDERS to 0.30,
            MuscleGroups.CHEST to 0.12,
            MuscleGroups.ARMS to 0.10,
            MuscleGroups.CORE to 0.10,
            MuscleGroups.FOREARMS to 0.03
        )
        // A Health Connect record that says "weightlifting, 50 minutes" and nothing more, or an
        // activity the app has no vector for. Spread evenly, for the same reason
        // [disciplineVector] spreads these two across channels: guessing a region from nothing
        // would be worse than admitting we do not know.
        WorkoutType.STRENGTH, WorkoutType.OTHER -> evenlySpread
    }

    /**
     * One share each across every group the diagram can paint.
     *
     * [MuscleGroups.OTHER] is excluded: it is the bucket for muscles a catalog entry failed to name,
     * and there is no region of a body that means "we do not know where this was".
     */
    private val evenlySpread: Map<String, Double> =
        MuscleGroups.all.associateWith { 1.0 / MuscleGroups.all.size }

    private fun String?.toMuscleList(): List<String> =
        this?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() } ?: emptyList()

    // ---- Calibration ---------------------------------------------------------------------------

    /**
     * Converts one hard-set stress unit into TSS-equivalent strain.
     *
     * Calibrated so a solid full-body session — roughly 20 working sets around RPE 8 on a mix of
     * tiers — lands near 75, which is what a firm hour of endurance work scores. Without a bridge
     * like this the two sources share a channel on wildly different scales and lifting simply
     * vanishes underneath cycling.
     *
     * `20 sets x effort(RPE 8) 0.59 x tier ~1.2 x 5.0 ≈ 71`.
     */
    internal const val STRESS_UNIT_TO_TSS = 5.0

    /** Assumed effort when a set carries no RPE. Matches LiftPath's own default. */
    private const val DEFAULT_RPE = 7.0

    /** Below this the set is a warm-up in all but name. */
    private const val TRIVIAL_RPE = 4.0
    private const val TRIVIAL_EFFORT = 0.10

    /** Chosen so RPE 10 → 1.00, RPE 8 → 0.59, RPE 6 → 0.35. */
    private const val EFFORT_DECAY_PER_RIR = 0.26

    /** A secondary target takes roughly a third of a primary's share of a set. */
    private const val SECONDARY_TARGET_WEIGHT = 0.35

    /** Extra structural cost of a heavy axially-loaded lift, on top of its muscular cost. */
    private const val AXIAL_IMPACT_SHARE = 0.40

    /** Ceiling on the zone-based systemic bonus for sessions whose TSS misses intensity. */
    private const val MAX_INTENSITY_BONUS = 0.40

    /**
     * TSS-equivalent impact per kilometre-zone unit. Set so an easy 10 km run contributes about 40
     * — comparable to the metabolic strain of the same run, which is the right balance for a
     * discipline whose defining cost is the pounding rather than the effort.
     */
    private const val IMPACT_PER_KM_UNIT = 3.0
}

/**
 * Maps LiftPath's 24 individual target muscles onto display groups and strain channels.
 *
 * Kept separate from [StrainMapper] because it is a lookup table with no physiology in it beyond
 * "where is this muscle" — and because the group names are user-facing, while the channels are not.
 */
object MuscleGroups {

    const val CHEST = "Chest"
    const val BACK = "Back"
    const val SHOULDERS = "Shoulders"
    const val ARMS = "Arms"
    const val FOREARMS = "Forearms"
    const val QUADS = "Quads"
    const val HAMSTRINGS_GLUTES = "Hamstrings & glutes"
    const val CALVES = "Calves"
    const val HIPS = "Hips"
    const val CORE = "Core"

    private val groupByMuscle: Map<String, String> = mapOf(
        "CHEST_UPPER" to CHEST, "CHEST_MIDDLE" to CHEST, "CHEST_LOWER" to CHEST,
        "LATS" to BACK, "TRAPS_MID" to BACK, "TRAPS_UPPER" to BACK, "LOWER_BACK" to BACK,
        "DELT_FRONT" to SHOULDERS, "DELT_SIDE" to SHOULDERS, "DELT_REAR" to SHOULDERS,
        "BICEPS" to ARMS, "TRICEPS_LONG" to ARMS, "TRICEPS_LATERAL" to ARMS,
        "FOREARMS" to FOREARMS,
        "QUADS" to QUADS,
        "HAMSTRINGS" to HAMSTRINGS_GLUTES, "GLUTES" to HAMSTRINGS_GLUTES,
        "CALVES" to CALVES, "TIBIALIS" to CALVES,
        "ADDUCTORS" to HIPS, "ABDUCTORS" to HIPS, "HIPFLEXORS" to HIPS,
        "ABS" to CORE, "OBLIQUES" to CORE
    )

    private val lowerGroups = setOf(QUADS, HAMSTRINGS_GLUTES, CALVES, HIPS)
    private val upperGroups = setOf(CHEST, BACK, SHOULDERS, ARMS, FOREARMS)

    /** The display group a muscle belongs to; unknown names group under [CORE]'s neighbour "Other". */
    fun groupFor(muscle: String): String = groupByMuscle[muscle.uppercase()] ?: OTHER

    /**
     * Which strain channel a muscle's load belongs in. Core maps to
     * [StrainChannel.SYSTEMIC] rather than to a limb, because trunk work is a whole-body cost with
     * no arm or leg to attribute it to.
     */
    fun channelFor(muscle: String): StrainChannel = when (groupFor(muscle)) {
        in lowerGroups -> StrainChannel.LOWER_MUSCULAR
        in upperGroups -> StrainChannel.UPPER_MUSCULAR
        else -> StrainChannel.SYSTEMIC
    }

    const val OTHER = "Other"

    /** Every display group, in head-to-toe order. */
    val all: List<String> = listOf(
        CHEST, BACK, SHOULDERS, ARMS, FOREARMS, CORE, QUADS, HAMSTRINGS_GLUTES, HIPS, CALVES
    )
}

/** Rounds a strain figure for display without pretending to a precision it does not have. */
internal fun Double.asStrainDisplay(): Int = roundToInt()
