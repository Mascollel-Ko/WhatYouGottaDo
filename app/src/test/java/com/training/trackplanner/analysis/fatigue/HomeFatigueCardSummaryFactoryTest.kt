package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.FatigueAvailability
import com.training.trackplanner.analysis.readiness.FatigueDetailSection
import com.training.trackplanner.analysis.readiness.FatigueDetailType
import com.training.trackplanner.analysis.readiness.FatigueLevel
import com.training.trackplanner.analysis.readiness.FatiguePresentationSnapshot
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.ReadinessStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.readiness.TodayStatusPhase
import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
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
        assertEquals("계획 완료 시", summary.projectionPrefix)
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
        assertEquals("보통", summary.primary.label)
        assertEquals("계획 완료 시", summary.projectionPrefix)
        assertEquals("주의", summary.projection?.label)
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
        assertNull(summary.statusText)
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
        assertNull(summary.statusText)
        assertEquals("현재 회복 판단", summary.phaseLabel)
        assertEquals("주의", summary.primary.label)
    }

    @Test
    fun completedPlanIgnoresProjectionEvenIfCallerPassesProjectedState() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(54),
            projected = state(80),
            confirmedSetCount = 6,
            unconfirmedSetCount = 0,
            todayStatus = phaseStatus(
                current = ReadinessStatus.CAUTION,
                projected = null,
                phase = TodayStatusPhase.COMPLETED
            )
        )

        assertEquals("현재", summary.primaryPrefix)
        assertNull(summary.projection)
        assertNull(summary.projectionPrefix)
        assertNull(summary.statusText)
        assertTrue(summary.detail?.contains("남은 계획") != true)
    }

    @Test
    fun remainingPlanShowsProjectionEvenWhenCurrentAndProjectedLabelsMatch() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(70),
            current = state(70),
            projected = state(72),
            confirmedSetCount = 3,
            unconfirmedSetCount = 3,
            todayStatus = phaseStatus(
                current = ReadinessStatus.FATIGUED,
                projected = ReadinessStatus.FATIGUED,
                phase = TodayStatusPhase.REMAINING_PLAN
            )
        )

        assertEquals("피로 누적", summary.primary.label)
        assertEquals("계획 완료 시", summary.projectionPrefix)
        assertEquals("피로 누적", summary.projection?.label)
    }

    @Test
    fun remainingPlanKeepsSingleAxisCurrentLabelWhenProjectionAccumulates() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(54),
            projected = state(72),
            confirmedSetCount = 3,
            unconfirmedSetCount = 3,
            todayStatus = phaseStatus(
                current = ReadinessStatus.FATIGUED,
                projected = ReadinessStatus.FATIGUED,
                phase = TodayStatusPhase.REMAINING_PLAN,
                currentSections = listOf(section(FatigueDetailType.BADMINTON_COURT, "동작 집중", FatigueLevel.HIGH)),
                projectedSections = listOf(
                    section(FatigueDetailType.BADMINTON_COURT, "동작 집중", FatigueLevel.HIGH),
                    section(FatigueDetailType.LOCAL_BODY_PART, "국소 근육", FatigueLevel.HIGH)
                )
            )
        )

        assertEquals("동작 집중 부담 높음", summary.primary.label)
        assertEquals("계획 완료 시", summary.projectionPrefix)
        assertEquals("피로 누적", summary.projection?.label)
        assertEquals("일부 수정 권장", summary.actionLabel)
    }

    @Test
    fun remainingPlanShowsNormalCurrentAndFatiguedProjection() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(42),
            projected = state(72),
            confirmedSetCount = 1,
            unconfirmedSetCount = 3,
            todayStatus = phaseStatus(
                current = ReadinessStatus.READY,
                projected = ReadinessStatus.FATIGUED,
                phase = TodayStatusPhase.REMAINING_PLAN
            )
        )

        assertEquals("보통", summary.primary.label)
        assertEquals("피로 누적", summary.projection?.label)
    }

    @Test
    fun remainingPlanShowsNormalCurrentAndDeepenedProjection() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(42),
            current = state(42),
            projected = state(88),
            confirmedSetCount = 1,
            unconfirmedSetCount = 3,
            todayStatus = phaseStatus(
                current = ReadinessStatus.READY,
                projected = ReadinessStatus.LIMITED,
                phase = TodayStatusPhase.REMAINING_PLAN
            )
        )

        assertEquals("보통", summary.primary.label)
        assertEquals("피로 심화", summary.projection?.label)
    }

    @Test
    fun primaryReadingUsesOfiWhenReadinessPresentationHasDifferentScore() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(20),
            current = state(60),
            projected = null,
            confirmedSetCount = 1,
            unconfirmedSetCount = 0,
            todayStatus = phaseStatus(
                current = ReadinessStatus.READY,
                projected = null,
                phase = TodayStatusPhase.COMPLETED,
                currentPresentation = presentation(82)
            )
        )

        assertEquals(60, summary.primary.score)
        assertEquals("보통", summary.primary.label)
    }

    @Test
    fun nullReadinessFatiguePresentationFallsBackToLegacyState() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(20),
            current = state(54),
            projected = null,
            confirmedSetCount = 1,
            unconfirmedSetCount = 0,
            todayStatus = phaseStatus(
                current = ReadinessStatus.READY,
                projected = null,
                phase = TodayStatusPhase.COMPLETED
            )
        )

        assertEquals(54, summary.primary.score)
        assertEquals("보통", summary.primary.label)
    }

    @Test
    fun readinessPresentationOverallScoreDoesNotReplaceAxisBasedLabel() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(20),
            current = state(30),
            projected = null,
            confirmedSetCount = 1,
            unconfirmedSetCount = 0,
            todayStatus = phaseStatus(
                current = ReadinessStatus.FATIGUED,
                projected = null,
                phase = TodayStatusPhase.COMPLETED,
                currentPresentation = presentation(140)
            )
        )

        assertEquals(30, summary.primary.score)
        assertEquals("보통", summary.primary.label)
    }

    @Test
    fun projectedReadingUsesProjectedOfiAndAxisBasedLabel() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(20),
            current = state(30),
            projected = state(72),
            confirmedSetCount = 0,
            unconfirmedSetCount = 3,
            todayStatus = phaseStatus(
                current = ReadinessStatus.READY,
                projected = ReadinessStatus.LIMITED,
                phase = TodayStatusPhase.REMAINING_PLAN,
                projectedPresentation = presentation(91)
            )
        )

        assertEquals(72, summary.projection?.score)
        assertEquals("보통", summary.projection?.label)
    }

    @Test
    fun cardPrimaryScoreMatchesTodayGraphOfiPoint() {
        val current = state(60)
        val todayPoint = MiniTrendPoint(current.date, current.overallFatigueIndex.toDouble())
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(20),
            current = current,
            projected = null,
            confirmedSetCount = 1,
            unconfirmedSetCount = 0,
            todayStatus = phaseStatus(
                current = ReadinessStatus.CAUTION,
                projected = null,
                phase = TodayStatusPhase.COMPLETED,
                currentPresentation = presentation(80)
            )
        )

        assertEquals(todayPoint.value.toInt(), summary.primary.score)
        assertTrue(summary.primary.label.isNotBlank())
    }

    @Test
    fun readinessGuidanceTextIsPreservedWhenScoresStayOnOfi() {
        val summary = HomeFatigueCardSummaryFactory.create(
            preWorkout = state(20),
            current = state(60),
            projected = state(72),
            confirmedSetCount = 3,
            unconfirmedSetCount = 3,
            todayStatus = phaseStatus(
                current = ReadinessStatus.CAUTION,
                projected = ReadinessStatus.LIMITED,
                phase = TodayStatusPhase.REMAINING_PLAN,
                currentPresentation = presentation(80),
                projectedPresentation = presentation(90)
            )
        )

        assertEquals(60, summary.primary.score)
        assertEquals(72, summary.projection?.score)
        assertTrue(summary.primary.label.isNotBlank())
        assertTrue(summary.projection?.label?.isNotBlank() == true)
        assertTrue(summary.phaseLabel?.isNotBlank() == true)
        assertTrue(summary.headline?.isNotBlank() == true)
        assertTrue(summary.detail?.isNotBlank() == true)
        assertTrue(summary.actionLabel?.isNotBlank() == true)
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
        phase: TodayStatusPhase,
        currentPresentation: FatiguePresentationSnapshot? = null,
        projectedPresentation: FatiguePresentationSnapshot? = null,
        currentSections: List<FatigueDetailSection> = emptyList(),
        projectedSections: List<FatigueDetailSection> = emptyList()
    ): PhaseAwareTodayStatus =
        PhaseAwareTodayStatus(
            phase = phase,
            current = readiness(current, currentPresentation, currentSections),
            projected = projected?.let { status -> readiness(status, projectedPresentation, projectedSections) },
            plannedSetCount = 6,
            confirmedSetCount = if (phase == TodayStatusPhase.COMPLETED) 6 else 3,
            unconfirmedSetCount = if (phase == TodayStatusPhase.REMAINING_PLAN) 3 else 0,
            phaseLabel = if (phase == TodayStatusPhase.REMAINING_PLAN) "남은 계획 판단" else "현재 회복 판단",
            headline = if (phase == TodayStatusPhase.REMAINING_PLAN) {
                "남은 계획을 조절해 진행하세요."
            } else {
                "오늘 운동 후 피로도는 평소 범위입니다."
            },
            detail = "공유된 오늘 상태 문구입니다.",
            actionLabel = "일부 수정 권장",
            keyAxes = emptyList()
        )

    private fun readiness(
        status: ReadinessStatus,
        fatiguePresentation: FatiguePresentationSnapshot? = null,
        sections: List<FatigueDetailSection> = emptyList()
    ): TodayReadinessSummary =
        TodayReadinessSummary(
            status = status,
            headline = "headline",
            shortReason = "reason",
            primaryReasons = emptyList(),
            recommendedModes = emptyList(),
            restrictedModes = emptyList(),
            confidence = AnalysisConfidence.MEDIUM,
            detailSections = sections,
            adaptiveBaselineNotes = emptyList(),
            generatedAt = LocalDateTime.of(2026, 6, 20, 10, 0),
            fatiguePresentation = fatiguePresentation
        )

    private fun section(
        type: FatigueDetailType,
        title: String,
        level: FatigueLevel
    ): FatigueDetailSection =
        FatigueDetailSection(
            type = type,
            title = title,
            level = level,
            summary = "",
            metrics = emptyList(),
            relatedCategories = emptyList(),
            restrictedTargets = emptyList()
        )

    private fun presentation(score: Int): FatiguePresentationSnapshot =
        FatiguePresentationSnapshot(
            overallScore = score,
            neuralScore = 0,
            localMuscleScore = 0,
            jointTendonScore = 0,
            systemicScore = 0,
            focusScore = 0,
            highCategories = emptyList(),
            highBodyParts = emptyList(),
            gate = TrainingGateSnapshot(
                overallScore = score,
                heavyLowerRestricted = false,
                highImpactRestricted = false,
                codReactiveRestricted = false,
                upperPushRestricted = false,
                overheadRestricted = false,
                gripForearmRestricted = false,
                volumeFactor = 1.0,
                rpeCap = null,
                reasons = emptyList()
            ),
            reduceToday = emptyList(),
            availableToday = listOf(
                FatigueAvailability(
                    key = "low_risk_skill",
                    label = "Low-risk skill work",
                    reason = "Low burden"
                )
            ),
            reasons = emptyList()
        )
}
