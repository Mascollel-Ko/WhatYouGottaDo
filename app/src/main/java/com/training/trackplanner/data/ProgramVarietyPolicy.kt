package com.training.trackplanner.data

import kotlin.math.max

internal class ProgramVarietyPolicy {
    fun selectionPoolSize(request: ProgramSkeletonRequest, exerciseCount: Int): Int =
        max(30, exerciseCount * 8)

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

    fun distributionWarnings(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): List<String> {
        val weeklyStableOveruse = items
            .filterNot { it.selectionRole == ProgramExerciseRole.ANCHOR.name }
            .groupBy { Triple(it.weekNumber, it.stableKey, it.selectionRole) }
            .any { (key, rows) -> key.second.isNotBlank() && rows.size > 2 }
        val lowDistinctWeek = if (request.availableDaysPerWeek >= 5) {
            items.groupBy(ProgramSkeletonItem::weekNumber).any { (_, rows) ->
                rows.map(ProgramSkeletonItem::stableKey).filter(String::isNotBlank).toSet().size < 8
            }
        } else {
            false
        }
        val transferTargetOveruse = items
            .filter { it.selectionRole == ProgramExerciseRole.TRANSFER.name }
            .groupBy { it.redundancyGroup.ifBlank { it.stableKey } }
            .any { (key, rows) ->
                key.isNotBlank() && key != "NOT_APPLICABLE" &&
                    rows.map(ProgramSkeletonItem::weekNumber).distinct().size >= request.durationWeeks &&
                    rows.size > request.durationWeeks * 2
            }
        return buildList {
            if (weeklyStableOveruse) add("PROGRAM_WEEKLY_STABLEKEY_OVERUSE")
            if (lowDistinctWeek) add("PROGRAM_WEEKLY_DISTINCT_EXERCISE_LOW")
            if (transferTargetOveruse) add("PROGRAM_WIDE_TRANSFER_TARGET_OVERUSE")
        }
    }
}
