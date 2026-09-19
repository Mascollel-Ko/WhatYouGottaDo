package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
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
        val supportiveOnly = SuccessfulDoseBand(
            eligibleWeekCount = 4,
            directUnitsMedian = 0.0,
            source = SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK
        )
        assertTrue(!supportiveOnly.hasPersonalDirectBaseline)

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
    fun completelyInactiveWeekIsExcludedFromEligibleActiveWeeks() {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val rows = (0..7).filter { it != 3 }.map { offset ->
            PlanningSetRecord(
                completeEnd.minusDays(offset * 7L + 2L),
                "anchor", "anchor", "RESISTANCE", 1, 5, 80.0, 0, 7.0
            )
        }
        val snapshot = snapshot(cutoff, rows, mapOf("anchor" to metadata("anchor")))
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("anchor", "anchor", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        val history = QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), catalog)
        assertEquals(8, history.indexedWeekCount)
        assertEquals(7, history.bands.getValue(TrainableQuality.STRENGTH).eligibleWeekCount)
        assertEquals(0, history.excludedWeekCount)
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
            relation("power-direct", directKey, TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("power-supportive", supportiveKey, TrainableQuality.POWER, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY),
            relation("rfd-overlap", directKey, TrainableQuality.RAPID_FORCE_PRODUCTION, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        val history = QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), catalog)
        val power = history.bands.getValue(TrainableQuality.POWER)
        val rfd = history.bands.getValue(TrainableQuality.RAPID_FORCE_PRODUCTION)
        assertEquals(4, power.eligibleWeekCount)
        assertEquals(3.0, power.directUnitsMedian!!, 0.0)
        assertEquals(6.0, power.supportiveUnitsMedian!!, 0.0)
        assertEquals(3.0, rfd.directUnitsMedian!!, 0.0)
    }

    @Test
    fun taskBandsUseObjectiveValuesInsteadOfExerciseStableKeys() {
        val cutoff = LocalDate.of(2026, 9, 19)
        val key = "decel_drill"
        val row = PlanningSetRecord(completedTrainingWeekEnd(cutoff).minusDays(2), key, key, "RESISTANCE", 1, 8, 40.0, 0, 7.0)
        val courtKey = "generic_court"
        val courtRow = row.copy(stableKey = courtKey, exerciseName = courtKey)
        val snapshot = snapshot(cutoff, listOf(row, courtRow), mapOf(
            key to metadata(key), courtKey to metadata(courtKey).copy(activityKind = "SPORT_SESSION")
        ), directObjectives = mapOf(key to setOf("DECELERATION"), courtKey to setOf("DECELERATION")))
        val history = QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), CanonicalExercisePhysicalQualityCatalog.EMPTY)
        assertTrue(history.taskBands.containsKey("DECELERATION"))
        assertTrue(!history.taskBands.containsKey("decel_drill"))
        assertEquals(1, history.taskBands.getValue("DECELERATION").directExposureWeekCount)
    }

    @Test
    fun intermittentPowerPreservesWeeklyZerosAndHistoricalFrequency() {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val rows = (0..7).flatMap { offset ->
            val end = completeEnd.minusDays(offset * 7L)
            val anchor = PlanningSetRecord(end.minusDays(2), "anchor", "anchor", "RESISTANCE", 1, 5, 80.0, 0, 7.0)
            val power = if (offset % 2 == 0) (1..4).map { index ->
                PlanningSetRecord(end.minusDays(2), "power", "power", "RESISTANCE", index, 3, 50.0, 0, 7.0)
            } else emptyList()
            listOf(anchor) + power
        }
        val snapshot = snapshot(cutoff, rows, mapOf("anchor" to metadata("anchor"), "power" to metadata("power")))
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("power", "power", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("anchor", "anchor", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        val history = QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), catalog)
        val band = history.bands.getValue(TrainableQuality.POWER)
        assertEquals(8, band.eligibleWeekCount)
        assertEquals(4, band.directExposureWeekCount)
        assertEquals(0.5, band.directExposureWeekFrequency!!, 0.0)
        assertEquals(4.0, band.exposureWeekDirectUnitsMedian!!, 0.0)
        assertEquals(0.0, band.weeklyDirectUnitsQ25!!, 0.0)
        assertTrue(band.hasPersonalDirectBaseline)

        val target = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(
                profile(TrainableQuality.POWER, NeedRelevance.MODERATE, TrainingNeedDecision.MAINTAIN), history), history
        ).qualityTargets.single()
        assertEquals(0.0, target.targetWeeklyDirectUnitsMin!!, 0.0)
        assertEquals(4.0, target.targetExposureWeekDirectUnitsPreferred!!, 0.0)
        assertEquals(0.5, target.targetExposureWeekFrequency!!, 0.0)
    }

    @Test
    fun continuousStrengthPreservesWeeklyAndExposureWeekDose() {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val counts = listOf(12, 12, 11, 13, 12, 12, 13, 11)
        val rows = counts.flatMapIndexed { offset, count ->
            val end = completeEnd.minusDays(offset * 7L)
            (1..count).map { index -> PlanningSetRecord(end.minusDays(2), "strength", "strength", "RESISTANCE", index, 3, 100.0, 0, 7.0) }
        }
        val snapshot = snapshot(cutoff, rows, mapOf("strength" to metadata("strength")))
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("strength", "strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        val band = QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), catalog).bands.getValue(TrainableQuality.STRENGTH)
        assertEquals(8, band.eligibleWeekCount)
        assertEquals(8, band.directExposureWeekCount)
        assertEquals(1.0, band.directExposureWeekFrequency!!, 0.0)
        assertEquals(12.0, band.weeklyDirectUnitsMedian!!, 0.0)
        assertEquals(12.0, band.exposureWeekDirectUnitsMedian!!, 0.0)
        assertTrue(band.hasPersonalDirectBaseline)
    }

    @Test
    fun intermittentComparisonRequiresHistoricalExposureFrequencyAndDose() {
        val history = listOf(4, 0, 4, 0, 4, 0, 4, 0)
        val zero = comparePower(history, List(8) { 0 })
        assertEquals(FrequencyComparisonStatus.BELOW_FREQUENCY, zero.frequencyStatus)
        assertEquals(TargetComparisonStatus.BELOW_TARGET_BAND, zero.status)
        assertEquals(0, zero.plannedExposureWeekCount)

        val matched = comparePower(history, history)
        assertEquals(FrequencyComparisonStatus.WITHIN_FREQUENCY, matched.frequencyStatus)
        assertEquals(TargetComparisonStatus.WITHIN_TARGET_BAND, matched.exposureWeekDoseStatus)
        assertEquals(TargetComparisonStatus.WITHIN_TARGET_BAND, matched.status)
        assertEquals(4, matched.plannedExposureWeekCount)
        assertEquals(0.5, matched.plannedExposureWeekFrequency!!, 0.0)
        assertEquals(4.0, matched.plannedExposureWeekUnitsMedian!!, 0.0)
    }

    @Test
    fun sparseComparisonDoesNotTreatZeroWeeklyQuartilesAsZeroFrequencyTarget() {
        val history = listOf(4, 4, 0, 0, 0, 0, 0, 0)
        val zero = comparePower(history, List(8) { 0 })
        assertEquals(0.25, zero.historicalTargetExposureFrequency!!, 0.0)
        assertEquals(FrequencyComparisonStatus.BELOW_FREQUENCY, zero.frequencyStatus)
        assertTrue(zero.status != TargetComparisonStatus.WITHIN_TARGET_BAND)

        val compatible = comparePower(history, listOf(4, 4, 0, 0, 0, 0, 0, 0))
        assertEquals(FrequencyComparisonStatus.WITHIN_FREQUENCY, compatible.frequencyStatus)
        assertEquals(2, compatible.plannedExposureWeekCount)
    }

    @Test
    fun continuousComparisonSeparatesFullAndHalfFrequencyPlans() {
        val history = List(8) { 4 }
        val full = comparePower(history, history)
        assertEquals(FrequencyComparisonStatus.WITHIN_FREQUENCY, full.frequencyStatus)
        assertEquals(TargetComparisonStatus.WITHIN_TARGET_BAND, full.status)

        val half = comparePower(history, listOf(4, 0, 4, 0, 4, 0, 4, 0))
        assertEquals(FrequencyComparisonStatus.BELOW_FREQUENCY, half.frequencyStatus)
        assertEquals(TargetComparisonStatus.BELOW_TARGET_BAND, half.status)
    }

    @Test
    fun noMinimumTargetAllowsZeroExposureWithoutFrequencyTarget() {
        val fixture = powerHistory(List(8) { 4 })
        val history = fixture.history
        val plan = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(
                profile(TrainableQuality.POWER, NeedRelevance.NONE, TrainingNeedDecision.NO_EXTRA_NEED), history
            ), history
        )
        val comparison = TargetPlanComparisonEngine().compare(
            plan, generatedPowerProgram(List(8) { 0 }), fixture.snapshot, fixture.catalog
        ).qualityComparisons.single()
        assertEquals(FrequencyComparisonStatus.NO_FREQUENCY_TARGET, comparison.frequencyStatus)
        assertEquals(TargetComparisonStatus.NO_MINIMUM_TARGET, comparison.status)
    }

    @Test
    fun directionOnlyTargetDoesNotManufactureFrequency() {
        val fixture = powerHistory(List(8) { 0 })
        val history = QualityDoseHistory(emptyMap(), emptyMap(), 8, 0)
        val plan = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(
                profile(TrainableQuality.POWER, NeedRelevance.MODERATE, TrainingNeedDecision.DEVELOP), history
            ), history
        )
        val comparison = TargetPlanComparisonEngine().compare(
            plan, generatedPowerProgram(List(8) { 0 }), fixture.snapshot, fixture.catalog
        ).qualityComparisons.single()
        assertEquals(TargetNumericAuthority.DIRECTION_ONLY, plan.qualityTargets.single().numericAuthority)
        assertEquals(FrequencyComparisonStatus.NO_FREQUENCY_TARGET, comparison.frequencyStatus)
        assertEquals(TargetComparisonStatus.DIRECTION_ONLY, comparison.status)
    }

    @Test
    fun strengthAndHypertrophyHistoryUsesSharedPrescriptionShapeAndPowerKeepsSemanticExposure() {
        val threeRep = analyzePrescriptionHistory(3)
        assertEquals(4, threeRep.bands.getValue(TrainableQuality.STRENGTH).directExposureWeekCount)
        assertEquals(0, threeRep.bands.getValue(TrainableQuality.HYPERTROPHY).directExposureWeekCount)
        assertEquals(4, threeRep.bands.getValue(TrainableQuality.POWER).directExposureWeekCount)
        assertEquals(RealizedStimulusClass.STRENGTH_LIKE, provisionalRealizedStimulusClass(PlanningSetRecord(
            LocalDate.of(2026, 9, 12), "squat", "squat", "RESISTANCE", 1, 3, 100.0, 0, 7.0)))

        val twelveRep = analyzePrescriptionHistory(12)
        assertEquals(0, twelveRep.bands.getValue(TrainableQuality.STRENGTH).directExposureWeekCount)
        assertEquals(4, twelveRep.bands.getValue(TrainableQuality.HYPERTROPHY).directExposureWeekCount)
        assertEquals(4, twelveRep.bands.getValue(TrainableQuality.POWER).directExposureWeekCount)
        assertEquals(RealizedStimulusClass.HYPERTROPHY_LIKE, provisionalRealizedStimulusClass(PlanningSetRecord(
            LocalDate.of(2026, 9, 12), "squat", "squat", "RESISTANCE", 1, 12, 100.0, 0, 7.0)))

        val ambiguous = analyzePrescriptionHistory(20)
        assertEquals(0, ambiguous.bands.getValue(TrainableQuality.STRENGTH).directExposureWeekCount)
        assertEquals(0, ambiguous.bands.getValue(TrainableQuality.HYPERTROPHY).directExposureWeekCount)
        assertEquals(4, ambiguous.bands.getValue(TrainableQuality.POWER).directExposureWeekCount)
        assertEquals(RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS, provisionalRealizedStimulusClass(PlanningSetRecord(
            LocalDate.of(2026, 9, 12), "squat", "squat", "RESISTANCE", 1, 20, 100.0, 0, 7.0)))
        assertTrue(ambiguous.notes.contains("AMBIGUOUS_REALIZED_STIMULUS_IS_NOT_ASSIGNED_TO_STRENGTH_OR_HYPERTROPHY"))
    }

    private fun analyzePrescriptionHistory(reps: Int): QualityDoseHistory {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val rows = (0..3).flatMap { offset ->
            val date = completeEnd.minusDays(offset * 7L + 2L)
            listOf(PlanningSetRecord(date, "squat", "squat", "RESISTANCE", 1, reps, 100.0, 0, 7.0))
        }
        val snapshot = snapshot(cutoff, rows, mapOf("squat" to metadata("squat")))
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("squat-strength", "squat", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("squat-hypertrophy", "squat", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("squat-power", "squat", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        return QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), catalog)
    }

    private fun comparePower(historyCounts: List<Int>, plannedCounts: List<Int>): QualityTargetComparison {
        val fixture = powerHistory(historyCounts)
        val plan = TargetStimulusPlanEngine().build(
            TrainingDecisionPortfolioEngine().build(
                profile(TrainableQuality.POWER, NeedRelevance.MODERATE, TrainingNeedDecision.MAINTAIN), fixture.history
            ), fixture.history
        )
        return TargetPlanComparisonEngine().compare(
            plan, generatedPowerProgram(plannedCounts), fixture.snapshot, fixture.catalog
        ).qualityComparisons.single()
    }

    private fun powerHistory(counts: List<Int>): PowerHistoryFixture {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val rows = counts.flatMapIndexed { offset, count ->
            val end = completeEnd.minusDays(offset * 7L)
            val anchor = PlanningSetRecord(end.minusDays(2), "anchor", "anchor", "RESISTANCE", 1, 5, 80.0, 0, 7.0)
            val power = (1..count).map { index ->
                PlanningSetRecord(end.minusDays(2), "power", "power", "RESISTANCE", index, 3, 50.0, 0, 7.0)
            }
            listOf(anchor) + power
        }
        val snapshot = snapshot(cutoff, rows, mapOf("anchor" to metadata("anchor"), "power" to metadata("power")))
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            relation("power", "power", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        return PowerHistoryFixture(snapshot, catalog, QualityDoseHistoryAnalyzer().analyze(snapshot, emptyState(), catalog))
    }

    private fun generatedPowerProgram(weeklyCounts: List<Int>): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            "comparison", ProgramGoal.STRENGTH, 3, 60, emptySet(), "", 0.0, "AUTO",
            ProgramPeriodizationType.AUTO, weeklyCounts.size
        )
        val items = weeklyCounts.flatMapIndexed { weekIndex, count ->
            (1..count).map { index ->
                ProgramSkeletonItem(
                    localId = "w${weekIndex + 1}-$index", weekNumber = weekIndex + 1, dayOfWeek = 1,
                    orderIndex = index, exerciseStableKey = "power", exerciseName = "power", category = "TEST",
                    restSeconds = 60, prescription = "", setCount = 1, reps = 3, weightKg = 0.0, seconds = 0,
                    selectionReason = "TEST", weightSource = "TEST"
                )
            }
        }
        return GeneratedProgramSkeleton("comparison", weeklyCounts.size * 7, request,
            ProgramPeriodizationType.AUTO, emptyList(), items)
    }

    private data class PowerHistoryFixture(
        val snapshot: PlanningHistorySnapshot,
        val catalog: CanonicalExercisePhysicalQualityCatalog,
        val history: QualityDoseHistory
    )

    private fun snapshot(
        cutoff: LocalDate,
        rows: List<PlanningSetRecord>,
        metadata: Map<String, com.training.trackplanner.data.RuntimeExerciseMetadata>,
        directObjectives: Map<String, Set<String>> = emptyMap(),
        supportiveObjectives: Map<String, Set<String>> = emptyMap()
    ) = PlanningHistorySnapshot(
        cutoff = cutoff,
        allConfirmedSets = rows,
        exercises = emptyMap(),
        metadata = metadata,
        badmintonObjectives = emptyMap(),
        profilePrimaryGoal = "STRENGTH_GAIN",
        strengthTrainingYears = 1.0,
        badmintonTrainingYears = 0.0,
        preferences = PersonalizedPlanningPreferences(),
        badmintonDirectObjectives = directObjectives,
        badmintonSupportiveObjectives = supportiveObjectives
    )

    private fun metadata(key: String) = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
        activityKind = "EXERCISE",
        programSlot = "MAIN_LOWER_STRENGTH",
        analysisEligibility = com.training.trackplanner.data.MetadataTokenField.parse("STRENGTH_PROGRESS"),
        progressMetricType = "LOAD_REPS"
    )

    private fun relation(id: String, key: String, quality: TrainableQuality, level: StimulusCapabilityLevel) =
        ExercisePhysicalQualityRelation(id, key, quality, level, PhysicalQualityRegion.LOWER, PhysicalQualityMode.GENERAL,
            true, "TEST", emptySet(), "PASS", "")

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
