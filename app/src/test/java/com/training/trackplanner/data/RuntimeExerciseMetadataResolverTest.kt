package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RuntimeExerciseMetadataResolverTest {
    @Test
    fun resolutionOrderIsRoomThenCanonicalThenSafeDefault() {
        val builtIn = exercise("builtin_key", "Built in")
        val canonical = RuntimeExerciseMetadataDefaults.forExercise(builtIn).copy(programSlot = "CANONICAL_SLOT")
        val override = canonical.copy(programSlot = "ROOM_SLOT")
        val resolver = RuntimeExerciseMetadataResolver(
            canonicalCatalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical)),
            persistedRows = listOf(override)
        )

        assertEquals("ROOM_SLOT", resolver.resolve(builtIn).programSlot)
        assertEquals("NOT_APPLICABLE", resolver.resolve(exercise("missing", "Missing")).programSlot)

        val canonicalOnlyResolver = RuntimeExerciseMetadataResolver(
            canonicalCatalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical)),
            persistedRows = emptyList()
        )
        assertSame(canonical, canonicalOnlyResolver.resolve(builtIn))
    }

    private fun exercise(stableKey: String, name: String) = Exercise(
        name = name,
        category = "Strength",
        stableKey = stableKey
    )
}
