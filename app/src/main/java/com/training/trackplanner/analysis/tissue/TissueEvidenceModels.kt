package com.training.trackplanner.analysis.tissue

enum class TissueIdentifierVerificationStatus {
    UNVERIFIED,
    PMID_VERIFIED,
    DOI_VERIFIED,
    PMID_AND_DOI_VERIFIED,
    IDENTIFIER_NOT_FOUND,
    IDENTIFIER_CONFLICT
}

enum class TissueBibliographicMatchStatus {
    UNVERIFIED,
    MATCHED,
    PARTIAL_MATCH,
    MISMATCHED
}

enum class TissueClaimVerificationStatus {
    UNREVIEWED,
    TITLE_ONLY,
    ABSTRACT_SUPPORTED,
    FULL_TEXT_SUPPORTED,
    TABLE_OR_FIGURE_SUPPORTED,
    PARTIALLY_SUPPORTED,
    NOT_SUPPORTED,
    CONTRADICTED,
    UNABLE_TO_VERIFY
}

enum class TissuePublicationIntegrityStatus {
    ACTIVE,
    CORRECTED,
    RETRACTED,
    EXPRESSION_OF_CONCERN,
    STATUS_UNKNOWN
}

enum class TissueNetworkCapabilityStatus {
    LIVE_SOURCE_VERIFICATION_AVAILABLE,
    PARTIAL_SOURCE_VERIFICATION_AVAILABLE,
    LIVE_SOURCE_VERIFICATION_UNAVAILABLE,
    NETWORK_PERMISSION_DENIED
}

enum class TissueClaimComparisonStatus {
    MATCH,
    PARTIAL_MATCH,
    BAND_DISAGREEMENT,
    CONDITION_MISMATCH,
    TISSUE_MISMATCH,
    DIMENSION_MISMATCH,
    UNSUPPORTED
}

data class TissueEvidenceSource(
    val sourceId: String,
    val pmid: String,
    val doi: String,
    val title: String,
    val identifierVerificationStatus: TissueIdentifierVerificationStatus,
    val bibliographicMatchStatus: TissueBibliographicMatchStatus,
    val publicationIntegrityStatus: TissuePublicationIntegrityStatus,
    val verificationCapabilityStatus: TissueNetworkCapabilityStatus,
    val sourceStatus: String
)

data class TissueDraftClaim(
    val draftClaimId: String,
    val sourceId: String,
    val stableKey: String,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val proposedBand: TissueLoadBand?,
    val claimValue: Double?,
    val claimUnit: String,
    val evidenceLocator: String,
    val preparedBy: String,
    val preparedByType: TissueActorType
)

data class TissueBlindReview(
    val blindReviewId: String,
    val draftClaimId: String,
    val sourceId: String,
    val stableKey: String,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val claimVerificationStatus: TissueClaimVerificationStatus,
    val blindReviewedBy: String,
    val blindReviewedByType: TissueActorType
)

data class TissueFinalClaim(
    val claimId: String,
    val draftClaimId: String,
    val blindReviewId: String,
    val sourceId: String,
    val stableKey: String,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val finalClaimValue: Double?,
    val finalClaimUnit: String,
    val evidenceLocator: String,
    val comparisonStatus: TissueClaimComparisonStatus,
    val identifierVerificationStatus: TissueIdentifierVerificationStatus,
    val bibliographicMatchStatus: TissueBibliographicMatchStatus,
    val claimVerificationStatus: TissueClaimVerificationStatus,
    val publicationIntegrityStatus: TissuePublicationIntegrityStatus,
    val preparedBy: String,
    val preparedByType: TissueActorType,
    val blindReviewedBy: String,
    val blindReviewedByType: TissueActorType,
    val humanApprovedBy: String,
    val productionEligibility: Boolean
)

data class TissueReviewBatchApproval(
    val reviewBatchId: String,
    val auditManifestId: String,
    val humanApprover: String,
    val humanApprovedAt: String,
    val automatedValidationPassed: Boolean,
    val approvalDecision: String
)
