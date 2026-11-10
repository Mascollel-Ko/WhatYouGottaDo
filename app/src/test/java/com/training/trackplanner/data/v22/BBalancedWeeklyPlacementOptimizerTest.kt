package com.training.trackplanner.data.v22

import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Public, self-contained regression cases: no private personal-history fixture required. */
class BBalancedWeeklyPlacementOptimizerTest {
    private fun exercise(key: String) = Exercise(
        stableKey = key,
        name = key,
        category = "근력운동",
        activityKind = "STRENGTH",
        planningEligibility = "ELIGIBLE",
        defaultRestSeconds = 60
    )

    private fun item(key: String, count: Int = 3) = V22ExercisePlan(
        exercise = key,
        stableKey = key,
        category = "근력운동",
        role = "ACCESSORY",
        setCount = count,
        sets = List(count) { index ->
            V22ObservedSet(
                weightKg = 10.0 + index, reps = 8 + index, seconds = null,
                observedRpe = null, sourceDate = "2026-11-01", loadStatus = "TEST"
            )
        },
        historicalSets28d = 10,
        historicalSets56d = 20
    )

    private fun session(day: Int, vararg keys: String): V22Session {
        val items = keys.map { item(it) }
        return V22Session(day, items, items.sumOf(V22ExercisePlan::setCount))
    }

    private fun relation(key: String, muscle: String, role: String = "PRIMARY") =
        CanonicalMetadataRelation(
            domain = CanonicalRelationDomain.MUSCLE,
            relationKey = "$key:$muscle:$role",
            exerciseStableKey = key,
            relationType = role,
            relationValue = muscle,
            coefficient = if (role == "PRIMARY") 1.0 else 0.5,
            sourceStableKey = key,
            status = "CANONICAL_ACTIVE",
            provenance = "SELF_CONTAINED_TEST"
        )

    private val muscles = listOf(
        relation("squat", "QUADS"),
        relation("deadlift", "GLUTES"),
        relation("row", "LATS"),
        relation("core", "ABS"),
        relation("main_press", "CHEST"),
        relation("main_press", "TRICEPS", "SECONDARY"),
        relation("press_b", "CHEST"),
        relation("press_b", "TRICEPS", "SECONDARY"),
        relation("press_c", "DELTOIDS"),
        relation("press_c", "TRICEPS", "SECONDARY"),
        relation("triceps", "TRICEPS"),
        relation("leg_a", "QUADS"),
        relation("leg_b", "HAMSTRINGS"),
        relation("pull", "LATS"),
        relation("push_a", "CHEST"),
        relation("push_a", "TRICEPS", "SECONDARY"),
        relation("push_b", "CHEST"),
        relation("push_b", "TRICEPS", "SECONDARY")
    )
    private val exercises = muscles.map { it.exerciseStableKey }.distinct().associateWith(::exercise)

    private fun weeklyPrescriptions(sessions: List<V22Session>) =
        sessions.flatMap(V22Session::items).groupBy(V22ExercisePlan::stableKey)
            .mapValues { (_, items) -> items.map { it.copy() }.sortedBy { it.setCount } }

    @Test
    fun overloadedFridayPressesRedistributeWithoutChangingAnyPrescriptionOrMainDay() {
        val base = listOf(
            session(1, "squat", "row"),
            session(3, "deadlift", "core"),
            session(5, "main_press", "press_b", "press_c", "triceps")
        )
        val peak = base.map { day ->
            val changed = day.items.map { original ->
                val sets = original.sets.take(2).map { it.copy(reps = 5, sourceDate = "PEAK") }
                original.copy(setCount = sets.size, sets = sets)
            }
            day.copy(items = changed, nominalSets = changed.sumOf(V22ExercisePlan::setCount))
        }
        val first = BBalancedWeeklyPlacementOptimizer.optimize(
            base, listOf(base, peak), exercises, muscles, setOf("main_press", "squat", "deadlift"), 60
        )
        assertTrue(first.status, first.changed)
        assertTrue(first.moves.any { it.fromDay == 5 && it.toDay != 5 })
        assertTrue(first.after!!.totalCost < first.before!!.totalCost)
        assertEquals(5, first.sessions.single { day ->
            day.items.any { it.stableKey == "main_press" }
        }.session)
        assertEquals(listOf(1, 3, 5), first.sessions.map(V22Session::session))
        listOf(base, peak).zip(first.weeks).forEach { (before, after) ->
            assertEquals(weeklyPrescriptions(before), weeklyPrescriptions(after))
            assertEquals(before.sumOf(V22Session::nominalSets), after.sumOf(V22Session::nominalSets))
            assertTrue(after.all { it.items.map(V22ExercisePlan::stableKey).distinct().size == it.items.size })
            assertTrue(after.all { it.items.isNotEmpty() })
            assertTrue(after.all { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) <= 60 })
        }
        val repeated = BBalancedWeeklyPlacementOptimizer.optimize(
            base, listOf(base, peak), exercises, muscles, setOf("main_press", "squat", "deadlift"), 60
        )
        assertEquals(first, repeated)
    }

    @Test
    fun swappingWorksWhenSingleItemMoveWouldExceedDailyTimeLimit() {
        val base = listOf(
            session(1, "push_a", "push_b"),
            session(3, "leg_a", "core"),
            session(5, "pull", "leg_b")
        )
        val optimized = BBalancedWeeklyPlacementOptimizer.optimize(
            base, listOf(base), exercises, muscles, emptySet(), 27
        )
        assertTrue(optimized.status, optimized.changed)
        // With three sets per item, all days are below 27 min with two items (25.52 min estimated),
        // but a three-item day exceeds the cap. The improvement requires exchange.
        assertTrue(optimized.moves.size >= 2)
        assertTrue(optimized.sessions.all { it.items.size == 2 })
        assertTrue(optimized.sessions.all { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) <= 27 })
        assertEquals(weeklyPrescriptions(base), weeklyPrescriptions(optimized.weeks.single()))
    }

    @Test
    fun protectedItemsAndUnresolvedMetadataRemainUntouched() {
        val base = listOf(
            session(1, "push_a", "push_b"),
            session(3, "leg_a", "core"),
            session(5, "pull", "leg_b")
        )
        val protected = base.flatMap(V22Session::items).mapTo(mutableSetOf(), V22ExercisePlan::stableKey)
        val noMove = BBalancedWeeklyPlacementOptimizer.optimize(
            base, listOf(base), exercises, muscles, protected, 60
        )
        assertFalse(noMove.changed)
        assertEquals(base, noMove.sessions)

        val incomplete = base.mapIndexed { index, day ->
            if (index == 0) {
                val changed = day.items.map { if (it.stableKey == "push_b") item("unknown") else it }
                day.copy(items = changed, nominalSets = changed.sumOf(V22ExercisePlan::setCount))
            } else day
        }
        val unknownProtected = BBalancedWeeklyPlacementOptimizer.optimize(
            incomplete, listOf(incomplete), exercises, muscles, emptySet(), 60
        )
        assertTrue(unknownProtected.moves.none { it.stableKey == "unknown" })
        assertEquals(1, unknownProtected.sessions.single {
            it.items.any { item -> item.stableKey == "unknown" }
        }.session)
    }

    @Test
    fun neverPushesAnOriginallyValidWeekdayOverItsOwnTimeLimit() {
        val baseline = listOf(
            session(1, "squat", "row"),
            session(3, "deadlift", "core"),
            session(5, "main_press", "press_b", "press_c", "triceps")
        )
        val cap = 27
        val oldTimes = baseline.map { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) }
        assertTrue(oldTimes[0] <= cap && oldTimes[1] <= cap && oldTimes[2] > cap)
        val result = BBalancedWeeklyPlacementOptimizer.optimize(
            baseline, listOf(baseline), exercises, muscles,
            setOf("squat", "deadlift", "main_press"), cap
        )
        val newTimes = result.weeks.single().map {
            BBalancedDayPlacementPlanner.estimateMinutes(it, exercises)
        }
        assertTrue(newTimes[0] <= cap)
        assertTrue(newTimes[1] <= cap)
        assertTrue(newTimes[2] <= oldTimes[2] + 1e-8)
        assertTrue(newTimes.max() <= oldTimes.max() + 1e-8)
        assertEquals(weeklyPrescriptions(baseline), weeklyPrescriptions(result.weeks.single()))
    }

    @Test
    fun neverCreatesANewHighestPhysicalOrMainWeightedSetDay() {
        val baseline = listOf(
            session(1, "squat", "row"),
            session(3, "deadlift", "core"),
            session(5, "main_press", "press_b", "press_c", "triceps")
        )
        val main = setOf("squat", "deadlift", "main_press")
        val result = BBalancedWeeklyPlacementOptimizer.optimize(
            baseline, listOf(baseline), exercises, muscles, main, 60
        )
        fun totals(sessions: List<V22Session>) = sessions.map { session ->
            session.items.sumOf { item -> item.setCount }
        }
        fun weighted(sessions: List<V22Session>) = sessions.map { session ->
            session.items.sumOf { item ->
                item.setCount * if (item.stableKey in main) 1.5 else 1.0
            }
        }
        assertTrue(totals(result.sessions).max() <= totals(baseline).max())
        assertTrue(weighted(result.sessions).max() <= weighted(baseline).max() + 1e-8)
        assertEquals(baseline.map(V22Session::session), result.sessions.map(V22Session::session))
    }

    @Test
    fun refusesMismatchedWeeksInsteadOfMovingAnUnmatchedPrescription() {
        val base = listOf(session(1, "squat"), session(3, "row"), session(5, "press_b"))
        val invalid = base.map { day ->
            if (day.session == 5) session(5, "press_c") else day
        }
        val result = BBalancedWeeklyPlacementOptimizer.optimize(
            base, listOf(base, invalid), exercises, muscles, emptySet(), 60
        )
        assertEquals("WEEKLY_EXERCISE_IDENTITY_MISMATCH", result.status)
        assertEquals(listOf(base, invalid), result.weeks)
        assertFalse(result.changed)
    }
    @Test
    fun fourDayWeekRedistributesPressConcentrationWithoutChangingDaysOrWeeklyPrescriptions() {
        // Regression for a real-world 4 day / 60 minute B request: presses must not
        // be forced to accumulate on one day while a lighter day is available.
        val base = listOf(
            session(1, "squat", "row"),
            session(2, "main_press", "press_b", "press_c", "triceps"),
            session(3, "deadlift", "core"),
            session(4, "pull", "leg_a")
        )
        val peak = base.map { day ->
            val reduced = day.items.map { item ->
                item.copy(setCount = 2, sets = item.sets.take(2).map { it.copy(sourceDate = "PEAK") })
            }
            day.copy(items = reduced, nominalSets = reduced.sumOf(V22ExercisePlan::setCount))
        }
        val protectedMain = setOf("squat", "deadlift", "main_press")
        val result = BBalancedWeeklyPlacementOptimizer.optimize(
            base, listOf(base, peak), exercises, muscles, protectedMain, 60
        )
        assertTrue(result.status, result.changed)
        assertTrue(result.moves.any { it.fromDay == 2 })
        assertTrue(result.after!!.totalCost < result.before!!.totalCost)
        assertEquals(listOf(1, 2, 3, 4), result.sessions.map(V22Session::session))
        protectedMain.forEach { main ->
            assertEquals(
                base.single { day -> day.items.any { it.stableKey == main } }.session,
                result.sessions.single { day -> day.items.any { it.stableKey == main } }.session
            )
        }
        listOf(base, peak).zip(result.weeks).forEach { (old, reflowed) ->
            assertEquals(weeklyPrescriptions(old), weeklyPrescriptions(reflowed))
            assertEquals(old.sumOf(V22Session::nominalSets), reflowed.sumOf(V22Session::nominalSets))
            assertTrue(reflowed.all { it.items.map(V22ExercisePlan::stableKey).distinct().size == it.items.size })
            assertTrue(reflowed.all { it.items.isNotEmpty() })
            assertTrue(reflowed.all {
                BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) <= 60
            })
        }
        assertEquals(result, BBalancedWeeklyPlacementOptimizer.optimize(
            base, listOf(base, peak), exercises, muscles, protectedMain, 60
        ))
    }

}
