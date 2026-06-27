package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.analysis.readiness.FatiguePresentationSnapshot
import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FatigueAnalysisMapperTest {
    @Test
    fun `simple state returns OFI and no more than two load items`() {
        val state = FatigueAnalysisMapper.map(history(14))

        assertEquals(14, state.simple.ofiSeries.size)
        assertEquals(2, state.simple.highLoadItems.size)
        assertEquals(2, state.simple.availableLoadItems.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `readiness presentation overrides simple load items but not historical OFI series`() {
        val history = history(14)
        val state = FatigueAnalysisMapper.map(
            history = history,
            fatiguePresentation = presentation(
                overall = 88,
                neural = 91,
                local = 15,
                joint = 76,
                systemic = 44,
                focus = 22
            )
        )

        assertEquals(history.last().state.overallFatigueIndex.toDouble(), state.simple.ofiSeries.last().value, 0.0001)
        assertEquals(91, state.simple.highLoadItems.first().score)
        assertEquals(FatigueTarget.NEUROMUSCULAR.label, state.simple.highLoadItems.first().label)
        assertEquals(15, state.simple.availableLoadItems.first().score)
    }

    @Test
    fun `null readiness presentation keeps legacy simple overview values`() {
        val history = history(14)
        val state = FatigueAnalysisMapper.map(
            history = history,
            fatiguePresentation = null
        )

        assertEquals(history.last().state.overallFatigueIndex.toDouble(), state.simple.ofiSeries.last().value, 0.0001)
        assertEquals(history.last().state.recoveryPressureScore, state.simple.highLoadItems.first().score)
    }

    @Test
    fun `readiness presentation scores are clamped for analysis overview`() {
        val history = history(14)
        val state = FatigueAnalysisMapper.map(
            history = history,
            fatiguePresentation = presentation(
                overall = 140,
                neural = 140,
                local = -5,
                joint = 76,
                systemic = 44,
                focus = 22
            )
        )

        assertEquals(history.last().state.overallFatigueIndex.toDouble(), state.simple.ofiSeries.last().value, 0.0001)
        assertTrue(state.simple.highLoadItems.all { item -> item.score in 0..100 })
        assertTrue(state.simple.availableLoadItems.all { item -> item.score in 0..100 })
    }

    @Test
    fun `simple OFI series matches detail overall historical series`() {
        val state = FatigueAnalysisMapper.map(
            history = history(14),
            fatiguePresentation = presentation(
                overall = 99,
                neural = 91,
                local = 15,
                joint = 76,
                systemic = 44,
                focus = 22
            ),
            projectedOverallFatigueScore = 99.0,
            hasRemainingUnconfirmedWork = true
        )

        val detailOverall = state.detail.fatigueTrendSeries
            .single { it.key == FatigueTarget.OVERALL.name }
            .points

        assertEquals(detailOverall, state.simple.ofiSeries)
    }

    @Test
    fun `projected fatigue does not overwrite final simple OFI point`() {
        val state = FatigueAnalysisMapper.map(
            history = history(14),
            projectedOverallFatigueScore = 99.0,
            hasRemainingUnconfirmedWork = true
        )
        val detailOverall = state.detail.fatigueTrendSeries
            .single { it.key == FatigueTarget.OVERALL.name }
            .points
        val actualLastPoint = detailOverall.last()
        val previousActualPoint = detailOverall.dropLast(1).last()

        assertEquals(actualLastPoint, state.simple.ofiSeries.last())
        assertEquals(previousActualPoint, state.simple.projectedOfiOverlay.first())
        assertEquals(actualLastPoint.date, state.simple.projectedOfiOverlay.last().date)
        assertEquals(99.0, state.simple.projectedOfiOverlay.last().value, 0.001)
    }

    @Test
    fun `projected overlay is emitted only when unconfirmed work remains`() {
        val withoutUnconfirmed = FatigueAnalysisMapper.map(
            history = history(14),
            projectedOverallFatigueScore = 88.0,
            hasRemainingUnconfirmedWork = false
        )
        val withUnconfirmed = FatigueAnalysisMapper.map(
            history = history(14),
            projectedOverallFatigueScore = 88.0,
            hasRemainingUnconfirmedWork = true
        )

        assertTrue(withoutUnconfirmed.simple.projectedOfiOverlay.isEmpty())
        assertEquals(withUnconfirmed.simple.ofiSeries.dropLast(1).last(), withUnconfirmed.simple.projectedOfiOverlay.first())
        assertEquals(88.0, withUnconfirmed.simple.projectedOfiOverlay.last().value, 0.001)
    }

    @Test
    fun `projected overlay is skipped when historical OFI has fewer than two points`() {
        val state = FatigueAnalysisMapper.map(
            history = history(1),
            projectedOverallFatigueScore = 88.0,
            hasRemainingUnconfirmedWork = true
        )

        assertTrue(state.simple.projectedOfiOverlay.isEmpty())
    }

    @Test
    fun `readiness presentation overview labels do not expose raw enum names`() {
        val state = FatigueAnalysisMapper.map(
            history = history(14),
            fatiguePresentation = presentation(
                overall = 88,
                neural = 91,
                local = 15,
                joint = 76,
                systemic = 44,
                focus = 22
            )
        )
        val labels = (state.simple.highLoadItems + state.simple.availableLoadItems).map { item -> item.label }

        assertTrue(labels.none { label -> label.contains("NEURAL") })
        assertTrue(labels.none { label -> label.contains("ELASTIC_SSC") })
        assertTrue(labels.none { label -> label.contains("BADMINTON_COURT") })
    }

    @Test
    fun `detail state exposes OFI and all six fatigue axes`() {
        val state = FatigueAnalysisMapper.map(
            history = history(14),
            selectedTargets = setOf(
                FatigueTarget.OVERALL,
                FatigueTarget.JOINT_TENDON_IMPACT
            )
        )

        assertEquals(7, state.detail.fatigueTrendSeries.size)
        assertEquals(2, state.detail.selectedFatigueTargets.size)
        assertTrue(state.detail.fatigueTrendSeries.any { it.key == FatigueTarget.OVERALL.name })
    }

    @Test
    fun `contribution state groups sources instead of repeating axis series`() {
        val state = FatigueAnalysisMapper.map(
            history = history(14),
            contributionTarget = FatigueTarget.LOCAL_MUSCULAR,
            grouping = ContributionGrouping.REDUNDANCY_GROUP
        )

        assertTrue(state.detail.contributionSeries.isNotEmpty())
        assertTrue(state.detail.contributionSeries.all { it.target == FatigueTarget.LOCAL_MUSCULAR })
        assertTrue(state.detail.contributionSeries.none { it.sourceKey in FatigueTarget.entries.map(FatigueTarget::name) })
    }

    @Test
    fun `twelve week period aggregates daily points into weekly points`() {
        val state = FatigueAnalysisMapper.map(
            history = history(84),
            period = FatigueAnalysisPeriod.TWELVE_WEEKS
        )

        assertTrue(state.detail.usesWeeklyAggregation)
        assertTrue(state.simple.ofiSeries.size in 12..13)
    }

    @Test
    fun `empty history returns compact empty state`() {
        val state = FatigueAnalysisMapper.map(emptyList())

        assertTrue(state.simple.ofiSeries.isEmpty())
        assertTrue(state.detail.contributionSeries.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `zero-only calculated history does not create a fake chart`() {
        val date = LocalDate.of(2026, 6, 19)
        val result = DailyFatigueResult(
            state = DailyFatigueState(
                date = date,
                neuromuscularFatigue = 0.0,
                systemicMuscularFatigue = 0.0,
                localMuscularFatigue = 0.0,
                jointTendonImpactFatigue = 0.0,
                movementFocusFatigue = 0.0,
                recoveryPressure = 0.0,
                neuromuscularScore = 0,
                systemicMuscularScore = 0,
                localMuscularScore = 0,
                jointTendonImpactScore = 0,
                movementFocusScore = 0,
                recoveryPressureScore = 0,
                overallFatigueIndex = 0,
                readinessLabel = FatigueReadinessLabel.LOW,
                cautionReasons = emptyList(),
                confidence = FatigueConfidence.LOW
            ),
            groupStates = emptyList(),
            recordContributions = emptyList()
        )

        val state = FatigueAnalysisMapper.map(listOf(result))

        assertTrue(state.simple.ofiSeries.isEmpty())
        assertTrue(state.detail.fatigueTrendSeries.isEmpty())
    }

    @Test
    fun `chart starts at first confirmed workout instead of profile-only dates`() {
        val seededHistory = history(14).mapIndexed { index, result ->
            if (index < 4) {
                result.copy(state = result.state.copy(confirmedTrainingLoad = 0.0))
            } else {
                result
            }
        }

        val state = FatigueAnalysisMapper.map(seededHistory)

        assertEquals(10, state.simple.ofiSeries.size)
        assertEquals(seededHistory[4].state.date, state.simple.ofiSeries.first().date)
    }

    @Test
    fun `explicit empty source selection stays empty`() {
        val state = FatigueAnalysisMapper.map(
            history = history(14),
            selectedSourceKeys = emptySet(),
            defaultSourcesWhenEmpty = false
        )

        assertTrue(state.detail.contributionSeries.isNotEmpty())
        assertTrue(state.detail.selectedContributionSourceKeys.isEmpty())
        assertTrue(state.detail.defaultContributionSourceKeys.isNotEmpty())
    }

    @Test
    fun `contribution options include selected-period percentages`() {
        val state = FatigueAnalysisMapper.map(history(14))

        assertEquals(100, state.detail.contributionSeries.single().periodContributionPercent)
    }

    private fun history(days: Int): List<DailyFatigueResult> {
        val end = LocalDate.of(2026, 6, 19)
        return (days - 1 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong())
            val score = 35 + (offset % 40)
            DailyFatigueResult(
                state = DailyFatigueState(
                    date = date,
                    neuromuscularFatigue = score.toDouble(),
                    systemicMuscularFatigue = (score + 2).toDouble(),
                    localMuscularFatigue = (score + 4).toDouble(),
                    jointTendonImpactFatigue = (score + 6).toDouble(),
                    movementFocusFatigue = (score + 8).toDouble(),
                    recoveryPressure = (score + 10).toDouble(),
                    neuromuscularScore = score,
                    systemicMuscularScore = score + 2,
                    localMuscularScore = score + 4,
                    jointTendonImpactScore = score + 6,
                    movementFocusScore = score + 8,
                    recoveryPressureScore = score + 10,
                    overallFatigueIndex = score + 5,
                    readinessLabel = FatigueReadinessLabel.NORMAL,
                    cautionReasons = emptyList(),
                    confidence = FatigueConfidence.HIGH,
                    confirmedTrainingLoad = score.toDouble()
                ),
                groupStates = listOf(
                    GroupFatigueState(
                        date = date,
                        groupType = "redundancyGroup",
                        groupKey = "HORIZONTAL_PUSH_COMPOUND",
                        neuromuscularFatigue = score * 0.4,
                        systemicMuscularFatigue = score * 0.5,
                        localFatigue = score * 0.8,
                        jointTendonImpactFatigue = score * 0.3,
                        movementFocusFatigue = score * 0.2,
                        recoveryPressure = score * 0.6
                    )
                ),
                recordContributions = emptyList()
            )
        }
    }

    private fun presentation(
        overall: Int,
        neural: Int,
        local: Int,
        joint: Int,
        systemic: Int,
        focus: Int
    ): FatiguePresentationSnapshot =
        FatiguePresentationSnapshot(
            overallScore = overall,
            neuralScore = neural,
            localMuscleScore = local,
            jointTendonScore = joint,
            systemicScore = systemic,
            focusScore = focus,
            highCategories = emptyList(),
            highBodyParts = emptyList(),
            gate = TrainingGateSnapshot(
                overallScore = overall,
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
