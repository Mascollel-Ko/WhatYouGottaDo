package com.training.trackplanner.data

internal class ProgramCandidateRerankingPolicy(
    private val corePatternPolicy: ProgramCorePatternPolicy = ProgramCorePatternPolicy()
) {
    fun adjustment(
        candidate: ProgramCandidate,
        classification: ProgramCandidateClassification,
        context: ProgramCandidateScoreContext
    ): Double {
        var score = 0.0
        if (candidate.exercise.stableKey in context.request.preferredExerciseStableKeys) score += 3.0
        if (needsFoundation(context, classification)) score += 2.4
        if (needsLoadedStrength(context) && candidate.isLoadedStrength) score += 1.8
        if (context.request.dailyAvailableMinutes >= 40 &&
            context.selectedInSession.size < 2 &&
            classification.tier in MAIN_WORTHY_TIERS
        ) {
            score += 0.8
        }
        if (context.periodizedWeek.role == ProgramWeekRole.TRANSFER_ACCESSORY &&
            classification.tier == ProgramCandidateTier.BADMINTON_TRANSFER
        ) {
            score += 0.7
        }
        if (context.periodizedWeek.role in FOUNDATION_ROLES &&
            classification.tier == ProgramCandidateTier.FOUNDATION_MAIN_WORTHY
        ) {
            score += 1.0
        }
        score += corePatternPolicy.adjustment(candidate, classification, context)
        score -= repeatedTransferPenalty(classification, context)
        if (context.plannedSlot.intensity == ProgramDayIntensity.LIGHT && candidate.highStress) score -= 2.2
        return score
    }

    private fun needsFoundation(
        context: ProgramCandidateScoreContext,
        classification: ProgramCandidateClassification
    ): Boolean {
        if (classification.foundationPatterns.isEmpty()) return false
        val weekPatterns = context.generatedItems
            .filter { it.weekNumber == context.week.weekIndex }
            .flatMap(::foundationPatterns)
            .toSet()
        return classification.foundationPatterns.any { it !in weekPatterns }
    }

    private fun needsLoadedStrength(context: ProgramCandidateScoreContext): Boolean {
        val loadedAvailable = context.request.availableEquipment.any { it.uppercase() in LOADED_EQUIPMENT }
        if (!loadedAvailable) return false
        val weekLoaded = context.generatedItems.count { item ->
            item.weekNumber == context.week.weekIndex &&
                item.exerciseName.contains(LOADED_NAME_HINTS, ignoreCase = true)
        }
        return weekLoaded < 2
    }

    private fun repeatedTransferPenalty(
        classification: ProgramCandidateClassification,
        context: ProgramCandidateScoreContext
    ): Double {
        val transferGoal = classification.transferGoal
        if (transferGoal.isBlank() || transferGoal == "NONE") return 0.0
        val repeated = context.generatedItems.count { item ->
            item.weekNumber == context.week.weekIndex &&
                item.selectionRole == ProgramExerciseRole.TRANSFER.name &&
                item.redundancyGroup == transferGoal
        }
        return if (repeated >= 2) 1.4 else 0.0
    }

    private fun foundationPatterns(item: ProgramSkeletonItem): Set<ProgramFoundationPattern> = buildSet {
        if (item.hasSlot(ProgramSlotId.LOWER_SQUAT_PATTERN)) add(ProgramFoundationPattern.SQUAT)
        if (item.hasSlot(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN)) add(ProgramFoundationPattern.HINGE)
        if (item.hasSlot(ProgramSlotId.UPPER_PULL_ANCHOR)) add(ProgramFoundationPattern.UPPER_PULL)
        if (item.hasSlot(ProgramSlotId.UPPER_PUSH_SUPPORT) ||
            item.hasSlot(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT)
        ) {
            add(ProgramFoundationPattern.PRESS)
        }
    }

    private fun ProgramSkeletonItem.hasSlot(slot: ProgramSlotId): Boolean {
        val name = slot.name
        return requestedTemplateSlot == name ||
            primarySlotCapabilities.any { it == name } ||
            secondarySlotCapabilities.any { it == name }
    }

    private fun String.contains(tokens: Set<String>, ignoreCase: Boolean): Boolean =
        tokens.any { contains(it, ignoreCase = ignoreCase) }

    private companion object {
        val MAIN_WORTHY_TIERS = setOf(
            ProgramCandidateTier.FOUNDATION_MAIN_WORTHY,
            ProgramCandidateTier.LOADED_SUPPORT
        )
        val FOUNDATION_ROLES = setOf(
            ProgramWeekRole.FOUNDATION_INTRO,
            ProgramWeekRole.FOUNDATION_LOAD,
            ProgramWeekRole.LINEAR_FOUNDATION,
            ProgramWeekRole.LINEAR_INTENSIFY
        )
        val LOADED_EQUIPMENT = setOf("BARBELL", "DUMBBELL", "MACHINE", "CABLE")
        val LOADED_NAME_HINTS = setOf(
            "barbell", "dumbbell", "machine", "cable", "squat", "deadlift", "row", "press",
            "諛붾꺼", "?ㅻ꺼", "癒몄떊", "耳?대툝"
        )
    }
}
