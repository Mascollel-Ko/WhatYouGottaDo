package com.training.trackplanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TEMP TABLE `exercise_key_migration` (
                `oldId` INTEGER NOT NULL PRIMARY KEY,
                `oldStableKey` TEXT NOT NULL,
                `canonicalStableKey` TEXT NOT NULL,
                `issueCode` TEXT
            )
            """.trimIndent()
        )
        val issues = mutableListOf<MigrationKeyIssue>()
        db.query(
            "SELECT `id`, `stableKey`, `equipment`, `equipmentTags` FROM `exercises` ORDER BY `id`"
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val stableKeyIndex = cursor.getColumnIndexOrThrow("stableKey")
            val equipmentIndex = cursor.getColumnIndexOrThrow("equipment")
            val equipmentTagsIndex = cursor.getColumnIndexOrThrow("equipmentTags")
            while (cursor.moveToNext()) {
                val oldId = cursor.getLong(idIndex)
                val oldStableKey = cursor.getString(stableKeyIndex).orEmpty()
                val resolution = ExerciseMigrationKeyPolicy.resolve(
                    oldId = oldId,
                    oldStableKey = oldStableKey,
                    equipment = cursor.getString(equipmentIndex).orEmpty(),
                    equipmentTags = cursor.getString(equipmentTagsIndex).orEmpty()
                )
                db.execSQL(
                    """
                    INSERT INTO `exercise_key_migration`
                        (`oldId`, `oldStableKey`, `canonicalStableKey`, `issueCode`)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(oldId, oldStableKey, resolution.canonicalStableKey, resolution.issueCode)
                )
                resolution.issueCode?.let { code ->
                    issues += MigrationKeyIssue(oldId, oldStableKey, resolution.canonicalStableKey, code)
                }
            }
        }

        createIdentityIssueTable(db)
        migrateRuntimeMetadataStableKeys(db)
        createExerciseTable(db)
        db.execSQL(
            """
            INSERT OR IGNORE INTO `exercises_new` (
                `stableKey`, `name`, `category`, `detail1`, `detail2`, `mode`, `description`,
                `defaultRestSeconds`, `familyId`, `familyName`, `familyRole`,
                `familyE1rmMultiplier`, `movementPattern`, `movementCategory`, `primaryMuscles`,
                `secondaryMuscles`, `equipment`, `equipmentTags`, `compoundType`, `forceType`,
                `bodyRegion`, `plane`, `laterality`, `axialLoadLevel`, `trainingRole`,
                `stabilityRoles`, `sportTransferDirect`, `sportTransferSupportive`,
                `badmintonTransferRoles`, `fatigueCategories`, `adaptiveBaselineGroups`,
                `accessoryRoles`, `loadProfile`, `recoveryDecayProfile`, `systemicLoadWeight`,
                `neuralHeavyWeight`, `neuralSpeedWeight`, `localLoadWeight`, `decelerationWeight`,
                `elasticSscWeight`, `rotationPowerWeight`, `antiRotationWeight`,
                `overheadSwingWeight`, `gripLoadWeight`, `progressMetricType`,
                `strengthProgressionGroup`, `hypertrophyVolumeGroup`, `mainLiftGroup`,
                `accessoryContributionGroup`, `estimated1RmEligible`, `volumeLoadEligible`,
                `badmintonTransferStrength`, `courtMovementTypes`, `badmintonSkillTargets`,
                `jointStressTags`, `stabilityDemandLevel`, `mobilityDemandLevel`,
                `balanceContributionTags`, `analysisEligibility`, `activityKind`,
                `planningEligibility`, `metadataConfidence`, `imageAssetName`, `isActive`,
                `archivedAt`, `isCustom`, `needsReview`
            )
            SELECT
                migration.`canonicalStableKey`, exercise.`name`, exercise.`category`,
                exercise.`detail1`, exercise.`detail2`, exercise.`mode`, exercise.`description`,
                exercise.`defaultRestSeconds`, exercise.`familyId`, exercise.`familyName`,
                exercise.`familyRole`, exercise.`familyE1rmMultiplier`, exercise.`movementPattern`,
                exercise.`movementCategory`, exercise.`primaryMuscles`, exercise.`secondaryMuscles`,
                exercise.`equipment`, exercise.`equipmentTags`, exercise.`compoundType`,
                exercise.`forceType`, exercise.`bodyRegion`, exercise.`plane`,
                exercise.`laterality`, exercise.`axialLoadLevel`, exercise.`trainingRole`,
                exercise.`stabilityRoles`, exercise.`sportTransferDirect`,
                exercise.`sportTransferSupportive`, exercise.`badmintonTransferRoles`,
                exercise.`fatigueCategories`, exercise.`adaptiveBaselineGroups`,
                exercise.`accessoryRoles`, exercise.`loadProfile`, exercise.`recoveryDecayProfile`,
                exercise.`systemicLoadWeight`, exercise.`neuralHeavyWeight`,
                exercise.`neuralSpeedWeight`, exercise.`localLoadWeight`,
                exercise.`decelerationWeight`, exercise.`elasticSscWeight`,
                exercise.`rotationPowerWeight`, exercise.`antiRotationWeight`,
                exercise.`overheadSwingWeight`, exercise.`gripLoadWeight`,
                exercise.`progressMetricType`, exercise.`strengthProgressionGroup`,
                exercise.`hypertrophyVolumeGroup`, exercise.`mainLiftGroup`,
                exercise.`accessoryContributionGroup`, exercise.`estimated1RmEligible`,
                exercise.`volumeLoadEligible`, exercise.`badmintonTransferStrength`,
                exercise.`courtMovementTypes`, exercise.`badmintonSkillTargets`,
                exercise.`jointStressTags`, exercise.`stabilityDemandLevel`,
                exercise.`mobilityDemandLevel`, exercise.`balanceContributionTags`,
                exercise.`analysisEligibility`, exercise.`activityKind`,
                exercise.`planningEligibility`, exercise.`metadataConfidence`,
                exercise.`imageAssetName`,
                CASE WHEN migration.`issueCode` IS NULL THEN exercise.`isActive` ELSE 0 END,
                exercise.`archivedAt`,
                CASE WHEN migration.`issueCode` IS NULL THEN exercise.`isCustom` ELSE 1 END,
                CASE WHEN migration.`issueCode` IS NULL THEN exercise.`needsReview` ELSE 1 END
            FROM `exercises` AS exercise
            INNER JOIN `exercise_key_migration` AS migration ON migration.`oldId` = exercise.`id`
            ORDER BY
                CASE
                    WHEN lower(trim(exercise.`stableKey`)) = lower(trim(migration.`canonicalStableKey`))
                    THEN 0 ELSE 1
                END,
                exercise.`id`
            """.trimIndent()
        )

        val hasDanglingReferences = db.longForQuery(
            """
            SELECT COUNT(*) FROM (
                SELECT `exerciseId` FROM `workout_entries`
                UNION ALL
                SELECT `exerciseId` FROM `training_program_items`
            ) AS reference
            LEFT JOIN `exercise_key_migration` AS migration
                ON migration.`oldId` = reference.`exerciseId`
            WHERE migration.`oldId` IS NULL
            """.trimIndent()
        ) > 0
        if (hasDanglingReferences) insertUnresolvedExercise(db)

        createWorkoutEntriesTable(db)
        createProgramItemsTable(db)
        db.execSQL(
            """
            INSERT INTO `workout_entries_new` (
                `id`, `date`, `exerciseStableKey`, `exerciseName`, `category`, `restSeconds`,
                `notes`, `rpe`, `maxReps`, `createdAt`, `completedAt`, `displayOrder`,
                `firstConfirmedAt`, `performedAt`
            )
            SELECT
                entry.`id`, entry.`date`,
                COALESCE(migration.`canonicalStableKey`, '${ExerciseMigrationKeyPolicy.UNRESOLVED_REFERENCE_KEY}'),
                entry.`exerciseName`, entry.`category`, entry.`restSeconds`, entry.`notes`,
                entry.`rpe`, entry.`maxReps`, entry.`createdAt`, entry.`completedAt`,
                entry.`displayOrder`, entry.`firstConfirmedAt`, entry.`performedAt`
            FROM `workout_entries` AS entry
            LEFT JOIN `exercise_key_migration` AS migration ON migration.`oldId` = entry.`exerciseId`
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `training_program_items_new` (
                `id`, `programId`, `weekNumber`, `dayOfWeek`, `orderIndex`,
                `exerciseStableKey`, `exerciseName`, `category`, `restSeconds`, `prescription`,
                `setCount`, `reps`, `weightKg`, `seconds`, `trainingSlot`, `dayIntensity`,
                `weightSource`
            )
            SELECT
                item.`id`, item.`programId`, item.`weekNumber`, item.`dayOfWeek`,
                item.`orderIndex`,
                COALESCE(migration.`canonicalStableKey`, '${ExerciseMigrationKeyPolicy.UNRESOLVED_REFERENCE_KEY}'),
                item.`exerciseName`, item.`category`, item.`restSeconds`, item.`prescription`,
                item.`setCount`, item.`reps`, item.`weightKg`, item.`seconds`,
                item.`trainingSlot`, item.`dayIntensity`, item.`weightSource`
            FROM `training_program_items` AS item
            LEFT JOIN `exercise_key_migration` AS migration ON migration.`oldId` = item.`exerciseId`
            """.trimIndent()
        )

        requireParity(db, "workout_entries", "workout_entries_new")
        requireParity(db, "training_program_items", "training_program_items_new")
        issues.forEach { issue -> insertMigrationIssue(db, issue) }
        insertDanglingReferenceIssues(db, "workout_entries", "WORKOUT_ENTRY")
        insertDanglingReferenceIssues(db, "training_program_items", "PROGRAM_ITEM")

        db.execSQL("DROP TABLE `workout_entries`")
        db.execSQL("ALTER TABLE `workout_entries_new` RENAME TO `workout_entries`")
        db.execSQL("CREATE INDEX `index_workout_entries_exerciseStableKey` ON `workout_entries` (`exerciseStableKey`)")
        db.execSQL("DROP TABLE `training_program_items`")
        db.execSQL("ALTER TABLE `training_program_items_new` RENAME TO `training_program_items`")
        db.execSQL("CREATE INDEX `index_training_program_items_programId` ON `training_program_items` (`programId`)")
        db.execSQL("CREATE INDEX `index_training_program_items_exerciseStableKey` ON `training_program_items` (`exerciseStableKey`)")
        db.execSQL("DROP TABLE `exercises`")
        db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
        db.execSQL("DROP TABLE `exercise_key_migration`")
    }
}

internal data class MigrationKeyResolution(
    val canonicalStableKey: String,
    val issueCode: String? = null
)

private data class MigrationKeyIssue(
    val oldId: Long,
    val oldStableKey: String,
    val canonicalStableKey: String,
    val issueCode: String
)

internal object ExerciseMigrationKeyPolicy {
    const val UNRESOLVED_REFERENCE_KEY = "migration_unresolved_exercise"

    private val directMappings = mapOf(
        "ex_201f6426" to "dumbbell_single_leg_rdl",
        "ex_885b629" to "dumbbell_single_leg_rdl",
        "imported_싱글_레그_rdl" to "dumbbell_single_leg_rdl",
        "imported_6코너_섀도우_풋워크" to "ex_33841b88",
        "imported_싱글_레그_홉_앤_스틱" to "ex_314df428",
        "ex_bb728af2" to "ex_e2efd0fe",
        "ex_f892893e" to "ex_bd072cd",
        "ex_8354acd" to "vipr_chop",
        "vipr_shovel_scoop" to "vipr_rotational_lift",
        "ex_26ac0c19" to "med_ball_overhead_slam",
        "ex_c821775c" to "ex_a12de111",
        "landmine_rainbow" to "landmine_rotation",
        "ex_5715d6ca" to "single_leg_hip_bridge",
        "ex_d634055c" to "single_leg_hip_bridge",
        "med_ball_side_throw" to "medicine_ball_rotational_throw",
        "medicine_ball_side_slam" to "med_ball_rotational_slam",
        "ex_d79824d2" to "half_kneeling_single_arm_kettlebell_press",
        "ex_e0759156" to "half_kneeling_single_arm_kettlebell_press",
        "ex_9523db82" to "dumbbell_romanian_deadlift",
        "imported_래터럴_바운드" to "lateral_bound_continuous"
    )
    private val halfKneelingSplitKeys = setOf("ex_8380d7fe", "ex_8e1b313e", "ex_66e8c8c2")
    private val legacyPlaceholderKeys = setOf(
        "imported_csv_복원_계획",
        "imported_csv_복원_근력운동",
        "imported_csv_복원_기능성운동",
        "imported_csv_복원_스포츠"
    )
    private val reviewOnlyKeys = legacyPlaceholderKeys + "ex_e3487166"

    fun isLegacyPlaceholderKey(stableKey: String): Boolean =
        stableKey.trim().lowercase() in legacyPlaceholderKeys

    fun resolve(oldId: Long, oldStableKey: String, equipment: String, equipmentTags: String): MigrationKeyResolution {
        val key = oldStableKey.trim().lowercase()
        directMappings[key]?.let { return MigrationKeyResolution(it) }
        if (key in halfKneelingSplitKeys) {
            val tool = reliableTool(equipment, equipmentTags)
            return when (tool) {
                "DUMBBELL" -> MigrationKeyResolution("half_kneeling_single_arm_dumbbell_press")
                "KETTLEBELL" -> MigrationKeyResolution("half_kneeling_single_arm_kettlebell_press")
                else -> review(oldId, "AMBIGUOUS_EQUIPMENT_SPLIT")
            }
        }
        if (key == "ex_d2bb7946") {
            return when (reliableTool(equipment, equipmentTags)) {
                "BARBELL" -> MigrationKeyResolution("barbell_romanian_deadlift")
                "DUMBBELL" -> MigrationKeyResolution("dumbbell_romanian_deadlift")
                else -> review(oldId, "AMBIGUOUS_EQUIPMENT_SPLIT")
            }
        }
        if (key.isBlank()) return review(oldId, "BLANK_STABLE_KEY")
        if (key in reviewOnlyKeys) return review(oldId, "DELETED_OR_MANUAL_EXERCISE")
        return MigrationKeyResolution(key)
    }

    private fun reliableTool(equipment: String, equipmentTags: String): String? {
        val tokens = "$equipment|$equipmentTags"
            .uppercase()
            .split('|', ',', '/', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        val matched = buildSet {
            if (tokens.any { it == "DUMBBELL" || it == "덤벨" }) add("DUMBBELL")
            if (tokens.any { it == "KETTLEBELL" || it == "케틀벨" }) add("KETTLEBELL")
            if (tokens.any { it == "BARBELL" || it == "바벨" }) add("BARBELL")
        }
        return matched.singleOrNull()
    }

    private fun review(oldId: Long, issueCode: String) =
        MigrationKeyResolution("migration_review_$oldId", issueCode)
}

private fun createIdentityIssueTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `exercise_identity_migration_issues` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `issueCode` TEXT NOT NULL,
            `sourceExerciseId` INTEGER,
            `sourceStableKey` TEXT NOT NULL,
            `canonicalStableKey` TEXT NOT NULL,
            `entityType` TEXT NOT NULL,
            `entityRowId` INTEGER,
            `message` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL
        )
        """.trimIndent()
    )
    db.execSQL("CREATE INDEX `index_exercise_identity_migration_issues_issueCode` ON `exercise_identity_migration_issues` (`issueCode`)")
    db.execSQL("CREATE INDEX `index_exercise_identity_migration_issues_sourceExerciseId` ON `exercise_identity_migration_issues` (`sourceExerciseId`)")
}

private fun migrateRuntimeMetadataStableKeys(db: SupportSQLiteDatabase) {
    db.query(
        """
        SELECT DISTINCT `oldStableKey`, `canonicalStableKey`
        FROM `exercise_key_migration`
        WHERE `oldStableKey` <> '' AND `oldStableKey` <> `canonicalStableKey`
        ORDER BY `oldStableKey`
        """.trimIndent()
    ).use { cursor ->
        val sourceIndex = cursor.getColumnIndexOrThrow("oldStableKey")
        val targetIndex = cursor.getColumnIndexOrThrow("canonicalStableKey")
        while (cursor.moveToNext()) {
            val source = cursor.getString(sourceIndex)
            val target = cursor.getString(targetIndex)
            if (!db.exists("runtime_exercise_metadata", "stableKey", source)) continue
            if (db.exists("runtime_exercise_metadata", "stableKey", target)) {
                db.execSQL("DELETE FROM `runtime_exercise_metadata` WHERE `stableKey` = ?", arrayOf(source))
                db.execSQL(
                    """
                    INSERT INTO `exercise_identity_migration_issues` (
                        `issueCode`, `sourceExerciseId`, `sourceStableKey`, `canonicalStableKey`,
                        `entityType`, `entityRowId`, `message`, `createdAt`
                    ) VALUES ('RUNTIME_METADATA_OVERRIDE_COLLISION', NULL, ?, ?, 'RUNTIME_METADATA', NULL, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        source,
                        target,
                        "Canonical runtime metadata override already existed; it was retained.",
                        System.currentTimeMillis()
                    )
                )
            } else {
                db.execSQL(
                    "UPDATE `runtime_exercise_metadata` SET `stableKey` = ? WHERE `stableKey` = ?",
                    arrayOf(target, source)
                )
            }
        }
    }
}

private fun SupportSQLiteDatabase.exists(table: String, column: String, value: String): Boolean =
    query("SELECT 1 FROM `$table` WHERE `$column` = ? LIMIT 1", arrayOf(value)).use { it.moveToFirst() }

private fun createExerciseTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `exercises_new` (
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
            `trainingRole` TEXT NOT NULL, `stabilityRoles` TEXT NOT NULL,
            `sportTransferDirect` TEXT NOT NULL, `sportTransferSupportive` TEXT NOT NULL,
            `badmintonTransferRoles` TEXT NOT NULL, `fatigueCategories` TEXT NOT NULL,
            `adaptiveBaselineGroups` TEXT NOT NULL, `accessoryRoles` TEXT NOT NULL,
            `loadProfile` TEXT NOT NULL, `recoveryDecayProfile` TEXT NOT NULL,
            `systemicLoadWeight` REAL NOT NULL, `neuralHeavyWeight` REAL NOT NULL,
            `neuralSpeedWeight` REAL NOT NULL, `localLoadWeight` REAL NOT NULL,
            `decelerationWeight` REAL NOT NULL, `elasticSscWeight` REAL NOT NULL,
            `rotationPowerWeight` REAL NOT NULL, `antiRotationWeight` REAL NOT NULL,
            `overheadSwingWeight` REAL NOT NULL, `gripLoadWeight` REAL NOT NULL,
            `progressMetricType` TEXT NOT NULL, `strengthProgressionGroup` TEXT NOT NULL,
            `hypertrophyVolumeGroup` TEXT NOT NULL, `mainLiftGroup` TEXT NOT NULL,
            `accessoryContributionGroup` TEXT NOT NULL, `estimated1RmEligible` INTEGER NOT NULL,
            `volumeLoadEligible` INTEGER NOT NULL, `badmintonTransferStrength` TEXT NOT NULL,
            `courtMovementTypes` TEXT NOT NULL, `badmintonSkillTargets` TEXT NOT NULL,
            `jointStressTags` TEXT NOT NULL, `stabilityDemandLevel` TEXT NOT NULL,
            `mobilityDemandLevel` TEXT NOT NULL, `balanceContributionTags` TEXT NOT NULL,
            `analysisEligibility` TEXT NOT NULL, `activityKind` TEXT NOT NULL,
            `planningEligibility` TEXT NOT NULL, `metadataConfidence` TEXT NOT NULL,
            `imageAssetName` TEXT NOT NULL, `isActive` INTEGER NOT NULL,
            `archivedAt` INTEGER, `isCustom` INTEGER NOT NULL, `needsReview` INTEGER NOT NULL,
            PRIMARY KEY(`stableKey`)
        )
        """.trimIndent()
    )
}

private fun createWorkoutEntriesTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `workout_entries_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL,
            `exerciseStableKey` TEXT NOT NULL, `exerciseName` TEXT NOT NULL,
            `category` TEXT NOT NULL, `restSeconds` INTEGER NOT NULL, `notes` TEXT NOT NULL,
            `rpe` REAL, `maxReps` INTEGER, `createdAt` INTEGER NOT NULL,
            `completedAt` INTEGER, `displayOrder` INTEGER NOT NULL,
            `firstConfirmedAt` INTEGER, `performedAt` INTEGER,
            FOREIGN KEY(`exerciseStableKey`) REFERENCES `exercises`(`stableKey`)
                ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """.trimIndent()
    )
}

private fun createProgramItemsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `training_program_items_new` (
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
            FOREIGN KEY(`exerciseStableKey`) REFERENCES `exercises`(`stableKey`)
                ON UPDATE NO ACTION ON DELETE NO ACTION
        )
        """.trimIndent()
    )
}

private fun insertUnresolvedExercise(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        INSERT OR IGNORE INTO `exercises_new` (
            `stableKey`, `name`, `category`, `detail1`, `detail2`, `mode`, `description`,
            `defaultRestSeconds`, `familyId`, `familyName`, `familyRole`,
            `familyE1rmMultiplier`, `movementPattern`, `movementCategory`, `primaryMuscles`,
            `secondaryMuscles`, `equipment`, `equipmentTags`, `compoundType`, `forceType`,
            `bodyRegion`, `plane`, `laterality`, `axialLoadLevel`, `trainingRole`,
            `stabilityRoles`, `sportTransferDirect`, `sportTransferSupportive`,
            `badmintonTransferRoles`, `fatigueCategories`, `adaptiveBaselineGroups`,
            `accessoryRoles`, `loadProfile`, `recoveryDecayProfile`, `systemicLoadWeight`,
            `neuralHeavyWeight`, `neuralSpeedWeight`, `localLoadWeight`, `decelerationWeight`,
            `elasticSscWeight`, `rotationPowerWeight`, `antiRotationWeight`,
            `overheadSwingWeight`, `gripLoadWeight`, `progressMetricType`,
            `strengthProgressionGroup`, `hypertrophyVolumeGroup`, `mainLiftGroup`,
            `accessoryContributionGroup`, `estimated1RmEligible`, `volumeLoadEligible`,
            `badmintonTransferStrength`, `courtMovementTypes`, `badmintonSkillTargets`,
            `jointStressTags`, `stabilityDemandLevel`, `mobilityDemandLevel`,
            `balanceContributionTags`, `analysisEligibility`, `activityKind`,
            `planningEligibility`, `metadataConfidence`, `imageAssetName`, `isActive`,
            `archivedAt`, `isCustom`, `needsReview`
        ) VALUES (
            '${ExerciseMigrationKeyPolicy.UNRESOLVED_REFERENCE_KEY}',
            '확인 필요한 이전 운동', '기타', '', '', '', '이전 데이터의 운동 참조를 확인해야 합니다.',
            60, '', '', '', 1.0, '', '', '', '', '', '', '', '', '', '', '', '', '',
            '', '', '', '', '', '', '', '', '', 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, '', '', '', '', '', 0, 0, '', '', '', '', '', '', '', '',
            '', '', 'UNKNOWN', '', 0, NULL, 1, 1
        )
        """.trimIndent()
    )
}

private fun insertMigrationIssue(db: SupportSQLiteDatabase, issue: MigrationKeyIssue) {
    db.execSQL(
        """
        INSERT INTO `exercise_identity_migration_issues` (
            `issueCode`, `sourceExerciseId`, `sourceStableKey`, `canonicalStableKey`,
            `entityType`, `entityRowId`, `message`, `createdAt`
        ) VALUES (?, ?, ?, ?, 'EXERCISE', ?, ?, ?)
        """.trimIndent(),
        arrayOf(
            issue.issueCode,
            issue.oldId,
            issue.oldStableKey,
            issue.canonicalStableKey,
            issue.oldId,
            "Exercise identity requires review; no ambiguous destination was guessed.",
            System.currentTimeMillis()
        )
    )
}

private fun insertDanglingReferenceIssues(db: SupportSQLiteDatabase, table: String, entityType: String) {
    db.execSQL(
        """
        INSERT INTO `exercise_identity_migration_issues` (
            `issueCode`, `sourceExerciseId`, `sourceStableKey`, `canonicalStableKey`,
            `entityType`, `entityRowId`, `message`, `createdAt`
        )
        SELECT
            'DANGLING_EXERCISE_ID', source.`exerciseId`, '',
            '${ExerciseMigrationKeyPolicy.UNRESOLVED_REFERENCE_KEY}', '$entityType', source.`id`,
            'Numeric exercise reference did not resolve; preserved under an unresolved review key.',
            ${System.currentTimeMillis()}
        FROM `$table` AS source
        LEFT JOIN `exercise_key_migration` AS migration ON migration.`oldId` = source.`exerciseId`
        WHERE migration.`oldId` IS NULL
        """.trimIndent()
    )
}

private fun requireParity(db: SupportSQLiteDatabase, oldTable: String, newTable: String) {
    val oldCount = db.longForQuery("SELECT COUNT(*) FROM `$oldTable`")
    val newCount = db.longForQuery("SELECT COUNT(*) FROM `$newTable`")
    check(oldCount == newCount) { "Migration row-count mismatch for $oldTable: $oldCount != $newCount" }
}

private fun SupportSQLiteDatabase.longForQuery(sql: String): Long =
    query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
