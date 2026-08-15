package com.training.trackplanner.data

internal class ProgramCorePatternPolicy {
    fun patternForStableKey(stableKey: String): ProgramCorePattern = when {
        stableKey == DEAD_BUG_STABLE_KEY -> ProgramCorePattern.ANTI_EXTENSION
        stableKey in ANTI_ROTATION_STABLE_KEYS -> ProgramCorePattern.ANTI_ROTATION
        else -> ProgramCorePattern.NONE
    }

    fun adjustment(
        candidate: ProgramCandidate,
        classification: ProgramCandidateClassification,
        context: ProgramCandidateScoreContext
    ): Double {
        if (classification.corePattern == ProgramCorePattern.NONE &&
            classification.tier !in ACCESSORY_TIERS
        ) {
            return 0.0
        }
        val weekItems = context.generatedItems.filter { it.weekNumber == context.week.weekIndex }
        val sameWeekStableKey = weekItems.count { it.stableKey == candidate.exercise.stableKey }
        val programStableKey = context.generatedItems.count { it.stableKey == candidate.exercise.stableKey }
        val samePatternInWeek = weekItems.count { corePattern(it) == classification.corePattern }
        val trunkFlexionAlreadyPresent = weekItems.any {
            corePattern(it) == ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION
        }

        var score = 0.0
        if (classification.tier in ACCESSORY_TIERS && sameWeekStableKey >= 1) score -= 3.5
        if (classification.tier in ACCESSORY_TIERS && programStableKey >= 2) score -= 2.0
        if (classification.corePattern == ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION && samePatternInWeek >= 1) {
            score -= 4.5
        } else if (classification.corePattern != ProgramCorePattern.NONE && samePatternInWeek >= 2) {
            score -= 2.0
        }
        if (trunkFlexionAlreadyPresent && classification.corePattern in ROTATION_PATTERNS) score += 1.4
        return score
    }

    fun warnings(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): List<String> {
        val weeklyTrunkFlexionRepeat = items
            .groupBy { it.weekNumber }
            .any { (_, rows) ->
                rows.count { corePattern(it) == ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION } > 1
            }
        val programAccessoryOveruse = items
            .filter(::isCoreAccessoryOrFiller)
            .groupBy { it.stableKey }
            .any { (stableKey, rows) ->
                stableKey.isNotBlank() && rows.size > request.durationWeeks
            }
        return buildList {
            if (weeklyTrunkFlexionRepeat) add("PROGRAM_CORE_PATTERN_TRUNK_FLEXION_REPEAT")
            if (programAccessoryOveruse) add("PROGRAM_CORE_ACCESSORY_STABLEKEY_OVERUSE")
        }
    }

    fun corePattern(item: ProgramSkeletonItem): ProgramCorePattern = patternForStableKey(item.stableKey)

    private fun isCoreAccessoryOrFiller(item: ProgramSkeletonItem): Boolean =
        item.selectionRole in ACCESSORY_ROLE_NAMES ||
            corePattern(item) != ProgramCorePattern.NONE

    private companion object {
        const val DEAD_BUG_STABLE_KEY = "ex_d5bdffe1"
        val ANTI_ROTATION_STABLE_KEYS =
            ProgramCandidateAuthority.badmintonAccessoryStableKeysByCategory
                .getValue(ProgramBadmintonCategory.ANTI_ROTATION) - DEAD_BUG_STABLE_KEY
        val ACCESSORY_TIERS = setOf(
            ProgramCandidateTier.CORE_ACCESSORY_PREHAB,
            ProgramCandidateTier.FILLER
        )
        val ROTATION_PATTERNS = setOf(
            ProgramCorePattern.ANTI_EXTENSION,
            ProgramCorePattern.ANTI_ROTATION,
            ProgramCorePattern.LATERAL_STABILITY,
            ProgramCorePattern.CARRY
        )
        val ACCESSORY_ROLE_NAMES = setOf(
            ProgramExerciseRole.CORE.name,
            ProgramExerciseRole.PREHAB.name,
            ProgramExerciseRole.ACCESSORY.name
        )
    }
}
