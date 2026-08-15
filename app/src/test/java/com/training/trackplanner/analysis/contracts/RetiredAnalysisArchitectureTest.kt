package com.training.trackplanner.analysis.contracts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredAnalysisArchitectureTest {
    @Test
    fun deadV3AnalysisIslandDoesNotReturnToProduction() {
        val sourceRoot = repoFile("app/src/main/java")
        val forbidden = listOf(
            "AnalysisEngineV3",
            "AnalysisDashboardV3Result",
            "AnalysisInputCollector",
            "AnalysisInputSnapshot",
            "CommonLoadMetrics",
            "CommonPlanProjectionMetrics",
            "CommonStrengthMetrics",
            "CommonTaxonomyMetrics",
            "AnalysisMethodRegistry"
        )
        val source = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        forbidden.forEach { symbol ->
            assertFalse("Retired analysis symbol returned: $symbol", source.contains(symbol))
        }
        assertTrue(repoFile("app/src/main/java/com/training/trackplanner/analysis/engine").listFiles().isNullOrEmpty())
    }

    @Test
    fun obsoleteBadmintonOracleDoesNotReturn() {
        assertFalse(
            repoFile(
                "app/src/test/java/com/training/trackplanner/analysis/contracts/LegacyBadmintonContractOracle.kt"
            ).exists()
        )
    }

    private fun repoFile(path: String): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(current) { directory ->
            directory.parentFile?.takeUnless { it == directory }
        }.firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${current.absolutePath}.")
        return File(root, path)
    }
}
