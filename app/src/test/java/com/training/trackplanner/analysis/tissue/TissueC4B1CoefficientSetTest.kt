package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueC4B1CoefficientSetTest {
    @Test
    fun canonicalScoresPublishOnlyPassedExactOrBoundedVariantConditions() {
        val canonical = rows(CANONICAL_FILE)
        val exact = rows(EXACT_FILE)
        val variants = rows(VARIANT_FILE)
        val conditions = rows("tissue_source_condition_registry_c4b1_v1.csv")
            .associateBy { it.required("sourceConditionId") }
        val decisions = rows("tissue_axis_aggregation_decision_c4b1_v1.csv")
        val observationIds = rows("tissue_source_condition_axis_observation_c4b1_v1.csv")
            .map { it.required("observationId") }.toSet()
        val canonicalById = canonical.associateBy { it.required("canonicalAxisScoreId") }

        assertEquals(14, canonical.size)
        assertEquals(10, exact.size)
        assertEquals(4, variants.size)
        assertEquals(setOf("M", "T"), canonical.map { it.required("axis") }.toSet())
        assertEquals(14, canonical.map { it.required("exerciseStableKey") + "|" + it.required("targetId") + "|" + it.required("axis") }.distinct().size)
        assertTrue(canonical.all { it.required("coefficientSetId") == C4B1_SET && it.required("runtimeEligible") == "false" })

        canonical.forEach { score ->
            val conditionId = score.required("primarySourceConditionId")
            val condition = conditions.getValue(conditionId)
            val decision = decisions.single {
                it.required("primarySourceConditionId") == conditionId &&
                    it.required("axis") == score.required("axis") &&
                    it.required("evidenceKey").contains("|${score.required("targetId")}|" )
            }
            assertEquals("PASSED", decision.required("heterogeneityStatus"))
            assertEquals(decision.required("selectedScore"), score.required("canonicalScore"))
            assertEquals(condition.required("candidateStableKeys"), score.required("exerciseStableKey"))
            assertTrue(score.tokens("rawObservationIds").all { it in observationIds })
            val expectedConfidence = if (score.required("status") == "DRAFT_EXACT_NON_PRODUCTION") "0.6500" else "0.6000"
            assertEquals(expectedConfidence, score.required("confidenceScore"))
        }

        exact.forEach { row ->
            val canonicalRow = canonicalById.getValue(row.required("canonicalAxisScoreId"))
            val condition = conditions.getValue(row.required("sourceConditionId"))
            assertEquals("EXACT_PROTOCOL", condition.required("exerciseCorrespondence"))
            assertEquals("1.0000", row.required("axisSpecificSimilarity"))
            assertEquals(canonicalRow.required("canonicalScore"), row.required("researchScore"))
            assertEquals(canonicalRow.required("conditionSelector"), row.required("conditionSelector"))
        }
        variants.forEach { row ->
            val canonicalRow = canonicalById.getValue(row.required("canonicalAxisScoreId"))
            val condition = conditions.getValue(row.required("sourceConditionId"))
            assertEquals("CLOSE_VARIANT_PROTOCOL", condition.required("exerciseCorrespondence"))
            assertTrue(row.required("axisSpecificSimilarity").toBigDecimal() < "0.85".toBigDecimal())
            assertEquals(canonicalRow.required("canonicalScore"), row.required("researchScore"))
            assertEquals(canonicalRow.required("conditionSelector"), row.required("variantSelector"))
        }
    }

    @Test
    fun coefficientDiffAndManifestAccountForEveryDraftAxisWithoutRuntimeReplacement() {
        val diff = rows(DIFF_FILE)
        val manifest = rows(MANIFEST_FILE).single()
        val traces = rows("tissue_mtc_fallback_resolution_trace_v1.csv")

        assertEquals(14, diff.size)
        assertEquals(10, diff.count { it.required("applicationType") == "EXACT_CONDITION_DRAFT" })
        assertEquals(4, diff.count { it.required("applicationType") == "CLOSE_VARIANT_DRAFT" })
        assertTrue(diff.all {
            it.value("c4aResearchScore").isBlank() &&
                it.required("operationalFallbackStatus") == "UNCHANGED" &&
                it.required("runtimeActivation") == "NOT_ACTIVATED"
        })
        assertEquals(C4B1_SET, manifest.required("coefficientSetId"))
        assertEquals("0.2.0", manifest.required("semanticVersion"))
        assertEquals("DRAFT_NON_PRODUCTION", manifest.required("status"))
        assertEquals("TISSUE_MTC_C4A_0_1_2", manifest.required("supersededCoefficientSetId"))
        assertEquals("10", manifest.required("exactChangedAxisCount"))
        assertEquals("4", manifest.required("variantChangedAxisCount"))
        assertEquals("1134", manifest.required("unchangedOperationalFallbackCount"))
        assertEquals("C4B1_CONTINUOUS_AXIS_SCORING_AND_FIRST_RESEARCH_BATCH_PARTIAL", manifest.required("completionStatus"))
        listOf("runtimeDefault", "runtimeActivation", "historicalSessionRecalculation").forEach {
            assertEquals("false", manifest.required(it))
        }
        listOf("humanApprovalCount", "finalClaimCount", "blindReviewCount", "productionProfileCount").forEach {
            assertEquals("0", manifest.required(it))
        }
        assertEquals(1134, traces.size)
        assertTrue(traces.all { it.required("coefficientSetId") == "TISSUE_MTC_C4A_0_1_1" })
        assertTrue(sourceFiles().none { it.readText(Charsets.UTF_8).contains(C4B1_SET) })
    }

    @Test
    fun semanticHashesAreDeterministicAndC4AArtifactsRemainImmutable() {
        val manifest = rows(MANIFEST_FILE).single()
        assertEquals(manifest.required("sourceSnapshotHash"), semanticBundleHash(SOURCE_SNAPSHOT_FILES))
        assertEquals(manifest.required("rubricSnapshotHash"), semanticBundleHash(RUBRIC_SNAPSHOT_FILES))
        assertEquals(manifest.required("scoringPolicyHash"), semanticBundleHash(SCORING_POLICY_FILES))
        assertEquals(C4A_SEMANTIC_HASH, semanticBundleHash(C4A_IMMUTABLE_FILES))

        val lf = "a,b\n2,beta\n1,alpha\n"
        val crlfBomReordered = "\uFEFFa,b\r\n1,alpha\r\n2,beta\r\n"
        assertEquals(semanticTextHash("probe.csv", lf), semanticTextHash("probe.csv", crlfBomReordered))
        assertNotEquals(semanticTextHash("probe.csv", lf), semanticTextHash("probe.csv", lf.replace("alpha", "changed")))
    }

    @Test
    fun unresolvedTargetsAndContextAxesKeepFallbackRatherThanFalseZero() {
        val canonical = rows(CANONICAL_FILE)
        val gaps = rows("tissue_first_batch_research_gap_matrix_c4b1_v1.csv")
        val gapTargets = gaps.map { it.required("targetId") }.toSet()

        assertTrue(canonical.none { it.required("axis") == "C" })
        assertTrue(canonical.none { it.required("canonicalScore") == "0.0000" })
        setOf("ANKLE_TALOCRURAL", "KNEE_TIBIOFEMORAL", "QUADRICEPS_TENDON").forEach { target ->
            assertTrue(target in gapTargets)
            assertTrue(canonical.none { it.required("targetId") == target })
        }
        assertTrue(gaps.all { it.required("runtimeFallbackPreserved") == "true" })
        assertFalse(canonical.any { it.required("targetType") == "FUNCTIONAL_COMPLEX" })
    }

    private fun rows(name: String) = TissueMetadataParser.table(assetFile(name).readText(Charsets.UTF_8)).rows

    private fun assetFile(name: String) = sequenceOf(
        File("app/src/main/assets/metadata/tissue_load_v1/$name"),
        File("src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists)

    private fun sourceFiles() = sequenceOf(File("app/src/main/java"), File("src/main/java"))
        .first(File::exists).walkTopDown().filter { it.isFile && it.extension == "kt" }

    private fun semanticBundleHash(names: List<String>) = names.sorted().joinToString("\n--FILE--\n") { name ->
        semanticText(name, assetFile(name).readText(Charsets.UTF_8))
    }.sha256()

    private fun semanticTextHash(name: String, text: String) = semanticText(name, text).sha256()

    private fun semanticText(name: String, text: String): String {
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            .split('\n').filter(String::isNotEmpty)
        return "$name\n${lines.first()}\n${lines.drop(1).sorted().joinToString("\n")}"
    }

    private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun Map<String, String>.required(name: String) = value(name).also {
        require(it.isNotBlank()) { "Missing $name" }
    }

    private fun Map<String, String>.value(name: String) = get(name).orEmpty().trim()
    private fun Map<String, String>.tokens(name: String) = value(name).split('|').map(String::trim).filter(String::isNotBlank).toSet()

    private companion object {
        const val C4B1_SET = "TISSUE_MTC_C4B1_0_2_0"
        const val CANONICAL_FILE = "tissue_canonical_axis_score_c4b1_v1.csv"
        const val EXACT_FILE = "tissue_mtc_exact_stable_key_override_c4b1_v1.csv"
        const val VARIANT_FILE = "tissue_mtc_variant_profile_c4b1_v1.csv"
        const val DIFF_FILE = "tissue_mtc_coefficient_diff_c4a_to_c4b1_v1.csv"
        const val MANIFEST_FILE = "tissue_mtc_coefficient_set_manifest_c4b1_v1.csv"
        const val C4A_SEMANTIC_HASH = "733ca3f5116db345023d7ae45c2d1ca3cf0bfebef2eebecf983fd3be73c074d1"

        val SOURCE_SNAPSHOT_FILES = listOf(
            "tissue_mtc_seed_semantic_correction_c4b1_v1.csv",
            "tissue_source_condition_registry_c4b1_v1.csv",
            "tissue_source_condition_axis_observation_c4b1_v1.csv",
            "tissue_source_condition_axis_score_c4b1_v1.csv",
            "tissue_source_dependency_registry_v1.csv",
            "tissue_axis_aggregation_decision_c4b1_v1.csv",
            "tissue_axis_aggregation_exclusion_c4b1_v1.csv",
            "tissue_first_batch_research_decision_c4b1_v1.csv",
            "tissue_first_batch_research_gap_matrix_c4b1_v1.csv"
        )
        val RUBRIC_SNAPSHOT_FILES = listOf(
            "tissue_metric_continuous_rubric_c4b1_v1.csv",
            "tissue_rubric_anchor_c4b1_v1.csv"
        )
        val SCORING_POLICY_FILES = listOf("tissue_axis_scoring_policy_v1.csv")
        val C4A_IMMUTABLE_FILES = listOf(
            "tissue_mtc_coefficient_set_manifest_research_v1.csv",
            "tissue_mtc_exact_stable_key_override_v1.csv",
            "tissue_mtc_variant_profile_v1.csv",
            "tissue_mtc_movement_family_profile_v1.csv",
            "tissue_mtc_fallback_resolution_trace_v1.csv"
        )
    }
}
