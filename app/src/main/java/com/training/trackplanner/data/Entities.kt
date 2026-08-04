package com.training.trackplanner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val stableKey: String,
    val name: String,
    val category: String,
    val detail1: String = "",
    val detail2: String = "",
    val mode: String = "",
    val description: String = "",
    val defaultRestSeconds: Int = 60,
    val familyId: String = "",
    val familyName: String = "",
    val familyRole: String = "",
    val familyE1rmMultiplier: Double = 1.0,
    val movementPattern: String = "",
    val movementCategory: String = "",
    val primaryMuscles: String = "",
    val secondaryMuscles: String = "",
    val equipment: String = "",
    val equipmentTags: String = "",
    val compoundType: String = "",
    val forceType: String = "",
    val bodyRegion: String = "",
    val plane: String = "",
    val laterality: String = "",
    val axialLoadLevel: String = "",
    val stabilityRoles: String = "",
    val sportTransferDirect: String = "",
    val sportTransferSupportive: String = "",
    val badmintonTransferRoles: String = "",
    val fatigueCategories: String = "",
    val adaptiveBaselineGroups: String = "",
    val accessoryRoles: String = "",
    val loadProfile: String = "",
    val recoveryDecayProfile: String = "",
    val systemicLoadWeight: Double = 0.0,
    val neuralHeavyWeight: Double = 0.0,
    val neuralSpeedWeight: Double = 0.0,
    val localLoadWeight: Double = 0.0,
    val decelerationWeight: Double = 0.0,
    val elasticSscWeight: Double = 0.0,
    val rotationPowerWeight: Double = 0.0,
    val antiRotationWeight: Double = 0.0,
    val overheadSwingWeight: Double = 0.0,
    val gripLoadWeight: Double = 0.0,
    val progressMetricType: String = "",
    val strengthProgressionGroup: String = "",
    val hypertrophyVolumeGroup: String = "",
    val mainLiftGroup: String = "",
    val accessoryContributionGroup: String = "",
    val estimated1RmEligible: Boolean = false,
    val volumeLoadEligible: Boolean = false,
    val badmintonTransferStrength: String = "",
    val courtMovementTypes: String = "",
    val badmintonSkillTargets: String = "",
    val jointStressTags: String = "",
    val stabilityDemandLevel: String = "",
    val mobilityDemandLevel: String = "",
    val balanceContributionTags: String = "",
    val analysisEligibility: String = "",
    val activityKind: String = "",
    val planningEligibility: String = "",
    val metadataConfidence: String = "UNKNOWN",
    val imageAssetName: String = "",
    val isActive: Boolean = true,
    val archivedAt: Long? = null,
    val isCustom: Boolean = false,
    val needsReview: Boolean = false
)

@Entity(
    tableName = "exercise_training_role_relations",
    primaryKeys = ["exerciseStableKey", "trainingRoleCode"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["stableKey"],
            childColumns = ["exerciseStableKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseStableKey"])]
)
data class ExerciseTrainingRoleRelation(
    val exerciseStableKey: String,
    val trainingRoleCode: String,
    val provenance: String,
    val reviewStatus: String,
    val notes: String = ""
)

@Entity(
    tableName = "exercise_program_slot_capability_relations",
    primaryKeys = ["exerciseStableKey", "capabilityCode"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["stableKey"],
            childColumns = ["exerciseStableKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseStableKey"])]
)
data class ExerciseProgramSlotCapabilityRelation(
    val exerciseStableKey: String,
    val capabilityCode: String,
    val provenance: String,
    val reviewStatus: String,
    val notes: String = ""
)

@Entity(
    tableName = "workout_entries",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["stableKey"],
            childColumns = ["exerciseStableKey"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["exerciseStableKey"])]
)
data class WorkoutEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val exerciseStableKey: String,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int = 60,
    val notes: String = "",
    val rpe: Double? = null,
    val maxReps: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val displayOrder: Int = 0,
    val firstConfirmedAt: Long? = null,
    val performedAt: Long? = null
)

@Entity(tableName = "workout_sets")
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val setIndex: Int,
    val reps: Int = 0,
    val weightKg: Double = 0.0,
    val seconds: Int = 0,
    val confirmed: Boolean = false,
    val manualWeight: Boolean = false,
    val rpe: Double? = null,
    val restSecondsOverride: Int? = null
)

@Entity(tableName = "daily_metrics")
data class DailyMetric(
    @PrimaryKey val date: String,
    val sleepHours: Double? = null,
    val bodyWeightKg: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_check_ins")
data class DailyCheckIn(
    @PrimaryKey val date: String,
    val sleepHours: Double? = null,
    val bodyWeightKg: Double? = null,
    val overallFatigue: Int? = null,
    val lowerBodyFatigue: Int? = null,
    val jointTendonDiscomfort: Int? = null,
    val focusMotivation: Int? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun validated(): DailyCheckIn {
        require(runCatching { java.time.LocalDate.parse(date) }.isSuccess) {
            "date must use ISO-8601 local date format."
        }
        require(sleepHours == null || sleepHours in 0.0..24.0) {
            "sleepHours must be between 0 and 24."
        }
        require(bodyWeightKg == null || (bodyWeightKg.isFinite() && bodyWeightKg > 0.0)) {
            "bodyWeightKg must be finite and greater than zero."
        }
        listOf(overallFatigue, lowerBodyFatigue, jointTendonDiscomfort, focusMotivation)
            .filterNotNull()
            .forEach { value -> require(value in 1..5) { "Check-in scores must be between 1 and 5." } }
        return this
    }
}

@Entity(
    tableName = "smash_speed_records",
    indices = [Index(value = ["date"])]
)
data class SmashSpeedRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val speedKmh: Double,
    val attemptIndex: Int? = null,
    val source: String = "external_app",
    val note: String? = null,
    val parentWorkoutEntryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun validated(): SmashSpeedRecord {
        require(runCatching { java.time.LocalDate.parse(date) }.isSuccess) {
            "date must use ISO-8601 local date format."
        }
        require(speedKmh in 1.0..500.0) {
            "speedKmh must be between 1 and 500."
        }
        require(attemptIndex == null || attemptIndex > 0) {
            "attemptIndex must be positive."
        }
        return this
    }
}

@Entity(
    tableName = "training_programs",
    indices = [Index(value = ["stableKey"], unique = true)]
)
data class TrainingProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableKey: String = ProgramStableKeyPolicy.newUserKey(),
    val name: String,
    val durationDays: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val goal: String = "",
    val weeklyTrainingDays: Int = 0,
    val sessionMinutes: Int = 0,
    val availableEquipment: String = "",
    val excludedExerciseText: String = "",
    val badmintonTransferRatio: Double = 0.4,
    val sportStrengthRatio: String = "AUTO",
    val periodizationType: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "training_program_tombstones")
data class TrainingProgramTombstone(
    @PrimaryKey val programStableKey: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val seedVersion: Int? = null
)

@Entity(
    tableName = "training_program_items",
    foreignKeys = [
        ForeignKey(
            entity = TrainingProgram::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["stableKey"],
            childColumns = ["exerciseStableKey"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["programId"]),
        Index(value = ["exerciseStableKey"])
    ]
)
data class TrainingProgramItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val exerciseStableKey: String,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int = 60,
    val prescription: String = "",
    val setCount: Int = 1,
    val reps: Int = 0,
    val weightKg: Double = 0.0,
    val seconds: Int = 0,
    val trainingSlot: String? = null,
    val dayIntensity: String? = null,
    val weightSource: String? = null
)

@Entity(
    tableName = "training_program_item_sets",
    foreignKeys = [
        ForeignKey(
            entity = TrainingProgramItem::class,
            parentColumns = ["id"],
            childColumns = ["programItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["programItemId"]),
        Index(value = ["programItemId", "setIndex"], unique = true)
    ]
)
data class TrainingProgramItemSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programItemId: Long,
    val setIndex: Int,
    val reps: Int = 0,
    val weightKg: Double = 0.0,
    val seconds: Int = 0
)

@Entity(
    tableName = "exercise_identity_migration_issues",
    indices = [
        Index(value = ["issueCode"]),
        Index(value = ["sourceExerciseId"])
    ]
)
data class ExerciseIdentityMigrationIssue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issueCode: String,
    val sourceExerciseId: Long?,
    val sourceStableKey: String,
    val canonicalStableKey: String,
    val entityType: String,
    val entityRowId: Long?,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)

object ProgramStableKeyPolicy {
    const val USER_PREFIX = "user_program_"
    const val LEGACY_PREFIX = "legacy_program_"

    fun newUserKey(): String = "$USER_PREFIX${java.util.UUID.randomUUID()}"
}

@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "initial_user_profiles")
data class InitialUserProfile(
    @PrimaryKey val id: Int = 1,
    val bodyWeightKg: Double? = null,
    val heightCm: Double? = null,
    val birthYearOrAgeRange: String = "",
    val gender: String = "",
    val birthYear: Int? = null,
    val sex: String = "UNSPECIFIED",
    val strengthSessionsPerWeek: Double? = null,
    val strengthMinutesPerSession: Int? = null,
    val strengthAverageRpe: Double? = null,
    val badmintonSessionsPerWeek: Double? = null,
    val badmintonMinutesPerSession: Int? = null,
    val badmintonAverageRpe: Double? = null,
    val strengthTrainingAge: String = "",
    val badmintonTrainingAge: String = "",
    val strengthTrainingYears: Double? = null,
    val badmintonTrainingYears: Double? = null,
    val habitualTrainingIntensity: String? = null,
    val hadRecentTrainingBreak: Boolean = false,
    val breakWeeks: Int? = null,
    val breakDueToPain: Boolean = false,
    val trainingBreakCategory: String = "NONE",
    val trainingBreakReason: String = "NONE",
    val squatLevel: String = "",
    val deadliftLevel: String = "",
    val benchPressLevel: String = "",
    val pullUpLevel: String = "",
    val squatKg: Double? = null,
    val deadliftKg: Double? = null,
    val benchPressKg: Double? = null,
    val pullUpMaxReps: Int? = null,
    val pullUpAddedWeightKg: Double? = null,
    val typicalSleepHours: Double? = null,
    val usualSleepHours: Double? = null,
    val sleepQuality: Int? = null,
    val currentFatigue: Int? = null,
    val currentSoreness: Int? = null,
    val currentStress: Int? = null,
    val currentMood: Int? = null,
    val currentCondition: Int? = null,
    val painAreas: String = "",
    val painAreaTags: String = "NONE",
    val avoidedMovements: String = "",
    val avoidMovementTags: String = "NONE",
    val goals: String = "",
    val primaryGoal: String = "MIXED",
    val secondaryGoalTags: String = "",
    val freeNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
