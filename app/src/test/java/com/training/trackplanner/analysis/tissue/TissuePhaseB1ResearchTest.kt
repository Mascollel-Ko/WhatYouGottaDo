package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.ExerciseMetadataAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissuePhaseB1ResearchTest {
    @Test
    fun committedDraftEvidenceBatchIsCompleteNonProductionAndReferentiallyValid() {
        val canonical = ExerciseMetadataAdapter.fromCsv(asset("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"))
        val canonicalNames = canonical.associate { it.stableKey to it.exerciseName }
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val sources = TissueEvidenceParser.sources(tissueAsset("tissue_load_evidence_registry_v1.csv"))
        val verifications = TissueEvidenceParser.sourceVerifications(tissueAsset("tissue_source_verification_v1.csv"))
        val drafts = TissueEvidenceParser.draftClaims(tissueAsset("tissue_evidence_claims_draft_v1.csv"))
        val decisions = TissueEvidenceParser.researchDecisions(tissueAsset("tissue_rubric_research_log_v1.csv"))
        val reviews = TissueEvidenceParser.targetExerciseReviews(tissueAsset("tissue_rubric_target_exercise_review_v1.csv"))
        val rubrics = TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv"))
        val blind = TissueEvidenceParser.blindReviews(tissueAsset("tissue_evidence_blind_review_v1.csv"))
        val final = TissueEvidenceParser.finalClaims(tissueAsset("tissue_evidence_claims_v1.csv"))

        assertEquals(10, sources.size)
        assertEquals(10, verifications.size)
        assertTrue(sources.all { it.identifierVerificationStatus == TissueIdentifierVerificationStatus.PMID_AND_DOI_VERIFIED })
        assertTrue(sources.all { it.bibliographicMatchStatus == TissueBibliographicMatchStatus.MATCHED })
        assertEquals(12, drafts.size)
        assertEquals(31, decisions.size)
        assertEquals(15, reviews.size)
        assertEquals(2, decisions.count { it.researchDecision == TissueResearchDecision.DRAFT_RUBRIC_CREATED })
        assertEquals(5, rubrics.size)
        assertEquals(6, reviews.count { it.researchUseStatus == TissueResearchUseStatus.USED_AS_DIRECT_ANCHOR })
        assertEquals(7, reviews.count { it.researchUseStatus == TissueResearchUseStatus.USED_AS_TRANSFER_REFERENCE })
        assertEquals(2, reviews.count { it.researchUseStatus == TissueResearchUseStatus.NO_COMPARABLE_SOURCE_FOUND })
        assertTrue(blind.isEmpty())
        assertTrue(final.isEmpty())

        val sourceReport = TissueEvidenceValidator.sourceRegistry(sources, verifications)
        assertTrue(sourceReport.errors.toString(), sourceReport.isValid)
        val evidenceReport = TissueEvidenceValidator.validate(
            sources, drafts, blind, final, canonicalNames.keys, catalog
        )
        assertTrue(evidenceReport.errors.toString(), evidenceReport.isValid)
        val researchReport = TissueEvidenceValidator.phaseB1Research(
            sources, drafts, decisions, reviews, canonicalNames, catalog
        )
        assertTrue(researchReport.errors.toString(), researchReport.isValid)
        val rubricReport = TissueEvidenceValidator.phaseB1Rubrics(sources, drafts, decisions, reviews, rubrics)
        assertTrue(rubricReport.errors.toString(), rubricReport.isValid)
        assertTrue(rubrics.all { it.rubricStatus == TissueRubricStatus.DRAFT_RESEARCHED_PENDING_BLIND_REVIEW })
        assertTrue(rubrics.all { it.anchorClaimIds.isEmpty() && it.evidenceClaimIds.isEmpty() })
        assertTrue(rubrics.all { it.draftClaimIds.isNotEmpty() && it.sourceRefs.isNotEmpty() })
        assertTrue(rubrics.all { it.humanApprovedBy.isBlank() && it.humanApprovedAt.isBlank() })

        val profileFiles = listOf(
            "exercise_joint_load_profiles_v1.csv",
            "exercise_tendon_load_profiles_v1.csv",
            "exercise_ligament_load_profiles_v1.csv",
            "exercise_fascia_load_profiles_v1.csv"
        )
        assertTrue(profileFiles.all { TissueMetadataParser.profiles(tissueAsset(it)).isEmpty() })
        assertTrue(TissueMetadataParser.scope(tissueAsset("exercise_tissue_scope_manifest_v1.csv")).none(TissueScopeEntry::productionEligibility))
    }

    @Test
    fun draftClaimsRemainConditionBoundedAndMachineReadable() {
        val drafts = TissueEvidenceParser.draftClaims(tissueAsset("tissue_evidence_claims_draft_v1.csv"))

        assertTrue(drafts.all { it.preparedByType == TissueActorType.AI_AGENT })
        assertTrue(drafts.all { it.exerciseCondition.isNotBlank() })
        assertTrue(drafts.all { it.claimParaphrase.isNotBlank() })
        assertTrue(drafts.filter { it.claimValue != null }.all { it.claimUnit.isNotBlank() && it.evidenceLocator.isNotBlank() })
        assertEquals(
            drafts.size,
            drafts.map { listOf(it.sourceId, it.stableKey, it.tissueId, it.loadDimension.name, it.exerciseCondition) }.distinct().size
        )
    }

    @Test
    fun phaseB1RejectsApprovalAndFinalClaimContamination() {
        val sources = TissueEvidenceParser.sources(tissueAsset("tissue_load_evidence_registry_v1.csv"))
        val drafts = TissueEvidenceParser.draftClaims(tissueAsset("tissue_evidence_claims_draft_v1.csv"))
        val decisions = TissueEvidenceParser.researchDecisions(tissueAsset("tissue_rubric_research_log_v1.csv"))
        val reviews = TissueEvidenceParser.targetExerciseReviews(tissueAsset("tissue_rubric_target_exercise_review_v1.csv"))
        val rubrics = TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv"))
        val invalid = rubrics.first().copy(
            anchorClaimIds = listOf(rubrics.first().draftClaimIds.first()),
            rubricStatus = TissueRubricStatus.APPROVED,
            humanApprovedBy = "Codex",
            humanApprovedAt = "2026-07-14T00:00:00Z"
        )

        val errors = TissueEvidenceValidator.phaseB1Rubrics(
            sources, drafts, decisions, reviews, listOf(invalid) + rubrics.drop(1)
        ).errors

        assertTrue(errors.any { it.contains("final-claim columns") })
        assertTrue(errors.any { it.contains("status is not allowed") })
        assertTrue(errors.any { it.contains("human approval") })
    }

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)
}
