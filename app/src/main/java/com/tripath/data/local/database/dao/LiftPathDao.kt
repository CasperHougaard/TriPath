package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tripath.data.local.database.entities.LiftExerciseCatalogEntry
import com.tripath.data.local.database.entities.LiftSessionLog
import com.tripath.data.local.database.entities.LiftSetLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Data access for the LiftPath sync tables — see [com.tripath.data.local.liftpath.LiftPathSyncManager]. */
@Dao
interface LiftPathDao {

    @Query("SELECT * FROM lift_session_logs ORDER BY date DESC")
    fun getAllSessions(): Flow<List<LiftSessionLog>>

    @Query("SELECT * FROM lift_session_logs ORDER BY date DESC")
    suspend fun getAllSessionsOnce(): List<LiftSessionLog>

    @Query("SELECT * FROM lift_session_logs WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun getSessionsByDateRange(from: LocalDate, to: LocalDate): List<LiftSessionLog>

    @Query("SELECT * FROM lift_set_logs WHERE sessionId = :sessionId ORDER BY setNumber")
    suspend fun getSetsForSession(sessionId: String): List<LiftSetLog>

    @Query("SELECT * FROM lift_exercise_catalog")
    suspend fun getAllExercisesOnce(): List<LiftExerciseCatalogEntry>

    /** Every set across every session. The strain model joins these by session rather than one at a time. */
    @Query("SELECT * FROM lift_set_logs")
    suspend fun getAllSetsOnce(): List<LiftSetLog>

    @Query("SELECT COUNT(*) FROM lift_session_logs")
    suspend fun getSessionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<LiftSessionLog>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<LiftSetLog>)

    @Query("DELETE FROM lift_set_logs WHERE sessionId = :sessionId")
    suspend fun deleteSetsForSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercises(exercises: List<LiftExerciseCatalogEntry>)

    /**
     * Replaces every set belonging to [sessionId] atomically, so a re-sync can never leave a
     * deleted or renumbered set behind alongside the fresh ones.
     */
    @Transaction
    suspend fun replaceSets(sessionId: String, sets: List<LiftSetLog>) {
        deleteSetsForSession(sessionId)
        insertSets(sets)
    }

    @Query("DELETE FROM lift_session_logs")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM lift_exercise_catalog")
    suspend fun deleteAllExercises()

    @Delete
    suspend fun deleteSessions(sessions: List<LiftSessionLog>)
}
