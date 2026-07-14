package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueMultidimensionalOntologyTest {
    @Test
    fun ontologyIsClosedCompatibleAndCoversEveryLegacyDimension() {
        assertEquals(TissueMechanicalLoadMode.entries.toSet(), modes.map { it.mechanicalLoadMode }.toSet())
        assertEquals(TissueTemporalMetric.entries.toSet(), temporalMetrics.map { it.temporalMetric }.toSet())
        assertEquals(TissueMeasurementMetric.entries.toSet(), measurementMetrics.map { it.measurementMetric }.toSet())
        assertEquals(TissueNormalizationBasis.entries.toSet(), normalizations.map { it.normalizationBasis }.toSet())
        assertEquals(TissueLoadDimension.entries.toSet(), migrations.map { it.legacyDimension }.toSet())
        assertTrue(report.errors.toString(), report.isValid)
    }

    @Test
    fun loadModeTemporalMetricAndMeasurementRemainIndependent() {
        val peak = dimensions.single { it.dimensionId == "PFJ_COMPRESSION_PEAK" }
        val impulse = dimensions.single { it.dimensionId == "PFJ_COMPRESSION_IMPULSE_EVENT" }
        val rate = dimensions.single { it.dimensionId == "PFJ_COMPRESSION_LOADING_RATE" }

        assertEquals(TissueMechanicalLoadMode.COMPRESSION, peak.mechanicalLoadMode)
        assertEquals(setOf(TissueTemporalMetric.PEAK, TissueTemporalMetric.IMPULSE_PER_EVENT, TissueTemporalMetric.LOADING_RATE),
            setOf(peak.temporalMetric, impulse.temporalMetric, rate.temporalMetric))
        assertEquals(setOf(TissueMeasurementMetric.MODELED_JOINT_CONTACT_FORCE), peak.allowedMeasurementMetrics)
        assertEquals(setOf(TissueMeasurementMetric.JOINT_CONTACT_FORCE_TIME_INTEGRAL), impulse.allowedMeasurementMetrics)
        assertEquals(setOf(TissueMeasurementMetric.MODELED_JOINT_CONTACT_FORCE_LOADING_RATE), rate.allowedMeasurementMetrics)
    }

    @Test
    fun invalidCrossProductAndUnspecifiedDerivedFormulaFailClosed() {
        val invalidMetric = dimensions.first().copy(
            dimensionId = "INVALID_METRIC",
            allowedMeasurementMetrics = setOf(TissueMeasurementMetric.GROUND_REACTION_FORCE_LOADING_RATE)
        )
        val missingFormula = dimensions.single { it.dimensionId == "PFJ_COMPRESSION_SESSION_IMPULSE" }
            .copy(dimensionId = "MISSING_FORMULA", derivedFormulaId = "")

        val invalid = validate(dimensions + invalidMetric + missingFormula)
        assertFalse(invalid.isValid)
        assertTrue(invalid.errors.any { "incompatible measurement metric" in it })
        assertTrue(invalid.errors.any { "requires a formula" in it })
    }

    @Test
    fun sourceSpecificCompositeCannotBecomeGenericRubricOrProfile() {
        val invalid = dimensions.first().copy(
            dimensionId = "INVALID_COMPOSITE",
            allowedMeasurementMetrics = setOf(TissueMeasurementMetric.SOURCE_DEFINED_COMPOSITE_INDEX),
            allowedNormalizationBases = setOf(TissueNormalizationBasis.SOURCE_DEFINED_NORMALIZED_INDEX),
            rubricEligible = true,
            profileEligible = true
        )

        val result = validate(dimensions + invalid)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "source-specific composite" in it })
    }

    @Test
    fun historicalDimensionsAndRequestsRemainParseable() {
        assertEquals(24, TissueMetadataParser.table(asset("tissue_evidence_claim_candidates_revised_v1.csv")).rows.size)
        assertEquals(2, TissueMetadataParser.table(asset("tissue_load_band_rubric_revised_v1.csv")).rows.size)
        assertEquals(
            "SOURCE_SPECIFIC_ONLY",
            migrations.single { it.legacyDimension == TissueLoadDimension.COMPRESSION }.migrationDecision.name
        )
        assertTrue(migrations.single { it.legacyDimension == TissueLoadDimension.IMPACT_IMPULSE }.requiredManualReview)
    }

    private fun validate(value: List<TissueLoadDimensionDefinition>) = TissueMultidimensionalValidator.ontology(
        modes, temporalMetrics, measurementMetrics, normalizations, value, migrations, catalog
    )
    private val report by lazy { validate(dimensions) }
    private val modes by lazy { TissueMultidimensionalParser.mechanicalLoadModes(asset("tissue_mechanical_load_mode_registry_v1.csv")) }
    private val temporalMetrics by lazy { TissueMultidimensionalParser.temporalMetrics(asset("tissue_temporal_metric_registry_v1.csv")) }
    private val measurementMetrics by lazy { TissueMultidimensionalParser.measurementMetrics(asset("tissue_measurement_metric_registry_v1.csv")) }
    private val normalizations by lazy { TissueMultidimensionalParser.normalizations(asset("tissue_normalization_registry_v1.csv")) }
    private val dimensions by lazy { TissueMultidimensionalParser.dimensions(asset("tissue_load_dimension_registry_v2.csv")) }
    private val migrations by lazy { TissueMultidimensionalParser.migrations(asset("tissue_load_dimension_migration_v1.csv")) }
    private val catalog by lazy { TissueMetadataParser.catalog(asset("canonical_tissue_catalog_v1.csv")) }
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
