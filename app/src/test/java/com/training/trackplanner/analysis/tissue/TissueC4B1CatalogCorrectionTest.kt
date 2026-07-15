package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC4B1CatalogCorrectionTest {
    @Test
    fun extractedSeedsHaveConsistentEffectiveSemantics() {
        val effective = TissueMtcResearchCatalogValidator.effectiveSeeds(seeds, corrections)
        val extracted = effective.filter { it["verificationStatus"] == "METRIC_EXTRACTED" }

        assertEquals(3, extracted.size)
        assertFalse(extracted.any { row ->
            listOf("reportedMetrics", "exerciseConditions", "knownLimitations", "notes")
                .any { field -> row[field].orEmpty().contains("NOT_YET_EXTRACTED", true) || row[field].orEmpty().contains("verification required", true) }
        })
        assertTrue(report.errors.toString(), report.isValid)
    }

    @Test
    fun internalMeasurementsAreNotMisclassifiedAsContextOnly() {
        val effective = TissueMtcResearchCatalogValidator.effectiveSeeds(seeds, corrections).associateBy { it.getValue("seedId") }

        assertEquals("VALIDATED_INTERNAL_MODEL", effective.getValue("TISSUE_MTC_SEED_003").getValue("evidenceRelationCandidate"))
        assertEquals("DIRECT_INTERNAL_MEASUREMENT", effective.getValue("TISSUE_MTC_SEED_041").getValue("evidenceRelationCandidate"))
        assertEquals("CADAVERIC_MECHANISM", effective.getValue("TISSUE_MTC_SEED_105").getValue("evidenceRelationCandidate"))
        assertEquals("UNVALIDATED_PROXY", effective.getValue("TISSUE_MTC_SEED_029").getValue("evidenceRelationCandidate"))
    }

    @Test
    fun pendingInternalModelsCannotMasqueradeAsValidatedResearchEvidence() {
        val pending = corrections.filter { it.evidenceRelationReviewStatus == TissueMtcEvidenceRelationReviewStatus.INTERNAL_MODEL_VALIDATION_PENDING }
        assertTrue(pending.isNotEmpty())
        assertTrue(pending.all { it.evidenceRelation == TissueMtcEvidenceRelation.VALIDATED_INTERNAL_MODEL && !it.researchEvidenceEligible })

        val invalid = pending.first().copy(evidenceRelation = TissueMtcEvidenceRelation.CONTEXT_ONLY)
        val invalidReport = TissueMtcResearchCatalogValidator.validate(seeds, conditions, extractions, candidates, gaps, corrections - pending.first() + invalid)
        assertTrue(invalidReport.errors.any { "pending internal-model review" in it })
    }

    @Test
    fun adversePublicationNoticeBlocksCatalogValidation() {
        val blocked = corrections.first().copy(publicationIntegrityStatus = "RETRACTED")
        val blockedReport = TissueMtcResearchCatalogValidator.validate(seeds, conditions, extractions, candidates, gaps, corrections - corrections.first() + blocked)
        assertTrue(blockedReport.errors.any { "publication-integrity blocker" in it })
    }

    private val seeds by lazy { table("tissue_mtc_research_seed_catalog_v1.csv") }
    private val conditions by lazy { table("tissue_source_condition_registry_v1.csv") }
    private val extractions by lazy { table("tissue_source_metric_extraction_c4a_v1.csv") }
    private val candidates by lazy { table("tissue_evidence_claim_candidates_c4a_v1.csv") }
    private val gaps by lazy { table("tissue_mtc_research_gap_matrix_v1.csv") }
    private val corrections by lazy { TissueMtcParser.seedSemanticCorrections(asset("tissue_mtc_seed_semantic_correction_c4b1_v1.csv")) }
    private val report by lazy { TissueMtcResearchCatalogValidator.validate(seeds, conditions, extractions, candidates, gaps, corrections) }
    private fun table(name: String) = TissueMetadataParser.table(asset(name)).rows
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
