package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScatterRelationshipAnalyzerTest {
    private val analyzer = ScatterRelationshipAnalyzer()
    private val monday = LocalDate.of(2026, 1, 5)

    @Test
    fun pairsOnlyMatchingWeeksAndSortsAscending() {
        val x = listOf(
            TrendDataPoint(monday.plusWeeks(2), 30.0),
            TrendDataPoint(monday, 10.0),
            TrendDataPoint(monday.plusWeeks(1), 20.0)
        )
        val y = listOf(
            TrendDataPoint(monday.plusWeeks(3), 400.0),
            TrendDataPoint(monday.plusWeeks(2), 300.0),
            TrendDataPoint(monday, 100.0)
        )

        val result = analyzer.analyze(
            TrendMetricId.STRENGTH_VOLUME,
            TrendMetricId.FATIGUE_COMPOSITE,
            mapOf(TrendMetricId.STRENGTH_VOLUME to x, TrendMetricId.FATIGUE_COMPOSITE to y)
        )

        assertEquals(listOf(monday.toString(), monday.plusWeeks(2).toString()), result.dataPoints.map { it.label })
        assertEquals(listOf(10.0, 30.0), result.dataPoints.map { it.x })
        assertEquals(listOf(100.0, 300.0), result.dataPoints.map { it.y })
    }

    @Test
    fun excludesNullValuesEvenOnCommonWeeks() {
        val result = analyzer.analyze(
            TrendMetricId.STRENGTH_VOLUME,
            TrendMetricId.FATIGUE_COMPOSITE,
            mapOf(
                TrendMetricId.STRENGTH_VOLUME to listOf(
                    TrendDataPoint(monday, null),
                    TrendDataPoint(monday.plusWeeks(1), 20.0)
                ),
                TrendMetricId.FATIGUE_COMPOSITE to listOf(
                    TrendDataPoint(monday, 100.0),
                    TrendDataPoint(monday.plusWeeks(1), 200.0)
                )
            )
        )

        assertEquals(1, result.dataPoints.size)
        assertEquals(monday.plusWeeks(1).toString(), result.dataPoints.single().label)
    }

    @Test
    fun sameMetricReturnsInvalidEmptyResult() {
        val result = analyzer.analyze(TrendMetricId.COURT_VOLUME, TrendMetricId.COURT_VOLUME, emptyMap())

        assertTrue(result.dataPoints.isEmpty())
        assertNull(result.correlation)
        assertEquals(AnalysisConfidence.LOW, result.confidence)
        assertTrue(result.interpretation.contains("서로 다른"))
    }

    @Test
    fun fewerThanEightPointsIsInsufficientAndLowConfidence() {
        val result = analyzeLinearSeries(7)

        assertNull(result.correlation)
        assertEquals(AnalysisConfidence.LOW, result.confidence)
        assertEquals("기록 부족", result.dataSufficiency)
        assertTrue(result.interpretation.contains("기록이 부족"))
    }

    @Test
    fun eightToNineteenPointsRemainReferenceOnly() {
        val small = analyzeLinearSeries(8)
        val lowModerate = analyzeLinearSeries(12)

        assertEquals(AnalysisConfidence.MEDIUM_LOW, small.confidence)
        assertEquals(AnalysisConfidence.MEDIUM_LOW, lowModerate.confidence)
        assertTrue(small.dataSufficiency.contains("소표본"))
        assertTrue(lowModerate.dataSufficiency.contains("우연 상관"))
    }

    @Test
    fun twentyPointsMayReachModerateButRemainNonCausal() {
        val result = analyzeLinearSeries(20)

        assertEquals(AnalysisConfidence.MEDIUM, result.confidence)
        assertTrue(result.interpretation.contains("패턴"))
        assertTrue(!result.interpretation.contains("원인"))
    }

    private fun analyzeLinearSeries(count: Int): ScatterAnalysisResult {
        val x = (0 until count).map { index -> TrendDataPoint(monday.plusWeeks(index.toLong()), index.toDouble()) }
        val y = (0 until count).map { index -> TrendDataPoint(monday.plusWeeks(index.toLong()), index * 2.0 + 1.0) }
        return analyzer.analyze(
            TrendMetricId.STRENGTH_VOLUME,
            TrendMetricId.FATIGUE_COMPOSITE,
            mapOf(TrendMetricId.STRENGTH_VOLUME to x, TrendMetricId.FATIGUE_COMPOSITE to y)
        )
    }
}
