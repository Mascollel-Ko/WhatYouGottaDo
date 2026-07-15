package com.training.trackplanner.analysis.tissue

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

enum class TissueInterpolationType { PIECEWISE_LINEAR, PIECEWISE_LOG_LINEAR, MONOTONE_SPLINE }
enum class TissueAnchorDecisionType { HYBRID_LITERATURE_POLICY }
enum class TissueAxisExclusionReason {
    DIFFERENT_AXIS, INCOMPATIBLE_METRIC, INCOMPATIBLE_METHOD, INCOMPATIBLE_POPULATION,
    LOW_AXIS_SPECIFIC_SIMILARITY, DUPLICATE_CONDITION, DEPENDENT_COHORT_WEIGHT_CAPPED,
    DIFFERENT_EVENT_TYPE, DIFFERENT_MOVEMENT_PHASE, DIFFERENT_LATERALITY, DIFFERENT_LOAD_BAND,
    DIFFERENT_ROM, AVERAGE_NOT_PEAK, METRIC_AMBIGUOUS, FULL_TEXT_UNAVAILABLE,
    PUBLICATION_INTEGRITY_BLOCKER, HETEROGENEITY_REQUIRES_SUBGROUP, OUTSIDE_CALIBRATION_DOMAIN
}
enum class TissueDependencyType { INDEPENDENT, SAME_COHORT, OVERLAPPING_COHORT, SAME_DATASET, SECONDARY_ANALYSIS }
enum class TissueHeterogeneityStatus { PASSED, REQUIRES_HUMAN_REVIEW }

data class TissueMetricRubricAnchor(
    val anchorId: String,
    val score: BigDecimal,
    val rawValue: BigDecimal,
    val sourceConditionIds: Set<String>,
    val sourceIds: Set<String>,
    val rationale: String,
    val limitations: String,
    val decisionType: TissueAnchorDecisionType
)

data class TissueMetricContinuousRubric(
    val rubricId: String,
    val targetId: String,
    val axis: TissueAxis,
    val physicalMetricType: String,
    val normalizationBasis: String,
    val measurementFamily: String,
    val populationScope: String,
    val comparisonFamily: String,
    val conditionFamily: String,
    val interpolationType: TissueInterpolationType,
    val anchors: List<TissueMetricRubricAnchor>,
    val referenceExposureDomain: String,
    val version: String
)

data class TissueMetricAxisScore(
    val rubricId: String,
    val rawValue: BigDecimal,
    val unclampedScore: BigDecimal,
    val canonicalScore: BigDecimal,
    val rangeStatus: TissueScoreRangeStatus
) {
    fun persistedScore() = canonicalScore.setScale(4, RoundingMode.HALF_UP)
}

object TissueMetricContinuousScorer {
    private val mc = MathContext.DECIMAL128
    private val zero = BigDecimal.ZERO
    private val four = BigDecimal("4")

    fun validate(rubric: TissueMetricContinuousRubric) {
        require(rubric.interpolationType != TissueInterpolationType.MONOTONE_SPLINE || rubric.anchors.size >= 5) {
            "Monotone spline requires at least five anchors."
        }
        require("injury" !in rubric.referenceExposureDomain.lowercase() && "failure" !in rubric.referenceExposureDomain.lowercase()) {
            "Training anchors cannot use injury or failure thresholds."
        }
        require(rubric.anchors.map { it.anchorId }.distinct().size == rubric.anchors.size) { "Duplicate rubric anchor." }
        val ordered = rubric.anchors.sortedBy { it.rawValue }
        require(ordered == rubric.anchors) { "Rubric anchors must be sorted by raw value." }
        require(ordered.zipWithNext().all { (left, right) -> left.rawValue < right.rawValue && left.score < right.score }) {
            "Rubric anchors must be strictly monotone."
        }
        require(ordered.all { it.score in zero..four }) { "Rubric anchor score is outside 0..4." }
        require(ordered.filter { it.score.compareTo(zero) == 0 }.all { "negligible" in it.rationale.lowercase() || "not applicable" in it.rationale.lowercase() }) {
            "Score zero requires explicit negligible or not-applicable evidence."
        }
    }

    fun score(rubric: TissueMetricContinuousRubric, rawValue: BigDecimal?): TissueMetricAxisScore? {
        if (rawValue == null) return null
        validate(rubric)
        val anchors = rubric.anchors
        if (anchors.size == 1) {
            if (rawValue.compareTo(anchors.single().rawValue) != 0) return null
            return TissueMetricAxisScore(rubric.rubricId, rawValue, anchors.single().score, anchors.single().score, TissueScoreRangeStatus.ANCHOR_ONLY_NO_INTERPOLATION)
        }
        val rangeStatus = when {
            rawValue < anchors.first().rawValue -> TissueScoreRangeStatus.BELOW_CALIBRATION_RANGE
            rawValue > anchors.last().rawValue -> TissueScoreRangeStatus.ABOVE_CALIBRATION_RANGE
            else -> TissueScoreRangeStatus.WITHIN_CALIBRATION_RANGE
        }
        val segment = when {
            rawValue <= anchors.first().rawValue -> anchors[0] to anchors[1]
            rawValue >= anchors.last().rawValue -> anchors[anchors.lastIndex - 1] to anchors.last()
            else -> anchors.zipWithNext().first { (left, right) -> rawValue >= left.rawValue && rawValue <= right.rawValue }
        }
        val unclamped = interpolate(rubric.interpolationType, rawValue, segment.first, segment.second)
        val canonical = unclamped.coerceIn(zero, four)
        val finalStatus = if (canonical.compareTo(unclamped) != 0) TissueScoreRangeStatus.BOUNDARY_CLAMPED else rangeStatus
        return TissueMetricAxisScore(rubric.rubricId, rawValue, unclamped, canonical, finalStatus)
    }

    private fun interpolate(type: TissueInterpolationType, value: BigDecimal, lower: TissueMetricRubricAnchor, upper: TissueMetricRubricAnchor): BigDecimal {
        if (value.compareTo(lower.rawValue) == 0) return lower.score
        if (value.compareTo(upper.rawValue) == 0) return upper.score
        val fraction = when (type) {
            TissueInterpolationType.PIECEWISE_LINEAR -> value.subtract(lower.rawValue, mc).divide(upper.rawValue.subtract(lower.rawValue, mc), mc)
            TissueInterpolationType.PIECEWISE_LOG_LINEAR -> {
                require(value.signum() > 0 && lower.rawValue.signum() > 0 && upper.rawValue.signum() > 0) { "Log interpolation requires positive values." }
                BigDecimal((ln(value.toDouble()) - ln(lower.rawValue.toDouble())) / (ln(upper.rawValue.toDouble()) - ln(lower.rawValue.toDouble())), mc)
            }
            TissueInterpolationType.MONOTONE_SPLINE -> error("Spline evaluation is not enabled in C4B-1.")
        }
        return lower.score.add(upper.score.subtract(lower.score, mc).multiply(fraction, mc), mc)
    }
}

data class TissueSourceConditionAxisCandidate(
    val sourceConditionId: String,
    val axis: TissueAxis,
    val score: BigDecimal,
    val axisSpecificSimilarity: Double,
    val evidenceRelation: TissueMtcEvidenceRelation,
    val populationMatchWeight: Double,
    val cohortId: String,
    val dependencyGroupId: String,
    val metricFamily: String,
    val exactCondition: Boolean,
    val metricPriority: Int = Int.MAX_VALUE,
    val exclusionReason: TissueAxisExclusionReason? = null
)

data class TissueAxisAggregateStatistics(
    val arithmeticMean: Double,
    val weightedMean: Double,
    val median: Double,
    val weightedStandardDeviation: Double,
    val minimum: Double,
    val maximum: Double,
    val effectiveSourceCount: Double,
    val independentCohortCount: Int,
    val dependencyGroupCount: Int
)

data class TissueAxisSelectionDecision(
    val derivationType: TissueAxisDerivationType,
    val selectedScore: BigDecimal?,
    val primarySourceConditionId: String,
    val includedSourceConditionIds: Set<String>,
    val excludedSourceConditionIds: Set<String>,
    val aggregationWeights: Map<String, Double>,
    val statistics: TissueAxisAggregateStatistics?,
    val heterogeneityStatus: TissueHeterogeneityStatus,
    val rationale: String
)

data class TissueAxisAggregationPolicySpec(
    val policyId: String,
    val minimumSimilarity: Double,
    val dominantSourceThreshold: Double,
    val dominantSourceMargin: Double,
    val maximumWeightedSd: Double,
    val maximumScoreRange: Double,
    val maximumSubgroupMeanDifference: Double,
    val version: String
)

object TissueAxisSourceSelector {
    private val evidenceWeights = mapOf(
        TissueMtcEvidenceRelation.DIRECT_INTERNAL_MEASUREMENT to 1.00,
        TissueMtcEvidenceRelation.VALIDATED_INTERNAL_MODEL to 0.90,
        TissueMtcEvidenceRelation.VALIDATED_PROXY to 0.75,
        TissueMtcEvidenceRelation.FINITE_ELEMENT_MECHANISM to 0.65,
        TissueMtcEvidenceRelation.CADAVERIC_MECHANISM to 0.60
    )

    fun select(candidates: List<TissueSourceConditionAxisCandidate>, policy: TissueAxisAggregationPolicySpec): TissueAxisSelectionDecision {
        require(candidates.map { it.axis }.distinct().size <= 1) { "Cross-axis aggregation is forbidden." }
        val eligibleRows = candidates.filter { it.exclusionReason == null && it.axisSpecificSimilarity >= policy.minimumSimilarity && it.evidenceRelation in evidenceWeights }
        val eligible = eligibleRows.groupBy { it.sourceConditionId }.values.map { conditionMetrics -> conditionMetrics.minBy { it.metricPriority } }
        val excluded = candidates.filterNot { it in eligible }.map { it.sourceConditionId }.toSet()
        if (eligible.isEmpty()) return TissueAxisSelectionDecision(
            TissueAxisDerivationType.REQUIRES_HUMAN_REVIEW, null, "", emptySet(), excluded, emptyMap(), null,
            TissueHeterogeneityStatus.REQUIRES_HUMAN_REVIEW, "No eligible source condition."
        )
        eligible.filter { it.exactCondition }.maxByOrNull { it.axisSpecificSimilarity }?.let { exact ->
            return single(exact, TissueAxisDerivationType.EXACT_SINGLE_CONDITION, excluded, "Exact condition selected.")
        }
        val ordered = eligible.sortedByDescending { it.axisSpecificSimilarity }
        val top = ordered.first()
        val second = ordered.getOrNull(1)
        if (top.axisSpecificSimilarity >= policy.dominantSourceThreshold &&
            (second == null || top.axisSpecificSimilarity - second.axisSpecificSimilarity >= policy.dominantSourceMargin)
        ) return single(top, TissueAxisDerivationType.CLOSEST_SINGLE_CONDITION, excluded, "Dominant closest condition selected by versioned similarity thresholds.")
        return aggregate(eligible, excluded, policy)
    }

    private fun single(candidate: TissueSourceConditionAxisCandidate, type: TissueAxisDerivationType, excluded: Set<String>, rationale: String) =
        TissueAxisSelectionDecision(type, candidate.score, candidate.sourceConditionId, setOf(candidate.sourceConditionId), excluded, mapOf(candidate.sourceConditionId to 1.0), null, TissueHeterogeneityStatus.PASSED, rationale)

    private fun aggregate(
        candidates: List<TissueSourceConditionAxisCandidate>,
        excluded: Set<String>,
        policy: TissueAxisAggregationPolicySpec
    ): TissueAxisSelectionDecision {
        val groupSizes = candidates.groupingBy { it.dependencyGroupId.ifBlank { it.sourceConditionId } }.eachCount()
        val rawWeights = candidates.associate { candidate ->
            val dependencyGroup = candidate.dependencyGroupId.ifBlank { candidate.sourceConditionId }
            val dependencyAdjustment = 1.0 / groupSizes.getValue(dependencyGroup)
            candidate.sourceConditionId to candidate.axisSpecificSimilarity.pow(2) * evidenceWeights.getValue(candidate.evidenceRelation) * candidate.populationMatchWeight * dependencyAdjustment
        }
        val totalWeight = rawWeights.values.sum()
        val weights = rawWeights.mapValues { it.value / totalWeight }
        val values = candidates.map { it.score.toDouble() }
        val weightedMean = candidates.sumOf { it.score.toDouble() * weights.getValue(it.sourceConditionId) }
        val weightedSd = sqrt(candidates.sumOf { (it.score.toDouble() - weightedMean).pow(2) * weights.getValue(it.sourceConditionId) })
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        val subgroupMeans = candidates.groupBy { it.metricFamily }.values.map { group -> group.map { it.score.toDouble() }.average() }
        val subgroupDifference = if (subgroupMeans.isEmpty()) 0.0 else subgroupMeans.max() - subgroupMeans.min()
        val status = if (weightedSd <= policy.maximumWeightedSd && values.max() - values.min() <= policy.maximumScoreRange && subgroupDifference <= policy.maximumSubgroupMeanDifference) {
            TissueHeterogeneityStatus.PASSED
        } else TissueHeterogeneityStatus.REQUIRES_HUMAN_REVIEW
        val stats = TissueAxisAggregateStatistics(
            values.average(), weightedMean, median, weightedSd, values.min(), values.max(),
            1.0 / weights.values.sumOf { it * it }, candidates.map { it.cohortId.ifBlank { it.sourceConditionId } }.distinct().size,
            candidates.map { it.dependencyGroupId.ifBlank { it.sourceConditionId } }.distinct().size
        )
        return TissueAxisSelectionDecision(
            if (status == TissueHeterogeneityStatus.PASSED) TissueAxisDerivationType.MULTI_SOURCE_AXIS_AGGREGATE else TissueAxisDerivationType.REQUIRES_HUMAN_REVIEW,
            if (status == TissueHeterogeneityStatus.PASSED) BigDecimal(weightedMean, MathContext.DECIMAL128) else null,
            "", candidates.map { it.sourceConditionId }.toSet(), excluded, weights, stats, status,
            if (status == TissueHeterogeneityStatus.PASSED) "Compatible same-axis conditions aggregated." else "Heterogeneity requires subgroup review."
        )
    }
}

object TissueAxisAggregationPolicyParser {
    fun policies(csv: String) = TissueMetadataParser.table(csv).rows.map { row ->
        TissueAxisAggregationPolicySpec(
            row.required("policyId"), row.double("minimumSimilarity"), row.double("dominantSourceThreshold"),
            row.double("dominantSourceMargin"), row.double("maximumWeightedSd"), row.double("maximumScoreRange"),
            row.double("maximumSubgroupMeanDifference"), row.required("version")
        )
    }

    private fun Map<String, String>.required(name: String) = get(name).orEmpty().trim().also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.double(name: String) = required(name).toDouble()
}
