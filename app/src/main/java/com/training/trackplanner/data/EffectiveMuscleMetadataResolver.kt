package com.training.trackplanner.data

/**
 * A single muscle-role authority for all live features.
 *
 * Retired two-field overrides are recognized only as a pending-review marker.
 * Their strings are never read, displayed, or projected as live muscle roles.
 * Backup import and the explicit upgrade-review UI own preservation of old data.
 */
enum class EffectiveMuscleMetadataSource {
    CANONICAL,
    LEGACY_USER_OVERRIDE,
    TYPED_USER_OVERRIDE,
    TYPED_CUSTOM_EXERCISE,
    LEGACY_CUSTOM_EXERCISE,
    LEGACY_HISTORY_ONLY,
    LEGACY_UNKNOWN_EXERCISE,
    CANONICAL_RELATIONS_MISSING
}

data class EffectiveMuscleMetadata(
    val canonical: CanonicalMuscleLookup,
    val source: EffectiveMuscleMetadataSource
) {
    val usesCanonicalRoles: Boolean
        get() = canonical.kind in setOf(
            CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS,
            CanonicalMuscleLookupKind.USER_CUSTOM_WITH_RELATIONS
        )

    val needsMigrationReview: Boolean
        get() = source in setOf(
            EffectiveMuscleMetadataSource.LEGACY_USER_OVERRIDE,
            EffectiveMuscleMetadataSource.LEGACY_CUSTOM_EXERCISE
        )
}

class EffectiveMuscleMetadataResolver(
    private val canonicalResolver: CanonicalMuscleRoleResolver
) {
    fun resolve(
        exercise: Exercise,
        overrides: List<ExerciseMetadataUserOverrideEntity>
    ): EffectiveMuscleMetadata {
        require(overrides.all { it.stableKey == exercise.stableKey }) {
            "Muscle override lookup must use one exact stableKey."
        }
        // Examine only the identity and scope of old rows, not their contents.
        val oldRows = overrides.filter { row ->
            row.fieldScope == ExerciseMetadataFieldScope.EXERCISE.name &&
                row.fieldKey in OLD_MUSCLE_FIELD_KEYS
        }.onEach(ExerciseMetadataUserOverrideEntity::validated)
        require(oldRows.distinctBy { it.fieldKey }.size == oldRows.size) {
            "Duplicate old muscle override for exercise: ${exercise.stableKey}"
        }
        val typed = overrides.any {
            it.fieldScope == ExerciseMetadataFieldScope.MUSCLE_RELATION.name &&
                it.fieldKey == UserMuscleRoleCodec.FIELD_KEY
        }
        val lookup = canonicalResolver.withUserOverrides(overrides).lookup(
            exercise = exercise, legacyMuscleOverridePresent = oldRows.isNotEmpty()
        )
        val source = when {
            typed && lookup.kind == CanonicalMuscleLookupKind.USER_CUSTOM_WITH_RELATIONS ->
                EffectiveMuscleMetadataSource.TYPED_CUSTOM_EXERCISE
            typed && lookup.kind == CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS ->
                EffectiveMuscleMetadataSource.TYPED_USER_OVERRIDE
            exercise.isCustom -> EffectiveMuscleMetadataSource.LEGACY_CUSTOM_EXERCISE
            oldRows.isNotEmpty() -> EffectiveMuscleMetadataSource.LEGACY_USER_OVERRIDE
            lookup.kind == CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS ->
                EffectiveMuscleMetadataSource.CANONICAL
            lookup.kind == CanonicalMuscleLookupKind.SELECTABLE_WITHOUT_RELATIONS ->
                EffectiveMuscleMetadataSource.CANONICAL_RELATIONS_MISSING
            lookup.kind == CanonicalMuscleLookupKind.HISTORY_ONLY_UNMAPPED ->
                EffectiveMuscleMetadataSource.LEGACY_HISTORY_ONLY
            else -> EffectiveMuscleMetadataSource.LEGACY_UNKNOWN_EXERCISE
        }
        return EffectiveMuscleMetadata(lookup, source)
    }

    private companion object {
        val OLD_MUSCLE_FIELD_KEYS = setOf("exercise.primaryMuscles", "exercise.secondaryMuscles")
    }
}
