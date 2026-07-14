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

    fun sourceRegistry(
        sources: List<TissueEvidenceSource>,
        verifications: List<TissueSourceVerification>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val sourceById = sources.associateBy(TissueEvidenceSource::sourceId)
        if (sourceById.size != sources.size) errors += "Duplicate sourceId."
        val duplicateIdentity = sources.groupBy { source ->
            listOf(source.pmid.normalized(), source.doi.normalized(), source.title.normalized()).joinToString("|")
        }.values.firstOrNull { it.size > 1 }
        if (duplicateIdentity != null) errors += "Duplicate bibliographic source under multiple source IDs."
        if (TissuePhaseB1Contract.preservedPreflightSourceId !in sourceById) {
            errors += "PREFLIGHT_32658037 is missing."
        }
        if ("SRC_PMID_32658037" in sourceById) {
            errors += "PREFLIGHT_32658037 was duplicated or renamed as SRC_PMID_32658037."
        }
        if (verifications.map(TissueSourceVerification::sourceId).distinct().size != verifications.size) {
            errors += "Duplicate source verification row."
        }
        verifications.forEach { verification ->
            val source = sourceById[verification.sourceId]
            if (source == null) {
                errors += "${verification.sourceId}: verification references unknown source."
            } else {
                if (verification.identifierVerificationStatus != source.identifierVerificationStatus) {
                    errors += "${verification.sourceId}: identifier status differs from registry."
                }
                if (verification.bibliographicMatchStatus != source.bibliographicMatchStatus) {
                    errors += "${verification.sourceId}: bibliographic status differs from registry."
                }
                if (verification.resolvedPmid.isNotBlank() && verification.resolvedPmid != source.pmid) {
                    errors += "${verification.sourceId}: resolved PMID differs from registry."
                }
                if (verification.resolvedDoi.isNotBlank() && verification.resolvedDoi.normalized() != source.doi.normalized()) {
                    errors += "${verification.sourceId}: resolved DOI differs from registry."
                }
            }
            if (verification.metadataSnapshotHash.isBlank()) {
                errors += "${verification.sourceId}: verification metadata hash is missing."
            }
        }
        return TissueValidationReport(errors)
    }

    fun phaseB1Research(
        sources: List<TissueEvidenceSource>,
        drafts: List<TissueDraftClaim>,
        decisions: List<TissueRubricResearchDecision>,
        exerciseReviews: List<TissueTargetExerciseReview>,
        canonicalNamesByStableKey: Map<String, String>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val sourceIds = sources.map(TissueEvidenceSource::sourceId).toSet()
        val draftById = drafts.associateBy(TissueDraftClaim::draftClaimId)
        val decisionById = decisions.associateBy(TissueRubricResearchDecision::researchDecisionId)
        val catalogById = catalog.associateBy(TissueCatalogEntry::tissueId)
        if (decisionById.size != decisions.size) errors += "Duplicate researchDecisionId."
        if (exerciseReviews.map(TissueTargetExerciseReview::targetExerciseReviewId).distinct().size != exerciseReviews.size) {
            errors += "Duplicate targetExerciseReviewId."
        }
        if (exerciseReviews.map(TissueTargetExerciseReview::stableKey).distinct().size != exerciseReviews.size) {
            errors += "Duplicate target-exercise review identity."
        }
        val actualTargets = decisions.map(TissueRubricResearchDecision::target)
        if (actualTargets.toSet() != TissuePhaseB1Contract.targetTissueDimensions || actualTargets.distinct().size != actualTargets.size) {
            errors += "Phase B1 tissue/dimension decisions are incomplete or duplicated."
        }
        decisions.forEach { decision ->
            if (decision.reviewBatchId != TissuePhaseB1Contract.reviewBatchId) {
                errors += "${decision.researchDecisionId}: wrong review batch."
            }
            if (decision.searchQuery.isBlank() || decision.searchDate.isBlank()) {
                errors += "${decision.researchDecisionId}: search query/date is missing."
            }
            val referencedSources = decision.candidateSourceIds + decision.includedSourceIds + decision.excludedSourceIds
            referencedSources.filterNot(sourceIds::contains).forEach {
                errors += "${decision.researchDecisionId}: unknown sourceId $it."
            }
            if (decision.excludedSourceIds.isNotEmpty() && decision.exclusionReasons.isEmpty()) {
                errors += "${decision.researchDecisionId}: excluded source lacks an approved reason."
            }
            decision.targetStableKeys.filterNot(canonicalNamesByStableKey::containsKey).forEach {
                errors += "${decision.researchDecisionId}: unknown target stableKey $it."
            }
            if (decision.loadDimension !in catalogById[decision.tissueId]?.supportedLoadDimensions.orEmpty() &&
                decision.researchDecision != TissueResearchDecision.OUT_OF_SCOPE_AFTER_AUDIT
            ) {
                errors += "${decision.researchDecisionId}: unsupported tissue dimension."
            }
            if (decision.researchDecision == TissueResearchDecision.DRAFT_RUBRIC_CREATED) {
                if (decision.includedSourceIds.isEmpty()) errors += "${decision.researchDecisionId}: draft rubric decision lacks a source."
                if (drafts.none { it.tissueId == decision.tissueId && it.loadDimension == decision.loadDimension }) {
                    errors += "${decision.researchDecisionId}: draft rubric decision lacks a draft claim."
                }
            } else if (decision.decisionReason.isBlank()) {
                errors += "${decision.researchDecisionId}: non-rubric decision lacks a reason."
            }
        }

        val reviewedKeys = exerciseReviews.map(TissueTargetExerciseReview::stableKey)
        if (reviewedKeys.toSet() != TissuePhaseB1Contract.targetStableKeys || reviewedKeys.distinct().size != reviewedKeys.size) {
            errors += "Phase B1 target-exercise reviews are incomplete or duplicated."
        }
        exerciseReviews.forEach { review ->
            if (canonicalNamesByStableKey[review.stableKey] != review.canonicalDisplayName) {
                errors += "${review.targetExerciseReviewId}: canonical display name mismatch."
            }
            review.researchDecisionIds.filterNot(decisionById::containsKey).forEach {
                errors += "${review.targetExerciseReviewId}: unknown researchDecisionId $it."
            }
            review.sourceIds.filterNot(sourceIds::contains).forEach {
                errors += "${review.targetExerciseReviewId}: unknown sourceId $it."
            }
            review.draftClaimIds.filterNot(draftById::containsKey).forEach {
                errors += "${review.targetExerciseReviewId}: unknown draftClaimId $it."
            }
            val supportedTargets = review.researchDecisionIds.mapNotNull(decisionById::get).map { it.target }.toSet()
            if (review.supportedTissueDimensions.any { it !in supportedTargets }) {
                errors += "${review.targetExerciseReviewId}: unsupported tissue/dimension link."
            }
            if (review.researchUseStatus != TissueResearchUseStatus.USED_AS_DIRECT_ANCHOR && review.nonUseReasons.isEmpty()) {
                errors += "${review.targetExerciseReviewId}: non-direct use lacks a reason."
            }
            if (review.researchUseStatus == TissueResearchUseStatus.USED_AS_DIRECT_ANCHOR &&
                (review.sourceIds.isEmpty() || review.draftClaimIds.isEmpty())
            ) {
                errors += "${review.targetExerciseReviewId}: direct anchor lacks a source or draft claim."
            }
            if (review.researchUseStatus == TissueResearchUseStatus.USED_AS_TRANSFER_REFERENCE && review.transferDistance.isBlank()) {
                errors += "${review.targetExerciseReviewId}: transfer reference lacks transfer distance."
            }
            if (review.preparedByType != TissueActorType.AI_AGENT) {
                errors += "${review.targetExerciseReviewId}: Phase B1 review preparer must be AI_AGENT."
            }
        }
        return TissueValidationReport(errors)
    }

    fun phaseB1Rubrics(
        sources: List<TissueEvidenceSource>,
        drafts: List<TissueDraftClaim>,
        decisions: List<TissueRubricResearchDecision>,
        exerciseReviews: List<TissueTargetExerciseReview>,
        rubrics: List<TissueLoadRubric>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val sourceById = sources.associateBy(TissueEvidenceSource::sourceId)
        val draftById = drafts.associateBy(TissueDraftClaim::draftClaimId)
        val decisionById = decisions.associateBy(TissueRubricResearchDecision::researchDecisionId)
        val rubricById = rubrics.associateBy(TissueLoadRubric::rubricId)
        if (rubricById.size != rubrics.size) errors += "Duplicate rubricId."
        if (rubrics.map { listOf(it.tissueId, it.loadDimension.name, it.loadBand.name) }.distinct().size != rubrics.size) {
            errors += "Duplicate rubric identity."
        }
        rubrics.forEach { rubric ->
            val decision = decisionById[rubric.researchDecisionId]
            if (decision == null) errors += "${rubric.rubricId}: unknown researchDecisionId."
            if (decision != null && (decision.tissueId != rubric.tissueId || decision.loadDimension != rubric.loadDimension)) {
                errors += "${rubric.rubricId}: rubric tissue/dimension mismatch."
            }
            if (decision?.researchDecision != TissueResearchDecision.DRAFT_RUBRIC_CREATED) {
                errors += "${rubric.rubricId}: rubric is linked to a non-rubric research decision."
            }
            if (rubric.loadBand == TissueLoadBand.NONE) errors += "${rubric.rubricId}: NONE cannot represent missing evidence."
            if (rubric.anchorConditions.isBlank()) errors += "${rubric.rubricId}: anchor conditions are missing."
            if (rubric.sourceRefs.isEmpty()) errors += "${rubric.rubricId}: draft band lacks an included source."
            if (rubric.draftClaimIds.isEmpty()) errors += "${rubric.rubricId}: draft band lacks a draft claim."
            if (rubric.anchorClaimIds.isNotEmpty() || rubric.evidenceClaimIds.isNotEmpty()) {
                errors += "${rubric.rubricId}: draft IDs must not be placed in final-claim columns."
            }
            rubric.sourceRefs.forEach { sourceId ->
                val source = sourceById[sourceId]
                if (source == null) errors += "${rubric.rubricId}: unknown sourceId $sourceId."
                if (source != null && rubric.tissueId !in source.supportedTissueIds) {
                    errors += "${rubric.rubricId}: rubric/source tissue mismatch."
                }
                if (decision != null && sourceId !in decision.includedSourceIds) {
                    errors += "${rubric.rubricId}: source is not included by the research decision."
                }
            }
            rubric.draftClaimIds.forEach { draftId ->
                val draft = draftById[draftId]
                if (draft == null) errors += "${rubric.rubricId}: unknown draftClaimId $draftId."
                if (draft != null && (draft.tissueId != rubric.tissueId || draft.loadDimension != rubric.loadDimension)) {
                    errors += "${rubric.rubricId}: draft claim tissue/dimension mismatch."
                }
            }
            if (rubric.metricLowerBound != null && rubric.metricUpperBound != null &&
                rubric.metricLowerBound > rubric.metricUpperBound
            ) {
                errors += "${rubric.rubricId}: invalid metric bounds."
            }
            if ((rubric.metricLowerBound != null || rubric.metricUpperBound != null) && rubric.metricUnit.isBlank()) {
                errors += "${rubric.rubricId}: quantitative threshold lacks a unit."
            }
            if (rubric.rubricStatus !in setOf(
                    TissueRubricStatus.DRAFT_RESEARCHED_PENDING_BLIND_REVIEW,
                    TissueRubricStatus.BLOCKED_INSUFFICIENT_EVIDENCE,
                    TissueRubricStatus.CONFLICTING_EVIDENCE
                )
            ) {
                errors += "${rubric.rubricId}: status is not allowed in Phase B1."
            }
            if (rubric.preparedByType != TissueActorType.AI_AGENT) errors += "${rubric.rubricId}: preparer must be AI_AGENT."
            if (rubric.blindReviewedBy.isNotBlank() || rubric.humanApprovedBy.isNotBlank() || rubric.humanApprovedAt.isNotBlank()) {
                errors += "${rubric.rubricId}: review or human approval is populated in Phase B1."
            }
        }
        decisions.filter { it.researchDecision == TissueResearchDecision.DRAFT_RUBRIC_CREATED }.forEach { decision ->
            if (rubrics.none { it.researchDecisionId == decision.researchDecisionId }) {
                errors += "${decision.researchDecisionId}: draft rubric decision lacks a rubric row."
            }
        }
        exerciseReviews.forEach { review ->
            review.draftRubricIds.filterNot(rubricById::containsKey).forEach {
                errors += "${review.targetExerciseReviewId}: unknown draftRubricId $it."
            }
            if (review.researchUseStatus == TissueResearchUseStatus.USED_AS_DIRECT_ANCHOR) {
                if (review.sourceIds.isEmpty() || review.draftClaimIds.isEmpty() || review.draftRubricIds.isEmpty()) {
                    errors += "${review.targetExerciseReviewId}: direct anchor lacks source, draft claim, or draft rubric."
                }
                val linkedTargets = review.draftRubricIds.mapNotNull(rubricById::get)
                    .map { TissueDimensionReference(it.tissueId, it.loadDimension) }.toSet()
                if (linkedTargets.none(review.supportedTissueDimensions::contains)) {
                    errors += "${review.targetExerciseReviewId}: direct anchor rubric does not match a supported tissue/dimension."
                }
            } else if (review.draftRubricIds.isNotEmpty()) {
                errors += "${review.targetExerciseReviewId}: non-direct review references a successful draft rubric."
            }
        }
        return TissueValidationReport(errors)
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

    private fun String.normalized(): String = lowercase().filter(Char::isLetterOrDigit)
}
