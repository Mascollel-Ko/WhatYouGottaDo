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

    @Test fun optimizedInitialSearchMatchesTestOnlyReferenceOracle() {
        val rows = items(4)
        val initial = mapOf(1 to rows, 2 to emptyList(), 3 to emptyList(), 4 to emptyList())
        val expected = referenceReview(initial, 90, true) { true }
        val metrics = InitialMainPlacementMetrics()
        val actual = InitialMainPlacement.review(initial, 90, null, true, metrics = metrics) { true }
        assertEquals(expected, actual)
        assertTrue(metrics.searchNodes > 0)
        assertTrue(metrics.objectiveEvaluations > 0)
    }

    @Test fun generationMemoReusesExactCanonicalComputations() {
        val memo = PlanningComputationMemo()
        var dayCalls = 0
        var tissueCalls = 0
        val source = PostGenerationFixture.snapshot().copy(
            planDayProjection = PlanDayProjection { dayCalls++; StandaloneDayLoad(10, listOf(10)) },
            planWeekTissueProjection = PlanWeekTissueProjection { _, _ -> tissueCalls++; PlannedTissueWeek(emptyList()) }
        )
        val wrapped = memo.wrap(source)
        val row = PostGenerationFixture.row("press", 1)
        wrapped.planDayProjection!!.evaluate(listOf(row))
        wrapped.planDayProjection!!.evaluate(listOf(row))
        wrapped.planWeekTissueProjection!!.evaluate(listOf(row), 8.5)
        wrapped.planWeekTissueProjection!!.evaluate(listOf(row), 8.5)
        val planner = PersonalizedPrescriptionPlanner().scopedTo(memo)
        val item = PostGenerationFixture.source("press")
        planner.prescribe(wrapped, StrengthIntent.MIXED, item, item.style)
        planner.prescribe(wrapped, StrengthIntent.MIXED, item, item.style)
        assertEquals(1, dayCalls)
        assertEquals(1, tissueCalls)
        assertEquals(1, memo.dayProjectionHits)
        assertEquals(1, memo.tissueProjectionHits)
        assertEquals(1, memo.prescriptionHits)
    }

    /** Small test oracle retaining the pre-optimization traversal and objective calculations. */
    private fun referenceReview(
        baseline: Map<Int, List<TimedPlannedExercise>>,
        minutes: Int,
        robust: Boolean,
        isMain: (PlannedExercise) -> Boolean
    ): Map<Int, List<TimedPlannedExercise>> {
        val days = baseline.keys.sorted()
        val actual = RecordBasedReviewedPolicy.defaultSchedule(1, days.size).getValue(1).sorted()
        val moving = baseline.values.flatten()
            .filter { (isMain(it.item) || StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey)) && MainSchedulingPolicy.ordinary(it.item, it.prescription) }
            .sortedByDescending { StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey) }
        val fixed = baseline.mapValues { (_, rows) -> rows.filterNot { candidate -> moving.any { it === candidate } }.toMutableList() }
        fun objective(layout: Map<Int, List<TimedPlannedExercise>>): RefObjective = RefObjective(
            StrengthPrimaryMainPolicy.counts(layout.values.map { rows -> rows.count { StrengthPrimaryMainPolicy.isPrimary(it.item.stableKey) } }),
            MainLayoutObjective.of(layout.mapKeys { actual[days.indexOf(it.key)] }.mapValues { (_, rows) -> rows.count { isMain(it.item) } })
        )
        var best = baseline
        var bestObjective = objective(best)
        var nodes = 0
        fun lower(row: TimedPlannedExercise) = false
        fun search(index: Int) {
            if (++nodes > 200_000) return
            if (objective(fixed) > bestObjective) return
            if (index == moving.size) {
                val candidate = objective(fixed)
                if (candidate < bestObjective) {
                    bestObjective = candidate
                    best = fixed.mapValues { it.value.toList() }
                }
                return
            }
            val row = moving[index]
            val targets = days.filter { day -> fixed.getValue(day).none { it.item.stableKey == row.item.stableKey } &&
                fixed.getValue(day).sumOf { it.estimatedSeconds } + row.estimatedSeconds <= minutes * 60
            }.sortedWith(compareBy<Int> { day ->
                fixed.getValue(day).add(row)
                val value = objective(fixed)
                fixed.getValue(day).removeAt(fixed.getValue(day).lastIndex)
                value
            }.thenBy { day -> if (lower(row)) fixed.getValue(day).filter(::lower).sumOf { it.estimatedSeconds } else 0 }
                .thenBy { day -> if (robust && row.item.scheduleTier() == ScheduleTier.CORE_MUST_DO && day > (days.size + 1) / 2) 1 else 0 }
                .thenBy { day -> fixed.getValue(day).sumOf { it.estimatedSeconds } }.thenBy { it })
            for (day in targets) {
                fixed.getValue(day).add(row)
                search(index + 1)
                fixed.getValue(day).removeAt(fixed.getValue(day).lastIndex)
            }
        }
        search(0)
        return best
    }

    private data class RefObjective(val primary: StrengthPrimaryObjective, val broadMain: MainLayoutObjective) : Comparable<RefObjective> {
        override fun compareTo(other: RefObjective) = compareValuesBy(this, other, RefObjective::primary, RefObjective::broadMain)
    }
}
