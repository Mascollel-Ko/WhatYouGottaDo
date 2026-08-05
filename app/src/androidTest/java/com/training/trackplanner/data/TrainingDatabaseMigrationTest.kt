package com.training.trackplanner.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
                    programId, weekNumber, dayOfWeek, orderIndex, exerciseStableKey, exerciseName,
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
                    id, date, exerciseStableKey, exerciseName, category, restSeconds, notes, rpe, maxReps,
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

    @Test
    fun migrate21To22AddsPosteriorLedgerWithoutChangingWorkoutOrBootstrapping() {
        helper.createDatabase(TEST_DB, 21).use { database ->
            database.execSQL(
                """
                INSERT INTO workout_entries (
                    id, date, exerciseStableKey, exerciseName, category, restSeconds, notes, rpe, maxReps,
                    createdAt, completedAt, displayOrder, firstConfirmedAt, performedAt
                ) VALUES (
                    7, '2026-07-20', 9, 'Migration fixture', 'Strength', 90, 'keep me', 9.0, 5,
                    1000, 2000, 3, 2000, 1500
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO workout_sets (
                    id, entryId, setIndex, reps, weightKg, seconds, confirmed, manualWeight, rpe, restSecondsOverride
                ) VALUES (11, 7, 1, 5, 80.0, 0, 1, 1, 9.0, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 22, true, TrainingDatabase.MIGRATION_21_22).use { database ->
            val expectedTables = setOf(
                "strength_posterior_events",
                "strength_posterior_history",
                "strength_posterior_model_state",
                "strength_curve_posteriors",
                "strength_posterior_evidence"
            )
            val actualTables = buildSet {
                database.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            check(actualTables.containsAll(expectedTables))

            val expectedIndexes = setOf(
                "index_strength_posterior_events_sessionKey",
                "index_strength_posterior_events_sessionDate",
                "index_strength_posterior_events_status",
                "index_strength_posterior_events_completionFingerprint",
                "index_strength_posterior_history_targetKey",
                "index_strength_posterior_history_eventUuid",
                "index_strength_posterior_evidence_eventUuid",
                "index_strength_posterior_evidence_exerciseStableKey"
            )
            val actualIndexes = buildSet {
                database.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            check(actualIndexes.containsAll(expectedIndexes))

            database.query(
                "SELECT exerciseName, notes, completedAt, performedAt FROM workout_entries WHERE id = 7"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getString(0) == "Migration fixture")
                check(cursor.getString(1) == "keep me")
                check(cursor.getLong(2) == 2000L)
                check(cursor.getLong(3) == 1500L)
            }
            database.query("SELECT reps, weightKg, confirmed FROM workout_sets WHERE id = 11").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 5)
                check(cursor.getDouble(1) == 80.0)
                check(cursor.getInt(2) == 1)
            }
            database.query(
                "SELECT COUNT(*) FROM app_meta WHERE `key` = '${StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY}'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 0)
            }
        }
    }

    @Test
    fun migrate22To23PreservesPosteriorRowsAndAddsRevisionTablesWithoutRunningRebuild() {
        helper.createDatabase(TEST_DB_22_23, 22).use { database ->
            database.execSQL(
                """
                INSERT INTO strength_posterior_events (
                    eventUuid, sessionKey, sessionDate, completionFingerprint, status, creationReason,
                    confirmedSetCount, createdAt, processedAt, modelVersion, curveVersion,
                    factorSchemaVersion, evidenceFingerprint, errorCode, errorMessage
                ) VALUES (
                    'legacy-event', 'date:2026-07-20', '2026-07-20', 'legacy-fingerprint',
                    'PROCESSED', 'INITIAL_INSTALLATION_BOOTSTRAP', 1, 1000, 1100,
                    'strength-performance-model-2.1.0', 'repetition-curve-assets-1.0.0',
                    'strength-factor-schema-2.0.0', 'legacy-evidence', NULL, NULL
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB_22_23,
            23,
            true,
            TrainingDatabase.MIGRATION_22_23
        ).use { database ->
            database.query(
                "SELECT eventUuid, completionFingerprint, modelVersion, revisionKey FROM strength_posterior_events"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getString(0) == "legacy-event")
                check(cursor.getString(1) == "legacy-fingerprint")
                check(cursor.getString(2) == "strength-performance-model-2.1.0")
                check(cursor.getString(3) == StrengthModelRevisionPolicy.LEGACY_REVISION_KEY)
            }
            val expectedTables = setOf(
                "strength_model_revisions",
                "strength_exercise_performance_state",
                "strength_exercise_performance_history",
                "strength_proxy_transfer_history"
            )
            val tables = buildSet {
                database.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            check(tables.containsAll(expectedTables))
            expectedTables.forEach { table ->
                database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    check(cursor.moveToFirst())
                    check(cursor.getInt(0) == 0)
                }
            }
            database.query(
                "SELECT COUNT(*) FROM app_meta WHERE `key` = '${StrengthModelRevisionPolicy.REBUILD_MARKER_KEY}'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 0)
            }
        }
    }

    @Test
    fun migrate23To24PreservesProgramsAndBackfillsUniqueStableKeys() {
        helper.createDatabase(TEST_DB_23_24, 23).use { database ->
            database.execSQL(
                """
                INSERT INTO training_programs (
                    id, name, durationDays, createdAt, goal, weeklyTrainingDays,
                    sessionMinutes, availableEquipment, excludedExerciseText,
                    badmintonTransferRatio, sportStrengthRatio, periodizationType, updatedAt
                ) VALUES
                    (1, 'Untouched seed', 28, 100, '', 4, 45, '바벨', '', 0.4, 'AUTO', '', 110),
                    (2, 'Renamed program', 28, 200, '', 3, 30, '', '', 0.4, 'AUTO', '', 210),
                    (3, 'User program', 14, 300, '', 2, 60, '', '', 0.4, 'AUTO', '', 310)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO training_program_items (
                    id, programId, weekNumber, dayOfWeek, orderIndex, exerciseStableKey,
                    exerciseName, category, restSeconds, prescription, setCount,
                    reps, weightKg, seconds, trainingSlot, dayIntensity, weightSource
                ) VALUES
                    (10, 1, 1, 1, 1, 0, 'Seed item', 'Strength', 90, '3x5', 3, 5, 80.0, 0, NULL, NULL, NULL),
                    (20, 2, 1, 2, 1, 0, 'Modified item', 'Strength', 60, '', 2, 8, 20.0, 0, NULL, NULL, NULL),
                    (30, 3, 1, 3, 1, 0, 'User item', 'Strength', 60, '', 1, 10, 10.0, 0, NULL, NULL, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB_23_24,
            24,
            true,
            TrainingDatabase.MIGRATION_23_24
        ).use { database ->
            database.query("SELECT id, stableKey FROM training_programs ORDER BY id").use { cursor ->
                var expectedId = 1
                while (cursor.moveToNext()) {
                    check(cursor.getLong(0) == expectedId.toLong())
                    check(cursor.getString(1) == "${ProgramStableKeyPolicy.LEGACY_PREFIX}$expectedId")
                    expectedId += 1
                }
                check(expectedId == 4)
            }
            database.query("SELECT COUNT(*) FROM training_program_items").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 3)
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'training_program_tombstones'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 1)
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_training_programs_stableKey'"
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 1)
            }
        }
    }

    @Test
    fun migrate24To25UsesCanonicalKeysAndPreservesUnresolvedHistoryForReview() {
        helper.createDatabase(TEST_DB_24_25, 24).use { database ->
            insertExercise24(database, 1, "ex_201f6426", "B-스탠스 RDL", "BARBELL", false)
            insertExercise24(database, 2, "ex_d2bb7946", "루마니안 데드리프트", "", false)
            insertExercise24(database, 3, "user_exercise_test", "내 운동", "DUMBBELL", true)
            insertRuntimeMetadata24(database, "ex_201f6426", "B-스탠스 RDL")
            database.execSQL(
                """
                INSERT INTO workout_entries (
                    id, date, exerciseId, exerciseName, category, restSeconds, notes, rpe,
                    maxReps, createdAt, completedAt, displayOrder, firstConfirmedAt, performedAt
                ) VALUES
                    (10, '2026-07-01', 1, 'B-스탠스 RDL', '근력운동', 90, '', 8.0, NULL, 1, 1, 0, 1, 1),
                    (11, '2026-07-02', 0, 'ID zero', '근력운동', 60, '', NULL, NULL, 1, NULL, 0, NULL, NULL),
                    (12, '2026-07-03', 999, 'Dangling', '근력운동', 60, '', NULL, NULL, 1, NULL, 0, NULL, NULL),
                    (13, '2026-07-04', 2, 'Generic RDL', '근력운동', 90, '', 8.0, NULL, 1, 1, 0, 1, 1),
                    (14, '2026-07-05', 3, '내 운동', '근력운동', 60, '', 7.0, NULL, 1, 1, 0, 1, 1)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO training_programs (
                    id, stableKey, name, durationDays, createdAt, goal, weeklyTrainingDays,
                    sessionMinutes, availableEquipment, excludedExerciseText, badmintonTransferRatio,
                    sportStrengthRatio, periodizationType, updatedAt
                ) VALUES (1, 'program', 'Program', 7, 1, '', 1, 45, '', '', 0.4, 'AUTO', '', 1)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO training_program_items (
                    id, programId, weekNumber, dayOfWeek, orderIndex, exerciseId, exerciseName,
                    category, restSeconds, prescription, setCount, reps, weightKg, seconds,
                    trainingSlot, dayIntensity, weightSource
                ) VALUES
                    (20, 1, 1, 1, 0, 1, 'B-스탠스 RDL', '근력운동', 90, '', 3, 8, 20.0, 0, NULL, NULL, NULL),
                    (21, 1, 1, 1, 1, 2, 'Generic RDL', '근력운동', 90, '', 3, 8, 20.0, 0, NULL, NULL, NULL),
                    (22, 1, 1, 1, 2, 0, 'ID zero', '근력운동', 60, '', 1, 0, 0.0, 0, NULL, NULL, NULL)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB_24_25, 25, true, MIGRATION_24_25).use { database ->
            check(database.count("workout_entries") == 5)
            check(database.count("training_program_items") == 3)
            check(database.foreignKeyTarget("workout_entries", "exerciseStableKey") == "exercises")
            check(database.foreignKeyTarget("training_program_items", "exerciseStableKey") == "exercises")
            check(database.singleString("SELECT exerciseStableKey FROM workout_entries WHERE id = 10") == "single_leg_rdl")
            check(database.singleString("SELECT exerciseStableKey FROM workout_entries WHERE id = 13") == "migration_review_2")
            check(database.singleString("SELECT exerciseStableKey FROM workout_entries WHERE id = 14") == "user_exercise_test")
            check(
                database.singleString("SELECT exerciseStableKey FROM workout_entries WHERE id = 11") ==
                    ExerciseMigrationKeyPolicy.UNRESOLVED_REFERENCE_KEY
            )
            check(
                database.singleString("SELECT stableKey FROM runtime_exercise_metadata") ==
                    "single_leg_rdl"
            )
            check(database.count("exercise_identity_migration_issues") >= 3)
            check(database.singleString("SELECT name FROM exercises WHERE stableKey = 'user_exercise_test'") == "내 운동")
            database.query(
                """
                SELECT metadataConfidence, isActive, isCustom, needsReview
                FROM exercises
                WHERE stableKey = '${ExerciseMigrationKeyPolicy.UNRESOLVED_REFERENCE_KEY}'
                """.trimIndent()
            ).use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getString(0) == "UNKNOWN")
                check(cursor.getInt(1) == 0)
                check(cursor.getInt(2) == 1)
                check(cursor.getInt(3) == 1)
            }
        }
    }

    @Test
    fun migrate25To26AddsProgramItemSetTableWithoutChangingLegacyItems() {
        helper.createDatabase(TEST_DB_25_26, 25).use { database ->
            insertRowWithDefaults(
                database,
                "training_programs",
                mapOf(
                    "id" to 1,
                    "stableKey" to "user_program_test",
                    "name" to "Legacy",
                    "durationDays" to 7
                )
            )
            insertRowWithDefaults(
                database,
                "exercises",
                mapOf(
                    "stableKey" to "squat",
                    "name" to "스쿼트",
                    "category" to "근력운동"
                )
            )
            insertRowWithDefaults(
                database,
                "training_program_items",
                mapOf(
                    "id" to 10,
                    "programId" to 1,
                    "weekNumber" to 1,
                    "dayOfWeek" to 1,
                    "orderIndex" to 1,
                    "exerciseStableKey" to "squat",
                    "exerciseName" to "스쿼트",
                    "category" to "근력운동",
                    "setCount" to 3,
                    "reps" to 5,
                    "weightKg" to 100.0
                )
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB_25_26,
            26,
            true,
            TrainingDatabase.MIGRATION_25_26
        ).use { database ->
            check(database.count("training_program_items") == 1)
            check(database.count("training_program_item_sets") == 0)
        }
    }

    @Test
    fun migrate26To27SplitsRolesAndPreservesUserRecords() {
        helper.createDatabase(TEST_DB_26_27, 26).use { database ->
            insertRowWithDefaults(
                database,
                "exercises",
                mapOf(
                    "stableKey" to "single_leg_rdl",
                    "name" to "Single leg RDL",
                    "category" to "Strength",
                    "trainingRole" to "MAIN_STRENGTH"
                )
            )
            insertRowWithDefaults(
                database,
                "workout_entries",
                mapOf(
                    "id" to 11,
                    "date" to "2026-08-01",
                    "exerciseStableKey" to "single_leg_rdl",
                    "exerciseName" to "Single leg RDL",
                    "category" to "Strength"
                )
            )
            insertRowWithDefaults(database, "workout_sets", mapOf("id" to 12, "entryId" to 11, "setIndex" to 1))
            insertRowWithDefaults(
                database,
                "training_programs",
                mapOf("id" to 21, "stableKey" to "migration_program", "name" to "Migration", "durationDays" to 7)
            )
            insertRowWithDefaults(
                database,
                "training_program_items",
                mapOf(
                    "id" to 22,
                    "programId" to 21,
                    "weekNumber" to 1,
                    "dayOfWeek" to 1,
                    "orderIndex" to 1,
                    "exerciseStableKey" to "single_leg_rdl",
                    "exerciseName" to "Single leg RDL",
                    "category" to "Strength"
                )
            )
            insertRowWithDefaults(
                database,
                "training_program_item_sets",
                mapOf("id" to 23, "programItemId" to 22, "setIndex" to 1)
            )
        }

        helper.runMigrationsAndValidate(TEST_DB_26_27, 27, true, MIGRATION_26_27).use { database ->
            check(database.singleString(
                "SELECT trainingRoleCode FROM exercise_training_role_relations WHERE exerciseStableKey='single_leg_rdl'"
            ) == "STRENGTH")
            check(database.singleString(
                "SELECT capabilityCode FROM exercise_program_slot_capability_relations WHERE exerciseStableKey='single_leg_rdl'"
            ) == "MAIN_STRENGTH_SLOT")
            check(database.count("workout_entries") == 1)
            check(database.count("workout_sets") == 1)
            check(database.count("training_program_items") == 1)
            check(database.count("training_program_item_sets") == 1)
            check(database.foreignKeyTarget("workout_entries", "exerciseStableKey") == "exercises")
            check(database.foreignKeyTarget("training_program_items", "exerciseStableKey") == "exercises")
            check(database.foreignKeyTarget("training_program_item_sets", "programItemId") == "training_program_items")
            database.query("PRAGMA table_info(`exercises`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
                check("trainingRole" !in columns)
            }
        }
    }

    private fun SupportSQLiteDatabase.foreignKeyTarget(table: String, column: String): String? =
        query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            val fromIndex = cursor.getColumnIndexOrThrow("from")
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .firstOrNull { it.getString(fromIndex) == column }
                ?.getString(tableIndex)
        }

    private fun insertExercise24(
        database: SupportSQLiteDatabase,
        id: Long,
        stableKey: String,
        name: String,
        equipment: String,
        custom: Boolean
    ) {
        insertRowWithDefaults(
            database = database,
            table = "exercises",
            values = mapOf(
                "id" to id,
                "stableKey" to stableKey,
                "name" to name,
                "category" to "근력운동",
                "equipment" to equipment,
                "equipmentTags" to equipment,
                "isActive" to 1,
                "isCustom" to if (custom) 1 else 0
            )
        )
    }

    private fun insertRuntimeMetadata24(
        database: SupportSQLiteDatabase,
        stableKey: String,
        name: String
    ) {
        insertRowWithDefaults(
            database = database,
            table = "runtime_exercise_metadata",
            values = mapOf(
                "stableKey" to stableKey,
                "exerciseName" to name
            )
        )
    }

    private fun insertRowWithDefaults(
        database: SupportSQLiteDatabase,
        table: String,
        values: Map<String, Any>
    ) {
        val columns = mutableListOf<Pair<String, String>>()
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex) to cursor.getString(typeIndex)
        }
        val arguments = columns.map { (column, type) ->
            values[column] ?: when (type.uppercase()) {
                "INTEGER" -> 0
                "REAL" -> 0.0
                else -> ""
            }
        }.toTypedArray()
        database.execSQL(
            "INSERT INTO `$table` (${columns.joinToString { "`${it.first}`" }}) " +
                "VALUES (${columns.joinToString { "?" }})",
            arguments
        )
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.singleString(sql: String): String =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private companion object {
        const val TEST_DB = "training-migration-test"
        const val TEST_DB_22_23 = "training-migration-22-23-test"
        const val TEST_DB_23_24 = "training-migration-23-24-test"
        const val TEST_DB_24_25 = "training-migration-24-25-test"
        const val TEST_DB_25_26 = "training-migration-25-26-test"
        const val TEST_DB_26_27 = "training-migration-26-27-test"

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
