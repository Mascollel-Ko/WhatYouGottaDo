package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC31ResearchCorrectionTest {
    @Test
    fun aclStrainUsesTensionWithPreContactLandingContext() {
        val acl = extractions.filter { it.tissueId == "KNEE_ACL" && it.measurementMetric == TissueMeasurementMetric.MEASURED_LIGAMENT_STRAIN }
        assertEquals(2, acl.size)
        assertTrue(acl.all { it.mechanicalLoadMode == TissueC31MechanicalLoadMode.TENSION })
        assertTrue(acl.all { it.eventContext == TissueEventContext.JUMP_LANDING && it.movementPhase == TissueMovementPhase.PRE_CONTACT })
        assertTrue(acl.all { it.peakTimingRelativeToContactMs == -55.0 })
        assertFalse(candidates.any { it.tissueId == "KNEE_ACL" && it.mechanicalLoadMode != TissueC31MechanicalLoadMode.TENSION })
    }

    @Test
    fun weightedHeelRiseSeparatesAddedLoadFromTotalMass() {
        val row = extractions.single { it.id == "C31METRIC_35142563_ACH_HEEL_ADDED20BW" }
        assertEquals(0.20, row.additionalExternalLoadFractionBw!!, 0.0)
        assertEquals(1.20, row.totalSystemMassFractionBw!!, 0.0)
        assertEquals("Additional mass equal to 20% of bodyweight", row.externalLoadDescription)
        assertFalse(extractions.any { "1.2 bodyweight external-load" in it.externalLoadDescription.lowercase() })
    }

    @Test
    fun fullImpactAuditAndProxyGateFailClosed() {
        assertEquals(188, corrections.size)
        assertEquals(6, TissueMetadataParser.table(asset("tissue_source_reread_c3_1_v1.csv")).rows.size)
        assertTrue(report.errors.toString(), report.isValid)

        val invalid = extractions.first().copy(evidenceRelation = TissueEvidenceRelation.VALIDATED_PROXY, proxyMappingId = "")
        assertFalse(TissueC31Validator.correctedResearch(listOf(invalid) + extractions.drop(1), candidates, decisions, corrections, dimensions).isValid)
    }

    private val extractions by lazy { TissueC31Parser.scientificRows(asset("tissue_source_metric_extraction_c3_1_v1.csv"), "metricExtractionId") }
    private val candidates by lazy { TissueC31Parser.scientificRows(asset("tissue_evidence_claim_candidates_c3_1_v1.csv"), "claimCandidateId") }
    private val decisions by lazy { TissueC31Parser.researchDecisions(asset("tissue_research_decision_c3_1_v1.csv")) }
    private val corrections by lazy { TissueC31Parser.correctionDispositions(asset("tissue_c3_1_correction_disposition_v1.csv")) }
    private val dimensions by lazy { TissueC31Parser.dimensions(asset("tissue_load_dimension_registry_c3_1_v1.csv")) }
    private val report by lazy { TissueC31Validator.correctedResearch(extractions, candidates, decisions, corrections, dimensions) }
    private fun asset(name: String) = sequenceOf(File("src/main/assets/metadata/tissue_load_v1/$name"), File("app/src/main/assets/metadata/tissue_load_v1/$name")).first(File::exists).readText(Charsets.UTF_8)
}
