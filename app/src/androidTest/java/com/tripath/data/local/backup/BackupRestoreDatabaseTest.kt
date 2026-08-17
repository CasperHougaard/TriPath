package com.tripath.data.local.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.entities.BodyCompositionLog
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.database.entities.DayNote
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionEntryKind
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.SleepLog
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.AllergySeverity
import com.tripath.data.model.WorkoutType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Verifies backup and restore against a real Room database, covering what the pure-JVM
 * [BackupRoundTripTest] cannot: the type converters (LocalDate as epoch day, zone-distribution
 * maps, enums) and the DAOs' upsert behaviour.
 *
 * Uses an **in-memory** database, so the test itself never touches the installed app's data.
 *
 * ## Warning: the Gradle task wipes the app
 * `./gradlew connectedAndroidTest` **uninstalls com.tripath when it finishes**, which deletes the
 * app's database and preferences on that device. The tests are harmless; the task is not. Before
 * running it on a phone with real training data, export a backup first (My Data → Export file)
 * or expect to restore afterwards.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreDatabaseTest {

    private lateinit var db: AppDatabase

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ==================== Fixtures ====================

    private val workout = WorkoutLog(
        connectId = "hc-1",
        date = LocalDate.of(2026, 8, 17),
        type = WorkoutType.BIKE,
        durationMinutes = 29,
        avgHeartRate = 132,
        calories = 286,
        computedTSS = 31,
        distanceMeters = 14_500.0,
        avgSpeedKmh = 30.0,
        avgPowerWatts = 180,
        steps = null,
        hrZoneDistribution = mapOf("Z1" to 5, "Z2" to 20, "Z3" to 4),
        powerZoneDistribution = mapOf("Z2" to 29),
        isIgnored = false
    )

    private val sleep = SleepLog(
        connectId = "sleep-1",
        date = LocalDate.of(2026, 8, 16),
        startTimeMillis = 1_786_900_000_000,
        endTimeMillis = 1_786_928_000_000,
        durationMinutes = 466,
        title = "Sleep",
        stagesJson = """[{"stage":"DEEP","minutes":90}]""",
        deepSleepMinutes = 90,
        lightSleepMinutes = 250,
        remSleepMinutes = 100,
        awakeMinutes = 26,
        sleepScore = 82,
        importedAt = 1_786_930_000_000,
        isIgnored = false
    )

    private val bodyScan = BodyCompositionLog(
        id = "bc-1",
        timestamp = 1_786_900_000_000,
        weightKg = 78.4,
        bodyFatPercent = 14.2,
        boneMassKg = 3.1,
        leanMassKg = 64.0,
        importedAt = 1_786_900_100_000,
        isIgnored = true
    )

    private val nutrition = NutritionLog(
        date = LocalDate.of(2026, 8, 17),
        energyKcal = 2450.0,
        proteinG = 180.5,
        carbsG = null,
        fatG = 70.25,
        creatineTaken = true,
        updatedAt = 1_786_900_000_000
    )

    private val ledgerEntry = NutritionEntry(
        id = 1L,
        date = LocalDate.of(2026, 8, 17),
        loggedAt = 1_786_900_000_000,
        kind = NutritionEntryKind.CUSTOM_ADD,
        label = "Chicken & rice",
        deltaKcal = 650.0,
        deltaProteinG = 45.0,
        deltaCarbsG = null,
        deltaFatG = 12.0
    )

    private val wellness = DailyWellnessLog(
        date = LocalDate.of(2026, 8, 17),
        sleepMinutes = 430,
        hrvRmssd = 62.5,
        morningWeight = 77.9,
        sorenessIndex = 3,
        moodIndex = 8,
        allergySeverity = AllergySeverity.MILD,
        completedTaskIds = listOf(1L, 5L)
    )

    private val note = DayNote(date = LocalDate.of(2026, 8, 17), note = "Legs heavy")

    private suspend fun seed() {
        db.workoutLogDao().insertAll(listOf(workout))
        db.sleepLogDao().insertAll(listOf(sleep))
        db.bodyCompositionDao().insertAll(listOf(bodyScan))
        db.nutritionLogDao().upsert(nutrition)
        db.nutritionEntryDao().insertAll(listOf(ledgerEntry))
        db.wellnessDao().insertLog(wellness)
        db.dayNoteDao().insertAll(listOf(note))
    }

    /** Reads the database the way [BackupManager.exportToBackupData] does. */
    private suspend fun exportSnapshot() = AppBackupData(
        timestamp = 1_786_978_803_303,
        workoutLogs = db.workoutLogDao().getAllOnceIncludingIgnored().map { it.toDto() },
        sleepLogs = db.sleepLogDao().getAllOnce().map { it.toDto() },
        bodyCompositionLogs = db.bodyCompositionDao().getAllOnce().map { it.toDto() },
        nutritionLogs = db.nutritionLogDao().getAllOnce().map { it.toDto() },
        nutritionEntries = db.nutritionEntryDao().getAllOnce().map { it.toDto() },
        wellnessLogs = db.wellnessDao().getAllLogsOnce().map { it.toDto() },
        dayNotes = db.dayNoteDao().getAllOnce().map { it.toDto() }
    )

    /** Writes the database the way [BackupManager.importBackupData] does. */
    private suspend fun restoreSnapshot(data: AppBackupData) {
        db.workoutLogDao().insertAll(data.workoutLogs.map { it.toEntity() })
        db.sleepLogDao().insertAll(data.sleepLogs.map { it.toEntity() })
        db.bodyCompositionDao().insertAll(data.bodyCompositionLogs.map { it.toEntity() })
        data.nutritionLogs.forEach { db.nutritionLogDao().upsert(it.toEntity()) }
        db.nutritionEntryDao().insertAll(data.nutritionEntries.map { it.toEntity() })
        data.wellnessLogs.forEach { db.wellnessDao().insertLog(it.toEntity()) }
        db.dayNoteDao().insertAll(data.dayNotes.map { it.toEntity() })
    }

    // ==================== Tests ====================

    @Test
    fun exportThenWipeThenRestoreRebuildsEveryRecord() = runTest {
        seed()

        val backupJson = json.encodeToString(AppBackupData.serializer(), exportSnapshot())

        // The destructive half of a replace-all restore.
        db.workoutLogDao().deleteAll()
        db.sleepLogDao().deleteAll()
        db.bodyCompositionDao().deleteAll()
        db.nutritionLogDao().deleteAll()
        db.nutritionEntryDao().deleteAll()
        db.wellnessDao().deleteAllLogs()
        db.dayNoteDao().deleteAll()

        assertEquals(0, db.workoutLogDao().getCount())
        assertEquals(0, db.bodyCompositionDao().getCount())

        restoreSnapshot(json.decodeFromString(AppBackupData.serializer(), backupJson))

        // Every field must come back through the type converters unchanged.
        assertEquals(workout, db.workoutLogDao().getAllOnceIncludingIgnored().single())
        assertEquals(sleep, db.sleepLogDao().getAllOnce().single())
        assertEquals(bodyScan, db.bodyCompositionDao().getAllOnce().single())
        assertEquals(nutrition, db.nutritionLogDao().getAllOnce().single())
        assertEquals(ledgerEntry, db.nutritionEntryDao().getAllOnce().single())
        assertEquals(wellness, db.wellnessDao().getAllLogsOnce().single())
        assertEquals(note, db.dayNoteDao().getAllOnce().single())
    }

    @Test
    fun mergeRestoreKeepsRecordsThatArentInTheBackup() = runTest {
        seed()
        val backupJson = json.encodeToString(AppBackupData.serializer(), exportSnapshot())

        // Simulates data logged on the new phone after the backup was taken.
        val newerWorkout = workout.copy(
            connectId = "hc-later",
            date = LocalDate.of(2026, 8, 20),
            durationMinutes = 61
        )
        db.workoutLogDao().insertAll(listOf(newerWorkout))
        db.bodyCompositionDao().insertAll(listOf(bodyScan.copy(id = "bc-later")))

        restoreSnapshot(json.decodeFromString(AppBackupData.serializer(), backupJson))

        // This is the guarantee that makes MERGE the safe default: importing an older backup
        // must not delete anything newer.
        assertEquals(2, db.workoutLogDao().getCount())
        assertEquals(2, db.bodyCompositionDao().getCount())
        assertNotNull(db.workoutLogDao().getAllOnceIncludingIgnored().find { it.connectId == "hc-later" })
    }

    @Test
    fun mergeRestoreIsIdempotent() = runTest {
        seed()
        val backupJson = json.encodeToString(AppBackupData.serializer(), exportSnapshot())
        val snapshot = json.decodeFromString(AppBackupData.serializer(), backupJson)

        restoreSnapshot(snapshot)
        restoreSnapshot(snapshot)

        // Upserting by primary key must not duplicate rows, however many times it runs.
        assertEquals(1, db.workoutLogDao().getCount())
        assertEquals(1, db.sleepLogDao().getCount())
        assertEquals(1, db.bodyCompositionDao().getCount())
        assertEquals(1, db.nutritionLogDao().getCount())
        assertEquals(1, db.dayNoteDao().getCount())
    }

    @Test
    fun restoringOverAnEditedRecordTakesTheBackupsVersion() = runTest {
        seed()
        val backupJson = json.encodeToString(AppBackupData.serializer(), exportSnapshot())

        // Same primary key, different content.
        db.workoutLogDao().insertAll(listOf(workout.copy(durationMinutes = 999, calories = 1)))
        assertEquals(999, db.workoutLogDao().getAllOnceIncludingIgnored().single().durationMinutes)

        restoreSnapshot(json.decodeFromString(AppBackupData.serializer(), backupJson))

        assertEquals(29, db.workoutLogDao().getAllOnceIncludingIgnored().single().durationMinutes)
    }

    @Test
    fun nullMetricsSurviveAsNullNotZero() = runTest {
        seed()
        val backupJson = json.encodeToString(AppBackupData.serializer(), exportSnapshot())
        db.nutritionLogDao().deleteAll()
        db.nutritionEntryDao().deleteAll()

        restoreSnapshot(json.decodeFromString(AppBackupData.serializer(), backupJson))

        // An unlogged macro must not come back as a real zero, or the day would read as
        // "ate no carbs" instead of "carbs not tracked".
        assertNull(db.nutritionLogDao().getAllOnce().single().carbsG)
        assertNull(db.nutritionEntryDao().getAllOnce().single().deltaCarbsG)
        assertNull(db.workoutLogDao().getAllOnceIncludingIgnored().single().steps)
    }

    @Test
    fun ignoredFlagsSurviveRestore() = runTest {
        seed()
        val backupJson = json.encodeToString(AppBackupData.serializer(), exportSnapshot())
        db.bodyCompositionDao().deleteAll()

        restoreSnapshot(json.decodeFromString(AppBackupData.serializer(), backupJson))

        // Excluded measurements must stay excluded, otherwise a restore silently re-pollutes
        // the user's charts with outliers they had already dismissed.
        assertEquals(true, db.bodyCompositionDao().getAllOnce().single().isIgnored)
    }
}
