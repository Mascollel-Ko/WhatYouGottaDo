package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.data.InitialUserProfile
import kotlin.math.roundToInt

object FatigueRecordFactors {
    fun rpeFactor(rpe: Double): Double =
        when {
            rpe >= 10.0 -> 1.50
            rpe >= 9.0 -> 1.35
            rpe >= 8.0 -> 1.25
            rpe >= 7.0 -> 1.16
            rpe >= 6.0 -> 1.08
            rpe >= 5.0 -> 1.00
            else -> 0.92
        }

    fun axisLevelMultiplier(level: String): Double =
        when (level.uppercase()) {
            "LOW" -> 0.25
            "HIGH" -> 0.85
            "VERY_HIGH" -> 1.00
            else -> 0.55
        }
}

object RecoveryPressureCalculator {
    fun calculate(otherFiveAxisLoads: List<Double>, durationWeight: Double): Double {
        if (otherFiveAxisLoads.isEmpty()) return 0.0
        val combined = 0.50 * (otherFiveAxisLoads.maxOrNull() ?: 0.0) +
            0.50 * otherFiveAxisLoads.average()
        return combined * durationWeight
    }
}

object FatigueDecayModel {
    private val factors = mapOf(
        "SHORT" to listOf(1.00, 0.35, 0.10, 0.00),
        "MEDIUM" to listOf(1.00, 0.60, 0.35, 0.15, 0.00),
        "LONG" to listOf(1.00, 0.75, 0.55, 0.35, 0.20, 0.10, 0.00),
        "VERY_LONG" to listOf(1.00, 0.85, 0.70, 0.55, 0.40, 0.28, 0.18, 0.10, 0.00)
    )

    fun factor(durationClass: String, daysSinceRecord: Int): Double {
        if (daysSinceRecord < 0) return 0.0
        val table = factors[durationClass.uppercase()] ?: factors.getValue("MEDIUM")
        return table.getOrElse(daysSinceRecord) { 0.0 }
    }

    fun atLeast(durationClass: String, minimum: String): String {
        val rank = mapOf("SHORT" to 0, "MEDIUM" to 1, "LONG" to 2, "VERY_LONG" to 3)
        val current = durationClass.uppercase().takeIf(rank::containsKey) ?: "MEDIUM"
        return if (rank.getValue(current) >= rank.getValue(minimum)) current else minimum
    }
}

object OverallFatigueIndexCalculator {
    fun calculate(axisScores: List<Int>): Int {
        if (axisScores.isEmpty()) return 0
        val mean = axisScores.average()
        val max = axisScores.maxOrNull()?.toDouble() ?: 0.0
        val highAxisPenalty = when (axisScores.count { it >= 80 }) {
            0 -> 0.0
            1 -> 60.0
            2 -> 80.0
            else -> 100.0
        }
        return (0.45 * mean + 0.35 * max + 0.20 * highAxisPenalty)
            .roundToInt()
            .coerceIn(0, 100)
    }
}

object FatigueThresholds {
    const val OFI_ELEVATED_START = 75
    const val OFI_CAUTION_START = 87
    const val OFI_HIGH_START = 98
    const val AXIS_HIGH_COUNT_START = 92
    const val DAILY_AXIS_CAUTION_START = 100

    const val PROGRAM_YELLOW_START = 52
    const val PROGRAM_ORANGE_START = 69
    const val PROGRAM_RED_START = 87
    const val PROGRAM_JOINT_RESTRICTED_START = 75
    const val PROGRAM_AXIS_RESTRICTED_START = 81

    const val PRESENTATION_ELEVATED_SCORE = 69
    const val PRESENTATION_RESTRICTED_SCORE = 81
    const val PRESENTATION_VERY_HIGH_SCORE = 100
    const val PRESENTATION_VOLUME_YELLOW_START = 52
    const val PRESENTATION_VOLUME_ORANGE_START = 69
    const val PRESENTATION_VOLUME_RED_START = 87

    const val PRESSURE_ELEVATED_RATIO = 1.3225
    const val PRESSURE_HIGH_RATIO = 1.5525
    const val PRESSURE_VERY_HIGH_RATIO = 1.84
    const val Z_ELEVATED = 1.15
    const val Z_HIGH = 1.725
    const val Z_VERY_HIGH = 2.30
    const val PERCENTILE_ELEVATED = 87.0
    const val PERCENTILE_HIGH = 98.0
    const val PERCENTILE_VERY_HIGH = 100.0
}

object FatigueLabelResolver {
    fun label(ofi: Int): FatigueReadinessLabel =
        when (ofi.coerceIn(0, 100)) {
            in 0..39 -> FatigueReadinessLabel.LOW
            in 40 until FatigueThresholds.OFI_ELEVATED_START -> FatigueReadinessLabel.NORMAL
            in FatigueThresholds.OFI_ELEVATED_START until FatigueThresholds.OFI_CAUTION_START -> FatigueReadinessLabel.ELEVATED
            in FatigueThresholds.OFI_CAUTION_START until FatigueThresholds.OFI_HIGH_START -> FatigueReadinessLabel.CAUTION
            else -> FatigueReadinessLabel.HIGH_FATIGUE
        }

    fun headline(label: FatigueReadinessLabel): String =
        when (label) {
            FatigueReadinessLabel.LOW -> "피로 부담이 낮습니다."
            FatigueReadinessLabel.NORMAL -> "평소 훈련 부하 범위입니다."
            FatigueReadinessLabel.ELEVATED -> "일부 피로가 평소보다 높습니다."
            FatigueReadinessLabel.CAUTION -> "강도나 반복 부위를 조절하세요."
            FatigueReadinessLabel.HIGH_FATIGUE -> "회복을 우선하고 고강도 훈련을 피하세요."
        }

    fun shouldRecommendRest(ofi: Int, axisScores: List<Int>, jointVeryHigh: Boolean): Boolean =
        ofi >= FatigueThresholds.OFI_HIGH_START ||
            axisScores.count { it >= FatigueThresholds.AXIS_HIGH_COUNT_START } >= 3 ||
            jointVeryHigh
}

object InitialProfileBaselineSeeder {
    fun seed(profile: InitialUserProfile?): FatigueBaselineSeed {
        val weeklySessions = (profile?.strengthSessionsPerWeek ?: 0.0) +
            (profile?.badmintonSessionsPerWeek ?: 0.0)
        val totalYears = (profile?.strengthTrainingYears ?: 0.0) +
            (profile?.badmintonTrainingYears ?: 0.0)
        val base = when {
            weeklySessions >= 7.0 || (weeklySessions >= 5.0 && totalYears >= 4.0) -> 62.0
            weeklySessions >= 3.0 || totalYears >= 2.0 -> 58.0
            weeklySessions >= 1.0 || totalYears > 0.0 -> 50.0
            else -> 40.0
        }
        val adjusted = when (profile?.trainingBreakCategory) {
            "MORE_THAN_EIGHT_WEEKS", "FIVE_TO_EIGHT_WEEKS" -> minOf(base, 40.0)
            "THREE_TO_FOUR_WEEKS" -> minOf(base, 50.0)
            else -> base
        }
        return FatigueBaselineSeed(FatigueAxisValues().map { adjusted }, adjusted)
    }
}

object FatigueBaselineCalculator {
    fun effectiveBaseline(
        recent14: List<FatigueAxisValues>,
        previous14: List<FatigueAxisValues>,
        seed: FatigueBaselineSeed,
        observedDayCount: Int
    ): EffectiveFatigueBaseline {
        val observed = combineWindowBaselines(recent14, previous14)
        val count = observedDayCount.coerceIn(0, 28)
        val seedWeight = (28 - count) / 28.0
        val axes = if (observed == null) {
            seed.axes
        } else {
            blend(observed, seed.axes, seedWeight)
        }
        val confidence = when {
            count >= 28 -> FatigueConfidence.HIGH
            count >= 14 -> FatigueConfidence.MEDIUM
            else -> FatigueConfidence.LOW
        }
        return EffectiveFatigueBaseline(axes, seedWeight, count, confidence)
    }

    fun workloadBaseline(values: List<Double>, fallback: Double): Pair<Double, FatigueConfidence> =
        if (values.isEmpty()) {
            fallback.coerceAtLeast(1.0) to FatigueConfidence.LOW
        } else {
            median(values).coerceAtLeast(1.0) to when {
                values.size >= 6 -> FatigueConfidence.HIGH
                values.size >= 3 -> FatigueConfidence.MEDIUM
                else -> FatigueConfidence.LOW
            }
        }

    private fun combineWindowBaselines(
        recent14: List<FatigueAxisValues>,
        previous14: List<FatigueAxisValues>
    ): FatigueAxisValues? {
        val recent = recent14.takeIf { it.isNotEmpty() }?.medianAxes()
        val previous = previous14.takeIf { it.isNotEmpty() }?.medianAxes()
        return when {
            recent != null && previous != null -> combine(recent, previous, 0.65)
            recent != null -> recent
            else -> previous
        }
    }

    private fun blend(
        observed: FatigueAxisValues,
        seed: FatigueAxisValues,
        seedWeight: Double
    ): FatigueAxisValues = combine(seed, observed, seedWeight)

    private fun combine(
        first: FatigueAxisValues,
        second: FatigueAxisValues,
        firstWeight: Double
    ): FatigueAxisValues {
        val secondWeight = 1.0 - firstWeight
        return FatigueAxisValues(
            first.neuromuscular * firstWeight + second.neuromuscular * secondWeight,
            first.systemicMuscular * firstWeight + second.systemicMuscular * secondWeight,
            first.localMuscular * firstWeight + second.localMuscular * secondWeight,
            first.jointTendonImpact * firstWeight + second.jointTendonImpact * secondWeight,
            first.movementFocus * firstWeight + second.movementFocus * secondWeight,
            first.recoveryPressure * firstWeight + second.recoveryPressure * secondWeight
        )
    }

    private fun List<FatigueAxisValues>.medianAxes(): FatigueAxisValues =
        FatigueAxisValues(
            median(map { it.neuromuscular }),
            median(map { it.systemicMuscular }),
            median(map { it.localMuscular }),
            median(map { it.jointTendonImpact }),
            median(map { it.movementFocus }),
            median(map { it.recoveryPressure })
        )

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}
