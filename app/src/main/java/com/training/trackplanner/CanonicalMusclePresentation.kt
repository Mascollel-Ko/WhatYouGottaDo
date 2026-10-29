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
