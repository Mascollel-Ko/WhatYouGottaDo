package com.training.trackplanner.data

/** Generic editor/execution projection only. No planner request, schedule or personalized decision. */
data class ProgramExecutionDraft(
    val items: List<ProgramSkeletonItem>,
    val progressionSessions: List<DraftProgressionSession> = emptyList()
)

internal fun GeneratedProgramSkeleton.executionDraft() = ProgramExecutionDraft(items, progressionSessions)

internal fun GeneratedProgramSkeleton.withExecutionDraft(draft: ProgramExecutionDraft) =
    copy(items = draft.items, progressionSessions = draft.progressionSessions)
