package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 13 to version 14.
 * Adds plannedDistanceMeters to training_plans for workouts with a planned run distance.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            ALTER TABLE `training_plans`
            ADD COLUMN `plannedDistanceMeters` INTEGER
            """.trimIndent()
        )
    }
}