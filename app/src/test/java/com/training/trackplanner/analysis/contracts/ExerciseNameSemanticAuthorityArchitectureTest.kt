package com.training.trackplanner.analysis.contracts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseNameSemanticAuthorityArchitectureTest {
    @Test
    fun exactLoadAuthoritiesCannotInspectPresentationOrHeuristicFields() {
        val guardedFiles = listOf(
            "app/src/main/java/com/training/trackplanner/analysis/features/BodyweightEffectiveLoadCalculator.kt",
            "app/src/main/java/com/training/trackplanner/analysis/features/BodyweightLoadProfileAuthority.kt",
            "app/src/main/java/com/training/trackplanner/analysis/features/DurationHoldLoadCalculator.kt",
            "app/src/main/java/com/training/trackplanner/analysis/features/DurationHoldProfileAuthority.kt"
        )
        val forbidden = listOf(
            "exercise.name",
            "exerciseName",
            "displayName",
            "familyName",
            "movementPattern",
            "movementCategory",
            "equipment",
            "mode",
            "category",
            "contains("
        )

        val offenders = guardedFiles.flatMap { path ->
            val source = repoFile(path).readText()
            forbidden.filter(source::contains).map { token -> "$path: $token" }
        }

        assertTrue("Name-derived load authority found:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun muscleAndGroupingAuthoritiesCannotRecoverSemanticsFromNames() {
        val muscleSource = repoFile(
            "app/src/main/java/com/training/trackplanner/analysis/lab/MuscleLoadInputBuilder.kt"
        ).readText()
        assertFalse(muscleSource.contains("exercise.name"))
        assertFalse(muscleSource.contains("exerciseName"))
        assertFalse(muscleSource.contains("exercise.stableKey"))

        val groupingSources = listOf(
            "app/src/main/java/com/training/trackplanner/analysis/coach/CoachFatigueCauseAnalyzer.kt",
            "app/src/main/java/com/training/trackplanner/analysis/coach/RpeAutoregulationAnalyzer.kt"
        ).joinToString("\n") { path -> repoFile(path).readText() }
            .filterNot(Char::isWhitespace)
        assertFalse(groupingSources.contains("stableKey.ifBlank{exerciseName}"))
        assertFalse(groupingSources.contains("stableKey.ifBlank{contribution.exerciseName}"))
        assertFalse(groupingSources.contains("groupBy{it.exerciseName}"))
        assertFalse(groupingSources.contains("groupBy{record.entry.exerciseName}"))
    }

    @Test
    fun retiredProxyPackageStaysAbsentAndPersistentStrengthAuthorityStaysPresent() {
        val retiredPackage = repoFile("app/src/main/java/com/training/trackplanner/analysis/proxyperformance")
        assertFalse(
            retiredPackage.walkTopDown().any { file -> file.isFile && file.extension == "kt" }
        )
        assertTrue(
            repoFile(
                "app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt"
            ).isFile
        )
    }

    private fun repoFile(path: String): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(current) { directory -> directory.parentFile?.takeUnless { it == directory } }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${current.absolutePath}.")
        return File(root, path)
    }
}
