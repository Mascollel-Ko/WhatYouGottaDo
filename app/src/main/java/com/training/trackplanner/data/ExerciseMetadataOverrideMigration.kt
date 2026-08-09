package com.training.trackplanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercise_metadata_user_overrides` (
                `stableKey` TEXT NOT NULL,
                `fieldScope` TEXT NOT NULL,
                `fieldKey` TEXT NOT NULL,
                `valueEncoding` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                `isExplicitEmpty` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `semanticCanonicalRevisionAtEdit` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`stableKey`, `fieldScope`, `fieldKey`),
                FOREIGN KEY(`stableKey`) REFERENCES `exercises`(`stableKey`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercise_metadata_user_overrides_stableKey` " +
                "ON `exercise_metadata_user_overrides` (`stableKey`)"
        )
    }
}
