package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tripath.data.local.database.entities.DayTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for DayTemplate entity.
 */
@Dao
interface DayTemplateDao {

    @Query("SELECT * FROM day_templates ORDER BY name ASC")
    fun getAll(): Flow<List<DayTemplate>>

    @Query("SELECT * FROM day_templates WHERE id = :id")
    suspend fun getById(id: String): DayTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: DayTemplate)

    @Update
    suspend fun update(template: DayTemplate)

    @Delete
    suspend fun delete(template: DayTemplate)
}

