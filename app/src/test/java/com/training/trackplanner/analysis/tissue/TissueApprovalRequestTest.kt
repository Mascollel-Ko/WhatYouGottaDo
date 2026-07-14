package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueApprovalRequestTest {
    @Test
    fun committedRequestCoversTheExactValidatedPendingScope() {
        val request = requests.single()
        val report = TissueEvidenceValidator.approvalRequests(
            requests, candidates, rubrics, sources, sourceVerifications, integrityRows, adjudications, audits
        )

        assertTrue(report.errors.toString(), report.isValid)
        assertEquals(TissueApprovalRequestStatus.PENDING_HUMAN_DECISION, request.requestStatus)
        assertEquals(12, request.candidateCount)
        assertEquals(5, request.rubricCount)
        assertEquals(10, request.sourceCount)
        assertEquals(2, request.userAdjudicationIds.size)
        assertEquals(request.requiredApprovalStatement(), request.requiredUserStatement)
        assertTrue(TissueEvidenceParser.batchApprovals(tissueAsset("tissue_review_batch_approval_v1.csv")).isEmpty())
    }

    @Test
    fun approvalScopeHashIsOrderIndependentButScientificChangesInvalidateIt() {
        val request = requests.single()
        val audit = audits.single { it.auditManifestId == request.auditManifestId }
        fun hash(candidateRows: List<TissueEvidenceClaimCandidate>) = TissueApprovalScopeHasher.hash(
            request.reviewPath,
            candidateRows,
            rubrics.reversed(),
            sources.reversed(),
            sourceVerifications.reversed(),
            integrityRows.reversed(),
            adjudications.map(TissueUserAdjudication::adjudicationId).reversed(),
            audit.auditManifestId,
            audit.inputSnapshotHash
        )

        assertEquals(request.approvalScopeHash, hash(candidates.reversed()))
        assertNotEquals(request.approvalScopeHash, hash(candidates.updatedFirst { copy(candidateValue = 999.0) }))
        assertEquals(request.approvalScopeHash, hash(candidates.updatedFirst {
            copy(candidateNotes = "Harmless note change", preparedAt = "2099-01-01T00:00:00Z")
        }))
    }

    @Test
    fun invalidCandidateCannotHideInsideTheRequest() {
        val invalid = candidates.updatedFirst {
            copy(technicalVerificationStatus = TissueTechnicalVerificationStatus.BLOCKED)
        }
        val report = TissueEvidenceValidator.approvalRequests(
            requests, invalid, rubrics, sources, sourceVerifications, integrityRows, adjudications, audits
        )

        assertFalse(report.isValid)
        assertTrue(report.errors.any { "invalid candidate or rubric was included" in it })
        assertTrue(report.errors.any { "approval-scope hash does not match" in it })
    }

    @Test
    fun approvalLedgerRequiresTheExactStatementScopeAndLaterTimestamp() {
        val request = requests.single()
        val approval = TissueReviewBatchApproval(
            batchApprovalId = "future-approval",
            reviewBatchId = request.reviewBatchId,
            reviewPath = request.reviewPath,
            approvalRequestId = request.approvalRequestId,
            approvalScopeHash = request.approvalScopeHash,
            auditManifestId = request.auditManifestId,
            auditInputSnapshotHash = request.auditInputSnapshotHash,
            sourceVerificationSnapshotHash = request.sourceVerificationSnapshotHash,
            publicationIntegritySnapshotHash = request.publicationIntegritySnapshotHash,
            approvedClaimCandidateIds = request.claimCandidateIds,
            approvedRubricIds = request.rubricIds,
            excludedClaimCandidateIds = emptyList(),
            excludedRubricIds = emptyList(),
            exclusionReasons = "",
            humanApproverLabel = "ChatGPT user",
            humanApproverType = TissueActorType.HUMAN_USER,
            humanApprovedAt = "2026-07-14T00:01:00Z",
            automatedValidationPassed = true,
            approvalDecision = TissueBatchApprovalDecision.APPROVED,
            approvalNotes = "Future fixture only."
        )
        val exact = TissueEvidenceValidator.approvalLedgerIngestion(
            approval,
            request,
            request.requiredUserStatement,
            request.sourceVerificationSnapshotHash,
            request.publicationIntegritySnapshotHash
        )
        assertTrue(exact.errors.toString(), exact.isValid)

        val casual = TissueEvidenceValidator.approvalLedgerIngestion(
            approval,
            request,
            "continue",
            request.sourceVerificationSnapshotHash,
            request.publicationIntegritySnapshotHash
        )
        assertFalse(casual.isValid)
        assertTrue(casual.errors.any { "does not exactly approve" in it })
    }

    @Test
    fun c2aAuditRecordsOneRequestAndNoPromotion() {
        val request = requests.single()
        val audit = audits.single { it.auditManifestId == request.auditManifestId }.values

        assertEquals("APPROVAL_PACKAGE_READY", audit.getValue("completionStatus"))
        assertEquals("1", audit.getValue("approvalRequestCount"))
        assertEquals("12", audit.getValue("approvalRequestCandidateCount"))
        assertEquals("5", audit.getValue("approvalRequestRubricCount"))
        assertEquals("10", audit.getValue("approvalRequestSourceCount"))
        assertEquals(request.approvalScopeHash, audit.getValue("approvalScopeHash"))
        assertEquals("0", audit.getValue("humanBatchApprovalCount"))
        assertEquals("0", audit.getValue("formalFinalClaimCount"))
        assertEquals("0", audit.getValue("productionProfileCount"))
    }

    private fun List<TissueEvidenceClaimCandidate>.updatedFirst(
        transform: TissueEvidenceClaimCandidate.() -> TissueEvidenceClaimCandidate
    ): List<TissueEvidenceClaimCandidate> = listOf(first().transform()) + drop(1)

    private val candidates by lazy {
        TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))
    }
    private val rubrics by lazy { TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv")) }
    private val sources by lazy { TissueEvidenceParser.sources(tissueAsset("tissue_load_evidence_registry_v1.csv")) }
    private val sourceVerifications by lazy {
        TissueEvidenceParser.sourceVerifications(tissueAsset("tissue_source_verification_v1.csv"))
    }
    private val integrityRows by lazy {
        TissueEvidenceParser.publicationIntegrityVerifications(
            tissueAsset("tissue_publication_integrity_verification_v1.csv")
        )
    }
    private val adjudications by lazy {
        TissueEvidenceParser.userAdjudications(tissueAsset("tissue_user_adjudication_v1.csv"))
    }
    private val requests by lazy {
        TissueEvidenceParser.approvalRequests(tissueAsset("tissue_review_batch_approval_request_v1.csv"))
    }
    private val audits by lazy {
        TissueMetadataParser.auditManifests(tissueAsset("tissue_metadata_audit_manifest_v1.csv"))
    }

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)
}
