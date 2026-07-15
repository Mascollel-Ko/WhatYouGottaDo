package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC4AResearchCatalogTest {
    @Test
    fun researchSeedsAreStructuredAndRemainNonApprovalLeads() {
        assertEquals(130, seeds.size)
        assertEquals(100, seeds.count { it["verificationStatus"] != "UNVERIFIED_SEED" })
        assertEquals(3, seeds.count { it["verificationStatus"] == "METRIC_EXTRACTED" })
        assertEquals(12, seeds.count { it["language"] == "ko" })
        assertEquals(30, seeds.count { it["publicationIntegrityStatus"] == "NOT_CHECKED" })
        assertFalse(seeds.any { it["verificationStatus"]?.contains("APPROVED") == true })
        assertTrue(report.errors.toString(), report.isValid)
    }

    @Test
    fun c4aCorrectionsSeparateProtocolsAndPreserveCandidateExtractionParity() {
        assertEquals(49, extractions.size)
        assertEquals(49, conditions.size)
        assertEquals(30, candidates.size)
        assertEquals(15, conditions.map { it.getValue("sourceId") }.toSet().size)

        val hop = candidates.single { it["claimCandidateId"] == "C3_ACH_MAX_HOP_LANDING_STRAIN" }
        assertEquals("C3COND_35142563_HOPLAND", hop["sourceConditionId"])
        assertEquals("EVENT_AVERAGE", hop["temporalMetric"])
        assertEquals("BODYWEIGHT_TASK", hop["externalLoadRepresentation"])
        assertTrue(hop["additionalExternalLoadFractionBw"].isNullOrBlank())
        assertTrue(hop["totalSystemMassFractionBw"].isNullOrBlank())
        assertTrue(hop["externalLoadPlacement"].isNullOrBlank())

        val heel = candidates.single { it["claimCandidateId"] == "C31_ACH_WEIGHTED_HEEL_ADDED20BW_STRAIN" }
        assertEquals("C3COND_35142563_HEEL12BW", heel["sourceConditionId"])
        assertEquals("0.20", heel["additionalExternalLoadFractionBw"])
        assertEquals("1.20", heel["totalSystemMassFractionBw"])
        assertEquals("Weighted vest attached firmly to torso", heel["externalLoadPlacement"])
        assertTrue(report.errors.toString(), report.isValid)
    }

    @Test
    fun pclAndTibiofemoralRowsUseMeasuredQuantitySemantics() {
        val pcl = extractions.single { it["metricExtractionId"] == "C3METRIC_10656979_PCL_FORCE" }
        assertEquals("TENSION", pcl["mechanicalLoadMode"])
        assertEquals("MODELED_LIGAMENT_FORCE", pcl["measurementMetric"])
        val tfj = extractions.single { it["metricExtractionId"] == "C3METRIC_8947402_TFJ_SHEAR" }
        assertEquals("INTERSEGMENTAL_JOINT_FORCE_RESULTANT", tfj["measurementMetric"])
    }

    @Test
    fun missingLoadNeverRendersAsAUnitOnlyValue() {
        assertEquals("not reported", TissueMtcResearchCatalogValidator.formatLoad("", "BW"))
        assertEquals("not applicable", TissueMtcResearchCatalogValidator.formatLoad(null, "BW", notApplicable = true))
        assertEquals("0.20 BW", TissueMtcResearchCatalogValidator.formatLoad("0.20", "BW"))
    }

    @Test
    fun correctedResearchSnapshotSupersedesWithoutMutatingOperationalTraceRegime() {
        val bridge = TissueMtcParser.coefficientSets(asset("tissue_mtc_coefficient_set_manifest_bridge_v1.csv")).single()
        val research = TissueMtcParser.coefficientSets(asset("tissue_mtc_coefficient_set_manifest_research_v1.csv")).single()
        assertEquals("TISSUE_MTC_C4A_0_1_1", research.supersedesCoefficientSetId)
        assertEquals("TISSUE_MTC_C4A_0_1_2", research.coefficientSetId)
        assertEquals(TissueMetadataValidator.semanticCsvHash(asset("tissue_source_metric_extraction_c4a_v1.csv")), research.sourceSnapshotHash)
        assertFalse(bridge.sourceSnapshotHash == research.sourceSnapshotHash)
        assertEquals(bridge.rubricSnapshotHash, research.rubricSnapshotHash)
        val traces = table("tissue_mtc_fallback_resolution_trace_v1.csv")
        assertTrue(traces.all { it["coefficientSetId"] == "TISSUE_MTC_C4A_0_1_1" })
    }

    private val seeds by lazy { table("tissue_mtc_research_seed_catalog_v1.csv") }
    private val conditions by lazy { table("tissue_source_condition_registry_v1.csv") }
    private val extractions by lazy { table("tissue_source_metric_extraction_c4a_v1.csv") }
    private val candidates by lazy { table("tissue_evidence_claim_candidates_c4a_v1.csv") }
    private val gaps by lazy { table("tissue_mtc_research_gap_matrix_v1.csv") }
    private val report by lazy { TissueMtcResearchCatalogValidator.validate(seeds, conditions, extractions, candidates, gaps) }
    private fun table(name: String) = TissueMetadataParser.table(asset(name)).rows
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
