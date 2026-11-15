package com.training.trackplanner.data

/** Typed meaning for the compatibility numeric load field. Legacy rows default to EXPLICIT_LOAD. */
enum class ProgramLoadState {
    EXPLICIT_LOAD,
    USER_CALIBRATION_REQUIRED,
    REAL_ZERO_LOAD,
    NOT_APPLICABLE
}

/** Set-level provenance. LEGACY_UNKNOWN preserves old rows without guessing their origin. */
enum class ProgramLoadSource {
    USER_FIXED,
    PERSONAL_MODEL,
    DIRECT_HISTORY_ESTIMATE,
    HISTORY_HOLD,
    CALIBRATION_REQUIRED,
    NON_LOAD_METRIC,
    LEGACY_UNKNOWN
}

/** A plan awaiting user-selected load is never a completed training observation. */
internal fun WorkoutSet.isAnalysisEligibleCompletedSet(): Boolean =
    confirmed && loadState != ProgramLoadState.USER_CALIBRATION_REQUIRED
