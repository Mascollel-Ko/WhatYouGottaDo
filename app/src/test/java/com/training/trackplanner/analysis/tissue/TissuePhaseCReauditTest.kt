package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        val adjudications = TissueEvidenceParser.userAdjudications(tissueAsset("tissue_user_adjudication_v1.csv"))
        val rubrics = TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv"))
        val profiles = listOf(
            "exercise_joint_load_profiles_v1.csv",
            "exercise_tendon_load_profiles_v1.csv",
            "exercise_ligament_load_profiles_v1.csv",
            "exercise_fascia_load_profiles_v1.csv"
        ).flatMap { TissueMetadataParser.profiles(tissueAsset(it)) }
        val blind = TissueEvidenceParser.blindReviews(tissueAsset("tissue_evidence_blind_review_v1.csv"))
        val final = TissueEvidenceParser.finalClaims(tissueAsset("tissue_evidence_claims_v1.csv"))
        val approvals = TissueEvidenceParser.batchApprovals(tissueAsset("tissue_review_batch_approval_v1.csv"))

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
        assertTrue(TissueEvidenceValidator.phaseCAdjudications(adjudications).isValid)
        assertTrue(TissueEvidenceValidator.phaseCRubrics(rubrics, reaudits, candidates, adjudications).isValid)
        assertTrue(TissueEvidenceValidator.phaseCNonProduction(blind, final, approvals, profiles).isValid)

        val reauditHeader = TissueMetadataParser.table(tissueAsset("tissue_evidence_reaudit_v1.csv")).header
        assertTrue(reauditHeader.containsAll(listOf("reviewMode", "independenceStatus", "recommendedAction")))
        val candidateHeader = TissueMetadataParser.table(tissueAsset("tissue_evidence_claim_candidates_v1.csv")).header
        assertTrue(candidateHeader.containsAll(listOf("technicalVerificationStatus", "productionEligibility")))
        val adjudicationHeader = TissueMetadataParser.table(tissueAsset("tissue_user_adjudication_v1.csv")).header
        assertTrue(adjudicationHeader.containsAll(listOf("isBatchApproval", "productionEligibilityEffect")))
    }

    @Test
    fun explicitAdjudicationsPreserveScopeWithoutGrantingApproval() {
        val adjudications = TissueEvidenceParser.userAdjudications(tissueAsset("tissue_user_adjudication_v1.csv"))
        assertEquals(2, adjudications.size)

        val achilles = adjudications.single { it.adjudicationId == TissuePhaseCContract.achillesAdjudicationId }
        assertEquals(listOf("ex_314df428"), achilles.stableKeys)
        assertEquals(listOf("ACHILLES_TENDON"), achilles.tissueIds)
        assertEquals(listOf(TissueLoadDimension.PEAK_TENSILE_LOAD), achilles.loadDimensions)
        assertTrue(achilles.requiredDisclosure.contains("CLOSE_VARIANT"))
        assertTrue(achilles.requiredDisclosure.contains("CLOSE_VARIANT_TRANSFER"))
        assertTrue(achilles.requiredDisclosure.contains("VERY_HIGH"))

        val pfj = adjudications.single { it.adjudicationId == TissuePhaseCContract.pfjAdjudicationId }
        assertEquals(listOf("KNEE_PATELLOFEMORAL"), pfj.tissueIds)
        assertEquals(listOf(TissueLoadDimension.COMPRESSION), pfj.loadDimensions)
        assertTrue(pfj.requiredDisclosure.contains("DIMENSION_SUPPORTED_BY_EXPLICIT_PROXY"))
        assertTrue(pfj.requiredDisclosure.contains("composite", ignoreCase = true))
        assertTrue(pfj.requiredDisclosure.contains("pure peak compression force", ignoreCase = true))

        assertTrue(adjudications.all { !it.isBatchApproval })
        assertTrue(adjudications.all { it.productionEligibilityEffect == TissueProductionEligibilityEffect.NONE })
    }

    @Test
    fun everyRubricIsReauditedAndRemainsPendingHumanApproval() {
        val reaudits = TissueEvidenceParser.reaudits(tissueAsset("tissue_evidence_reaudit_v1.csv"))
        val candidates = TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))
        val adjudications = TissueEvidenceParser.userAdjudications(tissueAsset("tissue_user_adjudication_v1.csv"))
        val rubrics = TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv"))

        assertEquals(5, rubrics.size)
        assertEquals(2, rubrics.count { it.reauditAction == TissueRubricReauditAction.RETAIN_WITH_LIMITATIONS })
        assertEquals(3, rubrics.count { it.reauditAction in setOf(TissueRubricReauditAction.CORRECT_ANCHOR, TissueRubricReauditAction.CORRECT_METRIC) })
        assertTrue(rubrics.all { it.rubricStatus == TissueRubricStatus.REAUDITED_WITH_LIMITATIONS })
        assertTrue(rubrics.all { it.reauditIds.isNotEmpty() && it.claimCandidateIds.isNotEmpty() })
        assertTrue(rubrics.all { it.reviewMode == TissueEvidenceReviewMode.SAME_SESSION_EVIDENCE_REAUDIT })
        assertTrue(rubrics.all { it.independenceStatus == TissueReviewIndependenceStatus.NOT_INDEPENDENT })
        assertTrue(rubrics.none { it.loadBand == TissueLoadBand.HIGH })

        val achillesHop = rubrics.single { it.rubricId == "RUBRIC_ACH_PEAK_VERY_HIGH" }
        assertEquals(listOf(TissuePhaseCContract.achillesAdjudicationId), achillesHop.userAdjudicationIds)
        val pfj = rubrics.filter { it.tissueId == "KNEE_PATELLOFEMORAL" }
        assertTrue(pfj.all { it.userAdjudicationIds == listOf(TissuePhaseCContract.pfjAdjudicationId) })
        assertTrue(pfj.all { it.metricType == "COMPOSITE_PATELLOFEMORAL_JOINT_LOADING_INDEX_50_PERCENT_PEAK_50_PERCENT_IMPULSE" })

        val report = TissueEvidenceValidator.phaseCRubrics(rubrics, reaudits, candidates, adjudications)
        assertTrue(report.errors.toString(), report.isValid)
    }

    @Test
    fun phaseCAuditManifestRemainsAnImmutableHistoricalSnapshot() {
        val audit = TissueMetadataParser.auditManifests(tissueAsset("tissue_metadata_audit_manifest_v1.csv"))
            .single { it.values.getValue("auditBatchId") == TissuePhaseCContract.reviewBatchId }
        val values = audit.values
        val hashes = mapOf(
            "evidenceRegistry" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_load_evidence_registry_v1.csv")),
            "sourceVerification" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_source_verification_v1.csv")),
            "draftClaims" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_evidence_claims_draft_v1.csv")),
            "reaudits" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_evidence_reaudit_v1.csv")),
            "claimCandidates" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_evidence_claim_candidates_v1.csv")),
            "userAdjudications" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_user_adjudication_v1.csv")),
            "rubrics" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_load_band_rubric_v1.csv")),
            "targetReviews" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_rubric_target_exercise_review_v1.csv"))
        )

        assertEquals("94c15f4d43e843cd0238b1dd276d83e962bdf846a28a110330c5a970c0a64463", audit.inputSnapshotHash)
        assertNotEquals(TissueMetadataValidator.combinedHash(hashes), audit.inputSnapshotHash)
        assertEquals(hashes.getValue("reaudits"), values.getValue("reauditSnapshotHash"))
        assertEquals(hashes.getValue("claimCandidates"), values.getValue("claimCandidateSnapshotHash"))
        assertEquals(hashes.getValue("userAdjudications"), values.getValue("userAdjudicationSnapshotHash"))
        assertEquals(hashes.getValue("targetReviews"), values.getValue("targetExerciseReviewSnapshotHash"))
        assertEquals("EVIDENCE_REAUDIT_COMPLETE_PENDING_BATCH_APPROVAL", values.getValue("completionStatus"))
        assertEquals("SAME_SESSION_EVIDENCE_REAUDIT", values.getValue("reviewMode"))
        assertEquals("NOT_INDEPENDENT", values.getValue("independenceStatus"))
        assertEquals("12", values.getValue("reauditRowCount"))
        assertEquals("12", values.getValue("claimCandidateCount"))
        assertEquals("2", values.getValue("userAdjudicationCount"))
        assertEquals("4", values.getValue("retainedClaimCount"))
        assertEquals("8", values.getValue("correctedClaimCount"))
        assertEquals("0", values.getValue("blockedClaimCount"))
        assertEquals("2", values.getValue("retainedRubricCount"))
        assertEquals("3", values.getValue("correctedRubricCount"))
        assertEquals("0", values.getValue("blockedRubricCount"))
        assertEquals("0", values.getValue("formalFinalClaimCount"))
        assertEquals("0", values.getValue("humanBatchApprovalCount"))
        assertEquals("0", values.getValue("productionProfileCount"))
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
    fun rubricValidatorRejectsUnknownCandidateAdjudication() {
        val reaudits = TissueEvidenceParser.reaudits(tissueAsset("tissue_evidence_reaudit_v1.csv"))
        val candidates = TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))
        val adjudications = TissueEvidenceParser.userAdjudications(tissueAsset("tissue_user_adjudication_v1.csv"))
        val rubrics = TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv"))
        val invalid = candidates.first().copy(userAdjudicationIds = listOf("UNKNOWN_ADJUDICATION"))

        val report = TissueEvidenceValidator.phaseCRubrics(
            rubrics,
            reaudits,
            listOf(invalid) + candidates.drop(1),
            adjudications
        )

        assertFalse(report.isValid)
        assertTrue(report.errors.any { it.contains("unknown user adjudication link") })
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
