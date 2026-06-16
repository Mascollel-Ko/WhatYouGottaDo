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

    @Test
    fun migrate9To10AddsStructuredInitialProfileFields() {
        helper.createDatabase(TEST_DB, 9).use { database ->
            database.execSQL(
                """
                INSERT INTO initial_user_profiles (
                    id, bodyWeightKg, heightCm, birthYearOrAgeRange, gender,
                    strengthTrainingAge, badmintonTrainingAge, hadRecentTrainingBreak, breakWeeks, breakDueToPain,
                    squatLevel, deadliftLevel, benchPressLevel, pullUpLevel,
                    typicalSleepHours, currentMood, painAreas, avoidedMovements, goals, createdAt, updatedAt
                ) VALUES (
                    1, 72.0, 175.0, '1990', '남성',
                    '6년', '2년', 1, 9, 1,
                    '', '', '', '',
                    7.0, 3, '어깨', '', '배드민턴', 1, 1
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 10, true, TrainingDatabase.MIGRATION_9_10).use { database ->
            database.query(
                """
                SELECT birthYear, sex, trainingBreakCategory, trainingBreakReason,
                       usualSleepHours, currentCondition
                FROM initial_user_profiles WHERE id = 1
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                check(cursor.getInt(0) == 1990)
                check(cursor.getString(1) == "MALE")
                check(cursor.getString(2) == "MORE_THAN_EIGHT_WEEKS")
                check(cursor.getString(3) == "PAIN_OR_INJURY")
                check(cursor.getDouble(4) == 7.0)
                check(cursor.getInt(5) == 3)
            }
        }
    }

    @Test
    fun migrate10To11KeepsStructuredInitialProfileFields() {
        helper.createDatabase(TEST_DB, 10).use { database ->
            database.execSQL(
                """
                INSERT INTO initial_user_profiles (
                    id, bodyWeightKg, heightCm, birthYearOrAgeRange, gender, birthYear, sex,
                    strengthSessionsPerWeek, strengthMinutesPerSession, strengthAverageRpe,
                    badmintonSessionsPerWeek, badmintonMinutesPerSession, badmintonAverageRpe,
                    strengthTrainingAge, badmintonTrainingAge, strengthTrainingYears, badmintonTrainingYears,
                    hadRecentTrainingBreak, breakWeeks, breakDueToPain,
                    trainingBreakCategory, trainingBreakReason,
                    squatLevel, deadliftLevel, benchPressLevel, pullUpLevel,
                    squatKg, deadliftKg, benchPressKg, pullUpMaxReps, pullUpAddedWeightKg,
                    typicalSleepHours, usualSleepHours, sleepQuality, currentFatigue, currentSoreness,
                    currentStress, currentMood, currentCondition,
                    painAreas, painAreaTags, avoidedMovements, avoidMovementTags,
                    goals, primaryGoal, secondaryGoalTags, freeNote, createdAt, updatedAt
                ) VALUES (
                    1, 72.0, 175.0, '', 'MALE', 1990, 'MALE',
                    4.0, 70, 7.0,
                    5.0, 80, 7.0,
                    '', '', 6.0, 2.0,
                    0, NULL, 0,
                    'NONE', 'NONE',
                    '', '', '', '',
                    120.0, 150.0, 90.0, 12, 10.0,
                    7.5, 7.5, 4, 2, 2,
                    2, 4, 4,
                    '', 'SHOULDER', '', 'BENCH_OR_PUSH',
                    '', 'BADMINTON_PERFORMANCE', '', '', 1, 2
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 11, true, TrainingDatabase.MIGRATION_10_11).use { database ->
            database.query(
                """
                SELECT birthYear, sex, strengthTrainingYears, badmintonTrainingYears,
                       painAreaTags, avoidMovementTags, primaryGoal
                FROM initial_user_profiles WHERE id = 1
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                check(cursor.getInt(0) == 1990)
                check(cursor.getString(1) == "MALE")
                check(cursor.getDouble(2) == 6.0)
                check(cursor.getDouble(3) == 2.0)
                check(cursor.getString(4) == "SHOULDER")
                check(cursor.getString(5) == "BENCH_OR_PUSH")
                check(cursor.getString(6) == "BADMINTON_PERFORMANCE")
            }
        }
    }

    private companion object {
        const val TEST_DB = "training-migration-test"
    }
}
