package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun catalogResolveUsesUniqueCanonicalNameWhenBuiltInExerciseLosesStableKey() {
        val canonical = RuntimeExerciseMetadataDefaults.forIdentity("barbell_back_squat", "스쿼트")
        val catalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical))
        val corruptedDbExercise = exercise("lost_barbell_back_squat", "스쿼트")

        assertSame(canonical, catalog.resolve(corruptedDbExercise))
    }

    @Test
    fun resolverCatalogKeepsLostStableKeyExerciseLinkedToCanonicalMetadata() {
        val canonical = RuntimeExerciseMetadataDefaults.forIdentity("barbell_back_squat", "스쿼트")
            .copy(progressMetricType = "ESTIMATED_1RM")
        val resolver = RuntimeExerciseMetadataResolver(
            canonicalCatalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical)),
            persistedRows = emptyList()
        )
        val corruptedDbExercise = exercise("lost_barbell_back_squat", "스쿼트")
        val catalog = resolver.catalog(listOf(corruptedDbExercise))

        assertEquals("ESTIMATED_1RM", resolver.resolve(corruptedDbExercise).progressMetricType)
        assertEquals("ESTIMATED_1RM", catalog.resolve(corruptedDbExercise)?.progressMetricType)
    }

    @Test
    fun catalogNameFallbackIgnoresAmbiguousExerciseNames() {
        val first = RuntimeExerciseMetadataDefaults.forIdentity("first_key", "Duplicate")
        val second = RuntimeExerciseMetadataDefaults.forIdentity("second_key", "Duplicate")
        val catalog = RuntimeExerciseMetadataCatalog.of(listOf(first, second))
        val corruptedDbExercise = exercise("lost_key", "Duplicate")

        assertNull(catalog.resolve(corruptedDbExercise))
    }

    @Test
    fun stalePersistedDefaultMetadataDoesNotOverrideCanonicalAnalysisFields() {
        val canonical = RuntimeExerciseMetadataDefaults.forIdentity("barbell_back_squat", "Back Squat").copy(
            progressMetricType = "ESTIMATED_1RM",
            strengthProgressionGroup = "SQUAT",
            analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS|HYPERTROPHY_VOLUME")
        )
        val corruptedDbExercise = exercise("lost_barbell_back_squat", "Back Squat")
        val stalePersisted = RuntimeExerciseMetadataDefaults.forExercise(corruptedDbExercise)
        val resolver = RuntimeExerciseMetadataResolver(
            canonicalCatalog = RuntimeExerciseMetadataCatalog.of(listOf(canonical)),
            persistedRows = listOf(stalePersisted)
        )

        val resolved = resolver.resolve(corruptedDbExercise)
        val catalogResolved = resolver.catalog(listOf(corruptedDbExercise)).resolve(corruptedDbExercise)

        assertEquals("lost_barbell_back_squat", resolved.stableKey)
        assertEquals("ESTIMATED_1RM", resolved.progressMetricType)
        assertEquals("SQUAT", resolved.strengthProgressionGroup)
        assertTrue("STRENGTH_PROGRESS" in resolved.analysisEligibility)
        assertEquals(ProgressMetricRuntimeBehavior.ESTIMATED_1RM, resolved.progressBehavior)
        assertEquals("ESTIMATED_1RM", catalogResolved?.progressMetricType)
        assertTrue("STRENGTH_PROGRESS" in catalogResolved!!.analysisEligibility)
    }

    @Test
    fun exerciseRowMetadataRepairsStalePersistedDefaultWhenCanonicalIsMissing() {
        val dbExercise = exercise("legacy_squat_key", "Legacy Squat").copy(
            progressMetricType = "",
            strengthProgressionGroup = "SQUAT",
            estimated1RmEligible = true,
            volumeLoadEligible = true,
            analysisEligibility = "",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            trainingRole = "MAIN_STRENGTH"
        )
        val stalePersisted = RuntimeExerciseMetadataDefaults.forIdentity(dbExercise.stableKey, dbExercise.name)
        val resolver = RuntimeExerciseMetadataResolver(
            canonicalCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
            persistedRows = listOf(stalePersisted)
        )

        val resolved = resolver.resolve(dbExercise)

        assertEquals("ESTIMATED_1RM", resolved.progressMetricType)
        assertEquals("SQUAT", resolved.strengthProgressionGroup)
        assertTrue("STRENGTH_PROGRESS" in resolved.analysisEligibility)
        assertTrue("HYPERTROPHY_VOLUME" in resolved.analysisEligibility)
    }

    private fun exercise(stableKey: String, name: String) = Exercise(
        name = name,
        category = "Strength",
        stableKey = stableKey
    )
}
