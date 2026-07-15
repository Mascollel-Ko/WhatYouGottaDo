package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC4AExerciseCatalogBridgeTest {
    @Test
    fun liveCanonicalCatalogIsMappedByExactStableKeyAndAuditedMovementFamily() {
        assertEquals(239, canonical.size)
        assertEquals(64, movementRegistry.size)
        assertEquals(239, mappings.size)
        assertEquals(canonical.map { it.getValue("stableKey") }.toSet(), mappings.map { it.stableKey }.toSet())
        assertEquals(setOf("AUDITED_APPLICABLE", "AUDITED_NOT_APPLICABLE"), mappings.map { it.mappingStatus }.toSet())
        assertFalse(TissueMetadataParser.table(asset("tissue_mtc_exercise_movement_family_mapping_v1.csv")).header.contains("exerciseName"))
    }

    @Test
    fun everyApplicableExerciseComplexRelationshipHasDeterministicNonNullMtc() {
        assertEquals(378, applicability.size)
        assertEquals(1134, traces.size)
        assertTrue(bridgeReport.errors.toString(), bridgeReport.isValid)
        assertTrue(traces.all { it.researchScore == null && it.operationalScore > 0.0 })
        assertTrue(traces.all { it.provenanceTier == TissueMtcProvenanceTier.MOVEMENT_FAMILY_DEFAULT })
        assertEquals(9, applicability.map { it.complexId }.toSet().size)
    }

    @Test
    fun allNineComplexesHaveExplicitMtcRulesWithoutChangingTheBaseCoefficientSet() {
        val complexIds = complexes.map { it.complexId }.toSet()
        val complexRules = (baseRules + bridgeRules).filter { it.targetType == TissueMtcTargetType.FUNCTIONAL_COMPLEX }
        assertEquals(complexIds, complexRules.map { it.targetId }.toSet())
        complexRules.groupBy { it.targetId }.forEach { (_, rows) -> assertEquals(TissueMtcAxis.entries.toSet(), rows.map { it.axis }.toSet()) }
        val coefficient = bridgeCoefficient.single()
        assertEquals("TISSUE_MTC_C4A_0_1_0", coefficient.supersedesCoefficientSetId)
        assertEquals(TissueMetadataValidator.combinedHash(mapOf(
            "base" to TissueMetadataValidator.semanticCsvHash(asset("tissue_mtc_axis_rubric_v1.csv")),
            "bridge" to TissueMetadataValidator.semanticCsvHash(asset("tissue_mtc_bridge_axis_rubric_v1.csv"))
        )), coefficient.rubricSnapshotHash)
        assertEquals(TissueMetadataValidator.combinedHash(mapOf(
            "base" to TissueMetadataValidator.semanticCsvHash(asset("tissue_mtc_axis_metric_registry_v1.csv")),
            "bridge" to TissueMetadataValidator.semanticCsvHash(asset("tissue_mtc_bridge_complex_axis_metric_registry_v1.csv"))
        )), coefficient.axisRegistrySnapshotHash)
        assertTrue(bridgeRubricReport.errors.toString(), bridgeRubricReport.isValid)
        assertEquals("TISSUE_MTC_C4A_0_1_1", traces.first().coefficientSetId)
    }

    @Test
    fun sparseInheritanceUsesRequiredBridgePriorityAndExactStableKeyMatching() {
        val request = TissueMtcResolutionRequest("exact_key", "condition_1", setOf("variant_key"), "SQUAT", "KNEE_EXTENSOR_CONTACT_COMPLEX", TissueMtcAxis.M)
        val candidates = listOf(
            candidate("conservative", "", "", "", TissueMtcInheritanceLevel.CONSERVATIVE_FALLBACK),
            candidate("complex", "", "", "", TissueMtcInheritanceLevel.FUNCTIONAL_COMPLEX),
            candidate("family", "", "", "SQUAT", TissueMtcInheritanceLevel.MOVEMENT_FAMILY),
            candidate("variant", "variant_key", "", "", TissueMtcInheritanceLevel.CLOSE_VARIANT),
            candidate("base", "exact_key", "", "", TissueMtcInheritanceLevel.STABLE_KEY_BASE),
            candidate("exact", "exact_key", "condition_1", "", TissueMtcInheritanceLevel.EXACT_CONDITION),
            candidate("prefix_must_not_match", "exact", "condition_1", "", TissueMtcInheritanceLevel.EXACT_CONDITION)
        )
        val expected = listOf("exact", "base", "variant", "family", "complex", "conservative")
        expected.indices.forEach { removed ->
            val remaining = expected.drop(removed).toSet() + "prefix_must_not_match"
            assertEquals(expected[removed], TissueMtcExerciseBridgeResolver.resolve(request, candidates.filter { it.candidateId in remaining })?.candidateId)
        }
        assertEquals("exact", TissueMtcExerciseBridgeResolver.resolve(request, candidates)?.candidateId)
    }

    @Test
    fun transferContractForbidsDisplayNameAndTrainingRoleBiomechanics() {
        val transfers = TissueMetadataParser.table(asset("tissue_mtc_transfer_rule_v1.csv")).rows
        assertEquals((1..6).map(Int::toString), transfers.map { it.getValue("priority") })
        assertTrue(transfers.all { it["stableKeyMatch"] == "EXACT_ONLY" })
        assertTrue(transfers.all { it["displayNameMatching"] == "FORBIDDEN" })
        assertTrue(transfers.all { it["trainingRoleAsBiomechanicalKey"] == "FORBIDDEN" })
        assertTrue(TissueMetadataParser.table(asset("tissue_mtc_exact_stable_key_override_v1.csv")).rows.isEmpty())
        assertTrue(TissueMetadataParser.table(asset("tissue_mtc_variant_profile_v1.csv")).rows.isEmpty())
    }

    private fun candidate(id: String, stableKey: String, condition: String, family: String, level: TissueMtcInheritanceLevel) =
        TissueMtcResolutionCandidate(id, stableKey, condition, family, "KNEE_EXTENSOR_CONTACT_COMPLEX", TissueMtcAxis.M, 2.0, level)

    private val canonical by lazy { TissueMetadataParser.table(assetFile("../canonical_exercise_metadata_v0_3_5_0_pass3_1.csv").readText()).rows }
    private val movementRegistry by lazy { TissueMetadataParser.table(asset("tissue_mtc_movement_family_registry_v1.csv")).rows }
    private val mappings by lazy { TissueMtcExerciseBridgeParser.movementMappings(asset("tissue_mtc_exercise_movement_family_mapping_v1.csv")) }
    private val applicability by lazy { TissueMtcExerciseBridgeParser.applicability(asset("tissue_mtc_exercise_complex_applicability_v1.csv")) }
    private val traces by lazy { TissueMtcExerciseBridgeParser.traces(asset("tissue_mtc_fallback_resolution_trace_v1.csv")) }
    private val baseScales by lazy { TissueMtcParser.axisScales(asset("tissue_mtc_axis_scale_registry_v1.csv")) }
    private val bridgeScales by lazy { TissueMtcParser.axisScales(asset("tissue_mtc_bridge_axis_scale_registry_v1.csv")) }
    private val baseRules by lazy { TissueMtcParser.axisMetricRules(asset("tissue_mtc_axis_metric_registry_v1.csv")) }
    private val bridgeRules by lazy { TissueMtcParser.axisMetricRules(asset("tissue_mtc_bridge_complex_axis_metric_registry_v1.csv")) }
    private val complexes by lazy { TissueMtcParser.functionalComplexes(asset("tissue_functional_complex_registry_v1.csv")) }
    private val bridgeCoefficient by lazy { TissueMtcParser.coefficientSets(asset("tissue_mtc_coefficient_set_manifest_bridge_v1.csv")) }
    private val bridgeRubricReport by lazy { TissueMtcValidator.rubricFoundation(
        bridgeScales,
        TissueMtcParser.rubrics(asset("tissue_mtc_bridge_axis_rubric_v1.csv")),
        emptyList(),
        TissueMtcParser.fallbackRules(asset("tissue_mtc_bridge_fallback_rule_v1.csv")),
        bridgeCoefficient
    ) }
    private val bridgeReport by lazy {
        TissueMtcExerciseBridgeValidator.validate(canonical.map { it.getValue("stableKey") }.toSet(), mappings, applicability, traces, (baseScales + bridgeScales).map { it.axisScaleId }.toSet())
    }
    private fun asset(name: String) = assetFile(name).readText(Charsets.UTF_8)
    private fun assetFile(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists)
}
