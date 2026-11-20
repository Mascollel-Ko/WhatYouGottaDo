package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.Exercise
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Frozen product policy. Values are selection heuristics, not clinical limits. */
internal object BCoachV2Policy {
    const val ENGINE_VERSION = "B_COACH_V2_3_COACHING_OPT"
    const val POLICY_VERSION = "B_COACH_V2_3_POLICY_2026_11_20"
    const val SOURCE_OWNER = "B_COACH_V2_3_COACHING_OPT"
    const val COEFFICIENT_SOURCE = "PRODUCT_HEURISTIC_NOT_CLINICAL"
    const val DISCLAIMER =
        "이 추천은 운동 기록·선택한 목표와 시범 계산 규칙을 바탕으로 생성한 훈련 계획입니다. 일부 자극·회복 점수는 아직 과학적으로 검증된 개인 예측값이 아니며, 미확정 중량은 직접 입력할 수 있습니다."

    /**
     * An outcome/assessment test is not a repeatable routine drill.
     * Keep explicitly selected tests available to users; do not recruit
     * them automatically into ordinary week-after-week training.
     */
    fun isTestOnlyExercise(exercise: Exercise): Boolean {
        val name = exercise.name.trim()
        val patternTags = exercise.movementPattern
            .split(Regex("[|,;/]")).map(String::trim)
        return name.endsWith("테스트") || name.endsWith("TEST", ignoreCase = true) ||
            patternTags.any { it.endsWith("_TEST", ignoreCase = true) ||
                it.equals("TEST_ONLY", ignoreCase = true) }
    }

    const val DEFAULT_GOAL_IMPORTANCE = 3
    const val DEFAULT_SESSION_MINUTES = 60
    const val FIXED_BLOCK_BOOST = 0.25
    const val PERSONAL_BLEND_MAX = 0.50
    const val PERSONAL_VALID_WEEKS_MIN = 2
    const val NEED_LOWER_RATIO = 0.85
    const val NEED_UPPER_RATIO = 1.20
    const val RECOVERY_WEEK_MULTIPLIER = 0.75
    const val UNKNOWN_RESISTANCE_SET_CREDIT = 0.75
    const val INITIAL_LOAD_CONSERVATIVE_FACTOR = 0.97
    const val FOLLOW_UP_SET_2_TO_3_FACTOR = 0.985
    const val FOLLOW_UP_SET_4_TO_5_FACTOR = 0.97
    const val WORKING_RIR_MAX_FATIGUE = 3.0
    const val RECOVERY_SET_ADJUSTMENT_MIN = 0.5
    const val RECOVERY_SET_ADJUSTMENT_MAX = 1.5
    const val MAX_PER_EXERCISE_SETS = 5
    const val MAX_WEEK_TO_WEEK_ADDED_SETS = 2
    const val MAX_SHOWN_ALTERNATIVES = 2
    // Android must not retain thousands of fully resolved multiweek plans,
    // each containing per-set load scenarios and objective traces. This is a
    // search-resource limit, never a clinical volume or adaptation threshold.
    const val SEARCH_BEAM_WIDTH = 4
    const val SEARCH_ROUNDS = 5
    const val MAX_CANDIDATE_EVALUATIONS = 256
    const val MAX_FINALISTS = 12
    const val TIE_TOLERANCE = 1e-6
    const val ESTIMATED_WARMUP_MINUTES = 8.0
    const val ESTIMATED_SETUP_KNOWN_MINUTES = 2.3
    const val ESTIMATED_SETUP_UNKNOWN_MINUTES = 4.0
    const val ESTIMATED_ACTIVE_SET_SECONDS = 45.0
    const val TIME_UPPER_FACTOR = 1.15
    const val TIME_UPPER_EXTRA_MINUTES = 2.0

    val scoreWeights: Map<BCoachObjectiveAxis, Double> = linkedMapOf(
        BCoachObjectiveAxis.GOAL_DEFICIT to 0.40,
        BCoachObjectiveAxis.GOAL_EXCESS to 0.09,
        BCoachObjectiveAxis.PROGRESSION_FIT to 0.16,
        BCoachObjectiveAxis.RECOVERY_LOAD to 0.14,
        BCoachObjectiveAxis.RAMP_CHANGE to 0.07,
        BCoachObjectiveAxis.EXECUTION to 0.06,
        BCoachObjectiveAxis.PLAN_CHANGE to 0.04,
        BCoachObjectiveAxis.UNCERTAINTY to 0.04
    ).also { require(kotlin.math.abs(it.values.sum() - 1.0) <= 1e-12) }

    val recoveryKernels: Map<String, DoubleArray> = linkedMapOf(
        "systemic" to doubleArrayOf(1.0, 0.55, 0.25, 0.10),
        "neuralHeavy" to doubleArrayOf(1.0, 0.65, 0.35, 0.15),
        "neuralSpeed" to doubleArrayOf(1.0, 0.40, 0.15, 0.0),
        "localMuscular" to doubleArrayOf(1.0, 0.60, 0.30, 0.10),
        "deceleration" to doubleArrayOf(1.0, 0.65, 0.35, 0.15),
        "elasticSsc" to doubleArrayOf(1.0, 0.50, 0.25, 0.05)
    )

    fun d0(goal: BBalancedGoal, importance: Int): Double {
        require(importance in 1..5)
        return when (goal) {
            BBalancedGoal.STRENGTH -> doubleArrayOf(2.0, 3.0, 4.0, 5.0, 6.0)[importance - 1]
            BBalancedGoal.HYPERTROPHY -> doubleArrayOf(4.0, 6.0, 8.0, 10.0, 12.0)[importance - 1]
            BBalancedGoal.CORE -> doubleArrayOf(2.0, 2.0, 4.0, 6.0, 8.0)[importance - 1]
            BBalancedGoal.SPORT_SUPPORT -> doubleArrayOf(0.0, 2.0, 4.0, 6.0, 8.0)[importance - 1]
        }
    }

    fun dMax(goal: BBalancedGoal): Double = when (goal) {
        BBalancedGoal.STRENGTH -> 10.0
        BBalancedGoal.HYPERTROPHY -> 20.0
        BBalancedGoal.CORE, BBalancedGoal.SPORT_SUPPORT -> 12.0
    }

    fun personalizedCenter(d0: Double, observedValidWeeks: List<Double>, maximum: Double): Double {
        val valid = observedValidWeeks.filter(Double::isFinite)
        if (valid.size < PERSONAL_VALID_WEEKS_MIN) return d0.coerceIn(0.0, maximum)
        val sorted = valid.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        val beta = PERSONAL_BLEND_MAX * valid.size.coerceAtMost(4) / 4.0
        return (d0 + beta * (median - d0)).coerceIn(0.0, maximum)
    }

    fun stageMultiplier(stage: Int): Double = (1.0 + 0.06 * stage).coerceIn(0.75, 1.20)

    fun interval(center: Double, multiplier: Double, scenario: BCoachV2Scenario): Pair<Double, Double> {
        val target = center * multiplier * scenario.needFactor
        val lower = floor(NEED_LOWER_RATIO * target).coerceAtLeast(0.0)
        val upper = max(lower, ceil(NEED_UPPER_RATIO * target))
        return lower to upper
    }

    fun ramp(current: Double, reference: Double, recoveryWeek: Boolean): Double {
        val denominator = max(2.0, reference) * 0.50
        val up = ((max(0.0, current - reference) / denominator) - 0.40).coerceIn(0.0, 1.0)
        if (recoveryWeek) return up
        val down = ((max(0.0, reference - current) / denominator) - 0.40).coerceIn(0.0, 1.0)
        return (up + 0.5 * down).coerceIn(0.0, 1.0)
    }

    fun weightedLoss(scores: Map<BCoachObjectiveAxis, Double>): Double = scoreWeights.entries.sumOf {
        it.value * scores.getValue(it.key).coerceIn(0.0, 1.0)
    }
}

internal enum class BCoachV2Scenario(
    val needFactor: Double,
    val indirectCreditFactor: Double,
    val recoveryKernelFactor: Double
) {
    LOW_STRESS(0.85, 1.15, 0.85),
    BASE(1.0, 1.0, 1.0),
    HIGH_STRESS(1.15, 0.85, 1.15)
}

enum class BCoachEvidenceStatus {
    OBSERVED,
    MODEL_ESTIMATED,
    POLICY_ASSUMED,
    MISSING_CALIBRATE,
    STALE,
    NOT_APPLICABLE
}

typealias BCoachLoadSource = ProgramLoadSource

sealed interface BCoachV2LoadInstruction {
    val evidenceStatus: BCoachEvidenceStatus
    val sourceVersion: String
    val evidenceIds: List<String>

    data class ExactLoad(
        val kg: Double,
        val tier: String,
        val targetRir: Double,
        val loadSource: BCoachLoadSource = BCoachLoadSource.PERSONAL_MODEL,
        val capacityKg: Double? = null,
        val freshRir: Double? = null,
        val expectedRir: Double? = null,
        val conservativeFactor: Double = 1.0,
        val followUpSetFactor: Double = 1.0,
        override val evidenceStatus: BCoachEvidenceStatus,
        override val sourceVersion: String,
        override val evidenceIds: List<String>
    ) : BCoachV2LoadInstruction

    /** A usable scientific estimate whose equipment grid is not yet known. */
    data class EstimatedLoad(
        val estimatedKg: Double,
        val tier: String,
        val targetRir: Double,
        val loadSource: BCoachLoadSource,
        val capacityKg: Double,
        val freshRir: Double,
        val expectedRir: Double,
        val relativeIntensity: Double,
        val conservativeFactor: Double,
        val followUpSetFactor: Double,
        override val evidenceStatus: BCoachEvidenceStatus,
        override val sourceVersion: String,
        override val evidenceIds: List<String>
    ) : BCoachV2LoadInstruction

    data class HoldLastConfirmed(
        val kg: Double,
        val sourceDate: String,
        val loadSource: BCoachLoadSource = BCoachLoadSource.HISTORY_HOLD,
        override val evidenceStatus: BCoachEvidenceStatus = BCoachEvidenceStatus.OBSERVED,
        override val sourceVersion: String = BCoachV2Policy.POLICY_VERSION,
        override val evidenceIds: List<String> = emptyList()
    ) : BCoachV2LoadInstruction

    data class Calibrate(
        val reason: String,
        val loadSource: BCoachLoadSource = BCoachLoadSource.CALIBRATION_REQUIRED,
        override val evidenceStatus: BCoachEvidenceStatus = BCoachEvidenceStatus.MISSING_CALIBRATE,
        override val sourceVersion: String = BCoachV2Policy.POLICY_VERSION,
        override val evidenceIds: List<String> = emptyList()
    ) : BCoachV2LoadInstruction

    data class NonLoadMetric(
        val metric: String,
        val value: Double,
        val loadSource: BCoachLoadSource = BCoachLoadSource.NON_LOAD_METRIC,
        override val evidenceStatus: BCoachEvidenceStatus = BCoachEvidenceStatus.POLICY_ASSUMED,
        override val sourceVersion: String = BCoachV2Policy.POLICY_VERSION,
        override val evidenceIds: List<String> = emptyList()
    ) : BCoachV2LoadInstruction
}

internal fun Double.unitInterval(): Double = coerceIn(0.0, 1.0)
