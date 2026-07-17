package com.training.trackplanner.analysis.tissue

enum class TissueCanonicalStatus(val severity: Int) {
    VERY_HIGH(5),
    HIGH(4),
    MODERATE(3),
    LOW(2),
    CALIBRATING(1),
    UNAVAILABLE(0)
}

enum class TissueSymptomOverride {
    NONE,
    CAUTION,
    BLOCK
}

data class TissueCalibrationResult(
    val status: TissueCanonicalStatus,
    val normalizedScore: Double?,
    val observationDays: Long,
    val symptomOverride: TissueSymptomOverride,
    val diagnostics: List<String>
)

data class TissueExerciseContribution(
    val exerciseStableKey: String,
    val exerciseName: String,
    val currentContribution: Double,
    val latestContributingEventTime: Long
)

data class TissueDimensionState(
    val key: TissueRcvLoadKey,
    val jointComplexStableKey: String,
    val tissueClass: String,
    val rawResidual: TissueResidualRange,
    val channelResiduals: Map<TissueRecoveryChannel, TissueResidualRange>,
    val status: TissueCanonicalStatus,
    val normalizedScore: Double?,
    val latestPositiveContributionTime: Long,
    val contributors: List<TissueExerciseContribution>,
    val timestampPrecisions: Set<TissueTimestampPrecision>,
    val evidenceGrades: Set<String>,
    val symptomOverride: TissueSymptomOverride,
    val diagnostics: List<String>
)

data class TissueJointComplexSummary(
    val jointComplexStableKey: String,
    val nameKo: String,
    val status: TissueCanonicalStatus,
    val displayScore: Double?,
    val highOrVeryHighChildCount: Int,
    val highestChild: TissueDimensionState?,
    val childStates: List<TissueDimensionState>,
    val contributors: List<TissueExerciseContribution>,
    val latestPositiveContributionTime: Long
)

data class TissueOfiSummary(
    val status: TissueCanonicalStatus,
    val topJointComplexes: List<TissueJointComplexSummary>
)

data class TissueCurrentState(
    val loadUnits: List<TissueDimensionState>,
    val jointComplexes: List<TissueJointComplexSummary>,
    val ofiSummary: TissueOfiSummary,
    val diagnostics: List<String>
)
