package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.NutritionPreset
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [NutritionPreset].
 */
@Dao
interface NutritionPresetDao {

    @Query("SELECT * FROM nutrition_presets ORDER BY label ASC")
    fun getAll(): Flow<List<NutritionPreset>>

    @Query("SELECT * FROM nutrition_presets ORDER BY label ASC")
    suspend fun getAllOnce(): List<NutritionPreset>

    @Query("SELECT COUNT(*) FROM nutrition_presets")
    suspend fun getCount(): Int

    /**
     * The preset with this label, ignoring case. Used to update a preset in place rather than
     * leaving two identical rows in a list ordered by label, where the athlete cannot tell them
     * apart or know which one they are deleting.
     */
    @Query("SELECT * FROM nutrition_presets WHERE label = :label COLLATE NOCASE LIMIT 1")
    suspend fun findByLabel(label: String): NutritionPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: NutritionPreset)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<NutritionPreset>)

    @Delete
    suspend fun delete(preset: NutritionPreset)

    @Query("DELETE FROM nutrition_presets")
    suspend fun deleteAll()
}
