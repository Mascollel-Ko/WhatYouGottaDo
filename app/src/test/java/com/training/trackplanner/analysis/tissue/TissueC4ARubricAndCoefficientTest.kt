package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC4ARubricAndCoefficientTest {
    @Test
    fun everyAxisHasAnExplicitScaleAndOperationalFallbackWithoutFabricatedResearch() {
        assertEquals(48, scales.size)
        assertEquals(96, rubrics.size)
        assertEquals(48, rubrics.count { it.rubricKind == TissueMtcRubricKind.FAMILY_DEFAULT })
        assertEquals(48, rubrics.count { it.rubricKind == TissueMtcRubricKind.CONSERVATIVE_FALLBACK })
        assertEquals(0, rubrics.count { it.rubricKind == TissueMtcRubricKind.ABSOLUTE_INTERVAL })
        assertEquals(48, provenance.size)
        assertTrue(provenance.all { it.researchScore == null && it.operationalScore > 0.0 })
        assertTrue(report.errors.toString(), report.isValid)
    }

    @Test
    fun unknownNeverBecomesZeroAndInvalidIntervalsFailCalibrationGate() {
        val invalidZero = rubrics.first().copy(rubricId = "INVALID_ZERO", score = 0.0)
        val invalidInterval = rubrics.first().copy(
            rubricId = "INVALID_INTERVAL", rubricKind = TissueMtcRubricKind.ABSOLUTE_INTERVAL,
            lowerBound = 1.0, upperBound = 2.0, lowerInclusive = true, upperInclusive = false,
            independentSourceCount = 1, boundaryDerivation = "", sensitivityAnalysisStatus = "NOT_RUN",
            operationalOnly = false, researchEligible = true
        )
        val invalid = TissueMtcValidator.rubricFoundation(scales, rubrics + invalidZero + invalidInterval, provenance, fallbacks, coefficients)
        assertFalse(invalid.isValid)
        assertTrue(invalid.errors.any { "UNKNOWN or fallback cannot become zero" in it })
        assertTrue(invalid.errors.any { "calibration gate failed" in it })
    }

    @Test
    fun fallbackLadderAndParentChildAllocationAreExplicit() {
        assertEquals((1..6).toList(), fallbacks.map { it.priority })
        assertEquals(TissueMtcInheritanceLevel.EXACT_CONDITION, fallbacks.first().inheritanceLevel)
        assertEquals(TissueMtcInheritanceLevel.CONSERVATIVE_FALLBACK, fallbacks.last().inheritanceLevel)
        assertTrue(fallbacks.all { it.allocationPolicy == "PARENT_ONLY_WHEN_COMPLEX_FALLBACK" })
    }

    @Test
    fun coefficientSetPinsPortableSemanticSnapshotsAndRemainsNonProduction() {
        val coefficient = coefficients.single()
        assertEquals("DRAFT_NON_PRODUCTION", coefficient.status)
        assertTrue(coefficient.effectiveFrom.isBlank() && coefficient.publishedAt.isBlank())
        assertEquals(hash("tissue_source_metric_extraction_c3_1_v1.csv"), coefficient.sourceSnapshotHash)
        assertEquals(hash("tissue_mtc_axis_rubric_v1.csv"), coefficient.rubricSnapshotHash)
        assertEquals(hash("tissue_functional_complex_registry_v1.csv"), coefficient.complexRegistrySnapshotHash)
        assertEquals(hash("tissue_mtc_axis_metric_registry_v1.csv"), coefficient.axisRegistrySnapshotHash)

        val rubricText = assetFile("tissue_mtc_axis_rubric_v1.csv").readText(Charsets.UTF_8)
        assertEquals(TissueMetadataValidator.semanticCsvHash(rubricText), TissueMetadataValidator.semanticCsvHash(rubricText.replace("\r\n", "\n")))
        assertNotEquals(coefficient.rubricSnapshotHash, TissueMetadataValidator.semanticCsvHash(rubricText.replaceFirst("2.0", "3.0")))
    }

    private val scales by lazy { TissueMtcParser.axisScales(asset("tissue_mtc_axis_scale_registry_v1.csv")) }
    private val rubrics by lazy { TissueMtcParser.rubrics(asset("tissue_mtc_axis_rubric_v1.csv")) }
    private val provenance by lazy { TissueMtcParser.axisProvenance(asset("tissue_mtc_axis_provenance_v1.csv")) }
    private val fallbacks by lazy { TissueMtcParser.fallbackRules(asset("tissue_mtc_fallback_rule_v1.csv")) }
    private val coefficients by lazy { TissueMtcParser.coefficientSets(asset("tissue_mtc_coefficient_set_manifest_v1.csv")) }
    private val report by lazy { TissueMtcValidator.rubricFoundation(scales, rubrics, provenance, fallbacks, coefficients) }
    private fun hash(name: String) = TissueMetadataValidator.semanticCsvHash(asset(name))
    private fun asset(name: String) = assetFile(name).readText(Charsets.UTF_8)
    private fun assetFile(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists)
}
