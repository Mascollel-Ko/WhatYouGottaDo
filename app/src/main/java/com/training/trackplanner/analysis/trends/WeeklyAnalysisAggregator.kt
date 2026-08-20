package com.training.trackplanner.analysis.trends

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class WeeklyAnalysisAggregator(
    private val window: WeeklyAnalysisWindow = WeeklyAnalysisWindow.DASHBOARD
) {
    fun aggregate(
        today: LocalDate,
        entriesWithSets: List<WorkoutEntryWithSets>,
        dailyMetrics: List<DailyMetric>
    ): List<WeeklyTrainingData> {
        val completedEntries = entriesWithSets.filter { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull()
            date != null && date <= today && record.sets.any { set -> set.confirmed }
        }
        val earliestCompletedDate = completedEntries
            .mapNotNull { record -> runCatching { LocalDate.parse(record.entry.date) }.getOrNull() }
            .minOrNull()
        val currentWeekStart = weekStart(today)
        val earliestDate = when (window) {
            WeeklyAnalysisWindow.DASHBOARD -> earliestCompletedDate
            WeeklyAnalysisWindow.FULL_HISTORY -> listOfNotNull(
                earliestCompletedDate,
                dailyMetrics.mapNotNull { metric -> runCatching { LocalDate.parse(metric.date) }.getOrNull() }.minOrNull()
            ).minOrNull()
        }
        val weekCount = when (window) {
            WeeklyAnalysisWindow.DASHBOARD -> if (earliestDate != null && earliestDate <= currentWeekStart.minusWeeks(8)) {
                PerformanceTrendConstants.EXTENDED_WEEK_COUNT
            } else {
                PerformanceTrendConstants.DEFAULT_WEEK_COUNT
            }
            WeeklyAnalysisWindow.FULL_HISTORY -> earliestDate
                ?.let { java.time.temporal.ChronoUnit.WEEKS.between(weekStart(it), currentWeekStart).toInt() + 1 }
                ?.coerceAtLeast(1)
                ?: 1
        }
        val startWeek = currentWeekStart.minusWeeks((weekCount - 1).toLong())
        val weeks = (0 until weekCount).map { offset ->
            val weekStart = startWeek.plusWeeks(offset.toLong())
            val weekEnd = weekStart.plusDays(6)
            WeeklyTrainingData(
                weekStart = weekStart,
                weekEnd = weekEnd,
                entries = completedEntries.filter { record ->
                    val date = LocalDate.parse(record.entry.date)
                    date in weekStart..weekEnd
                },
                dailyMetrics = dailyMetrics.filter { metric ->
                    val date = runCatching { LocalDate.parse(metric.date) }.getOrNull()
                    date != null && date in weekStart..weekEnd
                }
            )
        }
        return weeks
    }

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

enum class WeeklyAnalysisWindow {
    DASHBOARD,
    FULL_HISTORY
}
