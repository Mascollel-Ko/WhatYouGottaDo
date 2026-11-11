package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Self-contained checks for simultaneous accessory set and exercise/day choices.
 * No private training backup or device is required.
 */
class BJointExerciseSetOptimizerTest {
    private fun exercise(key: String, rest: Int = 60): Exercise = Exercise(
        stableKey = key, name = key, category = "근력운동",
        activityKind = "STRENGTH", planningEligibility = "ELIGIBLE",
        defaultRestSeconds = rest
    )

    private fun item(key: String, count: Int): V22ExercisePlan = V22ExercisePlan(
        exercise = key, stableKey = key, category = "근력운동",
        role = if (key.startsWith("main_")) "MAIN" else "ACCESSORY",
        setCount = count,
        sets = List(count) { index ->
            V22ObservedSet(null, 8, null, null, "2026-11-01", "TEST_" + index)
        },
        historicalSets28d = 0, historicalSets56d = 0
    )

    private fun session(day: Int, vararg entries: Pair<String, Int>): V22Session {
        val items = entries.map { (key, sets) -> item(key, sets) }
        return V22Session(day, items, items.sumOf(V22ExercisePlan::setCount))
    }

    private fun muscle(key: String, name: String): CanonicalMetadataRelation =
        CanonicalMetadataRelation(
            domain = CanonicalRelationDomain.MUSCLE,
            relationKey = key + ":" + name,
            exerciseStableKey = key,
            relationType = "PRIMARY",
            relationValue = name,
            coefficient = 1.0,
            sourceStableKey = key,
            status = "CANONICAL_ACTIVE",
            provenance = "SELF_CONTAINED_TEST"
        )

    private val main = setOf("main_a", "main_b", "main_c", "main_d")
    private val baseline = listOf(
        session(1, "main_a" to 3, "acc_a" to 1, "acc_b" to 1),
        session(2, "main_b" to 3, "acc_c" to 1),
        session(3, "main_c" to 3, "acc_d" to 1),
        session(4, "main_d" to 3, "acc_e" to 1)
    )
    private val exercises = (main + setOf("acc_a", "acc_b", "acc_c", "acc_d", "acc_e"))
        .associateWith(::exercise)
    private val relations = listOf(
        muscle("main_a", "LEGS"), muscle("main_b", "BACK"),
        muscle("main_c", "CHEST"), muscle("main_d", "GLUTES"),
        muscle("acc_a", "CORE_A"), muscle("acc_b", "CORE_A"),
        muscle("acc_c", "CORE_B"), muscle("acc_d", "CORE_C"),
        muscle("acc_e", "CORE_D")
    )

    private fun optimize(
        input: List<V22Session> = baseline,
        minutes: Int? = 60,
        available: Map<String, Exercise> = exercises,
        muscleRelations: List<CanonicalMetadataRelation> = relations
    ): BJointExerciseSetOptimizer.Result = BJointExerciseSetOptimizer.optimize(
        sessions = input,
        goalImportance = BBalancedGoalImportance(5, 1, 3, 5),
        blockGoal = BBlockGoal.BALANCED,
        autoFocus = BBalancedGoal.SPORT_SUPPORT,
        sessionMinutes = minutes,
        exercises = available,
        muscleRelations = muscleRelations,
        coreCatalog = CanonicalCoreCatalog.EMPTY,
        badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
        taskCatalog = BBalancedTaskCatalog.EMPTY,
        mainKeys = main
    )

    @Test
    fun fourDayExerciseSetSearchCanReduceRedundancyWithoutForcingMoreSets() {
        val result = optimize()
        assertTrue(result.status, result.changed)
        assertTrue(result.finalScore > result.baselineScore)
        assertTrue(result.changes.any {
            it.startsWith("DROP_REDUNDANT:") || it.startsWith("TRANSFER_SET:") ||
                it.startsWith("REPLACE:")
        })
        assertEquals(listOf(1, 2, 3, 4), result.sessions.map(V22Session::session))
        assertTrue(result.sessions.all { it.items.isNotEmpty() })
        assertTrue(result.sessions.all {
            BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) <= 60.0
        })
        main.forEach { key ->
            assertEquals(
                baseline.single { day -> day.items.any { it.stableKey == key } }
                    .items.single { it.stableKey == key },
                result.sessions.single { day -> day.items.any { it.stableKey == key } }
                    .items.single { it.stableKey == key }
            )
        }
        assertEquals(result, optimize())
    }

    @Test
    fun unknownOrReviewRequiredAccessoryIsNotSilentlyChanged() {
        val reviewKey = "acc_b"
        val changed = exercises.toMutableMap().also {
            it[reviewKey] = exercise(reviewKey).copy(needsReview = true)
        }
        val result = optimize(available = changed)
        val before = baseline.flatMap(V22Session::items).single { it.stableKey == reviewKey }
        val after = result.sessions.flatMap(V22Session::items).single { it.stableKey == reviewKey }
        assertEquals(before, after)
        assertEquals(
            baseline.single { day -> day.items.any { it.stableKey == reviewKey } }.session,
            result.sessions.single { day -> day.items.any { it.stableKey == reviewKey } }.session
        )
    }

    @Test
    fun noBudgetForChangeKeepsOriginalProgramWhenExtrasAreImpossible() {
        val zeroBudgetBaseline = listOf(
            session(1, "main_a" to 3, "acc_a" to 1),
            session(2, "main_b" to 3, "acc_c" to 1),
            session(3, "main_c" to 3, "acc_d" to 1),
            session(4, "main_d" to 3, "acc_e" to 1)
        )
        val result = optimize(input = zeroBudgetBaseline, minutes = 1)
        assertTrue(result.sessions.all { day ->
            day.items.map(V22ExercisePlan::stableKey).distinct().size == day.items.size
        })
        assertFalse(result.sessions.any { it.items.isEmpty() })
        // No time budget does not force preservation of needless accessories;
        // it only prohibits worsening the schedule or removing explicit mains.
        main.forEach { key ->
            assertTrue(result.sessions.any { day -> day.items.any { it.stableKey == key } })
        }
        result.sessions.forEachIndexed { index, day ->
            assertTrue(BBalancedDayPlacementPlanner.estimateMinutes(day, exercises) <=
                BBalancedDayPlacementPlanner.estimateMinutes(zeroBudgetBaseline[index], exercises))
        }
    }

    @Test
    fun strengthCapableOneSetAccessoryIsNotAutomaticallyLockedAsMain() {
        val overhead = "ex_32219f7a" // registered strength capability, not an explicit main
        val candidate = exercise(overhead).copy(movementPattern = "VERTICAL_PUSH")
        val trial = baseline.mapIndexed { index, day ->
            if (index == 0) day.copy(items = day.items + item(overhead, 1))
            else day
        }
        val augmented = exercises + (overhead to candidate)
        val reviewed = relations + muscle(overhead, "CORE_A")
        val result = BJointExerciseSetOptimizer.optimize(
            sessions = trial,
            goalImportance = BBalancedGoalImportance(5, 1, 3, 5),
            blockGoal = BBlockGoal.BALANCED,
            autoFocus = BBalancedGoal.SPORT_SUPPORT,
            sessionMinutes = 60,
            exercises = augmented,
            muscleRelations = reviewed,
            coreCatalog = CanonicalCoreCatalog.EMPTY,
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
            taskCatalog = BBalancedTaskCatalog.EMPTY,
            mainKeys = main + overhead
        )
        assertTrue(result.status, result.changed)
        assertFalse(result.sessions.flatMap(V22Session::items).any { it.stableKey == overhead })
        main.forEach { key ->
            assertTrue(result.sessions.flatMap(V22Session::items).any { it.stableKey == key })
        }
    }

    @Test
    fun unsupportedEquipmentIsNotInferredFromHighCoverageValue() {
        val inaccessible = "special_equipment_unique"
        val available = exercises + (inaccessible to exercise(inaccessible).copy(
            equipment = "SPECIAL_EQUIPMENT_NOT_OWNED", movementPattern = "HORIZONTAL_PULL"))
        val reviewed = relations + muscle(inaccessible, "UNIQUE_BACK")
        val result = optimize(available = available, muscleRelations = reviewed)
        assertFalse(result.sessions.flatMap(V22Session::items).any {
            it.stableKey == inaccessible
        })
    }

    @Test
    fun candidateSearchDoesNotMutateSourceInput() {
        val immutableBefore = baseline.map { day ->
            day.copy(items = day.items.map { it.copy(sets = it.sets.toList()) })
        }
        optimize()
        assertEquals(immutableBefore, baseline)
    }
}
