package com.training.trackplanner.analysis.readiness

import java.time.LocalDate
import kotlin.math.sqrt

class StatisticalBaselineCalculator {
    fun calculate(
        dailyLoads: List<DailyAnalysisLoad>,
        residual: ResidualFatigueSnapshot,
        today: LocalDate,
        lookbackDays: Int = 56
    ): StatisticalBaselineSnapshot {
        val startDate = today.minusDays((lookbackDays - 1).toLong())
        val dates = (0 until lookbackDays).map { offset -> startDate.plusDays(offset.toLong()) }
        val dailyByDate = dailyLoads.associateBy { daily -> daily.date }
        val activeSpanDays = activeSpanDays(dailyLoads, today)
        val confidence = confidenceFor(activeSpanDays)

        val categoryStats = FatigueCategoryKey.entries.associateWith { category ->
            val series = dates.map { date -> dailyByDate[date]?.categoryLoads?.get(category) ?: 0.0 }
            stat(
                series = series,
                current = residual.residualByCategory[category] ?: 0.0,
                sampleDays = activeSpanDays,
                confidence = confidence
            )
        }

        val baselineGroupKeys = dailyLoads
            .flatMap { daily -> daily.baselineGroupLoads.keys }
            .plus(residual.residualByAdaptiveBaselineGroup.keys)
            .toSet()
        val groupStats = baselineGroupKeys.associateWith { group ->
            val series = dates.map { date -> dailyByDate[date]?.baselineGroupLoads?.get(group) ?: 0.0 }
            stat(
                series = series,
                current = residual.residualByAdaptiveBaselineGroup[group] ?: 0.0,
                sampleDays = activeSpanDays,
                confidence = confidence
            )
        }

        val bodyPartKeys = dailyLoads
            .flatMap { daily -> daily.bodyPartLoads.keys }
            .plus(residual.residualByBodyPart.keys)
            .toSet()
        val bodyPartStats = bodyPartKeys.associateWith { part ->
            val series = dates.map { date -> dailyByDate[date]?.bodyPartLoads?.get(part) ?: 0.0 }
            stat(
                series = series,
                current = residual.residualByBodyPart[part] ?: 0.0,
                sampleDays = activeSpanDays,
                confidence = confidence
            )
        }

        return StatisticalBaselineSnapshot(
            categoryStats = categoryStats,
            baselineGroupStats = groupStats,
            bodyPartStats = bodyPartStats,
            overallConfidence = confidence
        )
    }

    private fun stat(
        series: List<Double>,
        current: Double,
        sampleDays: Int,
        confidence: AnalysisConfidence
    ): BaselineStat {
        val mean = series.averageOrZero()
        val std = series.standardDeviation(mean)
        val zScore = if (std > TodayReadinessConstants.LOW_STD_FLOOR) {
            (current - mean) / std
        } else {
            null
        }
        val percentile = percentile(series, current)
        val ewma = ewma(series)
        val pressure = if (ewma > TodayReadinessConstants.LOW_STD_FLOOR) {
            current / ewma
        } else {
            null
        }
        return BaselineStat(
            rollingMean = mean,
            rollingStd = std,
            zScore = zScore,
            percentile = percentile,
            ewmaBaseline = ewma,
            pressure = pressure,
            trend = trend(series),
            confidence = confidence,
            sampleDays = sampleDays
        )
    }

    private fun activeSpanDays(dailyLoads: List<DailyAnalysisLoad>, today: LocalDate): Int {
        val first = dailyLoads.minOfOrNull { daily -> daily.date } ?: return 0
        return (today.toEpochDay() - first.toEpochDay() + 1).coerceAtLeast(0).toInt()
    }

    private fun confidenceFor(sampleDays: Int): AnalysisConfidence =
        when {
            sampleDays >= 84 -> AnalysisConfidence.HIGH
            sampleDays >= 42 -> AnalysisConfidence.MEDIUM
            sampleDays >= 14 -> AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }

    private fun percentile(series: List<Double>, current: Double): Double? {
        if (series.isEmpty()) return null
        if (current <= TodayReadinessConstants.LOW_STD_FLOOR &&
            series.all { value -> value <= TodayReadinessConstants.LOW_STD_FLOOR }
        ) {
            return 0.0
        }
        val belowOrEqual = series.count { value -> value <= current }
        return belowOrEqual * 100.0 / series.size
    }

    private fun ewma(series: List<Double>): Double {
        if (series.isEmpty()) return 0.0
        var value = series.first()
        series.drop(1).forEach { sample ->
            value = (TodayReadinessConstants.EWMA_ALPHA * sample) +
                ((1.0 - TodayReadinessConstants.EWMA_ALPHA) * value)
        }
        return value
    }

    private fun trend(series: List<Double>): BaselineTrend {
        if (series.count { it > 0.0 } < 7) return BaselineTrend.INSUFFICIENT_DATA
        val recent = series.takeLast(14).averageOrZero()
        val previous = series.dropLast(14).takeLast(14).averageOrZero()
        if (previous <= TodayReadinessConstants.LOW_STD_FLOOR) return BaselineTrend.STABLE
        val ratio = recent / previous
        return when {
            ratio > 1.10 -> BaselineTrend.RISING
            ratio < 0.90 -> BaselineTrend.FALLING
            else -> BaselineTrend.STABLE
        }
    }

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()

    private fun List<Double>.standardDeviation(mean: Double): Double {
        if (isEmpty()) return 0.0
        val variance = sumOf { value -> (value - mean) * (value - mean) } / size
        return sqrt(variance)
    }
}
