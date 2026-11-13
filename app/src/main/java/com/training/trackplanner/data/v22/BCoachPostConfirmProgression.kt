package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

/**
 * Gate I: follow the Plan editor's explicit session links after a confirmed workout.
 * The next linked program occurrence is not necessarily the next calendar week or day.
 */
data class BCoachPostConfirmOutcome(
    val sourceEntryId: Long,
    val status: String,
    val targetEntryId: Long? = null,
    val nextLoadKg: Double? = null,
    val nextReps: Int? = null,
    val nextSets: Int? = null,
    val selectedAction: String? = null,
    val detail: String? = null
) {
    val wasApplied: Boolean get() = status == "NEXT_LINKED_UPDATED"
}

internal object BCoachPostConfirmLinkResolver {
    private const val SQUAT = BCoachLinkedSquatWeekPlanner.SQUAT

    data class Target(val status: String, val targetEntryId: Long? = null)

    fun resolve(
        sourceEntryId: Long,
        asOf: LocalDate,
        records: List<WorkoutEntryWithSets>,
        links: List<ProgramWorkoutLink>,
        exerciseStableKey: String = SQUAT
    ): Target {
        val source = records.singleOrNull { it.entry.id == sourceEntryId } ?: return Target("SOURCE_MISSING")
        if (source.entry.exerciseStableKey != exerciseStableKey) return Target("TRACK_EXERCISE_MISMATCH")
        val sourceDate = runCatching { LocalDate.parse(source.entry.date) }.getOrNull()
            ?: return Target("SOURCE_DATE_INVALID")
        if (sourceDate > asOf) return Target("SOURCE_IN_FUTURE")
        if (source.sets.isEmpty() || source.sets.any {
            !it.confirmed || it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
                !it.weightKg.isFinite() ||
                (if (exerciseStableKey == BCoachMainPolicy.WEIGHTED_PULL_UP)
                    it.weightKg < 0.0 else it.weightKg <= 0.0) || it.reps !in 1..20 ||
                (it.rpe ?: source.entry.rpe)?.takeIf { r -> r.isFinite() && r in 6.0..10.0 } == null
        }) return Target("WAITING_FOR_CONFIRMED_RPE_SETS")
        val sourceLink = links.singleOrNull { it.entryId == sourceEntryId }
            ?: return Target("SOURCE_NOT_PROGRAM_LINKED")
        val matched = BCoachProgramLinkChain.successor(sourceLink, links)
        if (!matched.found) return Target(matched.status)
        val matchedLink = checkNotNull(matched.link)
        val target = records.singleOrNull { it.entry.id == matchedLink.entryId }
            ?: return Target("TARGET_MISSING")
        if (target.entry.exerciseStableKey != exerciseStableKey) return Target("TRACK_EXERCISE_MISMATCH")
        val targetDate = runCatching { LocalDate.parse(target.entry.date) }.getOrNull()
            ?: return Target("TARGET_DATE_INVALID")
        if (targetDate < asOf || targetDate < sourceDate) return Target("TARGET_DATE_NOT_FUTURE")
        if (target.sets.isEmpty() || target.sets.any { it.confirmed } ||
            target.entry.firstConfirmedAt != null || target.entry.completedAt != null
        ) return Target("TARGET_ALREADY_STARTED")
        return Target("LINKED_FUTURE_TARGET_READY", target.entry.id)
    }
}
