package com.training.trackplanner.analysis.trends

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisChartTemporalPolicyTest {
    @Test
    fun thursdayMonthOwnsFourDayBoundaryWeek() {
        val label = AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2027, 6, 28))

        assertEquals(7, label.owningMonth)
        assertEquals("7월 1주", label.compactLabel)
        assertEquals(LocalDate.of(2027, 7, 4), label.weekEnd)
    }

    @Test
    fun fiveJulyDatesOwnCrossMonthWeek() {
        val label = AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2026, 6, 29))

        assertEquals(7, label.owningMonth)
        assertEquals("7월 1주", label.compactLabel)
        assertEquals("7월 1주 · 6월 29일~7월 5일", label.detailedLabel)
    }

    @Test
    fun threeJulyDatesLeaveWeekWithJune() {
        val label = AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2022, 6, 27))

        assertEquals(6, label.owningMonth)
        assertEquals(6, label.weekStart.plusDays(3).monthValue)
    }

    @Test
    fun ordinalCountsOwnedMondayWeeksIncludingPreviousMonthStart() {
        assertEquals("7월 1주", AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2026, 6, 29)).compactLabel)
        assertEquals("7월 2주", AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2026, 7, 6)).compactLabel)
        assertEquals("7월 5주", AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2026, 7, 27)).compactLabel)
    }

    @Test
    fun yearBoundaryLabelsRemainUnambiguous() {
        val december = AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2026, 12, 28), includeYear = true)
        val january = AnalysisChartTemporalPolicy.weekLabel(LocalDate.of(2027, 1, 4), includeYear = true)

        assertEquals("2026년 12월 5주", december.compactLabel)
        assertEquals("2026년 12월 5주 · 2026년 12월 28일~2027년 1월 3일", december.detailedLabel)
        assertEquals("2027년 1월 1주", january.compactLabel)
    }

    @Test
    fun weeklyDomainUsesCalendarWeeksAcrossDst() {
        val domain = AnalysisChartTemporalPolicy.weeklyDomain(
            listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 16))
        )
        val newYork = ZoneId.of("America/New_York")
        val elapsedHours = java.time.Duration.between(
            domain.first().atStartOfDay(newYork),
            domain[1].atStartOfDay(newYork)
        ).toHours()

        assertEquals(
            listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16)),
            domain
        )
        assertEquals(167L, elapsedHours)
    }

    @Test
    fun weeklyDensityShowsAllThroughEightAndKeepsBoundariesAfterEight() {
        val eight = (0L..7L).map { LocalDate.of(2026, 6, 29).plusWeeks(it) }
        assertEquals(eight.toSet(), AnalysisChartTemporalPolicy.axisLabelDates(eight, ChartTimeGranularity.WEEKLY))

        val twelve = (0L..11L).map { LocalDate.of(2026, 6, 29).plusWeeks(it) }
        val selected = AnalysisChartTemporalPolicy.axisLabelDates(twelve, ChartTimeGranularity.WEEKLY)
        assertTrue(twelve.first() in selected)
        assertTrue(twelve.last() in selected)
        assertTrue(LocalDate.of(2026, 8, 3) in selected)
        assertFalse(selected.size == twelve.size)
        assertEquals(12, AnalysisChartTemporalPolicy.weeklyDomain(twelve).size)
    }

    @Test
    fun dailyDensityKeepsFirstLastAndMonthChangeWithoutDroppingDomainPoints() {
        val dates = (0L..39L).map { LocalDate.of(2026, 6, 20).plusDays(it) }
        val selected = AnalysisChartTemporalPolicy.axisLabelDates(dates, ChartTimeGranularity.DAILY)

        assertTrue(dates.first() in selected)
        assertTrue(dates.last() in selected)
        assertTrue(LocalDate.of(2026, 7, 1) in selected)
        assertEquals(40, AnalysisChartTemporalPolicy.dailyDomain(dates).size)
    }
}
