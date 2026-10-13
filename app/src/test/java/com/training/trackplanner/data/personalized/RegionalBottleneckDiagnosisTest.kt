package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalBottleneckDiagnosisTest {
    @Test
    fun lowExposureWithPositiveResponseSuppressesExposureAndMorphology() {
        val fixture = fixture(currentStrengthWeeks = setOf(0, 1), previousStrengthWeeks = emptySet(), signal = 6.0, includeHypertrophy = false)
        val diagnosis = analyze(fixture).single { it.region == MovementCoverage.LOWER_KNEE }
        assertEquals(TrainingResponseState.POSITIVE_RESPONSE, diagnosis.performanceResponse)
        assertEquals(RegionalLimitingFactor.NO_CLEAR_LIMITATION, diagnosis.primaryInterpretation)
        assertTrue(RegionalLimitingFactor.EXPOSURE_LIMITED !in diagnosis.limitingFactors)
        assertTrue(RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING !in diagnosis.limitingFactors)
    }

    @Test
    fun lowStrengthExposureWithStableResponseIsExposureLimited() {
        val fixture = fixture(currentStrengthWeeks = setOf(0), previousStrengthWeeks = setOf(4, 5, 6, 7), signal = 0.0, includeHypertrophy = false)
        val diagnosis = analyze(fixture).single { it.region == MovementCoverage.LOWER_KNEE }
        assertEquals(RegionalLimitingFactor.EXPOSURE_LIMITED, diagnosis.primaryInterpretation)
    }

    @Test
    fun recoveryRestrictionTakesPriorityAndSuppressesMorphology() {
        val fixture = fixture(currentStrengthWeeks = setOf(0, 1, 2, 3), previousStrengthWeeks = setOf(4, 5, 6, 7), signal = 0.0, includeHypertrophy = false)
        val diagnosis = analyze(fixture, recovery = true).single { it.region == MovementCoverage.LOWER_KNEE }
        assertEquals(RegionalLimitingFactor.RECOVERY_LIMITED, diagnosis.primaryInterpretation)
        assertTrue(RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING !in diagnosis.limitingFactors)
    }

    @Test
    fun specificExerciseDropIsDistinguishedFromRegionalExposure() {
        val fixture = fixture(currentStrengthWeeks = setOf(0, 1, 2, 3), previousStrengthWeeks = setOf(5, 6, 7), signal = 0.0, includeHypertrophy = false, includeSpecific = true)
        val diagnosis = analyze(fixture).single { it.region == MovementCoverage.LOWER_KNEE }
        assertEquals(SpecificityContinuity.REDUCED, diagnosis.specificityContinuity)
        assertEquals(RegionalLimitingFactor.SPECIFICITY_POSSIBLY_LIMITING, diagnosis.primaryInterpretation)
    }

    @Test
    fun adequateStrengthAndLowHypertrophySupportAllowsCappedMorphologyHypothesis() {
        val fixture = fixture(currentStrengthWeeks = setOf(0, 1, 2, 3), previousStrengthWeeks = setOf(4, 5, 6, 7), signal = 0.0, includeHypertrophy = true)
        val diagnosis = analyze(fixture).single { it.region == MovementCoverage.LOWER_KNEE }
        assertEquals(RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING, diagnosis.primaryInterpretation)
        assertTrue(diagnosis.confidence != PlanningConfidence.HIGH)
    }

    @Test
    fun insufficientPerformanceEvidenceDoesNotInferMorphology() {
        val fixture = fixture(currentStrengthWeeks = setOf(0, 1, 2, 3), previousStrengthWeeks = setOf(4, 5, 6, 7), signal = null, includeHypertrophy = true)
        val diagnosis = analyze(fixture).single { it.region == MovementCoverage.LOWER_KNEE }
        assertEquals(RegionalLimitingFactor.INSUFFICIENT_EVIDENCE, diagnosis.primaryInterpretation)
    }

    @Test
    fun lowExposureAndRecoveryCanRemainMultifactorial() {
        val fixture = fixture(currentStrengthWeeks = setOf(0), previousStrengthWeeks = setOf(4, 5, 6, 7), signal = 0.0, includeHypertrophy = false)
        val diagnosis = analyze(fixture, recovery = true).single { it.region == MovementCoverage.LOWER_KNEE }
        assertEquals(RegionalLimitingFactor.MULTIFACTORIAL, diagnosis.primaryInterpretation)
        assertTrue(RegionalLimitingFactor.EXPOSURE_LIMITED in diagnosis.limitingFactors)
        assertTrue(RegionalLimitingFactor.RECOVERY_LIMITED in diagnosis.limitingFactors)
    }

    @Test
    fun indexContainsSupportedRegionsAndKeepsZeroRegionalWeeksInWeeklyBand() {
        val fixture = fixture(currentStrengthWeeks = setOf(0, 2), previousStrengthWeeks = setOf(4, 6), signal = 0.0, includeHypertrophy = false)
        val index = RegionalEvidenceIndexBuilder().build(fixture.snapshot, fixture.state, fixture.catalog)
        assertEquals(10, index.regions.size)
        assertTrue(MovementCoverage.ARMS_BICEPS in index.regions)
        assertTrue(MovementCoverage.ARMS_TRICEPS in index.regions)
        assertTrue(MovementCoverage.UPPER_PULL in index.regions)
        val band = index.regions.getValue(MovementCoverage.LOWER_KNEE).strengthDoseBand
        assertEquals(8, band.eligibleWeekCount)
        assertEquals(4, band.directExposureWeekCount)
        assertEquals(0.5, band.directExposureWeekFrequency!!, 0.0)
        assertEquals(0.0, band.weeklyUnitsQ25!!, 0.0)
        assertEquals(4, index.indexedRowCount)
    }

    @Test
    fun currentAndPreviousWeeklyMediansIncludeObservedZeroWeeksButExposureMediansDoNot() {
        val fixture = fixture(
            currentStrengthWeeks = setOf(0), previousStrengthWeeks = setOf(4),
            signal = 0.0, includeHypertrophy = false
        )
        val band = RegionalEvidenceIndexBuilder().build(fixture.snapshot, fixture.state, fixture.catalog)
            .regions.getValue(MovementCoverage.LOWER_KNEE).strengthDoseBand

        assertEquals(4, band.current28dEligibleWeekCount)
        assertEquals(4, band.previous28dEligibleWeekCount)
        assertEquals(0.0, band.current28dWeeklyUnitsMedian!!, 0.0)
        assertEquals(1.0, band.current28dExposureWeekUnitsMedian!!, 0.0)
        assertEquals(0.0, band.previous28dWeeklyUnitsMedian!!, 0.0)
        assertEquals(1.0, band.previous28dExposureWeekUnitsMedian!!, 0.0)
    }

    @Test
    fun alternatingEightSetWeeksUseWholeWeekMedianAndKeepExposureMedianSeparate() {
        val fixture = fixture(
            currentStrengthWeeks = emptySet(), previousStrengthWeeks = emptySet(),
            signal = 0.0, includeHypertrophy = false
        )
        val completeEnd = completedTrainingWeekEnd(fixture.snapshot.cutoff)
        val withoutTargetRows = fixture.snapshot.allConfirmedSets.filter { it.stableKey != "barbell_back_squat" }
        val targetRows = setOf(0, 2).flatMap { offset ->
            val date = completeEnd.minusDays(offset * 7L).minusDays(2)
            (1..8).map { setIndex -> PlanningSetRecord(
                date, "barbell_back_squat", "barbell_back_squat", "RESISTANCE", setIndex, 5, 100.0, 0, 7.0
            ) }
        }
        val snapshot = fixture.snapshot.copy(allConfirmedSets = withoutTargetRows + targetRows)
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
        val band = RegionalEvidenceIndexBuilder().build(snapshot, state, fixture.catalog)
            .regions.getValue(MovementCoverage.LOWER_KNEE).strengthDoseBand

        assertEquals(4, band.current28dEligibleWeekCount)
        assertEquals(4.0, band.current28dWeeklyUnitsMedian!!, 0.0)
        assertEquals(8.0, band.current28dExposureWeekUnitsMedian!!, 0.0)
    }

    @Test
    fun unobservedOrExcludedWeeksAreNotInventedAsZeroDoseWeeks() {
        val fixture = fixture(
            currentStrengthWeeks = emptySet(), previousStrengthWeeks = setOf(4, 5, 6, 7),
            signal = 0.0, includeHypertrophy = false
        )
        val completeEnd = completedTrainingWeekEnd(fixture.snapshot.cutoff)
        val previousOnly = fixture.snapshot.copy(allConfirmedSets = fixture.snapshot.allConfirmedSets.filter { row ->
            val weekEnd = row.date.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
            java.time.temporal.ChronoUnit.DAYS.between(weekEnd, fixture.snapshot.cutoff).toInt() in 28..55
        })
        val previousState = AthletePlanningStateBuilder().build(previousOnly, PersonalizedPlanningAnswers())
        val previousBand = RegionalEvidenceIndexBuilder().build(previousOnly, previousState, fixture.catalog)
            .regions.getValue(MovementCoverage.LOWER_KNEE).strengthDoseBand
        assertEquals(0, previousBand.current28dEligibleWeekCount)
        assertNull(previousBand.current28dWeeklyUnitsMedian)
        assertEquals(4, previousBand.previous28dEligibleWeekCount)

        val excludedEnd = completeEnd.minusDays(7)
        val excludedStart = excludedEnd.minusDays(6)
        val baseAssessment = requireNotNull(fixture.state.trainingStateAssessment)
        val excludedContext = WeeklyWorkloadEvidence(
            start = excludedStart, end = excludedEnd, units = 1, minutes = 0.0, days = 1,
            courtLoad = 0.0, medianRpe = null, performanceResponse = null,
            negativeBreadth = null, rpeDrift = null, excludedFromTolerance = true
        )
        val excludedState = fixture.state.copy(trainingStateAssessment = baseAssessment.copy(
            weeklyContext = baseAssessment.weeklyContext + excludedContext
        ))
        val excludedBand = RegionalEvidenceIndexBuilder().build(fixture.snapshot, excludedState, fixture.catalog)
            .regions.getValue(MovementCoverage.LOWER_KNEE).strengthDoseBand
        assertEquals(3, excludedBand.current28dEligibleWeekCount)
    }

    @Test
    fun upperPullWeeklyDoseCombinesDirectionsBeforeComputingWeeklyStatistics() {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val horizontalKey = "upper-pull-horizontal"
        val verticalKey = "upper-pull-vertical"
        val anchorKey = "weekly-observation-anchor"
        val horizontal = Exercise(horizontalKey, horizontalKey, "RESISTANCE", equipment = "BODYWEIGHT")
        val vertical = Exercise(verticalKey, verticalKey, "RESISTANCE", equipment = "BODYWEIGHT")
        val anchor = Exercise(anchorKey, anchorKey, "RESISTANCE", equipment = "BODYWEIGHT")
        fun meta(exercise: Exercise, slot: String) = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            activityKind = "EXERCISE", programSlot = slot,
            analysisEligibility = MetadataTokenField.parse("HYPERTROPHY_VOLUME"), progressMetricType = "LOAD_REPS"
        )
        val metadata = mapOf(
            horizontalKey to meta(horizontal, "HORIZONTAL_PULL_STRENGTH"),
            verticalKey to meta(vertical, "VERTICAL_PULL_STRENGTH"),
            anchorKey to meta(anchor, "MAIN_UPPER_STRENGTH")
        )
        val rows = buildList {
            (0..7).forEach { offset ->
                val date = completeEnd.minusDays(offset * 7L).minusDays(2)
                add(PlanningSetRecord(date, anchorKey, anchorKey, "RESISTANCE", 1, 10, 20.0, 0, 8.0))
                if (offset == 0) repeat(3) { index ->
                    add(PlanningSetRecord(date, horizontalKey, horizontalKey, "RESISTANCE", index + 1, 10, 20.0, 0, 8.0))
                }
                if (offset == 1) repeat(4) { index ->
                    add(PlanningSetRecord(date, verticalKey, verticalKey, "RESISTANCE", index + 1, 10, 20.0, 0, 8.0))
                }
            }
        }
        val snapshot = PlanningHistorySnapshot(
            cutoff, rows, mapOf(horizontalKey to horizontal, verticalKey to vertical, anchorKey to anchor),
            metadata, emptyMap(), "HYPERTROPHY", 1.0, 0.0, PersonalizedPlanningPreferences()
        )
        fun relation(key: String) = ExercisePhysicalQualityRelation(
            "relation-$key", key, TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.UPPER_PULL, PhysicalQualityMode.GENERAL, true, "TEST", setOf("TEST"), "PASS", ""
        )
        val index = RegionalEvidenceIndexBuilder().build(
            snapshot, AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers()),
            CanonicalExercisePhysicalQualityCatalog.of(listOf(relation(horizontalKey), relation(verticalKey)))
        )

        val upper = index.regions.getValue(MovementCoverage.UPPER_PULL).hypertrophyDoseBand
        val horizontalBand = index.regions.getValue(MovementCoverage.HORIZONTAL_PULL).hypertrophyDoseBand
        val verticalBand = index.regions.getValue(MovementCoverage.VERTICAL_PULL).hypertrophyDoseBand
        assertEquals(8, upper.eligibleWeekCount)
        assertEquals(3.5, upper.exposureWeekUnitsMedian!!, 0.0)
        assertEquals(3.0, horizontalBand.exposureWeekUnitsMedian!!, 0.0)
        assertEquals(4.0, verticalBand.exposureWeekUnitsMedian!!, 0.0)
        assertEquals(1.5, upper.current28dWeeklyUnitsMedian!!, 0.0)
        assertEquals(3.5, upper.current28dExposureWeekUnitsMedian!!, 0.0)

        val b1PullRepresentation = MovementExposureRepresentationAnalyzer()
            .analyze(snapshot, hypertrophyOriented = true)
            .single { it.movementCoverage == MovementCoverage.UPPER_PULL.name }
        assertEquals(7.0, b1PullRepresentation.currentExposure28d, 0.0)
        assertFalse(b1PullRepresentation.reasonCodes.contains("CURRENT_EXPOSURE_ABSENT"))
    }

    private fun analyze(fixture: Fixture, recovery: Boolean = false): List<RegionalBottleneckDiagnosis> {
        val index = RegionalEvidenceIndexBuilder().build(fixture.snapshot, fixture.state, fixture.catalog)
        return RegionalBottleneckDiagnosisEngine().analyze(index, NeedRelevance.MODERATE, recovery, false)
    }

    private fun fixture(
        currentStrengthWeeks: Set<Int>,
        previousStrengthWeeks: Set<Int>,
        signal: Double?,
        includeHypertrophy: Boolean,
        includeSpecific: Boolean = false
    ): Fixture {
        val cutoff = LocalDate.of(2026, 9, 19)
        val completeEnd = completedTrainingWeekEnd(cutoff)
        val rows = (0..7).flatMap { offset ->
            val end = completeEnd.minusDays(offset * 7L)
            val anchor = PlanningSetRecord(end.minusDays(2), "anchor", "anchor", "RESISTANCE", 1, 5, 80.0, 0, 7.0)
            val strength = if (offset in currentStrengthWeeks || offset in previousStrengthWeeks)
                listOf(PlanningSetRecord(end.minusDays(2), "barbell_back_squat", "barbell_back_squat", "RESISTANCE", 1, 3, 100.0, 0, 7.0)) else emptyList()
            val specific = if (includeSpecific && offset in previousStrengthWeeks)
                listOf(PlanningSetRecord(end.minusDays(2), "ex_c5043892", "ex_c5043892", "RESISTANCE", 1, 3, 100.0, 0, 7.0)) else emptyList()
            val hypertrophy = if (includeHypertrophy && offset in previousStrengthWeeks)
                listOf(PlanningSetRecord(end.minusDays(2), "barbell_back_squat", "barbell_back_squat", "RESISTANCE", 2, 12, 70.0, 0, 8.0)) else emptyList()
            listOf(anchor) + strength + specific + hypertrophy
        }
        val metadata = mapOf(
            "anchor" to metadata("anchor"),
            "barbell_back_squat" to metadata("barbell_back_squat"),
            "ex_c5043892" to metadata("ex_c5043892")
        )
        val signals = signal?.let { mapOf("barbell_back_squat" to CanonicalStrengthSignal(posteriorChangePercent = it, observationCount = 3, source = "TEST")) }.orEmpty()
        val snapshot = PlanningHistorySnapshot(
            cutoff, rows, (setOf("anchor", "barbell_back_squat", "ex_c5043892")).associateWith { key ->
                com.training.trackplanner.data.Exercise(key, key, "RESISTANCE")
            }, metadata, emptyMap(), "STRENGTH_GAIN", 1.0, 0.0,
            PersonalizedPlanningPreferences(), canonicalStrengthSignals = signals
        )
        val relations = mutableListOf(
            relation("squat-strength", "barbell_back_squat", TrainableQuality.STRENGTH, PhysicalQualityMode.GENERAL),
            relation("squat-hypertrophy", "barbell_back_squat", TrainableQuality.HYPERTROPHY, PhysicalQualityMode.GENERAL)
        )
        if (includeSpecific) relations += relation("specific-strength", "ex_c5043892", TrainableQuality.STRENGTH, PhysicalQualityMode.SQUAT)
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(relations)
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
        return Fixture(snapshot, state, catalog)
    }

    private fun metadata(key: String): RuntimeExerciseMetadata = RuntimeExerciseMetadataDefaults.forIdentity(key, key).copy(
        activityKind = "EXERCISE",
        programSlot = "MAIN_LOWER_STRENGTH",
        analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"),
        progressMetricType = "LOAD_REPS"
    )

    private fun relation(id: String, key: String, quality: TrainableQuality, mode: PhysicalQualityMode) =
        ExercisePhysicalQualityRelation(id, key, quality, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.LOWER, mode, true, "TEST", emptySet(), "PASS", "")

    private data class Fixture(val snapshot: PlanningHistorySnapshot, val state: AthletePlanningState, val catalog: CanonicalExercisePhysicalQualityCatalog)
}
