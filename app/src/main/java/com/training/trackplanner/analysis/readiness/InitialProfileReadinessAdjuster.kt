package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.InitialUserProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class InitialProfileReadinessAdjuster(
    private val calculator: InitialAdaptationProfileCalculator = InitialAdaptationProfileCalculator()
) {
    fun adaptationFor(initialProfile: InitialUserProfile?): InitialAdaptationProfile? =
        initialProfile?.let(calculator::calculate)

    fun adjustBaseline(
        residual: ResidualFatigueSnapshot,
        adaptiveBaseline: AdaptiveBaselineSnapshot,
        today: LocalDate,
        dailyLoads: List<DailyAnalysisLoad>,
        initialProfile: InitialUserProfile?
    ): AdaptiveBaselineSnapshot {
        val adaptation = adaptationFor(initialProfile) ?: return adaptiveBaseline
        val profileWeight = profileWeight(today, dailyLoads)
        if (profileWeight <= 0.0) return adaptiveBaseline

        val categoryTolerances = FatigueCategoryKey.entries.associateWith { category ->
            val personal = adaptiveBaseline.toleranceByCategory[category]
                ?: TodayReadinessConstants.CONSERVATIVE_TOLERANCE
            val profileTolerance = profileTolerance(
                currentLoad = residual.residualByCategory[category] ?: 0.0,
                personalTolerance = personal,
                targetPressure = targetPressureForCategory(category, adaptation)
            )
            blendDouble(personal, profileTolerance, profileWeight)
        }

        val groupKeys = adaptiveBaseline.toleranceByBaselineGroup.keys +
            residual.residualByAdaptiveBaselineGroup.keys
        val groupTolerances = groupKeys.associateWith { group ->
            val personal = adaptiveBaseline.toleranceByBaselineGroup[group]
                ?: TodayReadinessConstants.CONSERVATIVE_TOLERANCE
            val profileTolerance = profileTolerance(
                currentLoad = residual.residualByAdaptiveBaselineGroup[group] ?: 0.0,
                personalTolerance = personal,
                targetPressure = targetPressureForGroup(group, adaptation)
            )
            blendDouble(personal, profileTolerance, profileWeight)
        }

        val bodyPartKeys = adaptiveBaseline.toleranceByBodyPart.keys + residual.residualByBodyPart.keys
        val bodyPartTolerances = bodyPartKeys.associateWith { part ->
            val personal = adaptiveBaseline.toleranceByBodyPart[part]
                ?: TodayReadinessConstants.CONSERVATIVE_TOLERANCE
            val profileTolerance = profileTolerance(
                currentLoad = residual.residualByBodyPart[part] ?: 0.0,
                personalTolerance = personal,
                targetPressure = targetPressureForBodyPart(part, adaptation)
            )
            blendDouble(personal, profileTolerance, profileWeight)
        }

        val confidenceByCategory = FatigueCategoryKey.entries.associateWith { category ->
            val existing = adaptiveBaseline.confidenceByCategory[category] ?: AnalysisConfidence.LOW
            if (profileWeight >= 0.35) {
                maxConfidence(existing, minConfidence(adaptation.confidence, AnalysisConfidence.MEDIUM_LOW))
            } else {
                existing
            }
        }

        val dataSufficiency = if (profileWeight >= 0.35) {
            maxConfidence(
                adaptiveBaseline.dataSufficiency,
                minConfidence(adaptation.confidence, AnalysisConfidence.MEDIUM_LOW)
            )
        } else {
            adaptiveBaseline.dataSufficiency
        }

        return adaptiveBaseline.copy(
            toleranceByCategory = categoryTolerances,
            toleranceByBaselineGroup = adaptiveBaseline.toleranceByBaselineGroup + groupTolerances,
            toleranceByBodyPart = adaptiveBaseline.toleranceByBodyPart + bodyPartTolerances,
            confidenceByCategory = confidenceByCategory,
            dataSufficiency = dataSufficiency,
            baselineAdjustmentNotes = (adaptiveBaseline.baselineAdjustmentNotes + adaptation.notes).distinct()
        )
    }

    fun adjustSummary(
        summary: TodayReadinessSummary,
        today: LocalDate,
        dailyLoads: List<DailyAnalysisLoad>,
        pressure: FatiguePressureSnapshot,
        initialProfile: InitialUserProfile?
    ): TodayReadinessSummary {
        val adaptation = adaptationFor(initialProfile) ?: return summary
        val profileWeight = profileWeight(today, dailyLoads)
        if (profileWeight <= 0.0 || summary.status == ReadinessStatus.LIMITED) {
            return summary.copy(
                adaptiveBaselineNotes = (summary.adaptiveBaselineNotes + adaptation.notes).distinct()
            )
        }

        val strongSupport = adaptation.resistanceAdaptationScore > 1.10 &&
            adaptation.activityAdaptationScore > 1.05 &&
            adaptation.recoveryCapacityScore >= 0.95 &&
            adaptation.detrainingModifier >= 0.90 &&
            !adaptation.restrictionProfile.hasRestrictions
        val conservativeProfile = adaptation.recoveryCapacityScore <= 0.72 ||
            adaptation.detrainingModifier <= 0.70 ||
            adaptation.restrictionProfile.hasRestrictions
        val hasHighPressure = pressure.categoryPressures.values.any { item -> item.level >= FatigueLevel.HIGH } ||
            pressure.bodyPartPressures.values.any { item -> item.level >= FatigueLevel.HIGH }
        val hasVeryHighPressure = pressure.categoryPressures.values.any { item ->
            (item.pressure ?: 0.0) > 1.60
        }

        val adjustedStatus = when (summary.status) {
            ReadinessStatus.READY ->
                if (conservativeProfile && profileWeight >= 0.35) ReadinessStatus.CAUTION else summary.status
            ReadinessStatus.CAUTION -> when {
                conservativeProfile && hasHighPressure -> ReadinessStatus.FATIGUED
                strongSupport && profileWeight >= 0.35 && !hasHighPressure -> ReadinessStatus.READY
                else -> summary.status
            }
            ReadinessStatus.FATIGUED ->
                if (strongSupport && profileWeight >= 0.35 && !hasVeryHighPressure) {
                    ReadinessStatus.CAUTION
                } else {
                    summary.status
                }
            ReadinessStatus.LIMITED -> summary.status
        }

        val notes = (summary.adaptiveBaselineNotes + adaptation.notes).distinct()
        val restricted = (summary.restrictedModes + restrictedModesFromProfile(adaptation)).distinct()
        return if (adjustedStatus == summary.status) {
            summary.copy(adaptiveBaselineNotes = notes, restrictedModes = restricted.take(4))
        } else {
            summary.copy(
                status = adjustedStatus,
                headline = headlineFor(adjustedStatus),
                shortReason = reasonFor(adjustedStatus),
                adaptiveBaselineNotes = notes,
                restrictedModes = restricted.take(4)
            )
        }
    }

    private fun profileWeight(today: LocalDate, dailyLoads: List<DailyAnalysisLoad>): Double {
        val dates = dailyLoads.map { it.date }.distinct().sorted()
        if (dates.isEmpty() || dates.size < 4) return 0.75
        val lastGap = ChronoUnit.DAYS.between(dates.last(), today)
        if (lastGap >= 60) return 0.65
        return when {
            dates.size < 14 -> 0.70
            dates.size < 42 -> 0.40
            else -> 0.10
        }
    }

    private fun profileTolerance(
        currentLoad: Double,
        personalTolerance: Double,
        targetPressure: Double
    ): Double {
        val target = targetPressure.coerceIn(0.65, 1.75)
        val fromCurrentLoad = if (currentLoad > TodayReadinessConstants.LOW_STD_FLOOR) {
            currentLoad / target
        } else {
            personalTolerance / target
        }
        return fromCurrentLoad.coerceFinite(0.10, Double.MAX_VALUE)
    }

    private fun targetPressureForCategory(
        category: FatigueCategoryKey,
        adaptation: InitialAdaptationProfile
    ): Double {
        val adaptationScore = when (category) {
            FatigueCategoryKey.NEURAL_HEAVY -> adaptation.resistanceAdaptationScore
            FatigueCategoryKey.NEURAL_SPEED,
            FatigueCategoryKey.DECELERATION,
            FatigueCategoryKey.ELASTIC_SSC,
            FatigueCategoryKey.BADMINTON_COURT -> adaptation.badmintonAdaptationScore
            FatigueCategoryKey.SYSTEMIC,
            FatigueCategoryKey.LOCAL_MUSCLE -> adaptation.activityAdaptationScore
            FatigueCategoryKey.OVERHEAD_REPETITION,
            FatigueCategoryKey.GRIP_FOREARM,
            FatigueCategoryKey.ROTATION_POWER,
            FatigueCategoryKey.ANTI_ROTATION -> maxOf(
                adaptation.activityAdaptationScore,
                adaptation.badmintonAdaptationScore
            )
        }
        val capacity = (adaptationScore * adaptation.recoveryCapacityScore * adaptation.detrainingModifier)
            .coerceIn(0.45, 1.70)
        return (1.0 / capacity)
            .coerceIn(0.70, 1.60) *
            adaptation.goalSensitivityProfile.categorySensitivity(category) *
            adaptation.restrictionProfile.categorySensitivity(category)
    }

    private fun targetPressureForGroup(group: String, adaptation: InitialAdaptationProfile): Double {
        val upper = group.uppercase()
        val adaptationScore = when {
            upper in setOf("HEAVY_LOWER", "HINGE", "SQUAT_PATTERN", "UPPER_PUSH", "UPPER_PULL") ->
                adaptation.resistanceAdaptationScore
            upper in setOf("BADMINTON_COURT", "DECELERATION", "ELASTIC_SSC") ->
                adaptation.badmintonAdaptationScore
            else -> adaptation.activityAdaptationScore
        }
        val capacity = (adaptationScore * adaptation.recoveryCapacityScore * adaptation.detrainingModifier)
            .coerceIn(0.45, 1.70)
        return (1.0 / capacity).coerceIn(0.70, 1.60) *
            adaptation.restrictionProfile.groupSensitivity(group)
    }

    private fun targetPressureForBodyPart(part: String, adaptation: InitialAdaptationProfile): Double {
        val capacity = (adaptation.activityAdaptationScore * adaptation.recoveryCapacityScore * adaptation.detrainingModifier)
            .coerceIn(0.45, 1.70)
        return (1.0 / capacity).coerceIn(0.70, 1.60) *
            adaptation.restrictionProfile.bodyPartSensitivity(part)
    }

    private fun restrictedModesFromProfile(adaptation: InitialAdaptationProfile): List<String> = buildList {
        val pain = adaptation.restrictionProfile.painAreaTags
        val avoid = adaptation.restrictionProfile.avoidMovementTags
        if ("SHOULDER" in pain || "BENCH_OR_PUSH" in avoid || "OVERHEAD_PRESS" in avoid) {
            add("어깨 부담 큰 상체 푸시")
        }
        if ("LOW_BACK" in pain || "HEAVY_DEADLIFT" in avoid) add("고축부하 힌지")
        if ("KNEE" in pain || "HEAVY_SQUAT" in avoid) add("무거운 스쿼트")
        if ("JUMP_LANDING" in avoid || "LUNGE_DECELERATION" in avoid) add("점프/감속 드릴")
        if ("LONG_BADMINTON" in avoid) add("장시간 배드민턴")
    }

    private fun headlineFor(status: ReadinessStatus): String =
        when (status) {
            ReadinessStatus.READY -> "오늘은 예정 훈련을 진행해도 좋습니다."
            ReadinessStatus.CAUTION -> "오늘은 강도를 조금 조절하세요."
            ReadinessStatus.FATIGUED -> "오늘은 회복 여지를 더 남기세요."
            ReadinessStatus.LIMITED -> "오늘은 부담이 적은 운동이 낫습니다."
        }

    private fun reasonFor(status: ReadinessStatus): String =
        when (status) {
            ReadinessStatus.READY -> "초기 프로필상 현재 부하를 소화할 여지가 있습니다."
            ReadinessStatus.CAUTION -> "초기 프로필과 최근 기록을 함께 보면 조절이 낫습니다."
            ReadinessStatus.FATIGUED -> "최근 부하와 회복 입력을 함께 보수적으로 반영했습니다."
            ReadinessStatus.LIMITED -> "선택한 제한 신호를 우선 반영했습니다."
        }
}
