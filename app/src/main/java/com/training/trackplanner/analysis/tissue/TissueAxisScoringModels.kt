package com.training.trackplanner.analysis.tissue

import java.math.BigDecimal

enum class TissueAxis { M, T, C, D, R, P }
enum class TissueAxisProfile { MECHANICAL, DYNAMIC_STABILIZATION }
enum class TissueScoreRangeStatus {
    WITHIN_CALIBRATION_RANGE,
    BELOW_CALIBRATION_RANGE,
    ABOVE_CALIBRATION_RANGE,
    BOUNDARY_CLAMPED,
    ANCHOR_ONLY_NO_INTERPOLATION
}
enum class TissueAxisDerivationType {
    EXACT_SINGLE_CONDITION,
    CLOSEST_SINGLE_CONDITION,
    WITHIN_CONDITION_PRIMARY_METRIC,
    WITHIN_CONDITION_MULTI_METRIC,
    MULTI_SOURCE_AXIS_AGGREGATE,
    HYBRID_LITERATURE_POLICY,
    CONDITION_ANCHOR_ONLY,
    ORDERING_RULE_ONLY,
    COMPLEX_LEVEL_ESTIMATE,
    MOVEMENT_FAMILY_FALLBACK,
    CONSERVATIVE_FALLBACK,
    REQUIRES_HUMAN_REVIEW
}
enum class TissueAxisAggregationMethod { NONE, PRIMARY_METRIC, COMPLEMENTARY_METRICS, WEIGHTED_MEAN }
enum class TissueAxisConfidenceBand { LOW, MODERATE, HIGH }

data class TissueAxisScoringPolicySpec(
    val policyId: String,
    val axis: TissueAxis,
    val profile: TissueAxisProfile,
    val minimumScore: BigDecimal,
    val maximumScore: BigDecimal,
    val persistedDecimalPlaces: Int,
    val unknownIsNull: Boolean,
    val crossAxisAggregationAllowed: Boolean,
    val confidenceAltersScore: Boolean,
    val version: String
)

data class TissueAxisEvidenceKey(
    val exerciseStableKey: String,
    val conditionSelector: String,
    val targetType: TissueMtcTargetType,
    val targetId: String,
    val axis: TissueAxis
)

data class TissueRawAxisObservation(
    val observationId: String,
    val key: TissueAxisEvidenceKey,
    val sourceId: String,
    val sourceConditionId: String,
    val metricExtractionId: String,
    val physicalMetricType: String,
    val rawValue: BigDecimal?,
    val rawUnit: String,
    val normalizationBasis: String,
    val measurementFamily: String,
    val populationScope: String,
    val contextTags: Set<String>,
    val contextComponentScores: Map<String, BigDecimal>,
    val rationale: String,
    val limitations: String,
    val evidenceRelation: TissueMtcEvidenceRelation,
    val cohortId: String,
    val datasetId: String,
    val dependencyGroupId: String
)

data class TissueAxisEvidencePool(
    val key: TissueAxisEvidenceKey,
    val observations: List<TissueRawAxisObservation>
) {
    init {
        require(observations.isNotEmpty()) { "Axis evidence pool is empty." }
        require(observations.all { it.key == key }) { "Axis evidence pool crosses exercise, condition, target, or axis." }
    }
}

data class TissueCanonicalAxisScore(
    val key: TissueAxisEvidenceKey,
    val canonicalScore: BigDecimal,
    val unclampedScore: BigDecimal,
    val scoreRangeStatus: TissueScoreRangeStatus,
    val derivationType: TissueAxisDerivationType,
    val aggregationMethod: TissueAxisAggregationMethod,
    val primarySourceConditionId: String,
    val includedSourceConditionIds: Set<String>,
    val supportingSourceConditionIds: Set<String>,
    val excludedSourceConditionIds: Set<String>,
    val metricTypes: Set<String>,
    val rawObservationIds: Set<String>,
    val structuredContextTags: Set<String>,
    val contextComponentScores: Map<String, BigDecimal>,
    val rationaleText: String,
    val limitationsText: String,
    val sourceCount: Int,
    val independentCohortCount: Int,
    val metricFamilyCount: Int,
    val provenanceTier: TissueMtcProvenanceTier,
    val confidenceScore: BigDecimal,
    val confidenceBand: TissueAxisConfidenceBand,
    val evidenceCoverage: String,
    val rubricIds: Set<String>,
    val similarityPolicyVersion: String,
    val aggregationPolicyVersion: String,
    val coefficientSetId: String
)

fun TissueAxis.profile(): TissueAxisProfile = when (this) {
    TissueAxis.M, TissueAxis.T, TissueAxis.C -> TissueAxisProfile.MECHANICAL
    TissueAxis.D, TissueAxis.R, TissueAxis.P -> TissueAxisProfile.DYNAMIC_STABILIZATION
}
