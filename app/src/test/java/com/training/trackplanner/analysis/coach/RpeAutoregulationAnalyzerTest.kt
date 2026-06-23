package com.training.trackplanner.analysis.coach

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RpeAutoregulationAnalyzerTest {
    private val today = LocalDate.of(2026, 6, 23)
    private val exercise = Exercise(id = 1, name = "테스트 스쿼트", category = "근력운동", stableKey = "test_squat")
    private val neutralSleep = SleepRecoverySignal(
        recentAverageHours = 7.0,
        baselineAverageHours = 7.0,
        sleepDeficitHours = 0.0,
        severity = CoachingSignalSeverity.INFO,
        headline = "",
        detail = ""
    )

    @Test
    fun rpeRiseForComparableSetsCreatesWarning() {
        val entries = listOf(
            record(today.minusDays(20), rpe = 7.0),
            record(today.minusDays(14), rpe = 7.2),
            record(today.minusDays(1), rpe = 8.6)
        )

        val signal = RpeAutoregulationAnalyzer().analyze(today, entries, listOf(exercise), neutralSleep)

        assertNotNull(signal)
        assertTrue(signal!!.severity.priority() >= CoachingSignalSeverity.WATCH.priority())
        assertEquals("테스트 스쿼트", signal.exerciseName)
    }

    @Test
    fun lowSleepAddsContextWithoutCausalClaim() {
        val lowSleep = neutralSleep.copy(severity = CoachingSignalSeverity.WATCH)
        val signal = RpeAutoregulationAnalyzer().analyze(
            today,
            listOf(record(today.minusDays(20), 7.0), record(today.minusDays(14), 7.0), record(today, 9.0)),
            listOf(exercise),
            lowSleep
        )

        assertNotNull(signal)
        assertTrue(signal!!.sleepContext.orEmpty().contains("보수적으로"))
        assertTrue(!signal.detail.contains("때문"))
    }

    private fun record(date: LocalDate, rpe: Double): WorkoutEntryWithSets =
        WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = date.toEpochDay(),
                date = date.toString(),
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category
            ),
            sets = listOf(
                WorkoutSet(
                    entryId = date.toEpochDay(),
                    setIndex = 1,
                    reps = 5,
                    weightKg = 100.0,
                    confirmed = true,
                    rpe = rpe
                )
            )
        )
}
