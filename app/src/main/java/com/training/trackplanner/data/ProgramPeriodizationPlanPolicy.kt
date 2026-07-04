package com.training.trackplanner.data

internal class ProgramPeriodizationPlanPolicy {
    fun resolveType(request: ProgramSkeletonRequest): ProgramPeriodizationType =
        request.periodizationType.takeIf { it != ProgramPeriodizationType.AUTO }
            ?: if (request.goal == ProgramGoal.BADMINTON_SUPPORT && request.badmintonTransferRatio >= 0.5) {
                ProgramPeriodizationType.BADMINTON_WAVE
            } else {
                ProgramPeriodizationType.STEP_DELOAD
            }

    fun weekPlans(
        request: ProgramSkeletonRequest,
        type: ProgramPeriodizationType,
        base: List<ProgramWeekPlan>
    ): List<ProgramWeekPlan> = base.map { week ->
        when (roleFor(type, week.weekIndex, request.durationWeeks)) {
            ProgramWeekRole.FOUNDATION_INTRO -> week.copy(
                weekType = ProgramWeekType.ADAPT.name,
                volumeMultiplier = minOf(week.volumeMultiplier, 0.85),
                intensityMultiplier = minOf(week.intensityMultiplier, 0.88)
            )
            ProgramWeekRole.FOUNDATION_LOAD,
            ProgramWeekRole.LINEAR_FOUNDATION -> week.copy(
                weekType = ProgramWeekType.BUILD.name,
                volumeMultiplier = maxOf(week.volumeMultiplier, 0.95),
                intensityMultiplier = maxOf(week.intensityMultiplier, 0.92)
            )
            ProgramWeekRole.TRANSFER_ACCESSORY,
            ProgramWeekRole.UNDULATING -> week.copy(
                weekType = ProgramWeekType.HIGH.name,
                volumeMultiplier = maxOf(week.volumeMultiplier, 1.0),
                intensityMultiplier = maxOf(week.intensityMultiplier, 0.95)
            )
            ProgramWeekRole.CONSOLIDATION -> week.copy(
                weekType = ProgramWeekType.REBUILD.name,
                volumeMultiplier = week.volumeMultiplier.coerceIn(0.75, 0.90),
                intensityMultiplier = week.intensityMultiplier.coerceIn(0.82, 0.92)
            )
            ProgramWeekRole.LINEAR_INTENSIFY -> week.copy(
                weekType = ProgramWeekType.INTENSIFY.name,
                volumeMultiplier = week.volumeMultiplier.coerceIn(0.85, 1.0),
                intensityMultiplier = maxOf(week.intensityMultiplier, 1.0)
            )
            ProgramWeekRole.DELOAD -> week.copy(
                weekType = ProgramWeekType.DELOAD.name,
                volumeMultiplier = minOf(week.volumeMultiplier, 0.65),
                intensityMultiplier = minOf(week.intensityMultiplier, 0.75),
                deloadFlag = true,
                heavyExposureLimit = 1
            )
        }
    }

    fun plan(
        request: ProgramSkeletonRequest,
        type: ProgramPeriodizationType,
        weekPlans: List<ProgramWeekPlan>,
        schedule: List<PlannedSlot>
    ): List<ProgramPeriodizationWeekPlan> = weekPlans.map { week ->
        val role = roleFor(type, week.weekIndex, request.durationWeeks)
        ProgramPeriodizationWeekPlan(
            weekIndex = week.weekIndex,
            role = role,
            dayProfiles = schedule.associate { slot -> slot.dayOfWeek to dayProfile(role, slot) }
        )
    }

    fun scheduleForWeek(base: List<PlannedSlot>, periodizedWeek: ProgramPeriodizationWeekPlan): List<PlannedSlot> =
        base

    private fun roleFor(type: ProgramPeriodizationType, weekIndex: Int, totalWeeks: Int): ProgramWeekRole =
        when (type) {
            ProgramPeriodizationType.BADMINTON_WAVE -> when ((weekIndex - 1) % 4) {
                0 -> ProgramWeekRole.FOUNDATION_INTRO
                1 -> ProgramWeekRole.FOUNDATION_LOAD
                2 -> ProgramWeekRole.TRANSFER_ACCESSORY
                else -> ProgramWeekRole.CONSOLIDATION
            }
            ProgramPeriodizationType.LINEAR_STRENGTH -> if (weekIndex == totalWeeks) {
                ProgramWeekRole.LINEAR_INTENSIFY
            } else {
                ProgramWeekRole.LINEAR_FOUNDATION
            }
            ProgramPeriodizationType.DAILY_UNDULATING -> ProgramWeekRole.UNDULATING
            ProgramPeriodizationType.STEP_DELOAD -> if (weekIndex % 4 == 0 || weekIndex == totalWeeks) {
                ProgramWeekRole.DELOAD
            } else {
                ProgramWeekRole.FOUNDATION_LOAD
            }
            ProgramPeriodizationType.AUTO -> ProgramWeekRole.FOUNDATION_LOAD
        }

    private fun dayProfile(role: ProgramWeekRole, slot: PlannedSlot): ProgramDayProfile =
        when (role) {
            ProgramWeekRole.FOUNDATION_INTRO -> if (slot.intensity == ProgramDayIntensity.LIGHT) {
                ProgramDayProfile.LIGHT_RECOVERY
            } else {
                ProgramDayProfile.MEDIUM_SUPPORT
            }
            ProgramWeekRole.FOUNDATION_LOAD,
            ProgramWeekRole.LINEAR_FOUNDATION,
            ProgramWeekRole.LINEAR_INTENSIFY -> if (slot.slot in FOUNDATION_SLOTS) {
                ProgramDayProfile.HARD_FOUNDATION
            } else if (slot.intensity == ProgramDayIntensity.LIGHT) {
                ProgramDayProfile.LIGHT_RECOVERY
            } else {
                ProgramDayProfile.MEDIUM_SUPPORT
            }
            ProgramWeekRole.TRANSFER_ACCESSORY -> if (slot.intensity == ProgramDayIntensity.LIGHT) {
                ProgramDayProfile.LIGHT_RECOVERY
            } else {
                ProgramDayProfile.TRANSFER_EMPHASIS
            }
            ProgramWeekRole.CONSOLIDATION,
            ProgramWeekRole.DELOAD -> if (slot.slot in FOUNDATION_SLOTS) {
                ProgramDayProfile.MEDIUM_SUPPORT
            } else {
                ProgramDayProfile.LIGHT_RECOVERY
            }
            ProgramWeekRole.UNDULATING -> when (slot.intensity) {
                ProgramDayIntensity.HARD -> ProgramDayProfile.HARD_FOUNDATION
                ProgramDayIntensity.MODERATE -> ProgramDayProfile.MEDIUM_SUPPORT
                ProgramDayIntensity.LIGHT -> ProgramDayProfile.LIGHT_RECOVERY
            }
        }

    private companion object {
        val FOUNDATION_SLOTS = setOf(
            ProgramTrainingSlot.LOWER_STRENGTH,
            ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
            ProgramTrainingSlot.UPPER_STRENGTH,
            ProgramTrainingSlot.UPPER_STRENGTH_SCAP
        )
    }
}
