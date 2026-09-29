package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StimulusIncumbentSeedSourceBoundaryTest {
    @Test
    fun normalProductionCarriesTheBuilderSeedAndProjectsInjectedControlOnlyInItsTestAdapter() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile, File::getParentFile)
            .first { File(it, "settings.gradle.kts").isFile }
        val servicePath = File(root, "app/src/main/java/com/training/trackplanner/data/PersonalizedProgramPlanningService.kt")
        val builderPath = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt")
        val preparedModelPath = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/CanonicalStimulusPlanningResult.kt")
        val service = servicePath.readText().replace("\r\n", "\n")
        val builder = builderPath.readText().replace("\r\n", "\n")
        val preparedModel = preparedModelPath.readText().replace("\r\n", "\n")

        val projectionCalls = Regex("StimulusIncumbentIdentitySeed\\.fromControl\\(").findAll(service).toList()
        assertEquals("only the injected-control test adapter may project a program", 1, projectionCalls.size)
        val testAdapter = service.indexOf("private suspend fun buildCanonicalPlanningForInjectedControlTestAdapter(")
        val selectorComparison = service.indexOf("internal suspend fun generatePreparedStimulusSelectionComparison(")
        assertTrue(testAdapter >= 0 && selectorComparison > testAdapter)
        assertTrue(projectionCalls.single().range.first in testAdapter until selectorComparison)

        val productionPreparation = service.substring(
            service.indexOf("internal suspend fun generatePreparedWithCanonicalPlanning("), testAdapter
        )
        assertTrue(productionPreparation.contains("programBuilder.buildWithArtifacts("))
        assertTrue(productionPreparation.contains("generatedArtifacts.incumbentSeed"))
        assertFalse(productionPreparation.contains("StimulusIncumbentIdentitySeed.fromControl("))

        val selectionAndProduction = service.substring(selectorComparison)
        assertTrue(selectionAndProduction.contains("incumbentSeed = prepared.incumbentSeed"))
        assertTrue(selectionAndProduction.contains("incumbentSeed: StimulusIncumbentIdentitySeed"))
        assertFalse(selectionAndProduction.contains("StimulusIncumbentIdentitySeed.fromControl("))
        assertTrue(selectionAndProduction.contains("buildCanonicalPlanningForInjectedControlTestAdapter"))

        assertTrue(preparedModel.contains("val incumbentSeed: StimulusIncumbentIdentitySeed"))
        val compatibilityBuild = builder.substring(
            builder.indexOf("internal fun build("), builder.indexOf("internal fun buildWithArtifacts(")
        )
        assertTrue(compatibilityBuild.contains("buildWithArtifacts("))
        assertTrue(compatibilityBuild.contains("buildWithArtifacts(") && compatibilityBuild.contains(").program"))
        assertTrue(builder.contains("val finalizedItems = finalizedProgram.items"))
        assertTrue(builder.contains("StimulusIncumbentIdentitySeed.fromFinalizedOwners("))
        assertTrue(builder.indexOf("PostSplitWeeklyReflow().review(") <
            builder.indexOf("StimulusIncumbentIdentitySeed.fromFinalizedOwners("))
        assertFalse(builder.contains("StimulusIncumbentIdentitySeed.fromControl("))
    }
}
