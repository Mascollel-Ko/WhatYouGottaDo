package com.training.trackplanner.analysis.trends

import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

object BadmintonTrainingMethodSeries {
    fun totals(points: List<BadmintonDailyLoadPoint>): Map<String, Double> {
        val totals = linkedMapOf<String, Double>()
        points.forEach { point ->
            point.methodRaw.forEach { (key, value) ->
                if (value > 0.0) totals[key] = (totals[key] ?: 0.0) + value
            }
        }
        return totals.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    fun weeklyStackedGroups(points: List<BadmintonDailyLoadPoint>): List<StackedBarGroup> =
        points
            .groupBy { point -> point.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap()
            .mapNotNull { (week, rows) ->
                val byLabel = linkedMapOf<String, Double>()
                rows.forEach { point ->
                    // ponytail: methodRaw intentionally duplicates multi-label stimulus per transfer objective.
                    point.methodRaw.forEach { (key, value) ->
                        val label = BadmintonTrainingMethodLabels.label(key)
                        if (value > 0.0) byLabel[label] = (byLabel[label] ?: 0.0) + value
                    }
                }
                val segments = byLabel.entries
                    .filter { it.value > 0.0 }
                    .map { (label, value) -> StackedBarSegment(label, value) }
                if (segments.isEmpty()) null else StackedBarGroup(week.toString(), segments)
            }
}
