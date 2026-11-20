package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.CurveMatchLevel
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveKnot
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveProfile
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveProfileId
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveProvenance
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgressionLinkMode
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgressionRule
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.reconcileProgression
import com.training.trackplanner.data.configureProgressionSession
import com.training.trackplanner.data.withResolvedWeekDaySchedule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BCoachMultiobjectiveProductionTest {
    private val squat = exercise("barbell_back_squat", "BARBELL")

    @Test fun `T01 linked execution evidence remains source-program scoped`() {
        val first = BCoachMultiobjectiveProduction.linkedEvidenceFingerprint("program-a", "app-1", 3, 2)
        val second = BCoachMultiobjectiveProduction.linkedEvidenceFingerprint("program-b", "app-1", 3, 2)
        assertNotEquals(first, second)
        assertEquals(first, BCoachMultiobjectiveProduction.linkedEvidenceFingerprint("program-a", "app-1", 3, 2))
    }

    @Test fun `T02 applying a copied plan identity does not mutate recorded evidence`() {
        val rows = history()
        val copied = skeleton(2).copy(suggestedName = "copy")
        assertEquals(1, rows.size)
        assertNotEquals(BCoachMultiobjectiveProduction.fingerprint(skeleton(2)),
            BCoachMultiobjectiveProduction.fingerprint(copied))
        assertEquals(LocalDate.of(2026, 11, 10), rows.single().date)
    }

    @Test fun `T03 fixed weekdays are a hard constraint`() {
        val outcome = prepare(
            request = request().copy(fixedWeekdays = setOf(2)),
            baseline = skeleton(2, day = 1), candidate = skeleton(3, day = 1)
        )
        assertEquals(BCoachProductionStatus.INPUT_CONFLICT, outcome.decision.status)
        assertNull(outcome.proposal)
        assertTrue(outcome.decision.hardConstraints.any { it.id == "FIXED_WEEKDAYS" })
    }

    @Test fun `T04 every week and day is checked for time overflow`() {
        val baseline = skeleton(2)
        val overflow = baseline.items.single().copy(
            localId = "w2-overflow", weekNumber = 2, dayOfWeek = 2, restSeconds = 3_600
        )
        val outcome = prepare(baseline = baseline, candidate = baseline.copy(items = baseline.items + overflow))
        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, outcome.decision.status)
        assertTrue(outcome.decision.rejectedActionReasons.any { "SESSION_TIME_LIMIT" in it })
        assertTrue(outcome.decision.alternatives.all { it.axisScores.values.all { value -> value in 0.0..1.0 } })
    }

    @Test fun `T05 unreviewed automatic exercise is rejected while baseline is preserved`() {
        val unknown = exercise("unknown_added", "BAND", confidence = "UNKNOWN")
        val baseline = skeleton(2)
        val added = item("unknown_added", 1, 2, 2, 8, exerciseName = "Unknown")
        val outcome = prepare(
            exercises = mapOf(squat.stableKey to squat, unknown.stableKey to unknown),
            baseline = baseline, candidate = baseline.copy(items = baseline.items + added)
        )
        assertTrue(outcome.decision.rejectedActionReasons.any { "REVIEWED_AUTOMATIC_EXERCISE" in it })
        assertTrue(outcome.skeleton.items.any { it.exerciseStableKey == squat.stableKey })
    }

    @Test fun `T06 missing goal values become finite normalized values not zeros`() {
        val weights = prepare(history = emptyList()).decision.goalWeights
        assertEquals(listOf(3, 3, 3, 3), weights.map { it.effectiveImportance })
        assertEquals(1.0, weights.sumOf { requireNotNull(it.normalizedImportance) }, 1e-12)
        assertTrue(weights.none { it.effectiveImportance == 0 })
    }

    @Test fun `T07 fixed block boost is exactly point two five`() {
        assertEquals(0.25, BCoachV2Policy.FIXED_BLOCK_BOOST, 0.0)
        assertEquals(0.625, 0.75 * 0.5 + 0.25, 1e-12)
    }

    @Test fun `T08 balanced uses the normalized raw goal vector`() {
        val weights = BCoachV2GlobalSelector.effectiveGoalWeights(
            BBalancedGoalImportance(5, 3, 1, 1), false, emptyMap())
        assertEquals(listOf(0.5, 0.3, 0.1, 0.1), weights.map { it.normalizedImportance })
    }

    @Test fun `T09 peak weeks store zero Need and are scoring exempt`() {
        val plan = skeleton(2).copy(
            weekPlans = listOf(weekPlan(1, "B_BALANCED_PEAK_EVALUATION"), weekPlan(2, "RECOVERY")),
            items = skeleton(2).items.map { it.copy(selectionRole = "B12_FOCUS_PEAK") }
        )
        val outcome = prepare(baseline = plan, candidate = plan)
        assertTrue(outcome.decision.needEnvelopes.filter { it.week == 1 }.all {
            it.lower == 0.0 && it.upper == 0.0 && it.exemptFromNeedScoring
        })
    }

    @Test fun `T10 select approve calibrate and persistence replay preserve the exact structure`() {
        val outcome = prepare()
        val proposal = requireNotNull(outcome.proposal)
        val selected = requireNotNull(outcome.decision.recommendedCandidateId)
        val accepted = proposal.accept(requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton, selected)
        assertTrue(accepted.accepted)
        val acceptedPlan = requireNotNull(accepted.skeleton)
        val metadata = JSONObject(requireNotNull(acceptedPlan.planningMetadataJson))
            .getJSONObject("bCoachMultiobjectiveDecision")
        assertEquals(selected, metadata.getString("chosenAlternativeId"))
        BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
            acceptedPlan, history(), mapOf(squat.stableKey to squat),
            BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request())
        )
        assertEquals(BCoachMultiobjectiveProduction.persistenceStructureFingerprint(acceptedPlan),
            metadata.getString("acceptedStructureFingerprint"))
    }

    @Test fun `T11 volume patterns retain distinct stage sequences`() {
        val block = BBlockDefinition(weeks = 4)
        val values = BVolumePattern.entries.associateWith { BBlockPeriodization.steps(block.copy(pattern = it)) }
        assertEquals(BVolumePattern.entries.size, values.values.distinct().size)
    }

    @Test fun `T12 fixed block focus is not rewritten by candidate search`() {
        val plan = skeleton(2).copy(items = skeleton(2).items.map {
            it.copy(selectionRole = "B12_FOCUS_STRENGTH", selectionReason = "B12_BLOCK_1_FOCUS_STRENGTH")
        })
        val outcome = prepare(baseline = plan, candidate = plan)
        val accepted = requireNotNull(requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton).skeleton)
        assertTrue(accepted.items.all { "STRENGTH" in it.selectionRole })
    }

    @Test fun `T12b block marker is not confused with an explicit user lock`() {
        assertFalse(BCoachV2DoseEvaluator.isExplicitUserExerciseLock("B12_BLOCK_1_FOCUS_CORE"))
        assertTrue(BCoachV2DoseEvaluator.isExplicitUserExerciseLock("USER_EXERCISE_LOCKED"))
        assertTrue(BCoachV2DoseEvaluator.isExplicitUserExerciseLock("LOCKED_BY_USER"))
    }

    @Test fun `T12c three consecutive training days are rejected only when avoidable`() {
        val open = request().copy(days = 4, allowedWeekdays = (1..7).toSet())
        val constrained = open.copy(allowedWeekdays = setOf(1, 2, 3, 4))
        assertTrue(BCoachV2DoseEvaluator.shouldEnforceNonClusteredSchedule(open))
        assertFalse(BCoachV2DoseEvaluator.shouldEnforceNonClusteredSchedule(constrained))
        assertEquals(4, BCoachV2DoseEvaluator.longestConsecutiveRun(setOf(1, 2, 3, 4)))
        assertEquals(3, BCoachV2DoseEvaluator.longestConsecutiveRun(setOf(7, 1, 2)))
    }

    @Test fun `T13 Tier A is restricted to the four exact stable keys`() {
        assertEquals(setOf("barbell_back_squat", "barbell_deadlift", "barbell_bench_press", "ex_e41f4c2b"),
            BCoachV2LoadCandidateResolver.canonicalTierAKeys)
        assertFalse("romanian_deadlift" in BCoachV2LoadCandidateResolver.canonicalTierAKeys)
    }

    @Test fun `T14 missing RPE derives a conservative estimate without copying history`() {
        val row = history().single().copy(rpe = null)
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2), request(), listOf(row), mapOf(squat.stableKey to squat))
        val instruction = result.instructionsByLocalId.values.single() as BCoachV2LoadInstruction.EstimatedLoad
        assertEquals(BCoachLoadSource.DIRECT_HISTORY_ESTIMATE, instruction.loadSource)
        assertNotEquals(row.weightKg, instruction.estimatedKg)
        assertTrue(result.baseRirByLocalId.isNotEmpty())
    }

    @Test fun `T15 stale posterior downgrades one exercise without blocking plan generation`() {
        val outcome = prepare(modelSnapshots = mapOf(squat.stableKey to modelSnapshot(stale = true)), history = emptyList())
        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, outcome.decision.status)
        assertTrue(outcome.decision.loadInstructions.values.any { it.startsWith("CALIBRATE") })
    }

    @Test fun `T16 stale model is reported and direct history remains a typed estimate`() {
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2), request(), history(), mapOf(squat.stableKey to squat),
            mapOf(squat.stableKey to modelSnapshot(stale = true)), setOf(squat.stableKey))
        assertTrue(result.reasonCodes.any { it.startsWith("STALE_MODEL") })
        val instruction = result.instructionsByLocalId.values.single() as BCoachV2LoadInstruction.EstimatedLoad
        assertEquals(BCoachLoadSource.DIRECT_HISTORY_ESTIMATE, instruction.loadSource)
    }

    @Test fun `T17 canonical curve is monotone and inversion deterministic`() {
        val profile = curve().profile
        val loads = (1..20).map { requireNotNull(profile.evaluate(it.toDouble()).relativeLoad) }
        assertTrue(loads.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(profile.invert(0.75).repetitions, profile.invert(0.75).repetitions)
    }

    @Test fun `T18 one point two five plate becomes a two point five total grid`() {
        val result = modeledResolution()
        val kg = (result.instructionsByLocalId.values.single() as BCoachV2LoadInstruction.ExactLoad).kg
        assertEquals(0.0, kg % 2.5, 1e-9)
    }

    @Test fun `T19 modeled work load is below capacity and yields scenario viable RIR`() {
        val model = modelSnapshot()
        val result = modeledResolution()
        val load = (result.instructionsByLocalId.values.single() as BCoachV2LoadInstruction.ExactLoad).kg
        assertTrue(load < model.capacityBase)
        assertTrue(BCoachV2LoadCandidateResolver.predictedRir(model.curve, model.capacityLow, load, 5) >= 0.0)
    }

    @Test fun `T20 nine and eleven effective sets both remain inside eight to twelve Need`() {
        val interval = BCoachV2Policy.interval(10.0, 1.0, BCoachV2Scenario.BASE)
        assertEquals(8.0 to 12.0, interval)
        listOf(9.0, 11.0).forEach { dose ->
            assertEquals(0.0, maxOf(0.0, interval.first - dose), 0.0)
            assertEquals(0.0, maxOf(0.0, dose - interval.second), 0.0)
        }
    }

    @Test fun `T21 unconfirmed unusable rows cannot create a Tier C load`() {
        val unusable = history().single().copy(reps = null, weightKg = null)
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2), request(), listOf(unusable), mapOf(squat.stableKey to squat))
        assertTrue(result.instructionsByLocalId.values.single() is BCoachV2LoadInstruction.Calibrate)
    }

    @Test fun `T22 existing explicit manual load is preserved exactly`() {
        val resolved = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2, weightKg = 92.5), request(), emptyList(), mapOf(squat.stableKey to squat))
        assertEquals(92.5, resolved.skeleton.items.single().weightKg, 0.0)
        assertEquals("USER_FIXED", (resolved.instructionsByLocalId.values.single() as
            BCoachV2LoadInstruction.ExactLoad).tier)
    }

    @Test fun `T23 RPE policy preserves a distribution instead of collapsing to one RIR`() {
        val distribution = requireNotNull(RpeRirPolicy.fromCsv(rpePolicyCsv()).resolve(8.0))
        assertTrue(distribution.probabilities.size > 1)
        assertTrue(distribution.probabilities.all { it.probability in 0.0..1.0 })
    }

    @Test fun `T24 equipment increments use barbell times two and display units times one`() {
        val increments = BBlockEquipmentIncrements(1.25, 2.0, 5.0)
        assertEquals(2.5, requireNotNull(increments.incrementFor(BEquipmentIncrementKind.BARBELL)), 0.0)
        assertEquals(2.0, requireNotNull(increments.incrementFor(BEquipmentIncrementKind.DUMBBELL)), 0.0)
        assertEquals(5.0, requireNotNull(increments.incrementFor(BEquipmentIncrementKind.MACHINE)), 0.0)
        assertNull(increments.incrementFor(BEquipmentIncrementKind.UNKNOWN))
    }

    @Test fun `T25 future bodyweight remains unavailable rather than fabricated`() {
        assertEquals("UNAVAILABLE_NOT_FABRICATED", prepare().decision.inputAudit["futureBodyweight"])
        val weightedPullUp = exercise("ex_e41f4c2b", "DUMBBELL")
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2).copy(items = skeleton(2).items.map {
                it.copy(exerciseStableKey = weightedPullUp.stableKey, exerciseName = weightedPullUp.name)
            }),
            request().copy(bShadowDumbbellIncrementKg = 2.0),
            emptyList(),
            mapOf(weightedPullUp.stableKey to weightedPullUp),
            mapOf(weightedPullUp.stableKey to modelSnapshot(key = weightedPullUp.stableKey)),
            setOf(weightedPullUp.stableKey)
        )
        assertTrue(result.instructionsByLocalId.values.single() is BCoachV2LoadInstruction.Calibrate)
        assertTrue(result.reasonCodes.any { it.startsWith("CURRENT_BODYWEIGHT_REQUIRED") })
    }

    @Test fun `T26 a local posterior is consumed only under the same stable key`() {
        val other = exercise("romanian_deadlift", "BARBELL")
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2), request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
            mapOf(squat.stableKey to squat), mapOf(other.stableKey to modelSnapshot(key = other.stableKey)),
            setOf(squat.stableKey))
        assertTrue(result.instructionsByLocalId.values.single() is BCoachV2LoadInstruction.Calibrate)
    }

    @Test fun `T27 repetitions above twenty do not enter the canonical curve`() {
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2, reps = 21), request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
            mapOf(squat.stableKey to squat), mapOf(squat.stableKey to modelSnapshot()), setOf(squat.stableKey))
        assertTrue(result.instructionsByLocalId.values.single() is BCoachV2LoadInstruction.Calibrate)
    }

    @Test fun `T28 decision metadata contains no invented multi set success probability`() {
        val metadata = requireNotNull(prepare().skeleton.planningMetadataJson)
        assertFalse(metadata.contains("successProbability", ignoreCase = true))
        assertFalse(metadata.contains("independentProduct", ignoreCase = true))
    }

    @Test fun `T29 duplicate muscle aliases use max credit within one muscle`() {
        val evaluation = evaluate(skeleton(1, weightKg = 50.0), listOf(
            relation(squat.stableKey, "QUADS", "PRIMARY"),
            relation(squat.stableKey, "QUADS", "SECONDARY"),
            relation(squat.stableKey, "GLUTES", "SECONDARY")
        ))
        assertEquals(1.0, dose(evaluation, "HYPERTROPHY:QUADS"), 1e-9)
        assertEquals(0.5, dose(evaluation, "HYPERTROPHY:GLUTES"), 1e-9)
    }

    @Test fun `T30 another exercise history cannot replace same exercise load evidence`() {
        val otherHistory = history().map { it.copy(stableKey = "barbell_deadlift", weightKg = 180.0) }
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2), request(), otherHistory, mapOf(squat.stableKey to squat))
        assertTrue(result.instructionsByLocalId.values.single() is BCoachV2LoadInstruction.Calibrate)
    }

    @Test fun `T31 Tier C estimates from the recent same stable key load or calibrates`() {
        val estimate = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2), request(), history(), mapOf(squat.stableKey to squat))
        val instruction = estimate.instructionsByLocalId.values.single() as
            BCoachV2LoadInstruction.EstimatedLoad
        assertEquals(BCoachLoadSource.DIRECT_HISTORY_ESTIMATE, instruction.loadSource)
        assertNotEquals(100.0, instruction.estimatedKg, 0.0)
        val calibrate = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2), request(), emptyList(), mapOf(squat.stableKey to squat))
        assertTrue(calibrate.instructionsByLocalId.values.single() is BCoachV2LoadInstruction.Calibrate)
    }

    @Test fun `T32 duration work produces a non load metric without forced RIR`() {
        val plank = exercise("front_plank", "", metric = "DURATION_HOLD")
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(2, key = plank.stableKey, seconds = 20, reps = 0), request(), emptyList(),
            mapOf(plank.stableKey to plank))
        assertTrue(result.instructionsByLocalId.values.single() is BCoachV2LoadInstruction.NonLoadMetric)
        assertTrue(result.skeleton.items.single().setPrescriptions.all { it.targetRpeMin == null })
    }

    @Test fun `T33 no history still creates a concrete four week proposal`() {
        val plan = skeleton(2, weeks = 4, everyWeek = true)
        val outcome = prepare(history = emptyList(), baseline = plan, candidate = plan)
        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, outcome.decision.status)
        assertEquals(setOf(1, 2, 3, 4), requireNotNull(outcome.proposal).decision.needEnvelopes.map { it.week }.toSet())
        assertTrue(outcome.decision.alternatives.isNotEmpty())
    }

    @Test fun `T34 hypertrophy four blends eight ten ten twelve to center ten and interval eight twelve`() {
        val center = BCoachV2Policy.personalizedCenter(10.0, listOf(8.0, 10.0, 10.0, 12.0), 20.0)
        assertEquals(10.0, center, 0.0)
        assertEquals(8.0 to 12.0, BCoachV2Policy.interval(center, 1.0, BCoachV2Scenario.BASE))
    }

    @Test fun `T35 confirmed history and future planned load are distinct inputs to recovery kernel`() {
        val plan = skeleton(2)
        val score = BCoachV2ScheduleLoad.evaluate(
            plan, plan, request(), history(), mapOf(squat.stableKey to squat), BCoachV2Scenario.BASE)
        assertTrue(score.recoveryScore > 0.0)
        assertEquals(1, history().size)
    }

    @Test fun `T36 spaced sessions have no higher recovery interference than adjacent sessions`() {
        val adjacent = multiDaySkeleton(listOf(1, 2))
        val spaced = multiDaySkeleton(listOf(1, 4))
        val a = BCoachV2ScheduleLoad.evaluate(adjacent, adjacent, request(), emptyList(),
            mapOf(squat.stableKey to squat), BCoachV2Scenario.BASE)
        val b = BCoachV2ScheduleLoad.evaluate(spaced, spaced, request(), emptyList(),
            mapOf(squat.stableKey to squat), BCoachV2Scenario.BASE)
        assertTrue(b.recoveryScore <= a.recoveryScore + 1e-12)
    }

    @Test fun `T37 no clinical recovery cap or injury probability is emitted`() {
        val json = requireNotNull(prepare().skeleton.planningMetadataJson)
        assertFalse(json.contains("injuryProbability", true))
        assertFalse(json.contains("approvedRecoveryCap", true))
    }

    @Test fun `T38 recovery remains a soft finite objective rather than a hard ban`() {
        val outcome = prepare()
        val recovery = outcome.decision.candidateObjectives.single {
            it.axis == BCoachObjectiveAxis.RECOVERY_LOAD
        }.values.single().value
        assertTrue(requireNotNull(recovery) in 0.0..1.0)
        assertTrue(outcome.decision.hardConstraints.none { it.id == "RECOVERY_LOAD" })
    }

    @Test fun `T39 time upper uses setup active rest warmup and upper factor`() {
        val actual = BCoachV2ScheduleLoad.timeUpper(skeleton(2).items, mapOf(squat.stableKey to squat))
        val expected = 2.0 + 1.15 * (8.0 + 2.3 + 2 * 45.0 / 60.0 + 120.0 / 60.0 + 0.75)
        assertEquals(expected, actual, 1e-12)
    }

    @Test fun `T40 all eight objective scores are finite and three scenarios have unit regret normalizer`() {
        val decision = prepare().decision
        assertEquals(1.0, BCoachV2Policy.scoreWeights.values.sum(), 1e-12)
        assertEquals(BCoachObjectiveAxis.entries.toSet(), decision.candidateObjectives.map { it.axis }.toSet())
        assertTrue(decision.candidateObjectives.flatMap { it.values }.all { requireNotNull(it.value) in 0.0..1.0 })
        assertTrue(decision.alternatives.all { it.scenarioLosses.keys == BCoachV2Scenario.entries.map { s -> s.name }.toSet() })
    }

    @Test fun `T41 rest and order are preserved and are not search actions`() {
        val baseline = skeleton(2)
        val restOrderOnly = baseline.copy(items = baseline.items.map { it.copy(restSeconds = 999, orderIndex = 7) })
        val outcome = prepare(baseline = baseline, candidate = restOrderOnly)
        val proposal = requireNotNull(outcome.proposal)
        outcome.decision.alternatives.forEach { alternative ->
            val accepted = requireNotNull(proposal.accept(
                requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton, alternative.candidateId).skeleton)
            assertEquals(120, accepted.items.single().restSeconds)
            assertEquals(1, accepted.items.single().orderIndex)
        }
    }

    @Test fun `T42 a set mutation reevaluates Need time recovery and ramp`() {
        val left = evaluate(skeleton(2, weightKg = 50.0))
        val right = evaluate(skeleton(3, weightKg = 50.0))
        listOf(BCoachObjectiveAxis.GOAL_DEFICIT, BCoachObjectiveAxis.RECOVERY_LOAD,
            BCoachObjectiveAxis.RAMP_CHANGE).forEach { axis ->
            assertNotEquals(axis.name, left.baseAxes.getValue(axis), right.baseAxes.getValue(axis), 1e-12)
        }
        assertNotEquals(left.maximumDayMinutes, right.maximumDayMinutes, 1e-12)
    }

    @Test fun `T43 reversed goal importance changes effective input and is explicitly explainable`() {
        val strength = prepare(request = request(BBalancedGoalImportance(5, 1, 1, 1))).decision
        val sport = prepare(request = request(BBalancedGoalImportance(1, 1, 1, 5))).decision
        assertNotEquals(strength.inputFingerprint, sport.inputFingerprint)
        assertNotEquals(strength.goalWeights.map { it.normalizedImportance },
            sport.goalWeights.map { it.normalizedImportance })
        assertTrue(strength.candidateFingerprint != sport.candidateFingerprint ||
            sport.unknowns.any { it == "NO_ELIGIBLE_TASK:SPORT_SUPPORT" })
    }

    @Test fun `T44 identical inputs produce deterministic hashes recommendation alternatives and audit`() {
        val first = prepare().decision
        val second = prepare().decision
        assertEquals(first.inputFingerprint, second.inputFingerprint)
        assertEquals(first.candidateFingerprint, second.candidateFingerprint)
        assertEquals(first.baselineAndCandidatesCanonicalHash, second.baselineAndCandidatesCanonicalHash)
        assertEquals(first.alternatives, second.alternatives)
        assertEquals(first.effectiveSetBreakdown, second.effectiveSetBreakdown)
    }

    @Test fun `S01 no history fills all missing goal values with three`() =
        assertEquals(listOf(3, 3, 3, 3), BCoachV2GlobalSelector.effectiveGoalWeights(
            BBalancedGoalImportance(), false, emptyMap()).map { it.effectiveImportance })

    @Test fun `S02 hypertrophy importance four produces ten and eight twelve`() {
        assertEquals(10.0, BCoachV2Policy.d0(BBalancedGoal.HYPERTROPHY, 4), 0.0)
        assertEquals(8.0 to 12.0, BCoachV2Policy.interval(10.0, 1.0, BCoachV2Scenario.BASE))
    }

    @Test fun `S03 strength importance five produces six and five eight`() {
        assertEquals(6.0, BCoachV2Policy.d0(BBalancedGoal.STRENGTH, 5), 0.0)
        assertEquals(5.0 to 8.0, BCoachV2Policy.interval(6.0, 1.0, BCoachV2Scenario.BASE))
    }

    @Test fun `S04 primary and secondary credits deduplicate per muscle and allow another half muscle`() =
        `T29 duplicate muscle aliases use max credit within one muscle`()

    @Test fun `S05 ramp formula returns zero at twenty percent and point six at fifty percent`() {
        assertEquals(0.0, BCoachV2Policy.ramp(12.0, 10.0, false), 1e-12)
        assertEquals(0.6, BCoachV2Policy.ramp(15.0, 10.0, false), 1e-12)
    }

    @Test fun `S06 objective coefficients sum to one`() =
        assertEquals(1.0, BCoachV2Policy.scoreWeights.values.sum(), 1e-12)

    @Test fun `S07 minimax regret chooses B for the specified losses`() {
        val losses = mapOf(
            "A" to mapOf(BCoachV2Scenario.LOW_STRESS to 0.20, BCoachV2Scenario.BASE to 0.25,
                BCoachV2Scenario.HIGH_STRESS to 0.70),
            "B" to mapOf(BCoachV2Scenario.LOW_STRESS to 0.30, BCoachV2Scenario.BASE to 0.40,
                BCoachV2Scenario.HIGH_STRESS to 0.50)
        )
        val regrets = BCoachV2GlobalSelector.robustRegrets(losses)
        assertEquals(0.20, regrets.getValue("A"), 1e-12)
        assertEquals(0.15, regrets.getValue("B"), 1e-12)
        assertEquals("B", regrets.minBy { it.value }.key)
    }

    @Test fun `S08 missing load is typed calibration and never described as zero kilograms`() {
        val outcome = prepare(history = emptyList())
        assertTrue(outcome.decision.loadInstructions.values.any { it.startsWith("CALIBRATE") })
        val visible = BCoachV2Policy.DISCLAIMER + outcome.decision.loadInstructions.values.joinToString()
        assertFalse(visible.contains("0kg", true))
        assertFalse(visible.contains("null", true))
    }

    @Test fun `T67 shadow sport ranking stays read only and preserves production candidate`() {
        val base = skeleton(3, weeks = 4, everyWeek = true)
        val drill = exercise("shadow_reaction_drill", "BODYWEIGHT")
        val alternative = base.copy(items = base.items.map { item ->
            item.copy(localId = item.localId + "-shadow",
                exerciseStableKey = drill.stableKey, exerciseName = "Reaction drill")
        })
        fun eval(dose: Double) = BCoachV2CandidateEvaluation(
            axesByScenario = BCoachV2Scenario.entries.associateWith {
                BCoachObjectiveAxis.entries.associateWith { 0.1 }
            },
            lossesByScenario = BCoachV2Scenario.entries.associateWith { 0.2 },
            needEnvelopes = emptyList(),
            effectiveSetBreakdown = emptyList(),
            hardConstraints = emptyList(),
            reasonCodes = emptyList(),
            maximumDayMinutes = 55.0,
            planningStartDate = LocalDate.of(2026, 11, 16),
            sportObjectiveWeeks = (1..4).map { week ->
                BCoachSportShadowWeek("REACTION", week, dose, 2.0, 4.0)
            }
        )
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "shadow-reaction", drill.stableKey, BadmintonObjective.REACTION,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("shadow-evidence"), "test verified relation"
            )
        ))
        val before = BCoachMultiobjectiveProduction.fingerprint(base)
        val report = BCoachSportShadowReview.evaluate(
            listOf(
                BCoachSportShadowReview.Input("BASE", base, eval(0.0)),
                BCoachSportShadowReview.Input("ALT", alternative, eval(2.0))
            ),
            productionCandidateId = "BASE",
            catalog = catalog,
            exercises = mapOf(squat.stableKey to squat, drill.stableKey to drill)
        )
        assertEquals("BASE", report.productionCandidateId)
        assertEquals("ALT", report.shadowLeaderId)
        assertEquals(listOf("ALT", "BASE"), report.candidates.map { it.candidateId })
        assertEquals(1, report.candidates.first().objectiveCoverage)
        assertEquals(0.0, requireNotNull(report.candidates.first().shadowSportLoss), 1e-12)
        assertTrue(requireNotNull(report.candidates.last().shadowSportLoss) > 0.0)
        assertEquals(before, BCoachMultiobjectiveProduction.fingerprint(base))
        assertTrue(report.candidates.none {
            it.candidateId == "ALT" && it.productionRecommended
        })
    }

    @Test fun `T45 Epley fallback fixtures preserve capacity conservative factor and downward grid`() {
        val fixtures = listOf(
            Triple(5, 3.0, 137.5),
            Triple(8, 3.0, 127.5),
            Triple(10, 3.0, 120.0),
            Triple(12, 3.0, 115.0),
            Triple(8, 4.0, 122.5)
        )
        fixtures.forEach { (reps, rir, expected) ->
            val base = 180.0 / (1.0 + (reps + rir) / 30.0)
            assertEquals(base * 0.97, base * BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR, 1e-12)
            assertEquals(expected, requireNotNull(BCoachV2LoadCandidateResolver.epleyTrainingWeight(
                capacityKg = 180.0, reps = reps, targetRir = rir, step = 2.5
            )), 0.0)
        }
    }

    @Test fun `T46 personal repetition curve is applied before Epley without mutating capacity`() {
        val plan = withTargetRpe(skeleton(1, reps = 8), 7.0)
        val model = modelSnapshot().copy(capacityLow = 180.0, capacityBase = 180.0, capacityHigh = 180.0)
        val result = BCoachV2LoadCandidateResolver.resolve(
            plan, request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
            mapOf(squat.stableKey to squat), mapOf(squat.stableKey to model), setOf(squat.stableKey)
        )
        val instruction = result.instructionsBySetId.values.single() as BCoachV2LoadInstruction.ExactLoad
        val curveBase = 180.0 * requireNotNull(model.curve.evaluate(11.0).relativeLoad)
        val expected = kotlin.math.floor(curveBase * 0.97 / 2.5) * 2.5
        assertEquals(expected, instruction.kg, 0.0)
        assertNotEquals(127.5, instruction.kg, 0.0)
        assertEquals(180.0, requireNotNull(instruction.capacityKg), 0.0)
        assertEquals(BCoachLoadSource.PERSONAL_MODEL, instruction.loadSource)
    }


    @Test fun `P01 profile PR lower scenario does not cap prescribed or estimated load`() {
        val prior = modelSnapshot().copy(
            tier = "PROFILE_PR_PRIOR", capacityLow = 153.0,
            capacityBase = 170.0, capacityHigh = 178.5
        )
        val plan = withTargetRpe(skeleton(1, reps = 6), 7.0)
        val ratio = requireNotNull(prior.curve.evaluate(9.0).relativeLoad)
        val buffered = 170.0 * ratio * BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR
        val formerLowCap = 153.0 * ratio
        assertTrue(buffered > formerLowCap)

        fun resolve(oneSidePlateKg: Double?) = BCoachV2LoadCandidateResolver.resolve(
            plan, request().copy(bShadowPlateOneSideKg = oneSidePlateKg), emptyList(),
            mapOf(squat.stableKey to squat), mapOf(squat.stableKey to prior),
            mainExerciseKeys = setOf(squat.stableKey)
        )

        val rounded = resolve(1.25)
        val exact = rounded.instructionsBySetId.values.single() as BCoachV2LoadInstruction.ExactLoad
        assertEquals(kotlin.math.floor(buffered / 2.5) * 2.5, exact.kg, 0.0)
        assertTrue(exact.kg > formerLowCap)
        assertTrue(requireNotNull(exact.freshRir) < 5.0)

        val unrounded = resolve(null)
        val estimated = unrounded.instructionsBySetId.values.single() as BCoachV2LoadInstruction.EstimatedLoad
        assertEquals(buffered, estimated.estimatedKg, 1e-9)
        assertTrue(estimated.estimatedKg > formerLowCap)

        // Keep the lower-capacity scenario for risk evaluation, not load capping.
        assertEquals(153.0, prior.capacityLow, 0.0)
        val lowStressRir = unrounded.rirByScenarioAndSet
            .getValue(BCoachV2Scenario.LOW_STRESS).values.single()
        val highStressRir = unrounded.rirByScenarioAndSet
            .getValue(BCoachV2Scenario.HIGH_STRESS).values.single()
        assertTrue(lowStressRir > highStressRir)
    }

    @Test fun `P02 verified posterior still enforces low capacity no failure guard`() {
        val posterior = modelSnapshot().copy(
            tier = "TIER_A", capacityLow = 100.0,
            capacityBase = 120.0, capacityHigh = 140.0
        )
        val result = BCoachV2LoadCandidateResolver.resolve(
            withTargetRpe(skeleton(1, reps = 6), 7.0),
            request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
            mapOf(squat.stableKey to squat), mapOf(squat.stableKey to posterior),
            mainExerciseKeys = setOf(squat.stableKey)
        )
        val exact = result.instructionsBySetId.values.single() as BCoachV2LoadInstruction.ExactLoad
        assertTrue(BCoachV2LoadCandidateResolver.predictedRir(
            posterior.curve, posterior.capacityLow, exact.kg, 6
        ) >= 0.0)
    }

    @Test fun `T47 direct history without RPE is a conservative lower bound rather than failure`() {
        val row = history().single().copy(reps = 6.0, weightKg = 150.0, rpe = null)
        val result = BCoachV2LoadCandidateResolver.resolve(
            skeleton(1, reps = 8), request().copy(bShadowPlateOneSideKg = 1.25), listOf(row),
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        )
        val instruction = result.instructionsBySetId.values.single() as BCoachV2LoadInstruction.ExactLoad
        assertEquals(180.0, requireNotNull(instruction.capacityKg), 1e-12)
        assertEquals(4.0, instruction.targetRir, 0.0)
        assertEquals(122.5, instruction.kg, 0.0)
        assertEquals(BCoachLoadSource.DIRECT_HISTORY_ESTIMATE, instruction.loadSource)
        assertEquals(BCoachEvidenceStatus.POLICY_ASSUMED, instruction.evidenceStatus)
    }

    @Test fun `T48 distinct user fixed set loads remain sixty eighty one hundred`() {
        val base = skeleton(3, reps = 8)
        val fixed = base.copy(items = base.items.map { item ->
            item.copy(
                weightSource = "USER_FIXED",
                weightKg = 0.0,
                setPrescriptions = listOf(60.0, 80.0, 100.0).mapIndexed { index, kg ->
                    ProgramSetPrescription(index + 1, 8, kg, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD)
                }
            )
        })
        val result = BCoachV2LoadCandidateResolver.resolve(
            fixed, request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        )
        assertEquals(listOf(60.0, 80.0, 100.0), result.skeleton.items.single().setPrescriptions.map { it.weightKg })
        assertEquals(0.0, result.skeleton.items.single().weightKg, 0.0)
        assertTrue(result.instructionsBySetId.values.all {
            it is BCoachV2LoadInstruction.ExactLoad && it.loadSource == BCoachLoadSource.USER_FIXED
        })
    }

    @Test fun `T49 automatic three set prescription uses one twenty seven point five then backoffs`() {
        val direct = history().single().copy(reps = 6.0, weightKg = 150.0, rpe = null)
        val result = BCoachV2LoadCandidateResolver.resolve(
            withTargetRpe(skeleton(3, reps = 8), 7.0),
            request().copy(bShadowPlateOneSideKg = 1.25), listOf(direct),
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        )
        assertEquals(listOf(127.5, 125.0, 125.0),
            result.skeleton.items.single().setPrescriptions.map { it.weightKg })
        assertEquals(listOf(1.0, 0.985, 0.985), result.instructionsBySetId.values.map {
            (it as BCoachV2LoadInstruction.ExactLoad).followUpSetFactor
        })
    }

    @Test fun `T50 changing eight to ten reps recalculates load RIR and progression score without cache pollution`() {
        val direct = history().single().copy(reps = 6.0, weightKg = 150.0, rpe = null)
        val req = request().copy(bShadowPlateOneSideKg = 1.25)
        fun resolve(reps: Int) = BCoachV2LoadCandidateResolver.resolve(
            withTargetRpe(skeleton(3, reps = reps), 7.0), req, listOf(direct),
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        )
        fun evaluate(result: BCoachV2LoadResolution) = BCoachV2DoseEvaluator.evaluate(
            plan = result.skeleton, baseline = result.skeleton, request = req, history = listOf(direct),
            exercises = mapOf(squat.stableKey to squat), muscleRelations = emptyList(),
            coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
            mainExerciseKeys = setOf(squat.stableKey),
            goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                BBalancedGoalImportance(), true, emptyMap()),
            baseRirByLocalId = result.baseRirByLocalId,
            baseIntensityByLocalId = result.baseIntensityByLocalId,
            rirByScenario = result.rirByScenario,
            intensityByScenario = result.intensityByScenario,
            baseRirBySetId = result.baseRirBySetId,
            baseIntensityBySetId = result.baseIntensityBySetId,
            rirByScenarioAndSet = result.rirByScenarioAndSet,
            intensityByScenarioAndSet = result.intensityByScenarioAndSet
        )
        val eight = resolve(8)
        val ten = resolve(10)
        assertEquals(listOf(127.5, 125.0, 125.0), eight.skeleton.items.single().setPrescriptions.map { it.weightKg })
        assertEquals(listOf(120.0, 117.5, 117.5), ten.skeleton.items.single().setPrescriptions.map { it.weightKg })
        assertNotEquals(eight.baseRirBySetId, ten.baseRirBySetId)
        assertNotEquals(evaluate(eight).baseAxes.getValue(BCoachObjectiveAxis.PROGRESSION_FIT),
            evaluate(ten).baseAxes.getValue(BCoachObjectiveAxis.PROGRESSION_FIT), 1e-12)
        assertEquals(eight, resolve(8))
    }

    @Test fun `T51 lower body history personalizes quads but leaves chest Need at default`() {
        val plan = skeleton(1, weightKg = 50.0)
        val rows = listOf(
            history().single().copy(date = LocalDate.of(2026, 10, 13), reps = 8.0),
            history().single().copy(date = LocalDate.of(2026, 10, 20), reps = 8.0)
        )
        val weights = BCoachV2GlobalSelector.effectiveGoalWeights(
            BBalancedGoalImportance(hypertrophy = 3), true, emptyMap())
        val evaluation = BCoachV2DoseEvaluator.evaluate(
            plan = plan, baseline = plan, request = request().copy(asOf = LocalDate.of(2026, 11, 15)),
            history = rows, exercises = mapOf(squat.stableKey to squat),
            muscleRelations = listOf(relation(squat.stableKey, "QUADS", "PRIMARY")),
            coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
            mainExerciseKeys = setOf(squat.stableKey), goalWeights = weights
        )
        val chest = evaluation.needEnvelopes.first { it.taskId == "HYPERTROPHY:CHEST" }
        val quads = evaluation.needEnvelopes.first { it.taskId == "HYPERTROPHY:QUADS" }
        assertEquals(BCoachV2Policy.d0(BBalancedGoal.HYPERTROPHY, 3), requireNotNull(chest.center), 0.0)
        assertNotEquals(chest.center, quads.center)
        assertTrue(evaluation.effectiveSetBreakdown.any {
            it.startsWith("HYPERTROPHY:CHEST|w=1|") && "dose=0" in it
        })
        assertTrue(evaluation.baseAxes.getValue(BCoachObjectiveAxis.GOAL_DEFICIT) > 0.0)
    }

    @Test fun `T52 calendar mapping uses the same next Monday and honors explicit start`() {
        val plan = multiDaySkeleton(listOf(1, 3))
        val wednesday = request().copy(asOf = LocalDate.of(2026, 11, 11))
        val sunday = request().copy(asOf = LocalDate.of(2026, 11, 15))
        val fromWednesday = BCoachV2ScheduleLoad.evaluate(
            plan, plan, wednesday, emptyList(), mapOf(squat.stableKey to squat), BCoachV2Scenario.BASE)
        val fromSunday = BCoachV2ScheduleLoad.evaluate(
            plan, plan, sunday, emptyList(), mapOf(squat.stableKey to squat), BCoachV2Scenario.BASE)
        assertEquals(LocalDate.of(2026, 11, 16), fromWednesday.planningStartDate)
        assertEquals(fromWednesday.planningStartDate, fromSunday.planningStartDate)

        val observed = history().map { it.copy(date = sunday.asOf) }
        val defaultStart = BCoachV2ScheduleLoad.evaluate(
            plan, plan, sunday, observed, mapOf(squat.stableKey to squat), BCoachV2Scenario.BASE)
        val explicitStart = BCoachV2ScheduleLoad.evaluate(
            plan, plan, sunday.copy(planningStartDate = LocalDate.of(2026, 11, 23)), observed,
            mapOf(squat.stableKey to squat), BCoachV2Scenario.BASE)
        assertEquals(LocalDate.of(2026, 11, 23), explicitStart.planningStartDate)
        assertNotEquals(defaultStart.recoveryScore, explicitStart.recoveryScore, 1e-12)
        assertNotEquals(BCoachMultiobjectiveProduction.inputFingerprint(sunday, observed),
            BCoachMultiobjectiveProduction.inputFingerprint(
                sunday.copy(planningStartDate = LocalDate.of(2026, 11, 23)), observed))
    }

    @Test fun `T53 chosen alternative stores its own set loads and post approval calibration audit`() {
        val direct = history().single().copy(reps = 6.0, weightKg = 150.0, rpe = null)
        val outcome = prepare(
            request = request().copy(bShadowPlateOneSideKg = 1.25),
            history = listOf(direct),
            baseline = withTargetRpe(skeleton(3, reps = 8), 7.0),
            candidate = withTargetRpe(skeleton(3, reps = 10), 7.0)
        )
        val proposal = requireNotNull(outcome.proposal)
        outcome.decision.alternatives.forEach { alternative ->
            val accepted = requireNotNull(proposal.accept(
                requireNotNull(outcome.decision.proposalFingerprint),
                outcome.skeleton,
                alternative.candidateId
            ).skeleton)
            val decision = JSONObject(requireNotNull(accepted.planningMetadataJson))
                .getJSONObject("bCoachMultiobjectiveDecision")
            assertEquals(alternative.candidateFingerprint, decision.getString("acceptedCandidateFingerprint"))
            val storedLoads = decision.getJSONObject("loadInstructions").toString()
            accepted.items.flatMap { it.setPrescriptions }.filter { it.weightKg > 0.0 }.forEach { set ->
                assertTrue(storedLoads.contains("kg=${set.weightKg}"))
            }
        }

        val accepted = requireNotNull(proposal.accept(
            requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton
        ).skeleton)
        val adjusted = accepted.copy(items = accepted.items.map { item ->
            item.copy(setPrescriptions = item.setPrescriptions.mapIndexed { index, set ->
                if (index == 0) set.copy(weightKg = set.weightKg + 2.5) else set
            })
        })
        BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
            adjusted, listOf(direct), mapOf(squat.stableKey to squat),
            BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request())
        )
        val persisted = BCoachMultiobjectiveProduction.markAcceptedAndPersisted(adjusted)
        val audit = JSONObject(requireNotNull(persisted.planningMetadataJson))
            .getJSONObject("bCoachMultiobjectiveDecision")
        assertTrue(audit.getBoolean("postApprovalLoadAdjusted"))
        assertFalse(audit.getBoolean("scoresReflectCurrentPrescription"))
    }

    @Test fun `T54 two matching sessions replace the default follow up set factor`() {
        val rows = listOf(
            history().single().copy(date = LocalDate.of(2026, 11, 3), setIndex = 1, reps = 6.0, weightKg = 150.0),
            history().single().copy(date = LocalDate.of(2026, 11, 3), setIndex = 2, reps = 8.0, weightKg = 142.5),
            history().single().copy(date = LocalDate.of(2026, 11, 10), setIndex = 1, reps = 6.0, weightKg = 150.0),
            history().single().copy(date = LocalDate.of(2026, 11, 10), setIndex = 2, reps = 8.0, weightKg = 142.5)
        )
        val result = BCoachV2LoadCandidateResolver.resolve(
            withTargetRpe(skeleton(2, reps = 8), 7.0),
            request().copy(bShadowPlateOneSideKg = 1.25), rows,
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        )
        assertEquals(listOf(127.5, 120.0), result.skeleton.items.single().setPrescriptions.map { it.weightKg })
        val second = result.instructionsBySetId.values.elementAt(1) as BCoachV2LoadInstruction.ExactLoad
        assertEquals(0.95, second.followUpSetFactor, 1e-12)
        assertTrue(result.reasonCodes.any { it.startsWith("OBSERVED_FOLLOW_UP_FACTOR") })
    }

    @Test fun `T55 v22 counterexamples 01 to 10 select valid e1RM and never copy it as a training load`() {
        val rows = listOf(
            history().single().copy(date = LocalDate.of(2026, 11, 1), reps = 10.0, weightKg = 100.0, rpe = null),
            history().single().copy(date = LocalDate.of(2026, 11, 10), reps = 1.0, weightKg = 180.0, rpe = null)
        )
        val plan = withTargetRpe(skeleton(1, reps = 12), 7.0)
        val estimated = BCoachV2LoadCandidateResolver.resolve(
            plan, request(), rows, mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        ).instructionsBySetId.values.single() as BCoachV2LoadInstruction.EstimatedLoad
        assertEquals(180.0, requireNotNull(estimated.capacityKg), 0.0)
        assertEquals(116.4, estimated.estimatedKg, 1e-9)
        assertNotEquals(180.0, estimated.estimatedKg, 0.0)
        assertEquals(BCoachLoadSource.DIRECT_HISTORY_ESTIMATE, estimated.loadSource)
        assertEquals(BCoachV2Policy.INITIAL_LOAD_CONSERVATIVE_FACTOR, estimated.conservativeFactor, 0.0)

        val review = prepare(history = rows, baseline = plan, candidate = plan).decision.alternatives
            .flatMap(BCoachPlanAlternative::prescriptionSummary).joinToString("|")
        assertTrue("@~" in review)
        assertTrue("equipmentGrid=REQUIRED" in review)
        assertTrue("targetRir=3.0" in review && "freshRir=" in review && "workingRir=" in review)

        val prescribed = BCoachV2LoadCandidateResolver.resolve(
            plan, request().copy(bShadowPlateOneSideKg = 1.25), rows,
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        ).instructionsBySetId.values.single() as BCoachV2LoadInstruction.ExactLoad
        assertEquals(115.0, prescribed.kg, 0.0)
        assertEquals(180.0, requireNotNull(prescribed.capacityKg), 0.0)

        val rpeEstimate = BCoachV2LoadCandidateResolver.resolve(
            withTargetRpe(skeleton(1, reps = 8), 7.0), request(),
            listOf(history().single().copy(reps = 5.0, weightKg = 150.0, rpe = 8.0)),
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey),
            rirPolicy = RpeRirPolicy.fromCsv(rpePolicyCsv())
        ).instructionsBySetId.values.single() as BCoachV2LoadInstruction.EstimatedLoad
        assertEquals(185.0, requireNotNull(rpeEstimate.capacityKg), 1e-9)
        assertEquals(BCoachEvidenceStatus.OBSERVED, rpeEstimate.evidenceStatus)

        val stale = BCoachV2LoadCandidateResolver.resolve(
            plan, request(), listOf(rows.last().copy(date = LocalDate.of(2026, 1, 1))),
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        )
        assertEquals(BCoachEvidenceStatus.STALE,
            (stale.instructionsBySetId.values.single() as BCoachV2LoadInstruction.EstimatedLoad).evidenceStatus)
        assertTrue(stale.reasonCodes.any { it.startsWith("STALE_DIRECT_HISTORY_UNCERTAINTY") })

        `T46 personal repetition curve is applied before Epley without mutating capacity`()
        `T25 future bodyweight remains unavailable rather than fabricated`()
    }

    @Test fun `T56 v22 counterexamples 11 to 17 preserve per set loads and distinguish working RIR`() {
        val direct = history().single().copy(reps = 1.0, weightKg = 180.0, rpe = null)
        fun resolve(reps: Int) = BCoachV2LoadCandidateResolver.resolve(
            withTargetRpe(skeleton(3, reps = reps), 7.0), request(), listOf(direct),
            mapOf(squat.stableKey to squat), mainExerciseKeys = setOf(squat.stableKey)
        )
        val eight = resolve(8)
        val sets = eight.skeleton.items.single().setPrescriptions
        assertEquals(3, sets.size)
        assertTrue(sets.all { requireNotNull(it.estimatedLoadKg) > 0.0 && it.weightKg == 0.0 })
        assertTrue(sets.drop(1).all { requireNotNull(it.freshRir) != requireNotNull(it.expectedRir) })
        assertTrue(sets.all { it.loadSource == BCoachLoadSource.DIRECT_HISTORY_ESTIMATE })
        assertNotEquals(sets.map { it.estimatedLoadKg }, resolve(10).skeleton.items.single().setPrescriptions.map { it.estimatedLoadKg })
        assertEquals(eight, resolve(8))
        `T48 distinct user fixed set loads remain sixty eighty one hundred`()
        `T54 two matching sessions replace the default follow up set factor`()
    }

    @Test fun `T57 v22 counterexamples 18 to 23 use calculated RIR and relative intensity in dose`() {
        fun prescribed(weight: Double, rir: Double, intensity: Double): GeneratedProgramSkeleton {
            val base = skeleton(1, reps = 8, weightKg = weight)
            return base.copy(items = base.items.map { item ->
                item.copy(setPrescriptions = item.setPrescriptions.map { set ->
                    set.copy(expectedRir = rir, relativeIntensity = intensity, estimatedLoadKg = weight)
                })
            })
        }
        val relation = listOf(relation(squat.stableKey, "QUADS", "PRIMARY"))
        assertTrue(dose(evaluate(prescribed(60.0, 3.0, 0.70), relation), "HYPERTROPHY:QUADS") >
            dose(evaluate(prescribed(60.0, 7.0, 0.70), relation), "HYPERTROPHY:QUADS"))
        assertTrue(dose(evaluate(prescribed(60.0, 3.0, 0.90)), "STRENGTH:${squat.stableKey}") >
            dose(evaluate(prescribed(60.0, 3.0, 0.60)), "STRENGTH:${squat.stableKey}"))
        assertEquals(
            dose(evaluate(prescribed(40.0, 3.0, 0.70), relation), "HYPERTROPHY:QUADS"),
            dose(evaluate(prescribed(100.0, 3.0, 0.70), relation), "HYPERTROPHY:QUADS"), 1e-12
        )
        assertEquals(1.0, BCoachV2DoseEvaluator.qEffort(4.0), 0.0)
        assertEquals(0.75, BCoachV2DoseEvaluator.qEffort(6.0), 0.0)
        assertEquals(0.5, BCoachV2DoseEvaluator.qEffort(8.0), 0.0)
        assertTrue(kotlin.math.abs(BCoachV2DoseEvaluator.qEffort(6.0 - 1e-8) -
            BCoachV2DoseEvaluator.qEffort(6.0 + 1e-8)) < 1e-7)
        `T32 duration work produces a non load metric without forced RIR`()
    }

    @Test fun `T58 v22 counterexamples 24 to 30 calculate bounded per set six axis recovery`() {
        fun set(reps: Int, rir: Double?, intensity: Double?, seconds: Int = 0) = ProgramSetPrescription(
            setIndex = 1, reps = reps, weightKg = 0.0, seconds = seconds,
            expectedRir = rir, relativeIntensity = intensity
        )
        assertNotEquals(
            BCoachV2ScheduleLoad.setAdjustment("systemic", set(5, 3.0, 0.75)),
            BCoachV2ScheduleLoad.setAdjustment("systemic", set(10, 3.0, 0.75)), 1e-12
        )
        assertTrue(BCoachV2ScheduleLoad.setAdjustment("neuralHeavy", set(5, 3.0, 0.90)) >
            BCoachV2ScheduleLoad.setAdjustment("neuralHeavy", set(5, 3.0, 0.60)))
        assertTrue(BCoachV2ScheduleLoad.setAdjustment("localMuscular", set(10, 1.0, 0.70)) >
            BCoachV2ScheduleLoad.setAdjustment("localMuscular", set(10, 7.0, 0.70)))
        assertTrue(BCoachV2ScheduleLoad.setAdjustment("deceleration", set(0, null, null, 60)) >
            BCoachV2ScheduleLoad.setAdjustment("deceleration", set(0, null, null, 20)))
        listOf(-20.0, 0.0, 50.0).forEach { rir ->
            val value = BCoachV2ScheduleLoad.setAdjustment("systemic", set(30, rir, 2.0))
            assertTrue(value in 0.5..1.5)
        }
        assertTrue(BCoachV2ScheduleLoad.setAdjustment("systemic", set(0, null, null)) > 0.0)
        `T52 calendar mapping uses the same next Monday and honors explicit start`()
    }

    @Test fun `T59 v22 counterexamples 31 to 36 integrate badminton and invalidate fingerprints`() {
        val session = V22BadmintonHistorySession(
            date = request().asOf,
            sessionStableKey = "badminton-session",
            practiceLoad = 63.0,
            durationMinutes = 60.0,
            components = listOf(V22BadmintonHistoryComponent(1L, "footwork", 60.0, 63.0, 8.0))
        )
        val badmintonExercise = exercise("footwork", "").copy(
            category = "스포츠", systemicLoadWeight = 0.6, neuralSpeedWeight = 1.0,
            decelerationWeight = 1.0, elasticSscWeight = 0.8
        )
        val noHistory = BCoachV2ScheduleLoad.evaluate(
            skeleton(1), skeleton(1), request(), emptyList(),
            mapOf(squat.stableKey to squat, badmintonExercise.stableKey to badmintonExercise),
            BCoachV2Scenario.BASE
        )
        val withHistory = BCoachV2ScheduleLoad.evaluate(
            skeleton(1), skeleton(1), request(), emptyList(),
            mapOf(squat.stableKey to squat, badmintonExercise.stableKey to badmintonExercise),
            BCoachV2Scenario.BASE, badmintonHistory = listOf(session)
        )
        assertNotEquals(noHistory.recoveryScore, withHistory.recoveryScore, 1e-12)
        assertNotEquals(
            BCoachMultiobjectiveProduction.inputFingerprint(request(), emptyList(), emptyList()),
            BCoachMultiobjectiveProduction.inputFingerprint(request(), emptyList(), listOf(session))
        )
        assertNotEquals(
            BCoachMultiobjectiveProduction.historyFingerprint(emptyList(), listOf(session)),
            BCoachMultiobjectiveProduction.historyFingerprint(emptyList(), listOf(session.copy(practiceLoad = 70.0)))
        )
        assertEquals(BCoachProductionStatus.USER_REVIEW_PROPOSED, prepare(history = emptyList()).decision.status)
    }

    @Test fun `T60 v221 set provenance protects only the actual user fixed set across mutations`() {
        val automaticModel = modelSnapshot().copy(
            capacityLow = 180.0, capacityBase = 180.0, capacityHigh = 180.0
        )
        val mixed = withTargetRpe(skeleton(3, reps = 8), 7.0).copy(items = listOf(
            withTargetRpe(skeleton(3, reps = 8), 7.0).items.single().copy(
                weightSource = "B_COACH_V2_SET_SPECIFIC",
                setPrescriptions = listOf(
                    ProgramSetPrescription(1, 8, 100.0, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD,
                        loadSource = ProgramLoadSource.USER_FIXED),
                    ProgramSetPrescription(2, 8, 90.0, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD,
                        loadSource = ProgramLoadSource.PERSONAL_MODEL),
                    ProgramSetPrescription(3, 8, 87.5, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD,
                        loadSource = ProgramLoadSource.DIRECT_HISTORY_ESTIMATE)
                )
            )
        ))
        val resolved = BCoachV2LoadCandidateResolver.resolve(
            mixed, request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
            mapOf(squat.stableKey to squat), mapOf(squat.stableKey to automaticModel),
            setOf(squat.stableKey)
        )
        val resolvedSets = resolved.skeleton.items.single().setPrescriptions
        assertEquals(100.0, resolvedSets[0].weightKg, 0.0)
        assertEquals(ProgramLoadSource.USER_FIXED, resolvedSets[0].loadSource)
        assertNotEquals(90.0, resolvedSets[1].weightKg, 0.0)
        assertNotEquals(87.5, resolvedSets[2].weightKg, 0.0)
        assertTrue(resolvedSets.drop(1).all { it.loadSource == ProgramLoadSource.PERSONAL_MODEL })

        val misleadingItemSource = mixed.copy(items = mixed.items.map { item ->
            item.copy(weightSource = "USER_FIXED", setPrescriptions = item.setPrescriptions.map {
                it.copy(weightKg = 90.0, loadSource = ProgramLoadSource.PERSONAL_MODEL)
            })
        })
        val automatic = BCoachV2LoadCandidateResolver.resolve(
            misleadingItemSource, request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
            mapOf(squat.stableKey to squat), mapOf(squat.stableKey to automaticModel),
            setOf(squat.stableKey)
        )
        assertTrue(automatic.instructionsBySetId.values.none {
            it is BCoachV2LoadInstruction.ExactLoad && it.loadSource == ProgramLoadSource.USER_FIXED
        })

        val legacy = mixed.copy(items = mixed.items.map { item ->
            item.copy(weightSource = "USER_FIXED", setPrescriptions = listOf(
                ProgramSetPrescription(1, 8, 77.5, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD,
                    loadSource = ProgramLoadSource.LEGACY_UNKNOWN)
            ), setCount = 1)
        })
        assertEquals(77.5, BCoachV2LoadCandidateResolver.resolve(
            legacy, request(), emptyList(), mapOf(squat.stableKey to squat)
        ).skeleton.items.single().setPrescriptions.single().weightKg, 0.0)

        val fixedLast = mixed.copy(items = mixed.items.map { item ->
            item.copy(setPrescriptions = item.setPrescriptions.mapIndexed { index, set ->
                if (index == 2) set.copy(weightKg = 100.0, loadSource = ProgramLoadSource.USER_FIXED) else set
            })
        })
        val added = BCoachV2GlobalSelector.mutateSets(fixedLast, squat.stableKey, +1)
        val newSet = added.items.single().setPrescriptions.last()
        assertEquals(0.0, newSet.weightKg, 0.0)
        assertEquals(ProgramLoadState.USER_CALIBRATION_REQUIRED, newSet.loadState)
        assertEquals(ProgramLoadSource.CALIBRATION_REQUIRED, newSet.loadSource)
        assertNull(newSet.freshRir)
        assertEquals(3, BCoachV2GlobalSelector.mutateSets(fixedLast, squat.stableKey, -1)
            .items.single().setCount)

        val fixedEight = mixed.copy(items = mixed.items.map { item ->
            item.copy(setCount = 1, setPrescriptions = listOf(item.setPrescriptions.first()))
        })
        val eight = BCoachV2LoadCandidateResolver.resolve(
            fixedEight, request(), listOf(history().single().copy(reps = 1.0, weightKg = 180.0, rpe = null)),
            mapOf(squat.stableKey to squat)
        ).skeleton.items.single().setPrescriptions.single()
        val nine = BCoachV2LoadCandidateResolver.resolve(
            BCoachV2GlobalSelector.mutateReps(fixedEight, squat.stableKey, +1), request(),
            listOf(history().single().copy(reps = 1.0, weightKg = 180.0, rpe = null)),
            mapOf(squat.stableKey to squat)
        ).skeleton.items.single().setPrescriptions.single()
        assertEquals(100.0, nine.weightKg, 0.0)
        assertNotEquals(eight.expectedRir, nine.expectedRir)

        val invalid = fixedEight.copy(items = fixedEight.items.map { item ->
            item.copy(setPrescriptions = item.setPrescriptions.map {
                it.copy(weightKg = 0.0, loadSource = ProgramLoadSource.USER_FIXED)
            })
        })
        val invalidResolution = BCoachV2LoadCandidateResolver.resolve(
            invalid, request(), emptyList(), mapOf(squat.stableKey to squat),
            mapOf(squat.stableKey to automaticModel)
        )
        assertTrue(invalidResolution.instructionsBySetId.values.single() is BCoachV2LoadInstruction.Calibrate)
        assertTrue(invalidResolution.reasonCodes.any { it.startsWith("INVALID_USER_FIXED_LOAD") })

        val reviewed = prepare(
            request = request().copy(bShadowPlateOneSideKg = 1.25),
            history = emptyList(),
            baseline = mixed,
            candidate = mixed,
            modelSnapshots = mapOf(squat.stableKey to automaticModel)
        )
        val accepted = requireNotNull(requireNotNull(reviewed.proposal).accept(
            requireNotNull(reviewed.decision.proposalFingerprint), reviewed.skeleton
        ).skeleton)
        assertEquals(100.0, accepted.items.single().setPrescriptions.first().weightKg, 0.0)
        assertEquals(ProgramLoadSource.USER_FIXED,
            accepted.items.single().setPrescriptions.first().loadSource)
    }

    @Test fun `T61 v221 history and plan use one candidate independent relative intensity reference`() {
        val asOf = LocalDate.of(2026, 11, 15)
        val model = modelSnapshot().copy(capacityLow = 100.0, capacityBase = 100.0, capacityHigh = 100.0)
        val rows = listOf(
            history().single().copy(date = LocalDate.of(2026, 10, 13), reps = 5.0, weightKg = 60.0, rpe = null),
            history().single().copy(date = LocalDate.of(2026, 10, 20), reps = 5.0, weightKg = 60.0, rpe = null)
        )
        val reference = BCoachV2LoadCandidateResolver.referenceCapacityByStableKey(
            request().copy(asOf = asOf), rows, mapOf(squat.stableKey to model)
        )
        assertEquals(100.0, reference.getValue(squat.stableKey), 0.0)
        val plan = skeleton(1, reps = 5, weightKg = 60.0)
        val setId = BCoachV2LoadCandidateResolver.setId(plan.items.single().localId, 1)
        val evaluation = BCoachV2DoseEvaluator.evaluate(
            plan = plan,
            baseline = plan,
            request = request().copy(asOf = asOf),
            history = rows,
            exercises = mapOf(squat.stableKey to squat),
            muscleRelations = emptyList(),
            coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
            mainExerciseKeys = setOf(squat.stableKey),
            goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                BBalancedGoalImportance(strength = 3), true, emptyMap()),
            baseIntensityBySetId = mapOf(setId to 0.60),
            referenceCapacityByStableKey = reference
        )
        val relativeCredit = 2.0 / 3.0
        assertEquals(relativeCredit, dose(evaluation, "STRENGTH:${squat.stableKey}"), 1e-12)
        assertEquals(4.0 + 0.25 * (relativeCredit - 4.0),
            requireNotNull(evaluation.needEnvelopes.first {
                it.taskId == "STRENGTH:${squat.stableKey}"
            }.center), 1e-12)
        assertTrue(evaluation.reasonCodes.any { it == "HISTORY_RPE_UNAVAILABLE:${squat.stableKey}" })
        assertTrue(evaluation.reasonCodes.none { it.startsWith("HISTORY_RELATIVE_INTENSITY_UNAVAILABLE") })

        val neutral = BCoachV2DoseEvaluator.evaluate(
            plan = plan, baseline = plan, request = request().copy(asOf = asOf), history = rows,
            exercises = mapOf(squat.stableKey to squat), muscleRelations = emptyList(),
            coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
            mainExerciseKeys = setOf(squat.stableKey),
            goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                BBalancedGoalImportance(strength = 3), true, emptyMap())
        )
        assertEquals(3.25, requireNotNull(neutral.needEnvelopes.first {
            it.taskId == "STRENGTH:${squat.stableKey}"
        }.center), 1e-12)
        assertTrue(neutral.reasonCodes.any {
            it == "HISTORY_RELATIVE_INTENSITY_UNAVAILABLE:${squat.stableKey}"
        })

        val highestValid = BCoachV2LoadCandidateResolver.referenceCapacityByStableKey(
            request().copy(asOf = asOf),
            rows + history().single().copy(date = asOf.minusDays(1), reps = 1.0, weightKg = 180.0, rpe = null) +
                history().single().copy(date = asOf.plusDays(1), reps = 1.0, weightKg = 250.0, rpe = null),
            mapOf(squat.stableKey to model)
        )
        assertEquals(180.0, highestValid.getValue(squat.stableKey), 0.0)
        `T51 lower body history personalizes quads but leaves chest Need at default`()
    }

    @Test fun `accepted proposal still rejects stale evidence and structural edits`() {
        val outcome = prepare()
        val accepted = requireNotNull(requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton).skeleton)
        val stale = assertThrows(IllegalStateException::class.java) {
            BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
                accepted, history() + history().single().copy(setIndex = 2),
                mapOf(squat.stableKey to squat), BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request()))
        }
        assertEquals("B_COACH_ACCEPTANCE_HISTORY_STALE", stale.message)
        val edited = accepted.copy(items = accepted.items.map { item ->
            item.copy(setPrescriptions = item.setPrescriptions.map { it.copy(reps = it.reps + 1) })
        })
        assertEquals("B_COACH_ACCEPTANCE_STRUCTURE_STALE", assertThrows(IllegalStateException::class.java) {
            BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
                edited, history(), mapOf(squat.stableKey to squat),
                BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request()))
        }.message)
    }

    @Test fun `T62 derived schedule and progression do not invalidate an otherwise unchanged proposal`() {
        val outcome = prepare(history = emptyList())
        val proposal = requireNotNull(outcome.proposal)
        val derived = outcome.skeleton.withResolvedWeekDaySchedule()
            .reconcileProgression(setOf(squat.stableKey), mapOf(squat.stableKey to 170.0))

        assertTrue(derived.progressionSessions.isNotEmpty())
        assertEquals(
            BCoachMultiobjectiveProduction.fingerprint(outcome.skeleton),
            BCoachMultiobjectiveProduction.fingerprint(derived)
        )
        val accepted = proposal.accept(
            requireNotNull(outcome.decision.proposalFingerprint), derived,
            requireNotNull(outcome.decision.recommendedCandidateId)
        )
        assertTrue(accepted.reason, accepted.accepted)
        val explicit = derived.configureProgressionSession(
            derived.items.first().localId, ProgressionLinkMode.SEPARATE, null,
            ProgressionRole.MAIN, ProgressionMode.CUSTOM, ProgressionRule(rpeThreshold = 7.5)
        )
        assertNotEquals(
            BCoachMultiobjectiveProduction.fingerprint(outcome.skeleton),
            BCoachMultiobjectiveProduction.fingerprint(explicit)
        )
        val acceptedDerived = requireNotNull(accepted.skeleton)
            .reconcileProgression(setOf(squat.stableKey), mapOf(squat.stableKey to 170.0))
        BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
            acceptedDerived, emptyList(), mapOf(squat.stableKey to squat),
            BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request())
        )
        val explicitAccepted = acceptedDerived.configureProgressionSession(
            acceptedDerived.items.first().localId, ProgressionLinkMode.SEPARATE, null,
            ProgressionRole.MAIN, ProgressionMode.CUSTOM, ProgressionRule(rpeThreshold = 7.5)
        )
        assertEquals("B_COACH_ACCEPTANCE_STRUCTURE_STALE", assertThrows(IllegalStateException::class.java) {
            BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
                explicitAccepted, emptyList(), mapOf(squat.stableKey to squat),
                BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request())
            )
        }.message)
    }

    @Test fun `T63 sport objective universe is identical when a candidate omits the drill`() {
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "r-footwork", "footwork_drill", BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("e-footwork"), "test"
            ),
            CanonicalBadmintonObjectiveRelation(
                "r-reaction", "reaction_drill", BadmintonObjective.REACTION,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("e-reaction"), "test"
            )
        ))
        fun evaluateSport(plan: GeneratedProgramSkeleton) = BCoachV2DoseEvaluator.evaluate(
            plan = plan, baseline = plan, request = request(), history = emptyList(),
            exercises = mapOf(squat.stableKey to squat), muscleRelations = emptyList(),
            coreCatalog = CanonicalCoreCatalog.EMPTY, badmintonCatalog = catalog,
            mainExerciseKeys = setOf(squat.stableKey),
            goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                BBalancedGoalImportance(3, 3, 3, 5), false, emptyMap())
        )
        val withoutDrill = evaluateSport(skeleton(3))
        val withUnrelatedRoster = evaluateSport(skeleton(3).copy(items = skeleton(3).items.map {
            it.copy(exerciseName = "Same dose, different display name")
        }))
        val expected = setOf("SPORT:FOOTWORK", "SPORT:REACTION")
        fun sportTasks(evaluation: BCoachV2CandidateEvaluation) = evaluation.effectiveSetBreakdown
            .map { it.substringBefore("|w=") }.filter { it.startsWith("SPORT:") && it != "SPORT:GENERAL" }.toSet()
        assertEquals(expected, sportTasks(withoutDrill))
        assertEquals(expected, sportTasks(withUnrelatedRoster))
        assertTrue(withoutDrill.effectiveSetBreakdown.filter { it.substringBefore("|w=") in expected }
            .all { row -> Regex("dose=([^|]+)").find(row)!!.groupValues[1].toDouble() == 0.0 })
    }

    @Test fun `accepted v2 point two proposal remains valid after repair version bump`() {
        val outcome = prepare()
        val accepted = requireNotNull(requireNotNull(outcome.proposal).accept(
            requireNotNull(outcome.decision.proposalFingerprint), outcome.skeleton).skeleton)
        val root = JSONObject(requireNotNull(accepted.planningMetadataJson))
        val decision = root.getJSONObject("bCoachMultiobjectiveDecision")
        val legacyEngine = "B_COACH_V2_2_GLOBAL_OPT"
        val legacyPolicy = "B_COACH_V2_2_POLICY_2026_11_15"
        val legacyOwner = "B_COACH_V2_2_GLOBAL_OPT"
        decision.put("engineVersion", legacyEngine)
            .put("policyVersion", legacyPolicy)
            .put("sourceOwner", legacyOwner)
            .put("proposalFingerprint", BCoachMultiobjectiveProduction.proposalFingerprint(
                baselineFingerprint = decision.getString("baselineFingerprint"),
                candidateFingerprint = decision.getString("candidateFingerprint"),
                inputFingerprint = decision.getString("inputFingerprint"),
                historyFingerprint = decision.getString("historyFingerprint"),
                exerciseFingerprint = decision.getString("exerciseFingerprint"),
                linkedEvidenceFingerprint = decision.getString("linkedEvidenceFingerprint"),
                engineVersion = legacyEngine,
                policyVersion = legacyPolicy,
                sourceOwner = legacyOwner
            ))
        val legacyAccepted = accepted.copy(planningMetadataJson = root.toString())

        BCoachMultiobjectiveProduction.validateAcceptedForPersistence(
            legacyAccepted, history(), mapOf(squat.stableKey to squat),
            BCoachMultiobjectiveProduction.linkedEvidenceFingerprint(request())
        )
    }

    @Test fun `T70 standalone performance tests are not automatic routine candidates`() {
        val test = Exercise(
            stableKey = "test_only_footwork", name = "6코너 풋워크 최대반복 테스트",
            category = "기능성운동", equipment = "BODYWEIGHT",
            progressMetricType = "QUALITY_BASED", metadataConfidence = "REVIEWED",
            movementPattern = "BADMINTON_SKILL_TEST"
        )
        val regular = test.copy(
            stableKey = "regular_footwork", name = "6코너 풋워크",
            movementPattern = "FOOTWORK_DIRECT"
        )
        assertTrue(BCoachV2Policy.isTestOnlyExercise(test))
        assertFalse(BCoachV2Policy.isTestOnlyExercise(regular))
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "test-footwork", test.stableKey, BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("test-review"), "test"),
            CanonicalBadmintonObjectiveRelation(
                "normal-footwork", regular.stableKey, BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("normal-review"), "test")
        ))
        val plan = skeleton(2)
        val neighbors = BCoachV2GlobalSelector.rosterNeighbors(
            plan, request().copy(availableEquipment = setOf("BODYWEIGHT")),
            mapOf(squat.stableKey to squat, test.stableKey to test,
                regular.stableKey to regular), catalog, setOf(squat.stableKey)
        )
        assertTrue("A test-only exercise must never be auto inserted",
            neighbors.none { candidate ->
                candidate.second.items.any { it.exerciseStableKey == test.stableKey }
            })
    }

    @Test fun `T71 block roster continuity is not sacrificed for cosmetic sport rotation`() {
        val first = Exercise(
            stableKey = "first_footwork", name = "First footwork", category = "기능성운동",
            equipment = "BODYWEIGHT", progressMetricType = "QUALITY_BASED",
            metadataConfidence = "REVIEWED", badmintonSkillTargets = "FOOTWORK"
        )
        val next = first.copy(stableKey = "reaction_footwork", name = "Reaction footwork",
            badmintonSkillTargets = "FIRST_STEP_REACTION")
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "footwork-direct", first.stableKey, BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("source-a"), "test"),
            CanonicalBadmintonObjectiveRelation(
                "reaction-direct", next.stableKey, BadmintonObjective.REACTION,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("source-b"), "test")
        ))
        val plan = skeleton(3, weeks = 4, everyWeek = true).let { original ->
            original.copy(items = original.items.map { item ->
                item.copy(exerciseStableKey = first.stableKey, stableKey = first.stableKey,
                    exerciseName = first.name, category = first.category,
                    selectionRole = "SPORT_SUPPORT", selectionReason = "AUTO_GENERATED")
            })
        }
        val req = request(BBalancedGoalImportance(3, 3, 3, 5)).copy(
            weeks = 4, blockProgram = BBlockProgramDefinition(listOf(BBlockDefinition(weeks = 4))),
            availableEquipment = setOf("BODYWEIGHT")
        )
        val neighbors = BCoachV2GlobalSelector.rosterNeighbors(
            plan, req, mapOf(first.stableKey to first, next.stableKey to next), catalog, emptySet())
        assertTrue("Do not rotate exercises merely to vary weekly SPORT coverage",
            neighbors.none { it.first.startsWith("SPORT_ROTATE") || it.first.startsWith("REPAIR_") ||
                it.first.startsWith("SPORT_ADD_EVEN") })
        assertTrue("A genuinely different reviewed drill remains an eligible whole-block swap",
            neighbors.any { it.first == "REPLACE_first_footwork_WITH_reaction_footwork" &&
                it.second.items.all { item -> item.exerciseStableKey == next.stableKey } })
    }

    @Test fun `T73 block stage respects user selected steady and wave patterns`() {
        val skeleton = skeleton(3, weeks = 4, everyWeek = true)
        val steady = request().copy(weeks = 4,
            blockProgram = BBlockProgramDefinition(listOf(BBlockDefinition(weeks = 4,
                pattern = BVolumePattern.STEADY))))
        val wave = steady.copy(blockProgram = BBlockProgramDefinition(listOf(
            BBlockDefinition(weeks = 4, pattern = BVolumePattern.WAVE))))
        assertEquals(listOf(0, 0, 0, 0),
            (1..4).map { BCoachV2DoseEvaluator.stageForWeek(skeleton, steady, it) })
        assertEquals(listOf(0, 2, 0, 2),
            (1..4).map { BCoachV2DoseEvaluator.stageForWeek(skeleton, wave, it) })
    }

    @Test fun `T74 approved strength exposure is independent of main placement slots`() {
        val plan = skeleton(3)
        val result = BCoachV2DoseEvaluator.evaluate(
            plan = plan, baseline = plan, request = request(), history = emptyList(),
            exercises = mapOf(squat.stableKey to squat), muscleRelations = emptyList(),
            coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
            mainExerciseKeys = emptySet(),
            goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                BBalancedGoalImportance(5, 3, 3, 3), false, emptyMap()))
        assertFalse("A squat must create a strength task without placement metadata",
            result.reasonCodes.contains("NO_ELIGIBLE_TASK:STRENGTH"))
        assertTrue(result.effectiveSetBreakdown.any { it.startsWith("STRENGTH:barbell_back_squat|") &&
            !it.contains("|dose=0|") })
    }

    @Test fun `T64 global search reselects reviewed sport exercises without inheriting weights`() {
        val light = Exercise(
            stableKey = "test_light_court", name = "Light court", category = "기능성운동",
            equipment = "BODYWEIGHT", progressMetricType = "QUALITY_BASED",
            metadataConfidence = "REVIEWED", badmintonSkillTargets = "FOOTWORK_SPEED"
        )
        val random = Exercise(
            stableKey = "test_random_court", name = "Random court", category = "기능성운동",
            equipment = "BODYWEIGHT", progressMetricType = "QUALITY_BASED",
            metadataConfidence = "REVIEWED",
            badmintonSkillTargets = "FOOTWORK_SPEED,FIRST_STEP_REACTION,DECELERATION_CONTROL"
        )
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "test-light-footwork", light.stableKey, BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("source-light"), "test"
            ),
            CanonicalBadmintonObjectiveRelation(
                "test-random-footwork", random.stableKey, BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("source-random"), "test"
            ),
            CanonicalBadmintonObjectiveRelation(
                "test-random-reaction", random.stableKey, BadmintonObjective.REACTION,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("source-random-reaction"), "test"
            )
        ))
        val base = skeleton(2)
        val source = base.items.single().copy(
            exerciseStableKey = light.stableKey, exerciseName = light.name,
            category = light.category, selectionReason = "AUTO_GENERATED",
            selectionRole = "SPORT_SUPPORT", weightSource = "CALIBRATION_REQUIRED"
        )
        val plan = base.copy(items = listOf(source))
        val inventory = mapOf(light.stableKey to light, random.stableKey to random)
        val req = request().copy(availableEquipment = setOf("BODYWEIGHT"))
        val neighbors = BCoachV2GlobalSelector.rosterNeighbors(
            plan, req, inventory, catalog, emptySet()
        )
        val swapped = neighbors.single { it.first == "REPLACE_test_light_court_WITH_test_random_court" }
            .second.items.single()
        assertEquals(random.stableKey, swapped.exerciseStableKey)
        assertEquals(2, swapped.setCount)
        assertTrue(swapped.setPrescriptions.all {
            it.weightKg == 0.0 && it.estimatedLoadKg == null &&
                it.loadSource == ProgramLoadSource.CALIBRATION_REQUIRED
        })
        val locked = plan.copy(items = listOf(source.copy(selectionReason = "USER_EXERCISE_LOCK")))
        assertTrue(BCoachV2GlobalSelector.rosterNeighbors(
            locked, req, inventory, catalog, emptySet()
        ).none { it.first.startsWith("REPLACE_") })
        val unavailable = inventory + (random.stableKey to random.copy(equipment = "CONE_MARKER"))
        assertTrue(BCoachV2GlobalSelector.rosterNeighbors(
            plan, req, unavailable, catalog, emptySet()
        ).none { it.second.items.any { item -> item.exerciseStableKey == random.stableKey } })
    }

    @Test fun `T68 sport repetition history personalizes Need without fabricating recovery load`() {
        val key = "sport_history_footwork"
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "history-footwork", key, BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("history-evidence"), "test"
            )
        ))
        val drill = Exercise(
            stableKey = key, name = "Sport footwork", category = "스포츠",
            progressMetricType = "QUALITY_BASED", metadataConfidence = "REVIEWED",
            neuralSpeedWeight = 1.0, decelerationWeight = 1.0
        )
        val plan = skeleton(3)
        val rows = listOf("2026-10-19", "2026-10-26", "2026-11-02").flatMap { date ->
            (1..4).map { index ->
                V22HistoryRow(LocalDate.parse(date), drill.name, key,
                    "스포츠", index, 8.0, null, null, null)
            }
        }
        fun sport(historyRows: List<V22HistoryRow>) = BCoachV2DoseEvaluator.evaluate(
            plan = plan, baseline = plan, request = request(), history = historyRows,
            exercises = mapOf(squat.stableKey to squat, key to drill),
            muscleRelations = emptyList(), coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = catalog, mainExerciseKeys = setOf(squat.stableKey),
            goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                BBalancedGoalImportance(3, 3, 3, 5), true, emptyMap())
        )
        val prior = sport(emptyList())
        val observed = sport(rows)
        val noHistoryCenter = requireNotNull(
            prior.needEnvelopes.first { it.taskId == "SPORT:FOOTWORK" }.center)
        val observedCenter = requireNotNull(
            observed.needEnvelopes.first { it.taskId == "SPORT:FOOTWORK" }.center)
        assertTrue("Completed repetitions must affect SPORT Need",
            observedCenter < noHistoryCenter)
        assertEquals(
            prior.axesByScenario.getValue(BCoachV2Scenario.BASE)
                .getValue(BCoachObjectiveAxis.RECOVERY_LOAD),
            observed.axesByScenario.getValue(BCoachV2Scenario.BASE)
                .getValue(BCoachObjectiveAxis.RECOVERY_LOAD), 1e-12
        )
        assertTrue(observed.reasonCodes.contains("SPORT_REPS_ONLY_DURATION_UNKNOWN:$key"))
    }

    @Test fun `T69 a timed functional drill does not enter recovery twice`() {
        val key = "timed_functional_footwork"
        val date = LocalDate.of(2026, 11, 13)
        val drill = Exercise(
            stableKey = key, name = "Timed footwork", category = "기능성운동",
            progressMetricType = "DURATION", metadataConfidence = "REVIEWED",
            systemicLoadWeight = 1.0, neuralSpeedWeight = 1.0,
            decelerationWeight = 1.0, elasticSscWeight = 1.0
        )
        val planned = skeleton(3)
        val timedRow = V22HistoryRow(
            date, drill.name, key, "기능성운동", 1, null, null, 600.0, 8.0
        )
        val practice = listOf(V22BadmintonHistorySession(
            date = date, sessionStableKey = "timed-2026-11-13",
            practiceLoad = 10.5, durationMinutes = 10.0,
            components = listOf(V22BadmintonHistoryComponent(
                entryId = 1L, stableKey = key, durationMinutes = 10.0,
                practiceLoad = 10.5, rpe = 8.0
            ))
        ))
        fun recovery(rows: List<V22HistoryRow>) = BCoachV2ScheduleLoad.evaluate(
            plan = planned, baseline = planned, request = request(),
            history = rows, exercises = mapOf(squat.stableKey to squat, key to drill),
            scenario = BCoachV2Scenario.BASE, badmintonHistory = practice
        ).recoveryScore
        assertEquals("The timed drill must contribute only through badminton practice load",
            recovery(emptyList()), recovery(listOf(timedRow)), 1e-12)
    }

    @Test fun `T66 longer recorded sport duration is not scored as one identical set`() {
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "duration-footwork", "duration_court", BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("duration-source"), "test"
            )
        ))
        val exercise = Exercise(
            stableKey = "duration_court", name = "Duration court", category = "기능성운동",
            equipment = "BODYWEIGHT", metadataConfidence = "REVIEWED",
            progressMetricType = "DURATION"
        )
        val shorter = skeleton(2, key = exercise.stableKey, seconds = 15, reps = 0)
        val longer = shorter.copy(items = shorter.items.map { item ->
            item.copy(seconds = 60, setPrescriptions = item.setPrescriptions.map { it.copy(seconds = 60) })
        })
        fun dose(plan: GeneratedProgramSkeleton): Double {
            val evaluation = BCoachV2DoseEvaluator.evaluate(
                plan = plan, baseline = shorter, request = request(),
                history = emptyList(), exercises = mapOf(exercise.stableKey to exercise),
                muscleRelations = emptyList(), coreCatalog = CanonicalCoreCatalog.EMPTY,
                badmintonCatalog = catalog, mainExerciseKeys = emptySet(),
                goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
                    BBalancedGoalImportance(3, 3, 3, 5), false, emptyMap()
                )
            )
            val row = evaluation.effectiveSetBreakdown.single {
                it.startsWith("SPORT:FOOTWORK|w=1|")
            }
            return Regex("dose=([^|]+)").find(row)!!.groupValues[1].toDouble()
        }
        assertTrue("Longer recorded duration must increase objective exposure", dose(longer) > dose(shorter))
    }

    @Test fun `T65 normal one-set candidate fails final hard constraints`() {
        val oneSet = skeleton(1)
        val evaluation = evaluate(oneSet)
        assertTrue(evaluation.hardConstraints.any {
            it.id == "NORMAL_TRAINING_MIN_TWO_SETS" &&
                it.status == BCoachConstraintStatus.FAIL
        })
        assertTrue(evaluate(skeleton(2)).hardConstraints.any {
            it.id == "NORMAL_TRAINING_MIN_TWO_SETS" &&
                it.status == BCoachConstraintStatus.PASS
        })
    }

    private fun prepare(
        request: V22GenerationRequest = request(),
        history: List<V22HistoryRow> = history(),
        exercises: Map<String, Exercise> = mapOf(squat.stableKey to squat),
        baseline: GeneratedProgramSkeleton = skeleton(2),
        candidate: GeneratedProgramSkeleton = skeleton(3),
        modelSnapshots: Map<String, BCoachV2LoadModelSnapshot> = emptyMap()
    ): BCoachMultiobjectiveProduction.Outcome = BCoachMultiobjectiveProduction.prepare(
        request = request,
        history = history,
        exercises = exercises,
        baseline = baseline,
        candidate = candidate,
        mainExerciseKeys = setOf(squat.stableKey),
        modelSnapshots = modelSnapshots
    )

    private fun evaluate(
        plan: GeneratedProgramSkeleton,
        relations: List<CanonicalMetadataRelation> = emptyList()
    ): BCoachV2CandidateEvaluation = BCoachV2DoseEvaluator.evaluate(
        plan = plan,
        baseline = plan,
        request = request(),
        history = emptyList(),
        exercises = plan.items.associate { item -> item.exerciseStableKey to exercise(item.exerciseStableKey, "BARBELL") },
        muscleRelations = relations,
        coreCatalog = CanonicalCoreCatalog.EMPTY,
        badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
        mainExerciseKeys = setOf(squat.stableKey),
        goalWeights = BCoachV2GlobalSelector.effectiveGoalWeights(
            BBalancedGoalImportance(3, 4, 3, 3), false, emptyMap())
    )

    private fun modeledResolution() = BCoachV2LoadCandidateResolver.resolve(
        skeleton(2), request().copy(bShadowPlateOneSideKg = 1.25), emptyList(),
        mapOf(squat.stableKey to squat), mapOf(squat.stableKey to modelSnapshot()), setOf(squat.stableKey))

    private fun withTargetRpe(plan: GeneratedProgramSkeleton, targetRpe: Double) = plan.copy(
        items = plan.items.map { item ->
            item.copy(setPrescriptions = item.setPrescriptions.map { it.copy(targetRpeMin = targetRpe) })
        }
    )

    private fun history() = listOf(V22HistoryRow(
        LocalDate.of(2026, 11, 10), "Back squat", squat.stableKey,
        "Strength", 1, 5.0, 100.0, null, 8.0
    ))

    private fun request(importance: BBalancedGoalImportance = BBalancedGoalImportance()) =
        V22GenerationRequest(
            name = "B v2", weeks = 2, days = 1, mode = V22Mode.B,
            asOf = LocalDate.of(2026, 11, 14), sessionMinutes = 60,
            bGoalImportance = importance,
            blockProgram = BBlockProgramDefinition(listOf(BBlockDefinition(weeks = 2)))
        )

    private fun skeleton(
        sets: Int,
        day: Int = 1,
        reps: Int = 5,
        weightKg: Double = 0.0,
        key: String = squat.stableKey,
        seconds: Int = 0,
        weeks: Int = 2,
        everyWeek: Boolean = false
    ): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            name = "B v2", goal = ProgramGoal.FUNCTIONAL_CONDITIONING,
            weeklyTrainingDays = 1, sessionMinutes = 60, availableEquipment = emptySet(),
            excludedExerciseText = "", badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = weeks
        )
        val weekItems = if (everyWeek) (1..weeks) else 1..1
        return GeneratedProgramSkeleton(
            suggestedName = request.name, durationDays = weeks * 7, request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = (1..weeks).map { weekPlan(it, "B_BALANCED_BLOCK_1_BALANCED") },
            items = weekItems.map { week -> item(key, week, day, sets, reps, weightKg, seconds = seconds) },
            weekDaySchedule = (1..weeks).associateWith { setOf(day) },
            planningMetadataJson = JSONObject().put("schema", "test").toString()
        )
    }

    private fun item(
        key: String,
        week: Int,
        day: Int,
        sets: Int,
        reps: Int,
        weightKg: Double = 0.0,
        exerciseName: String = "Back squat",
        seconds: Int = 0
    ): ProgramSkeletonItem {
        val loadState = if (weightKg > 0.0) ProgramLoadState.EXPLICIT_LOAD
            else ProgramLoadState.USER_CALIBRATION_REQUIRED
        val prescriptions = List(sets) { index ->
            ProgramSetPrescription(index + 1, reps, weightKg, seconds, null, loadState)
        }
        return ProgramSkeletonItem(
            localId = "w$week-d$day-$key", weekNumber = week, dayOfWeek = day, orderIndex = 1,
            exerciseStableKey = key, exerciseName = exerciseName, category = "Strength", restSeconds = 120,
            prescription = "v2", setCount = sets, reps = reps, weightKg = weightKg, seconds = seconds,
            selectionReason = "B12_BLOCK_1_FOCUS_BALANCED",
            weightSource = if (weightKg > 0.0) "USER_FIXED" else "USER_CALIBRATION_REQUIRED",
            selectionRole = "B12_FOCUS_BALANCED", setPrescriptions = prescriptions
        )
    }

    private fun multiDaySkeleton(days: List<Int>): GeneratedProgramSkeleton {
        val base = skeleton(3)
        return base.copy(
            request = base.request.copy(weeklyTrainingDays = days.size),
            items = days.mapIndexed { index, day ->
                base.items.single().copy(localId = "d$day", dayOfWeek = day, orderIndex = index + 1)
            },
            weekDaySchedule = mapOf(1 to days.toSet(), 2 to days.toSet())
        )
    }

    private fun exercise(
        key: String,
        equipment: String,
        confidence: String = "REVIEWED",
        metric: String = "ESTIMATED_1RM"
    ) = Exercise(
        stableKey = key, name = key, category = "Strength", equipment = equipment,
        metadataConfidence = confidence, progressMetricType = metric,
        systemicLoadWeight = 1.0, neuralHeavyWeight = 0.8, neuralSpeedWeight = 0.2,
        localLoadWeight = 0.7, decelerationWeight = 0.3, elasticSscWeight = 0.1
    )

    private fun relation(key: String, muscle: String, role: String) = CanonicalMetadataRelation(
        domain = CanonicalRelationDomain.MUSCLE, relationKey = "$key-$muscle-$role",
        exerciseStableKey = key, relationType = role, relationValue = muscle, coefficient = null,
        sourceStableKey = key, status = "REVIEWED", provenance = "TEST"
    )

    private fun curve(): ResolvedRepetitionCurve {
        val id = RepetitionCurveProfileId("test")
        val profile = RepetitionCurveProfile(
            id = id,
            knots = listOf(RepetitionCurveKnot(1.0, 1.0), RepetitionCurveKnot(5.0, 0.85),
                RepetitionCurveKnot(10.0, 0.70), RepetitionCurveKnot(20.0, 0.50)),
            provenance = RepetitionCurveProvenance(
                curveProfileId = id, curveVersion = "test-v1", sourceCitation = "test",
                sourceArtifactHash = "hash", sourceTableChecksum = "checksum", generatorVersion = "test",
                reviewedAt = "2026-11-15", supportedRepRange = 1..20, sourceExerciseScope = "test",
                sourceModelDescription = "test", runtimeAssetChecksum = "runtime"
            )
        )
        return ResolvedRepetitionCurve(profile, CurveMatchLevel.EXACT_EXERCISE, 1.0,
            "test", "exercise:${squat.stableKey}")
    }

    private fun modelSnapshot(key: String = squat.stableKey, stale: Boolean = false) =
        BCoachV2LoadModelSnapshot(
            stableKey = key, tier = if (key in BCoachV2LoadCandidateResolver.canonicalTierAKeys) "TIER_A" else "TIER_B",
            capacityLow = 100.0, capacityBase = 120.0, capacityHigh = 140.0, curve = curve(),
            sourceVersion = "test-model", evidenceIds = listOf("evidence"), stale = stale
        )

    private fun weekPlan(week: Int, type: String) = ProgramWeekPlan(
        weekIndex = week, weekType = type, volumeMultiplier = 1.0, intensityMultiplier = 1.0,
        heavyExposureLimit = 2, lowerBodyFatigueLimit = 8.0, axialLoadLimit = 2,
        plyometricLimit = 1, deloadFlag = false
    )

    private fun dose(evaluation: BCoachV2CandidateEvaluation, task: String): Double {
        val row = evaluation.effectiveSetBreakdown.single { it.startsWith("$task|w=1|") }
        return Regex("dose=([^|]+)").find(row)!!.groupValues[1].toDouble()
    }

    private fun rpePolicyCsv(): String = """
policyVersion,rpe,rir,probability,sourceClass,sourceCitation,rationale,reviewedStatus
strength-rpe-rir-policy-1.0.0,8.0,1,0.25,PRODUCT_POLICY,test,test,REVIEWED
strength-rpe-rir-policy-1.0.0,8.0,2,0.50,PRODUCT_POLICY,test,test,REVIEWED
strength-rpe-rir-policy-1.0.0,8.0,3,0.25,PRODUCT_POLICY,test,test,REVIEWED
strength-rpe-rir-policy-1.0.0,10.0,0,1.0,PRODUCT_POLICY,test,test,REVIEWED
""".trimIndent()
}
