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

    @Test
    fun retiredBadmintonCompositeDoesNotReturnToProduction() {
        val trendsRoot = repoFile("app/src/main/java/com/training/trackplanner/analysis/trends")
        val trendSource = trendsRoot.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file.name != "TrendMetricSelectionPolicy.kt"
            }
            .joinToString("\n") { it.readText() }
        val practiceSource = repoFile(
            "app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonPracticeLoadCalculator.kt"
        ).readText()

        assertFalse(repoFile(
            "app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingLoadIndexCalculator.kt"
        ).exists())
        listOf(
            "BADMINTON_TRAINING",
            "COURT_VOLUME",
            "FOOTWORK_REACTIVE",
            "BADMINTON_COURT_WEIGHT",
            "BADMINTON_FOOTWORK_WEIGHT",
            "BADMINTON_SUPPORT_WEIGHT",
            "BadmintonWeekIndex",
            "BadmintonDailyLoadPoint",
            "footworkReactiveRaw",
            "supportRaw",
            "trainingIndex"
        ).forEach { symbol ->
            assertFalse("Retired badminton composite symbol returned: $symbol", trendSource.contains(symbol))
        }
        listOf(
            "ExerciseAnalysisMapper",
            "AnalysisExerciseFeatures",
            "badmintonTransferRoles",
            "sportContextTags",
            "courtMovementTypes",
            "exerciseName"
        ).forEach { inference ->
            assertFalse("Practice admission inference returned: $inference", practiceSource.contains(inference))
        }
        assertTrue(practiceSource.contains("BadmintonPracticeCatalog.admits"))
        assertTrue(repoFile(
            "app/src/main/java/com/training/trackplanner/analysis/coach/CourtDurationRecoveryAnalyzer.kt"
        ).exists())
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
