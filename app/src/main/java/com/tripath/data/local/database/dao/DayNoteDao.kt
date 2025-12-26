package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tripath.data.local.database.entities.DayNote
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for DayNote entity.
 */
@Dao
interface DayNoteDao {

    @Query("SELECT * FROM day_notes WHERE date = :date")
    fun getByDate(date: LocalDate): Flow<DayNote?>

    @Query("SELECT * FROM day_notes WHERE date = :date")
    suspend fun getByDateOnce(date: LocalDate): DayNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dayNote: DayNote)

    @Update
    suspend fun update(dayNote: DayNote)

    @Delete
    suspend fun delete(dayNote: DayNote)

    @Query("DELETE FROM day_notes WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)
}

