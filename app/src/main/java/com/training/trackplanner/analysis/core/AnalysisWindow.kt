package com.training.trackplanner.analysis.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AnalysisWindow(
    val start: LocalDate,
    val endInclusive: LocalDate
) {
    init {
        require(!endInclusive.isBefore(start)) {
            "AnalysisWindow end must not be before start."
        }
    }

    val dayCount: Long
        get() = ChronoUnit.DAYS.between(start, endInclusive) + 1L

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)

    fun dates(): List<LocalDate> =
        (0L until dayCount).map { offset -> start.plusDays(offset) }
}

data class AnalysisWindows(
    val recent7Days: AnalysisWindow,
    val recent28Days: AnalysisWindow,
    val future7Days: AnalysisWindow,
    val future14Days: AnalysisWindow,
    val currentWeek: AnalysisWindow
) {
    companion object {
        fun from(today: LocalDate): AnalysisWindows {
            val weekStart = today.with(DayOfWeek.MONDAY)
            return AnalysisWindows(
                recent7Days = AnalysisWindow(today.minusDays(6), today),
                recent28Days = AnalysisWindow(today.minusDays(27), today),
                future7Days = AnalysisWindow(today.plusDays(1), today.plusDays(7)),
                future14Days = AnalysisWindow(today.plusDays(1), today.plusDays(14)),
                currentWeek = AnalysisWindow(weekStart, weekStart.plusDays(6))
            )
        }
    }
}
