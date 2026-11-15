package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
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
        minutes: Int = 60,
        sportImportance: Int = 1,
        availableEquipment: Set<String>? = null
    ): V22GenerationRequest = newBBalancedGenerationRequest(
        name = "coach test", days = 4, asOf = today,
        sessionMinutes = minutes, allowFourthDay = false,
        importance = BBalancedGoalImportance(1, 5, 1, sportImportance),
        blockProgram = BBlockProgramDefinition(),
        allowedWeekdays = allowed, fixedWeekdays = fixed,
        availableEquipment = availableEquipment
    )

    private fun optimize(
        sessions: List<V22Session> = base,
        request: V22GenerationRequest = request(),
        exercises: Map<String, Exercise> = exerciseMap,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY
    ) = BCoachJointBlockOptimizer.optimize(
        baseline = sessions,
        block = BBlockDefinition(weeks = 4, goal = BBlockGoal.BALANCED),
        focus = BBalancedGoal.HYPERTROPHY,
        request = request,
        history = emptyList(),
        exercises = exercises,
        muscleRelations = relations,
        coreCatalog = CanonicalCoreCatalog.EMPTY,
        badmintonCatalog = badmintonCatalog,
        capabilityKeys = mainKeys.toSet()
    )

    @Test
    fun allThirtyFiveFourDayOptionsAreEvaluatedAndConsecutiveExposureCanMove() {
        val result = optimize()
        assertTrue(result.status, result.evaluated >= 35)
        assertEquals(4, result.sessions.size)
        assertEquals(4, result.sessions.map(V22Session::session).distinct().size)
        assertFalse(result.sessions.map(V22Session::session) == listOf(1, 2, 3, 4))
        val selectedDays = result.sessions.map(V22Session::session).toSet()
        assertTrue(selectedDays.none { start ->
            start % 7 + 1 in selectedDays && (start + 1) % 7 + 1 in selectedDays
        })
        mainKeys.forEach { key ->
            assertEquals(1, result.sessions.flatMap(V22Session::items)
                .count { it.stableKey == key })
        }
        assertTrue(result.finalScore >= result.originalScore)
        assertTrue(result.sessions.flatMap(V22Session::items).all { item ->
            item.setCount >= if (item.stableKey in mainKeys) 3 else 2
        })
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
    fun `fixed consecutive days are respected only when explicitly forced`() {
        val forced = setOf(1, 2, 3, 4)
        val result = optimize(request = request(fixed = forced))
        assertEquals(forced, result.sessions.map(V22Session::session).toSet())
    }

    @Test
    fun `reviewed badminton drills use real equipment and sport importance`() {
        val drills = listOf(
            "footwork_drill" to BadmintonObjective.FOOTWORK,
            "reaction_drill" to BadmintonObjective.REACTION,
            "landing_drill" to BadmintonObjective.JUMP_LANDING
        )
        val catalog = CanonicalBadmintonObjectiveCatalog.of(drills.mapIndexed { index, (key, objective) ->
            CanonicalBadmintonObjectiveRelation(
                "r$index", key, objective, BadmintonObjectiveTransferLevel.DIRECT,
                "TEST", setOf("e$index"), "test"
            )
        })
        val exercises = exerciseMap + drills.associate { (key, _) ->
            key to exercise(key).copy(
                category = "배드민턴 드릴", equipment = "셔틀콕/코트",
                progressMetricType = "TIME_OR_DISTANCE"
            )
        }
        val unavailable = optimize(
            request = request(sportImportance = 5, availableEquipment = setOf("BODYWEIGHT")),
            exercises = exercises, badmintonCatalog = catalog
        )
        assertTrue(unavailable.candidateDiagnostics.count { "EQUIPMENT_UNAVAILABLE" in it } == 3)
        assertTrue(unavailable.sessions.flatMap(V22Session::items).none {
            item -> item.stableKey in drills.map { it.first }
        })

        val low = optimize(
            request = request(sportImportance = 1, availableEquipment = setOf("셔틀콕", "코트")),
            exercises = exercises, badmintonCatalog = catalog
        )
        val high = optimize(
            request = request(sportImportance = 5, availableEquipment = setOf("셔틀콕", "코트")),
            exercises = exercises, badmintonCatalog = catalog
        )
        fun selectedDrills(result: BCoachJointBlockOptimizer.Result) = result.sessions
            .flatMap(V22Session::items).count { item -> drills.any { it.first == item.stableKey } }
        assertTrue(high.candidateDiagnostics.none { "EQUIPMENT_UNKNOWN" in it || "EQUIPMENT_UNAVAILABLE" in it })
        assertTrue(selectedDrills(high) > 0)
        assertTrue(selectedDrills(high) >= selectedDrills(low))
        assertTrue(high.finalScore != low.finalScore)
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
    fun `T01 another program observations cannot change linked-plan completion`() {
        val linkedOnly = BCoachJointBlockOptimizer.evidence(
            emptyList(), today, linkedDuePlannedSets = 10, linkedConfirmedSets = 8)
        val withOtherProgramRows = BCoachJointBlockOptimizer.evidence(
            listOf(V22HistoryRow(today.minusDays(1), "other", "other_program_lift", "근력운동",
                1, 5.0, 200.0, null, 10.0)),
            today, linkedDuePlannedSets = 10, linkedConfirmedSets = 8)

        assertEquals(0.8, linkedOnly.plannedCompletionRate!!, 0.0)
        assertEquals(linkedOnly.plannedCompletionRate, withOtherProgramRows.plannedCompletionRate)
        assertEquals("LINKED_SAME_APPLICATION_CONFIRMED_SETS", withOtherProgramRows.authority)
    }

    @Test
    fun selectedMainIsNotEveryExerciseWithMainCapability() {
        val trial = listOf(day(1,
            item("main_a", 3, true), item("main_b", 1, false)))
        assertEquals(setOf("main_a"),
            BCoachJointBlockOptimizer.selectedMainKeys(trial, mainKeys.toSet()))
    }

    @Test
    fun heavilySkewedDaysAreRebalancedWithoutFixingWeeklyPhysicalSets() {
        val skewed = listOf(
            day(1, item("main_a", 3, true), item("accessory_a", 4),
                item("accessory_b", 4), item("accessory_c", 4),
                item("accessory_d", 3)),
            day(2, item("main_b", 3, true), item("pull_new", 2)),
            day(3, item("main_c", 3, true), item("accessory_a", 2)),
            day(4, item("main_d", 3, true), item("accessory_b", 2))
        )
        val beforeCounts = skewed.map(V22Session::nominalSets)
        assertEquals(listOf(18, 5, 5, 5), beforeCounts)
        val result = optimize(sessions = skewed)
        val afterCounts = result.sessions.map(V22Session::nominalSets)
        assertTrue("The optimizer must examine alternatives to an 18/5/5/5 split",
            result.evaluated >= 35)
        assertTrue("A less skewed arrangement should outrank the baseline here: " + afterCounts,
            (afterCounts.maxOrNull()!! - afterCounts.minOrNull()!!) <
                (beforeCounts.maxOrNull()!! - beforeCounts.minOrNull()!!))
        mainKeys.forEach { key ->
            assertEquals(1, result.sessions.flatMap(V22Session::items).count { it.stableKey == key })
        }
    }

    @Test
    fun twentySetsCanFitAnHourWhenExerciseSetupAndRestPermit() {
        val twenty = day(1, item("main_a", 5, true),
            item("accessory_a", 5), item("accessory_b", 5),
            item("accessory_c", 5))
        assertEquals(20, twenty.nominalSets)
        assertTrue(BBalancedDayPlacementPlanner.estimateMinutes(twenty, exerciseMap) <= 60.0)
    }
}
