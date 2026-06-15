package com.training.trackplanner.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["stableKey"], unique = true)]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val stableKey: String,
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
    val trainingRole: String = "",
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
    val metadataConfidence: String = "UNKNOWN"
)

@Entity(tableName = "workout_entries")
data class WorkoutEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val exerciseId: Long,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int = 60,
    val notes: String = "",
    val rpe: Double? = null,
    val maxReps: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
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

@Entity(tableName = "training_programs")
data class TrainingProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationDays: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "training_program_items")
data class TrainingProgramItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val exerciseId: Long,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int = 60,
    val prescription: String = "",
    val setCount: Int = 1,
    val reps: Int = 0,
    val weightKg: Double = 0.0,
    val seconds: Int = 0
)

@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
