package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleLoadInputBuilderTest {
    @Test
    fun metadataAliasesBuildCanonicalBucketWeights() {
        val exercise = Exercise(
            name = "대퇴근 테스트",
            category = "근력",
            stableKey = "user_ex_quad",
            primaryMuscles = "대퇴근|quadriceps",
            secondaryMuscles = "둔근"
        )

        val loads = MuscleLoadInputBuilder.contributions(exercise)

        assertEquals(1.0, loads[MuscleBucket.QUADS] ?: 0.0, 0.001)
        assertEquals(0.5, loads[MuscleBucket.GLUTES] ?: 0.0, 0.001)
    }

    @Test
    fun exerciseNamesCannotCreateFunctionalCoreMuscleBuckets() {
        listOf("Pallof press", "Russian twist").forEachIndexed { index, name ->
            val exercise = Exercise(
                name = name,
                category = "Strength",
                stableKey = "name_only_$index"
            )
            val loads = MuscleLoadInputBuilder.contributions(exercise)

            assertTrue(loads.isEmpty())
        }
    }

    @Test
    fun semanticLookingNameAndKeyCannotCreateMuscleAttribution() {
        val exercise = Exercise(
            name = "Overhead press",
            category = "Strength",
            stableKey = "name_only_overhead_press"
        )
        val loads = MuscleLoadInputBuilder.contributions(exercise)

        assertTrue(loads.isEmpty())
    }
}
