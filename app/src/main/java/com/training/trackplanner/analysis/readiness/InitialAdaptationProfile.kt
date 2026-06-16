package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.InitialUserProfile
import kotlin.math.max
import kotlin.math.min

data class InitialAdaptationProfile(
    val resistanceAdaptationScore: Double,
    val activityAdaptationScore: Double,
    val badmintonAdaptationScore: Double,
    val recoveryCapacityScore: Double,
    val detrainingModifier: Double,
    val restrictionProfile: InitialRestrictionProfile,
    val goalSensitivityProfile: GoalSensitivityProfile,
    val confidence: AnalysisConfidence,
    val notes: List<String>
)

data class InitialRestrictionProfile(
    val painAreaTags: Set<String>,
    val avoidMovementTags: Set<String>
) {
    val hasRestrictions: Boolean =
        painAreaTags.any { it != "NONE" } || avoidMovementTags.any { it != "NONE" }

    fun categorySensitivity(category: FatigueCategoryKey): Double =
        when (category) {
            FatigueCategoryKey.NEURAL_HEAVY ->
                if (hasAny("LOW_BACK", "KNEE", "HEAVY_SQUAT", "HEAVY_DEADLIFT")) 1.18 else 1.0
            FatigueCategoryKey.DECELERATION,
            FatigueCategoryKey.ELASTIC_SSC,
            FatigueCategoryKey.BADMINTON_COURT ->
                if (hasAny("KNEE", "CALF_ACHILLES", "ANKLE_FOOT", "HIP", "JUMP_LANDING", "LUNGE_DECELERATION")) 1.22 else 1.0
            FatigueCategoryKey.OVERHEAD_REPETITION ->
                if (hasAny("SHOULDER", "NECK", "BENCH_OR_PUSH", "OVERHEAD_PRESS")) 1.25 else 1.0
            FatigueCategoryKey.GRIP_FOREARM ->
                if (hasAny("WRIST_HAND")) 1.18 else 1.0
            FatigueCategoryKey.ROTATION_POWER,
            FatigueCategoryKey.ANTI_ROTATION ->
                if (hasAny("LOW_BACK", "HIP", "ROTATION")) 1.15 else 1.0
            else -> 1.0
        }

    fun groupSensitivity(group: String): Double {
        val key = group.uppercase()
        return when {
            key in setOf("HEAVY_LOWER", "HINGE", "SQUAT_PATTERN") &&
                hasAny("LOW_BACK", "KNEE", "HEAVY_SQUAT", "HEAVY_DEADLIFT") -> 1.20
            key in setOf("UPPER_PUSH", "SHOULDER_OVERHEAD") &&
                hasAny("SHOULDER", "BENCH_OR_PUSH", "OVERHEAD_PRESS") -> 1.25
            key == "BADMINTON_COURT" &&
                hasAny("KNEE", "ANKLE_FOOT", "JUMP_LANDING", "LUNGE_DECELERATION", "LONG_BADMINTON") -> 1.22
            key == "GRIP_FOREARM" && hasAny("WRIST_HAND") -> 1.18
            else -> 1.0
        }
    }

    fun bodyPartSensitivity(part: String): Double {
        val key = part.uppercase()
        return when {
            key.contains("SHOULDER") && hasAny("SHOULDER", "BENCH_OR_PUSH", "OVERHEAD_PRESS") -> 1.25
            key.contains("ROTATOR") && hasAny("SHOULDER", "OVERHEAD_PRESS") -> 1.25
            key.contains("LOW_BACK") && hasAny("LOW_BACK", "HEAVY_DEADLIFT") -> 1.25
            key.contains("QUADS") && hasAny("KNEE", "HEAVY_SQUAT", "LUNGE_DECELERATION") -> 1.18
            key.contains("CALVES") && hasAny("CALF_ACHILLES", "ANKLE_FOOT", "JUMP_LANDING") -> 1.18
            key.contains("FOREARM") && hasAny("WRIST_HAND") -> 1.18
            else -> 1.0
        }
    }

    private fun hasAny(vararg keys: String): Boolean =
        keys.any { key -> key in painAreaTags || key in avoidMovementTags }
}

data class GoalSensitivityProfile(
    val primaryGoal: String
) {
    fun categorySensitivity(category: FatigueCategoryKey): Double =
        when (primaryGoal) {
            "BADMINTON_PERFORMANCE" -> when (category) {
                FatigueCategoryKey.BADMINTON_COURT,
                FatigueCategoryKey.DECELERATION,
                FatigueCategoryKey.ELASTIC_SSC,
                FatigueCategoryKey.NEURAL_SPEED -> 1.12
                else -> 1.0
            }
            "STRENGTH_GAIN" -> when (category) {
                FatigueCategoryKey.NEURAL_HEAVY,
                FatigueCategoryKey.SYSTEMIC -> 0.96
                else -> 1.0
            }
            "RECOVERY_INJURY_PREVENTION" -> 1.12
            "HYPERTROPHY_PHYSIQUE" -> if (category == FatigueCategoryKey.LOCAL_MUSCLE) 1.10 else 1.0
            else -> 1.0
        }
}

class InitialAdaptationProfileCalculator {
    fun calculate(profile: InitialUserProfile): InitialAdaptationProfile {
        val detraining = detrainingModifier(profile)
        val recovery = recoveryCapacity(profile)
        val resistance = (
            0.62 +
                yearsScore(profile.strengthTrainingYears) * 0.34 +
                weeklyTrainingScore(
                    sessions = profile.strengthSessionsPerWeek,
                    minutes = profile.strengthMinutesPerSession,
                    rpe = profile.strengthAverageRpe
                ) * 0.30 +
                strengthMarkerScore(profile) * 0.18
            ).coerceIn(0.55, 1.55)
        val badminton = (
            0.62 +
                yearsScore(profile.badmintonTrainingYears) * 0.32 +
                weeklyVolumeScore(
                    sessions = profile.badmintonSessionsPerWeek,
                    minutes = profile.badmintonMinutesPerSession
                ) * 0.42
            ).coerceIn(0.55, 1.55)
        val totalWeeklyMinutes = weeklyMinutes(profile.strengthSessionsPerWeek, profile.strengthMinutesPerSession) +
            weeklyMinutes(profile.badmintonSessionsPerWeek, profile.badmintonMinutesPerSession)
        val activity = (
            0.70 +
                (totalWeeklyMinutes / 420.0).coerceIn(0.0, 1.0) * 0.45 +
                averageRpeScore(profile.strengthAverageRpe) * 0.16
            ).coerceIn(0.55, 1.50)
        val restriction = InitialRestrictionProfile(
            painAreaTags = profile.painAreaTags.toTags(),
            avoidMovementTags = profile.avoidMovementTags.toTags()
        )
        return InitialAdaptationProfile(
            resistanceAdaptationScore = resistance,
            activityAdaptationScore = activity,
            badmintonAdaptationScore = badminton,
            recoveryCapacityScore = recovery,
            detrainingModifier = detraining,
            restrictionProfile = restriction,
            goalSensitivityProfile = GoalSensitivityProfile(profile.primaryGoal.ifBlank { "MIXED" }),
            confidence = confidence(profile),
            notes = buildList {
                add("초기 프로필과 최근 기록을 함께 반영했습니다.")
                if (detraining < 0.85) add("최근 운동 공백이 있어 기준선을 보수적으로 적용했습니다.")
                if (restriction.hasRestrictions) add("선택한 통증 부위와 회피 움직임을 보수적으로 반영했습니다.")
                if (profile.primaryGoal == "BADMINTON_PERFORMANCE") {
                    add("배드민턴 목표에 맞춰 코트 피로를 더 민감하게 반영했습니다.")
                }
            }
        )
    }

    private fun yearsScore(years: Double?): Double =
        ((years ?: 0.0) / 6.0).coerceIn(0.0, 1.0)

    private fun weeklyTrainingScore(sessions: Double?, minutes: Int?, rpe: Double?): Double {
        return (weeklyVolumeScore(sessions, minutes) * 0.78 + averageRpeScore(rpe) * 0.22).coerceIn(0.0, 1.0)
    }

    private fun weeklyVolumeScore(sessions: Double?, minutes: Int?): Double {
        val weeklyMinutes = weeklyMinutes(sessions, minutes)
        return (weeklyMinutes / 240.0).coerceIn(0.0, 1.0)
    }

    private fun weeklyMinutes(sessions: Double?, minutes: Int?): Double =
        (sessions ?: 0.0).coerceAtLeast(0.0) * (minutes ?: 0).coerceAtLeast(0)

    private fun averageRpeScore(vararg rpes: Double?): Double {
        val values = rpes.mapNotNull { rpe -> rpe?.takeIf { it in 1.0..10.0 } }
        if (values.isEmpty()) return 0.45
        return ((values.average() - 1.0) / 9.0).coerceIn(0.0, 1.0)
    }

    private fun strengthMarkerScore(profile: InitialUserProfile): Double {
        val points = listOf(
            profile.squatKg?.let { it / 120.0 },
            profile.deadliftKg?.let { it / 150.0 },
            profile.benchPressKg?.let { it / 90.0 },
            profile.pullUpMaxReps?.let { it / 12.0 },
            profile.pullUpAddedWeightKg?.let { it / 30.0 }
        ).mapNotNull { it?.coerceIn(0.0, 1.0) }
        return points.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    }

    private fun recoveryCapacity(profile: InitialUserProfile): Double {
        var score = 1.0
        val sleepHours = profile.usualSleepHours ?: profile.typicalSleepHours
        if (sleepHours != null) {
            score += when {
                sleepHours >= 7.0 -> 0.12
                sleepHours < 5.5 -> -0.18
                sleepHours < 6.5 -> -0.08
                else -> 0.0
            }
        }
        score += scaleBonus(profile.sleepQuality, highIsGood = true)
        score += scaleBonus(profile.currentCondition ?: profile.currentMood, highIsGood = true)
        score += scaleBonus(profile.currentFatigue, highIsGood = true)
        score += scaleBonus(profile.currentSoreness, highIsGood = true)
        score += scaleBonus(profile.currentStress, highIsGood = true)
        return score.coerceIn(0.60, 1.25)
    }

    private fun scaleBonus(value: Int?, highIsGood: Boolean): Double {
        val bounded = value?.coerceIn(1, 5) ?: return 0.0
        return if (highIsGood) {
            (bounded - 3) * 0.04
        } else {
            (3 - bounded) * 0.045
        }
    }

    private fun detrainingModifier(profile: InitialUserProfile): Double {
        val base = when (profile.trainingBreakCategory) {
            "LESS_THAN_1_WEEK" -> 0.96
            "ONE_TO_TWO_WEEKS" -> 0.90
            "THREE_TO_FOUR_WEEKS" -> 0.80
            "FIVE_TO_EIGHT_WEEKS" -> 0.68
            "MORE_THAN_EIGHT_WEEKS" -> 0.55
            else -> 1.0
        }
        val reason = when (profile.trainingBreakReason) {
            "PAIN_OR_INJURY" -> 0.88
            "ILLNESS" -> 0.92
            "FATIGUE" -> 0.94
            else -> 1.0
        }
        return (base * reason).coerceIn(0.45, 1.0)
    }

    private fun confidence(profile: InitialUserProfile): AnalysisConfidence {
        val filled = listOfNotNull(
            profile.strengthTrainingYears,
            profile.badmintonTrainingYears,
            profile.strengthSessionsPerWeek,
            profile.strengthMinutesPerSession,
            profile.badmintonSessionsPerWeek,
            profile.badmintonMinutesPerSession,
            profile.usualSleepHours ?: profile.typicalSleepHours,
            profile.sleepQuality,
            profile.currentCondition ?: profile.currentMood,
            profile.currentFatigue,
            profile.currentSoreness,
            profile.currentStress
        ).size
        return when {
            filled >= 9 -> AnalysisConfidence.MEDIUM
            filled >= 5 -> AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }
    }

    private fun String.toTags(): Set<String> =
        split(",", "|", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
            .ifEmpty { setOf("NONE") }
}

internal fun blendDouble(personal: Double, profile: Double, profileWeight: Double): Double {
    val weight = profileWeight.coerceIn(0.0, 1.0)
    return personal * (1.0 - weight) + profile * weight
}

internal fun minConfidence(left: AnalysisConfidence, right: AnalysisConfidence): AnalysisConfidence =
    if (left.ordinal <= right.ordinal) left else right

internal fun maxConfidence(left: AnalysisConfidence, right: AnalysisConfidence): AnalysisConfidence =
    if (left.ordinal >= right.ordinal) left else right

internal fun Double.coerceFinite(minValue: Double, maxValue: Double): Double =
    if (isFinite()) coerceIn(minValue, maxValue) else min(max(1.0, minValue), maxValue)
