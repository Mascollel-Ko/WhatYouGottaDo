package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMuscleRoleCodecTest {
    @Test fun sixRolesRoundTripWithExplicitEmpty() {
        val key = "barbell_bench_press"
        val roles = listOf(
            UserMuscleRoleCodec.entry(key, "CHEST", CanonicalMuscleRole.PRIMARY),
            UserMuscleRoleCodec.entry(key, "TRICEPS", CanonicalMuscleRole.SECONDARY),
            UserMuscleRoleCodec.entry(key, "SHOULDERS", CanonicalMuscleRole.STABILIZER),
            UserMuscleRoleCodec.entry(key, "LATERAL_CORE", CanonicalMuscleRole.GENERAL_PARTICIPATION),
            UserMuscleRoleCodec.entry(key, "QUADS", CanonicalMuscleRole.NOT_SUPPORTED),
            UserMuscleRoleCodec.entry(key, "BICEPS", CanonicalMuscleRole.REVIEW_REQUIRED)
        )
        val encoded = UserMuscleRoleCodec.encode(roles)
        assertEquals(encoded, UserMuscleRoleCodec.encode(UserMuscleRoleCodec.decode(encoded, key)))
        assertTrue(UserMuscleRoleCodec.decode("", key).isEmpty())
    }

    @Test fun invalidAndDuplicateCodesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            UserMuscleRoleCodec.decode("UNKNOWN~PRIMARY~1.0", "custom")
        }
        assertThrows(IllegalArgumentException::class.java) {
            val row = UserMuscleRoleCodec.entry("custom", "CHEST", CanonicalMuscleRole.PRIMARY)
            UserMuscleRoleCodec.encode(listOf(row, row))
        }
    }

    @Test fun unreviewedCustomNeverGainsMuscleRelationsFromOldExerciseStrings() {
        val exercise = Exercise(
            stableKey = "user_ex_quad", name = "Custom", category = "Strength",
            isCustom = true, primaryMuscles = "QUADRICEPS", secondaryMuscles = "GLUTE"
        )
        val resolver = CanonicalMuscleRoleResolver(emptySet(), emptySet(), emptyList())
        assertEquals(CanonicalMuscleLookupKind.USER_CUSTOM_UNMAPPED, resolver.lookup(exercise).kind)
        assertTrue(resolver.lookup(exercise).relations.isEmpty())
    }

    @Test fun userTypedOverrideReplacesCanonicalOnlyForItsStableKey() {
        val base = CanonicalMuscleRoleResolver(
            setOf("built_in"), emptySet(),
            listOf(CanonicalMetadataRelation(
                domain = CanonicalRelationDomain.MUSCLE,
                relationKey = "M1", exerciseStableKey = "built_in",
                relationType = "PRIMARY", relationValue = "QUADS", coefficient = 1.0,
                sourceStableKey = "built_in", status = "APPROVED", provenance = "test"
            ))
        )
        val key = "built_in"
        val selected = UserMuscleRoleCodec.entry(key, "CHEST", CanonicalMuscleRole.PRIMARY)
        val userRow = ExerciseMetadataUserOverrideEntity(
            stableKey = key, fieldScope = ExerciseMetadataFieldScope.MUSCLE_RELATION.name,
            fieldKey = UserMuscleRoleCodec.FIELD_KEY,
            valueEncoding = ExerciseMetadataValueEncoding.TOKEN_SET.name,
            value = UserMuscleRoleCodec.encode(listOf(selected)), isExplicitEmpty = false,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "test"
        )
        assertEquals("QUADS", base.lookup(key).relations.single().muscleCode)
        val effective = base.withUserOverrides(listOf(userRow)).lookup(key)
        assertEquals("CHEST", effective.relations.single().muscleCode)
        assertEquals("QUADS", base.lookup(key).relations.single().muscleCode)
        assertEquals(CanonicalMuscleLookupKind.USER_CUSTOM_WITH_RELATIONS,
            base.withUserOverrides(listOf(userRow)).lookup(key, isCustom = true).kind)
        val merged = UserMuscleRoleCodec.effectiveRelations(
            listOf(CanonicalMetadataRelation(
                domain = CanonicalRelationDomain.MUSCLE,
                relationKey = "M1", exerciseStableKey = key, relationType = "PRIMARY",
                relationValue = "QUADS", coefficient = 1.0,
                sourceStableKey = key, status = "APPROVED", provenance = "test"
            )), listOf(userRow)
        )
        assertEquals(setOf("CHEST"), merged.map { it.relationValue }.toSet())
        assertFalse(merged.any { it.relationValue == "QUADS" })
    }
}
