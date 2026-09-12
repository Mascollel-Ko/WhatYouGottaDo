package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class MainSchedulingPolicyTest {
    private val f=PostGenerationFixture
    private val primaryKeys=listOf("barbell_back_squat","ex_a61f1e96","barbell_deadlift","ex_e41f4c2b")
    private fun items(count: Int)=primaryKeys.take(count).map { TimedPlannedExercise(f.source(it,1,priority=100),f.rx(1)) }
    private fun place(count: Int,days: Int,robust: Boolean=true)=TimedWeeklyPlacementPlanner().distribute(items(count),days,90,
        robustSchedule=robust,isMain={ MainSchedulingPolicy.role(it,true)==ProgressionRole.MAIN })
    @Test fun fourDaysThreeMainsPreferNoOverlapBeforeRobustEarlyWeekPreference() {
        val old=TimedWeeklyPlacementPlanner().distribute(items(3),4,90,robustSchedule=true).first
        val revised=place(3,4).first
        assertEquals(1,MainLayoutObjective.of(old.mapValues { it.value.size }).sameDayMainExcess)
        assertEquals(0,MainLayoutObjective.of(revised.mapValues { it.value.size }).sameDayMainExcess)
        assertEquals(3,revised.values.flatten().size)
    }
    @Test fun twoDaysThreeMainsKeepAllDemandWithTwoPlusOne() {
        val result=place(3,2)
        assertTrue(result.second.isEmpty())
        assertEquals(listOf(1,2),result.first.values.map { it.size }.sorted())
    }
    @Test fun twoDaysFourMainsPreferTwoPlusTwo() {
        assertEquals(listOf(2,2),place(4,2).first.values.map { it.size }.sorted())
    }
    @Test fun cyclicThreeDayStreakIsSecondaryAndTwoConsecutiveDaysAreLegal() {
        assertEquals(0,MainLayoutObjective.of(mapOf(1 to 1,2 to 1)).threeDayStreaks)
        for(days in listOf(listOf(1,2,3),listOf(6,7,1),listOf(7,1,2)))
            assertEquals(1,MainLayoutObjective.of(days.associateWith { 1 }).threeDayStreaks)
        val result=place(3,5).first
        val actual=RecordBasedReviewedPolicy.defaultSchedule(1,5).getValue(1).sorted()
        val objective=MainLayoutObjective.of(result.mapKeys { actual[it.key-1] }.mapValues { it.value.size })
        assertEquals(MainLayoutObjective(0,1,0),objective)
    }
    @Test fun mainAuthorityPreservesFinalContinuityAndLightVolumeExclusions() {
        val item=f.source("key")
        assertEquals(ProgressionRole.MAIN,MainSchedulingPolicy.role(item,true))
        assertEquals(ProgressionRole.ASSISTANCE,MainSchedulingPolicy.role(item,false))
        for(variant in listOf("LIGHT","VOLUME")) assertEquals(ProgressionRole.ASSISTANCE,MainSchedulingPolicy.role(item.copy(styleVariant=variant),true))
    }
    @Test fun nonMainPlacementAndEveryPrescriptionAreRetained() {
        val rows=items(3)
        val other=TimedPlannedExercise(f.source("other",1),f.rx(1))
        val initial=mapOf(1 to (rows+other),2 to emptyList(),3 to emptyList(),4 to emptyList())
        val result=InitialMainPlacement.review(initial,90,null,true) { it!=other.item }
        assertTrue(result.getValue(1).any { it===other })
        assertEquals((rows+other).toSet(),result.values.flatten().toSet())
        assertEquals(result,InitialMainPlacement.review(initial,90,null,true) { it!=other.item })
    }
    @Test fun timeAndSameKeyGatesDoNotDeleteOrModifyDemand() {
        val rows=items(3)
        val initial=mapOf(1 to listOf(rows[0],rows[1]),2 to listOf(rows[2]))
        val result=InitialMainPlacement.review(initial,2,null,true) { true }
        assertEquals(rows.toSet(),result.values.flatten().toSet())
        assertTrue(result.values.all { day -> day.sumOf { it.estimatedSeconds }<=120 })
    }
}
