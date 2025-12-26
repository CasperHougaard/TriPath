package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 3 to version 4.
 * Adds the special_periods table to track injury, holiday, and recovery periods.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create special_periods table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `special_periods` (
                `id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `startDate` INTEGER NOT NULL,
                `endDate` INTEGER NOT NULL,
                `notes` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

