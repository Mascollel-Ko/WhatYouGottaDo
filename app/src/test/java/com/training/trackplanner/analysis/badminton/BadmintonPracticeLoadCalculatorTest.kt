package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.trends.WeeklyTrainingData
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadmintonPracticeLoadCalculatorTest {
    private val date = LocalDate.parse("2026-08-10")

    @Test
    fun authorityAdmitsOnlyTheTwoExactSportSessionIdentities() {
        assertEquals(
            setOf("ex_ae9ecdbc", "ex_badminton_lesson"),
            BadmintonPracticeCatalog.stableKeys
        )
        BadmintonPracticeCatalog.stableKeys.forEach { stableKey ->
            assertTrue(BadmintonPracticeCatalog.admits(stableKey, "SPORT_SESSION"))
            assertFalse(BadmintonPracticeCatalog.admits(stableKey, "EXERCISE"))
        }
        assertFalse(BadmintonPracticeCatalog.admits("same_name_other_identity", "SPORT_SESSION"))
        assertFalse(BadmintonPracticeCatalog.admits("ex_badminton_match", "MATCH_RECORD"))
    }

    @Test
    fun transferAndContextMetadataDoNotAdmitAnUnlistedSportSession() {
        val exercise = Exercise(
            stableKey = "unlisted_session",
            name = "배드민턴",
            category = "Sport",
            activityKind = "SPORT_SESSION"
        )
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(exercise.stableKey, exercise.name).copy(
            activityKind = "SPORT_SESSION",
            badmintonTransferLevel = "DIRECT",
            sportContextTags = MetadataTokenField.parse("BADMINTON|COURT")
        )
        val calculator = BadmintonPracticeLoadCalculator(RuntimeExerciseMetadataCatalog.of(listOf(metadata)))

        assertEquals(
            0.0,
            calculator.calculateRaw(listOf(record(exercise, date, listOf(set(600)))), mapOf(exercise.stableKey to exercise)),
            0.0
        )
    }

    @Test
    fun resolvedActivityKindControlsAdmissionWithoutTheAnalysisFeatureMapper() {
        val exercise = exercise("ex_ae9ecdbc").copy(activityKind = "SPORT_SESSION")
        val demoted = RuntimeExerciseMetadataDefaults.forIdentity(exercise.stableKey, exercise.name)
            .copy(activityKind = "EXERCISE")
        val calculator = BadmintonPracticeLoadCalculator(RuntimeExerciseMetadataCatalog.of(listOf(demoted)))

        assertEquals(
            0.0,
            calculator.calculateRaw(listOf(record(exercise, date, listOf(set(600)))), mapOf(exercise.stableKey to exercise)),
            0.0
        )
    }

    @Test
    fun confirmedMinutesAndSetRpePrecedenceMatchTheGovernedArithmetic() {
        val exercise = exercise("ex_ae9ecdbc")
        val input = record(
            exercise,
            date,
            listOf(
                set(seconds = 120, rpe = 6.0),
                set(seconds = 180, rpe = 10.0),
                set(seconds = 600, rpe = 10.0, confirmed = false)
            ),
            entryRpe = 2.0
        )
        val point = calculator(exercise).dailyLoads(listOf(input), mapOf(exercise.stableKey to exercise)).single()

        assertEquals(5.0, point.durationMinutes, 0.0001)
        assertEquals(5.25, point.practiceLoad, 0.0001)
    }

    @Test
    fun entryRpeFallbackAndEveryBoundaryRemainCanonical() {
        val exercise = exercise("ex_badminton_lesson")
        val cases = listOf(
            null to 10.00,
            6.0 to 9.00,
            6.5 to 10.00,
            7.999 to 10.00,
            8.0 to 10.50,
            8.999 to 10.50,
            9.0 to 11.00,
            9.999 to 11.00,
            10.0 to 11.50,
            11.0 to 11.50
        )

        cases.forEach { (rpe, expected) ->
            val input = record(exercise, date, listOf(set(600, rpe = null)), entryRpe = rpe)
            assertEquals(
                "RPE=$rpe",
                expected,
                calculator(exercise).calculateRaw(listOf(input), mapOf(exercise.stableKey to exercise)),
                0.0001
            )
        }
    }

    @Test
    fun dailyAndCallerSuppliedWeeklyBucketsSumPracticeLoad() {
        val exercise = exercise("ex_ae9ecdbc")
        val inputs = listOf(
            record(exercise, date, listOf(set(300))),
            record(exercise, date, listOf(set(600, rpe = 8.0))),
            record(exercise, date.plusDays(2), listOf(set(60, rpe = 10.0))),
            record(exercise, date.plusDays(3), listOf(set(600, confirmed = false)))
        )
        val calculator = calculator(exercise)
        val daily = calculator.dailyLoads(inputs, mapOf(exercise.stableKey to exercise))
        val weekly = calculator.weeklyLoads(
            listOf(WeeklyTrainingData(date, date.plusDays(6), inputs, emptyList())),
            mapOf(exercise.stableKey to exercise)
        ).single()

        assertEquals(listOf(date, date.plusDays(2)), daily.map { it.date })
        assertEquals(listOf(15.50, 1.15), daily.map { it.practiceLoad })
        assertEquals(16.65, weekly.practiceLoad, 0.0001)
        assertEquals(16.0, weekly.durationMinutes, 0.0001)
    }

    private fun calculator(exercise: Exercise): BadmintonPracticeLoadCalculator {
        val metadata = RuntimeExerciseMetadataDefaults.forIdentity(exercise.stableKey, exercise.name)
            .copy(activityKind = "SPORT_SESSION")
        return BadmintonPracticeLoadCalculator(RuntimeExerciseMetadataCatalog.of(listOf(metadata)))
    }

    private fun exercise(stableKey: String) = Exercise(
        stableKey = stableKey,
        name = if (stableKey == "ex_badminton_lesson") "배드민턴 레슨" else "배드민턴",
        category = "Sport",
        activityKind = "SPORT_SESSION"
    )

    private fun record(
        exercise: Exercise,
        date: LocalDate,
        sets: List<WorkoutSet>,
        entryRpe: Double? = null
    ): WorkoutEntryWithSets {
        val entryId = (exercise.stableKey + date + sets.hashCode()).hashCode().toLong()
        return WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = entryId,
                date = date.toString(),
                exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name,
                category = exercise.category,
                rpe = entryRpe
            ),
            sets = sets.mapIndexed { index, value ->
                value.copy(id = entryId * 10 + index, entryId = entryId, setIndex = index + 1)
            }
        )
    }

    private fun set(
        seconds: Int,
        rpe: Double? = null,
        confirmed: Boolean = true
    ) = WorkoutSet(entryId = 0, setIndex = 0, seconds = seconds, rpe = rpe, confirmed = confirmed)
}
