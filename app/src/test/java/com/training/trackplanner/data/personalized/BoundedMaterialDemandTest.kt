package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/** Calls the real builder core with its existing capacity override; no fake allocation/placement callback. */
class BoundedMaterialDemandTest {
    private val f = PostGenerationFixture
    private fun build(capacity: Int, b: Int, c: Int, anchor: Boolean = false, experimental: Boolean = true, publicExpansion: Boolean = false, excluded: Set<String> = emptySet()): GeneratedProgramSkeleton {
        val base = f.snapshot()
        val history = (0..7).flatMap { week -> (1..3).map { day ->
            PlanningSetRecord(base.cutoff.minusDays(week * 7L + day), "press", "press", "RESISTANCE", 1, 5, 80.0, 0, 7.0)
        } } + listOf("row", "squat").map {
            PlanningSetRecord(base.cutoff.minusDays(70), it, it, "RESISTANCE", 1, 8, 50.0, 0, 7.0)
        }
        val snapshot = base.copy(allConfirmedSets = history, planDayProjection = f.safe,
            planWeekTissueProjection = PlanWeekTissueProjection { _, _ -> PlannedTissueWeek(emptyList()) })
        val initial = f.state(snapshot)
        val state = initial.copy(anchors = if (anchor) initial.anchors.filter { it.stableKey == "press" }
            .map { it.copy(styleConfidence = PlanningConfidence.LOW) } else emptyList())
        if (anchor) assertEquals(1, state.anchors.size)
        assertFalse(state.recoverySignals.isConstrained)
        val candidates = listOf(f.source("hinge",5, priority=100).copy(role="regional-A"),
            f.source("row",b, priority=90).copy(role="ordinary-B"),
            f.source("squat",c, priority=80).copy(role="ordinary-C"))
        val demand = MaterialDemand(candidates, emptyMap(), emptyMap())
        val rx = PlannedPrescription("regional exact", List(5) { ProgramSetPrescription(it+1,3,190.0,0) },120,"REGIONAL_AUTHORIZED")
        val regional = RegionalExperimentalTargetPlan(demand, emptyMap(), emptySet(),
            authorizedPrescriptionBySelectionRole=mapOf(RegionalSelectionIdentity("hinge","regional-A") to rx))
        val request = f.plan(emptyList(), listOf(1,2,4,6,7), minutes=180).request.copy(excludedExerciseStableKeys = excluded)
        val gaps = listOf(AdaptationGap("TEST_PRESSURE", "HIGH", "synthetic non-recovery demand"))
        val frequency = PlanningFrequencyProvenance(WeeklyDosePlanner().resolve(state,3),5,PlanningFrequencySource.AUTO)
        if (publicExpansion) return PersonalizedProgramBuilder().build(snapshot, state, gaps, BlockIntentPlanner().decide(state,gaps),
            request.durationWeeks, request, PersonalizedPlanningAnswers(), null, true,
            frequency.copy(recommendation = frequency.recommendation.copy(recommendedDays = 3), source = PlanningFrequencySource.EXPLICIT_USER),
            regionalTargetPlan = regional)
        val method = PersonalizedProgramBuilder::class.java.declaredMethods.single { it.name == "buildCore" }
        method.isAccessible = true
        return method.invoke(PersonalizedProgramBuilder(), snapshot, state, gaps, BlockIntentPlanner().decide(state,gaps),
            request.durationWeeks, request, PersonalizedPlanningAnswers(), null, true, frequency,
            null, f.envelope(capacity), PersonalizedPrescriptionPlanner(), PersonalizedPlannerProgressReporter.NONE,
            PlannerPerformanceMetrics(), if (experimental) null else demand, if (experimental) regional else null, null) as GeneratedProgramSkeleton
    }
    private fun units(plan: GeneratedProgramSkeleton, role: String) = plan.items.filter {
        it.weekNumber==1 && it.selectionRole==role }.sumOf { it.setPrescriptions.size }
    private fun audit(plan: GeneratedProgramSkeleton): JSONObject {
        val json=plan.personalizedDecision!!.frequencyDemand!!.toJson()
        return requireNotNull(json.optJSONObject("boundedMaterialAllocation")) { "Missing explicit demand maximum / unused-capacity audit" }
    }
    private fun report(label: String, plan: GeneratedProgramSkeleton) {
        plan.personalizedDecision!!.frequencyDemand!!.boundedMaterialAllocation?.let { trace ->
            assertTrue(trace.owners.all { it.finalFundedUnits <= it.maximumUnits && it.materializedUnits <= it.finalFundedUnits })
            for (candidate in plan.personalizedDecision!!.frequencyDemand!!.candidates.filter { !it.continuity && it.item.material }) {
                val original=candidate.prescription
                plan.items.filter { it.selectionRole==candidate.item.role && it.exerciseStableKey==candidate.item.stableKey }.forEach { row ->
                    assertEquals(original.restSeconds,row.restSeconds);assertEquals(original.weightSource,row.weightSource)
                    assertTrue(row.setPrescriptions.all { set -> original.sets.any { it.copy(setIndex=0)==set.copy(setIndex=0) } })
                }
            }
        }
        println("CAPACITY_CASE $label A=${units(plan,"regional-A")} B=${units(plan,"ordinary-B")} C=${units(plan,"ordinary-C")} total=${plan.items.filter { it.weekNumber==1 }.sumOf { it.setPrescriptions.size }}")
        println("CAPACITY_TRACE $label ${plan.personalizedDecision!!.frequencyDemand!!.toJson()}")
    }
    @Test fun otherLegitimateDemandUsesNineteenUnits() {
        val plan=build(19,6,8);report("A",plan)
        assertEquals(5,units(plan,"regional-A"));assertEquals(6,units(plan,"ordinary-B"));assertEquals(8,units(plan,"ordinary-C"))
        assertEquals(19,plan.items.filter { it.weekNumber==1 }.sumOf { it.setPrescriptions.size })
        assertEquals(0,audit(plan).getInt("unusedCapacityUnits"))
    }
    @Test fun unusedCapacityIsValidAtThirteenOfNineteen() {
        val plan=build(19,4,4);report("B",plan)
        assertEquals(5,units(plan,"regional-A"));assertEquals(4,units(plan,"ordinary-B"));assertEquals(4,units(plan,"ordinary-C"))
        assertEquals(13,plan.items.filter { it.weekNumber==1 }.sumOf { it.setPrescriptions.size })
        assertEquals(6,audit(plan).getInt("unusedCapacityUnits"))
    }
    @Test fun deferredCandidateIsReconsideredBeforeFillerExpansion() {
        val plan=build(19,4,10,anchor=true);report("DEFERRED",plan)
        assertEquals(5,units(plan,"regional-A"));assertEquals(4,units(plan,"ordinary-B"))
        assertEquals(9,units(plan,"ordinary-C"))
        assertTrue(plan.items.filter { it.weekNumber==1 }.sumOf { it.setPrescriptions.size } <=19)
    }
    @Test fun exhaustedDemandStopsDespiteSpareCapacity() {
        val plan=build(40,4,4,anchor=true);report("EXHAUSTED",plan)
        assertEquals(5,units(plan,"regional-A"));assertEquals(4,units(plan,"ordinary-B"));assertEquals(4,units(plan,"ordinary-C"))
        assertTrue(audit(plan).getInt("unusedCapacityUnits")>0)
    }
    @Test fun captureControlBeforeAnyProductionEdit() {
        val plan=build(40,4,4,anchor=true,experimental=false);report("CONTROL",plan)
        assertEquals("dee9d47df5990278160a076c6627c1cb16f7ffd7d2778505e0c15a7ab9d2fb7c", personalizedProgramFingerprint(plan.request,plan.items))
        assertNull(plan.personalizedDecision!!.frequencyDemand!!.toJson().optJSONObject("boundedMaterialAllocation"))
    }
    @Test fun tenUnitsRetainPriorityAndNeverExceedAnyMaximum() {
        val plan=build(10,4,4); report("C",plan)
        assertEquals(5,units(plan,"regional-A")); assertEquals(4,units(plan,"ordinary-B")); assertEquals(1,units(plan,"ordinary-C"))
        assertEquals(0,audit(plan).getInt("unusedCapacityUnits"))
    }
    @Test fun frequencyCanReleaseOnlyTheOriginalUnfundedCanonicalSubset() {
        val plan=build(19,4,10,anchor=true)
        val candidate=plan.personalizedDecision!!.frequencyDemand!!.candidates.single { it.item.role=="ordinary-C" }
        assertEquals(10,candidate.requestedUnits); assertEquals(9,candidate.fundedBaseUnits)
        val snapshot=f.snapshot().let { it.copy(allConfirmedSets=it.allConfirmedSets +
            PlanningSetRecord(it.cutoff.minusDays(70), "squat", "squat", "RESISTANCE",1,8,50.0,0,7.0)) }
        val portion=frequencyPortion(snapshot,f.state(snapshot),candidate,100,PersonalizedPrescriptionPlanner())!!
        assertEquals(1,portion.sets.size)
        assertEquals(candidate.prescription.sets.last().copy(setIndex=1),portion.sets.single())
        assertEquals(candidate.prescription.restSeconds,portion.restSeconds)
        assertEquals(candidate.prescription.weightSource,portion.weightSource)
        assertNull(frequencyPortion(snapshot,f.state(snapshot),candidate.copy(fundedBaseUnits=10),100,PersonalizedPrescriptionPlanner()))
    }
    @Test fun extraUserDaysInPublicBuilderDoNotRaiseMaterialMaxima() {
        val plan=build(40,4,4,anchor=true,publicExpansion=true); report("FREQUENCY",plan)
        assertNotNull(plan.personalizedDecision!!.frequencyExpansion)
        val trace=plan.personalizedDecision!!.frequencyDemand!!.boundedMaterialAllocation!!
        assertTrue(trace.owners.all { it.finalFundedUnits<=it.maximumUnits && it.materializedUnits<=it.finalFundedUnits })
        assertTrue(units(plan,"regional-A")<=5);assertTrue(units(plan,"ordinary-B")<=4);assertTrue(units(plan,"ordinary-C")<=4)
        assertTrue(plan.personalizedDecision!!.residualCompletion!!.additions.isEmpty())
    }
    @Test fun deferredReconsiderationPreservesExclusions() {
        val plan=build(19,4,10,anchor=true,excluded=setOf("squat"))
        assertEquals(0,units(plan,"ordinary-C"));assertEquals(4,units(plan,"ordinary-B"))
        val c=plan.personalizedDecision!!.frequencyDemand!!.candidates.single { it.item.role=="ordinary-C" }
        assertEquals(CandidateRejectionReason.SAFETY_OR_SEMANTIC_REJECTION,c.rejectionReason)
    }
    @Test fun boundedCompletionRestoresExactOrdinaryParentWithoutSemanticFiller() {
        val snapshot=f.snapshot(); val source=f.source("squat",3,"LOWER_KNEE")
        val parent=AuthorizedPrescription("parent",source,f.rx(3,45,10),false)
        val initial=f.plan(listOf(f.row("squat",1,2).copy(restSeconds=10)))
        val result=ResidualCompletion().complete(initial,snapshot,f.state(snapshot),listOf(AdaptationGap("LOWER_KNEE","HIGH","test")),
            listOf(parent),f.envelope(100),f.atoms(initial),mapOf("atom_0" to source),true,f.safe,
            mapOf("atom_0" to AuthorizedAtomOrigin("parent")),exactAuthorizationOnly=true)
        assertEquals(3,result.skeleton.items.filter { it.weekNumber==1 }.sumOf { it.setPrescriptions.size })
        assertTrue(result.trace.restorations.isNotEmpty());assertTrue(result.trace.additions.isEmpty())
        assertEquals(parent.prescription.sets,result.skeleton.items.first().setPrescriptions)
    }

    @Test fun canonicalMinimumCannotEnlargeOneRequestedUnit() {
        val snapshot=f.snapshot();val item=f.source("other",1)
        val allocation=BoundedMaterialDemandAllocation(snapshot,f.state(snapshot),f.plan(emptyList()).request,listOf(item),
            RegionalExperimentalTargetPlan(MaterialDemand(listOf(item),emptyMap(),emptyMap()),emptyMap(),emptySet()),
            PersonalizedPrescriptionPlanner(),100,0,0)
        assertEquals(listOf(0),allocation.finite.material)
        assertEquals(1,allocation.bounds.single().maximumUnits)
        assertEquals("PRESCRIPTION_EXCEEDS_DEMAND_MAXIMUM",allocation.bounds.single().rejection)
        assertEquals(CandidateRejectionReason.SAFETY_OR_SEMANTIC_REJECTION,allocation.candidates.single().rejectionReason)
    }
    @Test fun deferredAtomicVariantCannotBeShrunkToFillSpareCapacity() {
        val snapshot=f.snapshot();val item=f.source("row",4).copy(styleVariant="LIGHT")
        val allocation=BoundedMaterialDemandAllocation(snapshot,f.state(snapshot),f.plan(emptyList()).request,listOf(item),
            RegionalExperimentalTargetPlan(MaterialDemand(listOf(item),emptyMap(),emptyMap()),emptyMap(),emptySet()),
            PersonalizedPrescriptionPlanner(),3,0,0)
        assertEquals(listOf(0),allocation.finite.material)
        assertEquals(4,allocation.candidates.single().remainingUnits)
        assertEquals(CandidateRejectionReason.FINITE_CAPACITY,allocation.candidates.single().rejectionReason)
    }

}
