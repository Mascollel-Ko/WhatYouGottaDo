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

    fun canChangeLoad(role: ProgressionRole, exerciseStableKey: String): Boolean =
        role == ProgressionRole.MAIN && exerciseStableKey.isNotBlank()
}
