package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

class TissueC4B1ResearchBatchTest {
    @Test
    fun fullTextBatchUsesExactConditionIdentityAndCompleteProvenance() {
        val conditions = rows("tissue_source_condition_registry_c4b1_v1.csv")
        val observations = rows("tissue_source_condition_axis_observation_c4b1_v1.csv")
        val conditionIds = conditions.map { it.required("sourceConditionId") }.toSet()
        val sourceIds = conditions.map { it.required("sourceId") }.toSet()

        assertEquals(25, conditions.size)
        assertEquals(25, conditionIds.size)
        assertEquals(
            setOf("SRC_PMID_28145739", "SRC_PMID_31193251", "SRC_PMID_37272685", "SRC_PMID_37847102"),
            sourceIds
        )
        assertEquals(89, observations.size)
        assertEquals(89, observations.map { it.required("observationId") }.distinct().size)
        assertEquals(89, observations.map { it.required("metricExtractionId") }.distinct().size)
        assertTrue(observations.all { it.required("sourceConditionId") in conditionIds })
        assertTrue(observations.all { it.required("fullTextReviewed") == "true" })
        assertTrue(observations.all { it.required("evidenceLocatorType") == "TABLE_REPORTED" })
        assertTrue(observations.all { it.required("publicationIntegrityStatus") == "NO_ADVERSE_NOTICE_FOUND" })
        assertTrue(observations.filter { it.required("axis") in setOf("M", "T") }.all { it.required("rawValue").toBigDecimalOrNull() != null })
        assertTrue(observations.filter { it.required("axis") == "C" }.all { it.value("rawValue").isBlank() })

        val verified = (
            rows("tissue_source_verification_v1.csv") + rows("tissue_source_verification_c3_v1.csv")
        ).associateBy { it.required("sourceId") }
        val integrity = (
            rows("tissue_publication_integrity_verification_v1.csv") +
                rows("tissue_publication_integrity_verification_c3_v1.csv")
        ).associateBy { it.required("sourceId") }
        sourceIds.forEach { sourceId ->
            assertEquals("MATCHED", verified.getValue(sourceId).required("bibliographicMatchStatus"))
            assertEquals("NO_ADVERSE_NOTICE_FOUND", integrity.getValue(sourceId).required("integrityCheckStatus"))
        }
    }

    @Test
    fun metricRubricsAreMonotoneAndEveryStoredScoreReproduces() {
        val rubricRows = rows("tissue_metric_continuous_rubric_c4b1_v1.csv")
        val anchorRows = rows("tissue_rubric_anchor_c4b1_v1.csv")
        val observationRows = rows("tissue_source_condition_axis_observation_c4b1_v1.csv")
            .associateBy { it.required("observationId") }
        val scoreRows = rows("tissue_source_condition_axis_score_c4b1_v1.csv")
        val anchorsByRubric = anchorRows.groupBy { it.required("rubricId") }
        val rubrics = rubricRows.associate { row ->
            val rubricId = row.required("rubricId")
            rubricId to TissueMetricContinuousRubric(
                rubricId = rubricId,
                targetId = row.required("targetId"),
                axis = enumValueOf(row.required("axis")),
                physicalMetricType = row.required("physicalMetricType"),
                normalizationBasis = row.required("normalizationBasis"),
                measurementFamily = row.required("measurementFamily"),
                populationScope = row.required("populationScope"),
                comparisonFamily = row.required("comparisonFamily"),
                conditionFamily = row.required("conditionFamily"),
                interpolationType = enumValueOf(row.required("interpolationType")),
                anchors = anchorsByRubric.getValue(rubricId).map { anchor ->
                    TissueMetricRubricAnchor(
                        anchorId = anchor.required("anchorId"),
                        score = anchor.required("score").toBigDecimal(),
                        rawValue = anchor.required("rawValue").toBigDecimal(),
                        sourceConditionIds = anchor.tokens("sourceConditionIds"),
                        sourceIds = anchor.tokens("sourceIds"),
                        rationale = anchor.required("rationale"),
                        limitations = anchor.required("limitations"),
                        decisionType = enumValueOf(anchor.required("decisionType"))
                    )
                },
                referenceExposureDomain = row.required("referenceExposureDomain"),
                version = row.required("version")
            )
        }

        assertEquals(9, rubrics.size)
        assertEquals(36, anchorRows.size)
        assertEquals(69, scoreRows.size)
        rubrics.values.forEach(TissueMetricContinuousScorer::validate)
        scoreRows.forEach { stored ->
            val observation = observationRows.getValue(stored.required("observationId"))
            val rubric = rubrics.getValue(stored.required("rubricId"))
            assertEquals(observation.required("targetId"), rubric.targetId)
            assertEquals(observation.required("axis"), rubric.axis.name)
            assertEquals(observation.required("physicalMetricType"), rubric.physicalMetricType)
            val calculated = TissueMetricContinuousScorer.score(rubric, observation.required("rawValue").toBigDecimal())!!
            assertEquals(stored.required("canonicalScore").toBigDecimal(), calculated.persistedScore())
            assertEquals(
                stored.required("unclampedScore").toBigDecimal(),
                calculated.unclampedScore.setScale(4, RoundingMode.HALF_UP)
            )
            assertEquals(stored.required("scoreRangeStatus"), calculated.rangeStatus.name)
        }
    }

    @Test
    fun axesStayIndependentAndSameConditionMetricsReceiveNoDuplicateWeight() {
        val observations = rows("tissue_source_condition_axis_observation_c4b1_v1.csv")
        val decisions = rows("tissue_axis_aggregation_decision_c4b1_v1.csv")
        val exclusions = rows("tissue_axis_aggregation_exclusion_c4b1_v1.csv")
        val dependencies = rows("tissue_source_dependency_registry_v1.csv")

        assertEquals(setOf("M", "T", "C"), observations.map { it.required("axis") }.toSet())
        assertEquals(56, decisions.size)
        assertEquals(33, exclusions.size)
        assertTrue(decisions.all { it.required("evidenceKey").endsWith("|${it.required("axis")}") })
        assertTrue(decisions.all { it.required("averageDisclosure") == "NOT_AVERAGED" })
        assertTrue(decisions.filter { it.required("axis") == "C" }.all {
            it.required("derivationType") == "ORDERING_RULE_ONLY" && it.value("selectedScore").isBlank()
        })
        assertTrue(exclusions.filter { it.required("reason") == "DUPLICATE_CONDITION" }.all {
            it.required("retainedAsSupportingEvidence") == "true"
        })
        assertEquals(25, dependencies.size)
        assertTrue(dependencies.all { it.required("dependencyType") == "SAME_DATASET" })
        assertEquals(4, dependencies.map { it.required("cohortId") }.distinct().size)
    }

    @Test
    fun incompleteMetricDomainsAndTargetsRemainExplicitlyUnresolved() {
        val observations = rows("tissue_source_condition_axis_observation_c4b1_v1.csv")
        val scores = rows("tissue_source_condition_axis_score_c4b1_v1.csv")
        val decisions = rows("tissue_axis_aggregation_decision_c4b1_v1.csv")
        val exclusions = rows("tissue_axis_aggregation_exclusion_c4b1_v1.csv")
        val gaps = rows("tissue_first_batch_research_gap_matrix_c4b1_v1.csv")
        val lungeObservationIds = observations.filter { it.required("sourceId") == "SRC_PMID_31193251" }
            .map { it.required("observationId") }.toSet()

        assertTrue(lungeObservationIds.isNotEmpty())
        assertTrue(scores.none { it.required("observationId") in lungeObservationIds })
        assertTrue(exclusions.filter { it.required("observationId") in lungeObservationIds && it.required("axis") != "C" }
            .all { it.required("reason") == "OUTSIDE_CALIBRATION_DOMAIN" })
        assertEquals(18, gaps.size)
        assertTrue(gaps.all { it.required("runtimeFallbackPreserved") == "true" })
        setOf("ANKLE_TALOCRURAL", "KNEE_TIBIOFEMORAL", "QUADRICEPS_TENDON").forEach { target ->
            assertEquals(setOf("M", "T", "C"), gaps.filter { it.required("targetId") == target }.map { it.required("axis") }.toSet())
            assertTrue(gaps.filter { it.required("targetId") == target }.all { it.required("status").startsWith("DEFERRED") })
        }
        assertTrue(observations.all { it.required("runtimeEligible") == "false" })
        assertTrue(scores.all { it.required("runtimeEligible") == "false" })
        assertTrue(decisions.all { it.required("runtimeEligible") == "false" })
    }

    @Test
    fun candidateStableKeysUseOnlyTheCanonicalCatalog() {
        val canonical = rowsFrom("app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
            .map { it.required("stableKey") }.toSet()
        val mapped = rows("tissue_source_condition_registry_c4b1_v1.csv")
            .flatMap { it.tokens("candidateStableKeys") }
            .toSet()
        assertTrue(mapped.isNotEmpty())
        assertTrue(mapped.all { it in canonical })
        assertFalse(mapped.any { ' ' in it })
    }

    private fun rows(name: String) = rowsFrom("app/src/main/assets/metadata/tissue_load_v1/$name")

    private fun rowsFrom(path: String): List<Map<String, String>> {
        val file = sequenceOf(File(path), File(path.removePrefix("app/"))).first(File::exists)
        return TissueMetadataParser.table(file.readText(Charsets.UTF_8)).rows
    }

    private fun Map<String, String>.required(name: String) = value(name).also {
        require(it.isNotBlank()) { "Missing $name" }
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.tokens(name: String) = value(name).split('|').map(String::trim).filter(String::isNotBlank).toSet()
}
