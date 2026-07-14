package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC31OntologyTest {
    @Test
    fun physicalModesAndContextRegistriesAreClosedAndSeparate() {
        assertEquals(14, modes.size)
        assertFalse(modes.any { it.mechanicalLoadMode.name in setOf("IMPACT_STABILIZATION", "END_RANGE_STRESS", "ENERGY_STORAGE_RELEASE") })
        assertEquals(TissueEventContext.entries.map { it.name }.toSet(), events.map { it.id }.toSet())
        assertEquals(TissueMovementPhase.entries.map { it.name }.toSet(), phases.map { it.id }.toSet())
        assertEquals(TissuePositionContext.entries.map { it.name }.toSet(), positions.map { it.id }.toSet())
        assertEquals(TissueFunctionalDemand.entries.map { it.name }.toSet(), demands.map { it.id }.toSet())
        assertEquals(TissueResponseMetric.entries.map { it.name }.toSet(), responses.map { it.id }.toSet())
        assertTrue(report.errors.toString(), report.isValid)
    }

    @Test
    fun ligamentTensionIsExplicitAndContextOnlyDimensionsAreBlocked() {
        val acl = dimensions.single { it.dimensionId == "ACL_TENSION_PEAK" }
        assertEquals(TissueC31MechanicalLoadMode.TENSION, acl.mechanicalLoadMode)
        assertEquals(setOf(TissueMeasurementMetric.MEASURED_LIGAMENT_STRAIN), acl.allowedMeasurementMetrics)
        assertEquals(39, dimensions.size)

        val corrections = TissueMetadataParser.table(asset("tissue_c3_1_dimension_correction_v1.csv")).rows
        assertEquals(42, corrections.size)
        assertEquals(3, corrections.count { it["correctionDecision"] == "REMOVED_UNSUPPORTED_INTERPRETATION" })
        assertTrue(corrections.any { it["oldDimensionId"] == "ACH_ENERGY_PEAK" && it["newMechanicalLoadMode"] == "TENSION" })
    }

    @Test
    fun unvalidatedProxyCannotBecomeRubricEligible() {
        val invalid = dimensions.first().copy(
            dimensionId = "INVALID_PROXY", allowedEvidenceRelations = setOf(TissueEvidenceRelation.UNVALIDATED_PROXY), rubricEligible = true
        )
        assertFalse(validate(dimensions + invalid).isValid)
    }

    private fun validate(value: List<TissueC31LoadDimensionDefinition>) = TissueC31Validator.ontology(
        modes, events, phases, positions, demands, responses, relations, rubrics, loads, measurements, value, catalog
    )
    private val report by lazy { validate(dimensions) }
    private val modes by lazy { TissueC31Parser.mechanicalLoadModes(asset("tissue_mechanical_load_mode_registry_c3_1_v1.csv")) }
    private val events by lazy { registry("tissue_event_context_registry_v1.csv") }
    private val phases by lazy { registry("tissue_movement_phase_registry_v1.csv") }
    private val positions by lazy { registry("tissue_position_context_registry_v1.csv") }
    private val demands by lazy { registry("tissue_functional_demand_registry_v1.csv") }
    private val responses by lazy { registry("tissue_response_metric_registry_v1.csv") }
    private val relations by lazy { registry("tissue_evidence_relation_registry_v1.csv") }
    private val rubrics by lazy { registry("tissue_rubric_kind_registry_v1.csv") }
    private val loads by lazy { registry("tissue_external_load_representation_registry_v1.csv") }
    private val measurements by lazy { TissueC31Parser.measurementMetrics(asset("tissue_measurement_metric_registry_c3_1_v1.csv")) }
    private val dimensions by lazy { TissueC31Parser.dimensions(asset("tissue_load_dimension_registry_c3_1_v1.csv")) }
    private val catalog by lazy { TissueMetadataParser.catalog(asset("canonical_tissue_catalog_v1.csv")) }
    private fun registry(name: String) = TissueC31Parser.registry(asset(name))
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"), File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
