package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** No personal data fixture; verifies the whole-block decision contract. */
class BCoachJointBlockOptimizerTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun exercise(key: String, pattern: String = "") = Exercise(
        stableKey = key, name = key, category = "근력운동",
        activityKind = "STRENGTH", planningEligibility = "ELIGIBLE",
        movementPattern = pattern, defaultRestSeconds = 45
    )

    private fun item(key: String, count: Int, main: Boolean = false) =
        V22ExercisePlan(
            exercise = key, stableKey = key, category = "근력운동",
            role = if (main) "MAIN" else "ACCESSORY",
            setCount = count,
            sets = List(count) {
                V22ObservedSet(null, 8, null, null, "", "USER_CALIBRATION_REQUIRED")
            },
            historicalSets28d = 0, historicalSets56d = 0
        )

    private fun day(number: Int, vararg items: V22ExercisePlan) =
        V22Session(number, items.toList(), items.sumOf(V22ExercisePlan::setCount))

    private fun muscle(key: String, name: String) =
        CanonicalMetadataRelation(
            domain = CanonicalRelationDomain.MUSCLE,
            relationKey = key + ":" + name,
            exerciseStableKey = key,
            relationType = "PRIMARY", relationValue = name, coefficient = 1.0,
            sourceStableKey = key, status = "CANONICAL_ACTIVE",
            provenance = "TEST"
        )

    private val mainKeys = listOf("main_a", "main_b", "main_c", "main_d")
    private val base = listOf(
        day(1, item("main_a", 3, true), item("accessory_a", 1)),
        day(2, item("main_b", 3, true), item("accessory_b", 1)),
        day(3, item("main_c", 3, true), item("accessory_c", 1)),
        day(4, item("main_d", 3, true), item("accessory_d", 1))
    )
    private val exerciseMap = (
        mainKeys + listOf("accessory_a", "accessory_b", "accessory_c", "accessory_d", "pull_new")
    ).associateWith { key ->
        exercise(key, if (key == "pull_new") "PULL_HORIZONTAL" else "PUSH_HORIZONTAL")
    }
    private val relations = mainKeys.map { muscle(it, "CHEST") } + listOf(
        muscle("accessory_a", "CHEST"), muscle("accessory_b", "CHEST"),
        muscle("accessory_c", "CHEST"), muscle("accessory_d", "CHEST"),
        muscle("pull_new", "BACK_LATS")
    )
    private fun request(
        fixed: Set<Int>? = null,
        allowed: Set<Int>? = null,
        minutes: Int = 60
    ): V22GenerationRequest = newBBalancedGenerationRequest(
        name = "coach test", days = 4, asOf = today,
        sessionMinutes = minutes, allowFourthDay = false,
        importance = BBalancedGoalImportance(1, 5, 1, 1),
        blockProgram = BBlockProgramDefinition(),
        allowedWeekdays = allowed, fixedWeekdays = fixed
    )

    private fun optimize(
        sessions: List<V22Session> = base,
        request: V22GenerationRequest = request()
    ) = BCoachJointBlockOptimizer.optimize(
        baseline = sessions,
        block = BBlockDefinition(weeks = 4, goal = BBlockGoal.BALANCED),
        focus = BBalancedGoal.HYPERTROPHY,
        request = request,
        history = emptyList(),
        exercises = exerciseMap,
        muscleRelations = relations,
        coreCatalog = CanonicalCoreCatalog.EMPTY,
        badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
        capabilityKeys = mainKeys.toSet()
    )

    @Test
    fun allThirtyFiveFourDayOptionsAreEvaluatedAndConsecutiveExposureCanMove() {
        val result = optimize()
        assertTrue(result.status, result.evaluated >= 35)
        assertEquals(4, result.sessions.size)
        assertEquals(4, result.sessions.map(V22Session::session).distinct().size)
        assertFalse(result.sessions.map(V22Session::session) == listOf(1, 2, 3, 4))
        mainKeys.forEach { key ->
            assertEquals(1, result.sessions.flatMap(V22Session::items)
                .count { it.stableKey == key })
        }
        assertTrue(result.finalScore >= result.originalScore)
    }

    @Test
    fun fixedWeekdaysMustRemainFixedAndUnapprovedEquipmentCannotBeInvented() {
        val expected = setOf(1, 2, 4, 6)
        val result = optimize(request = request(fixed = expected))
        assertEquals(expected, result.sessions.map(V22Session::session).toSet())
        assertTrue(result.sessions.all { it.items.isNotEmpty() })
        assertTrue(result.alternatives.all { it.peakMinutes <= 60.0 })
    }

    @Test
    fun observedWorkoutRowsAreNotFalselyReportedAsCompletedPlan() {
        val rows = listOf(
            V22HistoryRow(today.minusDays(20), "lift", "main_a", "근력운동", 1, 5.0, 100.0, null, 8.0),
            V22HistoryRow(today.minusDays(4), "lift", "main_a", "근력운동", 1, 5.0, 105.0, null, 8.0),
            V22HistoryRow(today.minusDays(10), "lift", "main_b", "근력운동", 1, 5.0, 60.0, null, 8.0),
            V22HistoryRow(today.minusDays(3), "lift", "main_b", "근력운동", 1, 5.0, 63.0, null, 8.0)
        )
        val evidence = BCoachJointBlockOptimizer.evidence(rows, today)
        assertEquals(4, evidence.completedObservedSets28d)
        assertEquals(4, evidence.observedTrainingDates28d)
        assertEquals(2, evidence.comparableStrengthKeys)
        assertTrue((evidence.relativeStrengthTrend ?: 0.0) > 0.0)
        assertNull(evidence.plannedCompletionRate)
        assertEquals("OBSERVED_WORKOUT_ROWS_ONLY_NO_PLAN_EXECUTION_LINK", evidence.authority)
    }

    @Test
    fun selectedMainIsNotEveryExerciseWithMainCapability() {
        val trial = listOf(day(1,
            item("main_a", 3, true), item("main_b", 1, false)))
        assertEquals(setOf("main_a"),
            BCoachJointBlockOptimizer.selectedMainKeys(trial, mainKeys.toSet()))
    }
}
