package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissuePhaseCReauditTest {
    @Test
    fun phaseCSchemasStartSeparateFromBlindAndFinalClaims() {
        assertTrue(TissueEvidenceParser.reaudits(tissueAsset("tissue_evidence_reaudit_v1.csv")).isEmpty())
        assertTrue(TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv")).isEmpty())
        assertTrue(TissueEvidenceParser.userAdjudications(tissueAsset("tissue_user_adjudication_v1.csv")).isEmpty())
        assertTrue(TissueEvidenceParser.blindReviews(tissueAsset("tissue_evidence_blind_review_v1.csv")).isEmpty())
        assertTrue(TissueEvidenceParser.finalClaims(tissueAsset("tissue_evidence_claims_v1.csv")).isEmpty())

        val reauditHeader = TissueMetadataParser.table(tissueAsset("tissue_evidence_reaudit_v1.csv")).header
        assertTrue(reauditHeader.containsAll(listOf("reviewMode", "independenceStatus", "recommendedAction")))
        val candidateHeader = TissueMetadataParser.table(tissueAsset("tissue_evidence_claim_candidates_v1.csv")).header
        assertTrue(candidateHeader.containsAll(listOf("technicalVerificationStatus", "productionEligibility")))
        val adjudicationHeader = TissueMetadataParser.table(tissueAsset("tissue_user_adjudication_v1.csv")).header
        assertTrue(adjudicationHeader.containsAll(listOf("isBatchApproval", "productionEligibilityEffect")))
    }

    @Test
    fun phaseCIdentifiersAreDeterministicAndBatchBound() {
        val reaudit = TissuePhaseCContract.reauditId("DCLM_ACH_PEAK_SINGLE_HOP", "PREFLIGHT_32658037")
        assertEquals(reaudit, TissuePhaseCContract.reauditId("DCLM_ACH_PEAK_SINGLE_HOP", "PREFLIGHT_32658037"))
        assertEquals(
            TissuePhaseCContract.claimCandidateId("DCLM_ACH_PEAK_SINGLE_HOP", reaudit),
            TissuePhaseCContract.claimCandidateId("DCLM_ACH_PEAK_SINGLE_HOP", reaudit)
        )
        assertEquals(
            setOf(
                "USER_ADJUDICATION_ACHILLES_HOP_TRANSFER_V1",
                "USER_ADJUDICATION_PFJ_COMPOSITE_COMPRESSION_V1"
            ),
            TissuePhaseCContract.adjudicationIds
        )
    }

    private fun tissueAsset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
