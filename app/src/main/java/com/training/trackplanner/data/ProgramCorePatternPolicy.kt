package com.training.trackplanner.data

internal class ProgramCorePatternPolicy {
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

    fun corePattern(item: ProgramSkeletonItem): ProgramCorePattern {
        val text = listOf(item.exerciseName, item.stableKey, item.movementFamily, item.movementSubtype, item.redundancyGroup)
            .joinToString("|")
            .uppercase()
        return when {
            listOf("CAPTAIN", "LEG_RAISE", "HIP_FLEXOR", "CORE_FLEXION").any(text::contains) ->
                ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION
            "ANTI_ROTATION" in text || "PALLOF" in text -> ProgramCorePattern.ANTI_ROTATION
            "ANTI_EXTENSION" in text || "DEAD_BUG" in text -> ProgramCorePattern.ANTI_EXTENSION
            "SIDE_PLANK" in text || "LATERAL" in text -> ProgramCorePattern.LATERAL_STABILITY
            "CARRY" in text || "FARMER" in text || "SUITCASE" in text -> ProgramCorePattern.CARRY
            else -> ProgramCorePattern.NONE
        }
    }

    private fun isCoreAccessoryOrFiller(item: ProgramSkeletonItem): Boolean =
        item.selectionRole in ACCESSORY_ROLE_NAMES ||
            corePattern(item) != ProgramCorePattern.NONE

    private companion object {
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
