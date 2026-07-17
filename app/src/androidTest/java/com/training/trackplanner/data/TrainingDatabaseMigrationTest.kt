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

    @Test
    fun migrate11To12InvertsBadRecoveryScalesOnly() {
        helper.createDatabase(TEST_DB, 11).use { database ->
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
                    5.0, 80, 9.0,
                    '', '', 6.0, 2.0,
                    0, NULL, 0,
                    'NONE', 'NONE',
                    '', '', '', '',
                    120.0, 150.0, 90.0, 12, 10.0,
                    7.5, 7.5, 4, 5, 4,
                    1, 3, 4,
                    '', 'NONE', '', 'NONE',
                    '', 'MIXED', '', '', 1, 2
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 12, true, TrainingDatabase.MIGRATION_11_12).use { database ->
            database.query(
                """
                SELECT sleepQuality, currentFatigue, currentSoreness, currentStress, currentMood, currentCondition
                FROM initial_user_profiles WHERE id = 1
                """.trimIndent()
            ).use { cursor ->
                cursor.moveToFirst()
                check(cursor.getInt(0) == 4)
                check(cursor.getInt(1) == 1)
                check(cursor.getInt(2) == 2)
                check(cursor.getInt(3) == 5)
                check(cursor.getInt(4) == 3)
                check(cursor.getInt(5) == 4)
            }
        }
    }

    @Test
    fun migrate12To13AddsLosslessRuntimeMetadataTable() {
        helper.createDatabase(TEST_DB, 12).close()

        helper.runMigrationsAndValidate(TEST_DB, 13, true, TrainingDatabase.MIGRATION_12_13).use { database ->
            val actualColumns = buildSet {
                database.query("PRAGMA table_info(runtime_exercise_metadata)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assert(actualColumns == RUNTIME_METADATA_COLUMNS) {
                "Unexpected runtime metadata columns: $actualColumns"
            }
        }
    }

    @Test
    fun migrate13To14AddsPersistedRecordDisplayOrder() {
        helper.createDatabase(TEST_DB, 13).close()

        helper.runMigrationsAndValidate(TEST_DB, 14, true, TrainingDatabase.MIGRATION_13_14).use { database ->
            val columns = buildSet {
                database.query("PRAGMA table_info(workout_entries)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            check("displayOrder" in columns)
            check("firstConfirmedAt" in columns)
        }
    }

    @Test
    fun migrate16To17AddsSmashSpeedRecords() {
        helper.createDatabase(TEST_DB, 16).close()

        helper.runMigrationsAndValidate(TEST_DB, 17, true, TrainingDatabase.MIGRATION_16_17).use { database ->
            val columns = buildSet {
                database.query("PRAGMA table_info(smash_speed_records)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            check("date" in columns)
            check("speedKmh" in columns)
            check("attemptIndex" in columns)
        }
    }

    @Test
    fun migrate17To18AddsNullableProgramRestoreMetadata() {
        helper.createDatabase(TEST_DB, 17).use { database ->
            database.execSQL(
                """
                INSERT INTO training_program_items (
                    programId, weekNumber, dayOfWeek, orderIndex, exerciseId, exerciseName,
                    category, restSeconds, prescription, setCount, reps, weightKg, seconds
                ) VALUES (1, 1, 1, 1, 1, 'Legacy', 'Strength', 60, 'legacy', 3, 8, 40.0, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 18, true, TrainingDatabase.MIGRATION_17_18).use { database ->
            database.query(
                "SELECT trainingSlot, dayIntensity, weightSource FROM training_program_items"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.isNull(0))
                check(cursor.isNull(1))
                check(cursor.isNull(2))
            }
        }
    }

    @Test
    fun migrate18To19KeepsLegacyPerformedTimeUnknown() {
        helper.createDatabase(TEST_DB, 18).use { database ->
            database.execSQL(
                """
                INSERT INTO workout_entries (
                    id, date, exerciseId, exerciseName, category, restSeconds, notes, rpe, maxReps,
                    createdAt, completedAt, displayOrder, firstConfirmedAt
                ) VALUES (
                    1, '2026-07-13', 1, 'Legacy', 'Strength', 60, '', NULL, NULL,
                    1000, 2000, 1, 2000
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 19, true, TrainingDatabase.MIGRATION_18_19).use { database ->
            database.query("SELECT performedAt FROM workout_entries WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.isNull(0))
            }
        }
    }

    @Test
    fun migrate19To20MergesDailyConditionByTimestampAndPreservesBodyWeight() {
        helper.createDatabase(TEST_DB, 19).use { database ->
            database.execSQL(
                """
                INSERT INTO daily_check_ins (
                    date, sleepHours, overallFatigue, lowerBodyFatigue,
                    jointTendonDiscomfort, focusMotivation, note, createdAt, updatedAt
                ) VALUES
                    ('2026-07-15', 5.0, 4, NULL, NULL, NULL, 'older check-in', 100, 100),
                    ('2026-07-16', 8.0, 2, NULL, NULL, NULL, 'newer check-in', 100, 300)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO daily_metrics (date, sleepHours, bodyWeightKg, updatedAt) VALUES
                    ('2026-07-15', 7.5, 80.5, 200),
                    ('2026-07-16', 6.0, 79.5, 200),
                    ('2026-07-17', 7.0, 79.0, 400)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 20, true, TrainingDatabase.MIGRATION_19_20).use { database ->
            database.query(
                "SELECT date, sleepHours, bodyWeightKg, overallFatigue FROM daily_check_ins ORDER BY date"
            ).use { cursor ->
                check(cursor.moveToNext())
                check(cursor.getString(0) == "2026-07-15")
                check(cursor.getDouble(1) == 7.5)
                check(cursor.getDouble(2) == 80.5)
                check(cursor.getInt(3) == 4)

                check(cursor.moveToNext())
                check(cursor.getString(0) == "2026-07-16")
                check(cursor.getDouble(1) == 8.0)
                check(cursor.getDouble(2) == 79.5)
                check(cursor.getInt(3) == 2)

                check(cursor.moveToNext())
                check(cursor.getString(0) == "2026-07-17")
                check(cursor.getDouble(1) == 7.0)
                check(cursor.getDouble(2) == 79.0)
                check(cursor.isNull(3))
                check(!cursor.moveToNext())
            }
            database.query(
                "SELECT sleepHours, bodyWeightKg FROM daily_metrics WHERE date = '2026-07-16'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getDouble(0) == 8.0)
                check(cursor.getDouble(1) == 79.5)
            }
        }
    }

    @Test
    fun migrate20To21AddsNeutralHabitualIntensityAndPreservesProfile() {
        helper.createDatabase(TEST_DB, 20).use { database ->
            database.execSQL(
                """
                INSERT INTO initial_user_profiles (
                    id, bodyWeightKg, birthYearOrAgeRange, gender, sex,
                    strengthTrainingAge, badmintonTrainingAge, strengthTrainingYears, badmintonTrainingYears,
                    hadRecentTrainingBreak, breakDueToPain, trainingBreakCategory, trainingBreakReason,
                    squatLevel, deadliftLevel, benchPressLevel, pullUpLevel,
                    painAreas, painAreaTags, avoidedMovements, avoidMovementTags,
                    goals, primaryGoal, secondaryGoalTags, freeNote, createdAt, updatedAt
                ) VALUES (
                    1, 72.5, '', '', 'UNSPECIFIED',
                    '', '', 6.0, 2.0,
                    0, 0, 'NONE', 'NONE',
                    '', '', '', '',
                    '', 'NONE', '', 'NONE',
                    '', 'MIXED', '', '', 1, 2
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 21, true, TrainingDatabase.MIGRATION_20_21).use { database ->
            database.query(
                """
                SELECT bodyWeightKg, strengthTrainingYears, badmintonTrainingYears, habitualTrainingIntensity
                FROM initial_user_profiles WHERE id = 1
                """.trimIndent()
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getDouble(0) == 72.5)
                check(cursor.getDouble(1) == 6.0)
                check(cursor.getDouble(2) == 2.0)
                check(cursor.isNull(3))
            }
        }
    }

    private companion object {
        const val TEST_DB = "training-migration-test"

        val RUNTIME_METADATA_COLUMNS = setOf(
            "stableKey",
            "exerciseName",
            "activityKind",
            "planningEligibility",
            "movementFamily",
            "movementSubtype",
            "programSlot",
            "redundancyGroup",
            "progressMetricType",
            "strengthProgressionGroup",
            "analysisEligibility",
            "primaryStressProfile",
            "secondaryStressTags",
            "tendonStressTags",
            "ligamentJointStabilityStressTags",
            "jointImpactStressTags",
            "cognitiveStressTags",
            "sportContextTags",
            "recoveryDecayProfile",
            "stressMagnitudeHint",
            "badmintonTransferLevel",
            "badmintonTransferType",
            "badmintonSkillTargets",
            "badmintonPhysicalQualities",
            "transferConfidence",
            "sourceConfidenceLevel",
            "finalSourceStatus",
            "neuromuscularStressLevel",
            "systemicMuscularStressLevel",
            "localMuscularStressLevel",
            "jointTendonImpactStressLevel",
            "movementFocusDemandLevel",
            "recoveryDurationClass",
            "safeForSeedMutation"
        )
    }
}
