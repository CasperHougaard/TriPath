package com.tripath.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tripath.data.local.database.entities.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UserProfile entity.
 * This is a single-row table containing user settings and goals.
 */
@Dao
interface UserProfileDao {

    /**
     * Get the user profile as a Flow for reactive updates.
     */
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfile?>

    /**
     * Get the user profile as a one-shot value (for backup).
     */
    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileOnce(): UserProfile?

    /**
     * Insert or replace the user profile (upsert).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    /**
     * Update the existing user profile.
     */
    @Update
    suspend fun update(profile: UserProfile)

    /**
     * Delete the user profile.
     */
    @Query("DELETE FROM user_profile")
    suspend fun delete()

    /**
     * Check if a profile exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM user_profile WHERE id = 1)")
    suspend fun exists(): Boolean
}

