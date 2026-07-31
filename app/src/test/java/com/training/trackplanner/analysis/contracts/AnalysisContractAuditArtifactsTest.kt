package com.training.trackplanner.analysis.contracts

import com.training.trackplanner.data.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class AnalysisContractAuditArtifactsTest {
    @Test
    fun progressMetricRemainsReadOnlyCompatibilityOutsideCanonicalMetadata() {
        val usage = csv("docs/audits/metadata_field_usage_matrix.csv")
            .filter { it["fieldName"] == "progressMetricType" }
        assertEquals(2, usage.size)
        assertTrue(usage.all { it["currentDisposition"] == "LEGACY_COMPATIBILITY_READONLY" })
        assertTrue(usage.all { it["eventualReplacementStrategy"] == "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY" })
        assertFalse(usage.any { it["recommendedDisposition"] == "SPLIT_INTO_RELATIONS" })

        val mappings = csv("docs/audits/metadata_legacy_to_target_mapping_matrix.csv")
            .filter { it["legacyField"] == "progressMetricType" }
        assertTrue(mappings.isNotEmpty())
        assertTrue(mappings.all { it["targetLayer"] == "NON_METADATA_COMPATIBILITY_OR_ANALYSIS_PROTOCOL" })
        assertTrue(mappings.all { it["targetRelation"] == "NONE" })
        assertTrue(mappings.all { it["conversionMode"] == "LEGACY_COMPATIBILITY_READONLY" })
    }

    @Test
    fun v22SemanticMappingsUseReviewedBoundariesWithoutApprovingCandidates() {
        val usage = csv("docs/audits/metadata_field_usage_matrix.csv")
        val mappings = csv("docs/audits/metadata_legacy_to_target_mapping_matrix.csv")

        assertEquals(102, usage.size)
        usage.forEach { field ->
            assertTrue(mappings.any {
                it["legacyField"] == field["fieldName"] && it["storageLocation"] == field["storageLocation"]
            })
        }

        val semanticUses = setOf(
            "FIXED_EXERCISE_CLASSIFICATION", "FIXED_EXERCISE_RELATION", "FIXED_PROGRAM_PARAMETER",
            "FIXED_ANALYSIS_PARAMETER", "PROGRAM_POLICY", "RECORD_OR_INPUT_PROTOCOL", "PRESENTATION_ONLY",
            "PROVENANCE_ONLY", "LEGACY_COMPATIBILITY", "UNRESOLVED"
        )
        val statuses = setOf("AUTO_CANDIDATE", "SEMANTICALLY_REVIEWED", "APPROVED", "REJECTED", "UNRESOLVED")
        assertTrue(mappings.all { it["consumerSemanticUse"].orEmpty() in semanticUses })
        assertTrue(mappings.all { it["mappingStatus"].orEmpty() in statuses })
        assertFalse(mappings.any { it["mappingStatus"] == "APPROVED" })

        val rest = mappings.filter { it["legacyField"] == "defaultRestSeconds" }
        assertTrue(rest.isNotEmpty())
        assertTrue(rest.all {
            it["consumerSemanticUse"] == "FIXED_PROGRAM_PARAMETER" &&
                it["targetLayer"] == "PROGRAM_GENERATION" &&
                it["targetRelation"] == "ExerciseProgramTimingProfile" &&
                it["conversionMode"] == "DIRECT_COPY" &&
                it["mappingStatus"] == "SEMANTICALLY_REVIEWED"
        })

        val activityKind = mappings.filter { it["legacyField"] == "activityKind" }
        assertTrue(activityKind.isNotEmpty())
        assertTrue(activityKind.all {
            it["currentDisposition"] == "LEGACY_COMPATIBILITY_READONLY" &&
                it["targetLayer"] == "NON_METADATA_LEGACY_COMPATIBILITY" &&
                it["targetRelation"] == "NONE"
        })
        assertFalse(activityKind.any {
            it["targetLayer"] == "MOVEMENT_ANATOMY" || it["targetRelation"] == "ExerciseMovementAnatomyRelation"
        })

        val eligibility = mappings.filter { it["legacyField"] == "analysisEligibility" }
        assertTrue(eligibility.map { it["targetRelation"] }.toSet().size > 2)
        assertFalse(eligibility.any { it["mappingStatus"] == "APPROVED" })
    }

    @Test
    fun riskPathImpactIsCompleteAndSeparateFromConfirmedErrors() {
        val risks = csv("docs/audits/metadata_legacy_inference_risk_paths.csv")
        val impact = csv("docs/audits/metadata_inference_stablekey_impact.csv")
        val confirmed = csv("docs/audits/confirmed_metadata_errors.csv")

        assertEquals(20, risks.size)
        assertEquals(4_480, impact.size)
        assertEquals(224, impact.mapNotNull { it["exerciseStableKey"] }.toSet().size)
        assertTrue(
            impact.groupBy { it["riskPathId"] to it["exerciseStableKey"] }
                .values.all { it.size == 1 }
        )
        assertEquals(
            impact.count { it["reviewClassification"] == "CONFIRMED_CLASSIFICATION_ERROR" },
            confirmed.size
        )
        val riskIds = risks.mapNotNull { it["riskPathId"] }.toSet()
        confirmed.forEach { issue ->
            assertTrue(issue["riskPathId"].orEmpty() in riskIds)
            assertTrue(issue["exerciseStableKey"].orEmpty().isNotBlank())
            assertEquals(1, impact.count {
                it["riskPathId"] == issue["riskPathId"] &&
                    it["exerciseStableKey"] == issue["exerciseStableKey"] &&
                    it["reviewClassification"] == "CONFIRMED_CLASSIFICATION_ERROR"
            })
        }
    }

    @Test
    fun compatibilityInventoryMatchesEveryKotlinReference() {
        val inventoried = csv("docs/audits/metadata_legacy_compatibility_consumers.csv")
            .filter { it["legacyField"] == "progressMetricType" }
            .mapNotNull { it["filePath"] }
            .toSet()
        val detected = sequenceOf(
            repoFile("app/src/main/java"),
            repoFile("app/src/test/java")
        ).flatMap { root -> root.walkTopDown() }
            .filter {
                it.isFile && it.extension == "kt" && it.name != "AnalysisContractAuditArtifactsTest.kt" &&
                    it.readText().contains("progressMetricType", ignoreCase = true)
            }
            .map { it.relativeTo(repoRoot()).invariantSeparatorsPath }
            .toSet()

        assertEquals(detected, inventoried)
        assertTrue(inventoried.isNotEmpty())
        assertFalse(repoFile("app/src/main/java/com/training/trackplanner/analysis/contracts")
            .walkTopDown().filter(File::isFile).any { it.readText().contains("progressMetricType") })
    }

    @Test
    fun immutableBaselineRetainsExactBytesRowsAndStableKeys() {
        val baseline = repoFile("app/src/main/assets/metadata/analysis_contract_baseline_v1.csv")
        assertEquals(
            "6B0CBDEC60A38FCAFA1AA957BD8335EF9D3930175CF6E723E1A9D8265F384E52",
            MessageDigest.getInstance("SHA-256").digest(baseline.readBytes()).joinToString("") {
                "%02X".format(it.toInt() and 0xFF)
            }
        )
        val rows = csv("app/src/main/assets/metadata/analysis_contract_baseline_v1.csv")
        assertEquals(9_781, rows.size)
        assertEquals(224, rows.mapNotNull { it["exerciseStableKey"] }.toSet().size)
    }

    @Test
    fun reviewedTargetNeverPromotesHeuristicCurrentBehavior() {
        val design = repoFile("docs/metadata_analysis_contract_and_migration_plan_ko.md").readText()
        val protocol = repoFile("docs/protocols/data_portability/METADATA_ANALYSIS_CONTRACT_PHASE_0_1.md").readText()
        assertTrue(design.contains("legacy heuristic으로만 만들어진 관계는 자동으로 reviewed canonical 값으로 승격하지 않는다"))
        assertTrue(design.contains("사람이 stableKey별 의미와 근거를 검토한 뒤에만 `HUMAN_REVIEWED`와 `REVIEWED_CANONICAL`로 승격한다"))
        assertTrue(protocol.contains("REVIEWED_V1에서는 stableKey 단위 human"))
        assertTrue(protocol.contains("review 전까지 `UNRESOLVED`"))
        assertFalse(repoFile("app/src/main/java/com/training/trackplanner/analysis/contracts")
            .walkTopDown().filter(File::isFile).any { it.readText().contains("REVIEWED_CANONICAL") })
    }

    private fun csv(path: String): List<Map<String, String>> {
        val lines = repoFile(path).readLines().filter(String::isNotBlank)
        val header = SeedData.parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1).map { line ->
            val values = SeedData.parseCsvLine(line)
            header.indices.associate { index -> header[index] to values.getOrElse(index) { "" } }
        }
    }

    private fun repoRoot(): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(current) { it.parentFile?.takeUnless { parent -> parent == it } }
            .first { File(it, "settings.gradle.kts").isFile }
    }

    private fun repoFile(path: String): File = File(repoRoot(), path)
}
