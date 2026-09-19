package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TargetStimulusPlanTest {
    private val band = SuccessfulDoseBand(
        eligibleWeekCount = 4,
        directUnitsQ25 = 8.0,
        directUnitsMedian = 12.0,
        directUnitsQ75 = 16.0,
        directSessionsQ25 = 1.0,
        directSessionsMedian = 2.0,
        directSessionsQ75 = 3.0,
        supportiveUnitsQ25 = 2.0,
        supportiveUnitsMedian = 4.0,
        supportiveUnitsQ75 = 6.0,
        confidence = PlanningConfidence.HIGH,
        source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS
    )

    @Test
    fun positiveStrengthHoldsSuccessfulDoseWithoutAutomaticVolumeIncrease() {
        val profile = profile(TrainableQuality.STRENGTH, NeedRelevance.HIGH, TrainingNeedDecision.MAINTAIN_OR_PROGRESS,
            TrainingResponseState.POSITIVE_RESPONSE)
        val portfolio = TrainingDecisionPortfolioEngine().build(profile, history(TrainableQuality.STRENGTH to band))
        val target = TargetStimulusPlanEngine().build(portfolio, history(TrainableQuality.STRENGTH to band))
            .qualityTargets.single()
        assertEquals(TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION, target.action)
        assertEquals(TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, target.numericAuthority)
        assertEquals(12.0, target.targetDirectUnitsPreferred!!, 0.0)
        assertTrue(target.reasonCodes.contains("PROGRESSION_DOES_NOT_REQUIRE_AUTOMATIC_VOLUME_INCREASE"))
    }

    @Test
    fun hypertrophyMaintainWithoutOutcomeUsesToleratedDoseOnly() {
        val profile = profile(TrainableQuality.HYPERTROPHY, NeedRelevance.HIGH, TrainingNeedDecision.MAINTAIN,
            TrainingResponseState.INSUFFICIENT_EVIDENCE)
        val target = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(profile, history(TrainableQuality.HYPERTROPHY to band)),
            history(TrainableQuality.HYPERTROPHY to band)
        ).qualityTargets.single()
        assertEquals(TargetStimulusAction.HOLD_SUCCESSFUL_DOSE, target.action)
        assertEquals(TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, target.numericAuthority)
        assertTrue(target.reasonCodes.contains("NO_DIRECT_HYPERTROPHY_OUTCOME_AUTHORITY"))
        assertTrue(target.reasonCodes.contains("REPEATEDLY_TOLERATED_EXPOSURE"))
    }

    @Test
    fun developWithBaselineRestoresAndNovelStimulusStaysDirectionOnly() {
        val withBaseline = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(profile(TrainableQuality.POWER, NeedRelevance.MODERATE, TrainingNeedDecision.DEVELOP),
                history(TrainableQuality.POWER to band)), history(TrainableQuality.POWER to band)
        ).qualityTargets.single()
        assertEquals(TargetStimulusAction.RESTORE_PERSONAL_BASELINE, withBaseline.action)
        assertEquals(TargetNumericAuthority.PERSONAL_RESTORE_BASELINE, withBaseline.numericAuthority)

        val novelHistory = history(TrainableQuality.POWER to band.copy(
            eligibleWeekCount = 1, directUnitsQ25 = null, directUnitsMedian = null, directUnitsQ75 = null,
            directSessionsQ25 = null, directSessionsMedian = null, directSessionsQ75 = null,
            source = SuccessfulDoseSource.NO_PERSONAL_BASELINE, confidence = PlanningConfidence.LOW))
        val novel = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(profile(TrainableQuality.POWER, NeedRelevance.MODERATE, TrainingNeedDecision.DEVELOP), novelHistory), novelHistory
        ).qualityTargets.single()
        assertEquals(TargetStimulusAction.INTRODUCE_DIRECT_STIMULUS, novel.action)
        assertEquals(TargetNumericAuthority.DIRECTION_ONLY, novel.numericAuthority)
        assertNull(novel.targetDirectUnitsPreferred)
        assertTrue(novel.reasonCodes.contains("NO_PERSONAL_DOSE_BASELINE"))
    }

    @Test
    fun noExtraNeedIsNotZeroAndNegativeResponseDoesNotInventReduction() {
        val noNeed = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(profile(TrainableQuality.POWER, NeedRelevance.NONE, TrainingNeedDecision.NO_EXTRA_NEED), history()), history()
        ).qualityTargets.single()
        assertEquals(TargetStimulusAction.NO_MINIMUM_TARGET, noNeed.action)
        assertNull(noNeed.targetDirectUnitsMin)
        assertEquals(TargetNumericAuthority.NONE, noNeed.numericAuthority)
        assertTrue(noNeed.reasonCodes.contains("INCIDENTAL_STIMULUS_ALLOWED"))

        val negative = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(profile(TrainableQuality.STRENGTH, NeedRelevance.HIGH, TrainingNeedDecision.REDUCE,
                TrainingResponseState.NEGATIVE_RESPONSE), history(TrainableQuality.STRENGTH to band)), history(TrainableQuality.STRENGTH to band)
        ).qualityTargets.single()
        assertEquals(TargetStimulusAction.REDUCE_OR_RESTRUCTURE, negative.action)
        assertEquals(TargetNumericAuthority.DIRECTION_ONLY, negative.numericAuthority)
        assertNull(negative.targetDirectUnitsPreferred)
        assertTrue(negative.reasonCodes.contains("NEGATIVE_RESPONSE_REQUIRES_CAUSE_INSPECTION"))
    }

    @Test
    fun supportiveExposureNeverBecomesDirectDoseAndTaskPriorityIsNotPrimary() {
        val task = TaskTrainingDecision("DECELERATION", TrainingNeedDecision.DEVELOP, TargetStimulusAction.INTRODUCE_DIRECT_STIMULUS,
            TargetPriority.SECONDARY, PlanningConfidence.MODERATE, false, listOf("NO_EXPLICIT_BADMINTON_TASK_PRIORITY"), emptyList())
        val portfolio = TrainingDecisionPortfolio(emptyList(), listOf(task), emptyList())
        val target = TargetStimulusPlanEngine().build(portfolio, QualityDoseHistory(emptyMap(), emptyMap(), 8, 0)).taskTargets.single()
        assertEquals(TargetPriority.SECONDARY, target.priority)
        assertEquals(TargetNumericAuthority.DIRECTION_ONLY, target.numericAuthority)
        assertNull(target.targetDirectUnitsPreferred)

        val maintenanceTask = task.copy(task = "FOOTWORK", needDecision = TrainingNeedDecision.MAINTAIN,
            action = TargetStimulusAction.HOLD_SUCCESSFUL_DOSE, priority = TargetPriority.MAINTENANCE)
        val maintenanceTarget = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolio(emptyList(), listOf(maintenanceTask), emptyList()),
            QualityDoseHistory(emptyMap(), mapOf("FOOTWORK" to band), 8, 0)
        ).taskTargets.single()
        assertEquals(TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, maintenanceTarget.numericAuthority)
        assertEquals(12.0, maintenanceTarget.targetDirectUnitsPreferred!!, 0.0)
    }

    @Test
    fun doseHistoryUsesCompletedActiveWeeksAndLeavesExcludedWeeksOut() {
        val cutoff = LocalDate.of(2026, 9, 19)
        val key = "squat"
        val rows = (1..4).flatMap { week ->
            val end = cutoff.with(java.time.DayOfWeek.SUNDAY).minusDays((week * 7).toLong())
            (1..(week + 1)).map { index -> PlanningSetRecord(end.minusDays(2), key, key, "RESISTANCE", index, 5, 80.0, 0, 7.0) }
        }
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH",
            analysisEligibility = com.training.trackplanner.data.MetadataTokenField.parse("STRENGTH_PROGRESS"),
            progressMetricType = "LOAD_REPS")
        val snapshot = PlanningHistorySnapshot(cutoff, rows, emptyMap(), mapOf(key to metadata), emptyMap(), "STRENGTH_GAIN", 1.0, 0.0,
            PersonalizedPlanningPreferences())
        val relation = ExercisePhysicalQualityRelation("squat-strength", key, TrainableQuality.STRENGTH,
            StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.LOWER, PhysicalQualityMode.GENERAL,
            true, "TEST", emptySet(), "PASS", "")
        val excludedStart = completeWeekStart(cutoff, 2)
        val history = QualityDoseHistoryAnalyzer().analyze(
            snapshot,
            emptyState().copy(trainingStateAssessment = assessmentWithExcludedWeek(excludedStart)),
            CanonicalExercisePhysicalQualityCatalog.of(listOf(relation))
        )
        assertEquals(8, history.indexedWeekCount)
        assertEquals(3, history.bands.getValue(TrainableQuality.STRENGTH).eligibleWeekCount)
        assertEquals(1, history.excludedWeekCount)
        assertEquals(SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, history.bands.getValue(TrainableQuality.STRENGTH).source)
    }

    @Test
    fun supportiveAndOverlappingQualityViewsStaySeparate() {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val directKey = "direct"
        val supportiveKey = "supportive"
        val rows = (0..3).flatMap { offset ->
            val end = completeEnd.minusDays((offset * 7).toLong())
            val direct = (1..3).map { index -> PlanningSetRecord(end.minusDays(2), directKey, directKey, "RESISTANCE", index, 5, 80.0, 0, 7.0) }
            val supportive = (1..6).map { index -> PlanningSetRecord(end.minusDays(1), supportiveKey, supportiveKey, "RESISTANCE", index, 8, 40.0, 0, 7.0) }
            direct + supportive
        }
        fun metadata(key: String) = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH",
            analysisEligibility = com.training.trackplanner.data.MetadataTokenField.parse("STRENGTH_PROGRESS"),
            progressMetricType = "LOAD_REPS")
        val snapshot = PlanningHistorySnapshot(cutoff, rows, emptyMap(), mapOf(directKey to metadata(directKey), supportiveKey to metadata(supportiveKey)),
            emptyMap(), "STRENGTH_GAIN", 1.0, 0.0, PersonalizedPlanningPreferences())
        fun relation(id: String, key: String, quality: TrainableQuality, level: StimulusCapabilityLevel) = ExercisePhysicalQualityRelation(
            id, key, quality, level, PhysicalQualityRegion.LOWER, PhysicalQualityMode.GENERAL,
            true, "TEST", emptySet(), "PASS", "")
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("strength-direct", directKey, TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("strength-supportive", supportiveKey, TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY),
            relation("hypertrophy-overlap", directKey, TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        val history = QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), catalog)
        val strength = history.bands.getValue(TrainableQuality.STRENGTH)
        val hypertrophy = history.bands.getValue(TrainableQuality.HYPERTROPHY)
        assertEquals(4, strength.eligibleWeekCount)
        assertEquals(3.0, strength.directUnitsMedian!!, 0.0)
        assertEquals(6.0, strength.supportiveUnitsMedian!!, 0.0)
        assertEquals(3.0, hypertrophy.directUnitsMedian!!, 0.0)
    }

    private fun profile(
        quality: TrainableQuality,
        relevance: NeedRelevance,
        decision: TrainingNeedDecision,
        response: TrainingResponseState = TrainingResponseState.INSUFFICIENT_EVIDENCE,
        reasons: List<String> = emptyList()
    ) = AthleteNeedsProfile(
        LocalDate.of(2026, 9, 19),
        listOf(QualityNeed(quality, relevance, ExposureState.ESTABLISHED, response, decision, PlanningConfidence.HIGH,
            reasons, emptyList())), emptyList(), emptyList(), emptyList(), AthleteNeedsEvidenceSummary()
    )

    private fun history(vararg entries: Pair<TrainableQuality, SuccessfulDoseBand>) =
        QualityDoseHistory(entries.toMap(), emptyMap(), 8, 0)

    private fun emptyState() = AthletePlanningState(
        ObservedTrainingBehavior.UNKNOWN, StrengthExposure.PRESENT, StrengthIntent.STRENGTH_PRIORITY,
        BadmintonPlanningIntent.DISABLED, FreeWeightWillingness.UNRESOLVED, "MIXED", 56, 3.0, 0.0,
        0.0, 1.0, emptyList(), StrengthProgrammingStyle.UNRESOLVED, PlanningConfidence.LOW, 0,
        "NONE", PlanningConfidence.MODERATE
    )

    private fun completeWeekStart(cutoff: LocalDate, offset: Int): LocalDate =
        completedTrainingWeekEnd(cutoff).minusDays((offset * 7L) + 6L)

    private fun assessmentWithExcludedWeek(start: LocalDate) = TrainingStateAssessment(
        strain = LongitudinalStrainProfile(null, null, null, null, null, null, null, null, null, null, 0, 0, 0, null),
        adaptation = LongitudinalAdaptationProfile(emptyList(), emptyMap(), null, null, null, null),
        tolerance = WorkloadToleranceProfile(ToleranceWindow(null, null, null), ToleranceWindow(null, null, null), null, null, null, null, null),
        weeklyContext = listOf(WeeklyWorkloadEvidence(start, start.plusDays(6), 0, 0.0, 0, 0.0, null, null, null, null,
            excludedFromTolerance = true)),
        sustainable = SustainableWorkloadEvidence(null, null, null, 0, 0, 0, PlanningConfidence.LOW, emptyList(), null, null, false, false, emptyList()),
        strainScore = null, productiveEvidence = null, maladaptationEvidence = null, positiveBreadth = null, negativeBreadth = null,
        state = TrainingState.STABLE, confidence = PlanningConfidence.LOW, globalDoseFactor = 1.0, globalHardRestriction = false,
        hardRestrictionCodes = emptyList(), reasonCodes = emptyList()
    )
}
