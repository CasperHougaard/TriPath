package com.tripath.data.local.liftpath

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.tripath.data.local.database.dao.LiftPathDao
import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSessionLog
import com.tripath.data.local.database.entities.LiftSetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls set-level lifting detail from `content://com.liftpath.share` into the local Lift* tables.
 *
 * Mirrors [com.tripath.data.local.healthconnect.HealthConnectManager]'s sync shape: independent,
 * silently-degrading reads (a missing capability or an unreachable provider yields nothing rather
 * than throwing), and Room is the source of truth the rest of the app reads from afterwards.
 */
@Singleton
class LiftPathSyncManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val liftPathDao: LiftPathDao
) {

    /**
     * Full sync: handshake, then sessions + sets for [daysBack], then the whole exercise catalog.
     * Returns the number of sessions synced, or a failure when the handshake itself failed (no
     * point reading further if LiftPath is unreachable or the schema has drifted).
     */
    suspend fun sync(daysBack: Int = DEFAULT_DAYS_BACK): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!LiftPathConnection.isEnabled(context) || !LiftPathConnection.isInstalled(context)) {
                return@withContext Result.failure(IllegalStateException("LiftPath integration is not enabled"))
            }
            val handshake = LiftPathConnection.handshake(context)
                ?: return@withContext Result.failure(IllegalStateException("LiftPath handshake failed"))
            if (!handshake.hasCapability(LiftPathShareContract.CAP_LIFT_SETS_V1)) {
                Log.w(TAG, "LiftPath does not advertise ${LiftPathShareContract.CAP_LIFT_SETS_V1} - skipping session sync")
                return@withContext Result.success(0)
            }

            val today = LocalDate.now()
            val from = today.minusDays(daysBack.toLong())
            val sessionCount = syncSessionsAndSets(from, today)

            if (handshake.hasCapability(LiftPathShareContract.CAP_LIFT_CATALOG_V1)) {
                syncExerciseCatalog()
            }

            LiftPathConnection.markSynced(context, System.currentTimeMillis())
            Result.success(sessionCount)
        } catch (e: Exception) {
            Log.e(TAG, "LiftPath sync failed", e)
            Result.failure(e)
        }
    }

    private suspend fun syncSessionsAndSets(from: LocalDate, to: LocalDate): Int {
        val uri = LiftPathShareContract.URI_SESSIONS.buildUpon()
            .appendQueryParameter(LiftPathShareContract.QUERY_FROM, from.toString())
            .appendQueryParameter(LiftPathShareContract.QUERY_TO, to.toString())
            .build()

        val sessions = queryRows(uri) { cursor -> cursor.toSessionOrNull() }
        if (sessions.isEmpty()) return 0
        liftPathDao.upsertSessions(sessions)

        val setsUri = LiftPathShareContract.URI_SETS.buildUpon()
            .appendQueryParameter(LiftPathShareContract.QUERY_FROM, from.toString())
            .appendQueryParameter(LiftPathShareContract.QUERY_TO, to.toString())
            .build()
        val setsBySession = queryRows(setsUri) { cursor -> cursor.toSetOrNull() }
            .groupBy { it.sessionId }

        // Replace per-session so a session with zero current sets still clears out old ones.
        sessions.forEach { session ->
            liftPathDao.replaceSets(session.id, setsBySession[session.id].orEmpty())
        }
        return sessions.size
    }

    private suspend fun syncExerciseCatalog() {
        val exercises = queryRows(LiftPathShareContract.URI_EXERCISES) { cursor -> cursor.toExerciseOrNull() }
        if (exercises.isNotEmpty()) {
            liftPathDao.upsertExercises(exercises)
        }
    }

    private fun <T> queryRows(uri: Uri, map: (Cursor) -> T?): List<T> {
        val rows = mutableListOf<T>()
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                map(cursor)?.let { rows.add(it) }
            }
        }
        return rows
    }

    private fun Cursor.toSessionOrNull(): LiftSessionLog? {
        val id = optString(LiftPathShareContract.Sessions.ID) ?: return null
        val date = optString(LiftPathShareContract.Sessions.DATE)?.let { parseDateOrNull(it) } ?: return null
        return LiftSessionLog(
            id = id,
            date = date,
            startMillis = optLong(LiftPathShareContract.Sessions.START_MILLIS),
            durationSeconds = optLong(LiftPathShareContract.Sessions.DURATION_SECONDS),
            planName = optString(LiftPathShareContract.Sessions.PLAN_NAME),
            dominantIntent = optString(LiftPathShareContract.Sessions.DOMINANT_INTENT),
            totalSets = optInt(LiftPathShareContract.Sessions.TOTAL_SETS) ?: 0
        )
    }

    private fun Cursor.toSetOrNull(): LiftSetLog? {
        val sessionId = optString(LiftPathShareContract.Sets.SESSION_ID) ?: return null
        val exerciseId = optInt(LiftPathShareContract.Sets.EXERCISE_ID) ?: return null
        return LiftSetLog(
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = optInt(LiftPathShareContract.Sets.SET_NUMBER) ?: 0,
            kg = optFloat(LiftPathShareContract.Sets.KG) ?: 0f,
            reps = optInt(LiftPathShareContract.Sets.REPS) ?: 0,
            rpe = optFloat(LiftPathShareContract.Sets.RPE),
            isWarmup = (optInt(LiftPathShareContract.Sets.IS_WARMUP) ?: 0) != 0,
            intent = optString(LiftPathShareContract.Sets.INTENT),
            durationSeconds = optInt(LiftPathShareContract.Sets.DURATION_SECONDS),
            bodyweightKg = optFloat(LiftPathShareContract.Sets.BODYWEIGHT_KG)
        )
    }

    private fun Cursor.toExerciseOrNull(): LiftExerciseCatalogEntry? {
        val id = optInt(LiftPathShareContract.Exercises.ID) ?: return null
        val name = optString(LiftPathShareContract.Exercises.NAME) ?: return null
        return LiftExerciseCatalogEntry(
            id = id,
            name = name,
            region = optString(LiftPathShareContract.Exercises.REGION),
            tier = optString(LiftPathShareContract.Exercises.TIER),
            pattern = optString(LiftPathShareContract.Exercises.PATTERN),
            mechanics = optString(LiftPathShareContract.Exercises.MECHANICS),
            primaryTargets = optString(LiftPathShareContract.Exercises.PRIMARY_TARGETS) ?: "",
            secondaryTargets = optString(LiftPathShareContract.Exercises.SECONDARY_TARGETS) ?: ""
        )
    }

    private fun parseDateOrNull(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }

    private fun Cursor.optString(name: String): String? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.optInt(name: String): Int? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getInt(i)
    }

    private fun Cursor.optLong(name: String): Long? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    private fun Cursor.optFloat(name: String): Float? {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) null else getFloat(i)
    }

    companion object {
        private const val TAG = "LiftPathSyncManager"
        const val DEFAULT_DAYS_BACK = 28
    }
}
