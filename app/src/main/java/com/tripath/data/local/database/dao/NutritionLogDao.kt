package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.NutritionLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for NutritionLog — the manual daily nutrition tracker.
 *
 * Mutations are expressed as single atomic UPSERT statements (`ON CONFLICT ... DO UPDATE`)
 * so a quick-add never races a concurrent read-modify-write. Quick-add helpers touch only
 * their own column, leaving every other field untouched (an unlogged field stays null).
 */
@Dao
interface NutritionLogDao {

    /**
     * Get all nutrition logs ordered by date (newest first).
     */
    @Query("SELECT * FROM nutrition_logs ORDER BY date DESC")
    fun getAll(): Flow<List<NutritionLog>>

    /**
     * Get all nutrition logs as a one-shot list (for backup).
     */
    @Query("SELECT * FROM nutrition_logs ORDER BY date DESC")
    suspend fun getAllOnce(): List<NutritionLog>

    /**
     * Observe the nutrition log for a specific date, or null when nothing is logged yet.
     */
    @Query("SELECT * FROM nutrition_logs WHERE date = :date")
    fun getByDateFlow(date: LocalDate): Flow<NutritionLog?>

    /**
     * Get the nutrition log for a specific date (one-shot), or null.
     */
    @Query("SELECT * FROM nutrition_logs WHERE date = :date")
    suspend fun getByDate(date: LocalDate): NutritionLog?

    // ---- Atomic single-macro quick-adds ---------------------------------------------------
    // COALESCE(x, 0) + delta => the first log of a field sets it to `delta` (not null + delta),
    // while other columns are never mentioned and therefore left as-is (null stays null).

    @Query(
        """INSERT INTO nutrition_logs (date, energyKcal, updatedAt) VALUES (:date, :delta, :now)
           ON CONFLICT(date) DO UPDATE SET energyKcal = COALESCE(energyKcal, 0) + :delta, updatedAt = :now"""
    )
    suspend fun addEnergy(date: LocalDate, delta: Double, now: Long)

    @Query(
        """INSERT INTO nutrition_logs (date, proteinG, updatedAt) VALUES (:date, :delta, :now)
           ON CONFLICT(date) DO UPDATE SET proteinG = COALESCE(proteinG, 0) + :delta, updatedAt = :now"""
    )
    suspend fun addProtein(date: LocalDate, delta: Double, now: Long)

    @Query(
        """INSERT INTO nutrition_logs (date, carbsG, updatedAt) VALUES (:date, :delta, :now)
           ON CONFLICT(date) DO UPDATE SET carbsG = COALESCE(carbsG, 0) + :delta, updatedAt = :now"""
    )
    suspend fun addCarbs(date: LocalDate, delta: Double, now: Long)

    @Query(
        """INSERT INTO nutrition_logs (date, fatG, updatedAt) VALUES (:date, :delta, :now)
           ON CONFLICT(date) DO UPDATE SET fatG = COALESCE(fatG, 0) + :delta, updatedAt = :now"""
    )
    suspend fun addFat(date: LocalDate, delta: Double, now: Long)

    /**
     * Custom add: increment several fields at once. The CASE guards preserve a column's null
     * when its delta is null, so "add 20g protein" never turns an unlogged kcal into 0.
     */
    @Query(
        """INSERT INTO nutrition_logs (date, energyKcal, proteinG, carbsG, fatG, updatedAt)
           VALUES (:date, :dKcal, :dProt, :dCarb, :dFat, :now)
           ON CONFLICT(date) DO UPDATE SET
             energyKcal = CASE WHEN :dKcal IS NULL THEN energyKcal ELSE COALESCE(energyKcal, 0) + :dKcal END,
             proteinG   = CASE WHEN :dProt IS NULL THEN proteinG   ELSE COALESCE(proteinG, 0) + :dProt END,
             carbsG     = CASE WHEN :dCarb IS NULL THEN carbsG     ELSE COALESCE(carbsG, 0) + :dCarb END,
             fatG       = CASE WHEN :dFat  IS NULL THEN fatG       ELSE COALESCE(fatG, 0) + :dFat END,
             updatedAt  = :now"""
    )
    suspend fun addNutritionRaw(date: LocalDate, dKcal: Double?, dProt: Double?, dCarb: Double?, dFat: Double?, now: Long)

    /**
     * Set the creatine flag for a day without disturbing macros.
     */
    @Query(
        """INSERT INTO nutrition_logs (date, creatineTaken, updatedAt) VALUES (:date, :taken, :now)
           ON CONFLICT(date) DO UPDATE SET creatineTaken = :taken, updatedAt = :now"""
    )
    suspend fun setCreatine(date: LocalDate, taken: Boolean, now: Long)

    /**
     * Insert or fully replace a day's row (used by the edit dialog, which writes a merged row).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: NutritionLog)

    /**
     * Delete a day's log (clear all data for that date).
     */
    @Delete
    suspend fun delete(log: NutritionLog)

    /**
     * Delete all nutrition logs.
     */
    @Query("DELETE FROM nutrition_logs")
    suspend fun deleteAll()

    /**
     * Get count of nutrition logs.
     */
    @Query("SELECT COUNT(*) FROM nutrition_logs")
    suspend fun getCount(): Int
}
