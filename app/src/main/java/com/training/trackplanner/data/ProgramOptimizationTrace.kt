package com.training.trackplanner.data

data class ProgramOptimizationSummary(
    val notices: List<ProgramUserNotice> = emptyList()
)

enum class ProgramUserNoticeCode {
    RULE_TABLE_STRUCTURE_CREATED,
    MAIN_EXERCISE_PRIORITY_RESTORED,
    FOUNDATION_BALANCE_RESTORED,
    REPEATED_CORE_PATTERN_REPLACED,
    ADJACENT_LOWER_FATIGUE_REDUCED,
    EXCLUDED_EXERCISES_APPLIED,
    PREFERRED_EXERCISES_INCLUDED,
    AUTOMATIC_QUALITY_ADJUSTMENT
}

enum class ProgramUserNoticeLevel {
    SUCCESS,
    INFO,
    WARNING
}

data class ProgramUserNotice(
    val code: ProgramUserNoticeCode,
    val count: Int = 0,
    val selectedCount: Int = 0,
    val totalCount: Int = 0,
    val level: ProgramUserNoticeLevel = ProgramUserNoticeLevel.INFO
)
