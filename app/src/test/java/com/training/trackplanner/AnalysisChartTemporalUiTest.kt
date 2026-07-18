package com.training.trackplanner

import com.training.trackplanner.analysis.fatigue.FatigueTimePoint
import com.training.trackplanner.analysis.trends.AnalysisChartTemporalPolicy
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.StackedBarGroup
import com.training.trackplanner.analysis.trends.StackedBarSegment
import com.training.trackplanner.analysis.trends.TrendDataPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisChartTemporalUiTest {
    @Test
    fun dailyChartExposesPeriodDatesWeekdayAndValue() {
        val dates = listOf(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 18),
            LocalDate.of(2026, 7, 31)
        )
        val spec = ChartSpec(
            type = ChartType.LINE,
            title = "배드민턴 관련 훈련량(일별)",
            lineSeries = listOf(
                ChartSeries(
                    "훈련량",
                    dates.mapIndexed { index, date -> TrendDataPoint(date, listOf(80.0, 123.0, 95.0)[index]) }
                )
            ),
            timeGranularity = ChartTimeGranularity.DAILY,
            xDomain = dates
        )

        assertEquals("7월 1일~7월 31일", analysisChartPeriodLabel(spec))
        assertTrue(analysisChartContentDescription(spec).contains("7월 18일 토요일, 훈련량 123"))
    }

    @Test
    fun weeklyLineAndStackedChartsExposeTheSameDetailedWeek() {
        val weekStart = LocalDate.of(2026, 6, 29)
        val line = ChartSpec(
            type = ChartType.LINE,
            title = "배드민턴 관련 훈련량(주별)",
            lineSeries = listOf(ChartSeries("훈련량", listOf(TrendDataPoint(weekStart, 85.0)))),
            timeGranularity = ChartTimeGranularity.WEEKLY,
            xDomain = listOf(weekStart)
        )
        val stacked = ChartSpec(
            type = ChartType.STACKED_BAR,
            title = "주별 배드민턴 전이 자극량",
            stackedBars = listOf(
                StackedBarGroup(
                    label = AnalysisChartTemporalPolicy.weekLabel(weekStart).compactLabel,
                    segments = listOf(StackedBarSegment("풋워크", 85.0)),
                    weekStart = weekStart
                )
            ),
            timeGranularity = ChartTimeGranularity.WEEKLY,
            xDomain = listOf(weekStart)
        )
        val detailed = "7월 1주 · 6월 29일~7월 5일"

        assertTrue(analysisChartContentDescription(line).contains(detailed))
        assertTrue(analysisChartContentDescription(stacked).contains(detailed))
        assertEquals(analysisChartPeriodLabel(line), analysisChartPeriodLabel(stacked))
    }

    @Test
    fun accumulatedFatigueChartShowsItsActualRollingRangeWithoutChangingValues() {
        val points = (0L..6L).map { offset ->
            FatigueTimePoint(LocalDate.of(2026, 7, 7).plusDays(offset), 40.0 + offset)
        }
        val spec = accumulatedFatigueChartSpec(points)

        assertEquals("누적 부담 흐름", spec.title)
        assertEquals("7월 7일~7월 13일", analysisChartPeriodLabel(spec))
        assertEquals(points.map(FatigueTimePoint::date), spec.xDomain)
        assertEquals(points.map(FatigueTimePoint::value), spec.lineSeries.single().points.map { it.value })
        assertTrue(analysisChartContentDescription(spec).contains("7월 13일 월요일, 현재 피로도 46"))
    }

    @Test
    fun matchingStrengthWeeksResolveToMatchingLabels() {
        val domain = AnalysisChartTemporalPolicy.weeklyDomain(
            listOf(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 13))
        )
        val muscleLabel = AnalysisChartTemporalPolicy.detailLabel(
            domain[1],
            ChartTimeGranularity.WEEKLY,
            domain
        )
        val repRangeLabel = AnalysisChartTemporalPolicy.detailLabel(
            domain[1],
            ChartTimeGranularity.WEEKLY,
            domain
        )

        assertEquals(muscleLabel, repRangeLabel)
        assertEquals("7월 2주 · 7월 6일~7월 12일", muscleLabel)
    }

    @Test
    fun e1rmAccessibilityUsesWeeklyDateAndSpokenUnit() {
        val weekStart = LocalDate.of(2026, 7, 13)
        val spec = ChartSpec(
            type = ChartType.LINE,
            title = "메인 운동 e1RM",
            lineSeries = listOf(ChartSeries("스쿼트 e1RM", listOf(TrendDataPoint(weekStart, 165.0)))),
            timeGranularity = ChartTimeGranularity.WEEKLY,
            xDomain = listOf(weekStart),
            valueUnit = "kg"
        )

        assertTrue(
            analysisChartContentDescription(spec)
                .contains("7월 3주 · 7월 13일~7월 19일, 스쿼트 e1RM 165킬로그램")
        )
    }
}
