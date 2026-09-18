package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AthleteNeedsProfileTest {
    private val cutoff = LocalDate.of(2026, 9, 19)
    private val catalog = CanonicalExercisePhysicalQualityCatalog.of(
        listOf(
            relation("squat", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("squat", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("bound", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY)
        )
    )

    @Test
    fun highVolumePositiveStrengthResponseIsMaintainOrProgress() {
        val snapshot = snapshot(
            goal = "STRENGTH_GAIN",
            rows = listOf(2L, 9L, 16L).flatMap { day ->
                (1..3).map { index -> record(cutoff.minusDays(day), "squat", index, 5) }
            },
            signals = mapOf("squat" to CanonicalStrengthSignal(100.0, 8.0, 3, "TEST"))
        )
        val profile = AthleteNeedsProfileEngine().analyze(snapshot, state(StrengthIntent.STRENGTH_PRIORITY), catalog)
        val strength = profile.qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(TrainingResponseState.POSITIVE_RESPONSE, strength.response)
        assertEquals(TrainingNeedDecision.MAINTAIN_OR_PROGRESS, strength.decision)
        assertTrue(profile.shadowOnly)
        assertTrue(!profile.prescriptionAuthority)
    }

    @Test
    fun powerLowAndHypertrophyOnlyGoalNeedsNoExtraPower() {
        val snapshot = snapshot(goal = "HYPERTROPHY_PHYSIQUE", rows = listOf(record(cutoff.minusDays(2), "squat", 1, 10)))
        val profile = AthleteNeedsProfileEngine().analyze(snapshot, state(StrengthIntent.HYPERTROPHY_PRIORITY), catalog)
        val power = profile.qualityNeeds.single { it.quality == TrainableQuality.POWER }
        assertEquals(NeedRelevance.NONE, power.relevance)
        assertEquals(TrainingNeedDecision.NO_EXTRA_NEED, power.decision)
    }

    @Test
    fun badmintonIntentWithNoPowerExposureDevelopsPower() {
        val snapshot = snapshot(goal = "BADMINTON_PERFORMANCE", rows = listOf(record(cutoff.minusDays(2), "squat", 1, 10)))
        val profile = AthleteNeedsProfileEngine().analyze(snapshot, state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED), catalog)
        val power = profile.qualityNeeds.single { it.quality == TrainableQuality.POWER }
        assertEquals(NeedRelevance.HIGH, power.relevance)
        assertEquals(ExposureState.ABSENT, power.currentExposure)
        assertEquals(TrainingNeedDecision.DEVELOP, power.decision)
    }

    @Test
    fun insufficientHistoryStaysLowConfidenceAndUnknownPriorityIsExplicit() {
        val snapshot = snapshot(goal = "", rows = listOf(record(cutoff.minusDays(1), "squat", 1, 10)))
        val profile = AthleteNeedsProfileEngine().analyze(snapshot, state(StrengthIntent.UNRESOLVED, BadmintonPlanningIntent.UNRESOLVED), catalog)
        assertTrue(profile.unresolved.contains("UNKNOWN_USER_PRIORITY"))
        assertTrue(profile.qualityNeeds.all { it.confidence == PlanningConfidence.LOW })
    }

    private fun relation(key: String, quality: TrainableQuality, level: StimulusCapabilityLevel) =
        ExercisePhysicalQualityRelation("$key-${quality.name}", key, quality, level,
            com.training.trackplanner.data.PhysicalQualityRegion.LOWER,
            com.training.trackplanner.data.PhysicalQualityMode.GENERAL, true, "TEST", setOf("test"), "PASS", "test")

    private fun record(date: LocalDate, key: String, index: Int, reps: Int) =
        PlanningSetRecord(date, key, key, "RESISTANCE", index, reps, 80.0, 0, 7.0)

    private fun snapshot(goal: String, rows: List<PlanningSetRecord>, signals: Map<String, CanonicalStrengthSignal> = emptyMap()) =
        PlanningHistorySnapshot(cutoff, rows, emptyMap(), emptyMap(), emptyMap(), goal, 1.0, 0.0,
            PersonalizedPlanningPreferences(), canonicalStrengthSignals = signals)

    private fun state(
        strengthIntent: StrengthIntent,
        badmintonIntent: BadmintonPlanningIntent = BadmintonPlanningIntent.UNRESOLVED
    ) = AthletePlanningState(
        observedBehavior = ObservedTrainingBehavior.UNKNOWN,
        strengthExposure = StrengthExposure.PRESENT,
        strengthIntent = strengthIntent,
        badmintonIntent = badmintonIntent,
        freeWeightWillingness = FreeWeightWillingness.UNRESOLVED,
        primaryAdaptation = "MIXED",
        historyDays = 56,
        recentTrainingDaysPerWeek = 3.0,
        scheduleVolatility = 0.0,
        machineSetRatio = 0.0,
        freeWeightSetRatio = 1.0,
        anchors = emptyList(),
        observedStrengthStyle = StrengthProgrammingStyle.UNRESOLVED,
        observedStyleConfidence = PlanningConfidence.LOW,
        structuredBadmintonSessions = 0,
        recoveryConstraint = "NONE",
        confidence = PlanningConfidence.MODERATE
    )
}
