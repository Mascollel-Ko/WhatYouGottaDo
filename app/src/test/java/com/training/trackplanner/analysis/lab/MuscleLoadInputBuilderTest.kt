package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleLoadInputBuilderTest {
    @Test
    fun metadataAliasesBuildCanonicalBucketWeights() {
        val exercise = Exercise(
            id = 1,
            name = "대퇴근 테스트",
            category = "근력",
            stableKey = "user_ex_quad",
            primaryMuscles = "대퇴근|quadriceps",
            secondaryMuscles = "둔근"
        )

        val loads = MuscleLoadInputBuilder.contributions(
            exercise = exercise,
            entry = WorkoutEntry(date = "2026-06-10", exerciseId = exercise.id, exerciseName = exercise.name, category = exercise.category)
        )

        assertEquals(1.0, loads[MuscleBucket.QUADS] ?: 0.0, 0.001)
        assertEquals(0.5, loads[MuscleBucket.GLUTES] ?: 0.0, 0.001)
    }
}
