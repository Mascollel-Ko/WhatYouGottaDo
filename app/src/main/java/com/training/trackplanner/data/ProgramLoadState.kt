package com.training.trackplanner.data

/** Typed meaning for the compatibility numeric load field. Legacy rows default to EXPLICIT_LOAD. */
enum class ProgramLoadState {
    EXPLICIT_LOAD,
    USER_CALIBRATION_REQUIRED,
    REAL_ZERO_LOAD,
    NOT_APPLICABLE
}

/** A plan awaiting user-selected load is never a completed training observation. */
internal fun WorkoutSet.isAnalysisEligibleCompletedSet(): Boolean =
    confirmed && loadState != ProgramLoadState.USER_CALIBRATION_REQUIRED
