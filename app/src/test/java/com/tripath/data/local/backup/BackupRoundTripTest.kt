package com.tripath.data.local.backup

import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionEntryKind
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.NutritionPreset
import com.tripath.data.local.database.entities.RawWorkoutData
import com.tripath.data.local.database.entities.ScannedFoodCache
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.SpecialPeriod
import com.tripath.data.local.database.entities.SpecialPeriodType
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WellnessTaskDefinition
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.local.preferences.PreferenceEntry
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.Intensity
import com.tripath.data.model.StrengthFocus
import com.tripath.data.model.TaskTriggerType
import com.tripath.data.model.WorkoutType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Guards the backup format: whatever goes into a backup must come back out unchanged.
 *
 * A silent field drop here is invisible in normal use — the app keeps working, and the loss only
 * surfaces on a new phone, when the original data is already gone.
 */
class BackupRoundTripTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun roundTrip(data: AppBackupData): AppBackupData =
        json.decodeFromString(json.encodeToString(AppBackupData.serializer(), data))

    // ==================== Per-entity field preservation ====================

    @Test
    fun `workout log survives round trip with every field`() {
        val original = WorkoutLog(
            connectId = "hc-123",
            date = LocalDate.of(2026, 3, 14),
            type = WorkoutType.BIKE,
            durationMinutes = 95,
            avgHeartRate = 142,
            calories = 830,
            computedTSS = 88,
            distanceMeters = 42_195.5,
            avgSpeedKmh = 31.4,
            avgPowerWatts = 215,
            steps = 1234,
            hrZoneDistribution = mapOf("Z1" to 10, "Z2" to 45, "Z3" to 40),
            powerZoneDistribution = mapOf("Z1" to 20, "Z2" to 75),
            isIgnored = true
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, workoutLogs = listOf(original.toDto()))
        ).workoutLogs.single().toEntity()

        assertEquals(original, restored)
    }

    @Test
    fun `sleep log round trip keeps the sleep score`() {
        // sleepScore was missing from the version 4 DTO, so it was silently dropped on restore.
        val original = SleepLog(
            connectId = "sleep-1",
            date = LocalDate.of(2026, 1, 2),
            startTimeMillis = 1_700_000_000_000,
            endTimeMillis = 1_700_028_000_000,
            durationMinutes = 466,
            title = "Night sleep",
            stagesJson = """[{"stage":"DEEP","minutes":90}]""",
            deepSleepMinutes = 90,
            lightSleepMinutes = 250,
            remSleepMinutes = 100,
            awakeMinutes = 26,
            sleepScore = 82,
            importedAt = 1_700_030_000_000,
            isIgnored = false
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, sleepLogs = listOf(original.toDto()))
        ).sleepLogs.single().toEntity()

        assertEquals(original, restored)
        assertEquals(82, restored.sleepScore)
    }

    @Test
    fun `raw workout samples survive round trip`() {
        val original = RawWorkoutData(
            connectId = "hc-123",
            rawExerciseType = 56,
            startTimeMillis = 1_700_000_000_000,
            endTimeMillis = 1_700_003_600_000,
            hrSamplesJson = """[{"t":1,"v":120}]""",
            powerSamplesJson = """[{"t":1,"v":200}]""",
            rawCalories = 700,
            rawDistanceMeters = 21_097.0,
            rawSteps = 9000,
            routeJson = """[{"lat":55.6,"lng":12.5}]""",
            cnsJson = """{"load":42}""",
            importedAt = 1_700_004_000_000
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, rawWorkoutData = listOf(original.toDto()))
        ).rawWorkoutData.single().toEntity()

        assertEquals(original, restored)
    }

    @Test
    fun `training plan survives round trip`() {
        val original = TrainingPlan(
            id = "plan-1",
            date = LocalDate.of(2026, 5, 1),
            type = WorkoutType.STRENGTH,
            subType = "Upper body",
            durationMinutes = 60,
            plannedTSS = 55,
            plannedDistanceMeters = 5000,
            strengthFocus = StrengthFocus.entries.first(),
            intensity = Intensity.entries.first()
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, trainingPlans = listOf(original.toDto()))
        ).trainingPlans.single().toEntity()

        assertEquals(original, restored)
    }

    @Test
    fun `body composition log survives round trip`() {
        val original = BodyCompositionLog(
            id = "bc-1",
            timestamp = 1_700_000_000_000,
            weightKg = 78.4,
            bodyFatPercent = 14.2,
            boneMassKg = 3.1,
            leanMassKg = 64.0,
            importedAt = 1_700_000_100_000,
            isIgnored = true
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, bodyCompositionLogs = listOf(original.toDto()))
        ).bodyCompositionLogs.single().toEntity()

        assertEquals(original, restored)
    }

    @Test
    fun `nutrition log survives round trip and keeps unlogged values null`() {
        val original = NutritionLog(
            date = LocalDate.of(2026, 2, 3),
            energyKcal = 2450.0,
            proteinG = 180.5,
            carbsG = null,
            fatG = 70.25,
            creatineTaken = true,
            updatedAt = 1_700_000_000_000
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, nutritionLogs = listOf(original.toDto()))
        ).nutritionLogs.single().toEntity()

        assertEquals(original, restored)
        // A day with no carbs logged must not come back as a genuine zero.
        assertNull(restored.carbsG)
    }

    @Test
    fun `nutrition ledger entry survives round trip`() {
        val original = NutritionEntry(
            id = 42L,
            date = LocalDate.of(2026, 2, 3),
            loggedAt = 1_700_000_000_000,
            kind = NutritionEntryKind.ADJUSTMENT,
            label = "Chicken & rice",
            deltaKcal = 160.0,
            deltaProteinG = -12.5,
            deltaCarbsG = null,
            deltaFatG = 3.0,
            prevKcal = 1840.0,
            prevProteinG = 150.0,
            creatineFrom = false,
            creatineTo = true
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, nutritionEntries = listOf(original.toDto()))
        ).nutritionEntries.single().toEntity()

        assertEquals(original, restored)
        // A negative delta is a real adjustment, not corruption — the sign has to survive.
        assertEquals(-12.5, restored.deltaProteinG!!, 0.0001)
        // Untouched fields stay null so undo doesn't subtract a phantom zero.
        assertNull(restored.deltaCarbsG)
    }

    @Test
    fun `nutrition preset survives round trip`() {
        val original = NutritionPreset(
            id = "preset-1",
            label = "Protein shake",
            kcal = 220.0,
            proteinG = 40.0,
            carbsG = null,
            fatG = 5.5,
            createdAt = 1_700_000_000_000
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, nutritionPresets = listOf(original.toDto()))
        ).nutritionPresets.single().toEntity()

        assertEquals(original, restored)
        // A preset saved without carbs must not come back as a genuine zero.
        assertNull(restored.carbsG)
    }

    @Test
    fun `scanned food survives round trip`() {
        val original = ScannedFoodCache(
            barcode = "5000112637922",
            name = "Oat bar",
            kcalPer100g = 384.0,
            proteinPer100g = null,
            // A row the athlete corrected by hand is the one it would hurt most to lose, since a
            // later lookup will never overwrite it back into place.
            isManualOverride = true,
            updatedAt = 1_700_000_000_000
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, scannedFoods = listOf(original.toDto()))
        ).scannedFoods.single().toEntity()

        assertEquals(original, restored)
        assertNull(restored.proteinPer100g)
    }

    @Test
    fun `wellness log and task survive round trip`() {
        val log = DailyWellnessLog(
            date = LocalDate.of(2026, 4, 4),
            sleepMinutes = 430,
            hrvRmssd = 62.5,
            morningWeight = 77.9,
            sorenessIndex = 3,
            moodIndex = 8,
            allergySeverity = AllergySeverity.MILD,
            completedTaskIds = listOf(1L, 5L, 9L)
        )
        val task = WellnessTaskDefinition(
            id = 7L,
            title = "Foam roll",
            description = "10 minutes after strength",
            type = TaskTriggerType.TRIGGER_STRENGTH,
            triggerThreshold = 2
        )

        val restored = roundTrip(
            AppBackupData(
                timestamp = 0L,
                wellnessLogs = listOf(log.toDto()),
                wellnessTasks = listOf(task.toDto())
            )
        )

        assertEquals(log, restored.wellnessLogs.single().toEntity())
        assertEquals(task, restored.wellnessTasks.single().toEntity())
    }

    @Test
    fun `day notes templates and special periods survive round trip`() {
        val note = DayNote(date = LocalDate.of(2026, 6, 6), note = "Felt strong, legs heavy")
        val template = DayTemplate(id = "t-1", name = "Brick day", activitiesJson = """[{"a":1}]""")
        val period = SpecialPeriod(
            id = "sp-1",
            type = SpecialPeriodType.INJURY,
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 21),
            notes = "Calf strain"
        )

        val restored = roundTrip(
            AppBackupData(
                timestamp = 0L,
                dayNotes = listOf(note.toDto()),
                dayTemplates = listOf(template.toDto()),
                specialPeriods = listOf(period.toDto())
            )
        )

        assertEquals(note, restored.dayNotes.single().toEntity())
        assertEquals(template, restored.dayTemplates.single().toEntity())
        assertEquals(period, restored.specialPeriods.single().toEntity())
    }

    @Test
    fun `every preference type survives round trip`() {
        val entries = listOf(
            PreferenceEntry("dark_theme", PreferenceEntry.TYPE_BOOLEAN, "false"),
            PreferenceEntry("ftp_bike", PreferenceEntry.TYPE_INT, "265"),
            PreferenceEntry("goal_date", PreferenceEntry.TYPE_LONG, "20500"),
            PreferenceEntry("weekly_hours_goal", PreferenceEntry.TYPE_FLOAT, "12.5"),
            PreferenceEntry("some_double", PreferenceEntry.TYPE_DOUBLE, "1.25"),
            PreferenceEntry("active_running_goal", PreferenceEntry.TYPE_STRING, """{"k":"v"}"""),
            PreferenceEntry("a_set", PreferenceEntry.TYPE_STRING_SET, """["a","b"]""")
        )

        val restored = roundTrip(
            AppBackupData(timestamp = 0L, preferences = entries.map { it.toDto() })
        ).preferences.map { it.toEntry() }

        assertEquals(entries, restored)
    }

    @Test
    fun `preference entry infers the type tag from the stored value`() {
        assertEquals(PreferenceEntry.TYPE_BOOLEAN, PreferenceEntry.of("k", true)?.type)
        assertEquals(PreferenceEntry.TYPE_INT, PreferenceEntry.of("k", 1)?.type)
        assertEquals(PreferenceEntry.TYPE_LONG, PreferenceEntry.of("k", 1L)?.type)
        assertEquals(PreferenceEntry.TYPE_FLOAT, PreferenceEntry.of("k", 1f)?.type)
        assertEquals(PreferenceEntry.TYPE_DOUBLE, PreferenceEntry.of("k", 1.0)?.type)
        assertEquals(PreferenceEntry.TYPE_STRING, PreferenceEntry.of("k", "v")?.type)
        assertEquals(PreferenceEntry.TYPE_STRING_SET, PreferenceEntry.of("k", setOf("v"))?.type)
        assertNull(PreferenceEntry.of("k", null))
    }

    // ==================== Coverage ====================

    @Test
    fun `export covers every table in the database`() {
        // Mirrors AppDatabase's entity list. If a table is added there without being added to
        // AppBackupData, this test is the intended failure point — before a user loses the data.
        val data = AppBackupData(timestamp = 0L)
        val covered = data.recordCounts().keys

        val expected = setOf(
            "trainingPlans",
            "workoutLogs",
            "rawWorkoutData",
            "sleepLogs",
            "specialPeriods",
            "dayNotes",
            "dayTemplates",
            "wellnessLogs",
            "wellnessTasks",
            "bodyCompositionLogs",
            "nutritionLogs",
            "nutritionEntries",
            "nutritionPresets",
            "dailyActivityLogs",
            "liftSessionLogs",
            "liftSetLogs",
            "liftExerciseCatalog",
            "scannedFoods",
            "preferences"
        )

        assertEquals(expected, covered)
    }

    @Test
    fun `record counts reflect the contents`() {
        val data = AppBackupData(
            timestamp = 0L,
            workoutLogs = listOf(
                WorkoutLog(
                    connectId = "a",
                    date = LocalDate.of(2026, 1, 1),
                    type = WorkoutType.RUN,
                    durationMinutes = 30
                ).toDto()
            ),
            dayNotes = listOf(DayNote(LocalDate.of(2026, 1, 1), "note").toDto())
        )

        assertEquals(1, data.recordCounts()["workoutLogs"])
        assertEquals(1, data.recordCounts()["dayNotes"])
        assertEquals(0, data.recordCounts()["sleepLogs"])
    }

    // ==================== Backward compatibility ====================

    @Test
    fun `version 4 backup still parses`() {
        // A real version 4 payload: no health tables, and the profile in its own object.
        val v4 = """
            {
              "version": 4,
              "timestamp": 1700000000000,
              "trainingPlans": [
                {
                  "id": "plan-1",
                  "date": "2026-05-01",
                  "type": "RUN",
                  "subType": null,
                  "durationMinutes": 45,
                  "plannedTSS": 40,
                  "strengthFocus": null,
                  "intensity": null
                }
              ],
              "workoutLogs": [
                {
                  "connectId": "hc-1",
                  "date": "2026-05-01",
                  "type": "RUN",
                  "durationMinutes": 44,
                  "avgHeartRate": 150,
                  "calories": 500,
                  "computedTSS": 42,
                  "distanceMeters": 10000.0,
                  "avgSpeedKmh": 13.6,
                  "avgPowerWatts": null,
                  "steps": 9000
                }
              ],
              "userProfile": {
                "id": 1,
                "ftpBike": 250,
                "maxHeartRate": 190,
                "defaultSwimTSS": 60,
                "defaultStrengthHeavyTSS": 60,
                "defaultStrengthLightTSS": 30,
                "goalDate": "2026-09-01",
                "weeklyHoursGoal": 10.0,
                "lthr": 165,
                "cssSecondsper100m": 95,
                "thresholdRunPace": 260
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString(AppBackupData.serializer(), v4)

        assertEquals(4, parsed.version)
        assertEquals(1, parsed.trainingPlans.size)
        assertEquals(1, parsed.workoutLogs.size)
        // Tables that didn't exist in version 4 default to empty rather than failing the parse.
        assertTrue(parsed.nutritionLogs.isEmpty())
        assertTrue(parsed.bodyCompositionLogs.isEmpty())
        assertTrue(parsed.preferences.isEmpty())

        val profile = parsed.userProfile
        assertNotNull(profile)
        assertEquals(250, profile!!.toEntity().ftpBike)
        assertEquals(LocalDate.of(2026, 9, 1), profile.toEntity().goalDate)
    }

    @Test
    fun `unknown enum names fall back instead of failing the whole restore`() {
        val dto = WorkoutLogDto(
            connectId = "x",
            date = LocalDate.of(2026, 1, 1),
            type = "PADEL_TENNIS_ON_ICE",
            durationMinutes = 30,
            avgHeartRate = null,
            calories = null,
            computedTSS = null,
            distanceMeters = null,
            avgSpeedKmh = null,
            avgPowerWatts = null,
            steps = null
        )

        // The record is kept — losing one workout's *type* beats losing the workout.
        assertEquals(WorkoutType.OTHER, dto.toEntity().type)
    }

    @Test
    fun `special period with an unknown type is skipped rather than mislabelled`() {
        val dto = SpecialPeriodDto(
            id = "sp-x",
            type = "SABBATICAL",
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 8),
            notes = null
        )

        // No neutral fallback exists, and calling an injury a holiday would change how the
        // coach plans around it.
        assertNull(dto.toEntity())
    }

    @Test
    fun `a backup from a newer format version is identifiable before import`() {
        val newer = AppBackupData(version = BackupManager.BACKUP_VERSION + 1, timestamp = 0L)
        val parsed = roundTrip(newer)

        assertTrue(parsed.version > BackupManager.BACKUP_VERSION)
    }
}
