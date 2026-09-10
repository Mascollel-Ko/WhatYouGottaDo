package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class FrequencyExpansionPlannerTest {
    private val f = PostGenerationFixture
    private val safeTissue = PlanWeekTissueProjection { _, _ -> PlannedTissueWeek(emptyList()) }
    private fun snapshot() = f.snapshot().copy(allConfirmedSets = f.snapshot().allConfirmedSets.map { it.copy(date = f.cutoff.minusDays(70)) },
        planDayProjection = f.safe, planWeekTissueProjection = safeTissue)
    private fun frequency(days: Int) = PlanningFrequencyProvenance(WeeklyDosePlanner().resolve(f.state(), 3).copy(recommendedDays = 3),
        days, PlanningFrequencySource.EXPLICIT_USER)
    private fun decision(days: Int) = PersonalizedPlanningDecision("test", "test", generatedAtEpochMillis = 0,
        historyCutoff = f.cutoff.toString(), historyWindowDays = 56, planningHorizonWeeks = 3, adaptationIntentMinWeeks = 3,
        adaptationIntentMaxWeeks = 3, observedTrainingBehavior = "MIXED", strengthIntent = "MIXED", strengthIntentProvenance = "TEST",
        badmintonIntent = "ENABLED", badmintonIntentProvenance = "TEST", primaryAdaptation = "TEST", secondaryTargets = emptyList(),
        strengthStyle = "NONE", strengthStyleProvenance = "TEST", weeklyFrequency = days, confidence = "HIGH", reasonCodes = emptyList(),
        reasons = emptyList(), constraints = emptyList(), metadataAuthorityVersion = "test",
        planningBudget = PlanningBudget(18.0, 18, 18, 0, 0, 1.0))
    private fun queue() = listOf("support" to 3, "other" to 4, "hinge" to 5).mapIndexed { i, (key, count) ->
        CapacityCandidateTrace(i + 4, f.source(key, count), f.rx(count), 0, false, CandidateRejectionReason.FINITE_CAPACITY) }
    private fun base(): GeneratedProgramSkeleton {
        val demand = listOf("press", "row", "squat").map { key -> AuthorizedSchedulingDemand(key, f.source(key, 6), f.rx(6), false) }
        val plan = f.plan(demand.mapIndexed { i, a -> residualItem(snapshot(), a.item, a.prescription, a.id, 1 + i * 2, 1) })
        return plan.copy(personalizedDecision = decision(3).copy(frequencyDemand = FrequencyDemandProvenance(frequency(3), queue(), demand, f.envelope(), 18)))
    }
    private fun expand(days: Int = 5, snapshot: PlanningHistorySnapshot = snapshot(), candidates: List<CapacityCandidateTrace> = queue(),
        forceSameDay: Boolean = false, retained: List<CapacityCandidateTrace> = emptyList()): GeneratedProgramSkeleton {
        val base = base().let { it.copy(personalizedDecision = it.personalizedDecision!!.copy(
            frequencyDemand = it.personalizedDecision!!.frequencyDemand!!.copy(candidates = candidates, retainedIncumbents = retained))) }
        val state = f.state(snapshot)
        val request = base.request.copy(weeklyTrainingDays = days)
        return FrequencyExpansionPlanner().expand(snapshot, state, request, base, frequency(days)) { authorized, envelope ->
            val selectedDays = if (days == 4) listOf(1, 3, 5, 7) else listOf(1, 2, 3, 5, 6)
            val allocation = SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).allocateAuthorized(snapshot, state, authorized,
                days, request.sessionMinutes, request)
            val sources = mutableListOf<PlannedExercise>()
            val rows = allocation.days.flatMap { (logical, atoms) -> atoms.mapIndexed { i, a ->
                sources += a.timed.item
                residualItem(snapshot, a.timed.item, a.timed.prescription, "a${sources.size}",
                    if (forceSameDay && a.timed.item.stableKey == "support") 1 else selectedDays[logical - 1], i + 1)
            } }
            val plan = f.plan(rows, selectedDays).copy(personalizedDecision = decision(days),
                weekPlans = listOf(ProgramWeekPlan(1, "NORMAL", 1.0, 1.0, 3, 1.0, 3, 3, false)))
            val atoms = f.atoms(plan)
            val sourceMap = plan.items.filter { it.weekNumber == 1 }.mapIndexed { i, row -> atoms.getValue(row.localId) to sources[i] }.toMap()
            ResidualCompletion().complete(plan, snapshot, state, emptyList(), authorized.map {
                AuthorizedPrescription(it.id, it.item, it.prescription, it.continuity) }, envelope, atoms, sourceMap, true, snapshot.planDayProjection)
        }
    }
    @Test fun exactIntegerCeilings() {
        for ((algorithm, user) in listOf(2 to 3, 2 to 5, 3 to 4, 3 to 5, 4 to 5)) {
            val expected = mapOf(2 to 3 to 69, 2 to 5 to 115, 3 to 4 to 61, 3 to 5 to 77, 4 to 5 to 58)
            assertEquals(expected[algorithm to user], frequencyExpandedTarget(46, algorithm, user))
        }
    }
    @Test fun autoEqualAndLowerSelectionsRetainTheExistingPath() {
        val snapshot = f.snapshot().copy(planDayProjection = f.safe)
        val state = f.state(snapshot)
        val gaps = AdaptationGapAnalyzer().analyze(snapshot, state)
        val intent = BlockIntentPlanner().decide(state, gaps)
        val request = f.plan(listOf(f.row("press", 1))).request
        fun build(frequency: PlanningFrequencyProvenance) = PersonalizedProgramBuilder().build(snapshot, state, gaps, intent,
            request.durationWeeks, request, PersonalizedPlanningAnswers(), null, frequency = frequency)
        val equal = build(frequency(3))
        val lower = build(frequency(3).copy(recommendation = frequency(3).recommendation.copy(recommendedDays = 4)))
        val auto = build(frequency(3).copy(source = PlanningFrequencySource.AUTO))
        listOf(equal, lower, auto).forEach {
            assertNull(it.personalizedDecision!!.frequencyExpansion)
            assertFalse(it.personalizedDecision!!.frequencyDemand!!.frequency.toJson().getBoolean("expansionActivated"))
            assertEquals(equal.items, it.items)
            assertEquals(equal.weekDaySchedule, it.weekDaySchedule)
        }
    }
    @Test fun frequencyReleasesOriginalRankedDemandWithoutScalingBase() {
        val four = expand(4).personalizedDecision!!.frequencyExpansion!!
        val fivePlan = expand(5)
        val five = fivePlan.personalizedDecision!!.frequencyExpansion!!
        assertTrue(four.toJson().toString(), 18 < four.actualFinalMaterializedUnits)
        assertTrue(five.toJson().toString(), four.actualFinalMaterializedUnits <= five.actualFinalMaterializedUnits)
        assertEquals(listOf(4, 5, 6), five.expansionAttempts.filter { it.action == "AUTHORIZED" }.map { it.rank })
        assertEquals(18, five.unitOrigins.count { it.fundingSource == PlanningFundingSource.BASE })
        assertEquals(30, five.actualFinalMaterializedUnits)
        assertEquals(12, five.finalExpandedUnits)
        assertEquals(30, five.unitOrigins.size)
        assertTrue(fivePlan.items.filter { it.weekNumber == 1 && it.exerciseStableKey in setOf("press", "row", "squat") }.all { it.setCount == 6 })
    }
    @Test fun queueExhaustionDoesNotCreateFiller() {
        val trace = expand(candidates = queue().take(2)).personalizedDecision!!.frequencyExpansion!!
        assertEquals(trace.toJson().toString(), 7, trace.finalExpandedUnits)
        assertEquals(25, trace.actualFinalMaterializedUnits)
        assertEquals(30, trace.mathematicalExpandedTarget)
    }
    @Test fun cutoffIncumbentsFollowFiniteCapacityWithoutLosingTheirReason() {
        val retained = queue().drop(1).map { it.copy(rejectionReason = CandidateRejectionReason.GLOBAL_ANCHOR_CUTOFF) }
        val plan = expand(candidates = queue().take(1), retained = retained)
        val trace = plan.personalizedDecision!!.frequencyExpansion!!
        assertEquals(listOf(4, 5, 6), trace.expansionAttempts.filter { it.action == "AUTHORIZED" }.map { it.rank })
        assertEquals("FINITE_CAPACITY_RECOVERED", trace.expansionAttempts.first().reason)
        assertEquals("GLOBAL_ANCHOR_CUTOFF_RECOVERED", trace.expansionAttempts[1].reason)
        assertTrue(plan.personalizedDecision!!.frequencyDemand!!.expansionSelectedKeys.contains("other"))
        assertEquals(18, trace.unitOrigins.count { it.fundingSource == PlanningFundingSource.BASE })
    }
    @Test fun excludesAndSafetyRejectionsNeverRevive() {
        val queue = queue().map { if (it.originalRank == 5) it.copy(rejectionReason = CandidateRejectionReason.SAFETY_OR_SEMANTIC_REJECTION) else it }
        val trace = expand(candidates = queue).personalizedDecision!!.frequencyExpansion!!
        assertEquals(listOf(4, 6), trace.capacityRejectedCandidates.map { it.originalRank })
        assertFalse(trace.unitOrigins.any { it.originalRank == 5 })
    }
    @Test fun absoluteOfiGateRollsBackLowestRankEvenWhenRatiosCouldBeInBand() {
        val projection = PlanDayProjection { rows -> StandaloneDayLoad(if (rows.any { it.exerciseStableKey == "hinge" }) 87 else 30, listOf(30)) }
        val plan = expand(snapshot = snapshot().copy(planDayProjection = projection))
        val trace = plan.personalizedDecision!!.frequencyExpansion!!
        assertTrue(trace.toJson().toString(), trace.rollbackActions.isNotEmpty())
        assertEquals(6, trace.rollbackActions.first().rank)
        assertEquals(7, trace.finalExpandedUnits)
        assertEquals(18, trace.unitOrigins.count { it.fundingSource == PlanningFundingSource.BASE })
        assertTrue(trace.expansionAttempts.any { it.action == "RELOCATION_REJECTED" })
    }
    @Test fun relocationKeepsExpansionAndEveryAcceptedMovePassesWholeWeekGates() {
        val projection = PlanDayProjection { rows -> StandaloneDayLoad(if (rows.any { it.exerciseStableKey == "support" } &&
            rows.any { it.exerciseStableKey == "press" }) 87 else 30, listOf(30)) }
        val trace = expand(snapshot = snapshot().copy(planDayProjection = projection), forceSameDay = true).personalizedDecision!!.frequencyExpansion!!
        assertTrue(trace.toJson().toString(), trace.expansionAttempts.any { it.action == "RELOCATED" })
        assertTrue(trace.rollbackActions.isEmpty())
        assertEquals(12, trace.finalExpandedUnits)
    }
    @Test fun missingProjectionFailsClosedWithoutShrinkingBase() {
        val trace = expand(snapshot = snapshot().copy(planWeekTissueProjection = null)).personalizedDecision!!.frequencyExpansion!!
        assertEquals(0, trace.finalExpandedUnits)
        assertEquals(18, trace.actualFinalMaterializedUnits)
        assertEquals(listOf(6, 5, 4), trace.rollbackActions.map { it.rank })
    }
    @Test fun legalPartialUsesOriginalPrescriptionAndStructuredPortionsRemainAtomic() {
        val snapshot = f.snapshot()
        val state = f.state(snapshot)
        val prescriptions = PersonalizedPrescriptionPlanner()
        val source = f.source("press", 4)
        val candidate = CapacityCandidateTrace(4, source, prescriptions.prescribe(snapshot, state.strengthIntent, source, source.style),
            2, true, CandidateRejectionReason.FINITE_CAPACITY)
        val portion = frequencyPortion(snapshot, state, candidate, 2, prescriptions)!!
        assertEquals(2, portion.sets.size)
        assertEquals(candidate.prescription.sets.take(2), portion.sets)
        assertNull(frequencyPortion(snapshot, state, candidate.copy(item = source.copy(styleVariant = "STRUCTURED")), 2, prescriptions))
        assertNull(frequencyPortion(snapshot, state, candidate.copy(prescription = candidate.prescription.copy(
            sets = candidate.prescription.sets.mapIndexed { index, set -> set.copy(weightKg = index * 10.0) })), 2, prescriptions))
    }
    @Test fun identicalPartialUnitsKeepExplicitParentRatherThanTakingBaseIdentityByDayOrder() {
        val source = f.source("press", 2)
        val base = AuthorizedSchedulingDemand("base", source, f.rx(2), true)
        val extra = base.copy(id = "extra", fundingSource = PlanningFundingSource.USER_FREQUENCY_EXPANSION, originalRank = 4)
        val plan = f.plan(listOf(f.row("press", 1, 2, id = "extra"), f.row("press", 3, 2, id = "base")))
        val atoms = f.atoms(plan)
        val week = RepresentativeWeek.derive(plan, atoms)!!
        val explicit = week.items.associate { atoms.getValue(it.localId) to AuthorizedAtomOrigin(if (it.dayOfWeek == 1) "extra" else "base") }
        val completed = CompletionResult(plan.copy(personalizedDecision = decision(3).copy(
            authorizedScheduling = AuthorizedSchedulingTrace(listOf(base, extra), emptyList(), explicit))),
            ResidualCompletionTrace("test", "", "", f.cutoff.toString()), week, explicit.keys.associateWith { source }, null)
        val origins = frequencyUnitOrigins(week.items, completed, listOf(base, extra))!!
        assertTrue(origins.filter { it.localId == week.items.first().localId }.all { it.authorizedDemandId == "extra" })
        assertEquals(2, origins.count { it.fundingSource == PlanningFundingSource.BASE })
        assertEquals(2, origins.count { it.fundingSource == PlanningFundingSource.USER_FREQUENCY_EXPANSION })
    }
}
