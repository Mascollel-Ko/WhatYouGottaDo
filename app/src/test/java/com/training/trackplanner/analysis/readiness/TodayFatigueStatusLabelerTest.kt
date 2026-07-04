package com.training.trackplanner.analysis.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime

class TodayFatigueStatusLabelerTest {
    @Test
    fun movementFocusOnlyHighUsesAxisSpecificCurrentLabel() {
        val summary = summary(section(FatigueDetailType.BADMINTON_COURT, "동작 집중", FatigueLevel.HIGH))

        val label = TodayFatigueStatusLabeler.label(summary)

        assertEquals("동작 집중 부담 높음", label)
        assertNotEquals("피로 누적", label)
    }

    @Test
    fun projectedAccumulationDoesNotOverwriteSingleAxisCurrentLabel() {
        val current = summary(section(FatigueDetailType.BADMINTON_COURT, "동작 집중", FatigueLevel.HIGH))
        val projected = summary(
            section(FatigueDetailType.BADMINTON_COURT, "동작 집중", FatigueLevel.HIGH),
            section(FatigueDetailType.LOCAL_BODY_PART, "국소 근육", FatigueLevel.HIGH)
        )

        assertEquals("동작 집중 부담 높음", TodayFatigueStatusLabeler.label(current))
        assertEquals("피로 누적", TodayFatigueStatusLabeler.label(projected))
    }

    @Test
    fun twoCurrentHighAxesUseAccumulationLabel() {
        val summary = summary(
            section(FatigueDetailType.BADMINTON_COURT, "동작 집중", FatigueLevel.HIGH),
            section(FatigueDetailType.LOCAL_BODY_PART, "국소 근육", FatigueLevel.HIGH)
        )

        assertEquals("피로 누적", TodayFatigueStatusLabeler.label(summary))
    }

    @Test
    fun systemicPlusAnotherHighAxisUsesDeepeningLabel() {
        val summary = summary(
            section(FatigueDetailType.SYSTEMIC, "전신 피로", FatigueLevel.HIGH),
            section(FatigueDetailType.LOCAL_BODY_PART, "국소 근육", FatigueLevel.HIGH)
        )

        assertEquals("피로 심화", TodayFatigueStatusLabeler.label(summary))
    }

    @Test
    fun presentationGroupsNeuralSubtypesAsOneCurrentAxis() {
        val summary = summary(
            section(FatigueDetailType.NEURAL_HEAVY, "고중량/힘 기반 신경계 피로", FatigueLevel.HIGH),
            section(FatigueDetailType.NEURAL_SPEED, "고속/반응 신경계 피로", FatigueLevel.HIGH),
            presentation = presentation(neural = 75)
        )

        assertEquals("신경계 피로 높음", TodayFatigueStatusLabeler.label(summary))
        assertEquals("현재 높음", TodayFatigueStatusLabeler.axisStates(summary).first { it.label == "신경계" }.displayLabel)
    }

    @Test
    fun presentationIsTheCurrentAxisSourceForLabelAndAxisStatus() {
        val summary = summary(
            section(FatigueDetailType.NEURAL_HEAVY, "고중량/힘 기반 신경계 피로", FatigueLevel.HIGH),
            section(FatigueDetailType.NEURAL_SPEED, "고속/반응 신경계 피로", FatigueLevel.HIGH),
            presentation = presentation(neural = 20, focus = 75)
        )

        val axes = TodayFatigueStatusLabeler.axisStates(summary).associateBy { it.label }

        assertEquals("동작 집중 부담 높음", TodayFatigueStatusLabeler.label(summary))
        assertEquals("낮음", axes.getValue("신경계").displayLabel)
        assertEquals("현재 높음", axes.getValue("동작 집중").displayLabel)
    }

    @Test
    fun presentationScoreEightyIsElevatedNotHighAfterThresholdRelaxation() {
        val summary = summary(presentation = presentation(focus = 80))

        val maxLevel = TodayFatigueStatusLabeler.axisStates(summary).maxOf { it.level }

        assertEquals(FatigueLevel.ELEVATED, maxLevel)
    }

    private fun summary(
        vararg sections: FatigueDetailSection,
        presentation: FatiguePresentationSnapshot? = null
    ): TodayReadinessSummary =
        TodayReadinessSummary(
            status = ReadinessStatus.FATIGUED,
            headline = "headline",
            shortReason = "reason",
            primaryReasons = emptyList(),
            recommendedModes = emptyList(),
            restrictedModes = emptyList(),
            confidence = AnalysisConfidence.MEDIUM,
            detailSections = sections.toList(),
            adaptiveBaselineNotes = emptyList(),
            generatedAt = LocalDateTime.of(2026, 6, 20, 10, 0),
            fatiguePresentation = presentation
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

    private fun presentation(
        neural: Int = 0,
        systemic: Int = 0,
        local: Int = 0,
        joint: Int = 0,
        focus: Int = 0
    ): FatiguePresentationSnapshot =
        FatiguePresentationSnapshot(
            overallScore = listOf(neural, systemic, local, joint, focus).maxOrNull() ?: 0,
            neuralScore = neural,
            localMuscleScore = local,
            jointTendonScore = joint,
            systemicScore = systemic,
            focusScore = focus,
            highCategories = emptyList(),
            highBodyParts = emptyList(),
            gate = TrainingGateSnapshot(
                overallScore = 0,
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
            availableToday = emptyList(),
            reasons = emptyList()
        )
}
