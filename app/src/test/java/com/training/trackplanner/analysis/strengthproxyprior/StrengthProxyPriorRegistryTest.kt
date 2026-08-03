package com.training.trackplanner.analysis.strengthproxyprior

import com.training.trackplanner.data.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class StrengthProxyPriorRegistryTest {
    @Test
    fun registryContainsOnlyTheFiveApprovedTargetsAndExactAnchors() {
        val registry = registry()

        assertEquals(StrengthTargetKey.entries.toSet(), registry.targetsByKey.keys)
        assertEquals("barbell_bench_press", registry.targetsByKey.getValue(StrengthTargetKey.BENCH_PRESS).anchorExerciseStableKey)
        assertEquals("barbell_back_squat", registry.targetsByKey.getValue(StrengthTargetKey.BACK_SQUAT).anchorExerciseStableKey)
        assertEquals("barbell_deadlift", registry.targetsByKey.getValue(StrengthTargetKey.DEADLIFT).anchorExerciseStableKey)
        assertEquals("ex_e41f4c2b", registry.targetsByKey.getValue(StrengthTargetKey.WEIGHTED_PULL_UP).anchorExerciseStableKey)
        assertEquals("ex_32219f7a", registry.targetsByKey.getValue(StrengthTargetKey.MILITARY_PRESS).anchorExerciseStableKey)
        val military = registry.targetsByKey.getValue(StrengthTargetKey.MILITARY_PRESS)
        assertTrue(military.canonicalExecutionSemantics.contains("Standing strict barbell overhead press"))
        assertTrue(military.canonicalExecutionSemantics.contains("without intentional knee or hip drive"))
        assertTrue(military.canonicalExecutionSemantics.contains("excludes push press push jerk and split jerk"))
        assertTrue(military.canonicalExecutionSemantics.contains("existing ex_32219f7a history is canonical"))
        assertFalse(targetRows().any { it["targetKey"] == "CONVENTIONAL_DEADLIFT" })
    }

    @Test
    fun militaryPressDecisionIsStrictDirectAndPressBridgesRemainProvisional() {
        val registry = registry()
        val overheadRelations = registry.relationsByExercise.getValue("ex_32219f7a")

        assertEquals(setOf(StrengthTargetKey.BENCH_PRESS, StrengthTargetKey.MILITARY_PRESS), overheadRelations.map { it.targetKey }.toSet())
        val direct = overheadRelations.single { it.targetKey == StrengthTargetKey.MILITARY_PRESS }
        assertEquals(StrengthProxyEvidenceClass.DIRECT_ANCHOR_PRODUCT_POLICY, direct.evidenceClass)
        assertEquals(StrengthProxyApprovalStatus.REVIEWED_DIRECT_ANCHOR, direct.approvalStatus)
        assertFalse(direct.requiresPostMetadataResearchReview)
        assertTrue(direct.rationale.contains("strict standing barbell overhead press"))
        assertTrue(direct.rationale.contains("without knee or hip drive"))

        registry.relations.filter { it.targetKey == StrengthTargetKey.MILITARY_PRESS && it !== direct }.forEach { bridge ->
            assertEquals(StrengthProxyEvidenceClass.PROVISIONAL_PRODUCT_PRIOR, bridge.evidenceClass)
            assertEquals(StrengthProxyApprovalStatus.TEMPORARY_APPROVED, bridge.approvalStatus)
            assertTrue(bridge.requiresPostMetadataResearchReview)
        }
    }

    @Test
    fun allNonDirectPriorsStayBroadTemporaryAndExactlyStableKeyLinked() {
        val registry = registry()
        val seedKeys = csv("app/src/main/assets/training_settings_seed.csv")
            .filter { it["row_type"] == "exercise" }
            .mapNotNull { it["stable_key"] }
            .toSet()

        registry.relations.forEach { relation ->
            assertTrue(relation.exerciseStableKey in seedKeys)
            val direct = relation.exerciseStableKey == registry.targetsByKey.getValue(relation.targetKey).anchorExerciseStableKey
            if (!direct) {
                assertEquals(StrengthProxyEvidenceClass.PROVISIONAL_PRODUCT_PRIOR, relation.evidenceClass)
                assertEquals(StrengthProxyApprovalStatus.TEMPORARY_APPROVED, relation.approvalStatus)
                assertTrue(relation.requiresPostMetadataResearchReview)
                assertTrue(relation.priorTransferSlopeSd >= 0.5)
                assertTrue(relation.priorResidualLogSdSd >= 0.5)
            }
        }
    }

    @Test
    fun duplicateRelationIsRejected() {
        val rows = relationRows()
        assertTrue(runCatching { StrengthProxyPriorRegistry.fromRows(targetRows(), rows + rows.first()) }.isFailure)
    }

    @Test
    fun priorRegistryIsIsolatedFromTheProductionStrengthRegistry() {
        assertEquals(
            "65EDBCA0901D598844529360D5B75984C3C8B9A72F758B9364C49CD254B27C81",
            sha256("app/src/main/assets/strength_performance/strength_target_registry_v1.csv")
        )
        assertEquals(
            "E8F272FC70B451F00C8F84E0FEF301BA1247659DAAEE369CA3198B870082C349",
            sha256("app/src/main/assets/strength_performance/strength_proxy_loadings_v1.csv")
        )
        val sourceRoot = repoFile("app/src/main/java")
        val consumers = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "analysis${File.separator}strengthproxyprior" !in it.path }
            .filter { it.readText().contains("StrengthProxyPriorRegistry") }
            .toList()
        assertTrue("Prior-only registry must not affect production strength output: $consumers", consumers.isEmpty())
    }

    private fun registry(): StrengthProxyPriorRegistry =
        StrengthProxyPriorRegistry.fromRows(targetRows(), relationRows())

    private fun targetRows(): List<Map<String, String>> =
        csv("app/src/main/assets/metadata/strength_proxy_prior_v1/strength_target_refs_v1.csv")

    private fun relationRows(): List<Map<String, String>> =
        csv("app/src/main/assets/metadata/strength_proxy_prior_v1/strength_proxy_relations_v1.csv")

    private fun csv(path: String): List<Map<String, String>> {
        val lines = repoFile(path).readLines(Charsets.UTF_8).filter(String::isNotBlank)
        val header = SeedData.parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1).map { line ->
            val values = SeedData.parseCsvLine(line)
            header.indices.associate { index -> header[index] to values.getOrElse(index) { "" } }
        }
    }

    private fun sha256(path: String): String = MessageDigest.getInstance("SHA-256")
        .digest(repoFile(path).readText(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private fun repoFile(path: String): File = File(repoRoot(), path)

    private fun repoRoot(): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(current) { it.parentFile?.takeUnless { parent -> parent == it } }
            .first { File(it, "settings.gradle.kts").isFile }
    }
}
