package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueC4AMtcFoundationTest {
    @Test
    fun lowerLimbComplexesAndEveryTargetDeclareIndependentMtcAxes() {
        assertEquals(9, complexes.size)
        assertEquals(46, complexes.sumOf { it.componentIds.size })
        assertEquals(16, rules.groupBy { it.targetType to it.targetId }.size)
        assertEquals(48, rules.size)
        assertTrue(report.errors.toString(), report.isValid)
        assertTrue(complexes.all { it.outputPolicy == "SEPARATE_WHEN_DIRECT_EVIDENCE" })
    }

    @Test
    fun stabilizationProfilesRemainParallelToMechanicalTissueLoad() {
        assertEquals(
            setOf("HAMSTRING_DYNAMIC_STABILIZATION", "PERONEAL_DYNAMIC_STABILIZATION", "POSTERIOR_TIBIAL_DYNAMIC_STABILIZATION"),
            stabilization.map { it.profileId }.toSet()
        )
        assertTrue(stabilization.all { it.separateFromMechanicalLoad })
        assertEquals(9, rules.count { it.targetType == TissueMtcTargetType.DYNAMIC_STABILIZATION })
    }

    @Test
    fun invalidAxisMetricCombinationFailsClosed() {
        val magnitude = rules.first { it.axis == TissueMtcAxis.M }
        val invalid = magnitude.copy(axisMetricRuleId = "INVALID", primaryMetricTypes = setOf("T_LOADING_RATE"))
        val result = TissueMtcValidator.foundation(complexes, rules + invalid, stabilization)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "do not match axis" in it })
    }

    @Test
    fun ontologyIncludesAverageMetricsIntersegmentalForceAndMechanismRelations() {
        assertEquals(TissueMtcTemporalMetric.entries.toSet(), TissueMtcParser.temporalMetrics(asset("tissue_mtc_temporal_metric_registry_v1.csv")).toSet())
        assertEquals(TissueMtcMeasurementMetric.entries.toSet(), TissueMtcParser.measurementMetrics(asset("tissue_mtc_measurement_metric_registry_v1.csv")).toSet())
        assertEquals(TissueMtcEvidenceRelation.entries.toSet(), TissueMtcParser.evidenceRelations(asset("tissue_mtc_evidence_relation_registry_v1.csv")).toSet())
    }

    private val complexes by lazy { TissueMtcParser.functionalComplexes(asset("tissue_functional_complex_registry_v1.csv")) }
    private val rules by lazy { TissueMtcParser.axisMetricRules(asset("tissue_mtc_axis_metric_registry_v1.csv")) }
    private val stabilization by lazy { TissueMtcParser.dynamicStabilizationProfiles(asset("tissue_dynamic_stabilization_profile_registry_v1.csv")) }
    private val report by lazy { TissueMtcValidator.foundation(complexes, rules, stabilization) }
    private fun asset(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
