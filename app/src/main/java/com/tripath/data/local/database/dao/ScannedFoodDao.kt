package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripath.data.local.database.entities.ScannedFoodCache

@Dao
interface ScannedFoodDao {

    @Query("SELECT * FROM scanned_foods WHERE barcode = :barcode")
    suspend fun getByBarcode(barcode: String): ScannedFoodCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: ScannedFoodCache)

    /** All cached foods as a one-shot list (for backup). */
    @Query("SELECT * FROM scanned_foods")
    suspend fun getAllOnce(): List<ScannedFoodCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<ScannedFoodCache>)

    /** Total cached foods, for the My Data browser. */
    @Query("SELECT COUNT(*) FROM scanned_foods")
    suspend fun getCount(): Int

    @Query("DELETE FROM scanned_foods")
    suspend fun deleteAll()
}
