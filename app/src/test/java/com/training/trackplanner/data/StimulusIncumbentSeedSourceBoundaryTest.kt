package com.training.trackplanner.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusIncumbentSeedSourceBoundaryTest {
    @Test
    fun canonicalB5B6SourcesExcludeLegacyControlAuthority() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile, File::getParentFile)
            .first { File(it, "settings.gradle.kts").isFile }
        fun source(path: String) = File(root, path).readText().replace("\r\n", "\n")
        fun code(path: String) = Regex("(?s)/\\*.*?\\*/|//[^\\n]*").replace(source(path), "")

        val service = code("app/src/main/java/com/training/trackplanner/data/PersonalizedProgramPlanningService.kt")
        val selector = code("app/src/main/java/com/training/trackplanner/data/personalized/StimulusCandidateSelection.kt")
        val context = code("app/src/main/java/com/training/trackplanner/data/personalized/StimulusRealizationPrescriptionInputs.kt")
        val compatibleHistory = code("app/src/main/java/com/training/trackplanner/data/personalized/TargetCompatiblePersonalHistory.kt")
        val realization = code("app/src/main/java/com/training/trackplanner/data/personalized/StimulusPrescriptionRealization.kt")
        val authorization = code("app/src/main/java/com/training/trackplanner/data/personalized/StimulusPrescriptionMaterialization.kt")
        val prepared = code("app/src/main/java/com/training/trackplanner/data/personalized/CanonicalStimulusPlanningResult.kt")

        assertFalse(service.contains("StimulusIncumbentIdentitySeed"))
        assertFalse(service.contains("StimulusIncumbentPrescriptionBaseline"))
        assertFalse(service.contains("generatedArtifacts.incumbentSeed"))
        assertFalse(service.contains("generatedArtifacts.prescriptionBaseline"))
        assertFalse(prepared.contains("StimulusIncumbentIdentitySeed"))
        assertFalse(prepared.contains("StimulusIncumbentPrescriptionBaseline"))

        val selectorClass = selector.substring(selector.indexOf("class StimulusTargetCandidateSelector"), selector.indexOf("class StimulusSelectionProgramComparisonEngine"))
        assertFalse(selectorClass.contains("incumbentSeed"))
        assertFalse(selectorClass.contains("controlKeys"))
        assertFalse(selectorClass.contains("controlDirectCapability"))
        assertTrue(selectorClass.contains("strengthCompatibleHistoryKeys"))
        assertTrue(selectorClass.contains("hypertrophyCompatibleHistoryKeys"))
        assertTrue(selectorClass.contains("recentHistoryStableKeys"))
        assertTrue(selectorClass.contains("contextHistoryStableKeys"))
        assertFalse(selectorClass.contains("DIRECT_CAPABILITY_IDENTITY_ALREADY_PRESENT"))

        assertTrue(context.contains("selectionPlan.selectedCandidates"))
        assertTrue(context.contains("targetCompatiblePersonalHistoryPrescription("))
        assertTrue(context.contains("prescriptionPlanner.prescribe"))
        assertTrue(compatibleHistory.contains("snapshot.allConfirmedSets"))
        assertTrue(compatibleHistory.contains("snapshot.reviewedRealization(row)"))
        assertTrue(compatibleHistory.contains("realization.isRealized"))
        assertFalse(context.contains("StimulusIncumbentIdentitySeed"))
        assertFalse(context.contains("StimulusIncumbentPrescriptionBaseline"))
        assertFalse(context.contains("control.items"))

        assertTrue(authorization.contains("canonicalPrescriptionContext"))
        assertTrue(authorization.contains("historyBackedOwners"))
        assertFalse(authorization.contains("controlPrescriptions"))
        assertFalse(authorization.contains("PRESERVE_CONTROL_OWNER"))
        assertFalse(realization.contains("CONTROL_EXISTING_DIRECT_IDENTITY"))
        assertFalse(realization.contains("controlDirectCapabilityIdentities"))
        assertFalse(realization.contains("PRESERVE_CONTROL_OWNER"))

        val b6Start = service.indexOf("internal suspend fun generatePreparedStimulusPrescriptionMaterializationComparison(")
        val b6End = service.indexOf("internal suspend fun generatePreparedStimulusProductionCutoverEvaluation(", b6Start)
        val b6 = service.substring(b6Start, b6End)
        assertTrue(b6.contains("buildCanonicalPrescriptionContext("))
        assertTrue(b6.contains("StimulusTargetCandidateSelector().build("))
        assertTrue(b6.contains("canonicalPrescriptionContext = canonicalPrescriptionContext"))
        assertFalse(b6.contains("incumbentSeed"))
        assertFalse(b6.contains("prescriptionBaseline"))
        assertFalse(b6.contains("controlPrescriptions"))
        assertTrue(b6.contains("StimulusSelectionProgramComparisonEngine().compare("))

        val productionStart = service.indexOf("internal suspend fun generatePreparedProduction(")
        val productionEnd = service.indexOf("internal fun evaluateStimulusProductionCutover(", productionStart)
        val production = service.substring(productionStart, productionEnd)
        assertFalse("normal production must not depend on the CONTROL-first compatibility helper",
            production.contains("generatePreparedWithCanonicalPlanning("))
        assertFalse("normal production must not call the combined B6/control helper",
            production.contains("generatePreparedStimulusProductionCutoverEvaluation("))
        val prepare = production.indexOf("val context = prepareCanonicalGenerationContext(")
        val experimental = production.indexOf("buildCanonicalExperimentalGeneration(", prepare)
        val control = production.indexOf("val control = materializeLateControl(", experimental)
        val comparison = production.indexOf("compareCanonicalExperimentalWithControl(", control)
        assertTrue(prepare >= 0 && experimental > prepare && control > experimental && comparison > control)

        val canonicalModels = code("app/src/main/java/com/training/trackplanner/data/personalized/PreparedCanonicalGeneration.kt")
        val contextStart = canonicalModels.indexOf("internal data class PreparedCanonicalGenerationContext(")
        val contextEnd = canonicalModels.indexOf("internal data class CanonicalExperimentalGeneration", contextStart)
        val contextFields = canonicalModels.substring(contextStart, contextEnd)
        listOf("control:", "controlProgram", "incumbentSeed", "prescriptionBaseline", "b7Decision", "b8Decision", "routingResult")
            .forEach { forbidden -> assertFalse("prepared context cannot contain $forbidden", contextFields.contains(forbidden)) }
        val artifactStart = canonicalModels.indexOf("internal data class CanonicalExperimentalGeneration(")
        val artifactEnd = canonicalModels.indexOf("internal enum class ProductionGenerationPhase", artifactStart)
        val artifactFields = canonicalModels.substring(artifactStart, artifactEnd)
        assertFalse("canonical experimental artifact cannot contain CONTROL", artifactFields.contains("control"))
    }
}
