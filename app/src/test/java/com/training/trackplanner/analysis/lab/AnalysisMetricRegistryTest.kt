package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisMetricRegistryTest {
    private val explorerMetrics = setOf(
        TrendMetricId.STRENGTH_PERFORMANCE,
        TrendMetricId.STRENGTH_INTENSITY,
        TrendMetricId.STRENGTH_VOLUME,
        TrendMetricId.STRENGTH_EFFICIENCY,
        TrendMetricId.BADMINTON_TRAINING,
        TrendMetricId.COURT_VOLUME,
        TrendMetricId.FOOTWORK_REACTIVE,
        TrendMetricId.BADMINTON_SUPPORT,
        TrendMetricId.FATIGUE_COMPOSITE,
        TrendMetricId.SYSTEMIC_FATIGUE,
        TrendMetricId.STRENGTH_FATIGUE,
        TrendMetricId.BADMINTON_FATIGUE,
        TrendMetricId.LOCAL_BODY_PART_FATIGUE,
        TrendMetricId.RECOVERY_PERFORMANCE_PENALTY,
        TrendMetricId.SLEEP_HOURS,
        TrendMetricId.OVERALL_FATIGUE_CHECKIN,
        TrendMetricId.LOWER_BODY_FATIGUE_CHECKIN,
        TrendMetricId.JOINT_TENDON_DISCOMFORT_CHECKIN,
        TrendMetricId.FOCUS_MOTIVATION_CHECKIN,
        TrendMetricId.RECOVERY_CHECKIN_COMPOSITE,
        TrendMetricId.STRENGTH_DELTA_NEXT,
        TrendMetricId.FATIGUE_DELTA_NEXT
    )

    @Test
    fun registryContainsEveryRelationshipExplorerMetricExactlyOnce() {
        assertEquals(explorerMetrics, AnalysisMetricRegistry.descriptors.map { it.id }.toSet())
        assertEquals(explorerMetrics.size, AnalysisMetricRegistry.descriptors.size)
        assertTrue(AnalysisMetricRegistry.descriptors.all { it.supportsScatter })
    }

    @Test
    fun scatterSelectorCanFilterToMetricsWithAvailableValues() {
        val date = LocalDate.of(2026, 1, 5)
        val series = mapOf(
            TrendMetricId.STRENGTH_VOLUME to listOf(TrendDataPoint(date, 100.0)),
            TrendMetricId.FATIGUE_COMPOSITE to listOf(TrendDataPoint(date, null)),
            TrendMetricId.COURT_VOLUME to emptyList()
        )

        val available = AnalysisMetricRegistry.scatterMetrics(series)

        assertEquals(listOf(TrendMetricId.STRENGTH_VOLUME), available.map { it.id })
        assertTrue(available.none { it.id == TrendMetricId.SLEEP_HOURS })
    }

    @Test
    fun registryDescriptorsAreReadyForFutureTimeSeriesAndMultivariateUse() {
        assertTrue(AnalysisMetricRegistry.descriptors.all { it.supportsTimeSeries })
        assertTrue(AnalysisMetricRegistry.descriptors.all { it.supportsMultivariate })
        assertTrue(AnalysisMetricRegistry.descriptors.all { it.description.isNotBlank() })
    }

    @Test
    fun checkInMetricDirectionsMatchTheirMeaning() {
        assertEquals(true, AnalysisMetricRegistry.descriptor(TrendMetricId.FOCUS_MOTIVATION_CHECKIN)?.higherIsBetter)
        assertEquals(false, AnalysisMetricRegistry.descriptor(TrendMetricId.OVERALL_FATIGUE_CHECKIN)?.higherIsBetter)
        assertEquals(false, AnalysisMetricRegistry.descriptor(TrendMetricId.JOINT_TENDON_DISCOMFORT_CHECKIN)?.higherIsBetter)
    }

    @Test
    fun metricCategoryDisplayLabelsAreKoreanNotRawEnumNames() {
        assertEquals("근력", AnalysisMetricCategory.STRENGTH.displayLabelKo())
        assertEquals("배드민턴", AnalysisMetricCategory.BADMINTON.displayLabelKo())
        assertEquals("피로", AnalysisMetricCategory.FATIGUE.displayLabelKo())
        assertEquals("전이", AnalysisMetricCategory.TRANSFER.displayLabelKo())
        assertEquals("회복/컨디션", AnalysisMetricCategory.RECOVERY.displayLabelKo())
        assertEquals("훈련량", AnalysisMetricCategory.VOLUME.displayLabelKo())
        assertEquals("파생 지표", AnalysisMetricCategory.DERIVED.displayLabelKo())
    }
}
