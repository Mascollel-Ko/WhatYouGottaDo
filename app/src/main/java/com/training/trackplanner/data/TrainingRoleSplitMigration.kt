package com.training.trackplanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TEMP TABLE `legacy_training_roles` (`exerciseStableKey` TEXT NOT NULL, `legacyRole` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `legacy_training_roles` SELECT `stableKey`, `trainingRole` FROM `exercises` WHERE trim(`trainingRole`) <> ''"
        )

        createExercisesWithoutLegacyRole(db)
        recreateExerciseReferences(db)
        createRoleRelationTables(db)
        migrateIntrinsicTrainingRoles(db)
        APPROVED_PROGRAM_CAPABILITIES.forEach { (stableKey, capability) ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO `exercise_program_slot_capability_relations`
                    (`exerciseStableKey`, `capabilityCode`, `provenance`, `reviewStatus`, `notes`)
                SELECT ?, ?, 'LEGACY-TRAINING-ROLE-SEED-V1', 'APPROVED', 'Exact 26-row compatibility migration'
                WHERE EXISTS (SELECT 1 FROM `exercises` WHERE `stableKey` = ?)
                """.trimIndent(),
                arrayOf(stableKey, capability.name, stableKey)
            )
        }
        db.execSQL("DROP TABLE `legacy_training_roles`")
    }
}

private fun createExercisesWithoutLegacyRole(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE `exercises_new` (
            `stableKey` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL,
            `detail1` TEXT NOT NULL, `detail2` TEXT NOT NULL, `mode` TEXT NOT NULL,
            `description` TEXT NOT NULL, `defaultRestSeconds` INTEGER NOT NULL,
            `familyId` TEXT NOT NULL, `familyName` TEXT NOT NULL, `familyRole` TEXT NOT NULL,
            `familyE1rmMultiplier` REAL NOT NULL, `movementPattern` TEXT NOT NULL,
            `movementCategory` TEXT NOT NULL, `primaryMuscles` TEXT NOT NULL,
            `secondaryMuscles` TEXT NOT NULL, `equipment` TEXT NOT NULL,
            `equipmentTags` TEXT NOT NULL, `compoundType` TEXT NOT NULL,
            `forceType` TEXT NOT NULL, `bodyRegion` TEXT NOT NULL, `plane` TEXT NOT NULL,
            `laterality` TEXT NOT NULL, `axialLoadLevel` TEXT NOT NULL,
            `stabilityRoles` TEXT NOT NULL, `sportTransferDirect` TEXT NOT NULL,
            `sportTransferSupportive` TEXT NOT NULL, `badmintonTransferRoles` TEXT NOT NULL,
            `fatigueCategories` TEXT NOT NULL, `adaptiveBaselineGroups` TEXT NOT NULL,
            `accessoryRoles` TEXT NOT NULL, `loadProfile` TEXT NOT NULL,
            `recoveryDecayProfile` TEXT NOT NULL, `systemicLoadWeight` REAL NOT NULL,
            `neuralHeavyWeight` REAL NOT NULL, `neuralSpeedWeight` REAL NOT NULL,
            `localLoadWeight` REAL NOT NULL, `decelerationWeight` REAL NOT NULL,
            `elasticSscWeight` REAL NOT NULL, `rotationPowerWeight` REAL NOT NULL,
            `antiRotationWeight` REAL NOT NULL, `overheadSwingWeight` REAL NOT NULL,
            `gripLoadWeight` REAL NOT NULL, `progressMetricType` TEXT NOT NULL,
            `strengthProgressionGroup` TEXT NOT NULL, `hypertrophyVolumeGroup` TEXT NOT NULL,
            `mainLiftGroup` TEXT NOT NULL, `accessoryContributionGroup` TEXT NOT NULL,
            `estimated1RmEligible` INTEGER NOT NULL, `volumeLoadEligible` INTEGER NOT NULL,
            `badmintonTransferStrength` TEXT NOT NULL, `courtMovementTypes` TEXT NOT NULL,
            `badmintonSkillTargets` TEXT NOT NULL, `jointStressTags` TEXT NOT NULL,
            `stabilityDemandLevel` TEXT NOT NULL, `mobilityDemandLevel` TEXT NOT NULL,
            `balanceContributionTags` TEXT NOT NULL, `analysisEligibility` TEXT NOT NULL,
            `activityKind` TEXT NOT NULL, `planningEligibility` TEXT NOT NULL,
            `metadataConfidence` TEXT NOT NULL, `imageAssetName` TEXT NOT NULL,
            `isActive` INTEGER NOT NULL, `archivedAt` INTEGER, `isCustom` INTEGER NOT NULL,
            `needsReview` INTEGER NOT NULL, PRIMARY KEY(`stableKey`)
        )
        """.trimIndent()
    )
    val columns = EXERCISE_COLUMNS_WITHOUT_LEGACY_ROLE.joinToString("`, `", prefix = "`", postfix = "`")
    db.execSQL("INSERT INTO `exercises_new` ($columns) SELECT $columns FROM `exercises`")
}

private fun recreateExerciseReferences(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE `workout_entries_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL,
            `exerciseStableKey` TEXT NOT NULL, `exerciseName` TEXT NOT NULL,
            `category` TEXT NOT NULL, `restSeconds` INTEGER NOT NULL, `notes` TEXT NOT NULL,
            `rpe` REAL, `maxReps` INTEGER, `createdAt` INTEGER NOT NULL,
            `completedAt` INTEGER, `displayOrder` INTEGER NOT NULL,
            `firstConfirmedAt` INTEGER, `performedAt` INTEGER,
            FOREIGN KEY(`exerciseStableKey`) REFERENCES `exercises_new`(`stableKey`)
                ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """.trimIndent()
    )
    db.execSQL("INSERT INTO `workout_entries_new` SELECT * FROM `workout_entries`")
    db.execSQL(
        """
        CREATE TABLE `training_program_items_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `programId` INTEGER NOT NULL,
            `weekNumber` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL,
            `orderIndex` INTEGER NOT NULL, `exerciseStableKey` TEXT NOT NULL,
            `exerciseName` TEXT NOT NULL, `category` TEXT NOT NULL,
            `restSeconds` INTEGER NOT NULL, `prescription` TEXT NOT NULL,
            `setCount` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL,
            `seconds` INTEGER NOT NULL, `trainingSlot` TEXT, `dayIntensity` TEXT,
            `weightSource` TEXT,
            FOREIGN KEY(`programId`) REFERENCES `training_programs`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`exerciseStableKey`) REFERENCES `exercises_new`(`stableKey`)
                ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """.trimIndent()
    )
    db.execSQL("INSERT INTO `training_program_items_new` SELECT * FROM `training_program_items`")
    db.execSQL(
        """
        CREATE TABLE `training_program_item_sets_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `programItemId` INTEGER NOT NULL,
            `setIndex` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL,
            `seconds` INTEGER NOT NULL,
            FOREIGN KEY(`programItemId`) REFERENCES `training_program_items_new`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("INSERT INTO `training_program_item_sets_new` SELECT * FROM `training_program_item_sets`")

    db.execSQL("DROP TABLE `training_program_item_sets`")
    db.execSQL("DROP TABLE `training_program_items`")
    db.execSQL("DROP TABLE `workout_entries`")
    db.execSQL("DROP TABLE `exercises`")
    db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
    db.execSQL("ALTER TABLE `workout_entries_new` RENAME TO `workout_entries`")
    db.execSQL("ALTER TABLE `training_program_items_new` RENAME TO `training_program_items`")
    db.execSQL("ALTER TABLE `training_program_item_sets_new` RENAME TO `training_program_item_sets`")
    db.execSQL("CREATE INDEX `index_workout_entries_exerciseStableKey` ON `workout_entries` (`exerciseStableKey`)")
    db.execSQL("CREATE INDEX `index_training_program_items_programId` ON `training_program_items` (`programId`)")
    db.execSQL("CREATE INDEX `index_training_program_items_exerciseStableKey` ON `training_program_items` (`exerciseStableKey`)")
    db.execSQL("CREATE INDEX `index_training_program_item_sets_programItemId` ON `training_program_item_sets` (`programItemId`)")
    db.execSQL("CREATE UNIQUE INDEX `index_training_program_item_sets_programItemId_setIndex` ON `training_program_item_sets` (`programItemId`, `setIndex`)")
}

private fun createRoleRelationTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE `exercise_training_role_relations` (
            `exerciseStableKey` TEXT NOT NULL, `trainingRoleCode` TEXT NOT NULL,
            `provenance` TEXT NOT NULL, `reviewStatus` TEXT NOT NULL, `notes` TEXT NOT NULL,
            PRIMARY KEY(`exerciseStableKey`, `trainingRoleCode`),
            FOREIGN KEY(`exerciseStableKey`) REFERENCES `exercises`(`stableKey`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX `index_exercise_training_role_relations_exerciseStableKey` ON `exercise_training_role_relations` (`exerciseStableKey`)")
    db.execSQL(
        """
        CREATE TABLE `exercise_program_slot_capability_relations` (
            `exerciseStableKey` TEXT NOT NULL, `capabilityCode` TEXT NOT NULL,
            `provenance` TEXT NOT NULL, `reviewStatus` TEXT NOT NULL, `notes` TEXT NOT NULL,
            PRIMARY KEY(`exerciseStableKey`, `capabilityCode`),
            FOREIGN KEY(`exerciseStableKey`) REFERENCES `exercises`(`stableKey`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX `index_exercise_program_slot_capability_relations_exerciseStableKey` ON `exercise_program_slot_capability_relations` (`exerciseStableKey`)")
}

private fun migrateIntrinsicTrainingRoles(db: SupportSQLiteDatabase) {
    val mappings = mapOf(
        "MAIN_STRENGTH" to TrainingRole.STRENGTH,
        "SECONDARY_STRENGTH" to TrainingRole.STRENGTH,
        "POWER" to TrainingRole.POWER,
        "PLYOMETRIC" to TrainingRole.PLYOMETRIC,
        "STABILITY" to TrainingRole.STABILITY,
        "MOBILITY" to TrainingRole.MOBILITY,
        "PREHAB" to TrainingRole.PREHAB,
        "SKILL" to TrainingRole.SKILL_DRILL,
        "SKILL_DRILL" to TrainingRole.SKILL_DRILL,
        "CONDITIONING" to TrainingRole.CONDITIONING,
        "TEST" to TrainingRole.TEST,
        "RECOVERY" to TrainingRole.RECOVERY
    )
    db.query("SELECT `exerciseStableKey`, `legacyRole` FROM `legacy_training_roles`").use { cursor ->
        while (cursor.moveToNext()) {
            val stableKey = cursor.getString(0)
            val role = mappings[cursor.getString(1).uppercase()] ?: continue
            db.execSQL(
                """
                INSERT OR IGNORE INTO `exercise_training_role_relations`
                    (`exerciseStableKey`, `trainingRoleCode`, `provenance`, `reviewStatus`, `notes`)
                VALUES (?, ?, 'ROOM-26-27-LEGACY-MIGRATION', 'MIGRATED', 'Mapped from persisted legacy field')
                """.trimIndent(),
                arrayOf(stableKey, role.name)
            )
        }
    }
}

private val EXERCISE_COLUMNS_WITHOUT_LEGACY_ROLE = listOf(
    "stableKey", "name", "category", "detail1", "detail2", "mode", "description",
    "defaultRestSeconds", "familyId", "familyName", "familyRole", "familyE1rmMultiplier",
    "movementPattern", "movementCategory", "primaryMuscles", "secondaryMuscles", "equipment",
    "equipmentTags", "compoundType", "forceType", "bodyRegion", "plane", "laterality",
    "axialLoadLevel", "stabilityRoles", "sportTransferDirect", "sportTransferSupportive",
    "badmintonTransferRoles", "fatigueCategories", "adaptiveBaselineGroups", "accessoryRoles",
    "loadProfile", "recoveryDecayProfile", "systemicLoadWeight", "neuralHeavyWeight",
    "neuralSpeedWeight", "localLoadWeight", "decelerationWeight", "elasticSscWeight",
    "rotationPowerWeight", "antiRotationWeight", "overheadSwingWeight", "gripLoadWeight",
    "progressMetricType", "strengthProgressionGroup", "hypertrophyVolumeGroup", "mainLiftGroup",
    "accessoryContributionGroup", "estimated1RmEligible", "volumeLoadEligible",
    "badmintonTransferStrength", "courtMovementTypes", "badmintonSkillTargets", "jointStressTags",
    "stabilityDemandLevel", "mobilityDemandLevel", "balanceContributionTags", "analysisEligibility",
    "activityKind", "planningEligibility", "metadataConfidence", "imageAssetName", "isActive",
    "archivedAt", "isCustom", "needsReview"
)

private val APPROVED_PROGRAM_CAPABILITIES = listOf(
    "barbell_romanian_deadlift" to ProgramSlotCapability.MAIN_STRENGTH_SLOT,
    "dumbbell_romanian_deadlift" to ProgramSlotCapability.MAIN_STRENGTH_SLOT,
    "ex_314df428" to ProgramSlotCapability.PLYOMETRIC_SLOT,
    "ex_33841b88" to ProgramSlotCapability.SPEED_REACTIVE_SLOT,
    "ex_34e7d21" to ProgramSlotCapability.PLYOMETRIC_SLOT,
    "ex_462c760e" to ProgramSlotCapability.SECONDARY_STRENGTH_SLOT,
    "ex_5322f2d1" to ProgramSlotCapability.ACCESSORY_SLOT,
    "ex_5ca7133f" to ProgramSlotCapability.ACCESSORY_SLOT,
    "ex_85f12271" to ProgramSlotCapability.STABILITY_SLOT,
    "ex_8824026f" to ProgramSlotCapability.PLYOMETRIC_SLOT,
    "ex_a12de111" to ProgramSlotCapability.SPEED_REACTIVE_SLOT,
    "ex_bd072cd" to ProgramSlotCapability.ACCESSORY_SLOT,
    "ex_d60745b4" to ProgramSlotCapability.ACCESSORY_SLOT,
    "ex_e2efd0fe" to ProgramSlotCapability.MAIN_STRENGTH_SLOT,
    "ex_eb636bac" to ProgramSlotCapability.ACCESSORY_SLOT,
    "half_kneeling_single_arm_dumbbell_press" to ProgramSlotCapability.STABILITY_SLOT,
    "half_kneeling_single_arm_kettlebell_press" to ProgramSlotCapability.STABILITY_SLOT,
    "landmine_rotation" to ProgramSlotCapability.POWER_SLOT,
    "lateral_bound_continuous" to ProgramSlotCapability.PLYOMETRIC_SLOT,
    "med_ball_overhead_slam" to ProgramSlotCapability.POWER_SLOT,
    "med_ball_rotational_slam" to ProgramSlotCapability.POWER_SLOT,
    "medicine_ball_rotational_throw" to ProgramSlotCapability.POWER_SLOT,
    "single_leg_hip_bridge" to ProgramSlotCapability.STABILITY_SLOT,
    "single_leg_rdl" to ProgramSlotCapability.MAIN_STRENGTH_SLOT,
    "vipr_chop" to ProgramSlotCapability.POWER_SLOT,
    "vipr_rotational_lift" to ProgramSlotCapability.POWER_SLOT
)
