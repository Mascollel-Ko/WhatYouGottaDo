package com.training.trackplanner.data

internal class ProgramFoundationAnchorPolicy {
    fun reserveSlots(
        slots: List<TemplateExerciseSlot>,
        request: ProgramSkeletonRequest,
        week: ProgramPeriodizationWeekPlan,
        plannedSlot: PlannedSlot
    ): List<TemplateExerciseSlot> {
        if (slots.isEmpty() || request.goal != ProgramGoal.BADMINTON_SUPPORT || request.dailyAvailableMinutes < 40) {
            return slots
        }
        if (slots.any { it.targetSlot != null }) return slots
        if (slots.any(TemplateExerciseSlot::required)) return slots
        val target = foundationTarget(week.role, plannedSlot.slot) ?: return slots
        if (slots.any { it.targetSlot == target && it.role == ProgramExerciseRole.ANCHOR }) return slots
        val reserved = TemplateExerciseSlot(
            targetSlot = target,
            role = if (target == ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL) {
                ProgramExerciseRole.SUPPORT
            } else {
                ProgramExerciseRole.ANCHOR
            },
            required = true,
            minimumRepeatGapDays = 7
        )
        return listOf(reserved) + slots.drop(1)
    }

    fun warnings(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): List<String> {
        if (request.goal != ProgramGoal.BADMINTON_SUPPORT) return emptyList()
        val weeklyPatterns = items.groupBy(ProgramSkeletonItem::weekNumber).mapValues { (_, rows) ->
            rows.flatMap { item ->
                buildList {
                    if (item.hasSlot(ProgramSlotId.LOWER_SQUAT_PATTERN)) add(ProgramFoundationPattern.SQUAT)
                    if (item.hasSlot(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN)) add(ProgramFoundationPattern.HINGE)
                    if (item.hasSlot(ProgramSlotId.UPPER_PULL_ANCHOR)) add(ProgramFoundationPattern.UPPER_PULL)
                    if (item.hasSlot(ProgramSlotId.UPPER_PUSH_SUPPORT) ||
                        item.hasSlot(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT)
                    ) {
                        add(ProgramFoundationPattern.PRESS)
                    }
                }
            }.toSet()
        }
        return buildList {
            if (weeklyPatterns.any { (_, patterns) -> ProgramFoundationPattern.SQUAT !in patterns }) {
                add("PROGRAM_FOUNDATION_SQUAT_UNDERUSED")
            }
            if (weeklyPatterns.any { (_, patterns) -> ProgramFoundationPattern.HINGE !in patterns }) {
                add("PROGRAM_FOUNDATION_HINGE_UNDERUSED")
            }
            if (weeklyPatterns.any { (_, patterns) -> ProgramFoundationPattern.UPPER_PULL !in patterns }) {
                add("PROGRAM_FOUNDATION_UPPER_PULL_UNDERUSED")
            }
        }
    }

    private fun foundationTarget(role: ProgramWeekRole, slot: ProgramTrainingSlot): ProgramSlotId? =
        when (slot) {
            ProgramTrainingSlot.LOWER_STRENGTH,
            ProgramTrainingSlot.LOWER_STRENGTH_HEAVY -> ProgramSlotId.LOWER_SQUAT_PATTERN
            ProgramTrainingSlot.UPPER_STRENGTH,
            ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
            ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> ProgramSlotId.UPPER_PULL_ANCHOR
            ProgramTrainingSlot.BADMINTON_TRANSFER,
            ProgramTrainingSlot.BADMINTON_COD,
            ProgramTrainingSlot.BADMINTON_COD_DECEL,
            ProgramTrainingSlot.POWER_REACTIVE,
            ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> when (role) {
                ProgramWeekRole.TRANSFER_ACCESSORY -> ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL
                else -> ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN
            }
            else -> null
        }

    private fun ProgramSkeletonItem.hasSlot(slot: ProgramSlotId): Boolean {
        val name = slot.name
        return requestedTemplateSlot == name ||
            primarySlotCapabilities.any { it == name } ||
            secondarySlotCapabilities.any { it == name }
    }
}
