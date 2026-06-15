package com.training.trackplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        WorkoutEntry::class,
        WorkoutSet::class,
        DailyMetric::class,
        TrainingProgram::class,
        TrainingProgramItem::class,
        AppMeta::class
    ],
    version = 8,
    exportSchema = false
)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun programDao(): ProgramDao
    abstract fun dailyMetricDao(): DailyMetricDao
    abstract fun appMetaDao(): AppMetaDao

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
                        MIGRATION_7_8
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
