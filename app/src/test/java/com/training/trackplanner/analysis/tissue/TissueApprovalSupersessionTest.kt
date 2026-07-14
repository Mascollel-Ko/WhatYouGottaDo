package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueApprovalSupersessionTest {
    @Test
    fun oldRequestPayloadIsPreservedAndSupersededWithoutApproval() {
        assertEquals(
            "c91fc47185a77cbe810bfcac8127f8be910530268502c6740ed53e69ee4a4ecc",
            approvalRequestFile.normalizedPayloadSha256()
        )
        val originalResolutions = resolutions.filter { it.approvalRequestId == requests.single().approvalRequestId }
        assertTrue(TissueEvidenceValidator.approvalRequestResolutions(originalResolutions, requests).isValid)
        assertEquals(
            TissueApprovalRequestResolutionStatus.SUPERSEDED_BEFORE_APPROVAL,
            resolutions.single { it.approvalRequestId == requests.single().approvalRequestId }.resolutionStatus
        )
        assertTrue(TissueEvidenceParser.batchApprovals(tissueAsset("tissue_review_batch_approval_v1.csv")).isEmpty())
    }

    @Test
    fun supersededScopeCannotBeIngestedEvenWithTheFormerExactStatement() {
        val request = requests.single()
        val report = TissueEvidenceValidator.approvalLedgerIngestion(
            approval = futureApproval(request),
            request = request,
            explicitUserStatement = request.requiredUserStatement,
            currentSourceVerificationSnapshotHash = request.sourceVerificationSnapshotHash,
            currentPublicationIntegritySnapshotHash = request.publicationIntegritySnapshotHash,
            requestResolutions = resolutions
        )

        assertFalse(report.isValid)
        assertTrue(report.errors.any { "superseded before approval" in it })
    }

    @Test
    fun humanResearchDirectivesCoverAllTwelveCandidatesWithoutProductionPromotion() {
        val candidates = TissueEvidenceParser.claimCandidates(tissueAsset("tissue_evidence_claim_candidates_v1.csv"))
        val report = TissueEvidenceValidator.humanResearchDirectives(directives, candidates)

        assertTrue(report.errors.toString(), report.isValid)
        assertEquals(12, directives.size)
        assertEquals(candidates.map { it.claimCandidateId }.toSet(), directives.map { it.claimCandidateId }.toSet())
        assertTrue(directives.all { it.directedByType == TissueActorType.HUMAN_USER })
        assertTrue(candidates.none { it.productionEligibility })
    }

    @Test
    fun explicitPfjMetricsAndPatellarStrainCannotCollapseIntoGenericCompressionOrForce() {
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val pfj = catalog.single { it.tissueId == "KNEE_PATELLOFEMORAL" }.supportedLoadDimensions
        val patellar = catalog.single { it.tissueId == "PATELLAR_TENDON" }.supportedLoadDimensions

        assertTrue(pfj.containsAll(setOf(
            TissueLoadDimension.PEAK_COMPRESSION,
            TissueLoadDimension.COMPRESSION_IMPULSE,
            TissueLoadDimension.COMPRESSION_LOADING_RATE,
            TissueLoadDimension.IMPACT_IMPULSE
        )))
        assertTrue(TissueLoadDimension.TENDON_STRAIN in patellar)
        assertFalse(TissueLoadDimension.COMPRESSION == TissueLoadDimension.PEAK_COMPRESSION)
        assertFalse(TissueLoadDimension.PEAK_TENSILE_LOAD == TissueLoadDimension.TENDON_STRAIN)
    }

    private fun futureApproval(request: TissueReviewBatchApprovalRequest) = TissueReviewBatchApproval(
        batchApprovalId = "forbidden-old-approval",
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
        humanApproverLabel = "fixture",
        humanApproverType = TissueActorType.HUMAN_USER,
        humanApprovedAt = "2026-07-14T00:02:00Z",
        automatedValidationPassed = true,
        approvalDecision = TissueBatchApprovalDecision.APPROVED,
        approvalNotes = "Must be rejected because the request is superseded."
    )

    private val requests by lazy { TissueEvidenceParser.approvalRequests(approvalRequestFile.readText(Charsets.UTF_8)) }
    private val resolutions by lazy {
        TissueEvidenceParser.approvalRequestResolutions(tissueAsset("tissue_approval_request_resolution_v1.csv"))
    }
    private val directives by lazy {
        TissueEvidenceParser.humanResearchDirectives(tissueAsset("tissue_human_research_directive_v1.csv"))
    }
    private val approvalRequestFile get() = assetFile("metadata/tissue_load_v1/tissue_review_batch_approval_request_v1.csv")
    private fun tissueAsset(name: String): String = assetFile("metadata/tissue_load_v1/$name").readText(Charsets.UTF_8)
    private fun assetFile(relative: String): File = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists)
    private fun File.normalizedPayloadSha256(): String =
        readText(Charsets.UTF_8).removePrefix("\uFEFF").replace("\r\n", "\n")
            .toByteArray(Charsets.UTF_8).sha256()
    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }
}
