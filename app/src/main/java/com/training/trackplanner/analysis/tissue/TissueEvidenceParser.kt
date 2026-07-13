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
            sourceStatus = row.required("sourceStatus")
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
            preparedByType = row.enum("preparedByType")
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
    private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T =
        enumValueOf(required(name).uppercase(Locale.ROOT))
}
