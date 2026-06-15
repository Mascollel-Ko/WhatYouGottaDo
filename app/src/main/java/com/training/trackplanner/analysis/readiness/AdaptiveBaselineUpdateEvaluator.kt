package com.training.trackplanner.analysis.readiness

class AdaptiveBaselineUpdateEvaluator {
    fun evaluateCategoryExposures(
        dailyLoads: List<DailyAnalysisLoad>,
        stats: StatisticalBaselineSnapshot,
        outcomeSignals: List<AdaptiveOutcomeSignal>
    ): Map<FatigueCategoryKey, ExposureOutcome> {
        val signalsByCategory = outcomeSignals.groupBy { signal -> signal.category }
        return FatigueCategoryKey.entries.associateWith { category ->
            val stat = stats.categoryStats[category]
            val highThreshold = maxOf(
                stat?.rollingMean ?: 0.0,
                (stat?.ewmaBaseline ?: 0.0) * 1.15,
                TodayReadinessConstants.CONSERVATIVE_TOLERANCE
            )
            val highExposureDates = dailyLoads
                .filter { daily -> (daily.categoryLoads[category] ?: 0.0) >= highThreshold }
                .map { daily -> daily.date }
                .toSet()
            val signals = signalsByCategory[category].orEmpty()
            val failedDates = signals
                .filterNot { signal -> signal.isSuccessful() }
                .map { signal -> signal.date }
                .toSet()
            val explicitSuccessDates = signals
                .filter { signal -> signal.isSuccessful() }
                .map { signal -> signal.date }
                .toSet()
            val successful = highExposureDates.count { date ->
                date !in failedDates && date in explicitSuccessDates
            }
            val failed = highExposureDates.count { date -> date in failedDates } +
                signals.count { signal -> !signal.isSuccessful() && signal.date !in highExposureDates }

            ExposureOutcome(
                successfulExposureCount = successful,
                failedExposureCount = failed
            )
        }
    }

    private fun AdaptiveOutcomeSignal.isSuccessful(): Boolean =
        recoveryStable && !painPresent && !performanceDrop && !fatigueIncrease
}

data class ExposureOutcome(
    val successfulExposureCount: Int,
    val failedExposureCount: Int
)
