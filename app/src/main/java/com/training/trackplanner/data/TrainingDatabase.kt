package com.training.trackplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        WorkoutEntry::class,
        WorkoutSet::class,
        DailyMetric::class,
        DailyCheckIn::class,
        SmashSpeedRecord::class,
        TrainingProgram::class,
        TrainingProgramItem::class,
        TrainingProgramTombstone::class,
        AppMeta::class,
        InitialUserProfile::class,
        RuntimeExerciseMetadataEntity::class,
        StrengthPosteriorEventEntity::class,
        StrengthPosteriorHistoryEntity::class,
        StrengthPosteriorModelStateEntity::class,
        StrengthCurvePosteriorEntity::class,
        StrengthPosteriorEvidenceEntity::class,
        StrengthModelRevisionEntity::class,
        StrengthExercisePerformanceStateEntity::class,
        StrengthExercisePerformanceHistoryEntity::class,
        StrengthProxyTransferHistoryEntity::class
    ],
    version = 24,
    exportSchema = true
)
@TypeConverters(RuntimeMetadataTypeConverters::class)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun programDao(): ProgramDao
    abstract fun dailyMetricDao(): DailyMetricDao
    abstract fun dailyCheckInDao(): DailyCheckInDao
    abstract fun smashSpeedDao(): SmashSpeedDao
    abstract fun appMetaDao(): AppMetaDao
    abstract fun initialUserProfileDao(): InitialUserProfileDao
    abstract fun runtimeExerciseMetadataDao(): RuntimeExerciseMetadataDao
    abstract fun strengthPosteriorDao(): StrengthPosteriorDao

    companion object {
        @Volatile
        private var instance: TrainingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_meta` (
                        `key` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sets` ADD COLUMN `rpe` REAL")
                db.execSQL("ALTER TABLE `workout_sets` ADD COLUMN `restSecondsOverride` INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `equipment` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `compoundType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `plane` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `axialLoadLevel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `badmintonTransferRoles` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `fatigueCategories` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `adaptiveBaselineGroups` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `recoveryDecayProfile` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `systemicLoadWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `neuralHeavyWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `neuralSpeedWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `localLoadWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `decelerationWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `elasticSscWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `rotationPowerWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `antiRotationWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `overheadSwingWeight` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `gripLoadWeight` REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `progressMetricType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `strengthProgressionGroup` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `hypertrophyVolumeGroup` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `mainLiftGroup` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `accessoryContributionGroup` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `estimated1RmEligible` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `volumeLoadEligible` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `badmintonTransferStrength` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `courtMovementTypes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `badmintonSkillTargets` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `jointStressTags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `stabilityDemandLevel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `mobilityDemandLevel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `balanceContributionTags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `analysisEligibility` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `goal` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `weeklyTrainingDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `sessionMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `availableEquipment` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `excludedExerciseText` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `badmintonTransferRatio` REAL NOT NULL DEFAULT 0.4")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `sportStrengthRatio` TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `periodizationType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `activityKind` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `planningEligibility` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `imageAssetName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `archivedAt` INTEGER")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `needsReview` INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `initial_user_profiles` (
                        `id` INTEGER NOT NULL,
                        `bodyWeightKg` REAL,
                        `heightCm` REAL,
                        `birthYearOrAgeRange` TEXT NOT NULL DEFAULT '',
                        `gender` TEXT NOT NULL DEFAULT '',
                        `strengthSessionsPerWeek` REAL,
                        `strengthMinutesPerSession` INTEGER,
                        `strengthAverageRpe` REAL,
                        `badmintonSessionsPerWeek` REAL,
                        `badmintonMinutesPerSession` INTEGER,
                        `badmintonAverageRpe` REAL,
                        `strengthTrainingAge` TEXT NOT NULL DEFAULT '',
                        `badmintonTrainingAge` TEXT NOT NULL DEFAULT '',
                        `hadRecentTrainingBreak` INTEGER NOT NULL DEFAULT 0,
                        `breakWeeks` INTEGER,
                        `breakDueToPain` INTEGER NOT NULL DEFAULT 0,
                        `squatLevel` TEXT NOT NULL DEFAULT '',
                        `deadliftLevel` TEXT NOT NULL DEFAULT '',
                        `benchPressLevel` TEXT NOT NULL DEFAULT '',
                        `pullUpLevel` TEXT NOT NULL DEFAULT '',
                        `typicalSleepHours` REAL,
                        `sleepQuality` INTEGER,
                        `currentFatigue` INTEGER,
                        `currentSoreness` INTEGER,
                        `currentStress` INTEGER,
                        `currentMood` INTEGER,
                        `painAreas` TEXT NOT NULL DEFAULT '',
                        `avoidedMovements` TEXT NOT NULL DEFAULT '',
                        `goals` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `birthYear` INTEGER")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `sex` TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `strengthTrainingYears` REAL")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `badmintonTrainingYears` REAL")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `trainingBreakCategory` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `trainingBreakReason` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `squatKg` REAL")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `deadliftKg` REAL")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `benchPressKg` REAL")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `pullUpMaxReps` INTEGER")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `pullUpAddedWeightKg` REAL")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `usualSleepHours` REAL")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `currentCondition` INTEGER")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `painAreaTags` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `avoidMovementTags` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `primaryGoal` TEXT NOT NULL DEFAULT 'MIXED'")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `secondaryGoalTags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `freeNote` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `initial_user_profiles`
                    SET `sex` = CASE
                        WHEN lower(trim(`gender`)) IN ('male', 'm', '남', '남성') THEN 'MALE'
                        WHEN lower(trim(`gender`)) IN ('female', 'f', '여', '여성') THEN 'FEMALE'
                        ELSE 'UNSPECIFIED'
                    END,
                    `birthYear` = CASE
                        WHEN trim(`birthYearOrAgeRange`) GLOB '[12][0-9][0-9][0-9]'
                             AND CAST(trim(`birthYearOrAgeRange`) AS INTEGER) BETWEEN 1900 AND 2100
                        THEN CAST(trim(`birthYearOrAgeRange`) AS INTEGER)
                        ELSE NULL
                    END,
                    `trainingBreakCategory` = CASE
                        WHEN `breakWeeks` IS NULL OR `breakWeeks` <= 0 THEN 'NONE'
                        WHEN `breakWeeks` <= 1 THEN 'LESS_THAN_1_WEEK'
                        WHEN `breakWeeks` <= 2 THEN 'ONE_TO_TWO_WEEKS'
                        WHEN `breakWeeks` <= 4 THEN 'THREE_TO_FOUR_WEEKS'
                        WHEN `breakWeeks` <= 8 THEN 'FIVE_TO_EIGHT_WEEKS'
                        ELSE 'MORE_THAN_EIGHT_WEEKS'
                    END,
                    `trainingBreakReason` = CASE
                        WHEN `breakDueToPain` = 1 THEN 'PAIN_OR_INJURY'
                        ELSE 'NONE'
                    END,
                    `usualSleepHours` = `typicalSleepHours`,
                    `currentCondition` = `currentMood`
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v0.3.4.4.2 completes cold-start readiness binding without schema changes.
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE `initial_user_profiles`
                    SET
                        `currentFatigue` = CASE
                            WHEN `currentFatigue` BETWEEN 1 AND 5 THEN 6 - `currentFatigue`
                            ELSE `currentFatigue`
                        END,
                        `currentSoreness` = CASE
                            WHEN `currentSoreness` BETWEEN 1 AND 5 THEN 6 - `currentSoreness`
                            ELSE `currentSoreness`
                        END,
                        `currentStress` = CASE
                            WHEN `currentStress` BETWEEN 1 AND 5 THEN 6 - `currentStress`
                            ELSE `currentStress`
                        END
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `runtime_exercise_metadata` (
                        `stableKey` TEXT NOT NULL,
                        `exerciseName` TEXT NOT NULL,
                        `activityKind` TEXT NOT NULL,
                        `planningEligibility` TEXT NOT NULL,
                        `movementFamily` TEXT NOT NULL,
                        `movementSubtype` TEXT NOT NULL,
                        `programSlot` TEXT NOT NULL,
                        `redundancyGroup` TEXT NOT NULL,
                        `progressMetricType` TEXT NOT NULL,
                        `strengthProgressionGroup` TEXT NOT NULL,
                        `analysisEligibility` TEXT NOT NULL,
                        `primaryStressProfile` TEXT NOT NULL,
                        `secondaryStressTags` TEXT NOT NULL,
                        `tendonStressTags` TEXT NOT NULL,
                        `ligamentJointStabilityStressTags` TEXT NOT NULL,
                        `jointImpactStressTags` TEXT NOT NULL,
                        `cognitiveStressTags` TEXT NOT NULL,
                        `sportContextTags` TEXT NOT NULL,
                        `recoveryDecayProfile` TEXT NOT NULL,
                        `stressMagnitudeHint` TEXT NOT NULL,
                        `badmintonTransferLevel` TEXT NOT NULL,
                        `badmintonTransferType` TEXT NOT NULL,
                        `badmintonSkillTargets` TEXT NOT NULL,
                        `badmintonPhysicalQualities` TEXT NOT NULL,
                        `transferConfidence` TEXT NOT NULL,
                        `sourceConfidenceLevel` TEXT NOT NULL,
                        `finalSourceStatus` TEXT NOT NULL,
                        `neuromuscularStressLevel` TEXT NOT NULL,
                        `systemicMuscularStressLevel` TEXT NOT NULL,
                        `localMuscularStressLevel` TEXT NOT NULL,
                        `jointTendonImpactStressLevel` TEXT NOT NULL,
                        `movementFocusDemandLevel` TEXT NOT NULL,
                        `recoveryDurationClass` TEXT NOT NULL,
                        `safeForSeedMutation` INTEGER NOT NULL,
                        PRIMARY KEY(`stableKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_entries` ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `workout_entries` ADD COLUMN `firstConfirmedAt` INTEGER")
                db.execSQL("UPDATE `workout_entries` SET `displayOrder` = `id`")
                db.execSQL(
                    """
                    UPDATE `workout_entries`
                    SET `firstConfirmedAt` = COALESCE(`completedAt`, `createdAt`)
                    WHERE EXISTS (
                        SELECT 1 FROM `workout_sets`
                        WHERE `workout_sets`.`entryId` = `workout_entries`.`id`
                          AND `workout_sets`.`confirmed` = 1
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_check_ins` (
                        `date` TEXT NOT NULL,
                        `sleepHours` REAL,
                        `overallFatigue` INTEGER,
                        `lowerBodyFatigue` INTEGER,
                        `jointTendonDiscomfort` INTEGER,
                        `focusMotivation` INTEGER,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `runtime_exercise_metadata` ADD COLUMN `appCueProfile` TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `smash_speed_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `speedKmh` REAL NOT NULL,
                        `attemptIndex` INTEGER,
                        `source` TEXT NOT NULL,
                        `note` TEXT,
                        `parentWorkoutEntryId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_smash_speed_records_date` ON `smash_speed_records` (`date`)")
            }
        }

        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `training_program_items` ADD COLUMN `trainingSlot` TEXT")
                db.execSQL("ALTER TABLE `training_program_items` ADD COLUMN `dayIntensity` TEXT")
                db.execSQL("ALTER TABLE `training_program_items` ADD COLUMN `weightSource` TEXT")
            }
        }

        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_entries` ADD COLUMN `performedAt` INTEGER")
            }
        }

        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_check_ins` ADD COLUMN `bodyWeightKg` REAL")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `daily_check_ins` (
                        `date`, `sleepHours`, `bodyWeightKg`, `overallFatigue`, `lowerBodyFatigue`,
                        `jointTendonDiscomfort`, `focusMotivation`, `note`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        `date`, `sleepHours`, `bodyWeightKg`, NULL, NULL, NULL, NULL, NULL,
                        `updatedAt`, `updatedAt`
                    FROM `daily_metrics`
                    WHERE `sleepHours` IS NOT NULL OR `bodyWeightKg` IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `daily_check_ins`
                    SET
                        `sleepHours` = CASE
                            WHEN EXISTS (
                                SELECT 1 FROM `daily_metrics`
                                WHERE `daily_metrics`.`date` = `daily_check_ins`.`date`
                            ) AND (
                                `daily_check_ins`.`sleepHours` IS NULL OR
                                (
                                    SELECT `updatedAt` FROM `daily_metrics`
                                    WHERE `daily_metrics`.`date` = `daily_check_ins`.`date`
                                ) > `daily_check_ins`.`updatedAt`
                            )
                            THEN COALESCE(
                                (
                                    SELECT `sleepHours` FROM `daily_metrics`
                                    WHERE `daily_metrics`.`date` = `daily_check_ins`.`date`
                                ),
                                `daily_check_ins`.`sleepHours`
                            )
                            ELSE `daily_check_ins`.`sleepHours`
                        END,
                        `bodyWeightKg` = COALESCE(
                            `daily_check_ins`.`bodyWeightKg`,
                            (
                                SELECT `bodyWeightKg` FROM `daily_metrics`
                                WHERE `daily_metrics`.`date` = `daily_check_ins`.`date`
                            )
                        ),
                        `updatedAt` = MAX(
                            `daily_check_ins`.`updatedAt`,
                            COALESCE(
                                (
                                    SELECT `updatedAt` FROM `daily_metrics`
                                    WHERE `daily_metrics`.`date` = `daily_check_ins`.`date`
                                ),
                                `daily_check_ins`.`updatedAt`
                            )
                        )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `daily_metrics`
                    SET
                        `sleepHours` = (
                            SELECT `sleepHours` FROM `daily_check_ins`
                            WHERE `daily_check_ins`.`date` = `daily_metrics`.`date`
                        ),
                        `bodyWeightKg` = (
                            SELECT `bodyWeightKg` FROM `daily_check_ins`
                            WHERE `daily_check_ins`.`date` = `daily_metrics`.`date`
                        ),
                        `updatedAt` = MAX(
                            `daily_metrics`.`updatedAt`,
                            (
                                SELECT `updatedAt` FROM `daily_check_ins`
                                WHERE `daily_check_ins`.`date` = `daily_metrics`.`date`
                            )
                        )
                    WHERE EXISTS (
                        SELECT 1 FROM `daily_check_ins`
                        WHERE `daily_check_ins`.`date` = `daily_metrics`.`date`
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `daily_metrics` (`date`, `sleepHours`, `bodyWeightKg`, `updatedAt`)
                    SELECT `date`, `sleepHours`, `bodyWeightKg`, `updatedAt`
                    FROM `daily_check_ins`
                    WHERE `sleepHours` IS NOT NULL OR `bodyWeightKg` IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `initial_user_profiles` ADD COLUMN `habitualTrainingIntensity` TEXT")
            }
        }

        internal val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_posterior_events` (
                        `eventUuid` TEXT NOT NULL,
                        `sessionKey` TEXT NOT NULL,
                        `sessionDate` TEXT NOT NULL,
                        `completionFingerprint` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `creationReason` TEXT NOT NULL,
                        `confirmedSetCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `processedAt` INTEGER,
                        `modelVersion` TEXT NOT NULL,
                        `curveVersion` TEXT NOT NULL,
                        `factorSchemaVersion` TEXT NOT NULL,
                        `evidenceFingerprint` TEXT,
                        `errorCode` TEXT,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`eventUuid`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_posterior_events_sessionKey` ON `strength_posterior_events` (`sessionKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_posterior_events_sessionDate` ON `strength_posterior_events` (`sessionDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_posterior_events_status` ON `strength_posterior_events` (`status`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_strength_posterior_events_completionFingerprint` ON `strength_posterior_events` (`completionFingerprint`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_posterior_history` (
                        `eventUuid` TEXT NOT NULL,
                        `targetKey` TEXT NOT NULL,
                        `sessionDate` TEXT NOT NULL,
                        `priorMedian` REAL,
                        `priorLow50` REAL,
                        `priorHigh50` REAL,
                        `priorLow80` REAL,
                        `priorHigh80` REAL,
                        `priorLow95` REAL,
                        `priorHigh95` REAL,
                        `posteriorMedian` REAL,
                        `posteriorLow50` REAL,
                        `posteriorHigh50` REAL,
                        `posteriorLow80` REAL,
                        `posteriorHigh80` REAL,
                        `posteriorLow95` REAL,
                        `posteriorHigh95` REAL,
                        `directObservedLoad` REAL,
                        `directObservationType` TEXT NOT NULL,
                        `sessionObservationMedian` REAL,
                        `sessionObservationLow80` REAL,
                        `sessionObservationHigh80` REAL,
                        `posteriorMeanChange` REAL,
                        `posteriorVarianceBefore` REAL,
                        `posteriorVarianceAfter` REAL,
                        `intervalWidthChange80` REAL,
                        `predictivePercentile` REAL,
                        `standardizedSurprise` REAL,
                        `modelVersion` TEXT NOT NULL,
                        `factorSchemaVersion` TEXT NOT NULL,
                        `curveVersion` TEXT NOT NULL,
                        `targetConfigVersion` TEXT NOT NULL,
                        `evidenceFingerprint` TEXT NOT NULL,
                        `sourceEvidenceStatus` TEXT NOT NULL,
                        `sourceSetCountAtProcessing` INTEGER NOT NULL,
                        `bodyWeightKgAtProcessing` REAL,
                        `rawAddedWeightKgAtProcessing` REAL,
                        `bodyWeightSource` TEXT,
                        `curveProfileId` TEXT,
                        `curveMatchLevel` TEXT,
                        `curveCalibrationStatus` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`eventUuid`, `targetKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_posterior_history_targetKey` ON `strength_posterior_history` (`targetKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_posterior_history_eventUuid` ON `strength_posterior_history` (`eventUuid`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_posterior_model_state` (
                        `modelInstanceKey` TEXT NOT NULL,
                        `orderedFactorSchema` TEXT NOT NULL,
                        `stateMeanEncoded` TEXT NOT NULL,
                        `packedCovarianceEncoded` TEXT NOT NULL,
                        `stateDimension` INTEGER NOT NULL,
                        `lastProcessedEventUuid` TEXT,
                        `lastProcessedDate` TEXT,
                        `modelVersion` TEXT NOT NULL,
                        `curveVersion` TEXT NOT NULL,
                        `factorSchemaVersion` TEXT NOT NULL,
                        `stateFingerprint` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`modelInstanceKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_curve_posteriors` (
                        `curveSubjectKey` TEXT NOT NULL,
                        `canonicalProfileId` TEXT NOT NULL,
                        `thetaGridEncoded` TEXT NOT NULL,
                        `posteriorWeightsEncoded` TEXT NOT NULL,
                        `totalObservationCount` INTEGER NOT NULL,
                        `strongObservationCount` INTEGER NOT NULL,
                        `distinctRepRangeCount` INTEGER NOT NULL,
                        `minObservedReps` INTEGER,
                        `maxObservedReps` INTEGER,
                        `calibrationStatus` TEXT NOT NULL,
                        `curveVersion` TEXT NOT NULL,
                        `posteriorFingerprint` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`curveSubjectKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_posterior_evidence` (
                        `evidenceFingerprint` TEXT NOT NULL,
                        `eventUuid` TEXT NOT NULL,
                        `sessionKey` TEXT NOT NULL,
                        `sessionDate` TEXT NOT NULL,
                        `exerciseStableKey` TEXT NOT NULL,
                        `exerciseNameAtProcessing` TEXT NOT NULL,
                        `directTargetKey` TEXT,
                        `observationType` TEXT NOT NULL,
                        `capacityMedianKg` REAL NOT NULL,
                        `capacityLow80Kg` REAL NOT NULL,
                        `capacityHigh80Kg` REAL NOT NULL,
                        `lowerBoundOnly` INTEGER NOT NULL,
                        `logVariance` REAL NOT NULL,
                        `directObservedLoadKg` REAL,
                        `bodyWeightKg` REAL,
                        `rawAddedWeightKg` REAL,
                        `bodyWeightSource` TEXT NOT NULL,
                        `curveProfileId` TEXT NOT NULL,
                        `curveMatchLevel` TEXT NOT NULL,
                        `curveVarianceMultiplier` REAL NOT NULL,
                        `curveSubjectKey` TEXT NOT NULL,
                        `sourceSetIdsEncoded` TEXT NOT NULL,
                        `strongObservationCount` INTEGER NOT NULL,
                        `diagnosticsEncoded` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`evidenceFingerprint`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_posterior_evidence_eventUuid` ON `strength_posterior_evidence` (`eventUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_posterior_evidence_exerciseStableKey` ON `strength_posterior_evidence` (`exerciseStableKey`)")
            }
        }

        internal val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `strength_posterior_events` ADD COLUMN `revisionKey` TEXT NOT NULL DEFAULT '${StrengthModelRevisionPolicy.LEGACY_REVISION_KEY}'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_model_revisions` (
                        `revisionKey` TEXT NOT NULL,
                        `modelVersion` TEXT NOT NULL,
                        `factorSchemaVersion` TEXT NOT NULL,
                        `targetRegistryVersion` TEXT NOT NULL,
                        `proxyRegistryVersion` TEXT NOT NULL,
                        `rirPolicyVersion` TEXT NOT NULL,
                        `curveVersion` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `creationReason` TEXT NOT NULL,
                        `sourceRevisionKey` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `rebuildStartedAt` INTEGER,
                        `rebuildCompletedAt` INTEGER,
                        `revisionFingerprint` TEXT NOT NULL,
                        `errorCode` TEXT,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`revisionKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_exercise_performance_state` (
                        `revisionKey` TEXT NOT NULL,
                        `exerciseStableKey` TEXT NOT NULL,
                        `priorSource` TEXT NOT NULL,
                        `stateLogMean` REAL NOT NULL,
                        `stateLogVariance` REAL NOT NULL,
                        `lastProcessedEventUuid` TEXT NOT NULL,
                        `lastProcessedSessionKey` TEXT NOT NULL,
                        `lastProcessedDate` TEXT NOT NULL,
                        `baselineEstablished` INTEGER NOT NULL,
                        `observationCount` INTEGER NOT NULL,
                        `twoSidedObservationCount` INTEGER NOT NULL,
                        `modelVersion` TEXT NOT NULL,
                        `curveVersion` TEXT NOT NULL,
                        `rirPolicyVersion` TEXT NOT NULL,
                        `stateFingerprint` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`revisionKey`, `exerciseStableKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_exercise_performance_state_revisionKey` ON `strength_exercise_performance_state` (`revisionKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_exercise_performance_state_exerciseStableKey` ON `strength_exercise_performance_state` (`exerciseStableKey`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_exercise_performance_history` (
                        `revisionKey` TEXT NOT NULL,
                        `eventUuid` TEXT NOT NULL,
                        `sessionKey` TEXT NOT NULL,
                        `sessionDate` TEXT NOT NULL,
                        `exerciseStableKey` TEXT NOT NULL,
                        `priorLogMean` REAL NOT NULL,
                        `priorLogVariance` REAL NOT NULL,
                        `sessionLikelihoodLogMean` REAL,
                        `sessionLikelihoodLogVariance` REAL,
                        `sessionLikelihoodProper` INTEGER NOT NULL,
                        `innovationResidualLog` REAL,
                        `innovationVariance` REAL,
                        `posteriorLogMean` REAL NOT NULL,
                        `posteriorLogVariance` REAL NOT NULL,
                        `posteriorMeanIncrementLog` REAL NOT NULL,
                        `transitionDays` INTEGER NOT NULL,
                        `baselineEstablishedBefore` INTEGER NOT NULL,
                        `baselineEstablishedAfter` INTEGER NOT NULL,
                        `proxyTransferEligible` INTEGER NOT NULL,
                        `proxyTransferApplied` INTEGER NOT NULL,
                        `modelVersion` TEXT NOT NULL,
                        `curveVersion` TEXT NOT NULL,
                        `rirPolicyVersion` TEXT NOT NULL,
                        `evidenceFingerprint` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`revisionKey`, `eventUuid`, `exerciseStableKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_exercise_performance_history_revisionKey` ON `strength_exercise_performance_history` (`revisionKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_exercise_performance_history_eventUuid` ON `strength_exercise_performance_history` (`eventUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_exercise_performance_history_exerciseStableKey` ON `strength_exercise_performance_history` (`exerciseStableKey`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `strength_proxy_transfer_history` (
                        `revisionKey` TEXT NOT NULL,
                        `eventUuid` TEXT NOT NULL,
                        `sessionDate` TEXT NOT NULL,
                        `exerciseStableKey` TEXT NOT NULL,
                        `targetKey` TEXT NOT NULL,
                        `innovationResidualLog` REAL NOT NULL,
                        `innovationVariance` REAL NOT NULL,
                        `transferCoefficient` REAL NOT NULL,
                        `transferLogVariance` REAL NOT NULL,
                        `orderedSharedFactorKeys` TEXT NOT NULL,
                        `sharedLoadingVectorEncoded` TEXT NOT NULL,
                        `targetSpecificContribution` REAL NOT NULL,
                        `applied` INTEGER NOT NULL,
                        `exclusionReason` TEXT,
                        `proxyRegistryVersion` TEXT NOT NULL,
                        `modelVersion` TEXT NOT NULL,
                        `transferFingerprint` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`revisionKey`, `eventUuid`, `exerciseStableKey`, `targetKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_proxy_transfer_history_revisionKey` ON `strength_proxy_transfer_history` (`revisionKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_proxy_transfer_history_eventUuid` ON `strength_proxy_transfer_history` (`eventUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_proxy_transfer_history_exerciseStableKey` ON `strength_proxy_transfer_history` (`exerciseStableKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strength_proxy_transfer_history_targetKey` ON `strength_proxy_transfer_history` (`targetKey`)")
            }
        }

        internal val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `training_programs` ADD COLUMN `stableKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `training_programs`
                    SET `stableKey` = '${ProgramStableKeyPolicy.LEGACY_PREFIX}' || `id`
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_training_programs_stableKey` ON `training_programs` (`stableKey`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `training_program_tombstones` (
                        `programStableKey` TEXT NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `seedVersion` INTEGER,
                        PRIMARY KEY(`programStableKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): TrainingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrainingDatabase::class.java,
                    "training_track_planner.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
