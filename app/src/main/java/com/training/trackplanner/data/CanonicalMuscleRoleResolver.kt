package com.training.trackplanner.data

import java.util.Locale

/**
 * Read-only MUS-00 gateway for the six-role canonical muscle authority.
 *
 * This does not merge legacy primaryMuscles/secondaryMuscles and does not assign
 * training dose or change any UI, planning, fatigue, backup, or Room consumer.
 */
enum class CanonicalMuscleRole {
    PRIMARY,
    SECONDARY,
    STABILIZER,
    GENERAL_PARTICIPATION,
    NOT_SUPPORTED,
    REVIEW_REQUIRED
}

enum class CanonicalMuscleLookupKind {
    SELECTABLE_WITH_RELATIONS,
    SELECTABLE_WITHOUT_RELATIONS,
    HISTORY_ONLY_UNMAPPED,
    USER_CUSTOM_UNMAPPED,
    UNKNOWN_UNMAPPED,
    USER_CUSTOM_WITH_RELATIONS
}

data class CanonicalMuscleRoleEntry(
    val relationKey: String,
    val muscleCode: String,
    val role: CanonicalMuscleRole,
    val coefficient: Double?,
    val status: String,
    val sourceStableKey: String,
    val provenance: String
)

data class CanonicalMuscleLookup(
    val stableKey: String,
    val kind: CanonicalMuscleLookupKind,
    val relations: List<CanonicalMuscleRoleEntry>,
    /**
     * True if caller found an existing legacy muscle-field user override.
     * Do not silently discard that override when a consumer is migrated.
     */
    val legacyMuscleOverridePresent: Boolean
) {
    val hasCanonicalRelations: Boolean
        get() = kind == CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS

    val requiresOverridePolicy: Boolean
        get() = legacyMuscleOverridePresent

    fun forRole(role: CanonicalMuscleRole): List<CanonicalMuscleRoleEntry> =
        relations.filter { it.role == role }
}

/**
 * The input lists are the repository's manifest-verified identities and
 * MUSCLE relations. No history inheritance is inferred from sourceStableKey.
 * Custom/unknown exercises never inherit a similarly named built-in identity.
 */
class CanonicalMuscleRoleResolver(
    private val selectableStableKeys: Set<String>,
    private val historyOnlyStableKeys: Set<String>,
    private val relations: List<CanonicalMetadataRelation>,
    private val typedOverrides: Map<String, String> = emptyMap()
) {
    private val selectableKeys = selectableStableKeys.mapTo(mutableSetOf(), ::normalizeKey)
    private val historyKeys = historyOnlyStableKeys.mapTo(mutableSetOf(), ::normalizeKey)

    private val relationsByStableKey: Map<String, List<CanonicalMuscleRoleEntry>> = run {
        require(selectableKeys.intersect(historyKeys).isEmpty()) {
            "Canonical muscle selectable and history-only keys overlap."
        }
        require(relations.map { it.relationKey }.distinct().size == relations.size) {
            "Duplicate canonical muscle relation keys."
        }
        relations.map { relation ->
            require(relation.domain == CanonicalRelationDomain.MUSCLE) {
                "Non-muscle relation passed to muscle-role resolver: ${relation.relationKey}"
            }
            val key = normalizeKey(relation.exerciseStableKey)
            require(key in selectableKeys) {
                "Muscle relation references non-selectable identity: $key"
            }
            require(relation.relationKey.isNotBlank() && relation.relationValue.isNotBlank()) {
                "Incomplete canonical muscle relation."
            }
            require(relation.coefficient == null ||
                (relation.coefficient.isFinite() && relation.coefficient >= 0.0)) {
                "Invalid canonical muscle coefficient: ${relation.relationKey}"
            }
            key to CanonicalMuscleRoleEntry(
                relationKey = relation.relationKey,
                muscleCode = relation.relationValue,
                role = CanonicalMuscleRole.valueOf(relation.relationType),
                coefficient = relation.coefficient,
                status = relation.status,
                sourceStableKey = relation.sourceStableKey,
                provenance = relation.provenance
            )
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, rows) -> rows.sortedBy(CanonicalMuscleRoleEntry::relationKey) }
    }

    fun withUserOverrides(rows: List<ExerciseMetadataUserOverrideEntity>): CanonicalMuscleRoleResolver =
        CanonicalMuscleRoleResolver(
            selectableStableKeys, historyOnlyStableKeys, relations,
            rows.filter { it.fieldKey == UserMuscleRoleCodec.FIELD_KEY }
                .associate { it.stableKey.trim().lowercase(Locale.ROOT) to it.validated().value }
        )

    fun lookup(
        stableKey: String,
        isCustom: Boolean = false,
        legacyMuscleOverridePresent: Boolean = false
    ): CanonicalMuscleLookup {
        val normalizedKey = normalizeKey(stableKey)
        val userRows = typedOverrides[normalizedKey]?.let {
            UserMuscleRoleCodec.decode(it, normalizedKey)
        }
        val rows = userRows ?: if (!isCustom) relationsByStableKey[normalizedKey].orEmpty() else emptyList()
        val kind = when {
            isCustom && userRows != null -> CanonicalMuscleLookupKind.USER_CUSTOM_WITH_RELATIONS
            userRows != null -> CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS
            isCustom -> CanonicalMuscleLookupKind.USER_CUSTOM_UNMAPPED
            normalizedKey in selectableKeys && rows.isNotEmpty() ->
                CanonicalMuscleLookupKind.SELECTABLE_WITH_RELATIONS
            normalizedKey in selectableKeys ->
                CanonicalMuscleLookupKind.SELECTABLE_WITHOUT_RELATIONS
            normalizedKey in historyKeys -> CanonicalMuscleLookupKind.HISTORY_ONLY_UNMAPPED
            else -> CanonicalMuscleLookupKind.UNKNOWN_UNMAPPED
        }
        return CanonicalMuscleLookup(
            stableKey = normalizedKey,
            kind = kind,
            relations = rows,
            legacyMuscleOverridePresent = legacyMuscleOverridePresent
        )
    }

    fun lookup(exercise: Exercise, legacyMuscleOverridePresent: Boolean = false): CanonicalMuscleLookup =
        lookup(exercise.stableKey, exercise.isCustom, legacyMuscleOverridePresent)

    private fun normalizeKey(value: String): String = value.trim().lowercase(Locale.ROOT)
}
