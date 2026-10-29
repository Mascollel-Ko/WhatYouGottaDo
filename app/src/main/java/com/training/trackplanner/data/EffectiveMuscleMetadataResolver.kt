package com.training.trackplanner.data

/**
 * MUS-00.5: resolved authority for exercise muscle-role metadata.
 * A legacy user override is never silently replaced with canonical roles.
 */
enum class EffectiveMuscleMetadataSource {
    CANONICAL,
    LEGACY_USER_OVERRIDE,
    LEGACY_CUSTOM_EXERCISE,
    LEGACY_HISTORY_ONLY,
    LEGACY_UNKNOWN_EXERCISE,
    CANONICAL_RELATIONS_MISSING
}

data class LegacyMuscleFieldProjection(
    val primaryMuscles: String,
    val secondaryMuscles: String,
    /** Includes explicitly cleared (empty) values. */
    val overriddenFieldKeys: Set<String>
)

data class EffectiveMuscleMetadata(
    val canonical: CanonicalMuscleLookup,
    val source: EffectiveMuscleMetadataSource,
    val legacy: LegacyMuscleFieldProjection?
) {
    /** Safe for role-specific display/analysis only if no legacy user edit exists. */
    val usesCanonicalRoles: Boolean
        get() = source == EffectiveMuscleMetadataSource.CANONICAL

    val requiresLegacyCompatibility: Boolean
        get() = legacy != null
}

/**
 * Pure reconciliation of the effective exercise and its persisted override
 * rows. The repository facade reads rows from the DAO automatically.
 * It does not invent six canonical roles from the two legacy strings.
 */
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
        val muscleRows = overrides.filter { row ->
            row.fieldScope == ExerciseMetadataFieldScope.EXERCISE.name &&
                row.fieldKey in MUSCLE_FIELD_KEYS
        }.onEach(ExerciseMetadataUserOverrideEntity::validated)
        require(muscleRows.distinctBy { it.fieldKey }.size == muscleRows.size) {
            "Duplicate muscle override for exercise: ${exercise.stableKey}"
        }
        val byField = muscleRows.associateBy(ExerciseMetadataUserOverrideEntity::fieldKey)
        val lookup = canonicalResolver.lookup(
            exercise = exercise,
            legacyMuscleOverridePresent = byField.isNotEmpty()
        )
        val source = when {
            exercise.isCustom -> EffectiveMuscleMetadataSource.LEGACY_CUSTOM_EXERCISE
            byField.isNotEmpty() -> EffectiveMuscleMetadataSource.LEGACY_USER_OVERRIDE
            lookup.kind == CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS ->
                EffectiveMuscleMetadataSource.CANONICAL
            lookup.kind == CanonicalMuscleLookupKind.SELECTABLE_WITHOUT_RELATIONS ->
                EffectiveMuscleMetadataSource.CANONICAL_RELATIONS_MISSING
            lookup.kind == CanonicalMuscleLookupKind.HISTORY_ONLY_UNMAPPED ->
                EffectiveMuscleMetadataSource.LEGACY_HISTORY_ONLY
            else -> EffectiveMuscleMetadataSource.LEGACY_UNKNOWN_EXERCISE
        }
        val legacy = if (source == EffectiveMuscleMetadataSource.CANONICAL) {
            null
        } else {
            LegacyMuscleFieldProjection(
                primaryMuscles = byField[PRIMARY_FIELD]?.value ?: exercise.primaryMuscles,
                secondaryMuscles = byField[SECONDARY_FIELD]?.value ?: exercise.secondaryMuscles,
                overriddenFieldKeys = byField.keys.toSet()
            )
        }
        return EffectiveMuscleMetadata(lookup, source, legacy)
    }

    private companion object {
        const val PRIMARY_FIELD = "exercise.primaryMuscles"
        const val SECONDARY_FIELD = "exercise.secondaryMuscles"
        val MUSCLE_FIELD_KEYS = setOf(PRIMARY_FIELD, SECONDARY_FIELD)
    }
}
