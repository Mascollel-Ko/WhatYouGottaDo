package com.training.trackplanner.analysis.contracts

enum class AnalysisTypeId {
    OFI,
    PROGRAM_GENERATION,
    MUSCLE_LOAD,
    BADMINTON_TRANSFER,
    CONNECTIVE_TISSUE
}

enum class AnalysisCapabilityStatus { ENABLED, DISABLED, INCOMPLETE }

enum class AnalysisSourceStatus {
    MIGRATED_CURRENT_BEHAVIOR,
    USER_PERSISTED_EXACT,
    UNRESOLVED
}

data class ExerciseAnalysisCapability(
    val exerciseStableKey: String,
    val analysisTypeId: AnalysisTypeId,
    val status: AnalysisCapabilityStatus,
    val confidence: Double,
    val sourceStatus: AnalysisSourceStatus,
    val version: String
)

enum class OfiAxisId {
    HIGH_FORCE_NEURAL,
    SYSTEMIC_MUSCULAR,
    LOCAL_MUSCULAR,
    HIGH_SPEED,
    REACTIVE
}

enum class OfiComparisonPurpose {
    WORKLOAD_BASELINE,
    LOCAL_REPEAT_DETECTION,
    STRENGTH_COMPARISON
}

data class ExerciseOfiDoseProfile(
    val exerciseStableKey: String,
    val doseBasisId: String,
    val recordInputPolicy: String
)

data class ExerciseOfiAxisContribution(
    val exerciseStableKey: String,
    val axisId: OfiAxisId,
    val coefficient: Double,
    val recoveryProfileId: String,
    val confidence: Double,
    val sourceStatus: AnalysisSourceStatus,
    val version: String
)

data class ExerciseOfiComparisonGroup(
    val exerciseStableKey: String,
    val comparisonPurpose: OfiComparisonPurpose,
    val groupId: String
)

data class ExerciseOfiGoldenSnapshot(
    val exerciseStableKey: String,
    val roundedAxisScores: Map<OfiAxisId, Int>,
    val overallFatigueIndex: Int,
    val readinessLabel: String,
    val baselineConfidence: String,
    val cautionReasons: List<String>
)

enum class ProgramCapabilityRole { PRIMARY, SECONDARY, LIMITED }

enum class ProgramRoleEligibility { ELIGIBLE, INELIGIBLE, INCOMPLETE }

data class ExerciseProgramSlotCapability(
    val exerciseStableKey: String,
    val programSlotId: String,
    val capabilityRole: ProgramCapabilityRole,
    val fitScore: Double,
    val confidence: Double,
    val sourceStatus: AnalysisSourceStatus
)

data class ExerciseProgramRoleEligibility(
    val exerciseStableKey: String,
    val roleId: String,
    val eligibility: ProgramRoleEligibility
)

data class ExerciseVariantGroup(
    val exerciseStableKey: String,
    val variantGroupId: String
)

data class ExerciseProgressionGroup(
    val exerciseStableKey: String,
    val progressionGroupId: String
)

enum class MuscleContributionRole { PRIMARY, SECONDARY, STABILIZER }

data class ExerciseMuscleContribution(
    val exerciseStableKey: String,
    val muscleAnalysisUnitId: String,
    val contributionRole: MuscleContributionRole,
    val contributionCoefficient: Double,
    val confidence: Double,
    val sourceStatus: AnalysisSourceStatus
)

enum class ContractBadmintonTransferLevel { DIRECT, SUPPORTIVE, GENERAL, LOW, NONE }

data class ExerciseBadmintonTransferPoint(
    val exerciseStableKey: String,
    val performanceDomainId: String,
    val transferLevel: ContractBadmintonTransferLevel,
    val contributionWeight: Double,
    val confidence: Double,
    val sourceStatus: AnalysisSourceStatus
)

data class ExercisePhysicalQualityPoint(
    val exerciseStableKey: String,
    val physicalQualityId: String,
    val contributionMagnitude: Double,
    val confidence: Double,
    val sourceStatus: AnalysisSourceStatus
)

data class ExerciseMovementPatternMembership(
    val exerciseStableKey: String,
    val movementPatternId: String
)

data class ExerciseJointAction(
    val exerciseStableKey: String,
    val jointActionId: String
)

data class ExerciseBodyRegionMembership(
    val exerciseStableKey: String,
    val bodyRegionId: String
)

data class ExerciseModality(
    val exerciseStableKey: String,
    val modalityId: String
)

data class TrainingGoalMembership(
    val exerciseStableKey: String,
    val trainingGoalId: String
)

data class ExerciseAnalysisRelations(
    val exerciseStableKey: String,
    val capabilities: List<ExerciseAnalysisCapability>,
    val ofiDoseProfile: ExerciseOfiDoseProfile?,
    val ofiAxisContributions: List<ExerciseOfiAxisContribution>,
    val ofiComparisonGroups: List<ExerciseOfiComparisonGroup>,
    val ofiGoldenSnapshot: ExerciseOfiGoldenSnapshot?,
    val programSlotCapabilities: List<ExerciseProgramSlotCapability>,
    val programRoleEligibility: List<ExerciseProgramRoleEligibility>,
    val variantGroups: List<ExerciseVariantGroup>,
    val progressionGroups: List<ExerciseProgressionGroup>,
    val muscleContributions: List<ExerciseMuscleContribution>,
    val badmintonTransferPoints: List<ExerciseBadmintonTransferPoint>,
    val badmintonFatigueCost: String?,
    val physicalQualityPoints: List<ExercisePhysicalQualityPoint>,
    val movementPatterns: List<ExerciseMovementPatternMembership>,
    val jointActions: List<ExerciseJointAction>,
    val bodyRegions: List<ExerciseBodyRegionMembership>,
    val modalities: List<ExerciseModality>,
    val trainingGoals: List<TrainingGoalMembership>
)

data class AnalysisContractDiff(
    val exerciseStableKey: String,
    val analysisTypeId: AnalysisTypeId,
    val field: String,
    val oldValue: String,
    val newValue: String
)
