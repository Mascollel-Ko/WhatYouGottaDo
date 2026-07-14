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

enum class TissueResearchDecision {
    DRAFT_RUBRIC_CREATED,
    EVIDENCE_FOUND_BUT_NOT_COMPARABLE,
    CONFLICTING_EVIDENCE,
    BLOCKED_INSUFFICIENT_EVIDENCE,
    OUT_OF_SCOPE_AFTER_AUDIT
}

enum class TissueResearchUseStatus {
    USED_AS_DIRECT_ANCHOR,
    USED_AS_TRANSFER_REFERENCE,
    REVIEWED_NOT_USED,
    BLOCKED_INSUFFICIENT_EVIDENCE,
    NO_COMPARABLE_SOURCE_FOUND
}

enum class TissueResearchExclusionReason {
    WRONG_EXERCISE,
    WRONG_TISSUE,
    WRONG_LOAD_DIMENSION,
    PATIENT_ONLY_WITHOUT_TRANSFER_JUSTIFICATION,
    EMG_ONLY,
    NO_MECHANICAL_OUTCOME,
    UNVALIDATED_MODEL,
    DUPLICATE_DATASET,
    ABSTRACT_INSUFFICIENT,
    FULL_TEXT_UNAVAILABLE,
    RETRACTED,
    BIBLIOGRAPHIC_CONFLICT
}

enum class TissueResearchNonUseReason {
    TESTED_PROTOCOL_MISMATCH,
    LOAD_CONDITION_INCOMPATIBLE,
    ROM_CONDITION_INCOMPATIBLE,
    VELOCITY_CONDITION_INCOMPATIBLE,
    BILATERAL_UNILATERAL_MISMATCH,
    SURFACE_CONDITION_INCOMPATIBLE,
    ANTICIPATED_CONDITION_INCOMPATIBLE,
    POPULATION_NOT_TRANSFERABLE,
    EMG_ONLY,
    NO_TISSUE_SPECIFIC_MECHANICAL_OUTCOME,
    NO_VALIDATED_INTERNAL_LOAD_MODEL,
    FULL_TEXT_INSUFFICIENT,
    CONFLICTING_EVIDENCE,
    NO_COMPARABLE_SOURCE
}

data class TissueDimensionReference(
    val tissueId: String,
    val loadDimension: TissueLoadDimension
) {
    val encoded: String get() = "$tissueId:${loadDimension.name}"
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
    val sourceStatus: String,
    val authors: String = "",
    val publicationYear: String = "",
    val journal: String = "",
    val studyType: String = "",
    val population: String = "",
    val sampleSize: String = "",
    val trainingStatus: String = "",
    val sexComposition: String = "",
    val healthStatus: String = "",
    val exactExercise: String = "",
    val exerciseProtocol: String = "",
    val externalLoadCondition: String = "",
    val repetitionCondition: String = "",
    val romCondition: String = "",
    val velocityCondition: String = "",
    val surfaceCondition: String = "",
    val footwearCondition: String = "",
    val anticipatedCondition: String = "",
    val fatigueCondition: String = "",
    val measurementMethod: String = "",
    val measuredOutcome: String = "",
    val reportedMetric: String = "",
    val reportedValue: String = "",
    val reportedLowerBound: String = "",
    val reportedUpperBound: String = "",
    val reportedUnit: String = "",
    val supportedTissueIds: List<String> = emptyList(),
    val supportedLoadDimensions: List<TissueLoadDimension> = emptyList(),
    val majorLimitations: String = "",
    val verifiedAt: String = "",
    val verificationMethod: String = "",
    val sourceNotes: String = ""
)

data class TissueSourceVerification(
    val sourceId: String,
    val resolvedPmid: String,
    val resolvedDoi: String,
    val resolvedTitle: String,
    val resolvedFirstAuthor: String,
    val resolvedYear: String,
    val resolvedJournal: String,
    val identifierVerificationStatus: TissueIdentifierVerificationStatus,
    val bibliographicMatchStatus: TissueBibliographicMatchStatus,
    val publicationIntegrityStatus: TissuePublicationIntegrityStatus,
    val networkCapabilityStatus: TissueNetworkCapabilityStatus,
    val verifiedAt: String,
    val verificationMethod: String,
    val metadataSnapshotHash: String,
    val verificationNotes: String
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
    val preparedByType: TissueActorType,
    val claimType: String = "",
    val claimParaphrase: String = "",
    val claimDirection: String = "",
    val claimLowerBound: Double? = null,
    val claimUpperBound: Double? = null,
    val comparatorExercise: String = "",
    val population: String = "",
    val exerciseCondition: String = "",
    val loadCondition: String = "",
    val romCondition: String = "",
    val velocityCondition: String = "",
    val surfaceCondition: String = "",
    val anticipatedCondition: String = "",
    val fatigueCondition: String = "",
    val evidenceLocatorType: String = "",
    val evidenceAccessLevel: String = "",
    val preparedAt: String = "",
    val draftNotes: String = ""
)

data class TissueRubricResearchDecision(
    val researchDecisionId: String,
    val reviewBatchId: String,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val targetStableKeys: List<String>,
    val database: String,
    val searchQuery: String,
    val searchDate: String,
    val candidateSourceIds: List<String>,
    val includedSourceIds: List<String>,
    val excludedSourceIds: List<String>,
    val exclusionReasons: List<TissueResearchExclusionReason>,
    val populationScope: String,
    val exerciseConditionScope: String,
    val measurementScope: String,
    val evidenceSufficiency: String,
    val researchDecision: TissueResearchDecision,
    val decisionReason: String,
    val preparedBy: String,
    val preparedByType: TissueActorType,
    val preparedAt: String,
    val researchNotes: String
) {
    val target: TissueDimensionReference get() = TissueDimensionReference(tissueId, loadDimension)
}

data class TissueTargetExerciseReview(
    val targetExerciseReviewId: String,
    val reviewBatchId: String,
    val stableKey: String,
    val canonicalDisplayName: String,
    val researchUseStatus: TissueResearchUseStatus,
    val supportedTissueDimensions: List<TissueDimensionReference>,
    val researchDecisionIds: List<String>,
    val sourceIds: List<String>,
    val draftClaimIds: List<String>,
    val draftRubricIds: List<String>,
    val directProtocolMatch: Boolean,
    val transferDistance: String,
    val nonUseReasons: List<TissueResearchNonUseReason>,
    val preparedBy: String,
    val preparedByType: TissueActorType,
    val preparedAt: String,
    val reviewNotes: String
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
