package com.training.trackplanner.analysis.lab

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanonicalMuscleLoadSourceSwitchTest {
    private val repo = CanonicalExerciseMetadataRepository(
        ApplicationProvider.getApplicationContext<Context>()
    )
    private val roles = repo.muscleRoleResolver()

    private fun exercise(key: String) = repo.exercises().first { it.stableKey == key }

    private fun credited(exercise: Exercise): Map<MuscleBucket, Double> =
        MuscleLoadInputBuilder.contributions(exercise, roles.lookup(exercise))

    @Test
    fun backSquatUsesOnlyCanonicalQuadAndGlutePrimaries() {
        val squat = exercise("barbell_back_squat").copy(
            primaryMuscles = "HAMSTRING,CORE", secondaryMuscles = "LAT"
        )
        assertEquals(
            mapOf(MuscleBucket.QUADS to 1.0, MuscleBucket.GLUTES to 1.0),
            credited(squat)
        )
    }

    @Test
    fun deadliftDoesNotCreditStabilizingLatsOrSpinalErectors() {
        val deadlift = exercise("barbell_deadlift").copy(
            primaryMuscles = "LAT,ERECTOR_SPINAE,QUADRICEPS"
        )
        assertEquals(
            mapOf(MuscleBucket.GLUTES to 1.0, MuscleBucket.HAMSTRINGS to 1.0),
            credited(deadlift)
        )
    }

    @Test
    fun benchAndLegPressUseCanonicalSecondaryCoefficients() {
        val bench = credited(exercise("barbell_bench_press").copy(primaryMuscles = "BICEPS"))
        assertEquals(1.0, bench.getValue(MuscleBucket.CHEST), 0.001)
        assertEquals(0.5, bench.getValue(MuscleBucket.SHOULDERS), 0.001)
        assertEquals(0.5, bench.getValue(MuscleBucket.TRICEPS), 0.001)
        assertFalse(MuscleBucket.BICEPS in bench)

        val leg = credited(exercise("ex_ab468462").copy(primaryMuscles = "HAMSTRING"))
        assertEquals(1.0, leg.getValue(MuscleBucket.QUADS), 0.001)
        assertEquals(0.5, leg.getValue(MuscleBucket.GLUTES), 0.001)
        assertFalse(MuscleBucket.HAMSTRINGS in leg)
    }

    @Test
    fun coreOnlyExerciseDoesNotInventTwelveBucketMuscleAttribution() {
        assertTrue(credited(exercise("band_pallof_press")).isEmpty())
    }

    @Test
    fun unknownKeyCannotImpersonateCanonicalExerciseByName() {
        val fake = Exercise(
            name = "벤치프레스", category = "근력운동", stableKey = "unknown_bench",
            primaryMuscles = "CHEST", secondaryMuscles = "TRICEPS"
        )
        assertTrue(credited(fake).isEmpty())
    }

    @Test
    fun customExerciseUsesTemporaryCompatibilityUntilOneTimeImport() {
        val custom = Exercise(
            name = "내 하체운동", category = "근력운동",
            stableKey = "user_ex_quad", isCustom = true,
            primaryMuscles = "QUADRICEPS", secondaryMuscles = "GLUTE"
        )
        val buckets = credited(custom)
        assertEquals(1.0, buckets.getValue(MuscleBucket.QUADS), 0.001)
        assertEquals(0.5, buckets.getValue(MuscleBucket.GLUTES), 0.001)
    }

    @Test
    fun liveSeriesUsesStableKeyAndPreservesRpeAndWeekAggregation() {
        val squat = exercise("barbell_back_squat").copy(primaryMuscles = "HAMSTRING")
        val row = record("barbell_back_squat", "2026-06-10", 100.0, 5, 0, 8.0)
        val series = StrengthAndMuscleMetricSeriesBuilder.build(
            entriesWithSets = listOf(row),
            exercises = listOf(squat),
            canonicalMuscleRoles = roles
        )
        assertEquals(575.0, series.getValue(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY).single().value ?: 0.0, 0.01)
        assertEquals(575.0, series.getValue(TrendMetricId.MUSCLE_GLUTES_LOAD_DAILY).single().value ?: 0.0, 0.01)
        assertTrue(series[TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_DAILY].isNullOrEmpty())
    }

    @Test
    fun existingHistoricalSidePlankHoldPolicySurvivesCanonicalSwitch() {
        val row = record("ex_f6d43398", "2026-06-10", 0.0, 0, 30, 8.0)
        val series = StrengthAndMuscleMetricSeriesBuilder.build(
            entriesWithSets = listOf(row),
            exercises = emptyList(),
            canonicalMuscleRoles = roles
        )
        assertEquals(8.625,
            series.getValue(TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_DAILY).single().value ?: 0.0, 0.001)
        assertEquals(3.45,
            series.getValue(TrendMetricId.MUSCLE_SHOULDERS_LOAD_DAILY).single().value ?: 0.0, 0.001)
    }

    private fun record(
        stableKey: String, date: String, weight: Double, reps: Int, seconds: Int, rpe: Double
    ) = WorkoutEntryWithSets(
        entry = WorkoutEntry(
            date = date, exerciseStableKey = stableKey,
            exerciseName = "test", category = "근력운동"
        ),
        sets = listOf(WorkoutSet(
            entryId = 1, setIndex = 1, weightKg = weight, reps = reps,
            seconds = seconds, confirmed = true, rpe = rpe
        ))
    )
}
