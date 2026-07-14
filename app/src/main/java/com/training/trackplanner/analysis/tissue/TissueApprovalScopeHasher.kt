package com.training.trackplanner.analysis.tissue

object TissueApprovalScopeHasher {
    fun hash(
        reviewPath: TissueFinalClaimReviewPath,
        candidates: List<TissueEvidenceClaimCandidate>,
        rubrics: List<TissueLoadRubric>,
        sources: List<TissueEvidenceSource>,
        sourceVerifications: List<TissueSourceVerification>,
        publicationIntegrity: List<TissuePublicationIntegrityVerification>,
        userAdjudicationIds: List<String>,
        auditManifestId: String,
        auditInputSnapshotHash: String
    ): String = TissueMetadataValidator.combinedHash(buildMap {
        put("reviewPath", reviewPath.name)
        candidates.forEach { put("candidate:${it.claimCandidateId}", it.scientificPayload()) }
        rubrics.forEach { put("rubric:${it.rubricId}", it.scientificPayload()) }
        sources.forEach { put("source:${it.sourceId}", it.identityPayload()) }
        sourceVerifications.forEach { put("sourceVerification:${it.sourceId}", it.verificationPayload()) }
        publicationIntegrity.forEach { put("publicationIntegrity:${it.sourceId}", it.integrityPayload()) }
        userAdjudicationIds.sorted().forEach { put("userAdjudication:$it", it) }
        put("auditManifestId", auditManifestId)
        put("auditInputSnapshotHash", auditInputSnapshotHash)
    })

    fun sourceVerificationSnapshotHash(rows: List<TissueSourceVerification>): String =
        TissueMetadataValidator.combinedHash(rows.associate { it.sourceId to it.verificationPayload() })

    fun publicationIntegritySnapshotHash(rows: List<TissuePublicationIntegrityVerification>): String =
        TissueMetadataValidator.combinedHash(rows.associate { it.sourceId to it.integrityPayload() })

    private fun TissueEvidenceClaimCandidate.scientificPayload(): String = listOf(
        claimCandidateId, reviewBatchId, draftClaimId, reauditId, sourceId, stableKey, tissueId, loadDimension.name,
        candidateClaimType, candidateClaimParaphrase, candidateClaimDirection, candidateValue, candidateLowerBound,
        candidateUpperBound, candidateUnit, normalizationBasis, supportedCondition, measurementMethod,
        evidenceLocatorType, evidenceLocator, evidenceAccessLevel.name, exerciseCorrespondence.name,
        tissueCorrespondence.name, dimensionCorrespondence.name, crossStudyComparability.name,
        maximumDefensibleBand?.name, bandBasis.name, claimSupportStatus.name, confidenceLevel.name,
        userAdjudicationIds.sorted().joinToString("|"), reviewMode.name, independenceStatus.name,
        technicalVerificationStatus.name
    ).encoded()

    private fun TissueLoadRubric.scientificPayload(): String = listOf(
        rubricId, tissueId, loadDimension.name, loadBand.name, metricType, metricLowerBound?.toPlainString(),
        lowerBoundInclusive, metricUpperBound?.toPlainString(), upperBoundInclusive, boundarySemanticsVersion, metricUnit,
        anchorStableKeys.sorted().joinToString("|"), anchorConditions, anchorClaimIds.sorted().joinToString("|"),
        researchDecisionId, draftClaimIds.sorted().joinToString("|"), assignmentMethod.name, evidenceSetId,
        evidenceClaimIds.sorted().joinToString("|"), sourceRefs.sorted().joinToString("|"), confidenceLevel.name,
        rubricStatus.name, reauditAction?.name, reauditIds.sorted().joinToString("|"),
        claimCandidateIds.sorted().joinToString("|"), userAdjudicationIds.sorted().joinToString("|"),
        reviewMode?.name, independenceStatus?.name
    ).encoded()

    private fun TissueEvidenceSource.identityPayload(): String = listOf(
        sourceId, pmid, doi.lowercase(), title, authors, publicationYear, journal, identifierVerificationStatus.name,
        bibliographicMatchStatus.name, publicationIntegrityStatus.name
    ).encoded()

    private fun TissueSourceVerification.verificationPayload(): String = listOf(
        sourceId, resolvedPmid, resolvedDoi.lowercase(), resolvedTitle, resolvedFirstAuthor, resolvedYear,
        resolvedJournal, identifierVerificationStatus.name, bibliographicMatchStatus.name,
        publicationIntegrityStatus.name, networkCapabilityStatus.name, verificationMethod, metadataSnapshotHash
    ).encoded()

    private fun TissuePublicationIntegrityVerification.integrityPayload(): String = listOf(
        sourceId, pmid, doi.lowercase(), pubmedPublicationTypes.sorted().joinToString("|"),
        pubmedCommentsCorrections.sorted().joinToString("|"), pubmedLinkedNotices.sorted().joinToString("|"),
        crossrefRelationTypes.sorted().joinToString("|"), crossrefUpdateTo.sorted().joinToString("|"),
        crossrefUpdatedBy.sorted().joinToString("|"), publisherNoticeStatus, publisherNoticeReference,
        integrityCheckStatus.name, verificationMethod, metadataSnapshotHash
    ).encoded()

    private fun List<Any?>.encoded(): String = joinToString("\u001F") { it?.toString().orEmpty() }
}

fun TissueReviewBatchApprovalRequest.requiredApprovalStatement(): String =
    "I approve approvalRequestId=$approvalRequestId,\n" +
        "approvalScopeHash=$approvalScopeHash,\n" +
        "reviewPath=${reviewPath.name},\n" +
        "covering exactly the listed $candidateCount claim candidates and $rubricCount rubric rows.\n" +
        "I understand this was a same-session, non-independent technical re-audit."
