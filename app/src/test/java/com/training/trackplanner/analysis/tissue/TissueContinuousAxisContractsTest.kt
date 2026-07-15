package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class TissueContinuousAxisContractsTest {
    @Test
    fun sixAxesHaveContinuousIndependentScoreContracts() {
        assertEquals(TissueAxis.entries.toSet(), scoringPolicies.map { it.axis }.toSet())
        assertEquals(setOf(TissueAxis.M, TissueAxis.T, TissueAxis.C), scoringPolicies.filter { it.profile == TissueAxisProfile.MECHANICAL }.map { it.axis }.toSet())
        assertEquals(setOf(TissueAxis.D, TissueAxis.R, TissueAxis.P), scoringPolicies.filter { it.profile == TissueAxisProfile.DYNAMIC_STABILIZATION }.map { it.axis }.toSet())
        assertTrue(scoringPolicies.all { it.minimumScore == BigDecimal("0.0000") && it.maximumScore == BigDecimal("4.0000") })
        assertTrue(scoringPolicies.all { it.persistedDecimalPlaces >= 4 && it.unknownIsNull })
        assertTrue(scoringPolicies.none { it.crossAxisAggregationAllowed || it.confidenceAltersScore })
    }

    @Test
    fun eachAxisUsesItsOwnSimilarityWeightsWithoutScoreFeedback() {
        assertEquals(TissueAxis.entries.toSet(), similarityPolicies.map { it.axis }.toSet())
        val byAxis = similarityPolicies.associateBy { it.axis }
        assertNotEquals(byAxis.getValue(TissueAxis.M).componentWeights, byAxis.getValue(TissueAxis.T).componentWeights)
        assertNotEquals(byAxis.getValue(TissueAxis.T).componentWeights, byAxis.getValue(TissueAxis.C).componentWeights)
        assertTrue(similarityPolicies.all { kotlin.math.abs(it.componentWeights.values.sum() - 1.0) < 1e-9 })
        assertFalse(TissueAxisSimilarityBreakdown::class.java.declaredFields.any { "score" in it.name.lowercase() })

        val allMatch = TissueAxisSimilarityBreakdown(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        assertEquals(1.0, TissueAxisSimilarityPolicy.score(byAxis.getValue(TissueAxis.M), allMatch), 1e-9)
    }

    @Test
    fun evidencePoolsCannotCrossAxesOrConditions() {
        val magnitude = observation(TissueAxis.M, "condition_a")
        TissueAxisEvidencePool(magnitude.key, listOf(magnitude))

        val temporal = observation(TissueAxis.T, "condition_a")
        assertThrows(IllegalArgumentException::class.java) { TissueAxisEvidencePool(magnitude.key, listOf(magnitude, temporal)) }

        val otherCondition = observation(TissueAxis.M, "condition_b")
        assertThrows(IllegalArgumentException::class.java) { TissueAxisEvidencePool(magnitude.key, listOf(magnitude, otherCondition)) }
    }

    @Test
    fun confidenceIsRepresentedSeparatelyFromScoreMagnitude() {
        val scoreFields = TissueCanonicalAxisScore::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(setOf("canonicalScore", "unclampedScore", "confidenceScore", "confidenceBand").all { it in scoreFields })
        assertEquals(TissueAxisDerivationType.entries.size, 12)
    }

    private fun observation(axis: TissueAxis, condition: String) = TissueRawAxisObservation(
        observationId = "obs_${axis.name}_$condition",
        key = TissueAxisEvidenceKey("exercise", condition, TissueMtcTargetType.TISSUE, "ACHILLES_TENDON", axis),
        sourceId = "source", sourceConditionId = condition, metricExtractionId = "metric", physicalMetricType = "force",
        rawValue = BigDecimal.ONE, rawUnit = "BW", normalizationBasis = "BODY_WEIGHT", measurementFamily = "MODEL",
        populationScope = "HEALTHY_ADULT", contextTags = emptySet(), contextComponentScores = emptyMap(),
        rationale = "test", limitations = "none", evidenceRelation = TissueMtcEvidenceRelation.VALIDATED_INTERNAL_MODEL,
        cohortId = "cohort", datasetId = "dataset", dependencyGroupId = "dependency"
    )

    private val scoringPolicies by lazy { TissueAxisContractParser.scoringPolicies(asset("tissue_axis_scoring_policy_v1.csv")) }
    private val similarityPolicies by lazy { TissueAxisContractParser.similarityPolicies(asset("tissue_axis_similarity_policy_v1.csv")) }
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
