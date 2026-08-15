package com.training.trackplanner.analysis.core

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreStimulusCalculatorTest {
    private val direct = Exercise("direct", "Direct", "근력운동")
    private val hidden = Exercise("hidden", "Hidden", "근력운동")
    private val sport = Exercise("sport", "Sport", "스포츠", activityKind = "SPORT_SESSION")
    private val catalog = CanonicalCoreCatalog.of(
        listOf(
            CanonicalCoreProfile("direct", CoreClass.DIRECT, CoreDirectTarget.ANTI_ROTATION),
            CanonicalCoreProfile("hidden", CoreClass.HIDDEN_HIGH, null),
            CanonicalCoreProfile("sport", CoreClass.HIDDEN_HIGH, null)
        ),
        historySourceByStableKey = mapOf("history_hidden" to "hidden")
    )
    private val calculator = CoreStimulusCalculator(catalog)

    @Test
    fun coreStimulusDependsOnConfirmedSetsAndMildRpeOnly() {
        val base = calculator.calculate(
            listOf(record(hidden, "2026-08-01", List(4) { set(rpe = 8.0, weight = 20.0, reps = 5, seconds = 0) })),
            mapOf(hidden.stableKey to hidden)
        )
        val changedDose = calculator.calculate(
            listOf(record(hidden, "2026-08-01", List(4) { set(rpe = 8.0, weight = 200.0, reps = 50, seconds = 900) })),
            mapOf(hidden.stableKey to hidden)
        )

        assertEquals(3.36, base.daily.sumOf(DailyCoreStimulus::total), 0.0001)
        assertEquals(
            base.daily.sumOf(DailyCoreStimulus::total),
            changedDose.daily.sumOf(DailyCoreStimulus::total),
            0.0001
        )
        assertEquals(0.0, base.daily.sumOf(DailyCoreStimulus::direct), 0.0001)
        assertTrue(base.daily.single().directTargets.isEmpty())
    }

    @Test
    fun directStimulusUsesApprovedTargetAndRpeBoundary() {
        val result = calculator.calculate(
            listOf(record(direct, "2026-08-01", List(3) { set(rpe = 7.0) })),
            mapOf(direct.stableKey to direct)
        )

        assertEquals(3.0, result.daily.single().direct, 0.0001)
        assertEquals(0.0, result.daily.single().indirect, 0.0001)
        assertEquals(3.0, result.daily.single().directTargets.getValue(CoreDirectTarget.ANTI_ROTATION), 0.0001)
    }

    @Test
    fun dailySeriesContainsRecordedDatesAndPreservesDirectIndirectTotals() {
        val result = calculator.calculate(
            listOf(
                record(hidden, "2026-08-01", listOf(set(rpe = 8.0))),
                record(direct, "2026-08-03", listOf(set(rpe = 8.0)))
            ),
            mapOf(hidden.stableKey to hidden, direct.stableKey to direct)
        )

        assertEquals(listOf("2026-08-01", "2026-08-03"), result.daily.map { it.date.toString() })
        assertEquals(0.84, result.daily.sumOf(DailyCoreStimulus::indirect), 0.0001)
        assertEquals(1.05, result.daily.sumOf(DailyCoreStimulus::direct), 0.0001)
    }

    @Test
    fun sportPracticeIsExcludedAndHistoricalIdentityResolvesWithoutRewrite() {
        val historicalExercise = hidden.copy(stableKey = "history_hidden")
        val result = calculator.calculate(
            listOf(
                record(sport, "2026-08-01", List(4) { set(rpe = 10.0) }),
                record(historicalExercise, "2026-08-01", listOf(set(rpe = 8.0)))
            ),
            mapOf(sport.stableKey to sport, historicalExercise.stableKey to historicalExercise)
        )

        assertEquals(0.84, result.daily.sumOf(DailyCoreStimulus::indirect), 0.0001)
        assertEquals("history_hidden", historicalExercise.stableKey)
    }

    @Test
    fun nonFiniteRpeFallsBackSafely() {
        assertEquals(1.0, AnalysisStimulusRpePolicy.modifier(Double.NaN), 0.0)
        assertEquals(1.0, AnalysisStimulusRpePolicy.modifier(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(1.15, AnalysisStimulusRpePolicy.modifier(10.0), 0.0)
    }

    private fun record(exercise: Exercise, date: String, sets: List<WorkoutSet>) = WorkoutEntryWithSets(
        WorkoutEntry(date = date, exerciseStableKey = exercise.stableKey, exerciseName = exercise.name, category = exercise.category),
        sets
    )

    private fun set(
        rpe: Double? = null,
        weight: Double = 0.0,
        reps: Int = 0,
        seconds: Int = 0
    ) = WorkoutSet(entryId = 0, setIndex = 0, weightKg = weight, reps = reps, seconds = seconds, confirmed = true, rpe = rpe)
}
