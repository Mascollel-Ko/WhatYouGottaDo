package com.training.trackplanner.data

import com.training.trackplanner.data.program.legacy.LegacyAutoSkeleton

/**
 * Post-finalization editor overlay. Stable Legacy localIds own bindings; shared sessions own settings.
 * Never stored in or fed back to the frozen planner. Prescriptions are read-only projections.
 */
data class LegacyProgressionDraft(
    val bindings: Map<String, DraftProgressionBinding> = emptyMap(),
    val sessions: List<DraftProgressionSession> = emptyList()
) {
    internal fun executionDraft(skeleton: LegacyAutoSkeleton): ProgramExecutionDraft {
        require(skeleton.items.map { it.localId }.distinct().size == skeleton.items.size)
        return ProgramExecutionDraft(skeleton.items.map { item ->
            // An explicit exercise replacement is a new eligibility/identity question, not a substring match.
            val binding = bindings[item.localId]?.takeIf { it.signature.exerciseStableKey == item.exerciseStableKey }
            ProgramSkeletonItem(
                localId = item.localId, weekNumber = item.weekNumber, dayOfWeek = item.dayOfWeek,
                orderIndex = item.orderIndex, exerciseStableKey = item.exerciseStableKey, exerciseName = item.exerciseName,
                category = item.category, restSeconds = item.restSeconds, prescription = item.prescription,
                setCount = item.setCount, reps = item.reps, weightKg = item.weightKg, seconds = item.seconds,
                selectionReason = item.selectionReason, weightSource = item.weightSource,
                trainingSlot = item.trainingSlot, dayIntensity = item.dayIntensity,
                setPrescriptions = LegacyAutoSetRows.resolve(item), progressionBinding = binding
            )
        }, sessions)
    }

    internal fun reconcile(skeleton: LegacyAutoSkeleton, context: ProgressionDraftContext): LegacyProgressionDraft =
        fromExecutionDraft(executionDraft(skeleton).reconcileProgression(context.eligibleKeys, context.oneRmSnapshots))

    companion object {
        /** Only session state crosses back from the generic editor, never exercise/prescription changes. */
        internal fun fromExecutionDraft(draft: ProgramExecutionDraft) = LegacyProgressionDraft(
            draft.items.mapNotNull { item -> item.progressionBinding?.let { item.localId to it } }.toMap(),
            draft.progressionSessions
        )
    }
}
