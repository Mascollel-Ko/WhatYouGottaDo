package com.training.trackplanner.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgramProgressionMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TrainingDatabase::class.java)
    @Test fun migrate30To31PreservesExistingDataAndValidatesAllNewTables() {
        helper.createDatabase("progression-migration", 30).use {
            it.execSQL("INSERT INTO app_meta (`key`,value,updatedAt) VALUES ('progression-migration-sentinel','preserved',123)")
            it.execSQL("INSERT INTO workout_sets (id,entryId,setIndex,reps,weightKg,seconds,confirmed,manualWeight,rpe,restSecondsOverride) VALUES (987,123,1,5,140,0,1,1,7,NULL)")
        }
        helper.runMigrationsAndValidate("progression-migration", 31, true, ProgramProgressionMigration.MIGRATION_30_31).use {
            it.query("SELECT value FROM app_meta WHERE `key`='progression-migration-sentinel'").use { cursor -> check(cursor.moveToFirst()); check(cursor.getString(0) == "preserved") }
            it.query("SELECT weightKg,confirmed FROM workout_sets WHERE id=987").use { cursor -> check(cursor.moveToFirst()); check(cursor.getDouble(0) == 140.0); check(cursor.getInt(1) == 1) }
            it.query("SELECT count(*) FROM program_workout_links").use { cursor -> check(cursor.moveToFirst()); check(cursor.getInt(0) == 0) }
        }
    }
}
