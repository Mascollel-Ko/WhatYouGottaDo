package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class PrimaryStrengthAnchorSpacingPolicyTest {
    private val f = PostGenerationFixture
    private fun state(snapshot: PlanningHistorySnapshot) = f.state(snapshot).copy(anchors = listOf(
        UserAnchor("press", "arbitrary label", 4, 9, "ignored", "LOAD_REPS", "UNKNOWN", 12.0)))

    @Test fun cyclicCalendarDistanceNotLogicalSlotDistance() {
        for (pair in listOf(1 to 2, 6 to 7, 7 to 1, 1 to 1))
            assertFalse(PrimaryStrengthAnchorSpacingPolicy.allowed(listOf(pair.first,pair.second)))
        for (pair in listOf(1 to 3, 6 to 1, 2 to 5))
            assertTrue(PrimaryStrengthAnchorSpacingPolicy.allowed(listOf(pair.first,pair.second)))
        assertTrue(PrimaryStrengthAnchorSpacingPolicy.allowed(listOf(1,3,5)))
    }

    @Test fun exactObservedContinuityResistanceAndAllSixCanonicalCoveragesRequired() {
        val base=f.snapshot()
        val slots=listOf("MAIN_LOWER_STRENGTH", "MAIN_HINGE_STRENGTH", "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY",
            "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY", "HORIZONTAL_PULL_STRENGTH", "VERTICAL_PULL_STRENGTH")
        for(slot in slots) {
            val snapshot=base.copy(metadata=base.metadata + ("press" to base.metadata.getValue("press").copy(programSlot=slot)))
            assertTrue(PrimaryStrengthAnchorSpacingPolicy.protects(snapshot,state(snapshot),"press",true))
            assertFalse(PrimaryStrengthAnchorSpacingPolicy.protects(snapshot,state(snapshot),"press",false))
            assertFalse(PrimaryStrengthAnchorSpacingPolicy.protects(snapshot,state(snapshot).copy(anchors=emptyList()),"press",true))
        }
        for(slot in listOf("ANKLE_CALF_SUPPORT", "CORE_STABILITY", "BICEPS_ACCESSORY", "TRICEPS_ACCESSORY", "OTHER")) {
            val snapshot=base.copy(metadata=base.metadata + ("press" to base.metadata.getValue("press").copy(programSlot=slot)))
            assertFalse(PrimaryStrengthAnchorSpacingPolicy.protects(snapshot,state(snapshot),"press",true))
        }
        val drill=base.copy(metadata=base.metadata + ("press" to base.metadata.getValue("press").copy(activityKind="STRUCTURED_BADMINTON_DRILL")))
        assertFalse(PrimaryStrengthAnchorSpacingPolicy.protects(drill,state(drill),"press",true))
    }

    @Test fun mandatorySixThroughNineUseLegalActualWeekdaysAndConserveVolume() {
        val snapshot=f.snapshot()
        for(days in 2..5) for(count in 6..9) {
            val parent=AuthorizedSchedulingDemand("p",f.source("press",count),f.rx(count),true)
            val initial=(1..days).associateWith { emptyList<AuthorizedTimedAtom>() }
            val placed=MandatoryContinuityPlacement(snapshot,state(snapshot),days,90).place(parent,initial,PersonalizedPrescriptionPlanner())
            val actual=RecordBasedReviewedPolicy.defaultSchedule(1,days).getValue(1).sorted()
            val occupied=placed.days.filterValues { it.isNotEmpty() }.keys.map { actual[it-1] }
            assertTrue(PrimaryStrengthAnchorSpacingPolicy.allowed(occupied))
            assertEquals(count,placed.days.values.flatten().sumOf { it.timed.prescription.sets.size })
        }
    }

    @Test fun adjacentOnlyAvailabilityKeepsExactCanonicalShortfallEvenWhenOfiIsAdvisory() {
        val snapshot=f.snapshot().copy(planDayProjection=PlanDayProjection { StandaloneDayLoad(99,listOf(100)) })
        val parent=AuthorizedSchedulingDemand("p",f.source("press",9),f.rx(9,seconds=60),true)
        // Four-day calendar is Mon/Tue/Thu/Sat. Block Thu/Sat: Mon+Tue must not become two anchor exposures.
        val blocker=AuthorizedTimedAtom(TimedPlannedExercise(f.source("other",3),f.rx(3,seconds=60)),AuthorizedAtomOrigin("block"))
        val result=MandatoryContinuityPlacement(snapshot,state(snapshot),4,3).place(parent,
            mapOf(1 to emptyList(),2 to emptyList(),3 to listOf(blocker),4 to listOf(blocker)),PersonalizedPrescriptionPlanner())
        val chunks=result.days.values.flatten().filter { it.origin.authorizedDemandId=="p" }
        assertEquals(listOf(3),chunks.map { it.timed.prescription.sets.size })
        assertEquals(6,9-chunks.sumOf { it.timed.prescription.sets.size })
        assertTrue(SplitPlacementFailure.PRIMARY_ANCHOR_CALENDAR_SPACING in result.failures)
    }
    @Test fun exactRestorationNeverUsesAdjacentCalendarDaysAndRetainsQcr() {
        val source=f.snapshot()
        val parent=AuthorizedPrescription("p",f.source("press",9),f.rx(9),true)
        val initial=listOf(f.row("press",1,3,id="existing"))
        val plan=f.plan(initial,listOf(1,2,3))
        val result=ResidualCompletion().complete(plan,source,state(source),emptyList(),listOf(parent),f.envelope(),f.atoms(plan),
            emptyMap(),true,f.safe,mapOf("atom_0" to AuthorizedAtomOrigin("p","p",0)))
        val actual=result.skeleton.items.filter { it.weekNumber==1 }
        assertEquals(listOf(3,3),actual.map { it.setCount })
        assertEquals(listOf(1,3),actual.map { it.dayOfWeek }.sorted())
        val shortfall=result.trace.exactShortfalls.single()
        assertEquals(9,shortfall.requested)
        assertEquals(6,shortfall.materialized)
        assertEquals(3,shortfall.shortfall)
    }
}
