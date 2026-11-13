package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgressionLinkMode
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgramProgressionItem
import com.training.trackplanner.data.ProgramProgressionTrack

/**
 * B-Coach execution authority is independent of AUTO session membership.
 * An automatically connected accessory must never become a main merely because
 * it is the only track for an exercise (legacy inference did exactly that).
 *
 * This policy is snapshotted in ProgramWorkoutLink.role when a B program is applied;
 * it does not depend on later edits to the program template.
 */
internal object BCoachMainPolicy {
    const val WEIGHTED_PULL_UP = "ex_e41f4c2b"

    val canonicalFour = setOf(
        "barbell_back_squat", "barbell_deadlift",
        "barbell_bench_press", WEIGHTED_PULL_UP
    )

    fun appliedRole(
        exerciseStableKey: String,
        track: ProgramProgressionTrack?,
        binding: ProgramProgressionItem?,
        allBindings: List<ProgramProgressionItem>
    ): ProgressionRole {
        if (track?.roleOverride == ProgressionRole.ASSISTANCE) return ProgressionRole.ASSISTANCE
        val explicitlyConnected = binding != null && allBindings.any {
            it.trackId == binding.trackId && it.linkMode == ProgressionLinkMode.EXISTING
        }
        val main = track?.roleOverride == ProgressionRole.MAIN ||
            binding?.signature?.plannerRole == ProgressionRole.MAIN ||
            explicitlyConnected || exerciseStableKey in canonicalFour
        return if (main) ProgressionRole.MAIN else ProgressionRole.ASSISTANCE
    }

    /** Legacy B links may have an inferred MAIN role from a single AUTO track.
     * For noncanonical lifts, automatic progression requires the new role snapshot.
     * Old applied programs can be safely recreated with the explicit role policy.
     */
    fun canChangeLoad(role: ProgressionRole, exerciseStableKey: String,
                      programMetadataJson: String?): Boolean =
        role == ProgressionRole.MAIN &&
            (exerciseStableKey in canonicalFour ||
                programMetadataJson?.contains("\\\"bCoachRolePolicySchema\\\":\\\"main-role-snapshot-1\\\"") == true)
}
