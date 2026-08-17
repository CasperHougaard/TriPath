package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.NutritionEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for the nutrition ledger — the itemised adds behind each day's totals.
 *
 * Writes here are always paired with the matching [NutritionLogDao] total change inside one
 * transaction, so the log can never claim an add the totals don't include (or vice versa).
 */
@Dao
interface NutritionEntryDao {

    /** Observe a day's entries, newest first. */
    @Query("SELECT * FROM nutrition_entries WHERE date = :date ORDER BY loggedAt DESC, id DESC")
    fun getByDateFlow(date: LocalDate): Flow<List<NutritionEntry>>

    @Query("SELECT * FROM nutrition_entries WHERE id = :id")
    suspend fun getById(id: Long): NutritionEntry?

    /** All entries as a one-shot list (for backup). */
    @Query("SELECT * FROM nutrition_entries ORDER BY loggedAt DESC")
    suspend fun getAllOnce(): List<NutritionEntry>

    /** How many entries remain for a day — used to decide whether an emptied day row can go. */
    @Query("SELECT COUNT(*) FROM nutrition_entries WHERE date = :date")
    suspend fun countForDate(date: LocalDate): Int

    /** Total across all days, for the My Data browser. */
    @Query("SELECT COUNT(*) FROM nutrition_entries")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(entry: NutritionEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NutritionEntry>)

    @Query("DELETE FROM nutrition_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM nutrition_entries WHERE date = :date")
    suspend fun deleteForDate(date: LocalDate)

    @Query("DELETE FROM nutrition_entries")
    suspend fun deleteAll()
}
