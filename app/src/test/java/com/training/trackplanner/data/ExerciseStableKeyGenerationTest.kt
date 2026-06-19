package com.training.trackplanner.data

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseStableKeyGenerationTest {
    @Test
    fun generatedKeyUsesUserPrefixAndUuidWithoutExerciseName() {
        val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        val key = UserExerciseStableKeyGenerator.generate(uuid)

        assertEquals("user_ex_123e4567-e89b-12d3-a456-426614174000", key)
        assertTrue(UserExerciseStableKeyGenerator.isUserExerciseKey(key))
        assertTrue(UUID.fromString(key.removePrefix(UserExerciseStableKeyGenerator.PREFIX)) == uuid)
    }

    @Test
    fun independentlyGeneratedKeysDoNotDependOnDuplicateNames() {
        val first = UserExerciseStableKeyGenerator.generate(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        )
        val second = UserExerciseStableKeyGenerator.generate(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        )

        assertNotEquals(first, second)
    }
}
