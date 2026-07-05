package com.training.trackplanner.data

internal class ProgramFoundationAnchorPolicy {
    fun reserveSlots(
        slots: List<TemplateExerciseSlot>,
        request: ProgramSkeletonRequest,
        week: ProgramPeriodizationWeekPlan,
        plannedSlot: PlannedSlot,
        availableSelectedMainStableKeys: Set<String> = emptySet(),
        generatedItems: List<ProgramSkeletonItem> = emptyList()
    ): List<TemplateExerciseSlot> {
        if (slots.isEmpty() || request.goal != ProgramGoal.BADMINTON_SUPPORT || request.dailyAvailableMinutes < 40) {
            return slots
        }
        selectedMainReservation(
            request = request,
            week = week,
            plannedSlot = plannedSlot,
            availableSelectedMainStableKeys = availableSelectedMainStableKeys,
            generatedItems = generatedItems
        )?.let { reserved ->
            return listOf(reserved) + slots.drop(1)
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

    private fun selectedMainReservation(
        request: ProgramSkeletonRequest,
        week: ProgramPeriodizationWeekPlan,
        plannedSlot: PlannedSlot,
        availableSelectedMainStableKeys: Set<String>,
        generatedItems: List<ProgramSkeletonItem>
    ): TemplateExerciseSlot? {
        if (!plannedSlot.slot.supportsSelectedMainReservation()) return null
        val available = SELECTED_MAIN_ORDER.filter(availableSelectedMainStableKeys::contains)
        if (available.isEmpty()) return null
        val currentWeekItems = generatedItems.filter { it.weekNumber == week.weekIndex }
        val weekSelected = currentWeekItems.map(ProgramSkeletonItem::stableKey).filter(available::contains)
        val programSelected = generatedItems.map(ProgramSkeletonItem::stableKey).filter(available::contains).toSet()
        val key = when {
            weekSelected.size < WEEKLY_SELECTED_MAIN_TARGET ->
                preferredForSlot(plannedSlot.slot).firstOrNull { it in available && it !in weekSelected } ?:
                    available.firstOrNull { it !in weekSelected }
            programSelected.size < minOf(PROGRAM_SELECTED_MAIN_DISTINCT_TARGET, available.size) ->
                available.firstOrNull { it !in programSelected }
            else -> null
        } ?: return null
        return TemplateExerciseSlot(
            targetSlot = targetSlotForSelectedMain(key),
            role = roleForSelectedMain(key),
            required = true,
            minimumRepeatGapDays = 7,
            selectedMainStableKey = key
        )
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

    private fun ProgramTrainingSlot.supportsSelectedMainReservation(): Boolean =
        this in setOf(
            ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT,
            ProgramTrainingSlot.LOWER_TRANSFER_FULL,
            ProgramTrainingSlot.UPPER_SCAP_CORE_FULL,
            ProgramTrainingSlot.LOWER_STRENGTH,
            ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
            ProgramTrainingSlot.BADMINTON_TRANSFER,
            ProgramTrainingSlot.BADMINTON_COD_DECEL,
            ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
            ProgramTrainingSlot.POWER_REACTIVE_LIGHT,
            ProgramTrainingSlot.UPPER_STRENGTH,
            ProgramTrainingSlot.BADMINTON_COD,
            ProgramTrainingSlot.POWER_REACTIVE
        )

    private fun preferredForSlot(slot: ProgramTrainingSlot): List<String> = when (slot) {
        ProgramTrainingSlot.LOWER_STRENGTH,
        ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
        ProgramTrainingSlot.LOWER_TRANSFER_FULL,
        ProgramTrainingSlot.BADMINTON_COD,
        ProgramTrainingSlot.BADMINTON_COD_DECEL,
        ProgramTrainingSlot.BADMINTON_TRANSFER,
        ProgramTrainingSlot.POWER_REACTIVE,
        ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> listOf("barbell_back_squat", "barbell_deadlift")
        ProgramTrainingSlot.UPPER_STRENGTH,
        ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
        ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> listOf("pull_up", "ex_32219f7a", "ex_8e1b313e")
        else -> SELECTED_MAIN_ORDER
    }

    private fun targetSlotForSelectedMain(stableKey: String): ProgramSlotId = when (stableKey) {
        "barbell_back_squat" -> ProgramSlotId.LOWER_SQUAT_PATTERN
        "barbell_deadlift" -> ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN
        "pull_up" -> ProgramSlotId.UPPER_PULL_ANCHOR
        "ex_32219f7a",
        "ex_8e1b313e" -> ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT
        else -> ProgramSlotId.LOWER_SQUAT_PATTERN
    }

    private fun roleForSelectedMain(stableKey: String): ProgramExerciseRole = when (stableKey) {
        "ex_32219f7a",
        "ex_8e1b313e" -> ProgramExerciseRole.SUPPORT
        else -> ProgramExerciseRole.ANCHOR
    }

    private companion object {
        const val WEEKLY_SELECTED_MAIN_TARGET = 2
        const val PROGRAM_SELECTED_MAIN_DISTINCT_TARGET = 3
        val SELECTED_MAIN_ORDER = listOf(
            "barbell_back_squat",
            "barbell_deadlift",
            "pull_up",
            "ex_32219f7a",
            "ex_8e1b313e"
        )
    }
}
