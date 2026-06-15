package com.training.trackplanner.analysis.readiness

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
            level = level(ratio, zScore, percentile, confidence),
            confidence = confidence,
            baselineTrend = trend
        )
    }

    private fun level(
        pressure: Double?,
        zScore: Double?,
        percentile: Double?,
        confidence: AnalysisConfidence
    ): FatigueLevel {
        val candidates = mutableListOf<FatigueLevel>()
        pressure?.let { value ->
            candidates += when {
                value > 1.60 -> FatigueLevel.VERY_HIGH
                value >= 1.35 -> FatigueLevel.HIGH
                value >= 1.15 -> FatigueLevel.ELEVATED
                value >= 0.75 -> FatigueLevel.NORMAL
                else -> FatigueLevel.LOW
            }
        }
        zScore?.let { value ->
            candidates += when {
                value > 2.0 -> FatigueLevel.VERY_HIGH
                value >= 1.5 -> FatigueLevel.HIGH
                value >= 1.0 -> FatigueLevel.ELEVATED
                else -> FatigueLevel.NORMAL
            }
        }
        percentile?.let { value ->
            candidates += when {
                value > 95.0 -> FatigueLevel.VERY_HIGH
                value >= 85.0 -> FatigueLevel.HIGH
                value >= 75.0 -> FatigueLevel.ELEVATED
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
