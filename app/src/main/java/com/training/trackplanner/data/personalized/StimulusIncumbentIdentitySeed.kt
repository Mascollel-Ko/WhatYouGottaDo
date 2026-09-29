package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton

/** Lossless B5 view of the CONTROL owners used to avoid proposing existing identities. */
data class StimulusIncumbentIdentity(
    val stableKey: String,
    val selectionRole: String
) {
    init {
        require(stableKey.isNotBlank()) { "B5 incumbent identity requires a stable key" }
    }
}

/** Minimal identity-only input to B5; it deliberately carries no program, request, or prescription. */
data class StimulusIncumbentIdentitySeed(
    val owners: List<StimulusIncumbentIdentity>
) {
    init {
        require(owners.distinct().size == owners.size) { "B5 incumbent seed contains a duplicate owner identity" }
    }

    /** B5's historical key-set semantics, derived without discarding owner identity in the seed. */
    val stableKeys: Set<String>
        get() = owners.mapTo(linkedSetOf(), StimulusIncumbentIdentity::stableKey)

    companion object {
        /** One-way projection from CONTROL. Repeated weekly rows collapse, distinct roles do not. */
        fun fromControl(control: GeneratedProgramSkeleton): StimulusIncumbentIdentitySeed =
            fromFinalizedOwners(control.items.map { item ->
                StimulusIncumbentIdentity(item.exerciseStableKey, item.selectionRole)
            })

        /** Builds the B5 projection from an already-finalized builder owner state. */
        fun fromFinalizedOwners(owners: Iterable<StimulusIncumbentIdentity>): StimulusIncumbentIdentitySeed =
            StimulusIncumbentIdentitySeed(
                owners = owners.distinct().sortedWith(
                    compareBy(StimulusIncumbentIdentity::stableKey, StimulusIncumbentIdentity::selectionRole)
                )
            )
    }
}
