package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
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
            relation("deadlift", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("bench", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("squat", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("bound", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            relation("run", TrainableQuality.CARDIORESPIRATORY_FITNESS, StimulusCapabilityLevel.DIRECT_CAPABILITY)
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
        assertEquals(NeedRelevance.MODERATE, power.relevance)
        assertEquals(ExposureState.ABSENT, power.currentExposure)
        assertEquals(TrainingNeedDecision.DEVELOP, power.decision)
    }

    @Test
    fun insufficientHistoryStaysLowConfidenceAndUnknownPriorityIsExplicit() {
        val snapshot = snapshot(goal = "", rows = listOf(record(cutoff.minusDays(1), "squat", 1, 10)))
        val profile = AthleteNeedsProfileEngine().analyze(snapshot, state(StrengthIntent.UNRESOLVED, BadmintonPlanningIntent.UNRESOLVED), catalog)
        assertTrue(profile.unresolved.contains("UNKNOWN_USER_PRIORITY"))
        assertTrue(profile.qualityNeeds.all { it.confidence == PlanningConfidence.LOW })
        assertTrue(profile.sportTaskNeeds.all { it.relevance == NeedRelevance.UNKNOWN })
    }

    @Test
    fun disabledBadmintonIntentMakesSportTasksIrrelevant() {
        val profile = analyze(snapshot("STRENGTH_GAIN", listOf(record(cutoff.minusDays(2), "squat", 1, 5))),
            state(StrengthIntent.STRENGTH_PRIORITY, BadmintonPlanningIntent.DISABLED))
        assertTrue(profile.sportTaskNeeds.all { it.relevance == NeedRelevance.NONE })
    }

    @Test
    fun hypertrophyExposureIsNotPositiveResponse() {
        val rows = listOf(2L, 9L).flatMap { day -> (1..3).map { record(cutoff.minusDays(day), "squat", it, 10) } }
        val profile = analyze(snapshot("HYPERTROPHY_PHYSIQUE", rows), state(StrengthIntent.HYPERTROPHY_PRIORITY))
        val need = profile.qualityNeeds.single { it.quality == TrainableQuality.HYPERTROPHY }
        assertEquals(ExposureState.ESTABLISHED, need.currentExposure)
        assertEquals(TrainingResponseState.INSUFFICIENT_EVIDENCE, need.response)
        assertEquals(TrainingNeedDecision.MAINTAIN, need.decision)
        assertTrue("missing maintain-without-response reason", need.reasonCodes.contains("MAINTAIN_FROM_ESTABLISHED_EXPOSURE_WITHOUT_RESPONSE_EVIDENCE"))
    }

    @Test
    fun cardioExposureIsNotPositiveResponse() {
        val rows = listOf(2L, 9L, 16L).mapIndexed { index, day -> record(cutoff.minusDays(day), "run", index + 1, 12) }
        val need = analyze(snapshot("WEIGHT_MANAGEMENT", rows), state(StrengthIntent.MIXED)).qualityNeeds
            .single { it.quality == TrainableQuality.CARDIORESPIRATORY_FITNESS }
        assertEquals(TrainingResponseState.INSUFFICIENT_EVIDENCE, need.response)
    }

    @Test
    fun powerTrainingDoesNotProvePowerResponse() {
        val rows = listOf(2L, 9L, 16L).mapIndexed { index, day -> record(cutoff.minusDays(day), "bound", index + 1, 5) }
        val need = analyze(snapshot("BADMINTON_PERFORMANCE", rows), state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED)).qualityNeeds
            .single { it.quality == TrainableQuality.POWER }
        assertEquals(TrainingResponseState.INSUFFICIENT_EVIDENCE, need.response)
    }

    @Test
    fun strengthResponseUsesOnlyRelevantRecentlyExposedKeys() {
        val snapshot = snapshot(
            "STRENGTH_GAIN",
            listOf(2L, 9L).flatMap { day -> (1..3).map { record(cutoff.minusDays(day), "squat", it, 5) } },
            mapOf(
                "squat" to CanonicalStrengthSignal(100.0, 5.0, 3, "TEST"),
                "unrelated" to CanonicalStrengthSignal(100.0, -20.0, 3, "TEST")
            )
        )
        val need = analyze(snapshot, state(StrengthIntent.STRENGTH_PRIORITY)).qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(TrainingResponseState.POSITIVE_RESPONSE, need.response)
    }

    @Test
    fun strengthResponseUsesMedianSoOneExtremeDoesNotDominate() {
        val rows = listOf("squat", "deadlift", "bench").mapIndexed { index, key ->
            record(cutoff.minusDays((index + 2).toLong()), key, 1, 5)
        }
        val signals = mapOf(
            "squat" to CanonicalStrengthSignal(100.0, 5.0, 2, "TEST"),
            "deadlift" to CanonicalStrengthSignal(100.0, 6.0, 2, "TEST"),
            "bench" to CanonicalStrengthSignal(100.0, -50.0, 2, "TEST")
        )
        val need = analyze(snapshot("STRENGTH_GAIN", rows, signals), state(StrengthIntent.STRENGTH_PRIORITY)).qualityNeeds
            .single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(TrainingResponseState.POSITIVE_RESPONSE, need.response)
    }

    @Test
    fun genericBadmintonIsContextNotStructuredTaskExposure() {
        val key = "ex_ae9ecdbc"
        val rows = (1..4).map { record(cutoff.minusDays(it.toLong()), key, it, 1) }
        val snapshot = snapshot("BADMINTON_PERFORMANCE", rows, metadata = metadataFor(key, "GENERIC"),
            directObjectives = mapOf(key to TASKS))
        val state = state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED).copy(
            badmintonObjectiveRepresentations = listOf(objectiveRepresentation("DECELERATION", 8.0)))
        val need = analyze(snapshot, state).sportTaskNeeds.single { it.task == "DECELERATION" }
        assertEquals(0, need.structuredDirectUnits)
        assertEquals(0, need.structuredSupportiveUnits)
        assertTrue(need.sportContextLoad > 0.0)
    }

    @Test
    fun structuredBadmintonDrillCountsUnitsAndSessions() {
        val key = "drill"
        val snapshot = snapshot("BADMINTON_PERFORMANCE", listOf(record(cutoff.minusDays(2), key, 1, 5)),
            metadata = metadataFor(key, "STRUCTURED"), directObjectives = mapOf(key to setOf("DECELERATION")))
        val need = analyze(snapshot, state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED)).sportTaskNeeds.single { it.task == "DECELERATION" }
        assertTrue(need.structuredDirectUnits > 0)
        assertTrue(need.structuredDirectSessions > 0)
    }

    @Test
    fun athleticPerformanceDrillCountsSupportiveUnits() {
        val key = "athletic"
        val snapshot = snapshot("BADMINTON_PERFORMANCE", listOf(record(cutoff.minusDays(2), key, 1, 5)),
            metadata = metadataFor(key, "ATHLETIC"), supportiveObjectives = mapOf(key to setOf("DECELERATION")))
        val need = analyze(snapshot, state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED)).sportTaskNeeds.single { it.task == "DECELERATION" }
        assertTrue(need.structuredSupportiveUnits > 0)
    }

    @Test
    fun badmintonIntentCreatesModerateTaskRelevanceWithoutExplicitSubGoal() {
        val profile = analyze(snapshot("BADMINTON_PERFORMANCE", listOf(record(cutoff.minusDays(2), "squat", 1, 10))),
            state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED))
        val expected = setOf("ACCELERATION", "DECELERATION", "FOOTWORK", "REACTION", "LUNGE_REACH", "JUMP_LANDING")
        assertTrue(profile.sportTaskNeeds.filter { it.task in expected }.all { it.relevance == NeedRelevance.MODERATE })
    }

    @Test
    fun badmintonIntentDoesNotMakeAllQualitiesHigh() {
        val profile = analyze(snapshot("BADMINTON_PERFORMANCE", listOf(record(cutoff.minusDays(2), "squat", 1, 10))),
            state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED))
        val checked = setOf(TrainableQuality.POWER, TrainableQuality.RAPID_FORCE_PRODUCTION,
            TrainableQuality.REACTIVE_STRENGTH_SSC, TrainableQuality.CARDIORESPIRATORY_FITNESS, TrainableQuality.MUSCULAR_ENDURANCE)
        assertTrue(profile.qualityNeeds.filter { it.quality in checked }.all { it.relevance != NeedRelevance.HIGH })
    }

    @Test
    fun strengthPriorityStillHasHighRelevance() {
        val need = analyze(snapshot("STRENGTH_GAIN", listOf(record(cutoff.minusDays(2), "squat", 1, 5))),
            state(StrengthIntent.STRENGTH_PRIORITY)).qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(NeedRelevance.HIGH, need.relevance)
    }

    @Test
    fun absoluteHighBadmintonLoadWithoutLowerNegativeEvidenceDoesNotReduce() {
        val snapshot = constrainedSnapshot()
        val state = state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED)
            .copy(recentCourtLoad = 100.0, courtBaselineLoad = 40.0, courtDeviation = 60.0, lowerNegativeEvidence = 0.0)
        assertTrue(analyze(snapshot, state).executionModifiers.none { it.domain == "BADMINTON" && it.modifier == ExecutionModifier.REDUCE })
    }

    @Test
    fun courtDeviationLowerDeteriorationAndRecoveryConstraintReduceExecutionOnly() {
        val snapshot = constrainedSnapshot()
        val state = state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED)
            .copy(courtDeviation = 2.0, lowerNegativeEvidence = 1.0)
        val profile = analyze(snapshot, state)
        assertTrue(profile.executionModifiers.any { it.domain == "BADMINTON" && it.modifier == ExecutionModifier.REDUCE &&
            it.reasonCodes == listOf("COURT_DEVIATION_PLUS_LOWER_NEGATIVE_EVIDENCE_PLUS_RECOVERY_CONSTRAINT") })
        assertEquals(TrainingNeedDecision.DEVELOP, profile.qualityNeeds.single { it.quality == TrainableQuality.POWER }.decision)
    }

    @Test
    fun normalHighBadmintonBaselineDoesNotReduce() {
        val state = state(StrengthIntent.MIXED, BadmintonPlanningIntent.ENABLED)
            .copy(recentCourtLoad = 100.0, courtBaselineLoad = 100.0, courtDeviation = 0.0, lowerNegativeEvidence = 1.0)
        assertTrue(analyze(constrainedSnapshot(), state).executionModifiers.none { it.domain == "BADMINTON" && it.modifier == ExecutionModifier.REDUCE })
    }

    private fun relation(key: String, quality: TrainableQuality, level: StimulusCapabilityLevel) =
        ExercisePhysicalQualityRelation("$key-${quality.name}", key, quality, level,
            com.training.trackplanner.data.PhysicalQualityRegion.LOWER,
            com.training.trackplanner.data.PhysicalQualityMode.GENERAL, true, "TEST", setOf("test"), "PASS", "test")

    private fun record(date: LocalDate, key: String, index: Int, reps: Int) =
        PlanningSetRecord(date, key, key, "RESISTANCE", index, reps, 80.0, 0, 7.0)

    private fun analyze(snapshot: PlanningHistorySnapshot, state: AthletePlanningState) =
        AthleteNeedsProfileEngine().analyze(snapshot, state, catalog)

    private fun snapshot(
        goal: String,
        rows: List<PlanningSetRecord>,
        signals: Map<String, CanonicalStrengthSignal> = emptyMap(),
        metadata: Map<String, RuntimeExerciseMetadata> = rows.map { it.stableKey }.distinct().associateWith { metadataFor(it, "RESISTANCE").getValue(it) },
        directObjectives: Map<String, Set<String>> = emptyMap(),
        supportiveObjectives: Map<String, Set<String>> = emptyMap(),
        recovery: PlanningRecoverySignals = PlanningRecoverySignals()
    ) = PlanningHistorySnapshot(cutoff, rows, emptyMap(), metadata, emptyMap(), goal, 1.0, 0.0,
        PersonalizedPlanningPreferences(), canonicalStrengthSignals = signals, recoverySignals = recovery,
        badmintonDirectObjectives = directObjectives, badmintonSupportiveObjectives = supportiveObjectives)

    private fun constrainedSnapshot() = snapshot("BADMINTON_PERFORMANCE", listOf(record(cutoff.minusDays(2), "squat", 1, 10)),
        recovery = PlanningRecoverySignals(readinessStatus = "CAUTION"))

    private fun metadataFor(key: String, kind: String): Map<String, RuntimeExerciseMetadata> {
        val base = RuntimeExerciseMetadataDefaults.forIdentity(key, key)
        val value = when (kind) {
            "GENERIC" -> base.copy(activityKind = "SPORT_SESSION")
            "STRUCTURED" -> base.copy(activityKind = "EXERCISE", programSlot = "BADMINTON_FOOTWORK",
                analysisEligibility = MetadataTokenField.parse("BADMINTON_TRANSFER"), badmintonTransferLevel = "DIRECT")
            "ATHLETIC" -> base.copy(activityKind = "EXERCISE", programSlot = "DECELERATION_LANDING")
            else -> base.copy(activityKind = "EXERCISE", analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS"))
        }
        return mapOf(key to value)
    }

    private fun objectiveRepresentation(objective: String, current: Double) = BadmintonObjectiveRepresentation(
        objective, current, 0.0, 0.0, 0.0, null, null, null, null, null, 1,
        PlanningConfidence.MODERATE, false, false, RepresentationState.NO_CLEAR_DEFICIT_SIGNAL, emptyList()
    )

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

    private companion object {
        val TASKS = setOf("ACCELERATION", "DECELERATION", "FOOTWORK", "REACTION", "CONDITIONING")
    }
}
