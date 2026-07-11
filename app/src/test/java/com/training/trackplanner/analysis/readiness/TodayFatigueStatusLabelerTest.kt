package com.training.trackplanner.analysis.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TodayFatigueStatusLabelerTest {
    @Test
    fun ofiNormalWithOneVeryHighAxisKeepsOverallLabelNormalAndSeparatesAxisWarning() {
        val status = TodayFatigueStatusLabeler.currentSummary(
            ofi = 42,
            summary = summary(
                section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
                presentation = presentation(joint = 100)
            )
        )

        assertEquals("피로도 보통", status.ofiLabel)
        assertEquals("관절/건/충격 피로도가 높습니다. 주의하세요.", status.axisMessage)
        assertEquals("축별 상태: 매우 높음(1), 높음(0), 보통(5), 낮음(0)", status.levelCountMessage)
        assertEquals(1, status.veryHighCount)
        assertEquals(0, status.highCount)
        assertEquals(5, status.normalCount)
        assertEquals(0, status.lowCount)
        assertFalse(status.ofiLabel.contains("피로 심화"))
    }

    @Test
    fun veryHighAxisMessageListsOnlyVeryHighAxes() {
        val status = TodayFatigueStatusLabeler.axisSummary(
            summary(
                section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
                presentation = presentation(neural = 100, joint = 100, focus = 84)
            )
        )

        assertEquals("신경계, 관절/건/충격 피로도가 높습니다. 주의하세요.", status.axisMessage)
        assertEquals(2, status.veryHighCount)
        assertEquals(1, status.highCount)
        assertFalse(status.axisMessage.contains("동작 집중"))
    }

    @Test
    fun highAxisMessageListsHighAxesWhenNoVeryHighAxisExists() {
        val status = TodayFatigueStatusLabeler.axisSummary(
            summary(
                section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
                presentation = presentation(neural = 84, focus = 84)
            )
        )

        assertEquals("신경계, 동작 집중 피로도가 높습니다. 스트레스를 줄이면 좋습니다.", status.axisMessage)
        assertEquals(0, status.veryHighCount)
        assertEquals(2, status.highCount)
    }

    @Test
    fun allGoodAxisMessageIsUsedWhenNoHighAxesExist() {
        val status = TodayFatigueStatusLabeler.axisSummary(
            summary(
                section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
                presentation = presentation()
            )
        )

        assertEquals("모든 피로도가 양호합니다. 힘차게 운동!", status.axisMessage)
        assertEquals("축별 상태: 매우 높음(0), 높음(0), 보통(6), 낮음(0)", status.levelCountMessage)
    }

    @Test
    fun elevatedOfiLabelDoesNotNeedHighAxisWarning() {
        val status = TodayFatigueStatusLabeler.currentSummary(
            ofi = 80,
            summary = summary(
                section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
                presentation = presentation()
            )
        )

        assertEquals("피로도 주의", status.ofiLabel)
        assertEquals("모든 피로도가 양호합니다. 힘차게 운동!", status.axisMessage)
    }

    @Test
    fun axisMessageUsesCanonicalDisplayOrderInsteadOfInputOrder() {
        val status = TodayFatigueStatusLabeler.axisSummary(
            summary(
                section(FatigueDetailType.PAIN, "관절 통증", FatigueLevel.HIGH),
                section(FatigueDetailType.NEURAL_SPEED, "신경 반응", FatigueLevel.HIGH)
            )
        )

        assertEquals("신경계, 관절/건/충격 피로도가 높습니다. 스트레스를 줄이면 좋습니다.", status.axisMessage)
    }

    @Test
    fun levelCountsSumToAxisCount() {
        val summary = summary(
            section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
            presentation = presentation(neural = 100, systemic = 84, local = 40, joint = 20, focus = 80)
        )
        val status = TodayFatigueStatusLabeler.axisSummary(summary)
        val axisCount = TodayFatigueStatusLabeler.axisStates(summary).size

        assertEquals(axisCount, status.veryHighCount + status.highCount + status.normalCount + status.lowCount)
    }

    @Test
    fun projectedAxisSummaryRemainsSeparateFromCurrentOfiStatus() {
        val current = TodayFatigueStatusLabeler.currentSummary(
            ofi = 42,
            summary = summary(
                section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
                presentation = presentation(joint = 100)
            )
        )
        val projected = TodayFatigueStatusLabeler.axisSummary(
            summary(
                section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.NORMAL),
                presentation = presentation(neural = 84, focus = 84)
            )
        )

        assertEquals("피로도 보통", current.ofiLabel)
        assertTrue(current.axisMessage.contains("관절/건/충격"))
        assertTrue(projected.axisMessage.contains("신경계, 동작 집중"))
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
        neural: Int = 40,
        systemic: Int = 40,
        local: Int = 40,
        joint: Int = 40,
        focus: Int = 40
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
