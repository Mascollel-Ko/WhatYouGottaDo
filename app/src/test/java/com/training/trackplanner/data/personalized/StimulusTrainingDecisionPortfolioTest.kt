package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusTrainingDecisionPortfolioTest {
    private val cutoff = LocalDate.of(2026, 9, 23)

    @Test
    fun qualityStrategyMatrixDistinguishesDirectZeroAndUnavailableBaselines() {
        val expected = mapOf(
            TrainingNeedDecision.DEVELOP to Triple(StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE,
                StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY),
            TrainingNeedDecision.MAINTAIN to Triple(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE,
                StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY, StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY),
            TrainingNeedDecision.MAINTAIN_OR_PROGRESS to Triple(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION,
                StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY,
                StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY),
            TrainingNeedDecision.REDISTRIBUTE to Triple(StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE,
                StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY, StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY),
            TrainingNeedDecision.REDUCE to Triple(StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE,
                StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE, StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE),
            TrainingNeedDecision.NO_EXTRA_NEED to Triple(StimulusDoseStrategy.NO_MINIMUM_TARGET,
                StimulusDoseStrategy.NO_MINIMUM_TARGET, StimulusDoseStrategy.NO_MINIMUM_TARGET),
            TrainingNeedDecision.UNKNOWN to Triple(StimulusDoseStrategy.UNRESOLVED,
                StimulusDoseStrategy.UNRESOLVED, StimulusDoseStrategy.UNRESOLVED),
            TrainingNeedDecision.PROGRESS to Triple(StimulusDoseStrategy.UNRESOLVED,
                StimulusDoseStrategy.UNRESOLVED, StimulusDoseStrategy.UNRESOLVED)
        )
        expected.forEach { (decision, states) ->
            assertEquals(states.first, build(decision, directBaseline()).qualityDecisions.single().strategy)
            assertEquals(states.second, build(decision, zeroBaseline()).qualityDecisions.single().strategy)
            assertEquals(states.third, build(decision, unavailableBaseline()).qualityDecisions.single().strategy)
        }
        assertTrue(build(TrainingNeedDecision.DEVELOP, zeroBaseline()).qualityDecisions.single().baselineAvailable)
        assertFalse(build(TrainingNeedDecision.DEVELOP, unavailableBaseline()).qualityDecisions.single().baselineAvailable)
    }

    @Test
    fun supportiveOnlyHistoryNeverBecomesDirectBaseline() {
        val band = SuccessfulDoseBand(
            eligibleWeekCount = 4,
            supportiveUnitsMedian = 6.0,
            confidence = PlanningConfidence.MODERATE,
            source = SuccessfulDoseSource.NO_PERSONAL_BASELINE,
            directExposureWeekCount = 0
        )
        val decision = build(TrainingNeedDecision.DEVELOP, baseline(true, mapOf(TrainableQuality.STRENGTH to band)))
            .qualityDecisions.single()
        assertFalse(decision.hasPersonalDirectBaseline)
        assertEquals(StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, decision.strategy)
        assertTrue(decision.reasonCodes.contains("SUPPORTIVE_ONLY_HISTORY_NOT_USED_AS_DIRECT_BASELINE"))
    }

    @Test
    fun needAndBaselineConfidenceRemainSeparateAndExecutionModifiersAreNonAuthoritative() {
        val profile = profile(TrainingNeedDecision.MAINTAIN, confidence = PlanningConfidence.HIGH).copy(
            executionModifiers = listOf(ExecutionModifierTrace("strength", emptyList(), ExecutionModifier.HOLD, listOf("TEST")))
        )
        val band = SuccessfulDoseBand(4, directUnitsMedian = 8.0, confidence = PlanningConfidence.LOW,
            source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, directExposureWeekCount = 4)
        val decision = StimulusTrainingDecisionPortfolioEngine().build(profile, baseline(true, mapOf(TrainableQuality.STRENGTH to band)))
            .qualityDecisions.single()
        assertEquals(PlanningConfidence.HIGH, decision.needConfidence)
        assertEquals(PlanningConfidence.LOW, decision.baselineConfidence)
        assertEquals(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE, decision.strategy)
        assertTrue(decision.reasonCodes.contains("CURRENT_EXECUTION_CONSTRAINT_PRESENT"))
    }

    @Test
    fun tasksRemainDirectionalAndNeverGainNumericBaselineAuthority() {
        val expected = mapOf(
            TrainingNeedDecision.UNKNOWN to StimulusDoseStrategy.UNRESOLVED,
            TrainingNeedDecision.PROGRESS to StimulusDoseStrategy.UNRESOLVED,
            TrainingNeedDecision.NO_EXTRA_NEED to StimulusDoseStrategy.NO_MINIMUM_TARGET,
            TrainingNeedDecision.DEVELOP to StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            TrainingNeedDecision.MAINTAIN to StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY,
            TrainingNeedDecision.MAINTAIN_OR_PROGRESS to StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY,
            TrainingNeedDecision.REDISTRIBUTE to StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY,
            TrainingNeedDecision.REDUCE to StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE
        )
        expected.forEach { (need, strategy) ->
            val decision = StimulusTrainingDecisionPortfolioEngine().build(
                profile(TrainingNeedDecision.NO_EXTRA_NEED, taskDecision = need), directBaseline()
            ).taskDecisions.single()
            assertEquals(strategy, decision.strategy)
            assertFalse(decision.numericBaselineAuthority)
            assertTrue(decision.reasonCodes.contains("NO_LEDGER_BACKED_COMPLETED_WEEK_TASK_BASELINE_IN_B3"))
            assertTrue(decision.reasonCodes.contains("TASK_REMAINS_DIRECTION_ONLY"))
        }
    }

    @Test
    fun taskComparisonAttributesNeedDifferenceToCanonicalNeedAndKeepsBaselineOut() {
        val legacy = TrainingDecisionPortfolio(
            qualityDecisions = emptyList(),
            taskDecisions = listOf(TaskTrainingDecision(
                task = "TASK", needDecision = TrainingNeedDecision.MAINTAIN,
                action = TargetStimulusAction.HOLD_SUCCESSFUL_DOSE,
                priority = TargetPriority.MAINTENANCE, confidence = PlanningConfidence.HIGH,
                explicitUserTaskPriority = false, reasonCodes = emptyList(), evidence = emptyList()
            )), unresolved = emptyList()
        )
        val canonical = build(TrainingNeedDecision.NO_EXTRA_NEED, directBaseline(), taskDecision = TrainingNeedDecision.DEVELOP)
        val comparison = StimulusTrainingDecisionPortfolioComparisonEngine()
            .compare(legacy, canonical, directBaseline()).taskComparisons.single()
        assertEquals(StimulusPortfolioComparisonStatus.DIFFERENT, comparison.status)
        assertTrue(comparison.reasonCodes.contains("NEED_DECISION_CHANGED_BY_LEDGER_SEMANTICS"))
        assertTrue(comparison.reasonCodes.contains("ACTION_CHANGED_BY_CANONICAL_NEED"))
        assertFalse(comparison.reasonCodes.contains("ACTION_CHANGED_BY_CANONICAL_BASELINE"))
        assertFalse(canonical.taskDecisions.single().numericBaselineAuthority)
    }

    @Test
    fun taskComparisonReportsDirectionalSemanticMatch() {
        val legacy = TrainingDecisionPortfolio(
            qualityDecisions = emptyList(),
            taskDecisions = listOf(TaskTrainingDecision(
                task = "TASK", needDecision = TrainingNeedDecision.MAINTAIN,
                action = TargetStimulusAction.HOLD_SUCCESSFUL_DOSE,
                priority = TargetPriority.MAINTENANCE, confidence = PlanningConfidence.HIGH,
                explicitUserTaskPriority = false, reasonCodes = emptyList(), evidence = emptyList()
            )), unresolved = emptyList()
        )
        val canonical = build(TrainingNeedDecision.NO_EXTRA_NEED, directBaseline(), taskDecision = TrainingNeedDecision.MAINTAIN)
        val comparison = StimulusTrainingDecisionPortfolioComparisonEngine()
            .compare(legacy, canonical, directBaseline()).taskComparisons.single()
        assertEquals(StimulusPortfolioComparisonStatus.MATCH, comparison.status)
        assertTrue(comparison.reasonCodes.contains("ACTION_SEMANTICS_MATCH"))
        assertTrue(comparison.reasonCodes.contains("TASK_REMAINS_DIRECTION_ONLY"))
    }

    @Test
    fun priorityPolicyMatchesLegacyTable() {
        val cases = listOf(
            Triple(NeedRelevance.HIGH, TrainingNeedDecision.DEVELOP, TargetPriority.PRIMARY),
            Triple(NeedRelevance.HIGH, TrainingNeedDecision.MAINTAIN, TargetPriority.MAINTENANCE),
            Triple(NeedRelevance.HIGH, TrainingNeedDecision.PROGRESS, TargetPriority.PRIMARY),
            Triple(NeedRelevance.MODERATE, TrainingNeedDecision.DEVELOP, TargetPriority.SECONDARY),
            Triple(NeedRelevance.MODERATE, TrainingNeedDecision.REDUCE, TargetPriority.MAINTENANCE),
            Triple(NeedRelevance.LOW, TrainingNeedDecision.DEVELOP, TargetPriority.BACKGROUND),
            Triple(NeedRelevance.NONE, TrainingNeedDecision.DEVELOP, TargetPriority.NONE),
            Triple(NeedRelevance.UNKNOWN, TrainingNeedDecision.DEVELOP, TargetPriority.UNRESOLVED)
        )
        cases.forEach { (relevance, need, expected) ->
            assertEquals(expected, build(need, directBaseline(), relevance).qualityDecisions.single().priority)
        }
    }

    @Test
    fun comparisonReportsMatchAndBaselineDifferenceWithoutChoosingWinner() {
        val matchingCanonical = build(TrainingNeedDecision.DEVELOP, directBaseline())
        val matchingLegacy = TrainingDecisionPortfolio(
            qualityDecisions = listOf(QualityTrainingDecision(
                TrainableQuality.STRENGTH, TrainingNeedDecision.DEVELOP,
                TargetStimulusAction.RESTORE_PERSONAL_BASELINE, TargetPriority.PRIMARY,
                PlanningConfidence.HIGH, emptyList(), emptyList()
            )), taskDecisions = emptyList(), unresolved = emptyList()
        )
        val matching = StimulusTrainingDecisionPortfolioComparisonEngine()
            .compare(matchingLegacy, matchingCanonical, directBaseline()).qualityComparisons.single()
        assertEquals(StimulusPortfolioComparisonStatus.MATCH, matching.status)
        assertTrue(matching.reasonCodes.contains("NEED_DECISION_MATCH"))
        assertTrue(matching.reasonCodes.contains("LEGACY_AND_CANONICAL_BASELINE_MATCH"))

        val changedCanonical = build(TrainingNeedDecision.MAINTAIN, zeroBaseline())
        val changedLegacy = matchingLegacy.copy(qualityDecisions = listOf(matchingLegacy.qualityDecisions.single().copy(
            needDecision = TrainingNeedDecision.MAINTAIN, action = TargetStimulusAction.HOLD_SUCCESSFUL_DOSE,
            priority = TargetPriority.MAINTENANCE
        )))
        val changed = StimulusTrainingDecisionPortfolioComparisonEngine()
            .compare(changedLegacy, changedCanonical, zeroBaseline()).qualityComparisons.single()
        assertEquals(StimulusPortfolioComparisonStatus.DIFFERENT, changed.status)
        assertTrue(changed.reasonCodes.contains("CANONICAL_BASELINE_DIFFERS_FROM_LEGACY"))
        assertTrue(changed.reasonCodes.contains("CANONICAL_PERSONAL_DIRECT_BASELINE_ABSENT"))
    }

    @Test
    fun compactJsonKeepsB3SeparateAndOmitsWeeklyEvidence() {
        val portfolio = build(TrainingNeedDecision.DEVELOP, directBaseline())
        val json = portfolio.copy(comparison = StimulusPortfolioComparison(emptyList(), emptyList()))
            .toCompactJson()
        assertTrue(json.has("qualityDecisions"))
        assertTrue(json.has("comparison"))
        assertFalse(json.has("weeklyEvidence"))
        assertNotNull(json.getJSONArray("qualityDecisions").getJSONObject(0).get("baselineConfidence"))
    }

    private fun build(
        decision: TrainingNeedDecision,
        baseline: LedgerBackedQualityDoseHistory,
        relevance: NeedRelevance = NeedRelevance.HIGH,
        taskDecision: TrainingNeedDecision = TrainingNeedDecision.UNKNOWN
    ): StimulusTrainingDecisionPortfolio = StimulusTrainingDecisionPortfolioEngine().build(
        profile(decision, relevance, taskDecision), baseline
    )

    private fun profile(
        decision: TrainingNeedDecision,
        relevance: NeedRelevance = NeedRelevance.HIGH,
        taskDecision: TrainingNeedDecision = TrainingNeedDecision.UNKNOWN,
        confidence: PlanningConfidence = PlanningConfidence.MODERATE
    ) = AthleteStimulusNeedProfile(
        generatedAtCutoff = cutoff,
        qualityNeeds = listOf(AthleteStimulusQualityNeed(
            TrainableQuality.STRENGTH, relevance, StimulusExposureEvidence(),
            TrainingResponseState.STABLE_RESPONSE, decision, confidence
        )),
        sportTaskNeeds = listOf(AthleteStimulusTaskNeed(
            "TASK", relevance, StimulusExposureEvidence(), TrainingResponseState.INSUFFICIENT_EVIDENCE,
            taskDecision, confidence
        )),
        courtContext = CourtContextEvidence()
    )

    private fun directBaseline() = baseline(true, mapOf(TrainableQuality.STRENGTH to SuccessfulDoseBand(
        eligibleWeekCount = 4, directUnitsMedian = 8.0, directSessionsMedian = 2.0,
        confidence = PlanningConfidence.HIGH, source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        directExposureWeekCount = 4, directExposureWeekFrequency = 1.0
    )))

    private fun zeroBaseline() = baseline(true, mapOf(TrainableQuality.STRENGTH to SuccessfulDoseBand(
        eligibleWeekCount = 8, confidence = PlanningConfidence.LOW, source = SuccessfulDoseSource.NO_PERSONAL_BASELINE
    )))

    private fun unavailableBaseline() = baseline(false, emptyMap())

    private fun baseline(available: Boolean, bands: Map<TrainableQuality, SuccessfulDoseBand>) = LedgerBackedQualityDoseHistory(
        bands = bands,
        weeklyEvidence = emptyMap(),
        indexedWeekCount = if (available) 8 else 0,
        excludedWeekCount = 0,
        horizon = qualityDoseHistoryHorizon(cutoff),
        comparisons = emptyMap(),
        available = available,
        reasonCodes = if (available) emptyList() else listOf("LEDGER_UNAVAILABLE_OR_CUTOFF_MISMATCH")
    )
}
