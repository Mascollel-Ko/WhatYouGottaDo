package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class TissueContinuousAxisScoringTest {
    @Test
    fun piecewiseInterpolationIsMonotoneContinuousAndDeterministic() {
        val rubric = rubric()
        assertEquals(BigDecimal("2"), TissueMetricContinuousScorer.score(rubric, BigDecimal("1.0"))!!.canonicalScore)
        assertEquals(BigDecimal("2.5"), TissueMetricContinuousScorer.score(rubric, BigDecimal("1.5"))!!.canonicalScore)
        assertEquals(
            TissueMetricContinuousScorer.score(rubric, BigDecimal("1.5")),
            TissueMetricContinuousScorer.score(rubric, BigDecimal("1.5"))
        )
        assertNull(TissueMetricContinuousScorer.score(rubric, null))
    }

    @Test
    fun extrapolationPreservesUnclampedScoreAndClampsBoundaries() {
        val low = TissueMetricContinuousScorer.score(rubric(), BigDecimal("-1"))!!
        val high = TissueMetricContinuousScorer.score(rubric(), BigDecimal("4"))!!
        assertTrue(low.unclampedScore < BigDecimal.ZERO)
        assertEquals(BigDecimal.ZERO, low.canonicalScore)
        assertEquals(TissueScoreRangeStatus.BOUNDARY_CLAMPED, low.rangeStatus)
        assertTrue(high.unclampedScore > BigDecimal("4"))
        assertEquals(BigDecimal("4"), high.canonicalScore)
        assertEquals(TissueScoreRangeStatus.BOUNDARY_CLAMPED, high.rangeStatus)
    }

    @Test
    fun invalidAnchorsAndFailureThresholdsAreRejected() {
        val inverted = rubric().copy(anchors = rubric().anchors.reversed())
        assertThrows(IllegalArgumentException::class.java) { TissueMetricContinuousScorer.validate(inverted) }
        assertThrows(IllegalArgumentException::class.java) {
            TissueMetricContinuousScorer.validate(rubric().copy(referenceExposureDomain = "Tissue failure threshold"))
        }
    }

    @Test
    fun exactAndDominantConditionsOutrankAggregationWithoutUsingScore() {
        val exact = candidate("exact", 1.0, "1.2", exact = true)
        val highScoreVariant = candidate("variant", 0.90, "4.0")
        assertEquals(TissueAxisDerivationType.EXACT_SINGLE_CONDITION, TissueAxisSourceSelector.select(listOf(exact, highScoreVariant), policy).derivationType)

        val dominant = TissueAxisSourceSelector.select(listOf(candidate("top", 0.90, "1.0"), candidate("second", 0.70, "4.0")), policy)
        assertEquals(TissueAxisDerivationType.CLOSEST_SINGLE_CONDITION, dominant.derivationType)
        assertEquals(BigDecimal("1.0"), dominant.selectedScore)
    }

    @Test
    fun aggregationIsSameAxisTraceableAndDependencyAdjusted() {
        val candidates = listOf(
            candidate("a", 0.80, "2.0", dependency = "shared", cohort = "cohort_a"),
            candidate("b", 0.80, "2.4", dependency = "shared", cohort = "cohort_a"),
            candidate("c", 0.80, "2.2", dependency = "independent", cohort = "cohort_c")
        )
        val decision = TissueAxisSourceSelector.select(candidates, policy)
        assertEquals(TissueAxisDerivationType.MULTI_SOURCE_AXIS_AGGREGATE, decision.derivationType)
        assertEquals(candidates.map { it.sourceConditionId }.toSet(), decision.includedSourceConditionIds)
        assertEquals(2, decision.statistics!!.independentCohortCount)
        assertEquals(2, decision.statistics.dependencyGroupCount)
        assertEquals(1.0, decision.aggregationWeights.values.sum(), 1e-9)
    }

    @Test
    fun sameConditionMetricsCountOnceAndLowSimilarityIsExcluded() {
        val primary = candidate("same", 0.80, "2.0", metricPriority = 1)
        val secondary = candidate("same", 0.80, "3.8", metricPriority = 2)
        val low = candidate("low", 0.59, "4.0")
        val other = candidate("other", 0.80, "2.2")
        val decision = TissueAxisSourceSelector.select(listOf(primary, secondary, low, other), policy)
        assertEquals(setOf("same", "other"), decision.includedSourceConditionIds)
        assertTrue("low" in decision.excludedSourceConditionIds)
        assertTrue(decision.selectedScore!!.toDouble() < 2.3)
    }

    @Test
    fun heterogeneityBlocksConflictingAverageAndCrossAxisInput() {
        val conflict = TissueAxisSourceSelector.select(listOf(candidate("low", 0.80, "1.0"), candidate("high", 0.80, "4.0")), policy)
        assertEquals(TissueAxisDerivationType.REQUIRES_HUMAN_REVIEW, conflict.derivationType)
        assertNull(conflict.selectedScore)
        assertEquals(TissueHeterogeneityStatus.REQUIRES_HUMAN_REVIEW, conflict.heterogeneityStatus)

        assertThrows(IllegalArgumentException::class.java) {
            TissueAxisSourceSelector.select(listOf(candidate("m", 0.80, "2.0"), candidate("t", 0.80, "2.0").copy(axis = TissueAxis.T)), policy)
        }
    }

    private fun rubric() = TissueMetricContinuousRubric(
        rubricId = "rubric", targetId = "ACHILLES_TENDON", axis = TissueAxis.M,
        physicalMetricType = "PEAK_TENDON_FORCE", normalizationBasis = "BODY_WEIGHT",
        measurementFamily = "VALIDATED_INTERNAL_MODEL", populationScope = "HEALTHY_ADULT",
        comparisonFamily = "ACHILLES_EXERCISE", conditionFamily = "LOWER_LIMB",
        interpolationType = TissueInterpolationType.PIECEWISE_LINEAR,
        anchors = listOf(
            anchor("a1", "1", "0.5"), anchor("a2", "2", "1.0"),
            anchor("a3", "3", "2.0"), anchor("a4", "4", "3.0")
        ),
        referenceExposureDomain = "Healthy training and sport exposure", version = "1.0.0"
    )

    private fun anchor(id: String, score: String, raw: String) = TissueMetricRubricAnchor(
        id, BigDecimal(score), BigDecimal(raw), setOf("condition_$id"), setOf("source_$id"),
        "Verified healthy exposure anchor", "bounded test", TissueAnchorDecisionType.HYBRID_LITERATURE_POLICY
    )

    private fun candidate(
        id: String,
        similarity: Double,
        score: String,
        exact: Boolean = false,
        dependency: String = id,
        cohort: String = id,
        metricPriority: Int = 1
    ) = TissueSourceConditionAxisCandidate(
        id, TissueAxis.M, BigDecimal(score), similarity, TissueMtcEvidenceRelation.VALIDATED_INTERNAL_MODEL,
        1.0, cohort, dependency, "PEAK_FORCE", exact, metricPriority
    )

    private val policy by lazy { TissueAxisAggregationPolicyParser.policies(asset("tissue_axis_aggregation_policy_v1.csv")).single() }
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
