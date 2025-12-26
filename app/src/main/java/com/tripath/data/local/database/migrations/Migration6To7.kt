package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 6 to version 7.
 * Adds the day_templates table for reusable day setups.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `day_templates` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `activitiesJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

