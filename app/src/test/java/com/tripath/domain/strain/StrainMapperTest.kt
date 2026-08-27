package com.tripath.domain.strain

import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSetLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StrainMapperTest {

    private val date = LocalDate.of(2026, 8, 20)

    private fun workout(
        type: WorkoutType,
        tss: Int = 80,
        minutes: Int = 60,
        distanceM: Double? = null,
        avgHr: Int? = null,
        avgPower: Int? = null,
        zones: Map<String, Int>? = null,
        ignored: Boolean = false
    ) = WorkoutLog(
        connectId = "$type-$tss",
        date = date,
        type = type,
        durationMinutes = minutes,
        avgHeartRate = avgHr,
        computedTSS = tss,
        distanceMeters = distanceM,
        avgPowerWatts = avgPower,
        hrZoneDistribution = zones,
        isIgnored = ignored
    )

    private fun exercise(
        id: Int,
        tier: String? = "TIER_2",
        pattern: String? = null,
        region: String? = null,
        primary: String = "",
        secondary: String = ""
    ) = LiftExerciseCatalogEntry(
        id = id,
        name = "ex$id",
        region = region,
        tier = tier,
        pattern = pattern,
        mechanics = "COMPOUND",
        primaryTargets = primary,
        secondaryTargets = secondary
    )

    private fun set(
        exerciseId: Int,
        rpe: Float? = 8f,
        reps: Int = 8,
        warmup: Boolean = false,
        kg: Float = 100f
    ) = LiftSetLog(
        sessionId = "s1",
        exerciseId = exerciseId,
        setNumber = 1,
        kg = kg,
        reps = reps,
        rpe = rpe,
        isWarmup = warmup
    )

    // ---- Discipline shape ----------------------------------------------------------------------

    /** The headline claim of the whole model: a swim must not tire the legs. */
    @Test
    fun `swimming loads the upper body and leaves the legs alone`() {
        val strain = StrainMapper.forWorkout(workout(WorkoutType.SWIM, tss = 80))
        assertTrue(strain.upperMuscular > strain.lowerMuscular * 3)
        assertTrue("lower impact was ${strain.lowerImpact}", strain.lowerImpact < 10.0)
    }

    @Test
    fun `cycling loads the legs muscularly but barely at all structurally`() {
        val strain = StrainMapper.forWorkout(workout(WorkoutType.BIKE, tss = 90, avgPower = 220))
        assertTrue(strain.lowerMuscular > 50.0)
        assertTrue("lower impact was ${strain.lowerImpact}", strain.lowerImpact < 10.0)
    }

    /** The distinction the two lower channels exist for. */
    @Test
    fun `running and cycling of equal load differ enormously in impact`() {
        val run = StrainMapper.forWorkout(
            workout(WorkoutType.RUN, tss = 90, distanceM = 12_000.0, avgHr = 150)
        )
        val ride = StrainMapper.forWorkout(
            workout(WorkoutType.BIKE, tss = 90, distanceM = 45_000.0, avgPower = 200)
        )
        assertTrue("run ${run.lowerImpact} vs ride ${ride.lowerImpact}", run.lowerImpact > ride.lowerImpact * 5)
        // ...while their muscular cost is far closer together.
        assertTrue(ride.lowerMuscular > run.lowerMuscular * 0.8)
    }

    @Test
    fun `an ignored workout contributes nothing`() {
        assertEquals(StrainVector.ZERO, StrainMapper.forWorkout(workout(WorkoutType.RUN, ignored = true)))
    }

    // ---- Intensity is counted once -------------------------------------------------------------

    /**
     * Heart-rate TSS is literally `hours x (avgHr/maxHr)^2 x 100`, so it already contains
     * intensity. Multiplying by a zone kicker on top would price the same hard hour twice.
     */
    @Test
    fun `an HR-derived TSS is not multiplied by the intensity kicker`() {
        val hardZones = mapOf("Z4" to 1800, "Z5" to 1800)
        val withHr = workout(WorkoutType.RUN, tss = 90, avgHr = 165, zones = hardZones)
        assertEquals(1.0, StrainMapper.intensityKicker(withHr), 0.0001)
        assertTrue(StrainMapper.carriesIntensity(withHr))
    }

    /** A swim's TSS is a flat per-hour figure, so the zone data is genuinely new information. */
    @Test
    fun `a flat per-hour TSS does get the intensity kicker`() {
        val hardSwim = workout(WorkoutType.SWIM, tss = 60, zones = mapOf("Z4" to 1800, "Z5" to 1800))
        val easySwim = workout(WorkoutType.SWIM, tss = 60, zones = mapOf("Z1" to 1800, "Z2" to 1800))
        assertTrue(StrainMapper.intensityKicker(hardSwim) > 1.3)
        assertEquals(1.0, StrainMapper.intensityKicker(easySwim), 0.0001)
        assertTrue(
            StrainMapper.forWorkout(hardSwim).upperMuscular >
                StrainMapper.forWorkout(easySwim).upperMuscular
        )
    }

    @Test
    fun `power-based cycling TSS already carries intensity`() {
        assertTrue(StrainMapper.carriesIntensity(workout(WorkoutType.BIKE, avgPower = 240)))
        assertTrue(!StrainMapper.carriesIntensity(workout(WorkoutType.BIKE)))
    }

    // ---- Impact is additive --------------------------------------------------------------------

    @Test
    fun `impact scales with distance covered, not with metabolic cost`() {
        val short = StrainMapper.impactLoad(workout(WorkoutType.RUN, tss = 90, distanceM = 5_000.0))
        val long = StrainMapper.impactLoad(workout(WorkoutType.RUN, tss = 90, distanceM = 20_000.0))
        assertTrue(long > short * 3)
    }

    @Test
    fun `a run with no distance recorded still scores its metabolic load`() {
        val strain = StrainMapper.forWorkout(workout(WorkoutType.RUN, tss = 90, avgHr = 150))
        assertTrue(strain.systemic > 0.0)
        assertEquals(0.0, StrainMapper.impactLoad(workout(WorkoutType.RUN, tss = 90)), 0.0001)
    }

    @Test
    fun `cycling distance produces no impact however far it goes`() {
        assertEquals(
            0.0,
            StrainMapper.impactLoad(workout(WorkoutType.BIKE, distanceM = 200_000.0)),
            0.0001
        )
    }

    // ---- Lifting: hard sets, not tonnage --------------------------------------------------------

    /** The core claim: how close to failure a set went matters, how much iron moved does not. */
    @Test
    fun `two sets of identical tonnage but different effort cost differently`() {
        val catalog = mapOf(1 to exercise(1, primary = "QUADS"))
        val easy = StrainMapper.forLiftSession(listOf(set(1, rpe = 6f, reps = 10, kg = 100f)), catalog)
        val hard = StrainMapper.forLiftSession(listOf(set(1, rpe = 10f, reps = 10, kg = 100f)), catalog)
        assertTrue("easy ${easy.lowerMuscular} vs hard ${hard.lowerMuscular}",
            hard.lowerMuscular > easy.lowerMuscular * 2)
    }

    @Test
    fun `a leg press does not dwarf a lateral raise merely by moving more weight`() {
        val catalog = mapOf(
            1 to exercise(1, tier = "TIER_3", primary = "QUADS"),
            2 to exercise(2, tier = "TIER_3", primary = "DELT_SIDE")
        )
        val legPress = StrainMapper.forLiftSession(listOf(set(1, rpe = 8f, reps = 10, kg = 300f)), catalog)
        val lateralRaise = StrainMapper.forLiftSession(listOf(set(2, rpe = 8f, reps = 10, kg = 8f)), catalog)
        assertEquals(legPress.lowerMuscular, lateralRaise.upperMuscular, 0.001)
    }

    @Test
    fun `effort rises steeply as a set approaches failure`() {
        val rpe6 = StrainMapper.effortFromRpe(6f)
        val rpe8 = StrainMapper.effortFromRpe(8f)
        val rpe10 = StrainMapper.effortFromRpe(10f)
        assertEquals(1.0, rpe10, 0.001)
        assertEquals(0.59, rpe8, 0.02)
        assertEquals(0.35, rpe6, 0.02)
        // Convex: the step from 8 to 10 must exceed the step from 6 to 8.
        assertTrue((rpe10 - rpe8) > (rpe8 - rpe6))
    }

    @Test
    fun `warm-ups are excluded outright`() {
        val catalog = mapOf(1 to exercise(1, primary = "QUADS"))
        val strain = StrainMapper.forLiftSession(
            listOf(set(1, warmup = true), set(1, warmup = true)),
            catalog
        )
        assertEquals(StrainVector.ZERO, strain)
    }

    @Test
    fun `a set with no RPE falls back to a working-set assumption rather than zero`() {
        val catalog = mapOf(1 to exercise(1, primary = "QUADS"))
        val strain = StrainMapper.forLiftSession(listOf(set(1, rpe = null)), catalog)
        assertTrue(strain.lowerMuscular > 0.0)
    }

    // ---- Lifting: where the load lands -----------------------------------------------------------

    @Test
    fun `a squat session loads both lower channels`() {
        val catalog = mapOf(
            1 to exercise(1, tier = "TIER_1", pattern = "SQUAT", primary = "QUADS", secondary = "GLUTES")
        )
        val strain = StrainMapper.forLiftSession(List(5) { set(1, rpe = 8f, reps = 5) }, catalog)
        assertTrue("muscular ${strain.lowerMuscular}", strain.lowerMuscular > 0.0)
        assertTrue("impact ${strain.lowerImpact}", strain.lowerImpact > 0.0)
        assertTrue("upper ${strain.upperMuscular}", strain.upperMuscular < 1.0)
    }

    /** A leg extension loads the same muscle without compressing anything. */
    @Test
    fun `an isolation leg movement produces muscular load but no impact`() {
        val catalog = mapOf(1 to exercise(1, tier = "TIER_3", pattern = "ISOLATION_KNEE_EXTENSION", primary = "QUADS"))
        val strain = StrainMapper.forLiftSession(List(4) { set(1) }, catalog)
        assertTrue(strain.lowerMuscular > 0.0)
        assertEquals(0.0, strain.lowerImpact, 0.001)
    }

    @Test
    fun `a bench press loads chest most and triceps some`() {
        val catalog = mapOf(
            1 to exercise(1, primary = "CHEST_MIDDLE", secondary = "TRICEPS_LONG,DELT_FRONT")
        )
        val strain = StrainMapper.forLiftSession(List(4) { set(1) }, catalog)
        assertTrue(strain.upperMuscular > 0.0)
        assertEquals(0.0, strain.lowerMuscular, 0.001)
    }

    @Test
    fun `heavy low-rep work is more systemic than high-rep work of the same effort`() {
        val catalog = mapOf(1 to exercise(1, primary = "QUADS"))
        val heavy = StrainMapper.forLiftSession(listOf(set(1, rpe = 9f, reps = 3)), catalog)
        val light = StrainMapper.forLiftSession(listOf(set(1, rpe = 9f, reps = 15)), catalog)
        assertTrue(heavy.systemic > light.systemic)
        assertTrue(light.lowerMuscular > heavy.lowerMuscular)
    }

    @Test
    fun `an exercise missing from the catalog still counts, spread rather than dropped`() {
        val strain = StrainMapper.forLiftSession(listOf(set(99)), emptyMap())
        assertTrue(!strain.isEmpty)
    }

    @Test
    fun `an exercise with no targets falls back to its body region`() {
        val catalog = mapOf(1 to exercise(1, region = "UPPER"))
        val strain = StrainMapper.forLiftSession(listOf(set(1)), catalog)
        assertTrue(strain.upperMuscular > 0.0)
        assertEquals(0.0, strain.lowerMuscular, 0.001)
    }

    // ---- Cross-source calibration ---------------------------------------------------------------

    /**
     * The two sources share channels, so they have to be on one scale. A hard full-body session and
     * a firm endurance hour should be in the same ballpark — if lifting scored an order of
     * magnitude lower it would simply vanish underneath cycling.
     */
    @Test
    fun `a hard lifting session is comparable to a firm endurance hour`() {
        val catalog = mapOf(
            1 to exercise(1, tier = "TIER_1", pattern = "SQUAT", primary = "QUADS"),
            2 to exercise(2, tier = "TIER_2", primary = "CHEST_MIDDLE"),
            3 to exercise(3, tier = "TIER_2", primary = "LATS")
        )
        val sets = List(7) { set(1, rpe = 8f, reps = 5) } +
            List(7) { set(2, rpe = 8f, reps = 8) } +
            List(6) { set(3, rpe = 8f, reps = 8) }
        val lifting = StrainMapper.forLiftSession(sets, catalog)
        val total = lifting.lowerImpact + lifting.lowerMuscular + lifting.upperMuscular + lifting.systemic

        val ride = StrainMapper.forWorkout(workout(WorkoutType.BIKE, tss = 75, avgPower = 210))
        val rideTotal = ride.lowerImpact + ride.lowerMuscular + ride.upperMuscular + ride.systemic

        assertTrue("lifting $total vs ride $rideTotal", total > rideTotal * 0.5)
        assertTrue("lifting $total vs ride $rideTotal", total < rideTotal * 3.0)
    }

    // ---- Muscle groups ---------------------------------------------------------------------------

    @Test
    fun `muscle group load names the groups a session actually worked`() {
        val catalog = mapOf(
            1 to exercise(1, primary = "QUADS"),
            2 to exercise(2, primary = "LATS")
        )
        val load = StrainMapper.muscleGroupLoad(listOf(set(1), set(2)), catalog)
        assertTrue(load.containsKey(MuscleGroups.QUADS))
        assertTrue(load.containsKey(MuscleGroups.BACK))
        assertTrue(!load.containsKey(MuscleGroups.CALVES))
    }

    @Test
    fun `muscles map to the channel their limb belongs to`() {
        assertEquals(StrainChannel.LOWER_MUSCULAR, MuscleGroups.channelFor("QUADS"))
        assertEquals(StrainChannel.LOWER_MUSCULAR, MuscleGroups.channelFor("CALVES"))
        assertEquals(StrainChannel.UPPER_MUSCULAR, MuscleGroups.channelFor("LATS"))
        assertEquals(StrainChannel.UPPER_MUSCULAR, MuscleGroups.channelFor("BICEPS"))
        // Trunk work has no limb to attribute to.
        assertEquals(StrainChannel.SYSTEMIC, MuscleGroups.channelFor("ABS"))
    }

    // ---- Muscle groups from endurance sessions ---------------------------------------------------

    @Test
    fun `every discipline's muscle weights sum to one`() {
        WorkoutType.entries.forEach { type ->
            val sum = StrainMapper.disciplineMuscleWeights(type).values.sum()
            assertEquals("weights for $type", 1.0, sum, 0.0001)
        }
    }

    @Test
    fun `a ride lands mostly on the quads`() {
        val load = StrainMapper.muscleGroupLoad(workout(WorkoutType.BIKE, tss = 100))
        val heaviest = load.maxByOrNull { it.value }?.key
        assertEquals(MuscleGroups.QUADS, heaviest)
        assertTrue("no upper-body load from a ride", load[MuscleGroups.BACK] == null)
    }

    @Test
    fun `a swim lands on the back and shoulders`() {
        val load = StrainMapper.muscleGroupLoad(workout(WorkoutType.SWIM, tss = 100))
        val topTwo = load.entries.sortedByDescending { it.value }.take(2).map { it.key }.toSet()
        assertEquals(setOf(MuscleGroups.BACK, MuscleGroups.SHOULDERS), topTwo)
        assertTrue("no quad load from a swim", load[MuscleGroups.QUADS] == null)
    }

    @Test
    fun `a run spreads across the whole leg including the calves`() {
        val load = StrainMapper.muscleGroupLoad(
            workout(WorkoutType.RUN, tss = 90, distanceM = 12_000.0)
        )
        listOf(MuscleGroups.QUADS, MuscleGroups.HAMSTRINGS_GLUTES, MuscleGroups.CALVES)
            .forEach { assertTrue("$it missing", (load[it] ?: 0.0) > 0.0) }
    }

    /**
     * The pool is the muscular channels only. Impact is not a muscle group, and the group map is
     * decayed on the muscular clock, so folding impact in would clear it three times too fast.
     */
    @Test
    fun `group load totals the session's muscular channels and no more`() {
        val log = workout(WorkoutType.RUN, tss = 90, distanceM = 12_000.0)
        val strain = StrainMapper.forWorkout(log)
        val total = StrainMapper.muscleGroupLoad(log).values.sum()

        assertEquals(strain.lowerMuscular + strain.upperMuscular, total, 0.0001)
        // A 12 km run has real impact and real systemic cost; neither is in the group map.
        assertTrue("this run should carry impact", strain.lowerImpact > 0.0)
        assertTrue("this run should carry systemic cost", strain.systemic > 0.0)
    }

    @Test
    fun `an ignored session paints nothing`() {
        assertTrue(
            StrainMapper.muscleGroupLoad(workout(WorkoutType.BIKE, ignored = true)).isEmpty()
        )
    }

    // ---- Dedup -----------------------------------------------------------------------------------

    /** A Health Connect strength session plus LiftPath detail is one session, not two. */
    @Test
    fun `a strength workout backed by LiftPath detail is not double counted`() {
        val hcSession = workout(WorkoutType.STRENGTH, tss = 60)
        assertEquals(StrainVector.ZERO, StrainMapper.forWorkout(hcSession, hasLiftDetail = true))
        assertTrue(!StrainMapper.forWorkout(hcSession, hasLiftDetail = false).isEmpty)
    }

    /**
     * The diagram inherits the dedup rule for free by asking [StrainMapper.forWorkout] for the
     * session's cost rather than recomputing it — a superseded record has no cost, so it has no
     * muscles to paint either.
     */
    @Test
    fun `a superseded strength record paints no muscle groups`() {
        val hcSession = workout(WorkoutType.STRENGTH, tss = 60)
        assertTrue(StrainMapper.muscleGroupLoad(hcSession, hasLiftDetail = true).isEmpty())
        assertTrue(StrainMapper.muscleGroupLoad(hcSession, hasLiftDetail = false).isNotEmpty())
    }

    /** A record that says "weightlifting, 50 minutes" cannot name a region, so it names them all. */
    @Test
    fun `a bare strength record spreads evenly rather than guessing`() {
        val load = StrainMapper.muscleGroupLoad(workout(WorkoutType.STRENGTH, tss = 60))
        assertEquals(MuscleGroups.all.size, load.size)
        assertEquals(1, load.values.map { (it * 1000).toInt() }.distinct().size)
    }
}
