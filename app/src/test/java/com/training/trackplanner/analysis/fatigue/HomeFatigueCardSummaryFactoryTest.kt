package com.training.trackplanner.analysis.fatigue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HomeFatigueCardSummaryFactoryTest {
    @Test
    fun noConfirmedSetsShowsPreWorkoutAndFullPlanProjection() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(42),
            projected = state(61),
            confirmedSetCount = 0,
            unconfirmedSetCount = 6
        )

        assertEquals("운동 전", summary.primaryPrefix)
        assertEquals(42, summary.primary.score)
        assertEquals("계획 후 예상", summary.projectionPrefix)
        assertEquals(61, summary.projection?.score)
        assertNull(summary.statusText)
    }

    @Test
    fun confirmedSetsShowCurrentAndRemainingPlanProjection() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(54),
            projected = state(69, localScore = 82),
            confirmedSetCount = 3,
            unconfirmedSetCount = 3
        )

        assertEquals("현재", summary.primaryPrefix)
        assertEquals(54, summary.primary.score)
        assertEquals("남은 계획 후 예상", summary.projectionPrefix)
        assertEquals("국소 근육 피로 주의", summary.projection?.label)
    }

    @Test
    fun noPlanShowsPreWorkoutWithoutProjection() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(42),
            projected = null,
            confirmedSetCount = 0,
            unconfirmedSetCount = 0
        )

        assertEquals("운동 전", summary.primaryPrefix)
        assertNull(summary.projection)
        assertEquals("오늘 계획 없음", summary.statusText)
    }

    @Test
    fun completedPlanShowsCurrentWithoutRemainingProjection() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(70, systemicScore = 84),
            projected = null,
            confirmedSetCount = 6,
            unconfirmedSetCount = 0
        )

        assertEquals("현재", summary.primaryPrefix)
        assertEquals(70, summary.primary.score)
        assertNull(summary.projection)
        assertEquals("계획 완료", summary.statusText)
    }

    private fun state(
        ofi: Int,
        systemicScore: Int = ofi,
        localScore: Int = ofi
    ): DailyFatigueState = DailyFatigueState(
        date = LocalDate.of(2026, 6, 20),
        neuromuscularFatigue = 0.0,
        systemicMuscularFatigue = 0.0,
        localMuscularFatigue = 0.0,
        jointTendonImpactFatigue = 0.0,
        movementFocusFatigue = 0.0,
        recoveryPressure = 0.0,
        neuromuscularScore = ofi,
        systemicMuscularScore = systemicScore,
        localMuscularScore = localScore,
        jointTendonImpactScore = ofi,
        movementFocusScore = ofi,
        recoveryPressureScore = ofi,
        overallFatigueIndex = ofi,
        readinessLabel = FatigueLabelResolver.label(ofi),
        cautionReasons = emptyList(),
        confidence = FatigueConfidence.MEDIUM
    )
}
