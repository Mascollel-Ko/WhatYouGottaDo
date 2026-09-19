package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
        assertEquals(7, index.regions.size)
        val band = index.regions.getValue(MovementCoverage.LOWER_KNEE).strengthDoseBand
        assertEquals(8, band.eligibleWeekCount)
        assertEquals(4, band.directExposureWeekCount)
        assertEquals(0.5, band.directExposureWeekFrequency!!, 0.0)
        assertEquals(0.0, band.weeklyUnitsQ25!!, 0.0)
        assertEquals(4, index.indexedRowCount)
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
                listOf(PlanningSetRecord(end.minusDays(2), "squat", "squat", "RESISTANCE", 1, 3, 100.0, 0, 7.0)) else emptyList()
            val specific = if (includeSpecific && offset in previousStrengthWeeks)
                listOf(PlanningSetRecord(end.minusDays(2), "specific-squat", "specific-squat", "RESISTANCE", 1, 3, 100.0, 0, 7.0)) else emptyList()
            val hypertrophy = if (includeHypertrophy && offset in previousStrengthWeeks)
                listOf(PlanningSetRecord(end.minusDays(2), "squat", "squat", "RESISTANCE", 2, 12, 70.0, 0, 8.0)) else emptyList()
            listOf(anchor) + strength + specific + hypertrophy
        }
        val metadata = mapOf("anchor" to metadata("anchor"), "squat" to metadata("squat"), "specific-squat" to metadata("specific-squat"))
        val signals = signal?.let { mapOf("squat" to CanonicalStrengthSignal(posteriorChangePercent = it, observationCount = 3, source = "TEST")) }.orEmpty()
        val snapshot = PlanningHistorySnapshot(
            cutoff, rows, emptyMap(), metadata, emptyMap(), "STRENGTH_GAIN", 1.0, 0.0,
            PersonalizedPlanningPreferences(), canonicalStrengthSignals = signals
        )
        val relations = mutableListOf(
            relation("squat-strength", "squat", TrainableQuality.STRENGTH, PhysicalQualityMode.GENERAL),
            relation("squat-hypertrophy", "squat", TrainableQuality.HYPERTROPHY, PhysicalQualityMode.GENERAL)
        )
        if (includeSpecific) relations += relation("specific-strength", "specific-squat", TrainableQuality.STRENGTH, PhysicalQualityMode.SQUAT)
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
