package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 5 to version 6.
 * Adds the day_notes table for day-specific notes.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `day_notes` (`date` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`date`))"
        )
    }
}

