package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalMuscleRoleResolverTest {
    private val repository = CanonicalExerciseMetadataRepository(
        ApplicationProvider.getApplicationContext<android.content.Context>()
    )
    private val resolver by lazy { repository.muscleRoleResolver() }

    @Test
    fun manifestVerifiedBenchHasExactRoleSeparationWithoutLegacyStringInference() {
        val lookup = resolver.lookup("  BARBELL_BENCH_PRESS  ")
        assertEquals("barbell_bench_press", lookup.stableKey)
        assertEquals(CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS, lookup.kind)
        assertTrue(lookup.hasCanonicalRelations)
        assertEquals(setOf("CHEST"), lookup.forRole(CanonicalMuscleRole.PRIMARY).map { it.muscleCode }.toSet())
        assertEquals(
            setOf("SHOULDERS", "TRICEPS"),
            lookup.forRole(CanonicalMuscleRole.SECONDARY).map { it.muscleCode }.toSet()
        )
        assertTrue(lookup.forRole(CanonicalMuscleRole.STABILIZER).any { it.muscleCode == "ANTERIOR_CORE" })
        assertTrue(lookup.relations.all { it.relationKey.isNotBlank() && it.provenance.isNotBlank() })
        assertFalse(lookup.requiresOverridePolicy)
    }

    @Test
    fun stabilizerIsNotASecondPrimaryAndReviewStatusIsPreserved() {
        val squat = resolver.lookup("barbell_back_squat")
        assertEquals(setOf("GLUTES", "QUADS"), squat.forRole(CanonicalMuscleRole.PRIMARY).map { it.muscleCode }.toSet())
        val core = squat.relations.single { it.muscleCode == "ANTERIOR_CORE" }
        assertEquals(CanonicalMuscleRole.STABILIZER, core.role)
        assertEquals(0.0, core.coefficient)
        assertTrue(core.status.isNotBlank())
    }

    @Test
    fun historyOnlyCustomAndUnknownHaveDifferentMissingReasons() {
        val historyKey = "ex_99728d25"
        assertTrue(repository.identity(historyKey)?.historyOnly == true)
        assertEquals(CanonicalMuscleLookupKind.HISTORY_ONLY_UNMAPPED, resolver.lookup(historyKey).kind)
        assertTrue(resolver.lookup(historyKey).relations.isEmpty())

        val custom = resolver.lookup("user_exercise_example", isCustom = true)
        assertEquals(CanonicalMuscleLookupKind.USER_CUSTOM_UNMAPPED, custom.kind)
        assertFalse(custom.hasCanonicalRelations)
        assertTrue(custom.relations.isEmpty())

        val unknown = resolver.lookup("unrecognized_exercise_example")
        assertEquals(CanonicalMuscleLookupKind.UNKNOWN_UNMAPPED, unknown.kind)
        assertTrue(unknown.relations.isEmpty())
    }

    @Test
    fun existingUserOverrideIsFlaggedInsteadOfSilentlyMergedOrDropped() {
        val result = resolver.lookup("barbell_bench_press", legacyMuscleOverridePresent = true)
        assertEquals(CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS, result.kind)
        assertTrue(result.requiresOverridePolicy)
        assertEquals(setOf("CHEST"), result.forRole(CanonicalMuscleRole.PRIMARY).map { it.muscleCode }.toSet())
        val custom = resolver.lookup("user_exercise_example", isCustom = true, legacyMuscleOverridePresent = true)
        assertTrue(custom.requiresOverridePolicy)
        assertEquals(CanonicalMuscleLookupKind.USER_CUSTOM_UNMAPPED, custom.kind)
    }

    @Test
    fun knownSelectableWithNoRelationsIsNotTreatedAsUnknownOrCustom() {
        val artificial = CanonicalMuscleRoleResolver(setOf("exercise_a"), setOf("old_exercise"), emptyList())
        assertEquals(
            CanonicalMuscleLookupKind.SELECTABLE_WITHOUT_RELATIONS,
            artificial.lookup("exercise_a").kind
        )
        assertEquals(CanonicalMuscleLookupKind.HISTORY_ONLY_UNMAPPED, artificial.lookup("old_exercise").kind)
        assertEquals(CanonicalMuscleLookupKind.UNKNOWN_UNMAPPED, artificial.lookup("other").kind)
    }

    @Test
    fun refusesForeignDomainsUnknownRolesAndNonSelectableRelationships() {
        fun row(domain: CanonicalRelationDomain, role: String = "PRIMARY", key: String = "exercise_a") =
            CanonicalMetadataRelation(
                domain = domain,
                relationKey = "MUSCLE_TEST",
                exerciseStableKey = key,
                relationType = role,
                relationValue = "QUADS",
                coefficient = 1.0,
                sourceStableKey = key,
                status = "PROVISIONAL_MECHANICS_V1",
                provenance = "test_fixture"
            )
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalMuscleRoleResolver(setOf("exercise_a"), emptySet(), listOf(row(CanonicalRelationDomain.OFI)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalMuscleRoleResolver(setOf("exercise_a"), emptySet(), listOf(row(CanonicalRelationDomain.MUSCLE, "UNSUPPORTED_ROLE")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalMuscleRoleResolver(setOf("exercise_a"), emptySet(), listOf(row(CanonicalRelationDomain.MUSCLE, key = "unknown")))
        }
    }
}
