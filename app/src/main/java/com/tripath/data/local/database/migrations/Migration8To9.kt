package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 8 to version 9.
 * Adds the raw_workout_data table for storing raw Health Connect samples.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `raw_workout_data` (
                `connectId` TEXT NOT NULL, 
                `rawExerciseType` INTEGER NOT NULL, 
                `startTimeMillis` INTEGER NOT NULL, 
                `endTimeMillis` INTEGER NOT NULL, 
                `hrSamplesJson` TEXT, 
                `powerSamplesJson` TEXT, 
                `rawCalories` INTEGER, 
                `rawDistanceMeters` REAL, 
                `rawSteps` INTEGER, 
                `importedAt` INTEGER NOT NULL, 
                PRIMARY KEY(`connectId`)
            )
            """.trimIndent()
        )
    }
}

