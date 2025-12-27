package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 10 to version 11.
 * Adds daily_wellness_logs and wellness_task_definitions tables for the Recovery Hub feature.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create daily_wellness_logs table
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_wellness_logs` (
                `date` INTEGER NOT NULL,
                `sleepMinutes` INTEGER,
                `hrvRmssd` REAL,
                `morningWeight` REAL,
                `sorenessIndex` INTEGER,
                `moodIndex` INTEGER,
                `allergySeverity` TEXT,
                `completedTaskIds` TEXT,
                PRIMARY KEY(`date`)
            )
            """.trimIndent()
        )

        // Create wellness_task_definitions table
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wellness_task_definitions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `type` TEXT NOT NULL,
                `triggerThreshold` INTEGER
            )
            """.trimIndent()
        )
    }
}
