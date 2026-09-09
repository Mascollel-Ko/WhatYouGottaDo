package com.training.trackplanner.data

/** Database-authoritative state captured on both sides of the raw-save transaction. */
data class RecordSetMutationResult(
    val date: String,
    val beforeCompletionState: StrengthSessionCompletionState,
    val afterCompletionState: StrengthSessionCompletionState,
    val newlyConfirmed: Boolean,
    val derivedAnalysisDirty: Boolean
) {
    val dateJustCompleted: Boolean
        get() = StrengthSessionCompletionDetector.eligible(beforeCompletionState, afterCompletionState)
}
