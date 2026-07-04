package com.training.trackplanner.data

internal class ProgramSelectionHistory {
    private val stableKeyDays = mutableMapOf<String, MutableList<Int>>()
    private val redundancyDays = mutableMapOf<String, MutableList<Int>>()
    private val familyWeeklyExposure = mutableMapOf<Pair<Int, String>, Int>()
    private val slotExposure = mutableMapOf<ProgramSlotId, Double>()

    fun penalty(candidate: ProgramCandidate, weekIndex: Int, absoluteDay: Int): Double {
        val stableGap = stableKeyDays[candidate.exercise.stableKey]
            .orEmpty()
            .maxOrNull()
            ?.let { absoluteDay - it }
            ?: Int.MAX_VALUE
        val stablePenalty = when {
            candidate.isRehabLikeActivation && stableGap <= 14 -> 6.0
            candidate.isRehabLikeActivation && stableGap <= 28 -> 2.5
            candidate.isAnchor && stableGap <= 7 -> 0.45
            candidate.isAnchor && stableGap <= 14 -> 0.15
            stableGap <= 7 -> 4.5
            stableGap <= 14 -> 2.0
            else -> 0.0
        }
        val redundancyKey = candidate.metadata?.redundancyGroup.orEmpty()
        val redundancyGap = redundancyDays[redundancyKey]
            .orEmpty()
            .maxOrNull()
            ?.let { absoluteDay - it }
            ?: Int.MAX_VALUE
        val redundancyPenalty = when {
            redundancyKey.isBlank() || redundancyKey == "NOT_APPLICABLE" -> 0.0
            redundancyGap <= 3 -> 1.4
            redundancyGap <= 7 -> 0.7
            else -> 0.0
        }
        val familyKey = candidate.metadata?.movementFamily.orEmpty()
        val familyCount = familyWeeklyExposure[weekIndex to familyKey] ?: 0
        val familyPenalty = when {
            familyKey.isBlank() || familyKey == "NOT_APPLICABLE" -> 0.0
            familyCount <= 1 -> 0.0
            else -> (familyCount - 1) * 0.65
        }
        return stablePenalty + redundancyPenalty + familyPenalty
    }

    fun coverage(slot: ProgramSlotId): Double = slotExposure[slot] ?: 0.0

    fun allowsRepeat(candidate: ProgramCandidate, absoluteDay: Int, minimumGapDays: Int): Boolean {
        if (minimumGapDays <= 0) return true
        val lastDay = stableKeyDays[candidate.exercise.stableKey].orEmpty().maxOrNull() ?: return true
        return absoluteDay - lastDay >= minimumGapDays
    }

    fun record(
        candidate: ProgramCandidate,
        weekIndex: Int,
        absoluteDay: Int,
        coveragePolicy: CoverageAccountingPolicy
    ) {
        stableKeyDays.getOrPut(candidate.exercise.stableKey) { mutableListOf() } += absoluteDay
        candidate.metadata?.redundancyGroup
            ?.takeUnless { it.isBlank() || it == "NOT_APPLICABLE" }
            ?.let { redundancyDays.getOrPut(it) { mutableListOf() } += absoluteDay }
        candidate.metadata?.movementFamily
            ?.takeUnless { it.isBlank() || it == "NOT_APPLICABLE" }
            ?.let { family ->
                val key = weekIndex to family
                familyWeeklyExposure[key] = (familyWeeklyExposure[key] ?: 0) + 1
            }
        coveragePolicy.creditedSlots(candidate.slotCapabilities).forEach { (slot, credit) ->
            slotExposure[slot] = coverage(slot) + credit.value
        }
    }
}
