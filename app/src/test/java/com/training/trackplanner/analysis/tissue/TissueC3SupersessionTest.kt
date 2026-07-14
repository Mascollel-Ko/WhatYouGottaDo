package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueC3SupersessionTest {
    @Test
    fun revisedRequestRemainsImmutableAndIsSupersededBeforeApproval() {
        assertEquals(
            "c5db161e3b6a0e8c04c89e38634561b4a2a14e1d4b5129e7be8f51ce23f853ab",
            revisedRequestFile.normalizedPayloadSha256()
        )

        val resolution = resolutions.single { it.approvalRequestId == revisedRequestId }
        assertEquals(revisedScopeHash, resolution.approvalScopeHash)
        assertEquals(TissueApprovalRequestResolutionStatus.SUPERSEDED_BEFORE_APPROVAL, resolution.resolutionStatus)
        assertEquals("TISSUE_C3_MULTIDIMENSIONAL_LOWER_R1", resolution.replacementResearchBatchId)
        assertTrue(TissueEvidenceParser.batchApprovals(tissueAsset("tissue_review_batch_approval_v1.csv")).isEmpty())
    }

    @Test
    fun revisedScopeCannotBeIngestedAfterSupersession() {
        val report = TissueEvidenceValidator.approvalLedgerIngestion(
            approval = approval,
            request = request,
            explicitUserStatement = request.requiredUserStatement,
            currentSourceVerificationSnapshotHash = request.sourceVerificationSnapshotHash,
            currentPublicationIntegritySnapshotHash = request.publicationIntegritySnapshotHash,
            requestResolutions = resolutions
        )

        assertFalse(report.isValid)
        assertTrue(report.errors.any { "superseded before approval" in it })
    }

    private val request = TissueReviewBatchApprovalRequest(
        approvalRequestId = revisedRequestId,
        reviewBatchId = "TISSUE_RESEARCH_C2A_R1_LOWER_REVISED",
        reviewPath = TissueFinalClaimReviewPath.SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
        claimCandidateIds = emptyList(),
        rubricIds = emptyList(),
        sourceIds = emptyList(),
        userAdjudicationIds = emptyList(),
        candidateCount = 0,
        rubricCount = 0,
        sourceCount = 0,
        auditManifestId = "historical",
        auditInputSnapshotHash = "historical",
        sourceVerificationSnapshotHash = "historical",
        publicationIntegritySnapshotHash = "historical",
        approvalScopeHash = revisedScopeHash,
        requestStatus = TissueApprovalRequestStatus.PENDING_HUMAN_DECISION,
        preparedBy = "Codex",
        preparedByType = TissueActorType.AI_AGENT,
        preparedAt = "2026-07-14T00:00:03Z",
        approvalSummary = "historical",
        knownLimitations = "historical",
        requiredUserStatement = "historical",
        requestNotes = "historical"
    )
    private val approval = TissueReviewBatchApproval(
        batchApprovalId = "forbidden-c2a-r1-approval",
        reviewBatchId = request.reviewBatchId,
        reviewPath = request.reviewPath,
        approvalRequestId = request.approvalRequestId,
        approvalScopeHash = request.approvalScopeHash,
        auditManifestId = request.auditManifestId,
        auditInputSnapshotHash = request.auditInputSnapshotHash,
        sourceVerificationSnapshotHash = request.sourceVerificationSnapshotHash,
        publicationIntegritySnapshotHash = request.publicationIntegritySnapshotHash,
        approvedClaimCandidateIds = emptyList(),
        approvedRubricIds = emptyList(),
        excludedClaimCandidateIds = emptyList(),
        excludedRubricIds = emptyList(),
        exclusionReasons = "",
        humanApproverLabel = "fixture",
        humanApproverType = TissueActorType.HUMAN_USER,
        humanApprovedAt = "2026-07-14T01:00:01Z",
        automatedValidationPassed = true,
        approvalDecision = TissueBatchApprovalDecision.APPROVED,
        approvalNotes = "Must be rejected because the request is superseded."
    )
    private val resolutions by lazy {
        TissueEvidenceParser.approvalRequestResolutions(tissueAsset("tissue_approval_request_resolution_v1.csv"))
    }
    private val revisedRequestFile get() = assetFile("metadata/tissue_load_v1/tissue_review_batch_approval_request_revised_v1.csv")
    private fun tissueAsset(name: String) = assetFile("metadata/tissue_load_v1/$name").readText(Charsets.UTF_8)
    private fun assetFile(relative: String) = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists)
    private fun File.normalizedPayloadSha256() =
        readText(Charsets.UTF_8).removePrefix("\uFEFF").replace("\r\n", "\n")
            .toByteArray(Charsets.UTF_8).sha256()
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val revisedRequestId = "TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD"
        const val revisedScopeHash = "74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f"
    }
}
