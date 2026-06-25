package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.BodyCompositionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyCompositionDao {

    @Query("SELECT * FROM body_composition_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<BodyCompositionLog>>

    @Query("SELECT * FROM body_composition_logs WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    suspend fun getLogsInRange(from: Long, to: Long): List<BodyCompositionLog>

    @Query("SELECT id FROM body_composition_logs WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<BodyCompositionLog>)

    @Query("SELECT * FROM body_composition_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): BodyCompositionLog?
}
