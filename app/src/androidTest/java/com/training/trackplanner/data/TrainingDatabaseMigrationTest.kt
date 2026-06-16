package com.training.trackplanner.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrainingDatabase::class.java
    )

    @Ignore("Requires exported v8 schema asset. v0.3.4.4 starts schema export for future migration tests.")
    @Test
    fun migrate8To9AddsInitialProfileWithoutDroppingCoreTables() {
        helper.createDatabase(TEST_DB, 8).use { database ->
            database.execSQL(
                """
                INSERT INTO exercises (id, name, category, detail1, detail2, mode, description, defaultRestSeconds)
                VALUES (1, '테스트 운동', '근력운동', '', '', '', '', 60)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, TrainingDatabase.MIGRATION_8_9).use { database ->
            database.query("SELECT COUNT(*) FROM exercises").use { cursor ->
                cursor.moveToFirst()
                check(cursor.getInt(0) == 1)
            }
            database.query("SELECT COUNT(*) FROM initial_user_profiles").use { cursor ->
                cursor.moveToFirst()
                check(cursor.getInt(0) == 0)
            }
        }
    }

    private companion object {
        const val TEST_DB = "training-migration-test"
    }
}
