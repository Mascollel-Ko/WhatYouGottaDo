package com.training.trackplanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** Only legacy rows are grouped by date. Persisted identity never depends on a date. */
internal val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workout_entries` ADD COLUMN `sessionStableKey` TEXT NOT NULL DEFAULT ''")
        val dates = mutableListOf<String>()
        db.query("SELECT DISTINCT `date` FROM `workout_entries` ORDER BY `date`").use { cursor ->
            while (cursor.moveToNext()) dates += cursor.getString(0)
        }
        dates.forEach { date ->
            db.execSQL(
                "UPDATE `workout_entries` SET `sessionStableKey` = ? WHERE `date` = ?",
                arrayOf(UUID.randomUUID().toString(), date)
            )
        }
    }
}
