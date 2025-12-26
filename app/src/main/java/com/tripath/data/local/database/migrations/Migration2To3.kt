package com.tripath.data.local.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 2 to version 3.
 * No schema changes - this is a no-op migration for completeness.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No schema changes between version 2 and 3
        // This migration exists for completeness and future-proofing
    }
}

