package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class TissueRubricBoundaryAndIntegrityTest {
    @Test
    fun pfjBoundaryUsesExactNonOverlappingDecimalIntervals() {
        val pfj = rubrics.filter { it.tissueId == "KNEE_PATELLOFEMORAL" }

        assertTrue(TissueMetadataValidator.rubricIntervals(rubrics).errors.toString(),
            TissueMetadataValidator.rubricIntervals(rubrics).isValid)
        assertEquals(listOf(TissueLoadBand.LOW), TissueMetadataValidator.matchingRubricBands(pfj, BigDecimal("0.333")))
        assertEquals(listOf(TissueLoadBand.MODERATE), TissueMetadataValidator.matchingRubricBands(pfj, BigDecimal("0.3331")))
        assertEquals(listOf(TissueLoadBand.MODERATE), TissueMetadataValidator.matchingRubricBands(pfj, BigDecimal("0.667")))
    }

    @Test
    fun invalidInclusivityAndOverlapFailWithoutRequiringMissingHighBand() {
        val low = rubrics.single { it.rubricId == "RUBRIC_PFJ_COMP_LOW" }
        val moderate = rubrics.single { it.rubricId == "RUBRIC_PFJ_COMP_MODERATE" }

        val overlap = TissueMetadataValidator.rubricIntervals(listOf(low, moderate.copy(lowerBoundInclusive = true)))
        assertFalse(overlap.isValid)
        assertTrue(overlap.errors.any { "overlapping intervals" in it })

        val blankBound = TissueMetadataValidator.rubricIntervals(listOf(low.copy(metricUpperBound = null)))
        assertFalse(blankBound.isValid)
        assertTrue(blankBound.errors.any { "upper-bound inclusivity" in it })
        assertFalse(rubrics.any { it.tissueId == "KNEE_PATELLOFEMORAL" && it.loadBand == TissueLoadBand.HIGH })
    }

    @Test
    fun committedPublicationIntegrityCoversEverySourceWithAuthoritativeMetadata() {
        val report = TissueEvidenceValidator.publicationIntegrity(sources, sourceVerifications, integrityRows)

        assertEquals(10, sources.size)
        assertEquals(sources.map { it.sourceId }.toSet(), integrityRows.map { it.sourceId }.toSet())
        assertTrue(report.errors.toString(), report.isValid)
        assertTrue(integrityRows.all { it.integrityCheckStatus == TissuePublicationIntegrityCheckStatus.NO_ADVERSE_NOTICE_FOUND })
        assertTrue(integrityRows.all { it.metadataSnapshotHash.length == 64 })
    }

    @Test
    fun publicationIntegrityRejectsUnknownRowsMissingHashesAndNonAuthoritativeChecks() {
        val row = integrityRows.first()
        val unknown = TissueEvidenceValidator.publicationIntegrity(
            sources,
            sourceVerifications,
            integrityRows.drop(1) + row.copy(sourceId = "unknown")
        )
        assertFalse(unknown.isValid)
        assertTrue(unknown.errors.any { "unknown source" in it })

        val incomplete = publicationReport(row.copy(metadataSnapshotHash = "", verificationMethod = "HTTP_200"))
        assertFalse(incomplete.isValid)
        assertTrue(incomplete.errors.any { "snapshot hash is missing" in it })
        assertTrue(incomplete.errors.any { "authoritative publication-integrity checks are incomplete" in it })

        val missingResult = publicationReport(row.copy(pubmedPublicationTypes = emptyList()))
        assertFalse(missingResult.isValid)
    }

    @Test
    fun productionIntegrityGateFailsClosedAndRequiresReviewedCorrections() {
        val source = sources.first()
        val row = integrityRows.single { it.sourceId == source.sourceId }
        assertTrue(TissueEvidenceValidator.productionIntegrityGate(source, row).isValid)

        listOf(
            TissuePublicationIntegrityStatus.STATUS_UNKNOWN to TissuePublicationIntegrityCheckStatus.UNABLE_TO_VERIFY,
            TissuePublicationIntegrityStatus.RETRACTED to TissuePublicationIntegrityCheckStatus.RETRACTED,
            TissuePublicationIntegrityStatus.EXPRESSION_OF_CONCERN to TissuePublicationIntegrityCheckStatus.EXPRESSION_OF_CONCERN,
            TissuePublicationIntegrityStatus.CORRECTED to TissuePublicationIntegrityCheckStatus.CORRECTION_FOUND_REVIEW_REQUIRED
        ).forEach { (sourceStatus, checkStatus) ->
            assertFalse(TissueEvidenceValidator.productionIntegrityGate(
                source.copy(publicationIntegrityStatus = sourceStatus),
                row.copy(integrityCheckStatus = checkStatus)
            ).isValid)
        }
        assertTrue(TissueEvidenceValidator.productionIntegrityGate(
            source.copy(publicationIntegrityStatus = TissuePublicationIntegrityStatus.CORRECTED),
            row.copy(integrityCheckStatus = TissuePublicationIntegrityCheckStatus.CORRECTION_REVIEWED_ACCEPTABLE)
        ).isValid)
    }

    private fun publicationReport(row: TissuePublicationIntegrityVerification): TissueValidationReport {
        val source = sources.single { it.sourceId == row.sourceId }
        val verification = sourceVerifications.single { it.sourceId == row.sourceId }
        return TissueEvidenceValidator.publicationIntegrity(listOf(source), listOf(verification), listOf(row))
    }

    private val rubrics by lazy { TissueMetadataParser.rubrics(tissueAsset("tissue_load_band_rubric_v1.csv")) }
    private val sources by lazy { TissueEvidenceParser.sources(tissueAsset("tissue_load_evidence_registry_v1.csv")) }
    private val sourceVerifications by lazy {
        TissueEvidenceParser.sourceVerifications(tissueAsset("tissue_source_verification_v1.csv"))
    }
    private val integrityRows by lazy {
        TissueEvidenceParser.publicationIntegrityVerifications(
            tissueAsset("tissue_publication_integrity_verification_v1.csv")
        )
    }

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)
}
