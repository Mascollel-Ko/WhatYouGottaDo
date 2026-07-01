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

    private fun summary(vararg sections: FatigueDetailSection): TodayReadinessSummary =
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
            generatedAt = LocalDateTime.of(2026, 6, 20, 10, 0)
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
}
