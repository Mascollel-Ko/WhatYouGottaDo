package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

/**
 * Gate H: find the single future next-week workout after a confirmed workout.
 * Pure and intentionally pessimistic: ambiguous/missing lineage is never guessed.
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
    val wasApplied: Boolean get() = status == "NEXT_WEEK_UPDATED"
}

internal object BCoachPostConfirmLinkResolver {
    private const val SQUAT = BCoachLinkedSquatWeekPlanner.SQUAT

    data class Target(val status: String, val targetEntryId: Long? = null)

    fun resolve(
        sourceEntryId: Long,
        asOf: LocalDate,
        records: List<WorkoutEntryWithSets>,
        links: List<ProgramWorkoutLink>
    ): Target {
        val source = records.singleOrNull { it.entry.id == sourceEntryId } ?: return Target("SOURCE_MISSING")
        if (source.entry.exerciseStableKey != SQUAT) return Target("NOT_SQUAT")
        val sourceDate = runCatching { LocalDate.parse(source.entry.date) }.getOrNull()
            ?: return Target("SOURCE_DATE_INVALID")
        if (sourceDate > asOf) return Target("SOURCE_IN_FUTURE")
        if (source.sets.isEmpty() || source.sets.any {
            !it.confirmed || it.loadState != ProgramLoadState.EXPLICIT_LOAD ||
                !it.weightKg.isFinite() || it.weightKg <= 0.0 || it.reps !in 1..20 ||
                it.rpe?.takeIf { r -> r.isFinite() && r in 6.0..10.0 } == null
        }) return Target("WAITING_FOR_CONFIRMED_RPE_SETS")
        val sourceLink = links.singleOrNull { it.entryId == sourceEntryId }
            ?: return Target("SOURCE_NOT_PROGRAM_LINKED")
        val byId = records.associateBy { it.entry.id }
        val targets = links.filter { link ->
            link.applicationId == sourceLink.applicationId &&
                link.sourceProgramStableKey == sourceLink.sourceProgramStableKey &&
                link.dayOfWeek == sourceLink.dayOfWeek &&
                link.weekNumber == sourceLink.weekNumber + 1 &&
                link.sequence > sourceLink.sequence &&
                byId[link.entryId]?.entry?.exerciseStableKey == SQUAT
        }
        if (targets.isEmpty()) return Target("NO_NEXT_WEEK_SQUAT")
        if (targets.size != 1) return Target("MULTIPLE_NEXT_WEEK_SQUATS")
        val target = byId[targets.single().entryId] ?: return Target("TARGET_MISSING")
        val targetDate = runCatching { LocalDate.parse(target.entry.date) }.getOrNull()
            ?: return Target("TARGET_DATE_INVALID")
        if (targetDate <= sourceDate || targetDate < asOf) return Target("TARGET_DATE_NOT_FUTURE")
        if (target.sets.isEmpty() || target.sets.any { it.confirmed } ||
            target.entry.firstConfirmedAt != null || target.entry.completedAt != null
        ) return Target("TARGET_ALREADY_STARTED")
        return Target("LINKED_FUTURE_TARGET_READY", target.entry.id)
    }
}
