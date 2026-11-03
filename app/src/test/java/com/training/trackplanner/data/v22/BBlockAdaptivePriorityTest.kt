package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BBlockAdaptivePriorityTest {
    private fun session(reps: Int = 10): V22Session {
        fun item(key: String) = V22ExercisePlan(
            exercise = key, stableKey = key, category = "근력운동", role = "TEST",
            setCount = 3, sets = List(3) { V22ObservedSet(null, reps, null, null, "", "UNSET") },
            historicalSets28d = 0, historicalSets56d = 0
        )
        return V22Session(1, listOf(item("barbell_squat"), item("curl"), item("row")), 9)
    }

    @Test fun repCapFallsBackToNextFeasibleLoadOrMainSet() {
        val plans = BBlockPrescriptionApplicator.apply(
            session(12), 2, BBalancedGoal.HYPERTROPHY,
            BBlockDefinition(goal = BBlockGoal.HYPERTROPHY),
            setOf("barbell_squat"),
            mapOf("barbell_squat" to Exercise("barbell_squat", "Squat", "근력운동", equipment = "barbell"),
                "curl" to Exercise("curl", "Curl", "근력운동", equipment = "dumbbell")),
            BChangeAmplitude.SMALL
        )
        val main = plans.items.first()
        assertTrue(main.plannedProgression.orEmpty().contains("LOAD_STEPS=2"))
        assertEquals(3, main.setCount)
        assertEquals(12, main.sets.first().reps)
    }

    @Test fun autoAmplitudeUsesHistoryCoverageNotRpeAsAnAutomaticLimit() {
        val now = LocalDate.of(2026, 11, 1)
        val history = (0..2).flatMap { day ->
            (1..3).map { index ->
                V22HistoryRow(now.minusDays(day.toLong()), "Squat", "barbell_squat", "근력운동",
                    index, 5.0, 100.0, null, 9.0)
            }
        }
        val limited = BBlockPrescriptionApplicator.resolveAmplitude(
            BBlockDefinition(goal = BBlockGoal.STRENGTH), history.take(3), now, listOf(session())
        )
        assertEquals(BChangeAmplitude.SMALL, limited)
        val adequate = BBlockPrescriptionApplicator.resolveAmplitude(
            BBlockDefinition(goal = BBlockGoal.STRENGTH), history, now, listOf(session())
        )
        assertEquals(BChangeAmplitude.MEDIUM, adequate)
        assertEquals(BChangeAmplitude.MEDIUM, BBlockPrescriptionApplicator.resolveAmplitude(
            BBlockDefinition(goal = BBlockGoal.STRENGTH), history.map { it.copy(rpe = 7.0) },
            now, listOf(session())
        ))
    }

    @Test fun smallAndMediumChangeDifferentAccessoryParticipationWithoutDoublingStep() {
        val input = session(8)
        val block = BBlockDefinition(goal = BBlockGoal.HYPERTROPHY, adjustment = BVolumeAdjustment.REPS_FIRST)
        val small = BBlockPrescriptionApplicator.apply(input, 1, BBalancedGoal.HYPERTROPHY,
            block, setOf("barbell_squat"), resolvedAmplitude = BChangeAmplitude.SMALL)
        val medium = BBlockPrescriptionApplicator.apply(input, 1, BBalancedGoal.HYPERTROPHY,
            block, setOf("barbell_squat"), resolvedAmplitude = BChangeAmplitude.MEDIUM)
        assertEquals(listOf(9, 8, 8), small.items.map { it.sets.first().reps })
        assertEquals(listOf(9, 9, 9), medium.items.map { it.sets.first().reps })
    }

    @Test fun customTwoStepsCanCombineRepsAndSets() {
        val block = BBlockDefinition(goal = BBlockGoal.HYPERTROPHY,
            adjustment = BVolumeAdjustment.CUSTOM,
            customPriority = listOf(BProgressionField.REPS, BProgressionField.SETS))
        val out = BBlockPrescriptionApplicator.apply(session(8), 2, BBalancedGoal.HYPERTROPHY,
            block, setOf("barbell_squat"), resolvedAmplitude = BChangeAmplitude.SMALL)
        assertEquals(4, out.items.first().setCount)
        assertEquals(9, out.items.first().sets.first().reps)
        assertEquals(9, out.items.first().sets.last().reps)
    }

    @Test fun timedSportDrillsDoNotAccumulateFictitiousBarbellLoadSteps() {
        val item = session().items.last().copy(
            category = "기능성운동",
            sets = List(2) { V22ObservedSet(null, null, 20, null, "", "UNSET") },
            setCount = 2
        )
        val output = BBlockPrescriptionApplicator.apply(
            V22Session(1, listOf(item), 2), 2, BBalancedGoal.SPORT_SUPPORT,
            BBlockDefinition(goal = BBlockGoal.SPORT_SUPPORT),
            emptySet(), resolvedAmplitude = BChangeAmplitude.MEDIUM
        )
        assertTrue(output.items.single().plannedProgression.orEmpty().contains("LOAD_STEPS=0"))
        assertEquals(20, output.items.single().sets.first().seconds)
    }

    @Test fun negativeStagesFirstReduceEveryMainSet() {
        val out = BBlockPrescriptionApplicator.apply(session(8), -1, BBalancedGoal.STRENGTH,
            BBlockDefinition(goal = BBlockGoal.STRENGTH), setOf("barbell_squat"),
            resolvedAmplitude = BChangeAmplitude.MEDIUM)
        assertEquals(2, out.items.first().setCount)
        assertEquals(3, out.items.last().setCount)
    }
}
