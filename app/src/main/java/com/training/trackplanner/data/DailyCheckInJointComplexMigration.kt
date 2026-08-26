package com.training.trackplanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `daily_check_ins` ADD COLUMN " +
                "`jointTendonDiscomfortJointComplexKey` TEXT"
        )
    }
}
