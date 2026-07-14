package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissuePhaseCReauditTest {
    @Test
    fun committedReauditCoversEveryDraftWithoutPromotingClaims() {
        val canonicalKeys = com.training.trackplanner.data.ExerciseMetadataAdapter.fromCsv(
            asset("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
        ).map { it.stableKey }.toSet()
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val sources = TissueEvidenceParser.sources(tissueAsset("tissue_load_evidence_registry_v1.csv"))
        val drafts = TissueEvidenceParser.draftClaims(tissueAsset("tissue_evidence_claims_draft_v1.csv"))
        val reaudits = TissueEvidenceParser.reaudits(tissueAsset("tissue_evidence_reaudit_v1.csv"))
        val candidates = TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))

        assertEquals(12, drafts.size)
        assertEquals(drafts.size, reaudits.size)
        assertEquals(12, candidates.size)
        assertTrue(reaudits.all(TissueEvidenceReaudit::evidenceLocatorVerified))
        assertTrue(reaudits.all { it.reviewMode == TissueEvidenceReviewMode.SAME_SESSION_EVIDENCE_REAUDIT })
        assertTrue(reaudits.all { it.independenceStatus == TissueReviewIndependenceStatus.NOT_INDEPENDENT })
        assertTrue(candidates.none(TissueEvidenceClaimCandidate::productionEligibility))
        assertTrue(candidates.all { it.humanApprovedBy.isBlank() && it.humanApprovedAt.isBlank() })
        assertTrue(TissueEvidenceValidator.phaseCReaudits(sources, drafts, reaudits, canonicalKeys, catalog).isValid)
        assertTrue(TissueEvidenceValidator.phaseCClaimCandidates(reaudits, candidates).isValid)

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
    fun correctedRangeAndRequiredProxyDisclosuresArePreserved() {
        val reaudits = TissueEvidenceParser.reaudits(tissueAsset("tissue_evidence_reaudit_v1.csv"))
        val candidates = TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))
        val seated = candidates.single { it.draftClaimId == "DCLM_ACH_PEAK_SEATED_CALF" }
        assertEquals(null, seated.candidateValue)
        assertEquals(0.5, seated.candidateLowerBound!!, 0.0)
        assertEquals(0.7, seated.candidateUpperBound!!, 0.0)
        assertTrue(seated.supportedCondition.contains("15 kg"))

        val achillesHop = candidates.single { it.draftClaimId == "DCLM_ACH_PEAK_SINGLE_HOP" }
        assertEquals(TissueExerciseCorrespondence.CLOSE_VARIANT, achillesHop.exerciseCorrespondence)
        assertEquals(TissueLoadBand.VERY_HIGH, achillesHop.maximumDefensibleBand)
        assertTrue(TissuePhaseCContract.achillesAdjudicationId in achillesHop.userAdjudicationIds)

        val pfj = candidates.filter { it.tissueId == "KNEE_PATELLOFEMORAL" }
        assertEquals(6, pfj.size)
        assertTrue(pfj.all { it.dimensionCorrespondence == TissueDimensionCorrespondence.DIMENSION_SUPPORTED_BY_EXPLICIT_PROXY })
        assertTrue(pfj.all { it.candidateClaimType.contains("COMPOSITE") && !it.candidateClaimType.contains("PURE") })
        assertTrue(reaudits.none { it.claimSupportStatus in setOf(
            TissueClaimSupportStatus.UNSUPPORTED,
            TissueClaimSupportStatus.CONTRADICTED,
            TissueClaimSupportStatus.UNABLE_TO_VERIFY
        ) })
    }

    @Test
    fun candidateValidatorRejectsProductionPromotion() {
        val reaudits = TissueEvidenceParser.reaudits(tissueAsset("tissue_evidence_reaudit_v1.csv"))
        val candidates = TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))
        val invalid = candidates.first().copy(productionEligibility = true, humanApprovedBy = "Codex")
        val report = TissueEvidenceValidator.phaseCClaimCandidates(reaudits, listOf(invalid) + candidates.drop(1))

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.contains("production eligibility") })
        assertTrue(report.errors.any { it.contains("human approval") })
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

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)
}
