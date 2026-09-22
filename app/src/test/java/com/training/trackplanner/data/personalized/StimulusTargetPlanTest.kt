package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusTargetPlanTest {
    @Test
    fun exactStrategyAuthorityMatrixNeverInventsDose() {
        val expected = mapOf(
            StimulusDoseStrategy.HOLD_PERSONAL_BASELINE to StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
            StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION to StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
            StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE to StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
            StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE to StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
            StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.NO_MINIMUM_TARGET to StimulusTargetNumericAuthority.NONE,
            StimulusDoseStrategy.UNRESOLVED to StimulusTargetNumericAuthority.UNRESOLVED
        )
        expected.forEach { (strategy, authority) ->
            val plan = StimulusTargetPlanEngine().build(portfolio(strategy), baseline(valid = true))
            val target = plan.qualityTargets.single()
            assertEquals(strategy, target.strategy)
            assertEquals(authority, target.numericAuthority)
            if (authority in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
                )) assertTrue(target.weeklyDirectUnitsTarget != null && target.weeklyDirectSessionsTarget != null)
            else assertNull(target.weeklyDirectUnitsTarget)
        }
    }

    @Test
    fun personalBaselineRangesPropagateExactlyWithoutRounding() {
        val target = StimulusTargetPlanEngine().build(
            portfolio(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE),
            baseline(valid = true)
        ).qualityTargets.single()
        assertEquals(StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, target.numericAuthority)
        assertEquals(StimulusTargetRange(4.0, 6.0, 9.0), target.weeklyDirectUnitsTarget)
        assertEquals(StimulusTargetRange(1.0, 2.0, 3.0), target.weeklyDirectSessionsTarget)
        assertEquals(StimulusTargetRange(5.0, 7.0, 10.0), target.exposureWeekDirectUnitsReference)
        assertEquals(StimulusTargetRange(1.0, 2.0, 2.0), target.exposureWeekDirectSessionsReference)
        assertEquals(0.75, requireNotNull(target.exposureWeekFrequencyReference), 0.0)
    }

    @Test
    fun progressionAndRestorePreserveTheSameB2Envelope() {
        val baseline = baseline(valid = true)
        val hold = StimulusTargetPlanEngine().build(portfolio(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE), baseline).qualityTargets.single()
        val progress = StimulusTargetPlanEngine().build(portfolio(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION), baseline).qualityTargets.single()
        val restore = StimulusTargetPlanEngine().build(portfolio(StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE), baseline).qualityTargets.single()
        assertEquals(hold.weeklyDirectUnitsTarget, progress.weeklyDirectUnitsTarget)
        assertEquals(hold.weeklyDirectSessionsTarget, progress.weeklyDirectSessionsTarget)
        assertEquals(hold.weeklyDirectUnitsTarget, restore.weeklyDirectUnitsTarget)
        assertEquals(StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE, restore.numericAuthority)
        assertFalse(restore.reasonCodes.contains("AUTOMATIC_NUMERIC_INCREASE"))
    }

    @Test
    fun reduceNeverCalculatesASecretPercentage() {
        val target = StimulusTargetPlanEngine().build(
            portfolio(StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE), baseline(valid = true)
        ).qualityTargets.single()
        assertEquals(StimulusTargetNumericAuthority.DIRECTION_ONLY, target.numericAuthority)
        assertNull(target.weeklyDirectUnitsTarget)
        assertNull(target.weeklyDirectSessionsTarget)
        assertTrue(target.reasonCodes.contains("AUTOMATIC_NUMERIC_REDUCTION_NOT_AUTHORIZED"))
    }

    @Test
    fun novelAndSupportiveOnlyEvidenceRemainDirectional() {
        val novel = StimulusTargetPlanEngine().build(
            portfolio(StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS), baseline(valid = false)
        ).qualityTargets.single()
        assertEquals(StimulusTargetNumericAuthority.DIRECTION_ONLY, novel.numericAuthority)
        assertNull(novel.weeklyDirectUnitsTarget)
        assertTrue(novel.reasonCodes.contains("NO_PERSONAL_DIRECT_BASELINE"))

        val supportiveOnly = StimulusTargetPlanEngine().build(
            portfolio(StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE), baseline(valid = false, supportiveOnly = true)
        ).qualityTargets.single()
        assertEquals(StimulusTargetNumericAuthority.DIRECTION_ONLY, supportiveOnly.numericAuthority)
        assertTrue(supportiveOnly.reasonCodes.contains("B3_B2_BASELINE_CONTRACT_MISMATCH"))
    }

    @Test
    fun unavailableBaselineForBaselineStrategyIsUnresolvedAndSafe() {
        val target = StimulusTargetPlanEngine().build(
            portfolio(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE), baseline(valid = true, available = false)
        ).qualityTargets.single()
        assertEquals(StimulusTargetNumericAuthority.UNRESOLVED, target.numericAuthority)
        assertNull(target.weeklyDirectUnitsTarget)
        assertTrue(target.reasonCodes.contains("CANONICAL_PERSONAL_BASELINE_NOT_AVAILABLE"))
    }

    @Test
    fun malformedBaselineDoesNotGetSortedOrPublished() {
        val band = validBand().copy(weeklyDirectUnitsQ25 = 8.0, weeklyDirectUnitsMedian = 4.0)
        val target = StimulusTargetPlanEngine().build(
            portfolio(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE), baseline(valid = true, band = band)
        ).qualityTargets.single()
        assertEquals(StimulusTargetNumericAuthority.DIRECTION_ONLY, target.numericAuthority)
        assertNull(target.weeklyDirectUnitsTarget)
        assertTrue(target.reasonCodes.contains("MALFORMED_OR_INCOMPLETE_PERSONAL_BASELINE"))
    }

    @Test
    fun everyTaskTargetRemainsNonNumericWithExactAuthority() {
        val strategies = listOf(
            StimulusDoseStrategy.UNRESOLVED to StimulusTargetNumericAuthority.UNRESOLVED,
            StimulusDoseStrategy.NO_MINIMUM_TARGET to StimulusTargetNumericAuthority.NONE,
            StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY to StimulusTargetNumericAuthority.DIRECTION_ONLY,
            StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE to StimulusTargetNumericAuthority.DIRECTION_ONLY
        )
        strategies.forEach { (strategy, authority) ->
            val target = StimulusTargetPlanEngine().build(taskPortfolio(strategy), baseline(valid = true)).taskTargets.single()
            assertEquals(authority, target.numericAuthority)
            assertNull(target.weeklyDirectUnitsTarget)
            assertNull(target.weeklyDirectSessionsTarget)
        }
    }

    @Test
    fun legacyComparisonReportsTaskBaselineWasNotAdopted() {
        val canonical = StimulusTargetPlanEngine().build(
            taskPortfolio(StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY), baseline(valid = true)
        )
        val legacy = TargetStimulusPlan(
            qualityTargets = emptyList(),
            taskTargets = listOf(TaskStimulusTarget(
                task = "SMASH", action = TargetStimulusAction.HOLD_SUCCESSFUL_DOSE,
                priority = TargetPriority.MAINTENANCE, baseline = validBand(),
                targetDirectUnitsMin = 2.0, targetDirectUnitsPreferred = 3.0, targetDirectUnitsMax = 4.0,
                numericAuthority = TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                confidence = PlanningConfidence.MODERATE, explicitUserTaskPriority = true,
                reasonCodes = emptyList(), evidence = emptyList()
            )),
            decisionPortfolio = TrainingDecisionPortfolio(emptyList(), emptyList(), emptyList()),
            unresolved = emptyList()
        )
        val comparison = StimulusTargetPlanComparisonEngine().compare(legacy, canonical).taskComparisons.single()
        assertEquals(StimulusTargetComparisonStatus.DIFFERENT, comparison.status)
        assertTrue(comparison.reasonCodes.contains("LEGACY_TASK_NUMERIC_BASELINE_NOT_ADOPTED_IN_B4"))
        assertTrue(comparison.reasonCodes.contains("CANONICAL_TASK_REMAINS_DIRECTION_ONLY"))
    }

    @Test
    fun controlAuditNormalizesWholeProgramCoverageToWeeklyValues() {
        val plan = StimulusTargetPlanEngine().build(portfolio(StimulusDoseStrategy.HOLD_PERSONAL_BASELINE), baseline(valid = true))
        val audit = StimulusTargetControlProgramAuditEngine().audit(
            plan,
            FinalStimulusNeedAuditResult(finalQualityCoverage = mapOf(
                TrainableQuality.STRENGTH to FinalStimulusNeedEvidence(
                    directUnits = 12, directSessions = 4, directExposureWeeks = 2
                )
            )),
            planningHorizonWeeks = 2
        )
        val quality = audit.qualityAudits.single()
        assertEquals(6.0, requireNotNull(quality.plannedWeeklyDirectUnits), 0.0)
        assertEquals(2.0, requireNotNull(quality.plannedWeeklyDirectSessions), 0.0)
        assertEquals(StimulusTargetControlStatus.WITHIN_BAND, quality.weeklyDirectUnitsStatus)
        assertEquals(StimulusTargetControlStatus.WITHIN_BAND, quality.weeklyDirectSessionsStatus)
        assertEquals(1.0, requireNotNull(quality.plannedExposureWeekFrequency), 0.0)
        assertTrue(quality.reasonCodes.contains("EXPOSURE_FREQUENCY_IS_REFERENCE_ONLY_IN_B4"))
    }

    private fun portfolio(strategy: StimulusDoseStrategy): StimulusTrainingDecisionPortfolio = StimulusTrainingDecisionPortfolio(
        qualityDecisions = listOf(StimulusQualityTrainingDecision(
            quality = TrainableQuality.STRENGTH, relevance = NeedRelevance.HIGH,
            needDecision = TrainingNeedDecision.MAINTAIN, strategy = strategy,
            priority = TargetPriority.PRIMARY, needConfidence = PlanningConfidence.MODERATE,
            baselineConfidence = PlanningConfidence.LOW, baselineAvailable = true,
            hasPersonalDirectBaseline = true, baselineSource = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
            baselineEligibleWeekCount = 4, baselineDirectUnitsMedian = 6.0,
            baselineDirectSessionsMedian = 2.0, baselineExposureWeekFrequency = 0.75,
            reasonCodes = emptyList(), evidence = emptyList()
        )),
        taskDecisions = emptyList(), unresolved = emptyList()
    )

    private fun taskPortfolio(strategy: StimulusDoseStrategy): StimulusTrainingDecisionPortfolio = StimulusTrainingDecisionPortfolio(
        qualityDecisions = emptyList(),
        taskDecisions = listOf(StimulusTaskTrainingDecision(
            task = "SMASH", relevance = NeedRelevance.HIGH, needDecision = TrainingNeedDecision.MAINTAIN,
            strategy = strategy, priority = TargetPriority.MAINTENANCE, needConfidence = PlanningConfidence.MODERATE,
            reasonCodes = emptyList(), evidence = emptyList()
        )), unresolved = emptyList()
    )

    private fun baseline(
        valid: Boolean,
        available: Boolean = true,
        supportiveOnly: Boolean = false,
        band: SuccessfulDoseBand = validBand(supportiveOnly)
    ): LedgerBackedQualityDoseHistory {
        val actualBand = if (valid) band else band.copy(
            source = SuccessfulDoseSource.NO_PERSONAL_BASELINE,
            directExposureWeekCount = 0,
            directUnitsMedian = 0.0,
            weeklyDirectUnitsMedian = 0.0
        )
        return LedgerBackedQualityDoseHistory(
            bands = TrainableQuality.entries.associateWith { actualBand },
            weeklyEvidence = emptyMap(), indexedWeekCount = 8, excludedWeekCount = 0,
            horizon = qualityDoseHistoryHorizon(LocalDate.of(2026, 9, 20)), comparisons = emptyMap(), available = available,
            reasonCodes = emptyList()
        )
    }

    private fun validBand(supportiveOnly: Boolean = false) = SuccessfulDoseBand(
        eligibleWeekCount = 4,
        directUnitsQ25 = if (supportiveOnly) 0.0 else 4.0,
        directUnitsMedian = if (supportiveOnly) 0.0 else 6.0,
        directUnitsQ75 = if (supportiveOnly) 0.0 else 9.0,
        directSessionsQ25 = if (supportiveOnly) 0.0 else 1.0,
        directSessionsMedian = if (supportiveOnly) 0.0 else 2.0,
        directSessionsQ75 = if (supportiveOnly) 0.0 else 3.0,
        supportiveUnitsMedian = if (supportiveOnly) 6.0 else 0.0,
        source = if (supportiveOnly) SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK else SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS,
        weeklyDirectUnitsQ25 = if (supportiveOnly) 0.0 else 4.0,
        weeklyDirectUnitsMedian = if (supportiveOnly) 0.0 else 6.0,
        weeklyDirectUnitsQ75 = if (supportiveOnly) 0.0 else 9.0,
        weeklyDirectSessionsQ25 = if (supportiveOnly) 0.0 else 1.0,
        weeklyDirectSessionsMedian = if (supportiveOnly) 0.0 else 2.0,
        weeklyDirectSessionsQ75 = if (supportiveOnly) 0.0 else 3.0,
        exposureWeekDirectUnitsQ25 = if (supportiveOnly) 0.0 else 5.0,
        exposureWeekDirectUnitsMedian = if (supportiveOnly) 0.0 else 7.0,
        exposureWeekDirectUnitsQ75 = if (supportiveOnly) 0.0 else 10.0,
        exposureWeekDirectSessionsQ25 = if (supportiveOnly) 0.0 else 1.0,
        exposureWeekDirectSessionsMedian = if (supportiveOnly) 0.0 else 2.0,
        exposureWeekDirectSessionsQ75 = if (supportiveOnly) 0.0 else 2.0,
        directExposureWeekCount = if (supportiveOnly) 0 else 3,
        directExposureWeekFrequency = if (supportiveOnly) 0.0 else 0.75
    )
}
