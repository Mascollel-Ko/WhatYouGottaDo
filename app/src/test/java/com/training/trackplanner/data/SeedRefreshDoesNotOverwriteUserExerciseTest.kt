package com.training.trackplanner.data

import org.junit.Assert.assertNull
import org.junit.Test

class SeedRefreshDoesNotOverwriteUserExerciseTest {
    @Test
    fun customExerciseIsExcludedFromSeedMerge() {
        val userExercise = Exercise(
            id = 17,
            name = "User exercise",
            category = "Strength",
            stableKey = "user_ex_123e4567-e89b-12d3-a456-426614174000",
            isCustom = true
        )
        val seedCollision = userExercise.copy(
            id = 0,
            name = "Seed collision",
            stableKey = "built_in_key",
            isCustom = false
        )

        assertNull(ExerciseStableKeyPolicy.mergeSeed(userExercise, seedCollision))
    }
}
