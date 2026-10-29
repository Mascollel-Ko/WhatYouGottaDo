package com.training.trackplanner

import android.content.Context
import com.training.trackplanner.data.CanonicalMuscleRole
import com.training.trackplanner.data.EffectiveMuscleMetadata

/**
 * Canonical muscleCode uses MUSCLE_GROUP, not the legacy MUSCLE domain.
 * Registered metadata labels and search aliases already cover both locales.
 * This is deliberately opt-in; no live screen is changed in MUS-00.5.
 */
internal class CanonicalMusclePresentation(
    private val catalogue: MetadataDisplayCatalogue
) {
    fun optionsForRole(
        metadata: EffectiveMuscleMetadata,
        role: CanonicalMuscleRole
    ): List<MetadataDisplayOption> {
        if (!metadata.usesCanonicalRoles || role !in DISPLAYABLE_ROLES) return emptyList()
        return metadata.canonical.forRole(role)
            .map { row -> catalogue.option(MetadataDisplayField.MUSCLE_GROUP, row.muscleCode) }
            .distinctBy(MetadataDisplayOption::code)
    }

    /**
     * Search permits all participating roles, including STABILIZER, but never
     * NOT_SUPPORTED/REVIEW_REQUIRED. A built-in's canonical identity wins over
     * an unmigrated legacy override; this is not the user editor policy.
     */
    fun searchableOptions(metadata: EffectiveMuscleMetadata): List<MetadataDisplayOption> =
        metadata.canonical.relations
            .asSequence()
            .filter { it.role in DISPLAYABLE_ROLES }
            .map { catalogue.option(MetadataDisplayField.MUSCLE_GROUP, it.muscleCode) }
            .distinctBy(MetadataDisplayOption::code)
            .toList()

    companion object {
        private val DISPLAYABLE_ROLES = setOf(
            CanonicalMuscleRole.PRIMARY,
            CanonicalMuscleRole.SECONDARY,
            CanonicalMuscleRole.STABILIZER,
            CanonicalMuscleRole.GENERAL_PARTICIPATION
        )
        fun from(context: Context): CanonicalMusclePresentation =
            CanonicalMusclePresentation(MetadataDisplayCatalogue.from(context))
    }
}
