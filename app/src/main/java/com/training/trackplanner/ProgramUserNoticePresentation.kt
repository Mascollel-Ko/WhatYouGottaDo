package com.training.trackplanner

import android.content.Context
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode

internal fun Context.programUserNoticeText(notice: ProgramUserNotice): String =
    when (notice.code) {
        ProgramUserNoticeCode.RULE_TABLE_STRUCTURE_CREATED ->
            getString(R.string.program_notice_rule_table_structure_created)
        ProgramUserNoticeCode.MAIN_EXERCISE_PRIORITY_RESTORED ->
            getString(R.string.program_notice_main_exercise_priority_restored)
        ProgramUserNoticeCode.FOUNDATION_BALANCE_RESTORED ->
            getString(R.string.program_notice_foundation_balance_restored)
        ProgramUserNoticeCode.REPEATED_CORE_PATTERN_REPLACED ->
            getString(R.string.program_notice_repeated_core_pattern_replaced)
        ProgramUserNoticeCode.ADJACENT_LOWER_FATIGUE_REDUCED ->
            getString(R.string.program_notice_adjacent_lower_fatigue_reduced)
        ProgramUserNoticeCode.EXCLUDED_EXERCISES_APPLIED ->
            resources.getQuantityString(
                R.plurals.program_notice_excluded_exercises_applied,
                notice.count,
                notice.count
            )
        ProgramUserNoticeCode.PREFERRED_EXERCISES_INCLUDED ->
            getString(
                R.string.program_notice_preferred_exercises_included,
                notice.totalCount,
                notice.selectedCount
            )
        ProgramUserNoticeCode.AUTOMATIC_QUALITY_ADJUSTMENT ->
            getString(R.string.program_notice_automatic_quality_adjustment)
    }
