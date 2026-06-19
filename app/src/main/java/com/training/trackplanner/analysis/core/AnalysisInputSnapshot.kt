package com.training.trackplanner.analysis.core

import java.time.LocalDate

typealias ExerciseId = Long

data class AnalysisInputSnapshot(
    val today: LocalDate,
    val completedEntriesUntilToday: List<AnalysisEntry>,
    val plannedEntriesFromTomorrow: List<AnalysisEntry>,
    val conditionRecordsUntilToday: List<AnalysisConditionRecord>,
    val exerciseMetadataMap: Map<ExerciseId, AnalysisExerciseMetadata>,
    val recentWindow: AnalysisWindow,
    val futureWindow: AnalysisWindow,
    val windows: AnalysisWindows
)

data class AnalysisEntry(
    val entryId: Long,
    val date: LocalDate,
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val rpe: Double?,
    val maxReps: Int?,
    val sets: List<AnalysisSet>
)

data class AnalysisSet(
    val setId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val confirmed: Boolean,
    val manualWeight: Boolean,
    val rpe: Double?,
    val restSecondsOverride: Int?
)

data class AnalysisConditionRecord(
    val date: LocalDate,
    val sleepHours: Double?,
    val bodyWeightKg: Double?
)

data class AnalysisExerciseMetadata(
    val exerciseId: ExerciseId,
    val stableKey: String,
    val activityKind: String,
    val planningEligibility: String,
    val category: String,
    val movementPattern: String,
    val movementCategory: String,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val equipment: String,
    val equipmentTags: String,
    val compoundType: String,
    val forceType: String,
    val bodyRegion: String,
    val plane: String,
    val laterality: String,
    val axialLoadLevel: String,
    val trainingRole: String,
    val stabilityRoles: String,
    val sportTransferDirect: String,
    val sportTransferSupportive: String,
    val badmintonTransferRoles: String,
    val fatigueCategories: String,
    val adaptiveBaselineGroups: String,
    val accessoryRoles: String,
    val loadProfile: String,
    val recoveryDecayProfile: String,
    val systemicLoadWeight: Double,
    val neuralHeavyWeight: Double,
    val neuralSpeedWeight: Double,
    val localLoadWeight: Double,
    val decelerationWeight: Double,
    val elasticSscWeight: Double,
    val rotationPowerWeight: Double,
    val antiRotationWeight: Double,
    val overheadSwingWeight: Double,
    val gripLoadWeight: Double,
    val progressMetricType: String,
    val strengthProgressionGroup: String,
    val hypertrophyVolumeGroup: String,
    val mainLiftGroup: String,
    val accessoryContributionGroup: String,
    val estimated1RmEligible: Boolean,
    val volumeLoadEligible: Boolean,
    val badmintonTransferStrength: String,
    val courtMovementTypes: String,
    val badmintonSkillTargets: String,
    val jointStressTags: String,
    val stabilityDemandLevel: String,
    val mobilityDemandLevel: String,
    val balanceContributionTags: String,
    val analysisEligibility: String,
    val metadataConfidence: String,
    val movementFamily: String = "",
    val movementSubtype: String = "",
    val programSlot: String = "",
    val redundancyGroup: String = "",
    val primaryStressProfile: String = "",
    val secondaryStressTags: String = "",
    val tendonStressTags: String = "",
    val ligamentJointStabilityStressTags: String = "",
    val jointImpactStressTags: String = "",
    val cognitiveStressTags: String = "",
    val sportContextTags: String = "",
    val stressMagnitudeHint: String = "",
    val neuromuscularStressLevel: String = "",
    val systemicMuscularStressLevel: String = "",
    val localMuscularStressLevel: String = "",
    val jointTendonImpactStressLevel: String = "",
    val movementFocusDemandLevel: String = "",
    val recoveryDurationClass: String = "",
    val canonicalBadmintonTransferLevel: String = "",
    val canonicalBadmintonTransferType: String = "",
    val canonicalBadmintonSkillTargets: String = "",
    val badmintonPhysicalQualities: String = "",
    val transferConfidence: String = "",
    val sourceConfidenceLevel: String = "",
    val finalSourceStatus: String = ""
)
