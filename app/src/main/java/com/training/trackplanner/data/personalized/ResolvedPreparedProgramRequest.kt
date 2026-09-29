package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonRequest

/** Shared upstream request inputs for CONTROL and canonical B5/B6 generation. */
internal data class ResolvedPreparedProgramRequest(
    val request: ProgramSkeletonRequest,
    val frequencyProvenance: PlanningFrequencyProvenance
)
