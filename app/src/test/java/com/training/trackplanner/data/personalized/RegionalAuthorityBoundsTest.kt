package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class RegionalAuthorityBoundsTest {
    private val f = PostGenerationFixture
    private val key = "hinge"
    private val role = "exact-regional-owner"
    private val target = RegionalStimulusTarget(MovementCoverage.POSTERIOR_CHAIN, TrainableQuality.STRENGTH,
        RegionalTargetAction.RESTORE, RegionalNumericAuthority.PRE_DECLINE_PERSONAL_PATTERN, 7.0)
    private fun rx(n: Int) = PlannedPrescription("authorized", List(n) { ProgramSetPrescription(it + 1, 3, 190.0, 0) }, 120, "AUTHORIZED")
    private fun authority() = RegionalExperimentalTargetPlan(
        MaterialDemand(listOf(f.source(key, 5).copy(role = role)), emptyMap(), emptyMap()),
        mapOf(key to target), emptySet(), authorizedPrescriptionBySelectionRole =
            mapOf(RegionalSelectionIdentity(key, role) to rx(5)))

    @Test fun mergeCannotRelabelOrdinaryQuantityAsRegional() {
        val ordinary = f.source(key,9).copy(role="ordinary")
        val plan = authority()
        val merge = Class.forName("com.training.trackplanner.data.personalized.PersonalizedProgramBuilderKt")
            .getDeclaredMethod("mergeTypedMaterialDemand", MaterialDemand::class.java, MaterialDemand::class.java, Set::class.java)
        merge.isAccessible = true
        val result = merge.invoke(null, MaterialDemand(listOf(ordinary),emptyMap(),emptyMap()), plan.demand, setOf(RegionalSelectionIdentity(key, role))) as MaterialDemand
        assertEquals(5, result.candidates.single { it.role==role }.targetSets)
        assertEquals(9, result.candidates.single { it.role=="ordinary" }.targetSets)
    }

    @Test fun regionalPrescriptionCannotManufactureUnitsBeyondFive() {
        val plan = authority()
        val result = runCatching { plan.authorizedPrescriptionFor(f.source(key, 9).copy(role = role)) }
        assertTrue("request 9 must be rejected or capped at authorization 5", result.isFailure || result.getOrNull()!!.sets.size <= 5)
    }

    @Test fun realBuilderSpareCapacityCannotExpandRegionalAuthorization() {
        assertEquals(RegionalTrainingDecision.RESTORE_STRENGTH_EXPOSURE,
            RegionalTrainingDecisionResolver().resolve(diagnosis(TrainingResponseState.STABLE_RESPONSE, false)).decision)
        val base = f.snapshot()
        val records = (0..7).flatMap { week -> (1..3).flatMap { day -> (1..10).map { set ->
            PlanningSetRecord(base.cutoff.minusDays(week * 7L + day), "press", "press", "RESISTANCE", set, 5, 80.0, 0, 7.0)
        } } }
        val snapshot = base.copy(allConfirmedSets = records, planDayProjection = f.safe,
            planWeekTissueProjection = PlanWeekTissueProjection { _, _ -> PlannedTissueWeek(emptyList()) })
        val initial = f.state(snapshot)
        val state = initial.copy(anchors = initial.anchors.map { it.copy(styleConfidence = PlanningConfidence.LOW) })
        val gaps = listOf(AdaptationGap("TEST_PRESSURE", "HIGH", "synthetic exposure limitation"))
        val request = f.plan(emptyList(), listOf(1,2,4,6,7), minutes = 180).request
        val result = PersonalizedProgramBuilder().build(snapshot, state, gaps, BlockIntentPlanner().decide(state,gaps),
            request.durationWeeks, request, PersonalizedPlanningAnswers(), null, explicitWeeklyDays = false,
            regionalTargetPlan = authority())
        val candidate = result.personalizedDecision!!.frequencyDemand!!.candidates.single { it.item.role == role }
        assertEquals(5, candidate.requestedUnits)
        assertTrue("funded regional units=" + candidate.fundedBaseUnits, candidate.fundedBaseUnits <= 5)
        assertEquals(5, candidate.fundedBaseUnits)
        val finalUnits = result.items.filter { it.weekNumber == 1 && it.selectionRole == role }.sumOf { it.setPrescriptions.size }
        assertEquals(5, finalUnits)
    }

    @Test fun sameStableKeyOrdinarySetsAreNotRegionalMaterialization() {
        val snapshot = f.snapshot()
        val ordinary = residualItem(snapshot, f.source(key,2).copy(role="ordinary"), rx(2), "ordinary", 1, 1)
        val regional = residualItem(snapshot, f.source(key,5).copy(role=role), rx(5), "regional", 3, 1)
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(ExercisePhysicalQualityRelation(
            "test", key, TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.LOWER, PhysicalQualityMode.HINGE, true, "TEST", setOf("TEST"), "PASS", "")))
        val result = FinalRegionalStimulusProjector().project(target, f.plan(listOf(ordinary,regional)), snapshot,
            catalog, RegionalSelectionIdentity(key, role), 2, 5, 5)
        assertEquals("ordinary 2 + regional 5 must report regional 5", 5, result.targetCompatibleMaterializedUnits)
        assertEquals(2, result.ordinarySameKeyCompatibleUnits)
        assertEquals(0, result.overrunUnits)
        val invalid = FinalRegionalStimulusProjector().project(target,
            f.plan(listOf(ordinary, regional.copy(setPrescriptions = rx(6).sets))), snapshot,
            catalog, RegionalSelectionIdentity(key, role), 2, 5, 5)
        assertEquals(6, invalid.targetCompatibleMaterializedUnits)
        assertEquals(1, invalid.overrunUnits)
        assertEquals("REGIONAL_AUTHORIZATION_OVERRUN", invalid.reasonCode)
    }

    @Test fun everyNumericRegionalTraceStaysWithinItsOwnResidual() {
        val base = f.snapshot()
        val history = (0..7).flatMap { week -> (1..3).flatMap { day -> (1..10).map { set ->
            PlanningSetRecord(base.cutoff.minusDays(week * 7L + day), "press", "press", "RESISTANCE", set, 5, 80.0, 0, 7.0)
        } } }
        val snapshot = base.copy(allConfirmedSets = history + listOf("hinge", "squat").map {
            PlanningSetRecord(base.cutoff.minusDays(10), it, it, "RESISTANCE", 1, 3, 100.0, 0, 7.0)
        }, planDayProjection = f.safe, planWeekTissueProjection = PlanWeekTissueProjection { _, _ -> PlannedTissueWeek(emptyList()) })
        val diagnoses = listOf(
            diagnosis(TrainingResponseState.STABLE_RESPONSE, false),
            diagnosis(TrainingResponseState.STABLE_RESPONSE, false, true).copy(region = MovementCoverage.LOWER_KNEE))
        val catalog = CanonicalExercisePhysicalQualityCatalog.of(listOf(
            ExercisePhysicalQualityRelation("hinge-test", "hinge", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                PhysicalQualityRegion.LOWER, PhysicalQualityMode.HINGE, true, "TEST", setOf("TEST"), "PASS", ""),
            ExercisePhysicalQualityRelation("squat-test", "squat", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT, true, "TEST", setOf("TEST"), "PASS", "")))
        val initial = f.state(snapshot)
        val state = initial.copy(anchors = initial.anchors.map { it.copy(styleConfidence = PlanningConfidence.LOW) })
        val request = f.plan(emptyList(), listOf(1,2,4,6,7), minutes = 180).request
        val demand = RegionalExperimentalMaterialDemandBuilder().build(diagnoses, f.plan(emptyList()), snapshot, state, request, catalog)
        assertEquals(2, demand.traces.count { it.authorizedUnits > 0 })
        val plan = PersonalizedProgramBuilder().build(snapshot, state, emptyList(), BlockIntentPlanner().decide(state, emptyList()),
            request.durationWeeks, request, PersonalizedPlanningAnswers(), null, explicitWeeklyDays = false, regionalTargetPlan = demand.targetPlan)
        demand.traces.forEach { trace ->
            val target = RegionalStimulusTargetResolver().resolve(trace.diagnosis)
            val projection = FinalRegionalStimulusProjector().project(target, plan, snapshot, catalog, trace.selectedIdentity,
                trace.existingPlannedCompatibleDose, trace.residualDose, trace.authorizedUnits)
            assertTrue("${trace.region} materialized <= authorized", projection.targetCompatibleMaterializedUnits <= trace.authorizedUnits)
            assertTrue("${trace.region} authorized <= residual", trace.authorizedUnits <= trace.residualDose)
            assertEquals(0, projection.overrunUnits)
        }
        // Parent provenance must win if a presentation role is stale after downstream processing.
        val scheduling = requireNotNull(plan.personalizedDecision?.authorizedScheduling)
        val row = plan.items.first { it.localId in scheduling.localOrigins }
        val owner = regionalSelectionIdentity(plan, row)
        val renamed = row.copy(selectionRole = "misleading-display-role")
        assertEquals(owner, regionalSelectionIdentity(plan, renamed))
    }

    @Test fun fiveAuthorizedThreeFundedFrequencyCanRecoverOnlyTwo() {
        val plan = authority()
        val item = plan.demand.candidates.single()
        val snapshot = f.snapshot()
        val trace = capacityCandidateTrace(snapshot, f.state(snapshot), listOf(item to false),
            listOf(item.copy(targetSets = 3)), PersonalizedPrescriptionPlanner()) { plan.authorizedPrescriptionFor(it) }.single()
        assertEquals(2, trace.remainingUnits)
        for (requested in 1..9) {
            val portion = frequencyPortion(snapshot, f.state(snapshot), trace, requested, PersonalizedPrescriptionPlanner())!!
            assertEquals(minOf(requested, 2), portion.sets.size)
            assertEquals(rx(5).sets.drop(3).take(minOf(requested, 2)).mapIndexed { i, set -> set.copy(setIndex = i + 1) }, portion.sets)
            assertEquals(rx(5).restSeconds, portion.restSeconds)
            assertEquals(rx(5).weightSource, portion.weightSource)
        }
    }

    private fun diagnosis(response: TrainingResponseState, recovery: Boolean, morphology: Boolean = false): RegionalBottleneckDiagnosis {
        val band = RegionalDoseBand(eligibleWeekCount=4, weeklyUnitsMedian=7.0, exposureWeekUnitsMedian=7.0,
            directExposureWeekCount=4, directExposureWeekFrequency=1.0, previous28dWeeklyUnitsMedian=7.0,
            previous28dExposureWeekUnitsMedian=7.0, source=SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS)
        val factor = if(morphology) RegionalLimitingFactor.MORPHOLOGICAL_CAPACITY_POSSIBLY_LIMITING else RegionalLimitingFactor.EXPOSURE_LIMITED
        return RegionalBottleneckDiagnosis(region=MovementCoverage.POSTERIOR_CHAIN, requirement=NeedRelevance.MODERATE,
            performanceResponse=response, validStrengthObservationCount=0, strengthExposureStatus=RegionalStrengthExposureStatus.LOW,
            strengthDoseBand=band, strengthExposureFrequency=1.0, specificityContinuity=SpecificityContinuity.CONTINUOUS,
            hypertrophySupportStatus=RegionalHypertrophySupportStatus.LOW, hypertrophyDoseBand=band, hypertrophyExposureFrequency=1.0,
            recoveryConstraint=recovery, sportLoadInterference=false, limitingFactors=listOf(factor), primaryInterpretation=factor,
            confidence=PlanningConfidence.MODERATE)
    }
    private fun verifyRecovery(response: TrainingResponseState, expected: RegionalTrainingDecision) {
        val d=diagnosis(response,true)
        assertEquals(expected, RegionalTrainingDecisionResolver().resolve(d).decision)
        val t=RegionalStimulusTargetResolver().resolve(d)
        assertTrue(t.action in setOf(RegionalTargetAction.HOLD,RegionalTargetAction.PRESERVE))
        if (expected == RegionalTrainingDecision.HOLD_FOR_RECOVERY) {
            assertEquals(RegionalTargetAction.HOLD, t.action)
            assertEquals(RegionalNumericAuthority.NONE, t.numericAuthority)
        }
        val built=RegionalExperimentalMaterialDemandBuilder().build(listOf(d),f.plan(emptyList()),f.snapshot(),f.state(),
            f.plan(emptyList()).request,CanonicalExercisePhysicalQualityCatalog.of(emptyList()))
        assertTrue(built.demand.candidates.isEmpty())
        assertTrue(built.traces.all { it.authorizedUnits==0 && it.residualDose==0 })
    }
    @Test fun positiveRecoveryPreserves() = verifyRecovery(TrainingResponseState.POSITIVE_RESPONSE,RegionalTrainingDecision.PRESERVE_EFFECTIVE_STRENGTH)
    @Test fun stableRecoveryHolds() = verifyRecovery(TrainingResponseState.STABLE_RESPONSE,RegionalTrainingDecision.HOLD_FOR_RECOVERY)
    @Test fun negativeRecoveryHolds() = verifyRecovery(TrainingResponseState.NEGATIVE_RESPONSE,RegionalTrainingDecision.HOLD_FOR_RECOVERY)
    @Test fun insufficientRecoveryHolds() = verifyRecovery(TrainingResponseState.INSUFFICIENT_EVIDENCE,RegionalTrainingDecision.HOLD_FOR_RECOVERY)
    @Test fun nonRecoveryExposureStillRestores() {
        assertEquals(RegionalTrainingDecision.RESTORE_STRENGTH_EXPOSURE,
            RegionalTrainingDecisionResolver().resolve(diagnosis(TrainingResponseState.STABLE_RESPONSE,false)).decision)
    }
    @Test fun nonRecoveryMorphologyStillAddsSupport() {
        assertEquals(RegionalTrainingDecision.ADD_HYPERTROPHY_SUPPORT,
            RegionalTrainingDecisionResolver().resolve(diagnosis(TrainingResponseState.STABLE_RESPONSE,false,true)).decision)
    }
}
