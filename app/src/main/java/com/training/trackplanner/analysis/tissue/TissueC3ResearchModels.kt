package com.training.trackplanner.analysis.tissue

enum class TissueC3CandidateDisposition {
    RETAIN_UNCHANGED,
    RETAIN_WITH_NARROWER_CONDITION,
    RECLASSIFY_EXACTLY,
    SPLIT_INTO_MULTIPLE_CLAIMS,
    SOURCE_SPECIFIC_ONLY,
    REMOVE_UNSUPPORTED_BAND,
    BLOCK_INSUFFICIENT_METHOD_DETAIL,
    BLOCK_INCOMPARABLE_METRIC
}

enum class TissueC3ExerciseCorrespondence {
    EXACT_PROTOCOL,
    EXACT_EXERCISE_DIFFERENT_LOAD,
    CLOSE_VARIANT,
    MOVEMENT_FAMILY_TRANSFER,
    NO_SUPPORTED_CORRESPONDENCE
}

enum class TissueC3ResearchDecision {
    DRAFT_RUBRIC_CREATED,
    SOURCE_CLAIMS_CREATED_NO_RUBRIC,
    EVIDENCE_FOUND_BUT_NOT_COMPARABLE,
    CONFLICTING_EVIDENCE,
    BLOCKED_INSUFFICIENT_EVIDENCE,
    BLOCKED_NO_EXTERNAL_LOAD_MODEL,
    BLOCKED_NO_VALIDATED_PROXY,
    BLOCKED_MISSING_DOSE_INPUT,
    OUT_OF_SCOPE_AFTER_AUDIT
}

enum class TissueBiomechanicalAnomalyType {
    HIGH_IMPACT_LOADING_RATE_INVERSION,
    LOADED_UNLOADED_EQUALITY,
    DEEP_SHALLOW_SQUAT_INVERSION,
    COPIED_PEAK_IMPULSE_BAND,
    CROSS_TISSUE_RUBRIC,
    CROSS_MODEL_RUBRIC
}

data class TissueSourceMetricExtraction(
    val metricExtractionId: String,
    val sourceId: String,
    val testedExercise: String,
    val testedExerciseConditionId: String,
    val appStableKeys: Set<String>,
    val tissueId: String,
    val mechanicalLoadMode: TissueMechanicalLoadMode,
    val temporalMetric: TissueTemporalMetric,
    val measurementMetric: TissueMeasurementMetric,
    val normalizationBasis: TissueNormalizationBasis,
    val reportedValue: Double?,
    val reportedLowerBound: Double?,
    val reportedUpperBound: Double?,
    val reportedDispersionType: String,
    val reportedDispersionValue: Double?,
    val reportedUnit: String,
    val externalLoadCondition: String,
    val relativeLoadCondition: String,
    val romCondition: String,
    val velocityCondition: String,
    val lateralityCondition: String,
    val surfaceCondition: String,
    val landingCondition: String,
    val fatigueCondition: String,
    val measurementMethod: String,
    val modelAssumptions: String,
    val evidenceLocatorType: String,
    val evidenceLocator: String,
    val sourceAccessLevel: String,
    val extractionConfidence: String,
    val extractionLimitations: String
)

data class TissueC3CandidateDispositionRow(
    val candidateId: String,
    val oldDimension: TissueLoadDimension,
    val newMechanicalLoadMode: TissueMechanicalLoadMode,
    val newTemporalMetric: TissueTemporalMetric,
    val newMeasurementMetric: TissueMeasurementMetric,
    val disposition: TissueC3CandidateDisposition,
    val replacementCandidateIds: Set<String>,
    val preservedScientificPayload: String,
    val removedInterpretation: String,
    val blockingReason: String
)

data class TissueMultidimensionalClaimCandidate(
    val claimCandidateId: String,
    val researchBatchId: String,
    val metricExtractionId: String,
    val sourceId: String,
    val stableKey: String,
    val testedExercise: String,
    val exerciseCorrespondence: TissueC3ExerciseCorrespondence,
    val tissueId: String,
    val mechanicalLoadMode: TissueMechanicalLoadMode,
    val temporalMetric: TissueTemporalMetric,
    val measurementMetric: TissueMeasurementMetric,
    val normalizationBasis: TissueNormalizationBasis,
    val claimValue: Double?,
    val claimLowerBound: Double?,
    val claimUpperBound: Double?,
    val claimDispersionType: String,
    val claimDispersionValue: Double?,
    val claimUnit: String,
    val externalLoadCondition: String,
    val relativeLoadCondition: String,
    val romCondition: String,
    val velocityCondition: String,
    val lateralityCondition: String,
    val surfaceCondition: String,
    val landingCondition: String,
    val fatigueCondition: String,
    val measurementMethod: String,
    val modelAssumptions: String,
    val evidenceLocatorType: String,
    val evidenceLocator: String,
    val evidenceAccessLevel: String,
    val maximumDefensibleBand: TissueLoadBand?,
    val bandBasis: String,
    val claimSupportStatus: String,
    val confidenceLevel: String,
    val sourceVerificationStatus: TissueIdentifierVerificationStatus,
    val bibliographicMatchStatus: TissueBibliographicMatchStatus,
    val publicationIntegrityStatus: TissuePublicationIntegrityCheckStatus,
    val claimLimitations: String
)

data class TissueMultidimensionalRubric(
    val rubricId: String,
    val tissueId: String,
    val mechanicalLoadMode: TissueMechanicalLoadMode,
    val temporalMetric: TissueTemporalMetric,
    val measurementMetric: TissueMeasurementMetric,
    val normalizationBasis: TissueNormalizationBasis,
    val loadBand: TissueLoadBand,
    val metricLowerBound: Double?,
    val metricUpperBound: Double?,
    val lowerBoundInclusive: Boolean,
    val upperBoundInclusive: Boolean,
    val metricUnit: String,
    val anchorStableKeys: Set<String>,
    val anchorConditionIds: Set<String>,
    val anchorClaimCandidateIds: Set<String>,
    val sourceRefs: Set<String>,
    val assignmentMethod: String,
    val comparisonPopulation: String,
    val comparisonMethodFamily: String,
    val confidenceLevel: String,
    val rubricStatus: String,
    val rubricLimitations: String
)

data class TissueC3ResearchDecisionRow(
    val researchDecisionId: String,
    val researchBatchId: String,
    val tissueId: String,
    val mechanicalLoadMode: TissueMechanicalLoadMode,
    val temporalMetric: TissueTemporalMetric,
    val measurementMetric: TissueMeasurementMetric,
    val targetStableKeys: Set<String>,
    val searchQuery: String,
    val searchDate: String,
    val includedSourceIds: Set<String>,
    val excludedSourceIds: Set<String>,
    val decision: TissueC3ResearchDecision,
    val decisionReason: String,
    val remainingBlocker: String
) {
    val targetId: String get() = "$tissueId:${mechanicalLoadMode.name}:${temporalMetric.name}:${measurementMetric.name}"
}

data class TissueExerciseVariantCorrespondenceRow(
    val stableKey: String,
    val canonicalDisplayName: String,
    val movementVariant: String,
    val correspondence: TissueC3ExerciseCorrespondence,
    val sourceIds: Set<String>,
    val transferVariables: Set<String>,
    val transferBoundary: String
)

data class TissueBiomechanicalAnomaly(
    val anomalyType: TissueBiomechanicalAnomalyType,
    val affectedIds: Set<String>,
    val explanation: String
)
