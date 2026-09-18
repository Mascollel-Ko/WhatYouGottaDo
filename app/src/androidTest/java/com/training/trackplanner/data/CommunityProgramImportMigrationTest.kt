package com.training.trackplanner.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityProgramImportMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TrainingDatabase::class.java)

    @Test
    fun migrate33To34AddsProvenanceWithoutDroppingPrograms() {
        helper.createDatabase("community-program-import-migration", 33).use { database ->
            database.execSQL(
                "INSERT INTO training_programs (stableKey, name, durationDays, createdAt, goal, weeklyTrainingDays, sessionMinutes, availableEquipment, excludedExerciseText, badmintonTransferRatio, sportStrengthRatio, periodizationType, updatedAt) " +
                    "VALUES ('existing-program', 'Existing', 7, 1, '', 0, 0, '', '', 0.4, 'AUTO', '', 1)"
            )
        }
        helper.runMigrationsAndValidate(
            "community-program-import-migration",
            34,
            true,
            TrainingDatabase.MIGRATION_33_34
        ).use { database ->
            database.query("SELECT COUNT(*) FROM training_programs").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 1)
            }
            database.query("PRAGMA table_info(community_program_imports)").use { cursor ->
                check(cursor.moveToFirst())
            }
        }
    }
}
