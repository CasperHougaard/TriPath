package com.tripath.data.local.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.tripath.BuildConfig
import com.tripath.data.local.database.AppDatabase
import com.tripath.data.local.database.entities.DailyWellnessLog
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.domain.health.AnalysisDay
import com.tripath.domain.health.CombinedAnalytics
import com.tripath.domain.strain.DisciplineVerdict
import com.tripath.domain.strain.ReadinessDriver
import com.tripath.domain.strain.ReadinessService
import com.tripath.domain.strain.StrainChannel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Read-only bridge that lets LiftPath see TriPath's training load, recovery and energy figures.
 *
 * LiftPath computes fatigue from lifting alone and knows nothing about intensity or recovery;
 * everything it needs already exists here (TSS, CTL/ATL/TSB, sleep, HRV, TDEE vs. intake) but
 * lives in a private Room database. This exposes exactly those figures and nothing else — no
 * routes, no raw samples, no writes.
 *
 * **Why a package check rather than a `signature` permission:** the two apps are signed with
 * different keys (TriPath has no release signing config; LiftPath signs with its own keystore),
 * so a signature permission would simply always be denied. [assertCallerAllowed] uses
 * `callingPackage`, which the framework derives from the binder calling UID and an app therefore
 * cannot forge.
 */
class TriPathShareProvider : ContentProvider() {

    private companion object {
        const val TAG = "TriPathShareProvider"

        const val CODE_HANDSHAKE = 1
        const val CODE_DAYS = 2
        const val CODE_WORKOUTS = 3
        const val CODE_READINESS = 4

        /** Range served when the caller does not ask for one. Covers LiftPath's fatigue window. */
        const val DEFAULT_RANGE_DAYS = 28L

        /** Hard ceiling so a malformed range cannot walk the whole history day by day. */
        const val MAX_RANGE_DAYS = 400L
    }

    /**
     * Hilt is not ready when a provider's `onCreate` runs — providers are created before
     * `Application.onCreate`. Resolved lazily on first query instead, which is always later.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ShareEntryPoint {
        fun appDatabase(): AppDatabase
        fun preferencesManager(): PreferencesManager
        fun readinessService(): ReadinessService
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(TriPathShareContract.AUTHORITY, TriPathShareContract.PATH_HANDSHAKE, CODE_HANDSHAKE)
        addURI(TriPathShareContract.AUTHORITY, TriPathShareContract.PATH_DAYS, CODE_DAYS)
        addURI(TriPathShareContract.AUTHORITY, TriPathShareContract.PATH_WORKOUTS, CODE_WORKOUTS)
        addURI(TriPathShareContract.AUTHORITY, TriPathShareContract.PATH_READINESS, CODE_READINESS)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        assertCallerAllowed()
        val ctx = context ?: return null
        val entryPoint = EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            ShareEntryPoint::class.java
        )

        return try {
            when (uriMatcher.match(uri)) {
                CODE_HANDSHAKE -> handshakeCursor(entryPoint)
                CODE_DAYS -> daysCursor(entryPoint, uri)
                CODE_WORKOUTS -> workoutsCursor(entryPoint, uri)
                CODE_READINESS -> readinessCursor(entryPoint)
                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            // A failed read must not take LiftPath's readiness screen down with it; an empty
            // cursor reads to the caller as "nothing to sync".
            Log.e(TAG, "Query failed for $uri", e)
            null
        }
    }

    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        CODE_HANDSHAKE, CODE_DAYS, CODE_WORKOUTS, CODE_READINESS ->
            "vnd.android.cursor.dir/vnd.${TriPathShareContract.AUTHORITY}"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("TriPath data is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("TriPath data is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("TriPath data is read-only")

    // ---- Cursors -----------------------------------------------------------------------------

    private fun handshakeCursor(entryPoint: ShareEntryPoint): Cursor = runBlocking {
        val db = entryPoint.appDatabase()
        val workoutCount = db.workoutLogDao().getCount()
        val latestWorkout = db.workoutLogDao().getAllOnce().maxByOrNull { it.date }?.date
        val latestWellness = db.wellnessDao().getAllLogsOnce().maxByOrNull { it.date }?.date

        MatrixCursor(TriPathShareContract.Handshake.COLUMNS).apply {
            addRow(
                arrayOf(
                    TriPathShareContract.CONTRACT_VERSION,
                    TriPathShareContract.schemaHash(),
                    TriPathShareContract.CAPABILITIES.joinToString(","),
                    BuildConfig.VERSION_NAME,
                    workoutCount,
                    latestWorkout?.toString(),
                    latestWellness?.toString()
                )
            )
        }
    }

    /**
     * One row per calendar day, built by [CombinedAnalytics] so the figures LiftPath sees are the
     * same ones TriPath's own analysis screen shows — same TDEE model, same forward-filled weight,
     * same CTL seeding — rather than a second, subtly different join.
     */
    private fun daysCursor(entryPoint: ShareEntryPoint, uri: Uri): Cursor = runBlocking {
        val db = entryPoint.appDatabase()
        val (from, to) = requestedRange(uri)

        val analysis = CombinedAnalytics.build(
            allWorkouts = db.workoutLogDao().getAllOnce(),
            nutrition = db.nutritionLogDao().getAllOnce(),
            sleep = db.sleepLogDao().getAllOnce(),
            bodyComposition = db.bodyCompositionDao().getAllOnce(),
            profile = entryPoint.preferencesManager().getUserProfile(),
            periodDays = ChronoUnit.DAYS.between(from, to),
            today = to
        )

        // Subjective/HRV data is not part of the combined analysis, so join it on here by date.
        val wellnessByDate: Map<LocalDate, DailyWellnessLog> =
            db.wellnessDao().getAllLogsOnce().associateBy { it.date }

        MatrixCursor(TriPathShareContract.Days.COLUMNS).apply {
            analysis.days
                .filter { !it.date.isBefore(from) }
                .forEach { day -> addRow(dayRow(day, wellnessByDate[day.date])) }
        }
    }

    private fun dayRow(day: AnalysisDay, wellness: DailyWellnessLog?): Array<Any?> = arrayOf(
        day.date.toString(),
        day.tss,
        day.ctl,
        day.atl,
        day.tsb,
        day.intakeKcal,
        day.expenditureKcal,
        day.balanceKcal,
        day.weightKg,
        day.sleepMinutes,
        day.sleepScore,
        wellness?.hrvRmssd,
        wellness?.sorenessIndex,
        wellness?.moodIndex,
        day.targetKcal,
        day.targetProteinG,
        day.targetCarbsG,
        day.targetFatG,
        day.rmrSource.name,
        day.energyAvailability,
        day.neatSteps
    )

    private fun workoutsCursor(entryPoint: ShareEntryPoint, uri: Uri): Cursor = runBlocking {
        val db = entryPoint.appDatabase()
        val (from, to) = requestedRange(uri)

        // WorkoutLog carries only a date; the real session bounds live in the raw record, and
        // LiftPath's fatigue timeline is hour-resolution, so hand them over when they survive.
        val rawByConnectId = db.rawWorkoutDataDao().getAll().associateBy { it.connectId }

        MatrixCursor(TriPathShareContract.Workouts.COLUMNS).apply {
            db.workoutLogDao().getAllOnce()
                .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
                .sortedBy { it.date }
                .forEach { log ->
                    val raw = rawByConnectId[log.connectId]
                    addRow(
                        arrayOf(
                            log.connectId,
                            log.date.toString(),
                            log.type.name,
                            log.durationMinutes,
                            log.avgHeartRate,
                            log.calories,
                            log.computedTSS,
                            log.distanceMeters,
                            log.hrZoneDistribution?.let { runCatching { Json.encodeToString(it) }.getOrNull() },
                            raw?.startTimeMillis,
                            raw?.endTimeMillis
                        )
                    )
                }
        }
    }


    /**
     * The current readiness verdict, computed by the same [ReadinessService] the Coach screen uses.
     *
     * Deliberately not a cached snapshot: readiness depends on how long ago the last session was,
     * so a value stored this morning is already stale by evening. Computing on demand costs one
     * pass over a bounded window and keeps both apps looking at the same number.
     */
    private fun readinessCursor(entryPoint: ShareEntryPoint): Cursor = runBlocking {
        val assessment = entryPoint.readinessService().currentReadiness()
        val strain = assessment.strain

        fun freshness(channel: StrainChannel): Int? = strain[channel]?.freshness

        val hoursToFresh = strain.channels.values
            .mapNotNull { state -> state.hoursToFresh?.let { state.channel.name to it } }
            .toMap()

        MatrixCursor(TriPathShareContract.Readiness.COLUMNS).apply {
            addRow(
                arrayOf(
                    assessment.score,
                    assessment.band.name,
                    assessment.action.name,
                    freshness(StrainChannel.LOWER_IMPACT),
                    freshness(StrainChannel.LOWER_MUSCULAR),
                    freshness(StrainChannel.UPPER_MUSCULAR),
                    freshness(StrainChannel.SYSTEMIC),
                    encodeJson(hoursToFresh),
                    encodeDrivers(assessment.drivers),
                    encodeVerdicts(assessment.disciplineVerdicts),
                    encodeJson(strain.muscleFreshness),
                    assessment.guidance,
                    assessment.weeklyLoadRampPct,
                    System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Payloads carry a `v` field alongside their data.
     *
     * The schema hash covers column names and types but cannot see inside a JSON string, so a
     * reshaped payload would otherwise pass every drift check and fail at parse time on the other
     * side. The version here plus the matching capability token is what guards that gap.
     */
    private fun encodeDrivers(drivers: List<ReadinessDriver>): String? = runCatching {
        buildJsonObject {
            put("v", JsonPrimitive(TriPathShareContract.JSON_PAYLOAD_VERSION))
            put(
                "items",
                buildJsonArray {
                    drivers.forEach { driver ->
                        add(
                            buildJsonObject {
                                put("label", JsonPrimitive(driver.label))
                                put("detail", JsonPrimitive(driver.detail))
                                put("impact", JsonPrimitive(driver.impact))
                            }
                        )
                    }
                }
            )
        }.toString()
    }.getOrNull()

    private fun encodeVerdicts(verdicts: List<DisciplineVerdict>): String? = runCatching {
        buildJsonObject {
            put("v", JsonPrimitive(TriPathShareContract.JSON_PAYLOAD_VERSION))
            put(
                "items",
                buildJsonArray {
                    verdicts.forEach { verdict ->
                        add(
                            buildJsonObject {
                                put("discipline", JsonPrimitive(verdict.discipline.name))
                                put("action", JsonPrimitive(verdict.action.name))
                                put("reason", JsonPrimitive(verdict.reason))
                            }
                        )
                    }
                }
            )
        }.toString()
    }.getOrNull()

    private fun encodeJson(values: Map<String, Number>): String? = runCatching {
        buildJsonObject {
            put("v", JsonPrimitive(TriPathShareContract.JSON_PAYLOAD_VERSION))
            put(
                "items",
                buildJsonObject {
                    values.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                }
            )
        }.toString()
    }.getOrNull()

    // ---- Helpers -----------------------------------------------------------------------------

    /**
     * Rejects every caller but LiftPath. `com.android.shell` is allowed in debug builds only, so
     * `adb shell content query` can smoke-test the provider without weakening release builds.
     */
    private fun assertCallerAllowed() {
        val caller = callingPackage
        val allowed = caller == TriPathShareContract.CONSUMER_PACKAGE ||
            (BuildConfig.DEBUG && caller == "com.android.shell")
        if (!allowed) {
            throw SecurityException("Package $caller may not read TriPath data")
        }
    }

    /** Inclusive `from`/`to` from the query string, clamped and defaulted to a sane window. */
    private fun requestedRange(uri: Uri): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        val to = uri.parseDate(TriPathShareContract.QUERY_TO) ?: today
        val from = uri.parseDate(TriPathShareContract.QUERY_FROM) ?: to.minusDays(DEFAULT_RANGE_DAYS)
        if (from.isAfter(to)) return to to to
        val span = ChronoUnit.DAYS.between(from, to)
        return if (span > MAX_RANGE_DAYS) to.minusDays(MAX_RANGE_DAYS) to to else from to to
    }

    private fun Uri.parseDate(key: String): LocalDate? {
        val raw = getQueryParameter(key) ?: return null
        return try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "Ignoring unparseable $key=$raw")
            null
        }
    }
}
