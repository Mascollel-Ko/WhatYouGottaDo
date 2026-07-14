package com.training.trackplanner.analysis.tissue

import java.util.Locale

object TissueEvidenceParser {
    fun sources(csv: String): List<TissueEvidenceSource> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueEvidenceSource(
            sourceId = row.required("sourceId"),
            pmid = row.value("pmid"),
            doi = row.value("doi"),
            title = row.required("title"),
            identifierVerificationStatus = row.enum("identifierVerificationStatus"),
            bibliographicMatchStatus = row.enum("bibliographicMatchStatus"),
            publicationIntegrityStatus = row.enum("publicationIntegrityStatus"),
            verificationCapabilityStatus = row.enum("verificationCapabilityStatus"),
            sourceStatus = row.required("sourceStatus"),
            authors = row.value("authors"),
            publicationYear = row.value("publicationYear"),
            journal = row.value("journal"),
            studyType = row.value("studyType"),
            population = row.value("population"),
            sampleSize = row.value("sampleSize"),
            trainingStatus = row.value("trainingStatus"),
            sexComposition = row.value("sexComposition"),
            healthStatus = row.value("healthStatus"),
            exactExercise = row.value("exactExercise"),
            exerciseProtocol = row.value("exerciseProtocol"),
            externalLoadCondition = row.value("externalLoadCondition"),
            repetitionCondition = row.value("repetitionCondition"),
            romCondition = row.value("romCondition"),
            velocityCondition = row.value("velocityCondition"),
            surfaceCondition = row.value("surfaceCondition"),
            footwearCondition = row.value("footwearCondition"),
            anticipatedCondition = row.value("anticipatedCondition"),
            fatigueCondition = row.value("fatigueCondition"),
            measurementMethod = row.value("measurementMethod"),
            measuredOutcome = row.value("measuredOutcome"),
            reportedMetric = row.value("reportedMetric"),
            reportedValue = row.value("reportedValue"),
            reportedLowerBound = row.value("reportedLowerBound"),
            reportedUpperBound = row.value("reportedUpperBound"),
            reportedUnit = row.value("reportedUnit"),
            supportedTissueIds = row.tokens("supportedTissueIds"),
            supportedLoadDimensions = row.tokens("supportedLoadDimensions").map { enumValueOf<TissueLoadDimension>(it) },
            majorLimitations = row.value("majorLimitations"),
            verifiedAt = row.value("verifiedAt"),
            verificationMethod = row.value("verificationMethod"),
            sourceNotes = row.value("sourceNotes")
        )
    }

    fun sourceVerifications(csv: String): List<TissueSourceVerification> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueSourceVerification(
            sourceId = row.required("sourceId"),
            resolvedPmid = row.value("resolvedPmid"),
            resolvedDoi = row.value("resolvedDoi"),
            resolvedTitle = row.required("resolvedTitle"),
            resolvedFirstAuthor = row.value("resolvedFirstAuthor"),
            resolvedYear = row.value("resolvedYear"),
            resolvedJournal = row.value("resolvedJournal"),
            identifierVerificationStatus = row.enum("identifierVerificationStatus"),
            bibliographicMatchStatus = row.enum("bibliographicMatchStatus"),
            publicationIntegrityStatus = row.enum("publicationIntegrityStatus"),
            networkCapabilityStatus = row.enum("networkCapabilityStatus"),
            verifiedAt = row.required("verifiedAt"),
            verificationMethod = row.required("verificationMethod"),
            metadataSnapshotHash = row.required("metadataSnapshotHash"),
            verificationNotes = row.value("verificationNotes")
        )
    }

    fun publicationIntegrityVerifications(csv: String): List<TissuePublicationIntegrityVerification> =
        TissueMetadataParser.table(csv).rows.map { row ->
            TissuePublicationIntegrityVerification(
                sourceId = row.required("sourceId"),
                pmid = row.value("pmid"),
                doi = row.value("doi"),
                pubmedPublicationTypes = row.tokens("pubmedPublicationTypes"),
                pubmedCommentsCorrections = row.tokens("pubmedCommentsCorrections"),
                pubmedLinkedNotices = row.tokens("pubmedLinkedNotices"),
                crossrefRelationTypes = row.tokens("crossrefRelationTypes"),
                crossrefUpdateTo = row.tokens("crossrefUpdateTo"),
                crossrefUpdatedBy = row.tokens("crossrefUpdatedBy"),
                publisherNoticeStatus = row.required("publisherNoticeStatus"),
                publisherNoticeReference = row.value("publisherNoticeReference"),
                integrityCheckStatus = row.enum("integrityCheckStatus"),
                checkedAt = row.required("checkedAt"),
                verificationMethod = row.required("verificationMethod"),
                metadataSnapshotHash = row.required("metadataSnapshotHash"),
                integrityNotes = row.required("integrityNotes")
            )
        }

    fun draftClaims(csv: String): List<TissueDraftClaim> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueDraftClaim(
            draftClaimId = row.required("draftClaimId"),
            sourceId = row.required("sourceId"),
            stableKey = row.required("stableKey"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            proposedBand = row.value("proposedBand").takeIf(String::isNotBlank)?.let { enumValueOf<TissueLoadBand>(it) },
            claimValue = row.value("claimValue").toDoubleOrNull(),
            claimUnit = row.value("claimUnit"),
            evidenceLocator = row.value("evidenceLocator"),
            preparedBy = row.required("preparedBy"),
            preparedByType = row.enum("preparedByType"),
            claimType = row.value("claimType"),
            claimParaphrase = row.value("claimParaphrase"),
            claimDirection = row.value("claimDirection"),
            claimLowerBound = row.value("claimLowerBound").toDoubleOrNull(),
            claimUpperBound = row.value("claimUpperBound").toDoubleOrNull(),
            comparatorExercise = row.value("comparatorExercise"),
            population = row.value("population"),
            exerciseCondition = row.value("exerciseCondition"),
            loadCondition = row.value("loadCondition"),
            romCondition = row.value("romCondition"),
            velocityCondition = row.value("velocityCondition"),
            surfaceCondition = row.value("surfaceCondition"),
            anticipatedCondition = row.value("anticipatedCondition"),
            fatigueCondition = row.value("fatigueCondition"),
            evidenceLocatorType = row.value("evidenceLocatorType"),
            evidenceAccessLevel = row.value("evidenceAccessLevel"),
            preparedAt = row.value("preparedAt"),
            draftNotes = row.value("draftNotes")
        )
    }

    fun researchDecisions(csv: String): List<TissueRubricResearchDecision> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueRubricResearchDecision(
            researchDecisionId = row.required("researchDecisionId"),
            reviewBatchId = row.required("reviewBatchId"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            targetStableKeys = row.tokens("targetStableKeys"),
            database = row.required("database"),
            searchQuery = row.required("searchQuery"),
            searchDate = row.required("searchDate"),
            candidateSourceIds = row.tokens("candidateSourceIds"),
            includedSourceIds = row.tokens("includedSourceIds"),
            excludedSourceIds = row.tokens("excludedSourceIds"),
            exclusionReasons = row.tokens("exclusionReasons").map { enumValueOf<TissueResearchExclusionReason>(it) },
            populationScope = row.value("populationScope"),
            exerciseConditionScope = row.value("exerciseConditionScope"),
            measurementScope = row.value("measurementScope"),
            evidenceSufficiency = row.required("evidenceSufficiency"),
            researchDecision = row.enum("researchDecision"),
            decisionReason = row.required("decisionReason"),
            preparedBy = row.required("preparedBy"),
            preparedByType = row.enum("preparedByType"),
            preparedAt = row.required("preparedAt"),
            researchNotes = row.value("researchNotes")
        )
    }

    fun targetExerciseReviews(csv: String): List<TissueTargetExerciseReview> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueTargetExerciseReview(
            targetExerciseReviewId = row.required("targetExerciseReviewId"),
            reviewBatchId = row.required("reviewBatchId"),
            stableKey = row.required("stableKey"),
            canonicalDisplayName = row.required("canonicalDisplayName"),
            researchUseStatus = row.enum("researchUseStatus"),
            supportedTissueDimensions = row.tokens("supportedTissueDimensions").map(::dimensionReference),
            researchDecisionIds = row.tokens("researchDecisionIds"),
            sourceIds = row.tokens("sourceIds"),
            draftClaimIds = row.tokens("draftClaimIds"),
            draftRubricIds = row.tokens("draftRubricIds"),
            directProtocolMatch = row.boolean("directProtocolMatch"),
            transferDistance = row.value("transferDistance"),
            nonUseReasons = row.tokens("nonUseReasons").map { enumValueOf<TissueResearchNonUseReason>(it) },
            preparedBy = row.required("preparedBy"),
            preparedByType = row.enum("preparedByType"),
            preparedAt = row.required("preparedAt"),
            reviewNotes = row.value("reviewNotes")
        )
    }

    fun reaudits(csv: String): List<TissueEvidenceReaudit> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueEvidenceReaudit(
            reauditId = row.required("reauditId"),
            reviewBatchId = row.required("reviewBatchId"),
            draftClaimId = row.required("draftClaimId"),
            sourceId = row.required("sourceId"),
            stableKey = row.required("stableKey"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            reviewMode = row.enum("reviewMode"),
            independenceStatus = row.enum("independenceStatus"),
            identifierVerificationStatus = row.enum("identifierVerificationStatus"),
            bibliographicMatchStatus = row.enum("bibliographicMatchStatus"),
            publicationIntegrityStatus = row.enum("publicationIntegrityStatus"),
            evidenceAccessLevel = row.enum("evidenceAccessLevel"),
            evidenceLocatorType = row.required("evidenceLocatorType"),
            evidenceLocator = row.required("evidenceLocator"),
            evidenceLocatorVerified = row.boolean("evidenceLocatorVerified"),
            exerciseCorrespondence = row.enum("exerciseCorrespondence"),
            tissueCorrespondence = row.enum("tissueCorrespondence"),
            dimensionCorrespondence = row.enum("dimensionCorrespondence"),
            verifiedExercise = row.required("verifiedExercise"),
            verifiedTissue = row.required("verifiedTissue"),
            verifiedDimension = row.required("verifiedDimension"),
            verifiedDirection = row.required("verifiedDirection"),
            verifiedMetric = row.required("verifiedMetric"),
            verifiedValue = row.value("verifiedValue").toDoubleOrNull(),
            verifiedLowerBound = row.value("verifiedLowerBound").toDoubleOrNull(),
            verifiedUpperBound = row.value("verifiedUpperBound").toDoubleOrNull(),
            verifiedUnit = row.value("verifiedUnit"),
            valueType = row.enum("valueType"),
            normalizationBasis = row.value("normalizationBasis"),
            crossStudyComparability = row.enum("crossStudyComparability"),
            verifiedCondition = row.required("verifiedCondition"),
            maximumDefensibleBand = row.optionalEnum<TissueLoadBand>("maximumDefensibleBand"),
            bandBasis = row.enum("bandBasis"),
            claimSupportStatus = row.enum("claimSupportStatus"),
            recommendedActions = row.tokens("recommendedAction").map { enumValueOf<TissueReauditRecommendedAction>(it) },
            userAdjudicationIds = row.tokens("userAdjudicationIds"),
            reviewedBy = row.required("reviewedBy"),
            reviewedByType = row.enum("reviewedByType"),
            reviewedAt = row.required("reviewedAt"),
            limitations = row.required("limitations"),
            reauditNotes = row.required("reauditNotes")
        )
    }

    fun claimCandidates(csv: String): List<TissueEvidenceClaimCandidate> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueEvidenceClaimCandidate(
            claimCandidateId = row.required("claimCandidateId"),
            reviewBatchId = row.required("reviewBatchId"),
            draftClaimId = row.required("draftClaimId"),
            reauditId = row.required("reauditId"),
            sourceId = row.required("sourceId"),
            stableKey = row.required("stableKey"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            candidateClaimType = row.required("candidateClaimType"),
            candidateClaimParaphrase = row.required("candidateClaimParaphrase"),
            candidateClaimDirection = row.required("candidateClaimDirection"),
            candidateValue = row.value("candidateValue").toDoubleOrNull(),
            candidateLowerBound = row.value("candidateLowerBound").toDoubleOrNull(),
            candidateUpperBound = row.value("candidateUpperBound").toDoubleOrNull(),
            candidateUnit = row.value("candidateUnit"),
            normalizationBasis = row.value("normalizationBasis"),
            supportedCondition = row.required("supportedCondition"),
            measurementMethod = row.required("measurementMethod"),
            evidenceLocatorType = row.required("evidenceLocatorType"),
            evidenceLocator = row.required("evidenceLocator"),
            evidenceAccessLevel = row.enum("evidenceAccessLevel"),
            exerciseCorrespondence = row.enum("exerciseCorrespondence"),
            tissueCorrespondence = row.enum("tissueCorrespondence"),
            dimensionCorrespondence = row.enum("dimensionCorrespondence"),
            crossStudyComparability = row.enum("crossStudyComparability"),
            maximumDefensibleBand = row.optionalEnum<TissueLoadBand>("maximumDefensibleBand"),
            bandBasis = row.enum("bandBasis"),
            claimSupportStatus = row.enum("claimSupportStatus"),
            confidenceLevel = row.enum("confidenceLevel"),
            userAdjudicationIds = row.tokens("userAdjudicationIds"),
            reviewMode = row.enum("reviewMode"),
            independenceStatus = row.enum("independenceStatus"),
            technicalVerificationStatus = row.enum("technicalVerificationStatus"),
            productionEligibility = row.boolean("productionEligibility"),
            preparedBy = row.required("preparedBy"),
            preparedByType = row.enum("preparedByType"),
            preparedAt = row.required("preparedAt"),
            reviewedBy = row.required("reviewedBy"),
            reviewedByType = row.enum("reviewedByType"),
            reviewedAt = row.required("reviewedAt"),
            humanApprovedBy = row.value("humanApprovedBy"),
            humanApprovedAt = row.value("humanApprovedAt"),
            candidateNotes = row.required("candidateNotes")
        )
    }

    fun userAdjudications(csv: String): List<TissueUserAdjudication> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueUserAdjudication(
            adjudicationId = row.required("adjudicationId"),
            reviewBatchId = row.required("reviewBatchId"),
            adjudicationScope = row.required("adjudicationScope"),
            stableKeys = row.tokens("stableKeys"),
            tissueIds = row.tokens("tissueIds"),
            loadDimensions = row.tokens("loadDimensions").map { enumValueOf<TissueLoadDimension>(it) },
            rubricIds = row.tokens("rubricIds"),
            draftClaimIds = row.tokens("draftClaimIds"),
            decision = row.required("decision"),
            decisionRationale = row.required("decisionRationale"),
            requiredDisclosure = row.required("requiredDisclosure"),
            decisionEffect = row.required("decisionEffect"),
            decisionActorType = row.enum("decisionActorType"),
            decisionSource = row.enum("decisionSource"),
            decisionRecordedBy = row.required("decisionRecordedBy"),
            decisionRecordedAt = row.required("decisionRecordedAt"),
            isBatchApproval = row.boolean("isBatchApproval"),
            productionEligibilityEffect = row.enum("productionEligibilityEffect"),
            adjudicationNotes = row.required("adjudicationNotes")
        )
    }

    fun blindReviews(csv: String): List<TissueBlindReview> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueBlindReview(
            blindReviewId = row.required("blindReviewId"),
            draftClaimId = row.required("draftClaimId"),
            sourceId = row.required("sourceId"),
            stableKey = row.required("stableKey"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            claimVerificationStatus = row.enum("claimVerificationStatus"),
            blindReviewedBy = row.required("blindReviewedBy"),
            blindReviewedByType = row.enum("blindReviewedByType")
        )
    }

    fun finalClaims(csv: String): List<TissueFinalClaim> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueFinalClaim(
            claimId = row.required("claimId"),
            reviewBatchId = row.value("reviewBatchId"),
            reviewPath = row.optionalEnum<TissueFinalClaimReviewPath>("reviewPath"),
            draftClaimId = row.required("draftClaimId"),
            blindReviewId = row.value("blindReviewId"),
            reauditId = row.value("reauditId"),
            claimCandidateId = row.value("claimCandidateId"),
            batchApprovalId = row.value("batchApprovalId"),
            sourceId = row.required("sourceId"),
            stableKey = row.required("stableKey"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            finalClaimType = row.value("finalClaimType"),
            finalClaimParaphrase = row.value("finalClaimParaphrase"),
            finalClaimDirection = row.value("finalClaimDirection"),
            finalClaimValue = row.value("finalClaimValue").toDoubleOrNull(),
            finalClaimLowerBound = row.value("finalClaimLowerBound").toDoubleOrNull(),
            finalClaimUpperBound = row.value("finalClaimUpperBound").toDoubleOrNull(),
            finalClaimUnit = row.value("finalClaimUnit"),
            normalizationBasis = row.value("normalizationBasis"),
            supportedCondition = row.value("supportedCondition"),
            measurementMethod = row.value("measurementMethod"),
            evidenceLocatorType = row.value("evidenceLocatorType"),
            evidenceLocator = row.value("evidenceLocator"),
            evidenceAccessLevel = row.optionalEnum<TissueEvidenceAccessLevel>("evidenceAccessLevel"),
            exerciseCorrespondence = row.optionalEnum<TissueExerciseCorrespondence>("exerciseCorrespondence"),
            tissueCorrespondence = row.optionalEnum<TissueCorrespondence>("tissueCorrespondence"),
            dimensionCorrespondence = row.optionalEnum<TissueDimensionCorrespondence>("dimensionCorrespondence"),
            crossStudyComparability = row.optionalEnum<TissueCrossStudyComparability>("crossStudyComparability"),
            maximumDefensibleBand = row.optionalEnum<TissueLoadBand>("maximumDefensibleBand"),
            bandBasis = row.optionalEnum<TissueEvidenceBandBasis>("bandBasis"),
            claimSupportStatus = row.optionalEnum<TissueClaimSupportStatus>("claimSupportStatus"),
            confidenceLevel = row.optionalEnum<TissueEvidenceConfidenceLevel>("confidenceLevel"),
            comparisonStatus = row.optionalEnum<TissueClaimComparisonStatus>("comparisonStatus")
                ?: row.enum("draftBlindComparisonStatus"),
            identifierVerificationStatus = row.enum("identifierVerificationStatus"),
            bibliographicMatchStatus = row.enum("bibliographicMatchStatus"),
            claimVerificationStatus = row.enum("claimVerificationStatus"),
            publicationIntegrityStatus = row.enum("publicationIntegrityStatus"),
            approvalAuditManifestId = row.value("approvalAuditManifestId"),
            approvalScopeHash = row.value("approvalScopeHash"),
            sourceVerificationSnapshotHash = row.value("sourceVerificationSnapshotHash"),
            preparedBy = row.required("preparedBy"),
            preparedByType = row.enum("preparedByType"),
            preparedAt = row.value("preparedAt"),
            blindReviewedBy = row.value("blindReviewedBy"),
            blindReviewedByType = row.optionalEnum<TissueActorType>("blindReviewedByType"),
            blindReviewedAt = row.value("blindReviewedAt"),
            humanApprovedBy = row.value("humanApprovedBy"),
            humanApprovedByType = row.optionalEnum<TissueActorType>("humanApprovedByType"),
            humanApprovedAt = row.value("humanApprovedAt"),
            productionEligibility = row.boolean("productionEligibility"),
            finalClaimNotes = row.value("finalClaimNotes").ifBlank { row.value("verificationNotes") }
        )
    }

    fun batchApprovals(csv: String): List<TissueReviewBatchApproval> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueReviewBatchApproval(
            batchApprovalId = row.required("batchApprovalId"),
            reviewBatchId = row.required("reviewBatchId"),
            reviewPath = row.enum("reviewPath"),
            approvalRequestId = row.required("approvalRequestId"),
            approvalScopeHash = row.required("approvalScopeHash"),
            auditManifestId = row.required("auditManifestId"),
            auditInputSnapshotHash = row.required("auditInputSnapshotHash"),
            sourceVerificationSnapshotHash = row.required("sourceVerificationSnapshotHash"),
            publicationIntegritySnapshotHash = row.required("publicationIntegritySnapshotHash"),
            approvedClaimCandidateIds = row.tokens("approvedClaimCandidateIds"),
            approvedRubricIds = row.tokens("approvedRubricIds"),
            excludedClaimCandidateIds = row.tokens("excludedClaimCandidateIds"),
            excludedRubricIds = row.tokens("excludedRubricIds"),
            exclusionReasons = row.value("exclusionReasons"),
            humanApproverLabel = row.value("humanApproverLabel"),
            humanApproverType = row.enum("humanApproverType"),
            humanApprovedAt = row.value("humanApprovedAt"),
            automatedValidationPassed = row.boolean("automatedValidationPassed"),
            approvalDecision = row.enum("approvalDecision"),
            approvalNotes = row.value("approvalNotes")
        )
    }

    fun approvalRequests(csv: String): List<TissueReviewBatchApprovalRequest> =
        TissueMetadataParser.table(csv).rows.map { row ->
            TissueReviewBatchApprovalRequest(
                approvalRequestId = row.required("approvalRequestId"),
                reviewBatchId = row.required("reviewBatchId"),
                reviewPath = row.enum("reviewPath"),
                claimCandidateIds = row.tokens("claimCandidateIds"),
                rubricIds = row.tokens("rubricIds"),
                sourceIds = row.tokens("sourceIds"),
                userAdjudicationIds = row.tokens("userAdjudicationIds"),
                candidateCount = row.required("candidateCount").toInt(),
                rubricCount = row.required("rubricCount").toInt(),
                sourceCount = row.required("sourceCount").toInt(),
                auditManifestId = row.required("auditManifestId"),
                auditInputSnapshotHash = row.required("auditInputSnapshotHash"),
                sourceVerificationSnapshotHash = row.required("sourceVerificationSnapshotHash"),
                publicationIntegritySnapshotHash = row.required("publicationIntegritySnapshotHash"),
                approvalScopeHash = row.required("approvalScopeHash"),
                requestStatus = row.enum("requestStatus"),
                preparedBy = row.required("preparedBy"),
                preparedByType = row.enum("preparedByType"),
                preparedAt = row.required("preparedAt"),
                approvalSummary = row.required("approvalSummary"),
                knownLimitations = row.required("knownLimitations"),
                requiredUserStatement = row.required("requiredUserStatement"),
                requestNotes = row.required("requestNotes")
            )
        }

    fun approvalRequestResolutions(csv: String): List<TissueApprovalRequestResolution> =
        TissueMetadataParser.table(csv).rows.map { row ->
            TissueApprovalRequestResolution(
                resolutionId = row.required("resolutionId"),
                approvalRequestId = row.required("approvalRequestId"),
                approvalScopeHash = row.required("approvalScopeHash"),
                resolutionStatus = row.enum("resolutionStatus"),
                resolutionReason = row.required("resolutionReason"),
                replacementResearchBatchId = row.value("replacementResearchBatchId"),
                replacementApprovalRequestId = row.value("replacementApprovalRequestId"),
                replacementApprovalScopeHash = row.value("replacementApprovalScopeHash"),
                resolvedBy = row.required("resolvedBy"),
                resolvedByType = row.enum("resolvedByType"),
                resolvedAt = row.required("resolvedAt"),
                resolutionNotes = row.required("resolutionNotes")
            )
        }

    fun humanResearchDirectives(csv: String): List<TissueHumanResearchDirective> =
        TissueMetadataParser.table(csv).rows.map { row ->
            TissueHumanResearchDirective(
                directiveId = row.required("directiveId"),
                reviewBatchId = row.required("reviewBatchId"),
                claimCandidateId = row.required("claimCandidateId"),
                directiveActions = row.tokens("directiveActions"),
                researchRequirements = row.required("researchRequirements"),
                prohibitedGeneralizations = row.required("prohibitedGeneralizations"),
                directedBy = row.required("directedBy"),
                directedByType = row.enum("directedByType"),
                directedAt = row.required("directedAt"),
                directiveNotes = row.required("directiveNotes")
            )
        }

    private fun Map<String, String>.value(name: String): String = get(name).orEmpty().trim()
    private fun Map<String, String>.required(name: String): String =
        value(name).also { require(it.isNotBlank()) { "Missing required field: $name" } }
    private fun Map<String, String>.boolean(name: String): Boolean = when (value(name).uppercase(Locale.ROOT)) {
        "TRUE", "1", "YES" -> true
        "FALSE", "0", "NO", "" -> false
        else -> error("Invalid boolean in $name: ${value(name)}")
    }
    private fun Map<String, String>.tokens(name: String): List<String> =
        value(name).split('|').map(String::trim).filter { it.isNotBlank() && it != "NONE" }.distinct()
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T =
        enumValueOf(required(name).uppercase(Locale.ROOT))
    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(name: String): T? =
        value(name).takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)?.let { enumValueOf<T>(it) }

    private fun dimensionReference(value: String): TissueDimensionReference {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2 && parts.all(String::isNotBlank)) { "Invalid tissue/dimension reference: $value" }
        return TissueDimensionReference(parts[0], enumValueOf(parts[1].uppercase(Locale.ROOT)))
    }
}
