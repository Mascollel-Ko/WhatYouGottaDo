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
        catalog: List<TissueCatalogEntry>,
        reaudits: List<TissueEvidenceReaudit> = emptyList(),
        candidates: List<TissueEvidenceClaimCandidate> = emptyList(),
        approvals: List<TissueReviewBatchApproval> = emptyList(),
        rubrics: List<TissueLoadRubric> = emptyList(),
        auditManifests: List<TissueMetadataAuditManifest> = emptyList()
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
        val reauditById = reaudits.associateBy(TissueEvidenceReaudit::reauditId)
        val candidateById = candidates.associateBy(TissueEvidenceClaimCandidate::claimCandidateId)
        val approvalById = approvals.associateBy(TissueReviewBatchApproval::batchApprovalId)
        val auditById = auditManifests.associateBy(TissueMetadataAuditManifest::auditManifestId)

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
            val source = sourceById[claim.sourceId]
            val approval = approvalById[claim.batchApprovalId]
            errors += validateCommonFinalClaim(
                claim,
                draft,
                source,
                canonicalStableKeys,
                tissueById,
                approval,
                auditById[claim.approvalAuditManifestId]
            )
            when (claim.reviewPath) {
                TissueFinalClaimReviewPath.INDEPENDENT_BLIND_REVIEW ->
                    errors += validateIndependentBlindPath(claim, blindById[claim.blindReviewId])
                TissueFinalClaimReviewPath.SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL ->
                    errors += validateSameSessionApprovalPath(
                        claim = claim,
                        reaudit = reauditById[claim.reauditId],
                        candidate = candidateById[claim.claimCandidateId],
                        approval = approval,
                        linkedRubrics = rubrics.filter { claim.claimCandidateId in it.claimCandidateIds }
                    )
                null -> errors += "${claim.claimId}: explicit reviewPath is required."
            }
            errors += validateProductionGate(claim, source, approval)
        }
        return TissueValidationReport(errors)
    }

    fun batchApprovals(
        approvals: List<TissueReviewBatchApproval>,
        auditManifests: List<TissueMetadataAuditManifest>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val audits = auditManifests.associateBy(TissueMetadataAuditManifest::auditManifestId)
        if (approvals.map(TissueReviewBatchApproval::batchApprovalId).distinct().size != approvals.size) {
            errors += "Duplicate batchApprovalId."
        }
        approvals.forEach { approval ->
            val audit = audits[approval.auditManifestId]
            if (audit == null) errors += "${approval.batchApprovalId}: audit manifest does not exist."
            if (approval.humanApproverLabel.isBlank() || approval.humanApprovedAt.isBlank() ||
                approval.humanApproverType != TissueActorType.HUMAN_USER
            ) {
                errors += "${approval.batchApprovalId}: human approval is missing or not HUMAN_USER."
            }
            if (!approval.automatedValidationPassed) errors += "${approval.batchApprovalId}: automated validation failed."
            if (approval.approvalScopeHash.isBlank() || approval.approvalRequestId.isBlank() ||
                approval.auditInputSnapshotHash.isBlank() || approval.sourceVerificationSnapshotHash.isBlank() ||
                approval.publicationIntegritySnapshotHash.isBlank()
            ) {
                errors += "${approval.batchApprovalId}: approval snapshots are incomplete."
            }
            if (audit != null && approval.auditInputSnapshotHash != audit.inputSnapshotHash) {
                errors += "${approval.batchApprovalId}: approval audit snapshot is stale."
            }
            if (approval.approvalDecision == TissueBatchApprovalDecision.APPROVED &&
                (approval.excludedClaimCandidateIds.isNotEmpty() || approval.excludedRubricIds.isNotEmpty())
            ) {
                errors += "${approval.batchApprovalId}: full approval cannot contain exclusions."
            }
            if (approval.approvalDecision == TissueBatchApprovalDecision.PARTIALLY_APPROVED &&
                ((approval.excludedClaimCandidateIds.isEmpty() && approval.excludedRubricIds.isEmpty()) || approval.exclusionReasons.isBlank())
            ) {
                errors += "${approval.batchApprovalId}: partial approval requires explicit exclusions and reasons."
            }
            if (audit?.auditDecision == TissueAuditDecision.BLOCKED) errors += "${approval.batchApprovalId}: audit is blocked."
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

    fun phaseCReaudits(
        sources: List<TissueEvidenceSource>,
        drafts: List<TissueDraftClaim>,
        reaudits: List<TissueEvidenceReaudit>,
        canonicalStableKeys: Set<String>,
        catalog: List<TissueCatalogEntry>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val sourceById = sources.associateBy(TissueEvidenceSource::sourceId)
        val draftById = drafts.associateBy(TissueDraftClaim::draftClaimId)
        val tissueById = catalog.associateBy(TissueCatalogEntry::tissueId)
        if (reaudits.map(TissueEvidenceReaudit::reauditId).distinct().size != reaudits.size) {
            errors += "Duplicate reauditId."
        }
        val counts = reaudits.groupingBy(TissueEvidenceReaudit::draftClaimId).eachCount()
        drafts.forEach { draft ->
            if (counts[draft.draftClaimId] != 1) errors += "${draft.draftClaimId}: expected exactly one re-audit row."
        }
        reaudits.forEach { reaudit ->
            val draft = draftById[reaudit.draftClaimId]
            val source = sourceById[reaudit.sourceId]
            if (draft == null) errors += "${reaudit.reauditId}: unknown draftClaimId."
            if (source == null) errors += "${reaudit.reauditId}: unknown sourceId."
            if (reaudit.stableKey !in canonicalStableKeys) errors += "${reaudit.reauditId}: unknown stableKey."
            if (reaudit.loadDimension !in tissueById[reaudit.tissueId]?.supportedLoadDimensions.orEmpty()) {
                errors += "${reaudit.reauditId}: unsupported tissue dimension."
            }
            if (draft != null && listOf(draft.sourceId, draft.stableKey, draft.tissueId, draft.loadDimension.name) !=
                listOf(reaudit.sourceId, reaudit.stableKey, reaudit.tissueId, reaudit.loadDimension.name)
            ) {
                errors += "${reaudit.reauditId}: draft identity changed during re-audit."
            }
            if (reaudit.reviewBatchId != TissuePhaseCContract.reviewBatchId ||
                reaudit.reviewMode != TissueEvidenceReviewMode.SAME_SESSION_EVIDENCE_REAUDIT ||
                reaudit.independenceStatus != TissueReviewIndependenceStatus.NOT_INDEPENDENT
            ) {
                errors += "${reaudit.reauditId}: invalid Phase C review identity."
            }
            if (reaudit.reviewedBy != "Codex" || reaudit.reviewedByType != TissueActorType.AI_AGENT) {
                errors += "${reaudit.reauditId}: invalid re-audit reviewer identity."
            }
            if (reaudit.reauditId != TissuePhaseCContract.reauditId(reaudit.draftClaimId, reaudit.sourceId)) {
                errors += "${reaudit.reauditId}: non-deterministic re-audit ID."
            }
            if (reaudit.recommendedActions.isEmpty()) errors += "${reaudit.reauditId}: recommended action is missing."
            val numeric = reaudit.verifiedValue != null || reaudit.verifiedLowerBound != null || reaudit.verifiedUpperBound != null
            if (numeric && (!reaudit.evidenceLocatorVerified || reaudit.evidenceLocator.isBlank() ||
                    reaudit.verifiedUnit.isBlank() || reaudit.normalizationBasis.isBlank() || reaudit.verifiedCondition.isBlank())
            ) {
                errors += "${reaudit.reauditId}: numeric result lacks a verified locator, unit, normalization, or condition."
            }
            reaudit.userAdjudicationIds.filterNot(TissuePhaseCContract.adjudicationIds::contains).forEach {
                errors += "${reaudit.reauditId}: unknown user adjudication ID $it."
            }
        }
        return TissueValidationReport(errors)
    }

    fun phaseCClaimCandidates(
        reaudits: List<TissueEvidenceReaudit>,
        candidates: List<TissueEvidenceClaimCandidate>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val reauditById = reaudits.associateBy(TissueEvidenceReaudit::reauditId)
        if (candidates.map(TissueEvidenceClaimCandidate::claimCandidateId).distinct().size != candidates.size) {
            errors += "Duplicate claimCandidateId."
        }
        val blockedStatuses = setOf(
            TissueClaimSupportStatus.UNSUPPORTED,
            TissueClaimSupportStatus.CONTRADICTED,
            TissueClaimSupportStatus.UNABLE_TO_VERIFY
        )
        candidates.forEach { candidate ->
            val reaudit = reauditById[candidate.reauditId]
            if (reaudit == null) errors += "${candidate.claimCandidateId}: unknown re-audit row."
            if (candidate.claimSupportStatus in blockedStatuses) {
                errors += "${candidate.claimCandidateId}: blocked claim became a positive candidate."
            }
            if (reaudit != null && !reaudit.evidenceLocatorVerified) {
                errors += "${candidate.claimCandidateId}: candidate uses an unverified locator."
            }
            if (reaudit != null && listOf(
                    candidate.draftClaimId,
                    candidate.sourceId,
                    candidate.stableKey,
                    candidate.tissueId,
                    candidate.loadDimension.name,
                    candidate.candidateValue,
                    candidate.candidateLowerBound,
                    candidate.candidateUpperBound,
                    candidate.candidateUnit
                ) != listOf(
                    reaudit.draftClaimId,
                    reaudit.sourceId,
                    reaudit.stableKey,
                    reaudit.tissueId,
                    reaudit.loadDimension.name,
                    reaudit.verifiedValue,
                    reaudit.verifiedLowerBound,
                    reaudit.verifiedUpperBound,
                    reaudit.verifiedUnit
                )
            ) {
                errors += "${candidate.claimCandidateId}: candidate differs from its re-audited value or identity."
            }
            if (candidate.claimCandidateId != TissuePhaseCContract.claimCandidateId(candidate.draftClaimId, candidate.reauditId)) {
                errors += "${candidate.claimCandidateId}: non-deterministic claim-candidate ID."
            }
            val numeric = candidate.candidateValue != null || candidate.candidateLowerBound != null || candidate.candidateUpperBound != null
            if (numeric && (candidate.candidateUnit.isBlank() || candidate.normalizationBasis.isBlank() || candidate.measurementMethod.isBlank() ||
                    candidate.supportedCondition.isBlank() || candidate.evidenceLocator.isBlank())
            ) {
                errors += "${candidate.claimCandidateId}: numeric candidate lacks unit, normalization, method, condition, or locator."
            }
            if (candidate.productionEligibility) errors += "${candidate.claimCandidateId}: production eligibility must be false."
            if (candidate.humanApprovedBy.isNotBlank() || candidate.humanApprovedAt.isNotBlank()) {
                errors += "${candidate.claimCandidateId}: human approval must remain blank."
            }
            if (candidate.reviewMode != TissueEvidenceReviewMode.SAME_SESSION_EVIDENCE_REAUDIT ||
                candidate.independenceStatus != TissueReviewIndependenceStatus.NOT_INDEPENDENT
            ) {
                errors += "${candidate.claimCandidateId}: invalid Phase C review identity."
            }
            if (candidate.stableKey == "ex_314df428" && candidate.tissueId == "ACHILLES_TENDON" &&
                candidate.loadDimension == TissueLoadDimension.PEAK_TENSILE_LOAD &&
                (candidate.exerciseCorrespondence != TissueExerciseCorrespondence.CLOSE_VARIANT ||
                    candidate.maximumDefensibleBand != TissueLoadBand.VERY_HIGH ||
                    TissuePhaseCContract.achillesAdjudicationId !in candidate.userAdjudicationIds)
            ) {
                errors += "${candidate.claimCandidateId}: Achilles hop transfer lacks close-variant adjudication disclosure."
            }
            if (candidate.tissueId == "KNEE_PATELLOFEMORAL" && candidate.loadDimension == TissueLoadDimension.COMPRESSION &&
                (candidate.dimensionCorrespondence !in setOf(
                    TissueDimensionCorrespondence.DIMENSION_SUPPORTED_BY_EXPLICIT_PROXY,
                    TissueDimensionCorrespondence.DIMENSION_PARTIALLY_SUPPORTED
                ) || !candidate.candidateClaimType.contains("COMPOSITE", ignoreCase = true) ||
                    candidate.candidateClaimType.contains("PURE", ignoreCase = true))
            ) {
                errors += "${candidate.claimCandidateId}: PFJ composite metric is not disclosed as an explicit proxy."
            }
        }
        return TissueValidationReport(errors)
    }

    fun phaseCAdjudications(
        adjudications: List<TissueUserAdjudication>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val byId = adjudications.associateBy(TissueUserAdjudication::adjudicationId)
        if (byId.keys != TissuePhaseCContract.adjudicationIds || byId.size != adjudications.size) {
            errors += "Phase C requires exactly the two explicit user adjudications."
        }
        adjudications.forEach { adjudication ->
            if (adjudication.reviewBatchId != TissuePhaseCContract.reviewBatchId ||
                adjudication.decisionActorType != TissueActorType.HUMAN_USER ||
                adjudication.decisionSource != TissueUserAdjudicationDecisionSource.EXPLICIT_USER_INSTRUCTION ||
                adjudication.decisionRecordedBy != "Codex"
            ) {
                errors += "${adjudication.adjudicationId}: invalid adjudication provenance."
            }
            if (adjudication.isBatchApproval) errors += "${adjudication.adjudicationId}: adjudication is not batch approval."
            if (adjudication.productionEligibilityEffect != TissueProductionEligibilityEffect.NONE) {
                errors += "${adjudication.adjudicationId}: adjudication cannot grant production eligibility."
            }
        }
        byId[TissuePhaseCContract.achillesAdjudicationId]?.let { adjudication ->
            if (adjudication.stableKeys != listOf("ex_314df428") ||
                adjudication.tissueIds != listOf("ACHILLES_TENDON") ||
                adjudication.loadDimensions != listOf(TissueLoadDimension.PEAK_TENSILE_LOAD) ||
                !adjudication.requiredDisclosure.contains("CLOSE_VARIANT") ||
                !adjudication.requiredDisclosure.contains("VERY_HIGH")
            ) {
                errors += "${adjudication.adjudicationId}: Achilles close-variant disclosure is incomplete."
            }
        }
        byId[TissuePhaseCContract.pfjAdjudicationId]?.let { adjudication ->
            if (adjudication.tissueIds != listOf("KNEE_PATELLOFEMORAL") ||
                adjudication.loadDimensions != listOf(TissueLoadDimension.COMPRESSION) ||
                !adjudication.requiredDisclosure.contains("composite", ignoreCase = true) ||
                !adjudication.requiredDisclosure.contains("DIMENSION_SUPPORTED_BY_EXPLICIT_PROXY")
            ) {
                errors += "${adjudication.adjudicationId}: PFJ composite-proxy disclosure is incomplete."
            }
        }
        return TissueValidationReport(errors)
    }

    fun phaseCRubrics(
        rubrics: List<TissueLoadRubric>,
        reaudits: List<TissueEvidenceReaudit>,
        candidates: List<TissueEvidenceClaimCandidate>,
        adjudications: List<TissueUserAdjudication>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        val reauditIds = reaudits.map(TissueEvidenceReaudit::reauditId).toSet()
        val candidateIds = candidates.map(TissueEvidenceClaimCandidate::claimCandidateId).toSet()
        val adjudicationIds = adjudications.map(TissueUserAdjudication::adjudicationId).toSet()
        val allowedStatuses = setOf(
            TissueRubricStatus.REAUDITED_PENDING_HUMAN_APPROVAL,
            TissueRubricStatus.REAUDITED_WITH_LIMITATIONS,
            TissueRubricStatus.BLOCKED_AFTER_REAUDIT
        )
        reaudits.filter { it.userAdjudicationIds.any { id -> id !in adjudicationIds } }
            .forEach { errors += "${it.reauditId}: unknown user adjudication link." }
        candidates.filter { it.userAdjudicationIds.any { id -> id !in adjudicationIds } }
            .forEach { errors += "${it.claimCandidateId}: unknown user adjudication link." }
        rubrics.forEach { rubric ->
            if (rubric.reauditAction == null) errors += "${rubric.rubricId}: re-audit action is missing."
            if (rubric.reauditIds.isEmpty() || rubric.reauditIds.any { it !in reauditIds }) {
                errors += "${rubric.rubricId}: re-audit links are missing or unknown."
            }
            if (rubric.rubricStatus != TissueRubricStatus.BLOCKED_AFTER_REAUDIT &&
                (rubric.claimCandidateIds.isEmpty() || rubric.claimCandidateIds.any { it !in candidateIds })
            ) {
                errors += "${rubric.rubricId}: claim-candidate links are missing or unknown."
            }
            if (rubric.userAdjudicationIds.any { it !in adjudicationIds }) {
                errors += "${rubric.rubricId}: unknown user adjudication link."
            }
            val linkedCandidates = candidates.filter { it.claimCandidateId in rubric.claimCandidateIds }
            if (linkedCandidates.any {
                    it.tissueId != rubric.tissueId || it.loadDimension != rubric.loadDimension ||
                        it.maximumDefensibleBand != rubric.loadBand
                }
            ) {
                errors += "${rubric.rubricId}: linked candidate does not support the rubric tissue, dimension, and band."
            }
            if (rubric.rubricId == "RUBRIC_ACH_PEAK_VERY_HIGH" &&
                rubric.userAdjudicationIds != listOf(TissuePhaseCContract.achillesAdjudicationId)
            ) {
                errors += "${rubric.rubricId}: Achilles transfer adjudication is required."
            }
            if (rubric.tissueId == "KNEE_PATELLOFEMORAL" &&
                rubric.userAdjudicationIds != listOf(TissuePhaseCContract.pfjAdjudicationId)
            ) {
                errors += "${rubric.rubricId}: PFJ composite-proxy adjudication is required."
            }
            if (rubric.rubricStatus !in allowedStatuses) errors += "${rubric.rubricId}: invalid Phase C rubric status."
            if (rubric.reviewMode != TissueEvidenceReviewMode.SAME_SESSION_EVIDENCE_REAUDIT ||
                rubric.independenceStatus != TissueReviewIndependenceStatus.NOT_INDEPENDENT
            ) {
                errors += "${rubric.rubricId}: invalid Phase C review identity."
            }
            if (rubric.blindReviewedBy.isNotBlank() || rubric.blindReviewedAt.isNotBlank() ||
                rubric.humanApprovedBy.isNotBlank() || rubric.humanApprovedAt.isNotBlank()
            ) {
                errors += "${rubric.rubricId}: blind or human approval must remain blank."
            }
        }
        return TissueValidationReport(errors)
    }

    fun phaseCNonProduction(
        blindReviews: List<TissueBlindReview>,
        finalClaims: List<TissueFinalClaim>,
        approvals: List<TissueReviewBatchApproval>,
        profiles: List<TissueLoadProfile>
    ): TissueValidationReport {
        val errors = mutableListOf<String>()
        if (blindReviews.isNotEmpty()) errors += "Phase C must not create blind-review rows."
        if (finalClaims.isNotEmpty()) errors += "Phase C must not create formal final claims."
        if (approvals.isNotEmpty()) errors += "Phase C must not create human batch approvals."
        if (profiles.isNotEmpty()) errors += "Phase C must not create production profile rows."
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

    private fun validateCommonFinalClaim(
        claim: TissueFinalClaim,
        draft: TissueDraftClaim?,
        source: TissueEvidenceSource?,
        canonicalStableKeys: Set<String>,
        tissueById: Map<String, TissueCatalogEntry>,
        approval: TissueReviewBatchApproval?,
        audit: TissueMetadataAuditManifest?
    ): List<String> = buildList {
        if (draft == null) add("${claim.claimId}: unknown draftClaimId.")
        if (source == null) add("${claim.claimId}: unknown sourceId.")
        if (claim.stableKey !in canonicalStableKeys) add("${claim.claimId}: unknown stableKey.")
        if (claim.loadDimension !in tissueById[claim.tissueId]?.supportedLoadDimensions.orEmpty()) {
            add("${claim.claimId}: unsupported tissue dimension.")
        }
        if (draft != null && listOf(draft.sourceId, draft.stableKey, draft.tissueId, draft.loadDimension.name) !=
            listOf(claim.sourceId, claim.stableKey, claim.tissueId, claim.loadDimension.name)
        ) {
            add("${claim.claimId}: final claim identity differs from draft.")
        }
        val values = listOfNotNull(claim.finalClaimValue, claim.finalClaimLowerBound, claim.finalClaimUpperBound)
        if (values.any { !it.isFinite() || it < 0.0 }) add("${claim.claimId}: final claim has invalid numeric values.")
        if (claim.finalClaimLowerBound != null && claim.finalClaimUpperBound != null &&
            claim.finalClaimLowerBound > claim.finalClaimUpperBound
        ) {
            add("${claim.claimId}: final claim range is inverted.")
        }
        if (values.isNotEmpty() && (claim.finalClaimUnit.isBlank() || claim.evidenceLocator.isBlank() ||
                claim.normalizationBasis.isBlank() || claim.supportedCondition.isBlank())) {
            add("${claim.claimId}: numeric final claim lacks unit, locator, normalization, or condition.")
        }
        if (source != null && (source.identifierVerificationStatus !in productionIdentifierStatuses ||
                source.bibliographicMatchStatus != TissueBibliographicMatchStatus.MATCHED ||
                claim.identifierVerificationStatus != source.identifierVerificationStatus ||
                claim.bibliographicMatchStatus != source.bibliographicMatchStatus)
        ) {
            add("${claim.claimId}: source identity or bibliography is not verified and matched.")
        }
        if (claim.claimVerificationStatus !in productionClaimStatuses) {
            add("${claim.claimId}: claim verification status is not supported.")
        }
        if (claim.humanApprovedBy.isBlank() || claim.humanApprovedByType != TissueActorType.HUMAN_USER ||
            claim.humanApprovedAt.isBlank()) {
            add("${claim.claimId}: explicit HUMAN_USER approval is required.")
        }
        if (claim.preparedByType == TissueActorType.AI_AGENT && claim.humanApprovedBy == claim.preparedBy) {
            add("${claim.claimId}: AI preparer cannot self-approve as human.")
        }
        if (approval == null) {
            add("${claim.claimId}: matching batch approval is required.")
        } else {
            if (approval.approvalDecision !in setOf(
                    TissueBatchApprovalDecision.APPROVED,
                    TissueBatchApprovalDecision.PARTIALLY_APPROVED
                ) || !approval.automatedValidationPassed
            ) {
                add("${claim.claimId}: batch approval is rejected, revoked, or invalid.")
            }
            if (claim.reviewPath != approval.reviewPath || claim.reviewBatchId != approval.reviewBatchId ||
                claim.approvalScopeHash != approval.approvalScopeHash ||
                claim.approvalAuditManifestId != approval.auditManifestId ||
                claim.sourceVerificationSnapshotHash != approval.sourceVerificationSnapshotHash
            ) {
                add("${claim.claimId}: approval identity or snapshot hash does not match.")
            }
            if (claim.humanApprovedBy != approval.humanApproverLabel ||
                claim.humanApprovedByType != approval.humanApproverType ||
                claim.humanApprovedAt != approval.humanApprovedAt
            ) {
                add("${claim.claimId}: final claim human approval differs from batch approval.")
            }
            if (audit == null || audit.inputSnapshotHash != approval.auditInputSnapshotHash) {
                add("${claim.claimId}: approval audit snapshot is stale or missing.")
            }
        }
    }

    private fun validateIndependentBlindPath(
        claim: TissueFinalClaim,
        blind: TissueBlindReview?
    ): List<String> = buildList {
        if (claim.blindReviewId.isBlank() || blind == null) add("${claim.claimId}: blind path requires a valid blind review.")
        if (claim.reauditId.isNotBlank() || claim.claimCandidateId.isNotBlank()) {
            add("${claim.claimId}: blind path cannot populate same-session candidate fields.")
        }
        if (blind != null && listOf(blind.draftClaimId, blind.sourceId, blind.stableKey, blind.tissueId, blind.loadDimension.name) !=
            listOf(claim.draftClaimId, claim.sourceId, claim.stableKey, claim.tissueId, claim.loadDimension.name)
        ) {
            add("${claim.claimId}: blind-review identity differs from final claim.")
        }
        if (blind != null && blind.claimVerificationStatus !in productionClaimStatuses) {
            add("${claim.claimId}: blind-review claim status is unsupported.")
        }
        if (claim.blindReviewedBy.isBlank() || claim.blindReviewedByType == null || claim.blindReviewedAt.isBlank()) {
            add("${claim.claimId}: blind-review identity is incomplete.")
        }
        if (claim.blindReviewedBy == claim.preparedBy || blind?.blindReviewedBy == claim.preparedBy) {
            add("${claim.claimId}: blind reviewer matches preparer.")
        }
    }

    private fun validateSameSessionApprovalPath(
        claim: TissueFinalClaim,
        reaudit: TissueEvidenceReaudit?,
        candidate: TissueEvidenceClaimCandidate?,
        approval: TissueReviewBatchApproval?,
        linkedRubrics: List<TissueLoadRubric>
    ): List<String> = buildList {
        if (claim.blindReviewId.isNotBlank() || claim.blindReviewedBy.isNotBlank() ||
            claim.blindReviewedByType != null || claim.blindReviewedAt.isNotBlank()) {
            add("${claim.claimId}: same-session path cannot contain fabricated blind-review fields.")
        }
        if (reaudit == null) add("${claim.claimId}: same-session path requires a valid re-audit.")
        if (candidate == null) add("${claim.claimId}: same-session path requires a valid claim candidate.")
        if (reaudit != null && (reaudit.reviewMode != TissueEvidenceReviewMode.SAME_SESSION_EVIDENCE_REAUDIT ||
                reaudit.independenceStatus != TissueReviewIndependenceStatus.NOT_INDEPENDENT)) {
            add("${claim.claimId}: re-audit identity is not the same-session non-independent path.")
        }
        if (candidate != null && (candidate.reviewMode != TissueEvidenceReviewMode.SAME_SESSION_EVIDENCE_REAUDIT ||
                candidate.independenceStatus != TissueReviewIndependenceStatus.NOT_INDEPENDENT)) {
            add("${claim.claimId}: candidate identity is not the same-session non-independent path.")
        }
        if (reaudit != null && candidate != null && (candidate.reauditId != reaudit.reauditId ||
                candidate.draftClaimId != reaudit.draftClaimId || candidate.sourceId != reaudit.sourceId)) {
            add("${claim.claimId}: candidate does not belong to the linked re-audit.")
        }
        if (candidate != null && !claim.matches(candidate)) {
            add("${claim.claimId}: final claim scientific payload differs from approved candidate.")
        }
        if (approval == null) return@buildList
        if (claim.claimCandidateId !in approval.approvedClaimCandidateIds) {
            add("${claim.claimId}: candidate is outside the approved scope.")
        }
        val linkedRubricIds = linkedRubrics.map(TissueLoadRubric::rubricId).toSet()
        if (!approval.approvedRubricIds.containsAll(linkedRubricIds)) {
            add("${claim.claimId}: linked rubrics are outside the approved scope.")
        }
    }

    private fun validateProductionGate(
        claim: TissueFinalClaim,
        source: TissueEvidenceSource?,
        approval: TissueReviewBatchApproval?
    ): List<String> = buildList {
        if (claim.productionEligibility && !claim.productionGatePasses(source)) {
            add("${claim.claimId}: production source/claim gate failed.")
        }
        if (claim.productionEligibility && approval?.approvalDecision !in
            setOf(TissueBatchApprovalDecision.APPROVED, TissueBatchApprovalDecision.PARTIALLY_APPROVED)) {
            add("${claim.claimId}: production eligibility lacks active approval.")
        }
    }

    private fun TissueFinalClaim.matches(candidate: TissueEvidenceClaimCandidate): Boolean =
        draftClaimId == candidate.draftClaimId && reauditId == candidate.reauditId && sourceId == candidate.sourceId &&
            stableKey == candidate.stableKey && tissueId == candidate.tissueId && loadDimension == candidate.loadDimension &&
            finalClaimType == candidate.candidateClaimType && finalClaimParaphrase == candidate.candidateClaimParaphrase &&
            finalClaimDirection == candidate.candidateClaimDirection && finalClaimValue == candidate.candidateValue &&
            finalClaimLowerBound == candidate.candidateLowerBound && finalClaimUpperBound == candidate.candidateUpperBound &&
            finalClaimUnit == candidate.candidateUnit && normalizationBasis == candidate.normalizationBasis &&
            supportedCondition == candidate.supportedCondition && measurementMethod == candidate.measurementMethod &&
            evidenceLocatorType == candidate.evidenceLocatorType && evidenceLocator == candidate.evidenceLocator &&
            evidenceAccessLevel == candidate.evidenceAccessLevel && exerciseCorrespondence == candidate.exerciseCorrespondence &&
            tissueCorrespondence == candidate.tissueCorrespondence && dimensionCorrespondence == candidate.dimensionCorrespondence &&
            crossStudyComparability == candidate.crossStudyComparability && maximumDefensibleBand == candidate.maximumDefensibleBand &&
            bandBasis == candidate.bandBasis && claimSupportStatus == candidate.claimSupportStatus &&
            confidenceLevel == candidate.confidenceLevel

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
