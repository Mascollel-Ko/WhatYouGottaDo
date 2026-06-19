package com.training.trackplanner.analysis.fatigue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class HomeTodaySummaryStateTest {
    @Test
    fun homeSummaryCarriesCountsFatigueAndSevenDaySeries() {
        val today = LocalDate.of(2026, 6, 19)
        val series = (6 downTo 0).map { offset ->
            MiniTrendPoint(today.minusDays(offset.toLong()), (60 - offset).toDouble())
        }
        val state = HomeTodaySummaryState(
            date = today,
            plannedExerciseCount = 3,
            confirmedSetCount = 8,
            unconfirmedSetCount = 2,
            fatigueLabel = FatigueReadinessLabel.NORMAL,
            fatigueScore = 60,
            fatigueHeadline = FatigueLabelResolver.headline(FatigueReadinessLabel.NORMAL),
            fatigueCard = HomeFatigueCardSummary(
                primaryPrefix = "현재",
                primary = HomeFatigueReading(60, "적정")
            ),
            cautionReasons = emptyList(),
            recentTrainingLoadSeries = series,
            recentFatigueSeries = series,
            confidence = FatigueConfidence.MEDIUM
        )

        assertEquals(7, state.recentTrainingLoadSeries.size)
        assertEquals(7, state.recentFatigueSeries.size)
        assertEquals(FatigueReadinessLabel.NORMAL, state.fatigueLabel)
        assertFalse(FatigueLabelResolver.shouldRecommendRest(state.fatigueScore!!, List(6) { 60 }, false))
    }
}
