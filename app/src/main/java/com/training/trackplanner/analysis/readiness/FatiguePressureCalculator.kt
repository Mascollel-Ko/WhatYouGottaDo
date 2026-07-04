package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.analysis.fatigue.FatigueThresholds

class FatiguePressureCalculator {
    fun calculate(
        residual: ResidualFatigueSnapshot,
        stats: StatisticalBaselineSnapshot,
        adaptiveBaseline: AdaptiveBaselineSnapshot
    ): FatiguePressureSnapshot {
        val categoryPressures = FatigueCategoryKey.entries.associateWith { category ->
            pressure(
                key = category.name,
                currentResidualLoad = residual.residualByCategory[category] ?: 0.0,
                adaptiveTolerance = adaptiveBaseline.toleranceByCategory[category],
                stat = stats.categoryStats[category],
                confidence = adaptiveBaseline.confidenceByCategory[category] ?: AnalysisConfidence.LOW,
                trend = adaptiveBaseline.trendByCategory[category] ?: BaselineTrend.INSUFFICIENT_DATA
            )
        }
        val groupKeys = stats.baselineGroupStats.keys
            .plus(residual.residualByAdaptiveBaselineGroup.keys)
            .plus(adaptiveBaseline.toleranceByBaselineGroup.keys)
        val groupPressures = groupKeys.associateWith { group ->
            pressure(
                key = group,
                currentResidualLoad = residual.residualByAdaptiveBaselineGroup[group] ?: 0.0,
                adaptiveTolerance = adaptiveBaseline.toleranceByBaselineGroup[group],
                stat = stats.baselineGroupStats[group],
                confidence = stats.baselineGroupStats[group]?.confidence ?: AnalysisConfidence.LOW,
                trend = stats.baselineGroupStats[group]?.trend ?: BaselineTrend.INSUFFICIENT_DATA
            )
        }
        val bodyPartKeys = stats.bodyPartStats.keys
            .plus(residual.residualByBodyPart.keys)
            .plus(adaptiveBaseline.toleranceByBodyPart.keys)
        val bodyPartPressures = bodyPartKeys.associateWith { part ->
            pressure(
                key = part,
                currentResidualLoad = residual.residualByBodyPart[part] ?: 0.0,
                adaptiveTolerance = adaptiveBaseline.toleranceByBodyPart[part],
                stat = stats.bodyPartStats[part],
                confidence = stats.bodyPartStats[part]?.confidence ?: AnalysisConfidence.LOW,
                trend = stats.bodyPartStats[part]?.trend ?: BaselineTrend.INSUFFICIENT_DATA
            )
        }

        return FatiguePressureSnapshot(
            categoryPressures = categoryPressures,
            baselineGroupPressures = groupPressures,
            bodyPartPressures = bodyPartPressures
        )
    }

    private fun pressure(
        key: String,
        currentResidualLoad: Double,
        adaptiveTolerance: Double?,
        stat: BaselineStat?,
        confidence: AnalysisConfidence,
        trend: BaselineTrend
    ): FatiguePressure {
        val ratio = adaptiveTolerance
            ?.takeIf { tolerance -> tolerance > TodayReadinessConstants.LOW_STD_FLOOR }
            ?.let { tolerance -> currentResidualLoad / tolerance }
        val zScore = stat?.zScore
        val percentile = stat?.percentile
        return FatiguePressure(
            key = key,
            currentResidualLoad = currentResidualLoad,
            adaptiveTolerance = adaptiveTolerance,
            rollingMean = stat?.rollingMean ?: 0.0,
            rollingStd = stat?.rollingStd ?: 0.0,
            zScore = zScore,
            percentile = percentile,
            pressure = ratio,
            level = level(currentResidualLoad, ratio, zScore, percentile, confidence),
            confidence = confidence,
            baselineTrend = trend
        )
    }

    private fun level(
        currentResidualLoad: Double,
        pressure: Double?,
        zScore: Double?,
        percentile: Double?,
        confidence: AnalysisConfidence
    ): FatigueLevel {
        if (currentResidualLoad <= TodayReadinessConstants.LOW_STD_FLOOR) return FatigueLevel.LOW
        if (pressure != null && pressure < 0.75) return FatigueLevel.LOW
        val candidates = mutableListOf<FatigueLevel>()
        pressure?.let { value ->
            candidates += when {
                value > FatigueThresholds.PRESSURE_VERY_HIGH_RATIO -> FatigueLevel.VERY_HIGH
                value >= FatigueThresholds.PRESSURE_HIGH_RATIO -> FatigueLevel.HIGH
                value >= FatigueThresholds.PRESSURE_ELEVATED_RATIO -> FatigueLevel.ELEVATED
                value >= 0.75 -> FatigueLevel.NORMAL
                else -> FatigueLevel.LOW
            }
        }
        zScore?.let { value ->
            candidates += when {
                value > FatigueThresholds.Z_VERY_HIGH -> FatigueLevel.VERY_HIGH
                value >= FatigueThresholds.Z_HIGH -> FatigueLevel.HIGH
                value >= FatigueThresholds.Z_ELEVATED -> FatigueLevel.ELEVATED
                else -> FatigueLevel.NORMAL
            }
        }
        percentile?.let { value ->
            candidates += when {
                value >= FatigueThresholds.PERCENTILE_VERY_HIGH -> FatigueLevel.VERY_HIGH
                value >= FatigueThresholds.PERCENTILE_HIGH -> FatigueLevel.HIGH
                value >= FatigueThresholds.PERCENTILE_ELEVATED -> FatigueLevel.ELEVATED
                else -> FatigueLevel.NORMAL
            }
        }
        val raw = candidates.maxByOrNull { candidate -> candidate.ordinal } ?: FatigueLevel.LOW
        return if (confidence == AnalysisConfidence.LOW && raw == FatigueLevel.VERY_HIGH) {
            FatigueLevel.HIGH
        } else {
            raw
        }
    }
}
