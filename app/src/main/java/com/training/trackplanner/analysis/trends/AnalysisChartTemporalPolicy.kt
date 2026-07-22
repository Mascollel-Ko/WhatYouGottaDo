package com.training.trackplanner.analysis.trends

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class AnalysisWeekLabel(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val owningYear: Int,
    val owningMonth: Int,
    val monthWeekOrdinal: Int,
    val compactLabel: String,
    val detailedLabel: String
)

object AnalysisChartTemporalPolicy {
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekLabel(date: LocalDate, includeYear: Boolean = false): AnalysisWeekLabel {
        val monday = weekStart(date)
        val sunday = monday.plusDays(6)
        val owner = YearMonth.from(monday.plusDays(3))
        val firstOwnedMonday = owner.atDay(1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
            .minusDays(3)
        val ordinal = ChronoUnit.WEEKS.between(firstOwnedMonday, monday).toInt() + 1
        val compact = buildString {
            if (includeYear) append("${owner.year}년 ")
            append("${owner.monthValue}월 ${ordinal}주")
        }
        return AnalysisWeekLabel(
            weekStart = monday,
            weekEnd = sunday,
            owningYear = owner.year,
            owningMonth = owner.monthValue,
            monthWeekOrdinal = ordinal,
            compactLabel = compact,
            detailedLabel = "$compact · ${dateRange(monday, sunday)}"
        )
    }

    fun weeklyDomain(dates: Iterable<LocalDate>): List<LocalDate> {
        val weeks = dates.map(::weekStart)
        val first = weeks.minOrNull() ?: return emptyList()
        val last = weeks.maxOrNull() ?: return emptyList()
        return generateSequence(first) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(last) }
            .toList()
    }

    fun dailyDomain(dates: Iterable<LocalDate>): List<LocalDate> =
        dates.distinct().sorted()

    fun domain(spec: ChartSpec): List<LocalDate> {
        val dates = spec.xDomain +
            spec.lineSeries.flatMap { series -> series.points.map(TrendDataPoint::weekStart) } +
            spec.forecastRange?.points?.map(ForecastPoint::weekStart).orEmpty() +
            spec.intervalBand?.points?.map(IntervalPoint::date).orEmpty() +
            spec.stackedBars.mapNotNull(StackedBarGroup::weekStart)
        return when (spec.timeGranularity) {
            ChartTimeGranularity.WEEKLY -> weeklyDomain(dates)
            ChartTimeGranularity.DAILY, null -> dailyDomain(dates)
        }
    }

    fun axisLabelDates(
        domain: List<LocalDate>,
        granularity: ChartTimeGranularity,
        maxLabels: Int = 8
    ): Set<LocalDate> {
        val dates = domain.distinct().sorted()
        if (dates.size <= maxLabels) return dates.toSet()
        val selected = linkedSetOf(dates.first(), dates.last())
        dates.zipWithNext().forEach { (previous, current) ->
            val monthChanged = when (granularity) {
                ChartTimeGranularity.DAILY -> YearMonth.from(previous) != YearMonth.from(current)
                ChartTimeGranularity.WEEKLY ->
                    owningMonth(previous) != owningMonth(current)
            }
            if (monthChanged) selected += current
        }
        val targetCount = maxOf(maxLabels, selected.size)
        if (targetCount > 1) {
            repeat(targetCount) { index ->
                val position = index * (dates.lastIndex.toDouble() / (targetCount - 1))
                selected += dates[position.toInt().coerceIn(dates.indices)]
            }
        }
        return selected
    }

    fun compactAxisLabel(
        date: LocalDate,
        granularity: ChartTimeGranularity,
        domain: List<LocalDate>,
        includeWeekday: Boolean = false
    ): String = when (granularity) {
        ChartTimeGranularity.DAILY -> buildString {
            append("${date.monthValue}/${date.dayOfMonth}")
            if (includeWeekday) append("\n${weekday(date.dayOfWeek)}")
        }
        ChartTimeGranularity.WEEKLY -> {
            val label = weekLabel(date, includeYear = spansOwningYears(domain)).compactLabel
            label.replace("월 ", "월\n")
        }
    }

    fun detailLabel(
        date: LocalDate,
        granularity: ChartTimeGranularity,
        domain: List<LocalDate>
    ): String = when (granularity) {
        ChartTimeGranularity.DAILY -> fullDate(date, includeYear = spansCalendarYears(domain))
        ChartTimeGranularity.WEEKLY ->
            weekLabel(date, includeYear = spansOwningYears(domain)).detailedLabel
    }

    fun periodLabel(domain: List<LocalDate>, granularity: ChartTimeGranularity): String? {
        val dates = domain.distinct().sorted()
        if (dates.isEmpty()) return null
        val start = dates.first()
        val end = when (granularity) {
            ChartTimeGranularity.DAILY -> dates.last()
            ChartTimeGranularity.WEEKLY -> weekStart(dates.last()).plusDays(6)
        }
        return dateRange(start, end)
    }

    fun dateRange(start: LocalDate, end: LocalDate): String {
        val includeYear = start.year != end.year
        val startLabel = if (includeYear) {
            "${start.year}년 ${start.monthValue}월 ${start.dayOfMonth}일"
        } else {
            "${start.monthValue}월 ${start.dayOfMonth}일"
        }
        val endLabel = if (includeYear) {
            "${end.year}년 ${end.monthValue}월 ${end.dayOfMonth}일"
        } else {
            "${end.monthValue}월 ${end.dayOfMonth}일"
        }
        return "$startLabel~$endLabel"
    }

    private fun fullDate(date: LocalDate, includeYear: Boolean): String =
        buildString {
            if (includeYear) append("${date.year}년 ")
            append("${date.monthValue}월 ${date.dayOfMonth}일 ${weekday(date.dayOfWeek)}요일")
        }

    private fun owningMonth(date: LocalDate): YearMonth =
        YearMonth.from(weekStart(date).plusDays(3))

    private fun spansOwningYears(domain: List<LocalDate>): Boolean =
        domain.map { owningMonth(it).year }.distinct().size > 1

    private fun spansCalendarYears(domain: List<LocalDate>): Boolean =
        domain.map(LocalDate::getYear).distinct().size > 1

    private fun weekday(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        DayOfWeek.SUNDAY -> "일"
    }
}
