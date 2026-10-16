package com.training.trackplanner.data

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression gate for reviewed B-balanced metadata semantics.
 * The task overlay is non-executable: these tests must not grant dose or owner authority.
 */
class BBalancedMetadataContractTest {
    private fun asset(path: String): File = sequenceOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path")
    ).firstOrNull(File::exists) ?: error("Missing metadata asset: $path")

    @Test
    fun overheadTricepsExtensionsBelongToArmsNotVerticalPress() {
        val file = asset("metadata/canonical_v1/physical_quality_relations.csv")
        val rows = file.readLines(Charsets.UTF_8).drop(1)
        val expected = mapOf(
            "pq_070" to "cable_overhead_triceps_extension",
            "pq_077" to "dumbbell_overhead_triceps_extension"
        )
        expected.forEach { (relationId, stableKey) ->
            val match = rows.filter { it.startsWith("$relationId,$stableKey,") }
            assertEquals("Expected exactly one physical-quality row for $stableKey", 1, match.size)
            val cols = match.single().split(",", limit = 11)
            assertEquals("HYPERTROPHY", cols[2])
            assertEquals("DIRECT_CAPABILITY", cols[3])
            assertEquals("ARMS", cols[4])
            assertEquals("GENERAL", cols[5])
            assertEquals("YES", cols[6])
            assertEquals("PASS", cols[9])
        }
        assertTrue(rows.any { it.startsWith("pq_") &&
            it.contains(",ex_32219f7a,") && it.contains(",VERTICAL_PRESS,") })
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        val manifest = asset("metadata/canonical_v1/manifest.json").readText(Charsets.UTF_8)
        val start = manifest.indexOf("\"path\": \"physical_quality_relations.csv\"")
        assertTrue("Missing physical-quality manifest entry", start >= 0)
        val entry = manifest.substring(start, manifest.indexOf('}', start))
        assertTrue(entry.contains("\"sha256\": \"$digest\""))
    }

    @Test
    fun hipAndCoreDetailedTasksStayNonExecutable() {
        val file = asset("metadata/b_balanced_v1/target_task_relations.csv")
        val rows = file.readLines(Charsets.UTF_8).drop(1).filter(String::isNotBlank)
            .map { it.split(",") }
        assertEquals(9, rows.size)
        assertTrue(rows.all { it.size == 8 && it[7] == "NONE" })
        assertEquals(rows.size, rows.map { it[0] }.distinct().size)
        val tasks = rows.groupBy({ it[1] }, { it[3] }).mapValues { (_, values) -> values.toSet() }
        assertEquals(setOf("HIP_ADDUCTION"), tasks["cable_hip_adduction"])
        assertEquals(setOf("HIP_ADDUCTION"), tasks["hip_adduction_machine"])
        assertEquals(setOf("HIP_ABDUCTION"), tasks["ex_728da646"])
        assertEquals(setOf("DYNAMIC_TRUNK_STABILIZATION", "ANTI_ROTATION"), tasks["ex_28347c1f"])
        assertEquals(setOf("DYNAMIC_TRUNK_STABILIZATION", "ANTI_EXTENSION"), tasks["ex_d5bdffe1"])
        assertEquals(setOf("ANTI_EXTENSION"), tasks["ex_a44ae2ca"])
        assertEquals(setOf("ANTI_LATERAL_FLEXION"), tasks["ex_f6d43398"])
        assertFalse(tasks.containsKey("ex_a8385c4a")) // Copenhagen status is under review.
    }
}
