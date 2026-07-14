package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC2AR1EvidenceRevisionTest {
    @Test
    fun everyOldCandidateHasOneDispositionAndRevisedRowsRemainNonProduction() {
        val oldIds = oldCandidates.map { it.claimCandidateId }.toSet()

        assertEquals(12, dispositions.size)
        assertEquals(oldIds, dispositions.map { it.getValue("priorClaimCandidateId") }.toSet())
        assertEquals(oldIds, revised.map { it.getValue("priorClaimCandidateId") }.toSet())
        assertEquals(24, revised.size)
        assertTrue(revised.all { it.getValue("productionEligibility") == "false" })
        assertTrue(revised.all { row -> requiredClaimFields.all { row.getValue(it).isNotBlank() } })
    }

    @Test
    fun seatedCalfKeepsTheExactInternalForceMeaningWithoutAGenericLowOrLoadMultiplier() {
        val seated = revised.single { it.getValue("revisedClaimCandidateId") == "R1C_01_ACH_SEATED_PEAK" }

        assertEquals("PREFLIGHT_32658037", seated.getValue("sourceId"))
        assertEquals("BODY_WEIGHT_NORMALIZED_INTERNAL_TENDON_FORCE", seated.getValue("normalizationBasis"))
        assertTrue("15 kg" in seated.getValue("externalLoadCondition"))
        assertTrue("unilateral" in seated.getValue("externalLoadCondition"))
        assertEquals("", seated.getValue("maximumDefensibleBand"))
        assertFalse(revisedRubrics.any { it.getValue("anchorStableKeys") == "ex_5c8751d2" })
        assertFalse(revisedRubrics.any { it.getValue("loadBand") == "LOW" })
    }

    @Test
    fun peakRateAndImpulseStaySeparateAndComparableOnlyWithinTheirOwnUnits() {
        val standing = revised.filter { it.getValue("stableKey") == "ex_5ca7133f" }
        assertEquals(setOf("PEAK_TENSILE_LOAD", "LOADING_RATE"), standing.map { it.getValue("loadDimension") }.toSet())
        assertEquals(setOf("BW", "BW/s"), standing.map { it.getValue("unit") }.toSet())
        assertEquals("", standing.single { it.getValue("loadDimension") == "LOADING_RATE" }.getValue("maximumDefensibleBand"))

        pfjPriorIds.forEach { priorId ->
            assertEquals(
                setOf("PEAK_COMPRESSION", "COMPRESSION_IMPULSE", "COMPRESSION_LOADING_RATE"),
                revised.filter { it.getValue("priorClaimCandidateId") == priorId }
                    .map { it.getValue("loadDimension") }.toSet()
            )
        }
        assertFalse(revised.any { it.getValue("tissueId") == "KNEE_PATELLOFEMORAL" && it.getValue("loadDimension") == "COMPRESSION" })
    }

    @Test
    fun pfjAnomalyAuditComparesLikeMetricsAndAllowsSlowExerciseImpulse() {
        fun value(id: String) = revised.single { it.getValue("revisedClaimCandidateId") == id }.getValue("value").toDouble()

        assertTrue(value("R1C_11A_PFJ_DROP_JUMP_PEAK") > value("R1C_08A_PFJ_FULL_SQUAT_PEAK"))
        assertTrue(value("R1C_11C_PFJ_DROP_JUMP_RATE") > value("R1C_08C_PFJ_FULL_SQUAT_RATE"))
        assertTrue(value("R1C_12C_PFJ_MAX_HOP_RATE") > value("R1C_10C_PFJ_LUNGE_RATE"))
        assertTrue(value("R1C_10B_PFJ_LUNGE_IMPULSE") > value("R1C_11B_PFJ_DROP_JUMP_IMPULSE"))
    }

    @Test
    fun weightedAndVariantTransfersRemainExplicitlyBlockedOrConditionBounded() {
        val bodyweightSquat = revised.single { it.getValue("revisedClaimCandidateId") == "R1C_05_PAT_BW_SQUAT_PEAK" }
        val bulgarian = revised.single { it.getValue("revisedClaimCandidateId") == "R1C_06_PAT_BULGARIAN_PEAK" }
        val lunge = revised.single { it.getValue("revisedClaimCandidateId") == "R1C_10A_PFJ_LUNGE_PEAK" }

        assertEquals("BODYWEIGHT_TASK_CONDITION", bodyweightSquat.getValue("relativeLoadCondition"))
        assertTrue("cannot populate weighted" in bodyweightSquat.getValue("claimLimitations"))
        assertEquals("ex_e2efd0fe", bulgarian.getValue("stableKey"))
        assertNotEquals("ex_bb728af2", bulgarian.getValue("stableKey"))
        assertEquals("Non-jumping", lunge.getValue("landingCondition"))
        assertTrue("no jump-lunge" in lunge.getValue("claimLimitations"))
    }

    @Test
    fun researchMatrixCoversEveryRequiredTargetExactlyOnce() {
        val decisions = TissueEvidenceParser.researchDecisions(tissueAsset("tissue_rubric_research_log_revised_v1.csv"))

        assertEquals(requiredTargets, decisions.map { it.target.encoded }.toSet())
        assertEquals(requiredTargets.size, decisions.size)
        assertTrue(decisions.all { it.reviewBatchId == "TISSUE_RESEARCH_C2A_R1_LOWER_REVISED" })
        assertTrue(decisions.any { it.researchDecision == TissueResearchDecision.BLOCKED_MISSING_EXTERNAL_LOAD_MODEL })
        assertTrue(decisions.none { it.decisionReason.isBlank() })
    }

    @Test
    fun compositeMetricIsSourceSpecificAndOldPfjRubricsAreRemoved() {
        val metric = table("tissue_source_specific_metric_v1.csv").single()
        val rubricDispositions = table("tissue_rubric_disposition_v1.csv")

        assertEquals("STUDY_SPECIFIC_COMPOSITE_LOADING_INDEX", metric.getValue("metricRole"))
        assertEquals("NONE", metric.getValue("genericLoadDimension"))
        assertEquals("false", metric.getValue("productionEligibility"))
        assertTrue(rubricDispositions.filter { it.getValue("priorRubricId").startsWith("RUBRIC_PFJ") }
            .all { it.getValue("newDisposition") == "REMOVED_COMPOSITE_RUBRIC" })
        assertTrue(revisedRubrics.none { it.getValue("tissueId") == "KNEE_PATELLOFEMORAL" })
    }

    @Test
    fun canonicalAuditUsesExactStableKeysAndKeepsSplitSquatVariantsDistinct() {
        val byKey = mappings.associateBy { it.getValue("stableKey") }
        val canonical = canonicalRows.associateBy { it.getValue("stableKey") }

        assertEquals(expectedMappingKeys, byKey.keys)
        listOf("ex_e2efd0fe", "ex_bb728af2", "ex_f2a79d37").forEach { key ->
            assertTrue(key in byKey)
            assertEquals(canonical.getValue(key).getValue("exerciseName"), byKey.getValue(key).getValue("canonicalDisplayName"))
        }
        assertEquals(3, listOf("ex_e2efd0fe", "ex_bb728af2", "ex_f2a79d37").map { byKey.getValue(it).getValue("movementVariant") }.distinct().size)
        assertEquals("NON_JUMP", byKey.getValue("ex_64644b5e").getValue("jumpOrNonJump"))
        assertEquals("JUMP_HOP_OR_LANDING", byKey.getValue("ex_df966b45").getValue("jumpOrNonJump"))
    }

    private val oldCandidates by lazy { TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv")) }
    private val revised by lazy { table("tissue_evidence_claim_candidates_revised_v1.csv") }
    private val dispositions by lazy { table("tissue_claim_candidate_disposition_v1.csv") }
    private val revisedRubrics by lazy { table("tissue_load_band_rubric_revised_v1.csv") }
    private val mappings by lazy { table("tissue_canonical_exercise_mapping_audit_v1.csv") }
    private val canonicalRows by lazy { TissueMetadataParser.table(asset("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")).rows }
    private val expectedMappingKeys by lazy {
        canonicalRows.filter { it.getValue("movementFamily") in mappingFamilies }.map { it.getValue("stableKey") }.toSet()
    }
    private fun table(name: String) = TissueMetadataParser.table(tissueAsset(name)).rows
    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)

    private val requiredClaimFields = setOf(
        "revisedClaimCandidateId", "priorClaimCandidateId", "sourceId", "stableKey", "tissueId", "loadDimension",
        "testedExercise", "appExerciseCorrespondence", "externalLoadCondition", "relativeLoadCondition", "romCondition",
        "velocityCondition", "bilateralOrUnilateral", "surfaceCondition", "landingCondition", "measurementMethod",
        "metric", "unit", "normalizationBasis", "claimDirection", "claimLimitations", "evidenceLocator",
        "sourceVerificationStatus", "publicationIntegrityStatus", "disposition", "technicalStatus"
    )
    private val pfjPriorIds = setOf(
        "CLAIM_C_8A2D37D94C1330F7", "CLAIM_C_084C0B3DCC597268", "CLAIM_C_0777BA7A1FB88DF1",
        "CLAIM_C_E09E28E71A401E8D", "CLAIM_C_E540DF218D72E603", "CLAIM_C_4D02CF35465C7511"
    )
    private val requiredTargets = setOf(
        "ACHILLES_TENDON:PEAK_TENSILE_LOAD", "ACHILLES_TENDON:LOADING_RATE",
        "ACHILLES_TENDON:CYCLIC_TENSILE_LOAD", "ACHILLES_TENDON:ENERGY_STORAGE_RELEASE",
        "PATELLAR_TENDON:PEAK_TENSILE_LOAD", "PATELLAR_TENDON:TENDON_STRAIN",
        "PATELLAR_TENDON:CYCLIC_TENSILE_LOAD", "PATELLAR_TENDON:ECCENTRIC_LOAD",
        "PATELLAR_TENDON:LOADING_RATE", "KNEE_PATELLOFEMORAL:PEAK_COMPRESSION",
        "KNEE_PATELLOFEMORAL:COMPRESSION_IMPULSE", "KNEE_PATELLOFEMORAL:COMPRESSION_LOADING_RATE",
        "KNEE_PATELLOFEMORAL:IMPACT_IMPULSE"
    )
    private val mappingFamilies = setOf(
        "SQUAT_VARIANTS", "LUNGE_SPLIT_SQUAT_VARIANTS", "CALF_RAISE_ANKLE_STIFFNESS_VARIANTS",
        "PLYOMETRIC_JUMP_VARIANTS", "ANKLE_STIFFNESS_SSC_CONDITIONING",
        "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS", "BADMINTON_REACTIVE_LUNGE_FOOTWORK", "JUMP_LUNGE_PLYOMETRIC"
    )
}
