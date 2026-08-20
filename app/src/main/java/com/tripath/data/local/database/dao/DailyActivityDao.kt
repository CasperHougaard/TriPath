package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.DailyActivityLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyActivityDao {

    @Query("SELECT * FROM daily_activity_logs ORDER BY date DESC")
    fun getAll(): Flow<List<DailyActivityLog>>

    @Query("SELECT * FROM daily_activity_logs ORDER BY date DESC")
    suspend fun getAllOnce(): List<DailyActivityLog>

    @Query("SELECT * FROM daily_activity_logs WHERE date = :date")
    suspend fun getByDate(date: LocalDate): DailyActivityLog?

    @Query("SELECT * FROM daily_activity_logs WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getInRange(from: LocalDate, to: LocalDate): List<DailyActivityLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DailyActivityLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<DailyActivityLog>)

    @Query("DELETE FROM daily_activity_logs")
    suspend fun deleteAll()
}
