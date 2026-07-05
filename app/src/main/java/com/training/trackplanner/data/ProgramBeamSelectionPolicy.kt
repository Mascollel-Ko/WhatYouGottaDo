package com.training.trackplanner.data

import kotlin.math.max

internal class ProgramBeamSelectionPolicy {
    fun candidateWindow(
        scored: List<Pair<ProgramCandidate, Double>>,
        desiredExerciseCount: Int
    ): List<Pair<ProgramCandidate, Double>> {
        if (scored.isEmpty()) return emptyList()
        val size = max(MIN_SLOT_CANDIDATES, desiredExerciseCount * 8).coerceAtMost(MAX_SLOT_CANDIDATES)
        return scored.take(size.coerceAtMost(scored.size))
    }

    fun choose(
        scored: List<Pair<ProgramCandidate, Double>>,
        context: ProgramCandidateScoreContext,
        classification: (ProgramCandidate) -> ProgramCandidateClassification,
        desiredExerciseCount: Int
    ): ProgramCandidate? {
        val window = candidateWindow(scored, desiredExerciseCount)
        if (window.isEmpty()) return null
        return window
            .take(BEAM_SIZE.coerceAtMost(window.size))
            .maxByOrNull { (candidate, score) ->
                score + beamAdjustment(candidate, classification(candidate), context)
            }
            ?.first
    }

    private fun beamAdjustment(
        candidate: ProgramCandidate,
        classification: ProgramCandidateClassification,
        context: ProgramCandidateScoreContext
    ): Double {
        var adjustment = 0.0
        val stableKey = candidate.exercise.stableKey
        if (stableKey in context.request.excludedExerciseStableKeys) {
            adjustment -= 10_000.0
        }
        if (stableKey in context.request.preferredExerciseStableKeys) {
            adjustment += 200.0
        }
        context.templateSlot.targetSlot?.let { target ->
            if (context.templateSlot.role == ProgramExerciseRole.ANCHOR) {
                if (candidate.slotCapabilities.hasAny(target)) adjustment += 30.0
                if (classification.tier == ProgramCandidateTier.FOUNDATION_MAIN_WORTHY) adjustment += 20.0
                if (classification.tier !in setOf(
                        ProgramCandidateTier.FOUNDATION_MAIN_WORTHY,
                        ProgramCandidateTier.LOADED_SUPPORT
                    )
                ) {
                    adjustment -= 20.0
                }
            }
        }
        val programRepeatCount = context.generatedItems.count { it.stableKey == stableKey }
        if (programRepeatCount > 0) {
            adjustment -= programRepeatCount * 12.0
        }
        if (classification.tier == ProgramCandidateTier.FOUNDATION_MAIN_WORTHY &&
            context.selectedInSession.none { selected ->
                selected.slotCapabilities.hasAny(ProgramSlotId.LOWER_SQUAT_PATTERN) ||
                    selected.slotCapabilities.hasAny(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN) ||
                    selected.slotCapabilities.hasAny(ProgramSlotId.UPPER_PULL_ANCHOR)
            }
        ) {
            adjustment += 1.0
        }
        if (classification.corePattern == ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION &&
            context.generatedItems.any { it.stableKey == candidate.exercise.stableKey }
        ) {
            adjustment -= 4.0
        }
        if (candidate.isLoadedStrength &&
            context.request.dailyAvailableMinutes >= 40 &&
            context.selectedInSession.none(ProgramCandidate::isLoadedStrength)
        ) {
            adjustment += 2.0
        }
        if (!candidate.matchesEquipment(context.request.availableEquipment)) {
            adjustment -= 2.0
        }
        if (context.selectedInSession.any { it.exercise.stableKey == candidate.exercise.stableKey }) {
            adjustment -= 100.0
        }
        return adjustment
    }

    private companion object {
        const val MIN_SLOT_CANDIDATES = 30
        const val MAX_SLOT_CANDIDATES = 30
        const val BEAM_SIZE = 30
    }
}
