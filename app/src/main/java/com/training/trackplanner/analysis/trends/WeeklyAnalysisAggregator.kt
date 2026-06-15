package com.training.trackplanner.analysis.trends

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class WeeklyAnalysisAggregator {
    fun aggregate(
        today: LocalDate,
        entriesWithSets: List<WorkoutEntryWithSets>,
        dailyMetrics: List<DailyMetric>
    ): List<WeeklyTrainingData> {
        val completedEntries = entriesWithSets.filter { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull()
            date != null && date <= today && record.sets.any { set -> set.confirmed }
        }
        val earliestDate = completedEntries
            .mapNotNull { record -> runCatching { LocalDate.parse(record.entry.date) }.getOrNull() }
            .minOrNull()
        val currentWeekStart = weekStart(today)
        val weekCount = if (earliestDate != null && earliestDate <= currentWeekStart.minusWeeks(8)) {
            PerformanceTrendConstants.EXTENDED_WEEK_COUNT
        } else {
            PerformanceTrendConstants.DEFAULT_WEEK_COUNT
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
