package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.personalized.PostGenerationFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class BBlockPrescriptionApplicatorTest {
    private fun session(): V22Session {
        fun item(key: String) = V22ExercisePlan(
            exercise = key, stableKey = key, category = "근력운동", role = "OTHER",
            setCount = 3, sets = List(3) { V22ObservedSet(null, 5, null, null, "", "USER_CALIBRATION_REQUIRED") },
            historicalSets28d = 0, historicalSets56d = 0
        )
        return V22Session(1, listOf(item("squat"), item("deadlift"), item("curl")), 9)
    }

    @Test fun setsChangeOnePerMainExerciseNotOncePerSession() {
        val next = BBlockPrescriptionApplicator.apply(
            session(), 1, BBalancedGoal.STRENGTH,
            BBlockDefinition(adjustment = BVolumeAdjustment.SETS_FIRST),
            setOf("squat", "deadlift")
        )
        assertEquals(listOf(4, 4, 3), next.items.map { it.setCount })
        assertEquals(11, next.nominalSets)
        val down = BBlockPrescriptionApplicator.apply(
            session(), -1, BBalancedGoal.STRENGTH, BBlockDefinition(), setOf("squat", "deadlift")
        )
        assertEquals(listOf(2, 2, 3), down.items.map { it.setCount })
    }

    @Test fun hypertrophyProgressesOneRepetitionPerSetAndPreservesIdentity() {
        val changed = BBlockPrescriptionApplicator.apply(
            session(), 2, BBalancedGoal.HYPERTROPHY, BBlockDefinition(), setOf("squat", "deadlift")
        )
        assertEquals(listOf("squat", "deadlift", "curl"), changed.items.map { it.stableKey })
        assertTrue(changed.items.all { item -> item.sets.all { it.reps == 7 } })
        assertTrue(changed.items.all { item -> item.sets.all { it.weightKg == null } })
    }

    @Test fun strengthLoadFirstDoesNotInventKgBeforeCalibration() {
        val changed = BBlockPrescriptionApplicator.apply(
            session(), 2, BBalancedGoal.STRENGTH, BBlockDefinition(), setOf("squat")
        )
        // Equipment without metadata cannot safely inherit a paired barbell plate.
        // Continue to the next feasible lever: sets for a main exercise, then reps.
        assertEquals(5, changed.items.first().setCount)
        assertTrue(changed.items.flatMap { it.sets }.all { it.weightKg == null })
        assertTrue(changed.items.first().plannedProgression!!.contains("LOAD_STEPS=0"))
        assertTrue(changed.items.first().plannedProgression!!.contains("START_LOAD_USER_REQUIRED"))
    }

    @Test fun fourWeekPatternsChangeActualSetAndRepPrescriptions() {
        fun doses(pattern: BVolumePattern) = BBlockPeriodization.steps(
            BBlockDefinition(weeks=4,goal=BBlockGoal.HYPERTROPHY,pattern=pattern,
                adjustment=BVolumeAdjustment.REPS_FIRST)
        ).map { stage ->
            val item=BBlockPrescriptionApplicator.apply(
                session(),stage,BBalancedGoal.HYPERTROPHY,
                BBlockDefinition(weeks=4,goal=BBlockGoal.HYPERTROPHY,pattern=pattern,
                    adjustment=BVolumeAdjustment.REPS_FIRST),
                setOf("squat"),resolvedAmplitude=BChangeAmplitude.SMALL
            ).items.first()
            item.setCount to item.sets.first().reps
        }
        assertEquals(List(4) { 3 to 5 },doses(BVolumePattern.STEADY))
        assertEquals(listOf(3 to 5,3 to 6,3 to 7,3 to 8),doses(BVolumePattern.PROGRESSIVE))
        assertEquals(listOf(3 to 5,3 to 7,3 to 5,3 to 7),doses(BVolumePattern.WAVE))
        assertEquals(listOf(3 to 5,3 to 6,3 to 7,2 to 5),doses(BVolumePattern.ACCUMULATE_RECOVER))
    }

    @Test fun manualStartLoadAuthorizesLaterWeeksWithoutInventingAnUnknownStart() {
        val fixture = PostGenerationFixture
        val base = fixture.plan(listOf(fixture.row("squat", 1)), days = listOf(1), horizon = 4)
        val steps = JSONArray().apply {
            (1..3).forEach { localWeek ->
                put(JSONObject()
                    .put("block", 1)
                    .put("localWeek", localWeek)
                    .put("session", 1)
                    .put("stableKey", "squat")
                    .put("loadSteps", localWeek))
            }
        }
        val metadata = JSONObject()
            .put("schema", BBalanced12WeekEngine.BLOCK_SCHEMA)
            .put("blocks", JSONArray().put(JSONObject().put("focus", BBalancedGoal.STRENGTH.name)))
            .put("blockSpecs", JSONArray().put(JSONObject()
                .put("weeks", 4)
                .put("pattern", BVolumePattern.PROGRESSIVE.name)
                .put("adjustment", BVolumeAdjustment.LOAD_FIRST.name)))
            .put("recoveryDaysBetween", JSONArray())
            .put("prescriptionSteps", steps)
            .put("equipmentIncrementKinds", JSONObject().put("squat", BEquipmentIncrementKind.BARBELL.name))
            .toString()
        val unresolved = base.copy(
            planningMetadataJson = metadata,
            items = base.items.map { item -> item.copy(
                weightKg = 0.0,
                setPrescriptions = item.setPrescriptions.map { set -> set.copy(
                    weightKg = 0.0,
                    loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
                ) }
            ) }
        )
        assertEquals(unresolved, unresolved.propagateBBlockStartLoad(unresolved, 1.25))

        val entered = unresolved.copy(items = unresolved.items.map { item ->
            if (item.weekNumber != 1) item else item.copy(
                weightKg = 100.0,
                setPrescriptions = item.setPrescriptions.map { set -> set.copy(
                    weightKg = 100.0,
                    loadState = ProgramLoadState.EXPLICIT_LOAD
                ) }
            )
        })
        val propagated = entered.propagateBBlockStartLoad(unresolved, 1.25)
        assertEquals(listOf(100.0, 102.5, 105.0, 107.5), propagated.items
            .sortedBy { it.weekNumber }
            .map { it.setPrescriptions.first().weightKg })
        assertTrue(propagated.items.all { item ->
            item.setPrescriptions.all { it.loadState == ProgramLoadState.EXPLICIT_LOAD }
        })
    }
}
