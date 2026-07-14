package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueC31SupersessionTest {
    @Test
    fun c3RequestRemainsImmutableAndIsSupersededBeforeApproval() {
        assertEquals(C3_REQUEST_SHA256, assetFile(C3_REQUEST_FILE).normalizedPayloadSha256())

        val resolution = resolutions.single { it.approvalRequestId == C3_REQUEST_ID }
        assertEquals(C3_SCOPE_HASH, resolution.approvalScopeHash)
        assertEquals(TissueApprovalRequestResolutionStatus.SUPERSEDED_BEFORE_APPROVAL, resolution.resolutionStatus)
        assertEquals("TISSUE_C3_1_ONTOLOGY_CORRECTION", resolution.replacementResearchBatchId)
        assertTrue(TissueEvidenceParser.batchApprovals(asset("tissue_review_batch_approval_v1.csv")).isEmpty())
    }

    @Test
    fun c3ScopeCannotBeIngestedAfterSupersession() {
        val request = TissueReviewBatchApprovalRequest(
            approvalRequestId = C3_REQUEST_ID,
            reviewBatchId = "TISSUE_C3_MULTIDIMENSIONAL_LOWER_R1",
            reviewPath = TissueFinalClaimReviewPath.SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
            claimCandidateIds = emptyList(), rubricIds = emptyList(), sourceIds = emptyList(),
            userAdjudicationIds = emptyList(), candidateCount = 0, rubricCount = 0, sourceCount = 0,
            auditManifestId = "historical", auditInputSnapshotHash = "historical",
            sourceVerificationSnapshotHash = "historical", publicationIntegritySnapshotHash = "historical",
            approvalScopeHash = C3_SCOPE_HASH, requestStatus = TissueApprovalRequestStatus.PENDING_HUMAN_DECISION,
            preparedBy = "Codex", preparedByType = TissueActorType.AI_AGENT,
            preparedAt = "2026-07-14T01:00:04Z", approvalSummary = "historical",
            knownLimitations = "historical", requiredUserStatement = "historical", requestNotes = "historical"
        )
        val approval = TissueReviewBatchApproval(
            batchApprovalId = "forbidden-c3-approval", reviewBatchId = request.reviewBatchId,
            reviewPath = request.reviewPath, approvalRequestId = request.approvalRequestId,
            approvalScopeHash = request.approvalScopeHash, auditManifestId = request.auditManifestId,
            auditInputSnapshotHash = request.auditInputSnapshotHash,
            sourceVerificationSnapshotHash = request.sourceVerificationSnapshotHash,
            publicationIntegritySnapshotHash = request.publicationIntegritySnapshotHash,
            approvedClaimCandidateIds = emptyList(), approvedRubricIds = emptyList(),
            excludedClaimCandidateIds = emptyList(), excludedRubricIds = emptyList(), exclusionReasons = "",
            humanApproverLabel = "fixture", humanApproverType = TissueActorType.HUMAN_USER,
            humanApprovedAt = "2026-07-15T00:00:00Z", automatedValidationPassed = true,
            approvalDecision = TissueBatchApprovalDecision.APPROVED,
            approvalNotes = "Must be rejected because the request is superseded."
        )

        val report = TissueEvidenceValidator.approvalLedgerIngestion(
            approval, request, request.requiredUserStatement, request.sourceVerificationSnapshotHash,
            request.publicationIntegritySnapshotHash, resolutions
        )
        assertFalse(report.isValid)
        assertTrue(report.errors.any { "superseded before approval" in it })
    }

    private val resolutions by lazy {
        TissueEvidenceParser.approvalRequestResolutions(asset("tissue_approval_request_resolution_v1.csv"))
    }
    private fun asset(name: String) = assetFile("metadata/tissue_load_v1/$name").readText(Charsets.UTF_8)
    private fun assetFile(relative: String) = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists)
    private fun File.normalizedPayloadSha256() = readText(Charsets.UTF_8).removePrefix("\uFEFF")
        .replace("\r\n", "\n").toByteArray(Charsets.UTF_8).sha256()
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val C3_REQUEST_FILE = "metadata/tissue_load_v1/tissue_review_batch_approval_request_c3_v1.csv"
        const val C3_REQUEST_ID = "TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B"
        const val C3_SCOPE_HASH = "48f86fee6c39d28b18e8ab9fbacd748e52a606db30c9b4cbfd377be4193162b8"
        const val C3_REQUEST_SHA256 = "c51f7fd111b2466addfc28317090c8d90eaf8e73c77169dd8dff9354b63a7378"
    }
}
