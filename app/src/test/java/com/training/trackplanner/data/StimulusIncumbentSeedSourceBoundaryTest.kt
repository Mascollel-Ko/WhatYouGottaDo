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
        val realizationInputsPath = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/StimulusRealizationPrescriptionInputs.kt")
        val service = servicePath.readText().replace("\r\n", "\n")
        val builder = builderPath.readText().replace("\r\n", "\n")
        val preparedModel = preparedModelPath.readText().replace("\r\n", "\n")
        val realizationInputs = realizationInputsPath.readText().replace("\r\n", "\n")
        val realizationInputsCode = Regex("(?s)/\\*.*?\\*/|//[^\\n]*").replace(realizationInputs, "")

        val seedProjectionCalls = Regex("StimulusIncumbentIdentitySeed\\.fromControl\\(").findAll(service).toList()
        val baselineProjectionCalls = Regex("StimulusIncumbentPrescriptionBaseline\\.fromControl\\(").findAll(service).toList()
        assertEquals("only the injected-control test adapter may project a seed", 1, seedProjectionCalls.size)
        assertEquals("only the injected-control test adapter may project a baseline", 1, baselineProjectionCalls.size)
        val testAdapter = service.indexOf("private suspend fun buildCanonicalPlanningForInjectedControlTestAdapter(")
        val selectorComparison = service.indexOf("internal suspend fun generatePreparedStimulusSelectionComparison(")
        assertTrue(testAdapter >= 0 && selectorComparison > testAdapter)
        assertTrue(seedProjectionCalls.single().range.first in testAdapter until selectorComparison)
        assertTrue(baselineProjectionCalls.single().range.first in testAdapter until selectorComparison)

        val productionPreparation = service.substring(
            service.indexOf("internal suspend fun generatePreparedWithCanonicalPlanning("), testAdapter
        )
        assertTrue(productionPreparation.contains("programBuilder.buildWithArtifacts("))
        assertTrue(productionPreparation.contains("generatedArtifacts.incumbentSeed"))
        assertTrue(productionPreparation.contains("generatedArtifacts.prescriptionBaseline"))
        assertFalse(productionPreparation.contains("StimulusIncumbentIdentitySeed.fromControl("))
        assertFalse(productionPreparation.contains("StimulusIncumbentPrescriptionBaseline.fromControl("))

        val selectionAndProduction = service.substring(selectorComparison)
        assertTrue(selectionAndProduction.contains("incumbentSeed = prepared.incumbentSeed"))
        assertTrue(selectionAndProduction.contains("incumbentSeed: StimulusIncumbentIdentitySeed"))
        assertFalse(selectionAndProduction.contains("StimulusIncumbentIdentitySeed.fromControl("))
        assertFalse(selectionAndProduction.contains("StimulusIncumbentPrescriptionBaseline.fromControl("))
        assertTrue(selectionAndProduction.contains("buildCanonicalPlanningForInjectedControlTestAdapter"))

        assertTrue(preparedModel.contains("val incumbentSeed: StimulusIncumbentIdentitySeed"))
        assertTrue(preparedModel.contains("val prescriptionBaseline: StimulusIncumbentPrescriptionBaseline"))
        val compatibilityBuild = builder.substring(
            builder.indexOf("internal fun build("), builder.indexOf("internal fun buildWithArtifacts(")
        )
        assertTrue(compatibilityBuild.contains("buildWithArtifacts("))
        assertTrue(compatibilityBuild.contains("buildWithArtifacts(") && compatibilityBuild.contains(").program"))
        assertTrue(builder.contains("val finalizedProgram = observeBoundedMaterialDemand(result)"))
        assertTrue(builder.contains("val finalizedPrescriptionRows = result.items.map"))
        assertTrue(builder.contains("StimulusIncumbentPrescriptionBaseline.fromFinalizedRows(finalizedPrescriptionRows)"))
        assertTrue(builder.contains("StimulusIncumbentIdentitySeed.fromFinalizedOwners("))
        assertFalse(builder.contains("finalizedProgram.items"))
        assertTrue(builder.contains("StimulusIncumbentIdentitySeed.fromFinalizedOwners("))
        assertTrue(builder.indexOf("PostSplitWeeklyReflow().review(") <
            builder.indexOf("StimulusIncumbentIdentitySeed.fromFinalizedOwners("))
        assertFalse(builder.contains("StimulusIncumbentIdentitySeed.fromControl("))

        val b6Start = service.indexOf("internal suspend fun generatePreparedStimulusPrescriptionMaterializationComparison(")
        val b6End = service.indexOf("internal suspend fun generatePreparedStimulusProductionCutoverEvaluation(", b6Start)
        val b6 = service.substring(b6Start, b6End)
        assertTrue(b6.contains("buildStimulusRealizationPrescriptionInputs("))
        assertTrue(b6.contains("incumbentSeed = incumbentSeed"))
        assertTrue(b6.contains("prescriptionBaseline = prescriptionBaseline"))
        assertTrue(b6.contains("experimentalItems = experimental.items"))
        assertTrue(b6.contains("currentPrescriptions = realizationInputs.currentPrescriptions"))
        assertFalse(b6.contains("control.items"))
        assertFalse(b6.contains("control.request"))
        assertFalse(b6.contains("control.items.asSequence()"))
        assertFalse(b6.contains("val controlPrescriptions = control.items"))
        assertTrue(b6.contains("StimulusSelectionProgramComparisonEngine().compare("))

        assertTrue(realizationInputs.contains("incumbentSeed: StimulusIncumbentIdentitySeed"))
        assertTrue(realizationInputs.contains("prescriptionBaseline: StimulusIncumbentPrescriptionBaseline"))
        assertTrue(realizationInputs.contains("experimentalItems: Iterable<ProgramSkeletonItem>"))
        assertTrue(realizationInputs.contains("putAll(experimentalPrescriptions)"))
        assertTrue(realizationInputs.contains("prescriptionBaseline.prescriptions.forEach"))
        assertFalse(realizationInputsCode.contains("GeneratedProgramSkeleton"))
        assertFalse(realizationInputsCode.contains("control.items"))
        assertFalse(realizationInputsCode.contains("control.request"))
    }
}
