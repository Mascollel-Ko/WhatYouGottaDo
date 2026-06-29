package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.SmashSpeedRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object SmashSpeedMetricSeriesBuilder {
    fun build(records: List<SmashSpeedRecord>): Map<TrendMetricId, List<TrendDataPoint>> {
        val weekly = records.mapNotNull { record ->
            runCatching { LocalDate.parse(record.date) }.getOrNull()?.let { date -> date to record.speedKmh }
        }.groupBy { (date, _) -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }

        fun series(value: (List<Double>) -> Double): List<TrendDataPoint> =
            weekly.toSortedMap().map { (week, rows) ->
                TrendDataPoint(week, value(rows.map { (_, speed) -> speed }))
            }

        return mapOf(
            TrendMetricId.SMASH_SPEED_TOP3_AVG to series { speeds -> speeds.sortedDescending().take(3).average() },
            TrendMetricId.SMASH_SPEED_BEST to series { speeds -> speeds.maxOrNull() ?: 0.0 },
            TrendMetricId.SMASH_SPEED_AVG to series { speeds -> speeds.average() },
            TrendMetricId.SMASH_ATTEMPT_COUNT to series { speeds -> speeds.size.toDouble() }
        )
    }
}
