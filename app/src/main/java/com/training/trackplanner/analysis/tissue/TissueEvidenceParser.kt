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
            draftClaimId = row.required("draftClaimId"),
            blindReviewId = row.required("blindReviewId"),
            sourceId = row.required("sourceId"),
            stableKey = row.required("stableKey"),
            tissueId = row.required("tissueId"),
            loadDimension = row.enum("loadDimension"),
            finalClaimValue = row.value("finalClaimValue").toDoubleOrNull(),
            finalClaimUnit = row.value("finalClaimUnit"),
            evidenceLocator = row.value("evidenceLocator"),
            comparisonStatus = row.enum("draftBlindComparisonStatus"),
            identifierVerificationStatus = row.enum("identifierVerificationStatus"),
            bibliographicMatchStatus = row.enum("bibliographicMatchStatus"),
            claimVerificationStatus = row.enum("claimVerificationStatus"),
            publicationIntegrityStatus = row.enum("publicationIntegrityStatus"),
            preparedBy = row.required("preparedBy"),
            preparedByType = row.enum("preparedByType"),
            blindReviewedBy = row.required("blindReviewedBy"),
            blindReviewedByType = row.enum("blindReviewedByType"),
            humanApprovedBy = row.value("humanApprovedBy"),
            productionEligibility = row.boolean("productionEligibility")
        )
    }

    fun batchApprovals(csv: String): List<TissueReviewBatchApproval> = TissueMetadataParser.table(csv).rows.map { row ->
        TissueReviewBatchApproval(
            reviewBatchId = row.required("reviewBatchId"),
            auditManifestId = row.required("auditManifestId"),
            humanApprover = row.value("humanApprover"),
            humanApprovedAt = row.value("humanApprovedAt"),
            automatedValidationPassed = row.boolean("automatedValidationPassed"),
            approvalDecision = row.required("approvalDecision")
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

    private fun dimensionReference(value: String): TissueDimensionReference {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2 && parts.all(String::isNotBlank)) { "Invalid tissue/dimension reference: $value" }
        return TissueDimensionReference(parts[0], enumValueOf(parts[1].uppercase(Locale.ROOT)))
    }
}
