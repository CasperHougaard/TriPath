package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 7 to version 8.
 * Adds hrZoneDistribution and powerZoneDistribution columns to workout_logs.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `workout_logs` ADD COLUMN `hrZoneDistribution` TEXT"
        )
        database.execSQL(
            "ALTER TABLE `workout_logs` ADD COLUMN `powerZoneDistribution` TEXT"
        )
    }
}

