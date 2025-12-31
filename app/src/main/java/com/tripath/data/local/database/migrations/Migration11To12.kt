package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 11 to version 12.
 * Adds sleepScore column to sleep_logs table for sleep quality scoring.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add sleepScore column to sleep_logs table
        database.execSQL(
            """
            ALTER TABLE `sleep_logs` 
            ADD COLUMN `sleepScore` INTEGER
            """.trimIndent()
        )
    }
}

