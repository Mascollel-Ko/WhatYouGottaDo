package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class RegionalFrequencyPrescriptionTest {
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
        val demand = listOf("press", "row", "other").map { key -> AuthorizedSchedulingDemand(key, f.source(key, 6), f.rx(6), false) }
        val plan = f.plan(demand.mapIndexed { i, a -> residualItem(snapshot(), a.item, a.prescription, a.id, 1 + i * 2, 1) })
        return plan.copy(personalizedDecision = decision(3).copy(frequencyDemand = FrequencyDemandProvenance(frequency(3), queue(), demand, f.envelope(), 18)))
    }
    private fun expand(days: Int = 5, snapshot: PlanningHistorySnapshot = snapshot(), candidates: List<CapacityCandidateTrace> = queue(),
        forceSameDay: Boolean = false, regionalBase: AuthorizedSchedulingDemand? = null, retained: List<CapacityCandidateTrace> = emptyList()): GeneratedProgramSkeleton {
        val base = base().let { original ->
            if (regionalBase == null) original else original.copy(personalizedDecision = original.personalizedDecision!!.copy(
                frequencyDemand = original.personalizedDecision!!.frequencyDemand!!.copy(
                    baseAuthorized = original.personalizedDecision!!.frequencyDemand!!.baseAuthorized + regionalBase)))
        }.let { it.copy(personalizedDecision = it.personalizedDecision!!.copy(
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
    @Test fun lowerKneeHypertrophySurvivesPartialCapacityAndFiveDayExpansion() = verifyRegional(
        "squat", MovementCoverage.LOWER_KNEE, TrainableQuality.HYPERTROPHY, PhysicalQualityMode.SQUAT, 10, 70.0)

    @Test fun posteriorStrengthSurvivesPartialCapacityAndFiveDayExpansion() = verifyRegional(
        "hinge", MovementCoverage.POSTERIOR_CHAIN, TrainableQuality.STRENGTH, PhysicalQualityMode.HINGE, 4, 120.0)

    private fun verifyRegional(key: String, region: MovementCoverage, quality: TrainableQuality,
        mode: PhysicalQualityMode, reps: Int, load: Double) {
        val snapshot = snapshot()
        val state = f.state(snapshot)
        val item = f.source(key, 3).copy(role = "exact-target-owner")
        val target = RegionalStimulusTarget(region, quality, RegionalTargetAction.ADD_SUPPORT,
            RegionalNumericAuthority.FULL_WINDOW_PERSONAL_BAND, weeklyDoseTarget = 3.0)
        val rx = PlannedPrescription("authorized", List(3) { ProgramSetPrescription(it + 1, reps, load, 0) }, 90, "TARGET_TEST")
        val plan = RegionalExperimentalTargetPlan(MaterialDemand(listOf(item), emptyMap(), emptyMap()),
            mapOf(key to target), emptySet(), authorizedPrescriptionBySelectionRole =
                mapOf(RegionalSelectionIdentity(key, item.role) to rx))
        val funded = item.copy(targetSets = 1)
        val traces = capacityCandidateTrace(snapshot, state, listOf(item to false),
            listOf(item.copy(role = "other-owner", targetSets = 3), funded), PersonalizedPrescriptionPlanner()) {
            plan.authorizedPrescriptionFor(it)
        }
        val candidate = traces.single()
        assertEquals(rx, candidate.prescription)
        assertEquals(PrescriptionAuthoritySource.REGIONAL_TARGET_AUTHORIZED, candidate.prescriptionAuthority)
        assertEquals(1, candidate.fundedBaseUnits)
        assertEquals(2, candidate.remainingUnits)
        for (limit in 1..2) {
            val portion = frequencyPortion(snapshot, state, candidate, limit, PersonalizedPrescriptionPlanner())!!
            assertEquals(rx.sets.drop(1).take(limit).mapIndexed { i, set -> set.copy(setIndex = i + 1) }, portion.sets)
            assertEquals(rx.restSeconds, portion.restSeconds)
            assertEquals(rx.weightSource, portion.weightSource)
        }
        val ordinary = item.copy(role = "other-owner")
        val ordinaryTrace = capacityCandidateTrace(snapshot, state, listOf(ordinary to false), emptyList(),
            PersonalizedPrescriptionPlanner()) { plan.authorizedPrescriptionFor(it) }.single()
        assertEquals(PrescriptionAuthoritySource.PRODUCTION_CANONICAL, ordinaryTrace.prescriptionAuthority)
        assertEquals(PersonalizedPrescriptionPlanner().prescribe(snapshot, state.strengthIntent, ordinary, ordinary.style), ordinaryTrace.prescription)
        val baseDemand = AuthorizedSchedulingDemand("regional-base", funded, plan.authorizedPrescriptionFor(funded)!!, false)
        val expanded = expand(candidates = listOf(candidate), regionalBase = baseDemand)
        val trace = expanded.personalizedDecision!!.frequencyExpansion!!
        assertEquals(3, trace.algorithmRecommendedDays)
        assertEquals(5, trace.userSelectedDays)
        assertEquals(2, trace.finalExpandedUnits)
        val targetRows = expanded.items.filter { it.weekNumber == 1 && it.selectionRole == item.role }
        assertEquals(3, targetRows.sumOf { it.setPrescriptions.size })
        assertTrue(targetRows.flatMap { it.setPrescriptions }.all { it.reps == reps && it.weightKg == load })
        val relation = ExercisePhysicalQualityRelation("test", key, quality, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.LOWER, mode, true, "TEST", setOf("TEST"), "PASS", "")
        val projection = FinalRegionalStimulusProjector().project(target, expanded, snapshot,
            CanonicalExercisePhysicalQualityCatalog.of(listOf(relation)), RegionalSelectionIdentity(key, item.role), 0, 3, 3)
        assertEquals(3, projection.targetCompatibleMaterializedUnits)
        assertEquals(0, projection.shortfall)
    }
}
