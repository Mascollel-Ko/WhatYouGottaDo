package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.ReadinessStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.readiness.TodayStatusPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

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
            unconfirmedSetCount = 3,
            todayStatus = phaseStatus(
                current = ReadinessStatus.READY,
                projected = ReadinessStatus.CAUTION,
                phase = TodayStatusPhase.REMAINING_PLAN
            )
        )

        assertEquals("현재", summary.primaryPrefix)
        assertEquals(54, summary.primary.score)
        assertEquals("진행 가능", summary.primary.label)
        assertEquals("남은 계획 후 예상", summary.projectionPrefix)
        assertEquals("조절 권장", summary.projection?.label)
        assertEquals("남은 계획 판단", summary.phaseLabel)
        assertTrue(summary.headline?.contains("남은 계획") == true)
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
            unconfirmedSetCount = 0,
            todayStatus = phaseStatus(
                current = ReadinessStatus.CAUTION,
                projected = null,
                phase = TodayStatusPhase.COMPLETED
            )
        )

        assertEquals("현재", summary.primaryPrefix)
        assertEquals(70, summary.primary.score)
        assertNull(summary.projection)
        assertEquals("계획 완료", summary.statusText)
        assertEquals("운동 후 회복 판단", summary.phaseLabel)
        assertEquals("조절 권장", summary.primary.label)
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

    private fun phaseStatus(
        current: ReadinessStatus,
        projected: ReadinessStatus?,
        phase: TodayStatusPhase
    ): PhaseAwareTodayStatus =
        PhaseAwareTodayStatus(
            phase = phase,
            current = readiness(current),
            projected = projected?.let(::readiness),
            plannedSetCount = 6,
            confirmedSetCount = if (phase == TodayStatusPhase.COMPLETED) 6 else 3,
            unconfirmedSetCount = if (phase == TodayStatusPhase.REMAINING_PLAN) 3 else 0,
            phaseLabel = if (phase == TodayStatusPhase.REMAINING_PLAN) "남은 계획 판단" else "운동 후 회복 판단",
            headline = if (phase == TodayStatusPhase.REMAINING_PLAN) {
                "남은 계획을 조절해 진행하세요."
            } else {
                "오늘 운동 후 피로도는 평소 범위입니다."
            },
            detail = "공유된 오늘 상태 문구입니다.",
            actionLabel = "일부 수정 권장",
            keyAxes = emptyList()
        )

    private fun readiness(status: ReadinessStatus): TodayReadinessSummary =
        TodayReadinessSummary(
            status = status,
            headline = "headline",
            shortReason = "reason",
            primaryReasons = emptyList(),
            recommendedModes = emptyList(),
            restrictedModes = emptyList(),
            confidence = AnalysisConfidence.MEDIUM,
            detailSections = emptyList(),
            adaptiveBaselineNotes = emptyList(),
            generatedAt = LocalDateTime.of(2026, 6, 20, 10, 0)
        )
}
