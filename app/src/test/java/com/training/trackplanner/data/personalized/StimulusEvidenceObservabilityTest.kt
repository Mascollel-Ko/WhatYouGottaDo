package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Focused regression coverage for the typed A–B5 evidence and authority boundaries. */
class StimulusEvidenceObservabilityTest {
    private val cutoff = LocalDate.of(2026, 9, 23)

    @Test
    fun reviewedSourceWithoutPhysicalQualityRelationIsIrrelevantToQualityViews() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val ledger = ledger(listOf(observation("reviewed-zero", 1, horizon.newestCompletedWeekEnd,
            StimulusClassificationAuthority.REVIEWED_CANONICAL)), mapOf("reviewed-zero" to profile("reviewed-zero")))
        val snapshot = snapshot(ledger)
        val evidence = StimulusNeedEvidenceIndexBuilder().build(snapshot)
        TrainableQuality.entries.forEach { quality ->
            val value = evidence.qualityEvidence.getValue(quality)
            assertEquals(0, value.current28d.classifiedSourceUnits)
            assertEquals(0, value.current28d.unclassifiedSourceUnits)
            assertEquals(0, value.current28d.directUnits)
            assertEquals(0, value.current28d.supportiveUnits)
        }
        val baseline = analyze(snapshot)
        TrainableQuality.entries.forEach { quality ->
            assertEquals(DoseBaselineObservability.NO_ELIGIBLE_CLASSIFIED_HISTORY,
                baseline.baselineObservability.getValue(quality))
            assertTrue(baseline.weeklyEvidence.getValue(quality).all { !it.hasSourceObservations })
        }
    }

    @Test
    fun reviewedNonRealizationIsAValidStrengthZero() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val key = "reviewed-zero"
        val reviewedZero = observation(key, 1, horizon.newestCompletedWeekEnd,
            StimulusClassificationAuthority.REVIEWED_CANONICAL).copy(
            realizedStimulusClassification = RealizedStimulusClassification.reviewedNonRealization()
        )
        val relationProfile = profile(key, relation(key, TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val baseline = analyze(snapshot(ledger(listOf(reviewedZero), mapOf(key to relationProfile))))
        val week = baseline.weeklyEvidence.getValue(TrainableQuality.STRENGTH).first { it.hasSourceObservations }
        assertEquals(1, week.classifiedSourceUnits)
        assertEquals(0, week.unclassifiedRelevantUnits)
        assertEquals(0, week.directUnits)
        assertEquals(1, week.excludedDirectByPrescriptionUnits)
        assertTrue(week.classificationComplete)
        assertTrue(week.eligibleForNumericBaseline)
        assertEquals(DoseBaselineObservability.COMPLETE, baseline.baselineObservability.getValue(TrainableQuality.STRENGTH))
    }

    @Test
    fun availableLedgerWithNoSourceWeeksIsNotAReviewedZero() {
        val baseline = analyze(snapshot(ledger(emptyList(), emptyMap())))
        assertEquals(DoseBaselineObservability.NO_ELIGIBLE_CLASSIFIED_HISTORY,
            baseline.baselineObservability.getValue(TrainableQuality.STRENGTH))
        assertEquals(0, baseline.classificationCompleteWeekCount)
        val decision = portfolio(TrainingNeedDecision.DEVELOP, baseline).qualityDecisions.single()
        assertEquals(StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY, decision.strategy)
        assertTrue(decision.reasonCodes.contains("NO_ELIGIBLE_CLASSIFIED_BASELINE_HISTORY"))
        assertFalse(decision.reasonCodes.contains("VALID_COMPLETED_WEEK_HISTORY"))
        assertFalse(decision.reasonCodes.contains("VALID_REVIEWED_ZERO_BASELINE_HISTORY"))
    }

    @Test
    fun excludedIncompleteSourceWeekStaysVisibleButDoesNotContaminateCompleteness() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val excludedStart = horizon.completedWeekEnds[1].minusDays(6)
        val observations = listOf(
            observation("direct", 1, horizon.newestCompletedWeekEnd, StimulusClassificationAuthority.REVIEWED_CANONICAL),
            observation("custom", 2, horizon.completedWeekEnds[1], StimulusClassificationAuthority.UNCLASSIFIED)
        )
        val profiles = mapOf(
            "direct" to profile("direct", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)),
            "custom" to profile("custom", relation("custom", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        )
        val baseline = analyze(snapshot(ledger(observations, profiles)), stateWithExcludedWeek(excludedStart))
        assertEquals(DoseBaselineObservability.COMPLETE, baseline.baselineObservability.getValue(TrainableQuality.STRENGTH))
        assertEquals(1, baseline.classificationCompleteSourceWeekCount.getValue(TrainableQuality.STRENGTH))
        assertEquals(0, baseline.classificationIncompleteSourceWeekCount.getValue(TrainableQuality.STRENGTH))
        assertEquals(6, baseline.emptyCompletedWeekCount)
        assertTrue(baseline.weeklyEvidence.getValue(TrainableQuality.STRENGTH).any {
            it.excludedFromBaseline && it.unclassifiedRelevantUnits > 0
        })
    }

    @Test
    fun observedDirectBaselineSurvivesPartialClassificationButNumericAuthorityDoesNot() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val observations = listOf(
            observation("direct", 1, horizon.completedWeekEnds[0], StimulusClassificationAuthority.REVIEWED_CANONICAL),
            observation("direct", 2, horizon.completedWeekEnds[1], StimulusClassificationAuthority.REVIEWED_CANONICAL),
            observation("custom", 3, horizon.completedWeekEnds[2], StimulusClassificationAuthority.UNCLASSIFIED)
        )
        val profiles = mapOf(
            "direct" to profile("direct", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)),
            "custom" to profile("custom", relation("custom", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        )
        val baseline = analyze(snapshot(ledger(observations, profiles)))
        assertEquals(DoseBaselineObservability.PARTIAL_UNCLASSIFIED, baseline.baselineObservability.getValue(TrainableQuality.STRENGTH))
        assertTrue(baseline.bands.getValue(TrainableQuality.STRENGTH).hasPersonalDirectBaseline)
        val decision = portfolio(TrainingNeedDecision.DEVELOP, baseline).qualityDecisions.single()
        assertTrue(decision.observedPersonalDirectBaseline)
        assertFalse(decision.numericBaselineUsable)
        assertEquals(PlanningConfidence.LOW, decision.baselineConfidence)
        assertEquals(StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY, decision.strategy)
        assertTrue(decision.reasonCodes.contains("OBSERVED_PERSONAL_DIRECT_BASELINE_PRESENT"))
        assertTrue(decision.reasonCodes.contains("NUMERIC_BASELINE_AUTHORITY_WITHHELD_DUE_TO_PARTIAL_CLASSIFICATION"))
        assertFalse(decision.reasonCodes.contains("NO_PERSONAL_DIRECT_BASELINE"))

        val target = StimulusTargetPlanEngine().build(
            portfolio(TrainingNeedDecision.DEVELOP, baseline), baseline
        ).qualityTargets.single()
        assertTrue(target.hasPersonalDirectBaseline)
        assertFalse(target.numericBaselineUsable)
        assertEquals(StimulusTargetNumericAuthority.DIRECTION_ONLY, target.numericAuthority)
        assertEquals(PlanningConfidence.LOW, target.baselineConfidence)
    }

    @Test
    fun incompletePriorWindowWithholdsExactTrendComparison() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val observations = buildList {
            repeat(5) { add(observation("direct", it.toLong() + 1, cutoff.minusDays(2), StimulusClassificationAuthority.REVIEWED_CANONICAL)) }
            repeat(2) { add(observation("direct", it.toLong() + 20, cutoff.minusDays(35), StimulusClassificationAuthority.REVIEWED_CANONICAL)) }
            repeat(3) { add(observation("custom", it.toLong() + 40, cutoff.minusDays(35), StimulusClassificationAuthority.UNCLASSIFIED)) }
        }
        val profiles = mapOf(
            "direct" to profile("direct", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)),
            "custom" to profile("custom", relation("custom", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        )
        val need = AthleteStimulusNeedEngine().analyze(snapshot(ledger(observations, profiles)), emptyState())
        val strength = need.qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(5, strength.exposure.current28d.directUnits)
        assertEquals(2, strength.exposure.prior28d.directUnits)
        assertEquals(3, strength.exposure.prior28d.unclassifiedSourceUnits)
        assertEquals(StimulusEvidenceCoverage.PARTIAL, strength.exposure.coverage)
        assertEquals(ExposureState.ESTABLISHED, strength.exposure.currentExposure)
        assertEquals(PlanningConfidence.LOW, strength.confidence)
        assertTrue(strength.reasonCodes.contains("CURRENT_PRIOR_TREND_COMPARISON_WITHHELD"))
        assertTrue(strength.reasonCodes.contains("CURRENT_WINDOW_ONLY_EXPOSURE_STATE_USED"))
    }

    @Test
    fun taskEvidenceBasisPropagatesFromB1ThroughB3AndB4() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val taskProfile = profile("task", objectives = listOf(
            CanonicalBadmintonObjectiveRelation("task", "task", BadmintonObjective.DECELERATION,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("TEST"), "TEST")
        ))
        val observation = observation("task", 1, horizon.newestCompletedWeekEnd, StimulusClassificationAuthority.UNCLASSIFIED,
            activity = PlannedActivityKind.STRUCTURED_BADMINTON_DRILL)
        val state = emptyState().copy(badmintonIntent = BadmintonPlanningIntent.ENABLED)
        val need = AthleteStimulusNeedEngine().analyze(snapshot(ledger(listOf(observation), mapOf("task" to taskProfile))), state)
        val taskNeed = need.sportTaskNeeds.first { it.task == BadmintonObjective.DECELERATION.name }
        assertEquals(StimulusEvidenceBasis.UNCLASSIFIED, taskNeed.exposure.evidenceBasis)
        val baseline = analyze(snapshot(ledger(listOf(observation), mapOf("task" to taskProfile))), state)
        val portfolio = StimulusTrainingDecisionPortfolioEngine().build(need, baseline)
        val decision = portfolio.taskDecisions.first { it.task == taskNeed.task }
        assertEquals(StimulusEvidenceBasis.UNCLASSIFIED, decision.evidenceBasis)
        val target = StimulusTargetPlanEngine().build(portfolio, baseline).taskTargets.first { it.task == taskNeed.task }
        assertEquals(StimulusEvidenceBasis.UNCLASSIFIED, target.evidenceBasis)
        assertFalse(target.prescriptionRealizationAuthority)
    }

    @Test
    fun reviewedSourceSeparatesStrengthRealizationFromCapabilityProxy() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val source = observation("mixed", 99, horizon.newestCompletedWeekEnd, StimulusClassificationAuthority.REVIEWED_CANONICAL)
            .copy(realizedStimulusClassification = RealizedStimulusClassification.UNCLASSIFIED)
        val profile = profile("mixed").copy(physicalQualities = listOf(
            relation("mixed-strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("mixed-power", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        ))
        val snapshot = snapshot(ledger(listOf(source), mapOf("mixed" to profile)))
        val evidence = StimulusNeedEvidenceIndexBuilder().build(snapshot)
        val strength = evidence.qualityEvidence.getValue(TrainableQuality.STRENGTH)
        val power = evidence.qualityEvidence.getValue(TrainableQuality.POWER)
        assertEquals(0, strength.classifiedSourceUnits)
        assertEquals(1, strength.unclassifiedSourceUnits)
        assertEquals(0, strength.current28d.directUnits)
        assertEquals(0, strength.current28d.excludedDirectByPrescriptionUnits)
        assertFalse(strength.reasonCodes.any { it.contains("PRESCRIPTION_INCOMPATIBLE") })
        assertEquals(1, power.classifiedSourceUnits)
        assertEquals(0, power.unclassifiedSourceUnits)
        assertEquals(1, power.current28d.directUnits)
        assertEquals(StimulusEvidenceCoverage.COMPLETE, power.coverage)

        val baseline = analyze(snapshot)
        assertEquals(0, baseline.weeklyEvidence.getValue(TrainableQuality.STRENGTH).sumOf { it.directUnits })
        assertEquals(1, baseline.weeklyEvidence.getValue(TrainableQuality.STRENGTH).sumOf { it.unclassifiedRelevantUnits })
        assertEquals(DoseBaselineObservability.PARTIAL_UNCLASSIFIED,
            baseline.baselineObservability.getValue(TrainableQuality.STRENGTH))
        assertTrue(baseline.weeklyEvidence.getValue(TrainableQuality.POWER).sumOf { it.directUnits } > 0)
        assertEquals(DoseBaselineObservability.COMPLETE, baseline.baselineObservability.getValue(TrainableQuality.POWER))
    }

    @Test
    fun taskRelationRemainsIndependentWhenPhysicalQualityRelationIsAbsent() {
        val horizon = qualityDoseHistoryHorizon(cutoff)
        val taskProfile = profile("task-only", objectives = listOf(
            CanonicalBadmintonObjectiveRelation("task-only", "task-only", BadmintonObjective.DECELERATION,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("TEST"), "TEST")
        ))
        val source = observation("task-only", 1, horizon.newestCompletedWeekEnd,
            StimulusClassificationAuthority.REVIEWED_CANONICAL, PlannedActivityKind.STRUCTURED_BADMINTON_DRILL)
        val snapshot = snapshot(ledger(listOf(source), mapOf("task-only" to taskProfile)))
        val evidence = StimulusNeedEvidenceIndexBuilder().build(snapshot)
        assertEquals(1, evidence.taskEvidence.getValue(BadmintonObjective.DECELERATION.name).current28d.directUnits)
        assertTrue(TrainableQuality.entries.all { evidence.qualityEvidence.getValue(it).current28d.classifiedSourceUnits == 0 })
    }

    private fun analyze(snapshot: PlanningHistorySnapshot, state: AthletePlanningState = emptyState()) =
        LedgerBackedQualityDoseHistoryAnalyzer().analyze(
            snapshot, state,
            QualityDoseHistoryAnalyzer().analyze(snapshot, state, CanonicalExercisePhysicalQualityCatalog.EMPTY)
        )

    private fun portfolio(decision: TrainingNeedDecision, baseline: LedgerBackedQualityDoseHistory) =
        StimulusTrainingDecisionPortfolioEngine().build(
            AthleteStimulusNeedProfile(
                generatedAtCutoff = cutoff,
                qualityNeeds = listOf(AthleteStimulusQualityNeed(
                    TrainableQuality.STRENGTH, NeedRelevance.HIGH, StimulusExposureEvidence(),
                    TrainingResponseState.INSUFFICIENT_EVIDENCE, decision, PlanningConfidence.MODERATE
                )),
                sportTaskNeeds = emptyList(), courtContext = CourtContextEvidence()
            ), baseline
        )

    private fun snapshot(ledger: StimulusExposureLedger) = PlanningHistorySnapshot(
        cutoff = cutoff, allConfirmedSets = emptyList(), exercises = emptyMap(), metadata = emptyMap(),
        badmintonObjectives = emptyMap(), profilePrimaryGoal = "STRENGTH_GAIN", strengthTrainingYears = 1.0,
        badmintonTrainingYears = 0.0, preferences = PersonalizedPlanningPreferences(), stimulusExposureLedger = ledger
    )

    private fun ledger(observations: List<StimulusSetObservation>, profiles: Map<String, CanonicalStimulusFacetProfile>) =
        StimulusExposureLedger(profiles, observations, emptyList(), cutoff, cutoff.minusDays(55))

    private fun profile(key: String, relation: ExercisePhysicalQualityRelation? = null,
        objectives: List<CanonicalBadmintonObjectiveRelation> = emptyList()) =
        CanonicalStimulusFacetProfile(key, physicalQualities = listOfNotNull(relation), badmintonObjectives = objectives,
            integrity = StimulusFacetIntegrity.CONSISTENT)

    private fun relation(id: String, quality: TrainableQuality, level: StimulusCapabilityLevel) = ExercisePhysicalQualityRelation(
        id, id, quality, level, PhysicalQualityRegion.LOWER, PhysicalQualityMode.GENERAL, true,
        "TEST", setOf("TEST"), "PASS", "TEST")

    private fun observation(key: String, id: Long, date: LocalDate, authority: StimulusClassificationAuthority,
        activity: PlannedActivityKind = PlannedActivityKind.RESISTANCE) = StimulusSetObservation(
        StimulusSourceRef(id, "backup-$id", id, id.toInt(), "session-$id", date, key), activity, 5, 50.0,
        0, 8.0, RealizedStimulusClass.STRENGTH_LIKE, key, authority)

    private fun stateWithExcludedWeek(start: LocalDate) = emptyState().copy(
        trainingStateAssessment = TrainingStateAssessment(
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
    )

    private fun emptyState() = AthletePlanningState(
        ObservedTrainingBehavior.UNKNOWN, StrengthExposure.PRESENT, StrengthIntent.STRENGTH_PRIORITY,
        BadmintonPlanningIntent.DISABLED, FreeWeightWillingness.UNRESOLVED, "MIXED", 56, 3.0, 0.0,
        0.0, 1.0, emptyList(), StrengthProgrammingStyle.UNRESOLVED, PlanningConfidence.LOW, 0,
        "NONE", PlanningConfidence.MODERATE
    )
}
