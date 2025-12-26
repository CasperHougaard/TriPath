package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.SpecialPeriod
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SpecialPeriodDao {

    @Query("SELECT * FROM special_periods ORDER BY startDate DESC")
    fun getAll(): Flow<List<SpecialPeriod>>

    /**
     * Get all special periods as a one-shot list (for backup).
     */
    @Query("SELECT * FROM special_periods ORDER BY startDate DESC")
    suspend fun getAllOnce(): List<SpecialPeriod>

    @Query("SELECT * FROM special_periods WHERE startDate <= :endDate AND endDate >= :startDate ORDER BY startDate ASC")
    fun getByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SpecialPeriod>>

    /**
     * Get periods that are active on a specific date.
     * A period is active if the date falls within [startDate, endDate] inclusive.
     */
    @Query("SELECT * FROM special_periods WHERE :date >= startDate AND :date <= endDate")
    fun getActivePeriods(date: LocalDate): Flow<List<SpecialPeriod>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(specialPeriod: SpecialPeriod)

    @Query("DELETE FROM special_periods WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM special_periods")
    suspend fun deleteAll()
}

