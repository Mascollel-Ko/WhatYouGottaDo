package com.training.trackplanner.analysis.trends

import kotlin.math.max

object TrendChartRange {
    fun percent(values: List<Double>, minPadding: Double = 5.0): Pair<Double, Double>? {
        if (values.isEmpty()) return null
        val minValue = values.minOrNull() ?: return null
        val maxValue = values.maxOrNull() ?: return null
        val padding = max((maxValue - minValue) * 0.15, minPadding)
        val lower = (minValue - padding).coerceAtLeast(0.0)
        val upper = (maxValue + padding).coerceAtMost(100.0)
        return if (upper - lower < minPadding) {
            (minValue - minPadding).coerceAtLeast(0.0) to (maxValue + minPadding).coerceAtMost(100.0)
        } else {
            lower to upper
        }
    }
}
