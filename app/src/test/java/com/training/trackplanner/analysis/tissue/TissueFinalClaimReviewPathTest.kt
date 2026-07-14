package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueFinalClaimReviewPathTest {
    @Test
    fun sameSessionPathAcceptsOnlyTheExactApprovedCandidate() {
        assertValid(validate(sameSessionClaim()))
    }

    @Test
    fun blindPathRequiresARealIndependentReview() {
        val claim = blindClaim()
        assertValid(validate(claim, blindReviews = listOf(blindReview()), approval = blindApproval()))

        val missing = validate(claim, blindReviews = emptyList(), approval = blindApproval())
        assertInvalid(missing, "blind path requires a valid blind review")

        val mixed = validate(
            claim.copy(reauditId = candidate.reauditId, claimCandidateId = candidate.claimCandidateId),
            blindReviews = listOf(blindReview()),
            approval = blindApproval()
        )
        assertInvalid(mixed, "blind path cannot populate same-session candidate fields")
    }

    @Test
    fun sameSessionPathRejectsMissingLinksAndFabricatedBlindReview() {
        val missing = validate(sameSessionClaim(), reaudits = emptyList(), candidates = emptyList())
        assertInvalid(missing, "same-session path requires a valid re-audit")
        assertInvalid(missing, "same-session path requires a valid claim candidate")

        val fabricated = validate(
            sameSessionClaim().copy(
                blindReviewId = "fabricated",
                blindReviewedBy = "Fabricated reviewer",
                blindReviewedByType = TissueActorType.HUMAN,
                blindReviewedAt = NOW
            )
        )
        assertInvalid(fabricated, "same-session path cannot contain fabricated blind-review fields")
    }

    @Test
    fun sameSessionPathRejectsCandidateIdentityAndValueChanges() {
        val identity = validate(sameSessionClaim().copy(stableKey = "ex_wrong"), canonicalKeys = setOf("ex_wrong"))
        assertInvalid(identity, "final claim scientific payload differs from approved candidate")

        val value = validate(sameSessionClaim().copy(finalClaimValue = 99.0))
        assertInvalid(value, "final claim scientific payload differs from approved candidate")
    }

    @Test
    fun finalClaimRequiresOneExplicitReviewPath() {
        val report = validate(sameSessionClaim().copy(reviewPath = null))
        assertInvalid(report, "explicit reviewPath is required")
    }

    @Test
    fun approvalMustBeActiveExactAndFresh() {
        val hashMismatch = validate(
            sameSessionClaim().copy(approvalScopeHash = "different")
        )
        assertInvalid(hashMismatch, "approval identity or snapshot hash does not match")

        val stale = validate(
            sameSessionClaim(),
            approval = sameSessionApproval().copy(auditInputSnapshotHash = "stale")
        )
        assertInvalid(stale, "approval audit snapshot is stale or missing")

        val stalePublication = validate(
            sameSessionClaim(),
            approval = sameSessionApproval().copy(publicationIntegritySnapshotHash = "stale")
        )
        assertInvalid(stalePublication, "approval source or publication snapshot is stale or missing")

        val revoked = validate(
            sameSessionClaim(),
            approval = sameSessionApproval().copy(approvalDecision = TissueBatchApprovalDecision.REVOKED)
        )
        assertInvalid(revoked, "batch approval is rejected, revoked, or invalid")
    }

    private fun validate(
        claim: TissueFinalClaim,
        blindReviews: List<TissueBlindReview> = emptyList(),
        reaudits: List<TissueEvidenceReaudit> = listOf(reaudit),
        candidates: List<TissueEvidenceClaimCandidate> = listOf(candidate),
        approval: TissueReviewBatchApproval = sameSessionApproval(),
        canonicalKeys: Set<String> = setOf(candidate.stableKey)
    ): TissueValidationReport = TissueEvidenceValidator.validate(
        sources = listOf(source),
        drafts = listOf(draft),
        blindReviews = blindReviews,
        finalClaims = listOf(claim),
        canonicalStableKeys = canonicalKeys,
        catalog = catalog,
        reaudits = reaudits,
        candidates = candidates,
        approvals = listOf(approval),
        rubrics = rubrics,
        auditManifests = listOf(audit),
        sourceVerifications = listOf(sourceVerification),
        publicationIntegrityVerifications = listOf(integrity)
    )

    private fun sameSessionClaim() = TissueFinalClaim(
        claimId = "FINAL_${candidate.claimCandidateId}",
        reviewBatchId = candidate.reviewBatchId,
        reviewPath = TissueFinalClaimReviewPath.SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
        draftClaimId = candidate.draftClaimId,
        blindReviewId = "",
        reauditId = candidate.reauditId,
        claimCandidateId = candidate.claimCandidateId,
        batchApprovalId = APPROVAL_ID,
        sourceId = candidate.sourceId,
        stableKey = candidate.stableKey,
        tissueId = candidate.tissueId,
        loadDimension = candidate.loadDimension,
        finalClaimType = candidate.candidateClaimType,
        finalClaimParaphrase = candidate.candidateClaimParaphrase,
        finalClaimDirection = candidate.candidateClaimDirection,
        finalClaimValue = candidate.candidateValue,
        finalClaimLowerBound = candidate.candidateLowerBound,
        finalClaimUpperBound = candidate.candidateUpperBound,
        finalClaimUnit = candidate.candidateUnit,
        normalizationBasis = candidate.normalizationBasis,
        supportedCondition = candidate.supportedCondition,
        measurementMethod = candidate.measurementMethod,
        evidenceLocatorType = candidate.evidenceLocatorType,
        evidenceLocator = candidate.evidenceLocator,
        evidenceAccessLevel = candidate.evidenceAccessLevel,
        exerciseCorrespondence = candidate.exerciseCorrespondence,
        tissueCorrespondence = candidate.tissueCorrespondence,
        dimensionCorrespondence = candidate.dimensionCorrespondence,
        crossStudyComparability = candidate.crossStudyComparability,
        maximumDefensibleBand = candidate.maximumDefensibleBand,
        bandBasis = candidate.bandBasis,
        claimSupportStatus = candidate.claimSupportStatus,
        confidenceLevel = candidate.confidenceLevel,
        comparisonStatus = TissueClaimComparisonStatus.MATCH,
        identifierVerificationStatus = source.identifierVerificationStatus,
        bibliographicMatchStatus = source.bibliographicMatchStatus,
        claimVerificationStatus = TissueClaimVerificationStatus.TABLE_OR_FIGURE_SUPPORTED,
        publicationIntegrityStatus = source.publicationIntegrityStatus,
        approvalAuditManifestId = audit.auditManifestId,
        approvalScopeHash = SCOPE_HASH,
        sourceVerificationSnapshotHash = sourceVerificationSnapshotHash,
        preparedBy = candidate.preparedBy,
        preparedByType = candidate.preparedByType,
        preparedAt = candidate.preparedAt,
        blindReviewedBy = "",
        blindReviewedByType = null,
        blindReviewedAt = "",
        humanApprovedBy = HUMAN,
        humanApprovedByType = TissueActorType.HUMAN_USER,
        humanApprovedAt = NOW,
        productionEligibility = false,
        finalClaimNotes = "Test fixture only."
    )

    private fun blindClaim() = sameSessionClaim().copy(
        reviewPath = TissueFinalClaimReviewPath.INDEPENDENT_BLIND_REVIEW,
        blindReviewId = BLIND_ID,
        reauditId = "",
        claimCandidateId = "",
        blindReviewedBy = REVIEWER,
        blindReviewedByType = TissueActorType.HUMAN,
        blindReviewedAt = NOW
    )

    private fun blindReview() = TissueBlindReview(
        blindReviewId = BLIND_ID,
        draftClaimId = draft.draftClaimId,
        sourceId = draft.sourceId,
        stableKey = draft.stableKey,
        tissueId = draft.tissueId,
        loadDimension = draft.loadDimension,
        claimVerificationStatus = TissueClaimVerificationStatus.TABLE_OR_FIGURE_SUPPORTED,
        blindReviewedBy = REVIEWER,
        blindReviewedByType = TissueActorType.HUMAN
    )

    private fun sameSessionApproval() = approval(
        TissueFinalClaimReviewPath.SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
        approvedCandidates = listOf(candidate.claimCandidateId),
        approvedRubrics = rubrics.map(TissueLoadRubric::rubricId)
    )

    private fun blindApproval() = approval(TissueFinalClaimReviewPath.INDEPENDENT_BLIND_REVIEW)

    private fun approval(
        path: TissueFinalClaimReviewPath,
        approvedCandidates: List<String> = emptyList(),
        approvedRubrics: List<String> = emptyList()
    ) = TissueReviewBatchApproval(
        batchApprovalId = APPROVAL_ID,
        reviewBatchId = candidate.reviewBatchId,
        reviewPath = path,
        approvalRequestId = "request",
        approvalScopeHash = SCOPE_HASH,
        auditManifestId = audit.auditManifestId,
        auditInputSnapshotHash = audit.inputSnapshotHash,
        sourceVerificationSnapshotHash = sourceVerificationSnapshotHash,
        publicationIntegritySnapshotHash = publicationIntegritySnapshotHash,
        approvedClaimCandidateIds = approvedCandidates,
        approvedRubricIds = approvedRubrics,
        excludedClaimCandidateIds = emptyList(),
        excludedRubricIds = emptyList(),
        exclusionReasons = "",
        humanApproverLabel = HUMAN,
        humanApproverType = TissueActorType.HUMAN_USER,
        humanApprovedAt = NOW,
        automatedValidationPassed = true,
        approvalDecision = TissueBatchApprovalDecision.APPROVED,
        approvalNotes = "Test fixture only."
    )

    private fun assertValid(report: TissueValidationReport) =
        assertTrue(report.errors.toString(), report.isValid)

    private fun assertInvalid(report: TissueValidationReport, message: String) {
        assertFalse(report.isValid)
        assertTrue(report.errors.toString(), report.errors.any { message in it })
    }

    private val source by lazy {
        TissueEvidenceParser.sources(tissueAsset("tissue_load_evidence_registry_v1.csv"))
            .single { it.sourceId == candidate.sourceId }
    }
    private val integrity by lazy {
        TissueEvidenceParser.publicationIntegrityVerifications(
            tissueAsset("tissue_publication_integrity_verification_v1.csv")
        ).single { it.sourceId == candidate.sourceId }
    }
    private val sourceVerification by lazy {
        TissueEvidenceParser.sourceVerifications(tissueAsset("tissue_source_verification_v1.csv"))
            .single { it.sourceId == candidate.sourceId }
    }
    private val sourceVerificationSnapshotHash by lazy {
        TissueApprovalScopeHasher.sourceVerificationSnapshotHash(listOf(sourceVerification))
    }
    private val publicationIntegritySnapshotHash by lazy {
        TissueApprovalScopeHasher.publicationIntegritySnapshotHash(listOf(integrity))
    }
    private val draft by lazy {
        TissueEvidenceParser.draftClaims(tissueAsset("tissue_evidence_claims_draft_v1.csv"))
            .single { it.draftClaimId == candidate.draftClaimId }
    }
    private val reaudit by lazy {
        TissueEvidenceParser.reaudits(tissueAsset("tissue_evidence_reaudit_v1.csv"))
            .single { it.reauditId == candidate.reauditId }
    }
    private val candidate by lazy {
        TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))
            .single { it.draftClaimId == "DCLM_ACH_PEAK_SINGLE_CALF" }
    }
    private val rubrics by lazy {
        TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv"))
            .filter { candidate.claimCandidateId in it.claimCandidateIds }
    }
    private val audit by lazy {
        TissueMetadataParser.auditManifests(tissueAsset("tissue_metadata_audit_manifest_v1.csv"))
            .single { it.values["auditBatchId"] == candidate.reviewBatchId }
    }
    private val catalog by lazy {
        TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
    }

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)

    private companion object {
        const val APPROVAL_ID = "approval"
        const val BLIND_ID = "blind"
        const val HUMAN = "ChatGPT user"
        const val NOW = "2026-07-14T00:00:00Z"
        const val REVIEWER = "Independent reviewer"
        const val SCOPE_HASH = "scope"
    }
}
