package com.training.trackplanner.data

internal enum class ProgramWeekRole {
    FOUNDATION_INTRO,
    FOUNDATION_LOAD,
    TRANSFER_ACCESSORY,
    CONSOLIDATION,
    LINEAR_FOUNDATION,
    LINEAR_INTENSIFY,
    DELOAD,
    UNDULATING
}

internal enum class ProgramDayProfile {
    HARD_FOUNDATION,
    MEDIUM_SUPPORT,
    TRANSFER_EMPHASIS,
    LIGHT_RECOVERY
}

internal data class ProgramPeriodizationWeekPlan(
    val weekIndex: Int,
    val role: ProgramWeekRole,
    val dayProfiles: Map<Int, ProgramDayProfile>
)
