package com.training.trackplanner.analysis.fatigue

import kotlin.math.abs

object HomeMiniChartSeriesBuilder {
    fun projected(
        current: List<MiniTrendPoint>,
        projectedTodayValue: Double?
    ): List<MiniTrendPoint>? {
        if (current.isEmpty() || projectedTodayValue == null) return null
        if (abs(current.last().value - projectedTodayValue) < 0.0001) return null

        return current.mapIndexed { index, point ->
            if (index == current.lastIndex) point.copy(value = projectedTodayValue) else point
        }
    }
}
