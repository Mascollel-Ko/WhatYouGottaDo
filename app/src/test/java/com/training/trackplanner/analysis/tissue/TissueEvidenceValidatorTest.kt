package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.ExerciseMetadataAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueEvidenceValidatorTest {
    @Test
    fun committedPreflightArtifactRemainsFailClosed() {
        val sources = TissueEvidenceParser.sources(tissueAsset("tissue_load_evidence_registry_v1.csv"))
        val source = sources.single()
        val draft = TissueEvidenceParser.draftClaims(tissueAsset("tissue_evidence_claims_draft_v1.csv"))
        val blind = TissueEvidenceParser.blindReviews(tissueAsset("tissue_evidence_blind_review_v1.csv"))
        val final = TissueEvidenceParser.finalClaims(tissueAsset("tissue_evidence_claims_v1.csv"))

        assertEquals(TissueNetworkCapabilityStatus.PARTIAL_SOURCE_VERIFICATION_AVAILABLE, source.verificationCapabilityStatus)
        assertEquals(TissueIdentifierVerificationStatus.UNVERIFIED, source.identifierVerificationStatus)
        assertEquals(TissueBibliographicMatchStatus.UNVERIFIED, source.bibliographicMatchStatus)
        assertEquals("UNVERIFIED", source.sourceStatus)
        assertTrue(draft.isEmpty())
        assertTrue(blind.isEmpty())
        assertTrue(final.isEmpty())

        val canonical = ExerciseMetadataAdapter.fromCsv(asset("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"))
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        assertTrue(
            TissueEvidenceValidator.validate(sources, draft, blind, final, canonical.map { it.stableKey }.toSet(), catalog).isValid
        )
    }

    @Test
    fun unverifiedTitleOnlyRetractedOrSelfReviewedClaimCannotPassProductionGate() {
        val source = source()
        val draft = draft()
        val blind = blind().copy(blindReviewedBy = draft.preparedBy)
        val claim = finalClaim().copy(
            identifierVerificationStatus = TissueIdentifierVerificationStatus.PMID_VERIFIED,
            bibliographicMatchStatus = TissueBibliographicMatchStatus.MATCHED,
            claimVerificationStatus = TissueClaimVerificationStatus.TITLE_ONLY,
            publicationIntegrityStatus = TissuePublicationIntegrityStatus.RETRACTED,
            productionEligibility = true,
            humanApprovedBy = ""
        )
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val report = TissueEvidenceValidator.validate(
            listOf(source),
            listOf(draft),
            listOf(blind),
            listOf(claim),
            setOf(draft.stableKey),
            catalog
        )

        assertFalse(report.isValid)
        assertTrue(report.errors.any { "blind reviewer matches preparer" in it })
        assertTrue(report.errors.any { "production source/claim gate failed" in it })
        assertTrue(report.errors.any { "lacks human approval" in it })
    }

    @Test
    fun numericClaimsRequireUnitsAndEvidenceLocators() {
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val report = TissueEvidenceValidator.validate(
            listOf(source()),
            listOf(draft().copy(claimValue = 12.0, claimUnit = "", evidenceLocator = "")),
            emptyList(),
            emptyList(),
            setOf("ex_fixture"),
            catalog
        )

        assertFalse(report.isValid)
        assertTrue(report.errors.any { "numeric claim lacks unit or locator" in it })
    }

    @Test
    fun batchApprovalCannotReferenceMissingOrBlockedAuditAndCannotOmitHuman() {
        val approval = TissueReviewBatchApproval(
            reviewBatchId = "batch",
            auditManifestId = "missing",
            humanApprover = "",
            humanApprovedAt = "",
            automatedValidationPassed = false,
            approvalDecision = "APPROVED"
        )
        val report = TissueEvidenceValidator.batchApprovals(listOf(approval), emptyList())

        assertFalse(report.isValid)
        assertEquals(3, report.errors.size)
        assertTrue(TissueEvidenceParser.batchApprovals(tissueAsset("tissue_review_batch_approval_v1.csv")).isEmpty())
    }

    @Test
    fun studyBackedProfileCannotUseUnverifiedSourceOrMissingFinalClaim() {
        val profile = profile().copy(
            evidenceStatus = TissueEvidenceStatus.STUDY_BACKED,
            productionEligibility = true,
            evidenceClaimIds = listOf("missing_claim"),
            sourceRefs = listOf("PREFLIGHT_32658037")
        )
        val report = TissueEvidenceValidator.productionProfiles(listOf(profile), emptyList(), listOf(source()))

        assertFalse(report.isValid)
        assertTrue(report.errors.any { "unknown evidence claim" in it })
        assertTrue(report.errors.any { "lacks approved final claims" in it })
        assertTrue(report.errors.any { "source gate failed" in it })
    }

    private fun source() = TissueEvidenceSource(
        sourceId = "PREFLIGHT_32658037",
        pmid = "32658037",
        doi = "10.2519/jospt.2020.9406",
        title = "Exercise Progression to Incrementally Load the Achilles Tendon.",
        identifierVerificationStatus = TissueIdentifierVerificationStatus.UNVERIFIED,
        bibliographicMatchStatus = TissueBibliographicMatchStatus.UNVERIFIED,
        publicationIntegrityStatus = TissuePublicationIntegrityStatus.STATUS_UNKNOWN,
        verificationCapabilityStatus = TissueNetworkCapabilityStatus.PARTIAL_SOURCE_VERIFICATION_AVAILABLE,
        sourceStatus = "UNVERIFIED"
    )

    private fun draft() = TissueDraftClaim(
        draftClaimId = "draft",
        sourceId = "PREFLIGHT_32658037",
        stableKey = "ex_fixture",
        tissueId = "ACHILLES_TENDON",
        loadDimension = TissueLoadDimension.PEAK_TENSILE_LOAD,
        proposedBand = null,
        claimValue = null,
        claimUnit = "",
        evidenceLocator = "",
        preparedBy = "Codex",
        preparedByType = TissueActorType.AI_AGENT
    )

    private fun blind() = TissueBlindReview(
        blindReviewId = "blind",
        draftClaimId = "draft",
        sourceId = "PREFLIGHT_32658037",
        stableKey = "ex_fixture",
        tissueId = "ACHILLES_TENDON",
        loadDimension = TissueLoadDimension.PEAK_TENSILE_LOAD,
        claimVerificationStatus = TissueClaimVerificationStatus.UNREVIEWED,
        blindReviewedBy = "Independent",
        blindReviewedByType = TissueActorType.HUMAN
    )

    private fun finalClaim() = TissueFinalClaim(
        claimId = "claim",
        draftClaimId = "draft",
        blindReviewId = "blind",
        sourceId = "PREFLIGHT_32658037",
        stableKey = "ex_fixture",
        tissueId = "ACHILLES_TENDON",
        loadDimension = TissueLoadDimension.PEAK_TENSILE_LOAD,
        finalClaimValue = null,
        finalClaimUnit = "",
        evidenceLocator = "",
        comparisonStatus = TissueClaimComparisonStatus.MATCH,
        identifierVerificationStatus = TissueIdentifierVerificationStatus.UNVERIFIED,
        bibliographicMatchStatus = TissueBibliographicMatchStatus.UNVERIFIED,
        claimVerificationStatus = TissueClaimVerificationStatus.UNREVIEWED,
        publicationIntegrityStatus = TissuePublicationIntegrityStatus.STATUS_UNKNOWN,
        preparedBy = "Codex",
        preparedByType = TissueActorType.AI_AGENT,
        blindReviewedBy = "Independent",
        blindReviewedByType = TissueActorType.HUMAN,
        humanApprovedBy = "",
        productionEligibility = false
    )

    private fun profile() = TissueLoadProfile(
        profileRowId = "profile",
        stableKey = "ex_fixture",
        tissueClass = TissueClass.TENDON,
        tissueId = "ACHILLES_TENDON",
        loadDimension = TissueLoadDimension.PEAK_TENSILE_LOAD,
        loadBand = TissueLoadBand.HIGH,
        evidenceStatus = TissueEvidenceStatus.ANATOMICAL_INFERENCE,
        evidenceLevel = "ANATOMICAL_INFERENCE",
        confidenceLevel = "LOW",
        reviewStatus = "DRAFT",
        productionEligibility = false,
        doseBasis = "EXTERNAL_LOAD_REPETITIONS",
        referenceConditionId = "fixture",
        modifierSetId = "",
        recoveryProfileId = "",
        sideAllocationPolicy = "BILATERAL_SHARED",
        rubricId = "fixture",
        evidenceSetId = "",
        evidenceClaimIds = emptyList(),
        sourceRefs = emptyList(),
        reviewBatchId = "",
        preparedBy = "Codex",
        preparedByType = TissueActorType.AI_AGENT,
        preparedAt = "2026-07-13",
        blindReviewedBy = "",
        blindReviewedByType = null,
        blindReviewedAt = "",
        humanApprovedBy = "",
        humanApprovedAt = "",
        reviewNotes = "fixture"
    )

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)
}
