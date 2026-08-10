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
        val compact = if (includeYear) {
            "${owner.year}년 ${owner.monthValue}월 ${ordinal}주"
        } else {
            "${owner.monthValue}월 ${ordinal}주"
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
            spec.intervalBands.flatMap { band -> band.points.map(IntervalPoint::date) } +
            spec.stackedBars.mapNotNull(StackedBarGroup::weekStart)
        return when (spec.timeGranularity) {
            ChartTimeGranularity.WEEKLY -> weeklyDomain(dates)
            ChartTimeGranularity.DAILY, null -> dailyDomain(dates)
        }
    }

    fun visibleAxisLabelIndices(
        domain: List<LocalDate>,
        granularity: ChartTimeGranularity,
        labelWidths: List<Int>,
        availableWidth: Int,
        minimumGap: Int = 0
    ): List<Int> {
        val dates = domain.distinct().sorted()
        if (dates.isEmpty() || availableWidth <= 0 || labelWidths.size != dates.size) return emptyList()
        val monthBoundaries = dates.indices.drop(1).filter { index ->
            when (granularity) {
                ChartTimeGranularity.DAILY -> YearMonth.from(dates[index - 1]) != YearMonth.from(dates[index])
                ChartTimeGranularity.WEEKLY -> owningMonth(dates[index - 1]) != owningMonth(dates[index])
            }
        }

        for (stride in 1..dates.size) {
            val selected = buildList {
                add(0)
                var index = stride
                while (index < dates.lastIndex) {
                    add(index)
                    index += stride
                }
                if (dates.lastIndex > 0) add(dates.lastIndex)
            }.distinct().sorted()
            if (!labelsFit(selected, labelWidths, availableWidth, minimumGap)) continue

            val preferred = selected.toMutableList()
            monthBoundaries.forEach { boundary ->
                val expanded = (preferred + boundary).distinct().sorted()
                if (labelsFit(expanded, labelWidths, availableWidth, minimumGap)) {
                    preferred += boundary
                }
            }
            return preferred.distinct().sorted()
        }
        return listOf(0)
    }

    fun compactAxisLabel(
        date: LocalDate,
        granularity: ChartTimeGranularity,
        domain: List<LocalDate>,
        includeWeekday: Boolean = false
    ): String = when (granularity) {
        ChartTimeGranularity.DAILY -> buildString {
            append("${date.monthValue}/${date.dayOfMonth}")
            if (includeWeekday) append(" ${weekday(date.dayOfWeek)}")
        }
        ChartTimeGranularity.WEEKLY -> weekLabel(date, includeYear = spansOwningYears(domain)).compactLabel
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
        if (includeYear) {
            "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 ${weekday(date.dayOfWeek)}요일"
        } else {
            "${date.monthValue}월 ${date.dayOfMonth}일 ${weekday(date.dayOfWeek)}요일"
        }

    private fun owningMonth(date: LocalDate): YearMonth =
        YearMonth.from(weekStart(date).plusDays(3))

    private fun spansOwningYears(domain: List<LocalDate>): Boolean =
        domain.map { owningMonth(it).year }.distinct().size > 1

    private fun spansCalendarYears(domain: List<LocalDate>): Boolean =
        domain.map(LocalDate::getYear).distinct().size > 1

    private fun labelsFit(
        indices: List<Int>,
        widths: List<Int>,
        availableWidth: Int,
        minimumGap: Int
    ): Boolean {
        if (indices.isEmpty()) return true
        var previousRight = Int.MIN_VALUE
        indices.forEach { index ->
            val width = widths[index].coerceAtMost(availableWidth)
            val center = if (widths.lastIndex <= 0) {
                availableWidth / 2
            } else {
                availableWidth * index / widths.lastIndex
            }
            val left = (center - width / 2).coerceIn(0, (availableWidth - width).coerceAtLeast(0))
            if (previousRight != Int.MIN_VALUE && left < previousRight + minimumGap) return false
            previousRight = left + width
        }
        return true
    }

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
