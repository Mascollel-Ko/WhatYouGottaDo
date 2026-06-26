package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun persistedLegacyMetadataKeepsCanonicalAppCueProfile() {
        val exercise = exercise("beep_key", "Beep drill")
        val canonical = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            appCueProfile = "RANDOM_BEEP_CUE"
        )
        val persisted = canonical.copy(
            programSlot = "ROOM_SLOT",
            appCueProfile = "NONE"
        )
        val resolved = RuntimeExerciseMetadataResolver(
            canonicalCatalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical)),
            persistedRows = listOf(persisted)
        ).resolve(exercise)

        assertEquals("ROOM_SLOT", resolved.programSlot)
        assertEquals("RANDOM_BEEP_CUE", resolved.appCueProfile)
    }

    @Test
    fun catalogResolveFailsWhenBuiltInExerciseLosesCanonicalStableKey() {
        val canonical = RuntimeExerciseMetadataDefaults.forIdentity("barbell_back_squat", "스쿼트")
        val catalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical))
        val corruptedDbExercise = exercise("lost_barbell_back_squat", "스쿼트")

        assertNull(catalog.resolve(corruptedDbExercise))
    }

    private fun exercise(stableKey: String, name: String) = Exercise(
        name = name,
        category = "Strength",
        stableKey = stableKey
    )
}
