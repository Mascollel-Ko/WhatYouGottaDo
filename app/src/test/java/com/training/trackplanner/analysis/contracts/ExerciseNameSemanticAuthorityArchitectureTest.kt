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

    @Test
    fun retiredSemanticInferenceEntrypointsStayAbsent() {
        val seedSource = repoFile(
            "app/src/main/java/com/training/trackplanner/data/SeedData.kt"
        ).readText()
        listOf(
            "exactExerciseMetadataFromParsedRows",
            "exerciseFromCsv",
            "movementPatternFor",
            "musclesFor",
            "equipmentFor",
            "familyIdFor",
            "sportTransferDirectFor"
        ).forEach { retiredName ->
            assertFalse("Retired SeedData inference returned: $retiredName", seedSource.contains(retiredName))
        }
        assertFalse(
            repoFile("app/src/main/java/com/training/trackplanner/data/ExerciseMetadataMapper.kt").isFile
        )

        val strengthSource = repoFile(
            "app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt"
        ).readText()
        assertFalse(strengthSource.contains("metadataProxyLoadings"))
        assertTrue(strengthSource.contains("proxyLoadings(exercise.stableKey)"))

        val fatigueSource = repoFile(
            "app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt"
        ).readText().filterNot(Char::isWhitespace)
        assertFalse(fatigueSource.contains("broadLegacyFatigueCategories"))
        assertFalse(fatigueSource.contains("groupBy{it.exerciseName}"))
        assertFalse(fatigueSource.contains("anyToken("))
        assertFalse(fatigueSource.contains("splitTokens("))
        assertTrue(fatigueSource.contains("\"exerciseStableKey\"tocontribution.stableKey"))
    }

    @Test
    fun blankBackupIdentityAndTissueDoseStayFailClosed() {
        val backupSource = repoFile(
            "app/src/main/java/com/training/trackplanner/data/BackupRestoreCanonicalizer.kt"
        ).readText()
        assertFalse(backupSource.contains("canonicalByName"))
        assertFalse(backupSource.contains("findByName"))
        assertFalse(backupSource.contains("LEGACY_CUSTOM_EXACT_NAME"))
        assertTrue(backupSource.contains("LEGACY_BLANK_STABLE_KEY_CUSTOM"))
        assertTrue(backupSource.contains("UserExerciseStableKeyGenerator.generateDeterministic"))

        val tissueDoseSource = repoFile(
            "app/src/main/java/com/training/trackplanner/analysis/tissue/TissueDoseResolver.kt"
        ).readText()
        listOf(
            "record.exercise.name",
            "record.exercise.equipment",
            "record.exercise.movementPattern",
            "contains("
        ).forEach { forbidden ->
            assertFalse("Tissue dose inference returned: $forbidden", tissueDoseSource.contains(forbidden))
        }
        assertTrue(tissueDoseSource.contains("record.exercise.stableKey == profile.exerciseStableKey"))
    }

    @Test
    fun disconnectedAdvancedBuilderStaysAbsent() {
        listOf(
            "ProgramBuilder.kt",
            "ProgramCandidateInventory.kt",
            "ProgramCandidateReservoir.kt",
            "ProgramSlotCandidateQuery.kt",
            "SlotCapabilityResolver.kt"
        ).forEach { fileName ->
            assertFalse(
                "Disconnected advanced builder file returned: $fileName",
                repoFile("app/src/main/java/com/training/trackplanner/data/$fileName").isFile
            )
        }
    }

    @Test
    fun semanticAuthoritiesCannotClassifyStableKeyFragments() {
        val guardedFiles = listOf(
            "app/src/main/java/com/training/trackplanner/data/ExercisePlanning.kt",
            "app/src/main/java/com/training/trackplanner/data/ProgramCandidateAuthority.kt",
            "app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt",
            "app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt",
            "app/src/main/java/com/training/trackplanner/analysis/tissue/TissueDoseResolver.kt"
        )
        val forbidden = listOf(
            "stableKey.contains(",
            "stableKey.startsWith(",
            "exerciseStableKey.contains(",
            "exerciseStableKey.startsWith("
        )
        val offenders = guardedFiles.flatMap { path ->
            val source = repoFile(path).readText()
            forbidden.filter(source::contains).map { token -> "$path: $token" }
        }

        assertTrue("Stable-key fragment inference found:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    private fun repoFile(path: String): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(current) { directory -> directory.parentFile?.takeUnless { it == directory } }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${current.absolutePath}.")
        return File(root, path)
    }
}
