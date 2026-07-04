package com.training.trackplanner.data

import java.util.Locale

internal enum class ProgramCandidateTier {
    FOUNDATION_MAIN_WORTHY,
    LOADED_SUPPORT,
    BADMINTON_TRANSFER,
    CORE_ACCESSORY_PREHAB,
    FILLER
}

internal enum class ProgramFoundationPattern {
    SQUAT,
    HINGE,
    UPPER_PULL,
    PRESS
}

internal enum class ProgramCorePattern {
    NONE,
    TRUNK_FLEXION_HIP_FLEXION,
    ANTI_EXTENSION,
    ANTI_ROTATION,
    LATERAL_STABILITY,
    CARRY,
    OTHER_CORE
}

internal data class ProgramCandidateClassification(
    val tier: ProgramCandidateTier,
    val foundationPatterns: Set<ProgramFoundationPattern> = emptySet(),
    val corePattern: ProgramCorePattern = ProgramCorePattern.NONE,
    val movementFamily: String = "",
    val equipmentFamily: String = "",
    val transferGoal: String = ""
)

internal class ProgramCandidateClassificationPolicy {
    fun classify(candidate: ProgramCandidate): ProgramCandidateClassification {
        val foundationPatterns = foundationPatterns(candidate)
        val corePattern = corePattern(candidate)
        val tier = when {
            foundationPatterns.isNotEmpty() && !candidate.isCore && !candidate.isRecovery ->
                ProgramCandidateTier.FOUNDATION_MAIN_WORTHY
            candidate.isLoadedStrength -> ProgramCandidateTier.LOADED_SUPPORT
            candidate.badmintonFit >= 0.75 -> ProgramCandidateTier.BADMINTON_TRANSFER
            corePattern != ProgramCorePattern.NONE || candidate.isCore || candidate.isRecovery || candidate.isIsolation ->
                ProgramCandidateTier.CORE_ACCESSORY_PREHAB
            else -> ProgramCandidateTier.FILLER
        }
        return ProgramCandidateClassification(
            tier = tier,
            foundationPatterns = foundationPatterns,
            corePattern = corePattern,
            movementFamily = candidate.metadata?.movementFamily.orEmpty(),
            equipmentFamily = candidate.exercise.equipment,
            transferGoal = candidate.metadata?.badmintonTransferType?.values?.firstOrNull().orEmpty()
        )
    }

    private fun foundationPatterns(candidate: ProgramCandidate): Set<ProgramFoundationPattern> = buildSet {
        val slots = candidate.slotCapabilities.primary + candidate.slotCapabilities.secondary + candidate.slotCapabilities.weakMatches
        if (ProgramSlotId.LOWER_SQUAT_PATTERN in slots || candidate.hasText("SQUAT", "LEG_PRESS", "KNEE_DOMINANT")) {
            add(ProgramFoundationPattern.SQUAT)
        }
        if (ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN in slots || candidate.hasText("DEADLIFT", "HINGE", "RDL")) {
            add(ProgramFoundationPattern.HINGE)
        }
        if (ProgramSlotId.UPPER_PULL_ANCHOR in slots || candidate.hasText("PULL", "ROW", "LAT_PULL")) {
            add(ProgramFoundationPattern.UPPER_PULL)
        }
        if (ProgramSlotId.UPPER_PUSH_SUPPORT in slots ||
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT in slots ||
            candidate.hasText("PRESS", "PUSH")
        ) {
            add(ProgramFoundationPattern.PRESS)
        }
    }

    private fun corePattern(candidate: ProgramCandidate): ProgramCorePattern {
        if (candidate.hasText("CAPTAIN", "LEG_RAISE", "HIP_FLEXOR", "CORE_FLEXION", "HANGING_LEG")) {
            return ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION
        }
        if (candidate.hasText("DEAD_BUG", "ANTI_EXTENSION")) return ProgramCorePattern.ANTI_EXTENSION
        if (candidate.hasText("PALLOF", "ANTI_ROTATION", "ROTATION_CONTROL")) return ProgramCorePattern.ANTI_ROTATION
        if (candidate.hasText("SIDE_PLANK", "LATERAL")) return ProgramCorePattern.LATERAL_STABILITY
        if (candidate.hasText("CARRY", "FARMER", "SUITCASE")) return ProgramCorePattern.CARRY
        return if (candidate.isCore) ProgramCorePattern.OTHER_CORE else ProgramCorePattern.NONE
    }

    private fun ProgramCandidate.hasText(vararg needles: String): Boolean {
        val text = listOf(
            exercise.name,
            exercise.stableKey,
            exercise.movementPattern,
            exercise.movementCategory,
            exercise.trainingRole,
            metadata?.movementFamily.orEmpty(),
            metadata?.movementSubtype.orEmpty(),
            metadata?.programSlot.orEmpty(),
            metadata?.redundancyGroup.orEmpty()
        ).joinToString("|").uppercase(Locale.US)
        return needles.any { text.contains(it) }
    }
}
