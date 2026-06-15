package com.training.trackplanner.analysis.readiness

class AdaptiveBaselineCalculator(
    private val updateEvaluator: AdaptiveBaselineUpdateEvaluator = AdaptiveBaselineUpdateEvaluator()
) {
    fun calculate(
        dailyLoads: List<DailyAnalysisLoad>,
        stats: StatisticalBaselineSnapshot,
        outcomeSignals: List<AdaptiveOutcomeSignal> = emptyList()
    ): AdaptiveBaselineSnapshot {
        val exposureOutcomes = updateEvaluator.evaluateCategoryExposures(
            dailyLoads = dailyLoads,
            stats = stats,
            outcomeSignals = outcomeSignals
        )
        val toleranceByCategory = FatigueCategoryKey.entries.associateWith { category ->
            adjustedTolerance(
                base = baseTolerance(stats.categoryStats[category]),
                exposureOutcome = exposureOutcomes[category] ?: ExposureOutcome(0, 0),
                stat = stats.categoryStats[category]
            )
        }

        val toleranceByGroup = stats.baselineGroupStats.mapValues { (_, stat) ->
            baseTolerance(stat)
        }
        val toleranceByBodyPart = stats.bodyPartStats.mapValues { (_, stat) ->
            baseTolerance(stat)
        }
        val trendByCategory = FatigueCategoryKey.entries.associateWith { category ->
            val outcome = exposureOutcomes[category]
            when {
                outcome == null -> BaselineTrend.INSUFFICIENT_DATA
                outcome.successfulExposureCount > outcome.failedExposureCount -> BaselineTrend.RISING
                outcome.failedExposureCount > 0 -> BaselineTrend.FALLING
                else -> stats.categoryStats[category]?.trend ?: BaselineTrend.INSUFFICIENT_DATA
            }
        }
        val confidenceByCategory = FatigueCategoryKey.entries.associateWith { category ->
            stats.categoryStats[category]?.confidence ?: AnalysisConfidence.LOW
        }

        return AdaptiveBaselineSnapshot(
            toleranceByCategory = toleranceByCategory,
            toleranceByBaselineGroup = toleranceByGroup,
            toleranceByBodyPart = toleranceByBodyPart,
            confidenceByCategory = confidenceByCategory,
            trendByCategory = trendByCategory,
            successfulExposureCountByCategory =
                exposureOutcomes.mapValues { (_, outcome) -> outcome.successfulExposureCount },
            failedExposureCountByCategory =
                exposureOutcomes.mapValues { (_, outcome) -> outcome.failedExposureCount },
            dataSufficiency = stats.overallConfidence,
            baselineAdjustmentNotes = notes(exposureOutcomes, stats.overallConfidence)
        )
    }

    private fun baseTolerance(stat: BaselineStat?): Double {
        if (stat == null) return TodayReadinessConstants.CONSERVATIVE_TOLERANCE
        val candidates = listOf(
            stat.ewmaBaseline.takeIf { value -> value > 0.0 },
            stat.rollingMean.takeIf { value -> value > 0.0 }
        ).filterNotNull()
        return candidates.maxOrNull()
            ?.coerceAtLeast(TodayReadinessConstants.CONSERVATIVE_TOLERANCE)
            ?: TodayReadinessConstants.CONSERVATIVE_TOLERANCE
    }

    private fun adjustedTolerance(
        base: Double,
        exposureOutcome: ExposureOutcome,
        stat: BaselineStat?
    ): Double {
        val upward = (exposureOutcome.successfulExposureCount * TodayReadinessConstants.SUCCESSFUL_EXPOSURE_BONUS)
            .coerceAtMost(TodayReadinessConstants.MAX_SINGLE_RUN_UPWARD_ADJUSTMENT)
        val downward = (exposureOutcome.failedExposureCount * TodayReadinessConstants.FAILED_EXPOSURE_PENALTY)
            .coerceAtMost(TodayReadinessConstants.MAX_SINGLE_RUN_DOWNWARD_ADJUSTMENT)
        val lowLoadDrift = if ((stat?.trend == BaselineTrend.FALLING) && exposureOutcome.successfulExposureCount == 0) {
            TodayReadinessConstants.LONG_LOW_LOAD_DECAY
        } else {
            0.0
        }
        return base * (1.0 + upward - downward - lowLoadDrift)
    }

    private fun notes(
        outcomes: Map<FatigueCategoryKey, ExposureOutcome>,
        confidence: AnalysisConfidence
    ): List<String> = buildList {
        if (confidence == AnalysisConfidence.LOW) {
            add("기록이 더 쌓이면 개인 기준선 판단이 안정됩니다.")
        }
        val rising = outcomes
            .filterValues { outcome -> outcome.successfulExposureCount > outcome.failedExposureCount }
            .keys
            .take(2)
        if (rising.isNotEmpty()) {
            add("최근 안정적으로 소화한 유형은 기준선을 약간 높게 적용합니다.")
        }
        val falling = outcomes
            .filterValues { outcome -> outcome.failedExposureCount > 0 }
            .keys
            .take(2)
        if (falling.isNotEmpty()) {
            add("회복 신호가 불안정한 유형은 기준선을 보수적으로 적용합니다.")
        }
    }
}
