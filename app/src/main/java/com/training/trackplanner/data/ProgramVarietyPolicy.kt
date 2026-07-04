package com.training.trackplanner.data

import kotlin.math.max

internal class ProgramVarietyPolicy {
    fun selectionPoolSize(request: ProgramSkeletonRequest, exerciseCount: Int): Int =
        max(8, exerciseCount * 3)

    fun chooseControlledCandidate(
        scored: List<Pair<ProgramCandidate, Double>>,
        weekIndex: Int,
        dayIndex: Int,
        itemIndex: Int,
        preference: ProgramVarietyPreference
    ): ProgramCandidate? {
        scored.firstOrNull() ?: return null
        val poolSize = when (preference) {
            ProgramVarietyPreference.LOW -> 4
            ProgramVarietyPreference.NORMAL -> 6
            ProgramVarietyPreference.HIGH -> 8
        }.coerceAtMost(scored.size)
        return scored[(weekIndex + dayIndex + itemIndex) % poolSize].first
    }

    fun allowsRepeat(
        history: ProgramSelectionHistory,
        candidate: ProgramCandidate,
        absoluteDay: Int,
        minimumGapDays: Int
    ): Boolean = history.allowsRepeat(candidate, absoluteDay, minimumGapDays)

    fun recordSelection(
        history: ProgramSelectionHistory,
        candidate: ProgramCandidate,
        weekIndex: Int,
        absoluteDay: Int,
        coveragePolicy: CoverageAccountingPolicy
    ) {
        history.record(candidate, weekIndex, absoluteDay, coveragePolicy)
    }
}
