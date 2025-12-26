package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 4 to version 5.
 * Removes the user_profile table as it has been moved to DataStore Preferences.
 * 
 * Note: This migration drops the user_profile table. If there is existing data,
 * it should be migrated to DataStore before this migration runs. However, since
 * this is a structural change (moving from Room to DataStore), the data migration
 * should be handled at the application level, not in the database migration.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop user_profile table - data should have been migrated to DataStore
        // before this migration runs
        database.execSQL("DROP TABLE IF EXISTS `user_profile`")
    }
}

