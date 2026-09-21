package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AthleteStimulusNeedTest {
    private val cutoff = LocalDate.of(2026, 9, 20)

    @Test
    fun prescriptionCompatibilityAndSessionIdentityAreAudited() {
        val strength = profile("s", relation("strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val supportive = profile("support", relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val observations = listOf(
            observation("s", 1, cutoff, "same-day-a", 5),
            observation("s", 2, cutoff, "same-day-b", 5),
            observation("support", 3, cutoff, "support", 5),
            observation("s", 5, cutoff, "incompatible", 12),
            observation("s", 4, cutoff.minusDays(28), "prior", 5)
        )
        val snapshot = snapshot(listOf("s", "support"), observations, mapOf(strength.stableKey to strength, supportive.stableKey to supportive))
        val profile = AthleteStimulusNeedEngine().analyze(snapshot, AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers()))
        val need = profile.qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(2, need.exposure.current28d.directUnits)
        assertEquals(1, need.exposure.current28d.excludedDirectByPrescriptionUnits)
        assertEquals(1, need.exposure.current28d.supportiveUnits)
        assertEquals(2, need.exposure.current28d.directSessions)
        assertEquals(1, need.exposure.current28d.directTrainingDays)
        assertTrue("DIRECT_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE" in need.exposure.reasonCodes)
    }

    @Test
    fun finalAuditUsesActualSetsAndExposesDistributionDelta() {
        val strength = profile("s", relation("strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val snapshot = snapshot(listOf("s"), emptyList(), mapOf(strength.stableKey to strength))
        val beforeRow = item("before", 1, List(6) { ProgramSetPrescription(it + 1, 5, 50.0, 0) })
        val afterRows = listOf(
            item("after-a", 1, List(3) { ProgramSetPrescription(it + 1, 5, 50.0, 0) }),
            item("after-b", 3, List(3) { ProgramSetPrescription(it + 1, 5, 50.0, 0) })
        )
        val request = ProgramSkeletonRequest("audit", ProgramGoal.BODYBUILDING, 3, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 1)
        fun plan(rows: List<ProgramSkeletonItem>) = com.training.trackplanner.data.GeneratedProgramSkeleton("audit", 7, request, request.periodizationType, emptyList(), rows)
        val audit = FinalStimulusNeedAudit().audit(plan(afterRows), snapshot, CanonicalExercisePhysicalQualityCatalog.EMPTY, plan(listOf(beforeRow)))
        val reflow = audit.finalReflowDistribution!!
        assertEquals(6, reflow.qualityBefore.getValue(TrainableQuality.STRENGTH).directUnits)
        assertEquals(6, audit.finalQualityCoverage.getValue(TrainableQuality.STRENGTH).directUnits)
        assertEquals(1, reflow.qualityBefore.getValue(TrainableQuality.STRENGTH).directSessions)
        assertEquals(2, reflow.qualityAfter.getValue(TrainableQuality.STRENGTH).directSessions)
        assertEquals(1, reflow.qualityDeltas.getValue(TrainableQuality.STRENGTH).directSessionsBefore)
        assertEquals(2, reflow.qualityDeltas.getValue(TrainableQuality.STRENGTH).directSessionsAfter)
    }

    @Test
    fun taskEvidenceGroupsDuplicateObjectiveRelationsWithDirectPrecedence() {
        val task = listOf(
            objectiveRelation("direct", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.DIRECT),
            objectiveRelation("supportive", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.SUPPORTIVE),
            objectiveRelation("reaction", BadmintonObjective.REACTION, BadmintonObjectiveTransferLevel.SUPPORTIVE),
            objectiveRelation("general", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.GENERAL)
        )
        val p = profile("drill", relation("drill", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY), task)
        val base = snapshot(listOf("drill"), listOf(observation("drill", 10, cutoff, "session", 5)), mapOf("drill" to p))
        val ledgerSnapshot = base.copy(metadata = structuredMetadata("drill", base.metadata))
        val index = StimulusNeedEvidenceIndexBuilder().build(ledgerSnapshot)
        assertEquals(1, index.taskEvidence.getValue("DECELERATION").current28d.directUnits)
        assertEquals(0, index.taskEvidence.getValue("DECELERATION").current28d.supportiveUnits)
        assertEquals(1, index.taskEvidence.getValue("REACTION").current28d.supportiveUnits)
    }

    @Test
    fun taskEvidenceIgnoresGeneralAndLowWhenNoApprovedTransferExists() {
        val p = profile("drill", relation("drill", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY), listOf(
            objectiveRelation("general", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.GENERAL),
            objectiveRelation("low", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.LOW)
        ))
        val s = snapshot(listOf("drill"), listOf(observation("drill", 11, cutoff, "session", 5)), mapOf("drill" to p))
        val index = StimulusNeedEvidenceIndexBuilder().build(s)
        assertEquals(0, index.taskEvidence.getValue("DECELERATION").current28d.directUnits)
        assertEquals(0, index.taskEvidence.getValue("DECELERATION").current28d.supportiveUnits)
    }

    @Test
    fun unavailableOrMismatchedLedgerIsUnknownWhileValidEmptyLedgerIsAbsent() {
        val base = snapshot(listOf("s"), emptyList(), mapOf("s" to profile("s", relation("s", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))))
        val state = AthletePlanningStateBuilder().build(base, PersonalizedPlanningAnswers())
        val unavailable = AthleteStimulusNeedEngine().analyze(base.copy(stimulusExposureLedger = base.stimulusExposureLedger.copy(cutoff = null)), state)
            .qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(ExposureState.UNKNOWN, unavailable.exposure.currentExposure)
        assertEquals(TrainingNeedDecision.UNKNOWN, unavailable.decision)
        assertTrue("LEDGER_UNAVAILABLE" in unavailable.exposure.reasonCodes)
        val mismatch = AthleteStimulusNeedEngine().analyze(base.copy(stimulusExposureLedger = base.stimulusExposureLedger.copy(cutoff = cutoff.minusDays(1))), state)
            .qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(ExposureState.UNKNOWN, mismatch.exposure.currentExposure)
        assertEquals(TrainingNeedDecision.UNKNOWN, mismatch.decision)
        assertTrue("LEDGER_CUTOFF_MISMATCH" in mismatch.exposure.reasonCodes)
        val empty = AthleteStimulusNeedEngine().analyze(base, state).qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(ExposureState.ABSENT, empty.exposure.currentExposure)
        assertEquals(TrainingNeedDecision.DEVELOP, empty.decision)
    }

    @Test
    fun windowBoundariesAndDirectActiveBinsAreExact() {
        val p = profile("s", relation("s", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val observations = listOf(0, 6, 7, 27, 28, 55, 56).mapIndexed { index, age -> observation("s", index.toLong() + 1, cutoff.minusDays(age.toLong()), "session-$index", 5) }
        val index = StimulusNeedEvidenceIndexBuilder().build(snapshot(listOf("s"), observations, mapOf("s" to p)))
        val evidence = index.qualityEvidence.getValue(TrainableQuality.STRENGTH)
        assertEquals(2, evidence.recent7d.directUnits)
        assertEquals(4, evidence.current28d.directUnits)
        assertEquals(2, evidence.prior28d.directUnits)
        assertEquals(6, evidence.context56d.directUnits)
        assertEquals(3, evidence.currentDirectActiveBins)
    }

    @Test
    fun supportiveOrIncompatibleDirectEvidenceCannotActivateDirectBins() {
        val supportive = profile("support", relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val incompatible = profile("incompatible", relation("incompatible", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val observations = listOf(observation("support", 1, cutoff, "support", 5), observation("incompatible", 2, cutoff, "incompatible", 12))
        val profiles = mapOf("support" to supportive, "incompatible" to incompatible)
        val index = StimulusNeedEvidenceIndexBuilder().build(snapshot(profiles.keys.toList(), observations, profiles))
        val evidence = index.qualityEvidence.getValue(TrainableQuality.STRENGTH)
        assertEquals(0, evidence.currentDirectActiveBins)
        assertEquals(1, evidence.current28d.supportiveUnits)
        assertEquals(1, evidence.current28d.excludedDirectByPrescriptionUnits)
    }

    @Test
    fun strengthAndHypertrophyUseOnlyCompatiblePrescriptionClasses() {
        val p = profile("dual", relation("strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY), listOf(),
            extraPhysical = relation("hypertrophy", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val observations = listOf(
            observation("dual", 1, cutoff, "five", 5),
            observation("dual", 2, cutoff, "twelve", 12),
            observation("dual", 3, cutoff, "ambiguous", 20)
        )
        val index = StimulusNeedEvidenceIndexBuilder().build(snapshot(listOf("dual"), observations, mapOf("dual" to p)))
        val strength = index.qualityEvidence.getValue(TrainableQuality.STRENGTH).current28d
        val hypertrophy = index.qualityEvidence.getValue(TrainableQuality.HYPERTROPHY).current28d
        assertEquals(1, strength.directUnits)
        assertEquals(2, strength.excludedDirectByPrescriptionUnits)
        assertEquals(1, hypertrophy.directUnits)
        assertEquals(2, hypertrophy.excludedDirectByPrescriptionUnits)
    }

    @Test
    fun strengthResponseUsesOnlyDirectStrengthLikeStableKeysWithEnoughObservations() {
        val keys = listOf("direct-strength", "supportive-strength", "direct-hypertrophy", "single-strength")
        val profiles = mapOf(
            "direct-strength" to profile("direct-strength", relation("strength", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY)),
            "supportive-strength" to profile("supportive-strength", relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY)),
            "direct-hypertrophy" to profile("direct-hypertrophy", relation("hypertrophy", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY)),
            "single-strength" to profile("single-strength", relation("single", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        )
        val observations = listOf(
            observation("direct-strength", 1, cutoff, "direct-a", 5),
            observation("direct-strength", 2, cutoff.minusDays(7), "direct-b", 5),
            observation("supportive-strength", 3, cutoff, "supportive-a", 5),
            observation("supportive-strength", 4, cutoff.minusDays(7), "supportive-b", 5),
            observation("direct-hypertrophy", 5, cutoff, "hypertrophy-a", 12),
            observation("direct-hypertrophy", 6, cutoff.minusDays(7), "hypertrophy-b", 12),
            observation("single-strength", 7, cutoff, "single-a", 5)
        )
        val signals = mapOf(
            "direct-strength" to CanonicalStrengthSignal(100.0, 3.0, 2, "TEST"),
            "supportive-strength" to CanonicalStrengthSignal(100.0, -100.0, 2, "TEST"),
            "direct-hypertrophy" to CanonicalStrengthSignal(100.0, -100.0, 2, "TEST"),
            "single-strength" to CanonicalStrengthSignal(100.0, -100.0, 1, "TEST")
        )
        val snapshot = snapshot(keys, observations, profiles, canonicalStrengthSignals = signals)
        val need = AthleteStimulusNeedEngine().analyze(snapshot, AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers()))
            .qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(TrainingResponseState.POSITIVE_RESPONSE, need.response)
    }

    @Test
    fun oneSourceObservationFeedsPowerAndRfdAsSeparateViews() {
        val profile = profile("power-source", relation("power", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY),
            extraPhysical = relation("rfd", TrainableQuality.RAPID_FORCE_PRODUCTION, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val observation = observation("power-source", 1, cutoff, "power-session", 3, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL)
        val snapshot = snapshot(listOf("power-source"), listOf(observation), mapOf("power-source" to profile))
        val index = StimulusNeedEvidenceIndexBuilder().build(snapshot)
        assertEquals(1, index.qualityEvidence.getValue(TrainableQuality.POWER).current28d.directUnits)
        assertEquals(1, index.qualityEvidence.getValue(TrainableQuality.RAPID_FORCE_PRODUCTION).current28d.directUnits)
        assertEquals(1, snapshot.stimulusExposureLedger.setObservations.size)
        assertEquals(1, snapshot.stimulusExposureLedger.summary(StimulusFacetFilter(quality = TrainableQuality.POWER)).confirmedSets)
        assertEquals(1, snapshot.stimulusExposureLedger.summary(StimulusFacetFilter(quality = TrainableQuality.RAPID_FORCE_PRODUCTION)).confirmedSets)
    }

    @Test
    fun supportivePrescriptionExclusionHasOnlySupportiveReason() {
        val p = profile("support", relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val s = snapshot(listOf("support"), listOf(observation("support", 1, cutoff, "support", 12)), mapOf("support" to p))
        val need = AthleteStimulusNeedEngine().analyze(s, AthletePlanningStateBuilder().build(s, PersonalizedPlanningAnswers()))
            .qualityNeeds.single { it.quality == TrainableQuality.STRENGTH }
        assertEquals(1, need.exposure.current28d.excludedSupportiveByPrescriptionUnits)
        assertTrue("SUPPORTIVE_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE" in need.exposure.reasonCodes)
        assertTrue("DIRECT_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE" !in need.exposure.reasonCodes)
    }

    @Test
    fun taskConfidenceHasOneOwnerForAbundantAndShortHistory() {
        val relation = objectiveRelation("direct", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.DIRECT)
        val p = profile("drill", relation("drill", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY), listOf(relation))
        val observations = listOf(0, 7, 14, 28).mapIndexed { index, age ->
            observation("drill", index.toLong() + 1, cutoff.minusDays(age.toLong()), "task-$index", 5, PlannedActivityKind.STRUCTURED_BADMINTON_DRILL)
        }
        val s = snapshot(listOf("drill"), observations, mapOf("drill" to p), preferences = badmintonPreferences())
        val task = AthleteStimulusNeedEngine().analyze(s, AthletePlanningStateBuilder().build(s, PersonalizedPlanningAnswers()))
            .sportTaskNeeds.single { it.task == "DECELERATION" }
        assertEquals(PlanningConfidence.MODERATE, task.exposure.confidence)
        assertEquals(task.exposure.confidence, task.confidence)
        val short = AthleteStimulusNeedEngine().analyze(s.copy(allConfirmedSets = s.allConfirmedSets.map { it.copy(date = cutoff.minusDays(7)) }),
            AthletePlanningStateBuilder().build(s.copy(allConfirmedSets = s.allConfirmedSets.map { it.copy(date = cutoff.minusDays(7)) }), PersonalizedPlanningAnswers()))
            .sportTaskNeeds.single { it.task == "DECELERATION" }
        assertEquals(PlanningConfidence.LOW, short.exposure.confidence)
        assertEquals(short.exposure.confidence, short.confidence)
    }

    @Test
    fun finalAuditGroupsDuplicateTaskRelationsPerActualSet() {
        val p = profile("drill", relation("drill", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY), listOf(
            objectiveRelation("direct", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.DIRECT),
            objectiveRelation("supportive", BadmintonObjective.DECELERATION, BadmintonObjectiveTransferLevel.SUPPORTIVE)
        ))
        val base = snapshot(listOf("drill"), emptyList(), mapOf("drill" to p), preferences = badmintonPreferences())
        val s = base.copy(metadata = structuredMetadata("drill", base.metadata))
        val plan = simplePlan(item("drill-row", 1, List(1) { ProgramSetPrescription(1, 5, 50.0, 0) }, key = "drill"))
        val audit = FinalStimulusNeedAudit().audit(plan, s)
        val need = audit.finalTaskCoverage.getValue("DECELERATION")
        assertEquals(1, need.directUnits)
        assertEquals(0, need.supportiveUnits)
    }

    @Test
    fun finalAuditFallbackMapsKeepDirectPrecedence() {
        val base = snapshot(listOf("drill"), emptyList(), emptyMap(), preferences = badmintonPreferences())
        val s = base.copy(
            metadata = structuredMetadata("drill", base.metadata),
            badmintonDirectObjectives = mapOf("drill" to setOf("DECELERATION")),
            badmintonSupportiveObjectives = mapOf("drill" to setOf("DECELERATION"))
        )
        val audit = FinalStimulusNeedAudit().audit(simplePlan(item("drill-row", 1,
            List(1) { ProgramSetPrescription(1, 5, 50.0, 0) }, key = "drill")), s)
        val need = audit.finalTaskCoverage.getValue("DECELERATION")
        assertEquals(1, need.directUnits)
        assertEquals(0, need.supportiveUnits)
    }

    @Test
    fun finalAuditNoMoveStatusIsExplicitAndUnchanged() {
        val s = snapshot(listOf("s"), emptyList(), emptyMap())
        val audit = FinalStimulusNeedAudit().audit(simplePlan(item("row", 1, List(1) { ProgramSetPrescription(1, 5, 50.0, 0) })), s)
        assertEquals("NO_FINAL_REFLOW_MOVES", audit.reflowAuditStatus)
        assertTrue(audit.finalReflowDistribution!!.qualityDeltas.values.all { it.directUnitsBefore == it.directUnitsAfter && it.directSessionsBefore == it.directSessionsAfter })
    }

    @Test
    fun finalAuditFailsSafeWhenTraceIdentityCannotBeReconstructed() {
        val p = profile("s", relation("s", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val s = snapshot(listOf("s"), emptyList(), mapOf("s" to p))
        val objective = PostSplitObjective(BalanceObjective(0, 0.0, 0.0), 0, 0)
        val trace = PostSplitReflowTrace("APPLIED", emptyList(), emptyList(), "before", "after",
            moves = listOf(PostSplitMove("missing", "s", 1, 2, objective, objective)))
        val plan = simplePlan(item("row", 2, List(1) { ProgramSetPrescription(1, 5, 50.0, 0) })).copy(
            personalizedDecision = minimalDecision(trace)
        )
        val audit = FinalStimulusNeedAudit().audit(plan, s)
        assertEquals("PRE_REFLOW_RECONSTRUCTION_UNAVAILABLE", audit.reflowAuditStatus)
        assertTrue(audit.finalReflowDistribution!!.qualityDeltas.isEmpty())
    }

    @Test
    fun finalAuditReconstructsPreReflowDistributionFromProductionTrace() {
        val p = profile("s", relation("s", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val s = snapshot(listOf("s"), emptyList(), mapOf("s" to p))
        val before = item("row-a", 1, List(1) { ProgramSetPrescription(1, 5, 50.0, 0) }) to
            item("row-b", 2, List(1) { ProgramSetPrescription(1, 5, 50.0, 0) })
        val objective = PostSplitObjective(BalanceObjective(0, 0.0, 0.0), 0, 0)
        val trace = PostSplitReflowTrace("APPLIED", emptyList(), emptyList(), "before", "after",
            moves = listOf(PostSplitMove("row-b", "s", 2, 1, objective, objective)))
        val finalPlan = simplePlan(before.first.copy(dayOfWeek = 1)).copy(
            items = listOf(before.first.copy(dayOfWeek = 1), before.second.copy(dayOfWeek = 1)),
            personalizedDecision = minimalDecision(trace)
        )
        val audit = FinalStimulusNeedAudit().audit(finalPlan, s)
        assertEquals("RECONSTRUCTED_FROM_POST_SPLIT_TRACE", audit.reflowAuditStatus)
        val delta = audit.finalReflowDistribution!!.qualityDeltas.getValue(TrainableQuality.STRENGTH)
        assertEquals(2, delta.directUnitsBefore)
        assertEquals(2, delta.directUnitsAfter)
        assertEquals(2, delta.directSessionsBefore)
        assertEquals(1, delta.directSessionsAfter)
    }

    @Test
    fun finalAuditSeparatesDirectAndSupportivePrescriptionCoverageReasons() {
        val direct = profile("direct", relation("direct", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY))
        val supportive = profile("support", relation("support", TrainableQuality.STRENGTH, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY))
        val s = snapshot(listOf("direct", "support"), emptyList(), mapOf("direct" to direct, "support" to supportive))
        val directAudit = FinalStimulusNeedAudit().audit(simplePlan(item("direct-row", 1,
            List(1) { ProgramSetPrescription(1, 12, 50.0, 0) }, key = "direct")), s)
        val directEvidence = directAudit.finalQualityCoverage.getValue(TrainableQuality.STRENGTH)
        assertEquals(0, directEvidence.directUnits)
        assertEquals(1, directEvidence.incompatibleDirectCapabilityUnits)
        assertTrue("DIRECT_CAPABILITY_PRESENT_BUT_PRESCRIPTION_INCOMPATIBLE" in directEvidence.reasonCodes)
        val supportiveAudit = FinalStimulusNeedAudit().audit(simplePlan(item("support-row", 1,
            List(1) { ProgramSetPrescription(1, 5, 50.0, 0) }, key = "support")), s)
        val supportiveEvidence = supportiveAudit.finalQualityCoverage.getValue(TrainableQuality.STRENGTH)
        assertEquals(1, supportiveEvidence.supportiveUnits)
        assertTrue("SUPPORTIVE_ONLY_FINAL_COVERAGE" in supportiveEvidence.reasonCodes)
    }

    private fun snapshot(keys: List<String>, observations: List<StimulusSetObservation>, profiles: Map<String, CanonicalStimulusFacetProfile>,
        preferences: PersonalizedPlanningPreferences = PersonalizedPlanningPreferences(strengthIntent = StrengthIntent.STRENGTH_PRIORITY, badmintonIntent = BadmintonPlanningIntent.DISABLED,
            freeWeightWillingness = FreeWeightWillingness.WILLING), ledgerCutoff: LocalDate? = cutoff,
        canonicalStrengthSignals: Map<String, CanonicalStrengthSignal> = emptyMap()): PlanningHistorySnapshot {
        val exercises = keys.associateWith { Exercise(it, it, "STRENGTH", equipment = "BODYWEIGHT") }
        val metadata = exercises.mapValues { (_, exercise) -> RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            activityKind = "EXERCISE", planningEligibility = "PROGRAM_SELECTABLE", programSlot = "MAIN_LOWER_STRENGTH",
            analysisEligibility = com.training.trackplanner.data.MetadataTokenField.parse("STRENGTH_PROGRESS"), progressMetricType = "LOAD_REPS") }
        val sets = if (observations.isEmpty()) listOf(PlanningSetRecord(cutoff, "s", "s", "STRENGTH", 1, 5, 50.0, 0, null))
        else observations.map { PlanningSetRecord(it.source.date, it.source.stableKey, it.source.stableKey, "STRENGTH", it.source.setIndex ?: 1, it.reps, it.weightKg, it.seconds, it.rpe) }
        return PlanningHistorySnapshot(cutoff, sets, exercises, metadata, emptyMap(), "STRENGTH_GAIN", 1.0, 0.0,
            preferences, canonicalStrengthSignals = canonicalStrengthSignals,
            stimulusExposureLedger = StimulusExposureLedger(profiles, observations, emptyList(), ledgerCutoff))
    }

    private fun profile(key: String, relation: ExercisePhysicalQualityRelation, objectives: List<CanonicalBadmintonObjectiveRelation> = emptyList(), extraPhysical: ExercisePhysicalQualityRelation? = null) =
        CanonicalStimulusFacetProfile(key, physicalQualities = listOfNotNull(relation, extraPhysical), badmintonObjectives = objectives, integrity = StimulusFacetIntegrity.CONSISTENT)

    private fun relation(id: String, quality: TrainableQuality, level: StimulusCapabilityLevel) = ExercisePhysicalQualityRelation(
        id, id, quality, level, PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", "TEST")

    private fun observation(key: String, id: Long, date: LocalDate, session: String, reps: Int, activity: PlannedActivityKind = PlannedActivityKind.RESISTANCE) = StimulusSetObservation(
        StimulusSourceRef(id, "backup-$id", id, 1, session, date, key), activity, reps, 50.0, 0, 8.0,
        if (reps in 1..6) RealizedStimulusClass.STRENGTH_LIKE else if (reps in 7..15) RealizedStimulusClass.HYPERTROPHY_LIKE else RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS, key)

    private fun objectiveRelation(id: String, objective: BadmintonObjective, level: BadmintonObjectiveTransferLevel) =
        CanonicalBadmintonObjectiveRelation(id, "drill", objective, level, "TEST", setOf("TEST"), "Synthetic future-compatible relation")

    private fun badmintonPreferences() = PersonalizedPlanningPreferences(strengthIntent = StrengthIntent.STRENGTH_PRIORITY,
        badmintonIntent = BadmintonPlanningIntent.ENABLED, freeWeightWillingness = FreeWeightWillingness.WILLING)

    private fun structuredMetadata(key: String, ignored: Map<String, RuntimeExerciseMetadata>) = ignored + (key to RuntimeExerciseMetadataDefaults.forExercise(Exercise(key, key, "STRENGTH", equipment = "BODYWEIGHT")).copy(
        activityKind = "EXERCISE", programSlot = "BADMINTON_FOOTWORK", analysisEligibility = MetadataTokenField.parse("BADMINTON_TRANSFER"), badmintonTransferLevel = "DIRECT"))

    private fun simplePlan(row: ProgramSkeletonItem) = com.training.trackplanner.data.GeneratedProgramSkeleton(
        "audit", 7, ProgramSkeletonRequest("audit", ProgramGoal.BODYBUILDING, 3, 60, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, 1),
        ProgramPeriodizationType.AUTO, emptyList(), listOf(row))

    private fun minimalDecision(trace: PostSplitReflowTrace) = PersonalizedPlanningDecision(
        decisionId = "audit", protocolVersion = "test", generatedAtEpochMillis = 0L, historyCutoff = cutoff.toString(),
        historyWindowDays = 56, planningHorizonWeeks = 1, adaptationIntentMinWeeks = 1, adaptationIntentMaxWeeks = 1,
        observedTrainingBehavior = "", strengthIntent = "", strengthIntentProvenance = "", badmintonIntent = "",
        badmintonIntentProvenance = "", primaryAdaptation = "", secondaryTargets = emptyList(), strengthStyle = "",
        strengthStyleProvenance = "", weeklyFrequency = 1, confidence = "", reasonCodes = emptyList(), reasons = emptyList(),
        constraints = emptyList(), metadataAuthorityVersion = "", postSplitReflow = trace
    )

    private fun item(id: String, day: Int, sets: List<ProgramSetPrescription>, key: String = "s") = ProgramSkeletonItem(
        localId = id, weekNumber = 1, dayOfWeek = day, orderIndex = 1, exerciseStableKey = key, exerciseName = key, category = "STRENGTH",
        restSeconds = 60, prescription = "test", setCount = sets.size, reps = sets.first().reps, weightKg = sets.first().weightKg, seconds = 0,
        selectionReason = "test", weightSource = "test", setPrescriptions = sets)
}
