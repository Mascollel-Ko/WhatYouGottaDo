package com.training.trackplanner.analysis.core

import com.training.trackplanner.analysis.trends.AnalysisChartTemporalPolicy
import java.time.LocalDate

data class WeeklyCoreStimulus(
    val weekStart: LocalDate,
    val direct: Double,
    val indirect: Double
) {
    val total: Double get() = direct + indirect
}

object CoreStimulusWeeklySeries {
    fun aggregate(daily: List<DailyCoreStimulus>): List<WeeklyCoreStimulus> {
        val byWeek = daily.groupBy { point -> AnalysisChartTemporalPolicy.weekStart(point.date) }
        return AnalysisChartTemporalPolicy.weeklyDomain(daily.map(DailyCoreStimulus::date)).map { weekStart ->
            val points = byWeek[weekStart].orEmpty()
            WeeklyCoreStimulus(
                weekStart = weekStart,
                direct = points.sumOf(DailyCoreStimulus::direct),
                indirect = points.sumOf(DailyCoreStimulus::indirect)
            )
        }
    }
}
