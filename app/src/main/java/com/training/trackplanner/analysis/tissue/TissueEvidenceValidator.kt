package com.training.trackplanner.analysis.tissue

object TissueEvidenceValidator {
    private val productionClaimStatuses = setOf(
        TissueClaimVerificationStatus.ABSTRACT_SUPPORTED,
        TissueClaimVerificationStatus.FULL_TEXT_SUPPORTED,
        TissueClaimVerificationStatus.TABLE_OR_FIGURE_SUPPORTED
    )
    private val productionIdentifierStatuses = setOf(
        TissueIdentifierVerificationStatus.PMID_VERIFIED,
        TissueIdentifierVerificationStatus.DOI_VERIFIED,
        TissueIdentifierVerificationStatus.PMID_AND_DOI_VERIFIED
    )

    fun validate(
        sources: List<TissueEvidenceSource>,
        drafts: List<TissueDraftClaim>,
        blindReviews: List<TissueBlindReview>,
        finalClaims: List<TissueFinalClaim>,
        canonicalStableKeys: Set<String>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val sourceById = sources.associateBy(TissueEvidenceSource::sourceId)
        val draftById = drafts.associateBy(TissueDraftClaim::draftClaimId)
        val blindById = blindReviews.associateBy(TissueBlindReview::blindReviewId)
        if (sourceById.size != sources.size) errors += "Duplicate sourceId."
        if (draftById.size != drafts.size) errors += "Duplicate draftClaimId."
        if (blindById.size != blindReviews.size) errors += "Duplicate blindReviewId."
        if (finalClaims.map(TissueFinalClaim::claimId).distinct().size != finalClaims.size) errors += "Duplicate claimId."
        val tissueById = catalog.associateBy(TissueCatalogEntry::tissueId)

        drafts.forEach { draft ->
            if (draft.sourceId !in sourceById) errors += "${draft.draftClaimId}: unknown sourceId."
            if (draft.stableKey !in canonicalStableKeys) errors += "${draft.draftClaimId}: unknown stableKey."
            if (draft.loadDimension !in tissueById[draft.tissueId]?.supportedLoadDimensions.orEmpty()) {
                errors += "${draft.draftClaimId}: unsupported tissue dimension."
            }
            if (draft.claimValue != null && (draft.claimUnit.isBlank() || draft.evidenceLocator.isBlank())) {
                errors += "${draft.draftClaimId}: numeric claim lacks unit or locator."
            }
        }
        blindReviews.forEach { blind ->
            val draft = draftById[blind.draftClaimId]
            if (draft == null) errors += "${blind.blindReviewId}: unknown draftClaimId."
            if (blind.sourceId !in sourceById) errors += "${blind.blindReviewId}: unknown sourceId."
            if (draft != null && blind.blindReviewedBy == draft.preparedBy) {
                errors += "${blind.blindReviewId}: blind reviewer matches preparer."
            }
        }
        finalClaims.forEach { claim ->
            val draft = draftById[claim.draftClaimId]
            val blind = blindById[claim.blindReviewId]
            val source = sourceById[claim.sourceId]
            if (draft == null) errors += "${claim.claimId}: unknown draftClaimId."
            if (blind == null) errors += "${claim.claimId}: missing blind review."
            if (source == null) errors += "${claim.claimId}: unknown sourceId."
            if (claim.preparedBy == claim.blindReviewedBy) errors += "${claim.claimId}: blind reviewer matches preparer."
            if (claim.finalClaimValue != null && (claim.finalClaimUnit.isBlank() || claim.evidenceLocator.isBlank())) {
                errors += "${claim.claimId}: numeric final claim lacks unit or locator."
            }
            if (claim.productionEligibility && !claim.productionGatePasses(source)) {
                errors += "${claim.claimId}: production source/claim gate failed."
            }
            if (claim.productionEligibility && claim.humanApprovedBy.isBlank()) {
                errors += "${claim.claimId}: production claim lacks human approval."
            }
            if (claim.humanApprovedBy.isNotBlank() && claim.preparedByType == TissueActorType.AI_AGENT &&
                claim.humanApprovedBy == claim.preparedBy
            ) {
                errors += "${claim.claimId}: AI preparer cannot self-approve as human."
            }
        }
        return TissueValidationReport(errors)
    }

    fun batchApprovals(
        approvals: List<TissueReviewBatchApproval>,
        auditManifests: List<TissueMetadataAuditManifest>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val audits = auditManifests.associateBy(TissueMetadataAuditManifest::auditManifestId)
        approvals.forEach { approval ->
            val audit = audits[approval.auditManifestId]
            if (audit == null) errors += "${approval.reviewBatchId}: audit manifest does not exist."
            if (approval.humanApprover.isBlank() || approval.humanApprovedAt.isBlank()) {
                errors += "${approval.reviewBatchId}: human approval is missing."
            }
            if (!approval.automatedValidationPassed) errors += "${approval.reviewBatchId}: automated validation failed."
            if (audit?.auditDecision == TissueAuditDecision.BLOCKED) errors += "${approval.reviewBatchId}: audit is blocked."
        }
        return TissueValidationReport(errors)
    }

    fun sourceRefs(sourceRefs: Collection<String>, sources: List<TissueEvidenceSource>): TissueValidationReport {
        val known = sources.map(TissueEvidenceSource::sourceId).toSet()
        val missing = sourceRefs.filter(String::isNotBlank).filterNot(known::contains).distinct()
        return TissueValidationReport(errors = missing.map { "Unknown sourceRef: $it" })
    }

    fun productionProfiles(
        profiles: List<TissueLoadProfile>,
        finalClaims: List<TissueFinalClaim>,
        sources: List<TissueEvidenceSource>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val claims = finalClaims.associateBy(TissueFinalClaim::claimId)
        val sourceById = sources.associateBy(TissueEvidenceSource::sourceId)
        profiles.filter(TissueLoadProfile::productionEligibility).forEach { profile ->
            val linkedClaims = profile.evidenceClaimIds.mapNotNull(claims::get)
            if (profile.evidenceClaimIds.size != linkedClaims.size) errors += "${profile.profileRowId}: unknown evidence claim."
            if (profile.evidenceStatus == TissueEvidenceStatus.STUDY_BACKED) {
                if (linkedClaims.isEmpty() || linkedClaims.any { !it.productionEligibility }) {
                    errors += "${profile.profileRowId}: STUDY_BACKED profile lacks approved final claims."
                }
                val sourceReport = sourceRefs(profile.sourceRefs, sources)
                errors += sourceReport.errors
                if (profile.sourceRefs.mapNotNull(sourceById::get).any { source ->
                        source.identifierVerificationStatus !in productionIdentifierStatuses ||
                            source.bibliographicMatchStatus != TissueBibliographicMatchStatus.MATCHED ||
                            source.publicationIntegrityStatus in setOf(
                                TissuePublicationIntegrityStatus.RETRACTED,
                                TissuePublicationIntegrityStatus.EXPRESSION_OF_CONCERN
                            )
                    }
                ) {
                    errors += "${profile.profileRowId}: STUDY_BACKED source gate failed."
                }
            }
        }
        return TissueValidationReport(errors)
    }

    private fun TissueFinalClaim.productionGatePasses(source: TissueEvidenceSource?): Boolean =
        source != null &&
            identifierVerificationStatus in productionIdentifierStatuses &&
            source.identifierVerificationStatus in productionIdentifierStatuses &&
            bibliographicMatchStatus == TissueBibliographicMatchStatus.MATCHED &&
            source.bibliographicMatchStatus == TissueBibliographicMatchStatus.MATCHED &&
            claimVerificationStatus in productionClaimStatuses &&
            publicationIntegrityStatus !in setOf(
                TissuePublicationIntegrityStatus.RETRACTED,
                TissuePublicationIntegrityStatus.EXPRESSION_OF_CONCERN
            ) &&
            source.publicationIntegrityStatus !in setOf(
                TissuePublicationIntegrityStatus.RETRACTED,
                TissuePublicationIntegrityStatus.EXPRESSION_OF_CONCERN
            )
}
