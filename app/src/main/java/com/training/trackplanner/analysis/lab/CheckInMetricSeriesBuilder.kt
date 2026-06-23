package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyMetric
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object CheckInMetricSeriesBuilder {
    fun build(
        checkIns: List<DailyCheckIn>,
        dailyMetrics: List<DailyMetric> = emptyList()
    ): Map<TrendMetricId, List<TrendDataPoint>> {
        val metricSleepByDate = dailyMetrics
            .filter { metric -> metric.sleepHours != null }
            .associate { metric -> metric.date to metric.sleepHours }
        val normalizedCheckIns = checkIns.map { checkIn ->
            checkIn.copy(sleepHours = metricSleepByDate[checkIn.date] ?: checkIn.sleepHours)
        }
        val weekly = normalizedCheckIns.mapNotNull { checkIn ->
            runCatching { LocalDate.parse(checkIn.date) }.getOrNull()?.let { date -> date to checkIn }
        }.groupBy { (date, _) -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val weeklySleepMetrics = dailyMetrics.mapNotNull { metric ->
            val sleepHours = metric.sleepHours ?: return@mapNotNull null
            runCatching { LocalDate.parse(metric.date) }.getOrNull()?.let { date -> date to sleepHours }
        }.groupBy { (date, _) -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }

        fun series(value: (DailyCheckIn) -> Double?): List<TrendDataPoint> = weekly.toSortedMap().map { (week, rows) ->
            val values = rows.mapNotNull { (_, checkIn) -> value(checkIn) }
            TrendDataPoint(week, values.takeIf { it.isNotEmpty() }?.average())
        }

        fun sleepSeries(): List<TrendDataPoint> = weeklySleepMetrics.toSortedMap().map { (week, rows) ->
            TrendDataPoint(week, rows.map { (_, sleepHours) -> sleepHours }.average())
        }.ifEmpty {
            series { it.sleepHours }
        }

        return mapOf(
            TrendMetricId.SLEEP_HOURS to sleepSeries(),
            TrendMetricId.OVERALL_FATIGUE_CHECKIN to series { it.overallFatigue?.toDouble() },
            TrendMetricId.LOWER_BODY_FATIGUE_CHECKIN to series { it.lowerBodyFatigue?.toDouble() },
            TrendMetricId.JOINT_TENDON_DISCOMFORT_CHECKIN to series { it.jointTendonDiscomfort?.toDouble() },
            TrendMetricId.FOCUS_MOTIVATION_CHECKIN to series { it.focusMotivation?.toDouble() },
            TrendMetricId.RECOVERY_CHECKIN_COMPOSITE to series(::recoveryComposite)
        )
    }

    private fun recoveryComposite(checkIn: DailyCheckIn): Double? {
        val goodDirectionValues = buildList {
            checkIn.sleepHours?.let { hours -> add(sleepScore(hours)) }
            checkIn.overallFatigue?.let { add(6.0 - it) }
            checkIn.lowerBodyFatigue?.let { add(6.0 - it) }
            checkIn.jointTendonDiscomfort?.let { add(6.0 - it) }
            checkIn.focusMotivation?.let { add(it.toDouble()) }
        }
        return goodDirectionValues.takeIf { it.isNotEmpty() }?.average()
    }

    private fun sleepScore(hours: Double): Double = when {
        hours < 5.0 -> 1.0
        hours < 6.0 -> 2.0
        hours < 7.0 -> 3.0
        hours < 8.0 -> 4.0
        else -> 5.0
    }
}
