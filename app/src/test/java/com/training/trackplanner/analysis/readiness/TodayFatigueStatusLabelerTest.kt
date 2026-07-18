package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueLabelResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TodayFatigueStatusLabelerTest {
    @Test
    fun canonicalOfiAndAxesDriveCurrentStatusWithoutRecoveryAxis() {
        val status = TodayFatigueStatusLabeler.currentSummary(
            state(ofi = 42, speed = 100, recovery = 100)
        )

        assertEquals("피로도 보통", status.ofiLabel)
        assertEquals("판단: 고속 피로도를 조절하면 좋습니다.", status.judgementMessage)
        assertEquals("고속 피로도가 높습니다. 주의하세요.", status.axisMessage)
        assertEquals("축별 상태: 매우 높음(1), 높음(0), 보통(4), 낮음(0)", status.levelCountMessage)
        assertEquals(1, status.veryHighCount)
        assertEquals(0, status.highCount)
        assertEquals(4, status.normalCount)
        assertEquals(0, status.lowCount)
        assertFalse(status.axisMessage.contains("회복 지속"))
        assertFalse(status.ofiLabel.contains("연결조직"))
        assertFalse(status.judgementMessage.contains("연결조직"))
        assertFalse(status.axisMessage.contains("연결조직"))
    }

    @Test
    fun veryHighAxisMessageListsOnlyVeryHighCanonicalAxes() {
        val status = TodayFatigueStatusLabeler.axisSummary(
            state(ofi = 42, highForce = 100, speed = 100, reactive = 84)
        )

        assertEquals("고중량·힘 신경계, 고속 피로도가 높습니다. 주의하세요.", status.axisMessage)
        assertEquals(2, status.veryHighCount)
        assertEquals(1, status.highCount)
        assertFalse(status.axisMessage.contains("반응"))
    }

    @Test
    fun highAxisMessageListsHighAxesWhenNoVeryHighAxisExists() {
        val status = TodayFatigueStatusLabeler.axisSummary(
            state(ofi = 42, highForce = 84, reactive = 84)
        )

        assertEquals("고중량·힘 신경계, 반응 피로도가 높습니다. 스트레스를 줄이면 좋습니다.", status.axisMessage)
        assertEquals(0, status.veryHighCount)
        assertEquals(2, status.highCount)
    }

    @Test
    fun singleAxisWarningSentenceNamesAxisOnceWithoutGenericStressReference() {
        val veryHighAxis = TodayFatigueStatusLabeler.axisStates(state(ofi = 42, speed = 100))
            .single { axis -> axis.label == "고속" }
        val highAxis = TodayFatigueStatusLabeler.axisStates(state(ofi = 42, reactive = 84))
            .single { axis -> axis.label == "반응" }

        assertEquals("고속 피로도가 높습니다. 주의하세요.", TodayFatigueStatusLabeler.axisWarningSentence(veryHighAxis))
        assertEquals("반응 피로도가 높습니다. 스트레스를 줄이면 좋습니다.", TodayFatigueStatusLabeler.axisWarningSentence(highAxis))
        assertFalse(TodayFatigueStatusLabeler.axisWarningSentence(veryHighAxis).contains("해당 스트레스"))
        assertFalse(TodayFatigueStatusLabeler.axisWarningSentence(highAxis).contains("해당 스트레스"))
    }

    @Test
    fun allGoodAxisMessageAndJudgementAreUsedWhenNoHighAxesExist() {
        val status = TodayFatigueStatusLabeler.currentSummary(state(ofi = 80))

        assertEquals("피로도 주의", status.ofiLabel)
        assertEquals("판단: 현재 특별히 조절이 필요한 피로도 축은 없습니다.", status.judgementMessage)
        assertEquals("모든 피로도가 양호합니다. 힘차게 운동!", status.axisMessage)
        assertEquals("축별 상태: 매우 높음(0), 높음(0), 보통(5), 낮음(0)", status.levelCountMessage)
    }

    @Test
    fun multipleAxisJudgementUsesOnlyCanonicalAxisNames() {
        val status = TodayFatigueStatusLabeler.currentSummary(
            state(ofi = 42, local = 84, speed = 84)
        )

        assertEquals("판단: 국소 근육과 고속 피로도를 조절하면 좋습니다.", status.judgementMessage)
        listOf("하체", "점프", "오버헤드", "배드민턴", "고중량").forEach { banned ->
            assertFalse(status.judgementMessage.contains(banned))
        }
    }

    @Test
    fun readinessFallbackAxisStatesDoNotExposeRecoveryAsDisplayedAxis() {
        val summary = readinessSummary(
            section(FatigueDetailType.RECOVERY, "회복", FatigueLevel.VERY_HIGH),
            presentation = presentation(speed = 100)
        )
        val axes = TodayFatigueStatusLabeler.axisStates(summary)

        assertEquals(5, axes.size)
        assertTrue(axes.any { it.label == "고속" })
        assertFalse(axes.any { it.label == "회복 지속" })
    }

    @Test
    fun presentationScoreEightyIsElevatedNotHighAfterThresholdRelaxation() {
        val maxLevel = TodayFatigueStatusLabeler.axisStates(state(ofi = 42, reactive = 80)).maxOf { it.level }

        assertEquals(FatigueLevel.ELEVATED, maxLevel)
    }

    private fun state(
        ofi: Int,
        highForce: Int = 40,
        systemic: Int = 40,
        local: Int = 40,
        speed: Int = 40,
        reactive: Int = 40,
        recovery: Int = 40
    ): DailyFatigueState =
        DailyFatigueState(
            date = LocalDate.of(2026, 6, 20),
            highForceNeuralFatigue = 0.0,
            systemicMuscularFatigue = 0.0,
            localMuscularFatigue = 0.0,
            highSpeedFatigue = 0.0,
            reactiveFatigue = 0.0,
            recoveryPressure = 0.0,
            highForceNeuralScore = highForce,
            systemicMuscularScore = systemic,
            localMuscularScore = local,
            highSpeedScore = speed,
            reactiveScore = reactive,
            recoveryPressureScore = recovery,
            overallFatigueIndex = ofi,
            readinessLabel = FatigueLabelResolver.label(ofi),
            cautionReasons = emptyList(),
            confidence = FatigueConfidence.MEDIUM
        )

    private fun readinessSummary(
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
        highForce: Int = 40,
        systemic: Int = 40,
        local: Int = 40,
        speed: Int = 40,
        reactive: Int = 40
    ): FatiguePresentationSnapshot =
        FatiguePresentationSnapshot(
            overallScore = listOf(highForce, systemic, local, speed, reactive).maxOrNull() ?: 0,
            highForceNeuralScore = highForce,
            localMuscularScore = local,
            highSpeedScore = speed,
            systemicMuscularScore = systemic,
            reactiveScore = reactive,
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
