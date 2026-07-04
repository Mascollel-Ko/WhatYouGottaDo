package com.training.trackplanner.data

internal class ProgramCompositionPolicy {
    fun strengthAnchorAdjustment(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        request: ProgramSkeletonRequest
    ): Double {
        if (request.goal != ProgramGoal.BADMINTON_SUPPORT) return 0.0
        if (role !in STRENGTH_ANCHOR_ROLES) return 0.0
        if (!candidate.isLoadedStrength && candidate.strengthFit < 0.8) return 0.0

        val strengthShare = (1.0 - request.badmintonTransferRatio).coerceIn(0.10, 0.80)
        val roleMultiplier = if (role == ProgramExerciseRole.ANCHOR) 1.0 else 0.65
        return when {
            strengthShare >= 0.55 -> 1.25 * roleMultiplier
            strengthShare >= 0.35 -> 0.85 * roleMultiplier
            else -> 0.45 * roleMultiplier
        }
    }

    fun warnings(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): List<String> {
        if (request.goal != ProgramGoal.BADMINTON_SUPPORT) return emptyList()
        val strengthAnchors = items.count(::isStrengthAnchor)
        val minimum = when {
            request.badmintonTransferRatio >= 0.80 -> request.durationWeeks
            request.availableDaysPerWeek >= 5 -> request.durationWeeks * 2
            else -> request.durationWeeks
        }
        return if (strengthAnchors < minimum) {
            listOf("PROGRAM_STRENGTH_ANCHOR_UNDERUSED")
        } else {
            emptyList()
        }
    }

    private fun isStrengthAnchor(item: ProgramSkeletonItem): Boolean =
        item.selectionRole in STRENGTH_ANCHOR_ROLE_NAMES &&
            item.badmintonTransferLevel != "DIRECT" &&
            (
                item.requestedTemplateSlot in STRENGTH_SLOT_NAMES ||
                    item.primarySlotCapabilities.any(STRENGTH_SLOT_NAMES::contains) ||
                    item.secondarySlotCapabilities.any(STRENGTH_SLOT_NAMES::contains)
                )

    private companion object {
        val STRENGTH_ANCHOR_ROLES = setOf(ProgramExerciseRole.ANCHOR, ProgramExerciseRole.SUPPORT)
        val STRENGTH_ANCHOR_ROLE_NAMES = STRENGTH_ANCHOR_ROLES.map(ProgramExerciseRole::name).toSet()
        val STRENGTH_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL.name,
            ProgramSlotId.UPPER_PULL_ANCHOR.name,
            ProgramSlotId.UPPER_PUSH_SUPPORT.name,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name
        )
    }
}
