package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 9 to version 10.
 * Adds sleep_logs table and routeJson column to raw_workout_data.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create sleep_logs table
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sleep_logs` (
                `connectId` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `startTimeMillis` INTEGER NOT NULL,
                `endTimeMillis` INTEGER NOT NULL,
                `durationMinutes` INTEGER NOT NULL,
                `title` TEXT,
                `stagesJson` TEXT,
                `deepSleepMinutes` INTEGER,
                `lightSleepMinutes` INTEGER,
                `remSleepMinutes` INTEGER,
                `awakeMinutes` INTEGER,
                `importedAt` INTEGER NOT NULL,
                PRIMARY KEY(`connectId`)
            )
            """.trimIndent()
        )
        
        // Add routeJson column to raw_workout_data
        database.execSQL(
            "ALTER TABLE `raw_workout_data` ADD COLUMN `routeJson` TEXT"
        )
    }
}

