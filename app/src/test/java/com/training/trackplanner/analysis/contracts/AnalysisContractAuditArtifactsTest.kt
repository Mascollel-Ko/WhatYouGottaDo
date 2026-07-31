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
    fun mappingInventoryAndHeuristicIssuesAreComplete() {
        val usage = csv("docs/audits/metadata_field_usage_matrix.csv")
        val mappings = csv("docs/audits/metadata_legacy_to_target_mapping_matrix.csv")
        val issues = csv("docs/audits/metadata_migration_issue_ledger.csv")
        val issueIds = issues.mapNotNull { it["issueId"] }.toSet()

        assertEquals(102, usage.size)
        usage.forEach { field ->
            assertTrue(mappings.any {
                it["legacyField"] == field["fieldName"] && it["storageOwner"] == field["storageLocation"]
            })
        }
        mappings.filter { it["derivationMode"] == "LEGACY_HEURISTIC_FALLBACK" }.forEach { mapping ->
            val linked = mapping["knownIssueIds"].orEmpty().split(';').filter(String::isNotBlank)
            assertTrue(linked.isNotEmpty())
            assertTrue(linked.all(issueIds::contains))
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
        assertTrue(design.contains("stableKey 단위 사람 검토 전까지 `UNRESOLVED`"))
        assertTrue(design.contains("현재 출력과 같다는 이유만으로 `REVIEWED_CANONICAL`이 될 수 없다"))
        assertTrue(protocol.contains("REVIEWED_V1에서는 stableKey 단위 human review 전까지 `UNRESOLVED`"))
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
