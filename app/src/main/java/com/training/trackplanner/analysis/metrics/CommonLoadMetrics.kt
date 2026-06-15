package com.training.trackplanner.analysis.metrics

import com.training.trackplanner.analysis.core.AnalysisEntry
import com.training.trackplanner.analysis.core.AnalysisInputSnapshot
import com.training.trackplanner.analysis.core.AnalysisSet
import java.time.LocalDate
import kotlin.math.sqrt

data class CommonLoadMetricsResult(
    val dailySessionLoad: Map<LocalDate, Double>,
    val weeklyLoad7: Double,
    val chronicLoad28: Double,
    val recentVsUsualLoadRatio: Double?,
    val trainingDays7: Int,
    val restDays7: Int,
    val dailyLoadMean7: Double,
    val dailyLoadStd7: Double,
    val monotonyCandidate7: Double?,
    val strainCandidate7: Double?
)

object CommonLoadMetrics {
    fun calculate(input: AnalysisInputSnapshot): CommonLoadMetricsResult {
        val dailySessionLoad = input.completedEntriesUntilToday
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.sumOf { entry -> entry.loadCandidate() } }

        val recent7Loads = input.windows.recent7Days
            .dates()
            .map { date -> dailySessionLoad[date] ?: 0.0 }
        val recent28Loads = input.windows.recent28Days
            .dates()
            .map { date -> dailySessionLoad[date] ?: 0.0 }

        val weeklyLoad7 = recent7Loads.sum()
        val chronicLoad28 = recent28Loads.sum()
        val usualWeeklyLoad = chronicLoad28 / 4.0
        val recentVsUsualLoadRatio = if (usualWeeklyLoad > 0.0) {
            weeklyLoad7 / usualWeeklyLoad
        } else {
            null
        }
        val trainingDays7 = recent7Loads.count { it > 0.0 }
        val mean7 = recent7Loads.averageOrZero()
        val std7 = recent7Loads.standardDeviation(mean7)
        val monotonyCandidate7 = if (std7 > 0.0) mean7 / std7 else null
        val strainCandidate7 = monotonyCandidate7?.let { monotony -> weeklyLoad7 * monotony }

        return CommonLoadMetricsResult(
            dailySessionLoad = dailySessionLoad,
            weeklyLoad7 = weeklyLoad7,
            chronicLoad28 = chronicLoad28,
            recentVsUsualLoadRatio = recentVsUsualLoadRatio,
            trainingDays7 = trainingDays7,
            restDays7 = (input.windows.recent7Days.dayCount.toInt() - trainingDays7).coerceAtLeast(0),
            dailyLoadMean7 = mean7,
            dailyLoadStd7 = std7,
            monotonyCandidate7 = monotonyCandidate7,
            strainCandidate7 = strainCandidate7
        )
    }

    private fun AnalysisEntry.loadCandidate(): Double =
        sets.sumOf { set -> set.loadCandidate() }

    private fun AnalysisSet.loadCandidate(): Double {
        val volumeLoad = if (reps > 0 && weightKg > 0.0) reps * weightKg else 0.0
        val timeLoad = if (seconds > 0) seconds / 60.0 else 0.0
        return volumeLoad + timeLoad
    }

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()

    private fun List<Double>.standardDeviation(mean: Double): Double {
        if (isEmpty()) return 0.0
        val variance = sumOf { value -> (value - mean) * (value - mean) } / size
        return sqrt(variance)
    }
}
