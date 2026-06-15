package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.FatigueCategoryKey
import com.training.trackplanner.analysis.readiness.FatiguePressure
import com.training.trackplanner.analysis.readiness.FatiguePressureSnapshot
import com.training.trackplanner.analysis.readiness.PainGateSnapshot
import com.training.trackplanner.analysis.readiness.PerformanceSignalSnapshot
import com.training.trackplanner.analysis.readiness.RecoverySignalSnapshot
import java.time.LocalDate

class FatigueCompositeIndexCalculator {
    fun calculate(
        weekStart: LocalDate,
        pressure: FatiguePressureSnapshot,
        recovery: RecoverySignalSnapshot,
        performance: PerformanceSignalSnapshot,
        pain: PainGateSnapshot
    ): FatigueWeekIndex {
        val categoryScores = pressure.categoryPressures.mapKeys { (key, _) -> key.name }
            .mapValues { (_, item) -> categoryFatigueScore(item) }
        val baselineGroupScores = pressure.baselineGroupPressures
            .mapValues { (_, item) -> categoryFatigueScore(item) }
        val bodyPartScores = pressure.bodyPartPressures
            .mapValues { (_, item) -> categoryFatigueScore(item) }

        val systemic = categoryScores[FatigueCategoryKey.SYSTEMIC.name] ?: 100.0
        val strengthScores = listOfNotNull(
            categoryScores[FatigueCategoryKey.NEURAL_HEAVY.name],
            baselineGroupScores["HEAVY_LOWER"],
            baselineGroupScores["HINGE"],
            baselineGroupScores["SQUAT_PATTERN"],
            baselineGroupScores["UPPER_PUSH"],
            baselineGroupScores["UPPER_PULL"]
        )
        val strengthGroup = meanMaxBlend(strengthScores)
        val badmintonScores = listOfNotNull(
            categoryScores[FatigueCategoryKey.NEURAL_SPEED.name],
            categoryScores[FatigueCategoryKey.DECELERATION.name],
            categoryScores[FatigueCategoryKey.ELASTIC_SSC.name],
            categoryScores[FatigueCategoryKey.BADMINTON_COURT.name],
            categoryScores[FatigueCategoryKey.OVERHEAD_REPETITION.name],
            categoryScores[FatigueCategoryKey.GRIP_FOREARM.name]
        )
        val badmintonGroup = meanMaxBlend(badmintonScores)
        val localGroup = localBodyPartGroup(bodyPartScores.values.toList())
        val recoveryPenalty = recoveryPerformancePenalty(recovery, performance, pain)
        val averageStandardized = TrendMath.mean(listOf(systemic, strengthGroup, badmintonGroup, localGroup))
        val maxCategory = listOf(systemic, strengthGroup, badmintonGroup, localGroup).maxOrNull() ?: 100.0
        val composite = TrendMath.clamp(
            PerformanceTrendConstants.FATIGUE_AVERAGE_WEIGHT * averageStandardized +
                PerformanceTrendConstants.FATIGUE_MAX_WEIGHT * maxCategory +
                PerformanceTrendConstants.FATIGUE_RECOVERY_WEIGHT * recoveryPenalty,
            PerformanceTrendConstants.FATIGUE_MIN,
            PerformanceTrendConstants.FATIGUE_MAX
        )

        return FatigueWeekIndex(
            weekStart = weekStart,
            systemicGroupScore = systemic,
            strengthGroupScore = strengthGroup,
            badmintonGroupScore = badmintonGroup,
            localBodyPartGroupScore = localGroup,
            recoveryPerformancePenaltyScore = recoveryPenalty,
            compositeIndex = composite,
            confidence = TrendMath.combineConfidence(
                listOf(
                    pressure.categoryPressures.values.minOfOrNull { item -> item.confidence }
                        ?: AnalysisConfidence.LOW,
                    recovery.confidence,
                    performance.confidence,
                    pain.confidence
                )
            ),
            categoryScores = categoryScores,
            bodyPartScores = bodyPartScores
        )
    }

    fun categoryFatigueScore(item: FatiguePressure): Double {
        val pressureScore = TrendMath.clamp(
            100.0 * (item.pressure ?: 1.0),
            PerformanceTrendConstants.FATIGUE_MIN,
            PerformanceTrendConstants.FATIGUE_MAX
        )
        val percentileScore = TrendMath.percentileScore(item.percentile)
        val zScore = item.zScore
        return if (zScore == null || zScore.isNaN() || zScore.isInfinite()) {
            TrendMath.weightedMean(
                values = listOf(pressureScore, percentileScore),
                weights = listOf(0.60, 0.40),
                fallback = 100.0
            )
        } else {
            TrendMath.weightedMean(
                values = listOf(pressureScore, percentileScore, TrendMath.zScoreBasedScore(zScore)),
                weights = listOf(0.50, 0.35, 0.15),
                fallback = 100.0
            )
        }
    }

    private fun meanMaxBlend(values: List<Double>): Double {
        if (values.isEmpty()) return 100.0
        return (0.70 * values.average()) + (0.30 * (values.maxOrNull() ?: 100.0))
    }

    private fun localBodyPartGroup(values: List<Double>): Double {
        if (values.isEmpty()) return 100.0
        val sorted = values.sortedDescending()
        val top1 = sorted.first()
        val top3Mean = sorted.take(3).average()
        return (0.50 * top1) + (0.50 * top3Mean)
    }

    private fun recoveryPerformancePenalty(
        recovery: RecoverySignalSnapshot,
        performance: PerformanceSignalSnapshot,
        pain: PainGateSnapshot
    ): Double {
        val scores = buildList {
            add(
                when (recovery.sleepSignal.name) {
                    "LOW", "NORMAL" -> 100.0
                    "ELEVATED" -> 110.0
                    "HIGH" -> 125.0
                    "VERY_HIGH" -> 140.0
                    else -> 100.0
                }
            )
            add(
                when {
                    performance.reasons.size >= 2 -> 135.0
                    performance.hasDrop -> 125.0
                    else -> 100.0
                }
            )
            add(
                when {
                    pain.isLimited -> 140.0
                    pain.restrictedTargets.isNotEmpty() -> 120.0
                    else -> 100.0
                }
            )
        }
        return (0.70 * scores.average()) + (0.30 * (scores.maxOrNull() ?: 100.0))
    }
}
