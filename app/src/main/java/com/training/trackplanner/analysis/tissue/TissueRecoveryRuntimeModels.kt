package com.training.trackplanner.analysis.tissue

enum class TissueTimestampPrecision {
    EXACT,
    DATE_ONLY_RANGE,
    MISSING
}

data class TissueEventTimeRange(
    val earliestEpochMillis: Long?,
    val latestEpochMillis: Long?,
    val precision: TissueTimestampPrecision,
    val diagnostics: List<String> = emptyList()
)

enum class TissueRecoveryChannel {
    FUNCTIONAL_CAPACITY,
    JOINT_PROTECTION_FUNCTION,
    FAST_MECHANICAL_STATE,
    BIOLOGICAL_REMODELING_ACTIVITY
}

data class TissueRcvLoadKey(
    val loadUnitStableKey: String,
    val loadDimension: String
)

enum class TissueEffortSource {
    SET_RPE,
    ENTRY_RPE,
    DOSE_ALREADY_EFFORT_ADJUSTED,
    UNRESOLVED
}

data class TissueEffortSelection(
    val value: Double?,
    val source: TissueEffortSource,
    val rejectedAvailableSources: List<TissueEffortSource> = emptyList(),
    val diagnostics: List<String> = emptyList()
)

data class TissueExposureEvent(
    val eventId: String,
    val recordId: Long,
    val exerciseStableKey: String,
    val exerciseName: String,
    val key: TissueRcvLoadKey,
    val jointComplexStableKey: String,
    val tissueClass: String,
    val initialExposure: Double,
    val rawDose: Double,
    val doseReference56d: Double,
    val normalizedDose: Double,
    val selectedEffort: TissueEffortSelection,
    val magnitudeM: Double,
    val rapidityS: Double,
    val contextModifier: Double,
    val mappingRoleWeight: Double,
    val curveIds: Map<TissueRecoveryChannel, String>,
    val performedTime: TissueEventTimeRange,
    val scoreVersion: String,
    val protocolVersion: String,
    val curveVersion: String,
    val evidenceGrade: String,
    val sourceRefs: List<String>,
    val diagnostics: List<String>
)

data class TissueEventLedgerResult(
    val events: List<TissueExposureEvent>,
    val diagnostics: List<String>
)

data class TissueResidualRange(
    val lower: Double,
    val upper: Double
)

data class TissueEventResidual(
    val event: TissueExposureEvent,
    val channelResiduals: Map<TissueRecoveryChannel, TissueResidualRange>,
    val currentResidualRange: TissueResidualRange,
    val biologicalActivityRange: TissueResidualRange?,
    val diagnostics: List<String>
)
