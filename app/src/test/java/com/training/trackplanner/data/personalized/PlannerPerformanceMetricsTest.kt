package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the observation-only production placement counters. */
class PlannerPerformanceMetricsTest {
    private val fixture = PostGenerationFixture

    @Test fun fundingUsesGreedyPlacementWithoutCanonicalReview() {
        val metrics = PlannerPerformanceMetrics()
        val snapshot = fixture.snapshot()
        val state = fixture.state(snapshot)
        val row = TimedPlannedExercise(fixture.source("press", sets = 1), fixture.rx(1))
        TimedWeeklyPlacementPlanner().distributeGreedy(listOf(row), 3, 30, snapshot,
            context = PlacementContext(snapshot, state, 3, 30), metrics = metrics)
        assertEquals(1, metrics.weeklyPlacementCalls)
        assertEquals(1, metrics.placementAtomEvaluations)
        assertEquals(0, metrics.initialPlacementLegalChecks)
        assertTrue(metrics.candidateDayChecks > 0)
    }

    @Test fun productionReviewUsesOrderedConstructionAndKeepsAllRows() {
        val base = fixture.snapshot()
        val keys = listOf("barbell_back_squat", "ex_a61f1e96", "barbell_deadlift")
        val exercises = keys.associateWith { Exercise(it, it, "운동", equipment = "BODYWEIGHT") }
        val snapshot = base.copy(
            exercises = base.exercises + exercises,
            metadata = base.metadata + exercises.mapValues { (_, exercise) ->
                RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(planningEligibility = "PROGRAM_SELECTABLE")
            },
        )
        val state = fixture.state(snapshot)
        val rows = keys.map { TimedPlannedExercise(fixture.source(it, sets = 1), fixture.rx(1)) }
        val metrics = PlannerPerformanceMetrics()
        val result = TimedWeeklyPlacementPlanner().distribute(rows, 4, 30, snapshot,
            planningState = state, context = PlacementContext(snapshot, state, 4, 30), metrics = metrics,
            isMain = { true })
        assertEquals(rows.toSet(), result.first.values.flatten().toSet())
        assertTrue(metrics.initialPlacementSearchNodes <= rows.size + 16)
        assertTrue(metrics.initialPlacementLegalChecks > 0)
    }

    @Test fun productionOrderingReachesTheExhaustiveObjectiveOnSyntheticPrimaries() {
        val base = fixture.snapshot()
        val keys = listOf("barbell_back_squat", "ex_a61f1e96", "barbell_deadlift")
        val exercises = keys.associateWith { Exercise(it, it, "운동", equipment = "BODYWEIGHT") }
        val snapshot = base.copy(
            exercises = base.exercises + exercises,
            metadata = base.metadata + exercises.mapValues { (_, exercise) ->
                RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(planningEligibility = "PROGRAM_SELECTABLE")
            },
        )
        val state = fixture.state(snapshot)
        val rows = keys.map { TimedPlannedExercise(fixture.source(it, sets = 1), fixture.rx(1)) }
        val baseline = mapOf(1 to rows, 2 to emptyList(), 3 to emptyList(), 4 to emptyList())
        val expected = InitialMainPlacementTestOracle.review(baseline, 30)
        val actual = InitialMainPlacement.review(baseline, 30, snapshot, true, state = state,
            performanceMetrics = PlannerPerformanceMetrics(), isMain = { true })
        assertEquals(expected.values.flatten().toSet(), actual.values.flatten().toSet())
        assertEquals(0, MainLayoutObjective.of(expected.mapValues { it.value.size }).sameDayMainExcess)
        assertEquals(0, MainLayoutObjective.of(actual.mapValues { it.value.size }).sameDayMainExcess)
    }

    @Test fun conditionalFourSetSplitIsCountedOnlyWhenExplicitlyNeeded() {
        val snapshot = fixture.snapshot()
        val parent = AuthorizedSchedulingDemand("parent", fixture.source("press", 4), fixture.rx(4, 45), true)
        val blocker = AuthorizedTimedAtom(TimedPlannedExercise(fixture.source("row", 6), fixture.rx(6, 45)), AuthorizedAtomOrigin("blocker"))
        val baseline = TimedExecutionAllocation(
            mapOf(1 to listOf(TimedPlannedExercise(parent.item, parent.prescription)), 2 to listOf(blocker.timed)), emptyList())
        val metrics = PlannerPerformanceMetrics()
        SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner(), performanceMetrics = metrics)
            .improve(snapshot, fixture.state(snapshot), listOf(parent, AuthorizedSchedulingDemand("blocker", blocker.timed.item, blocker.timed.prescription, false)), baseline, 2, 30)
        assertEquals(1, metrics.conditionalSplitParents)
        assertEquals(1, metrics.fullTrials)
        assertEquals(0, metrics.splitTrials)
    }
}

/** Small structural oracle for the production test; no physiological values are inferred. */
private object InitialMainPlacementTestOracle {
    fun review(baseline: Map<Int, List<TimedPlannedExercise>>, minutes: Int): Map<Int, List<TimedPlannedExercise>> {
        val days = baseline.keys.sorted()
        val moving = baseline.values.flatten()
        val fixed = baseline.mapValues { (_, rows) -> rows.filterNot { it in moving }.toMutableList() }
        val ordered = moving.sortedBy { it.item.stableKey }
        ordered.forEachIndexed { index, row -> fixed.getValue(days[index % days.size]).add(row) }
        return fixed.mapValues { it.value.toList() }
    }
}
