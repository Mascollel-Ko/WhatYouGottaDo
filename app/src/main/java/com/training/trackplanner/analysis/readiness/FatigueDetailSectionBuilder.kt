package com.training.trackplanner.analysis.readiness

import java.util.Locale

class FatigueDetailSectionBuilder {
    fun build(
        pressure: FatiguePressureSnapshot,
        recovery: RecoverySignalSnapshot,
        performance: PerformanceSignalSnapshot,
        pain: PainGateSnapshot,
        adaptiveBaseline: AdaptiveBaselineSnapshot
    ): List<FatigueDetailSection> =
        listOf(
            categorySection(
                type = FatigueDetailType.SYSTEMIC,
                title = "전신 피로",
                category = FatigueCategoryKey.SYSTEMIC,
                pressure = pressure
            ),
            categorySection(
                type = FatigueDetailType.NEURAL_HEAVY,
                title = "고중량/힘 기반 신경계 피로",
                category = FatigueCategoryKey.NEURAL_HEAVY,
                pressure = pressure
            ),
            categorySection(
                type = FatigueDetailType.NEURAL_SPEED,
                title = "고속 피로",
                category = FatigueCategoryKey.NEURAL_SPEED,
                pressure = pressure
            ),
            localBodyPartSection(pressure),
            categorySection(
                type = FatigueDetailType.BADMINTON_COURT,
                title = "배드민턴 코트 피로",
                category = FatigueCategoryKey.BADMINTON_COURT,
                pressure = pressure
            ),
            recoverySection(recovery),
            performanceSection(performance),
            painSection(pain),
            adaptiveBaselineSection(adaptiveBaseline)
        )

    private fun categorySection(
        type: FatigueDetailType,
        title: String,
        category: FatigueCategoryKey,
        pressure: FatiguePressureSnapshot
    ): FatigueDetailSection {
        val item = pressure.categoryPressures[category] ?: emptyPressure(category.name)
        return FatigueDetailSection(
            type = type,
            title = title,
            level = item.level,
            summary = when (item.level) {
                FatigueLevel.LOW -> "현재 부담은 낮게 잡힙니다."
                FatigueLevel.NORMAL -> "현재 부담은 평소 범위입니다."
                FatigueLevel.ELEVATED -> "현재 부담이 약간 높습니다."
                FatigueLevel.HIGH -> "현재 부담이 높게 잡힙니다."
                FatigueLevel.VERY_HIGH -> "현재 부담이 크게 잡힙니다."
                FatigueLevel.LIMITED -> "오늘은 해당 영역을 낮춰 잡습니다."
            },
            metrics = metrics(item),
            relatedCategories = listOf(category.koreanLabel()),
            restrictedTargets = restrictionsFor(category, item.level)
        )
    }

    private fun localBodyPartSection(pressure: FatiguePressureSnapshot): FatigueDetailSection {
        val topParts = pressure.bodyPartPressures.values
            .sortedByDescending { item -> item.level.ordinal }
            .take(3)
        val top = topParts.firstOrNull()
        return FatigueDetailSection(
            type = FatigueDetailType.LOCAL_BODY_PART,
            title = "부위별 피로",
            level = top?.level ?: FatigueLevel.LOW,
            summary = if (top == null || top.currentResidualLoad <= 0.0) {
                "부위별 기록이 아직 적습니다."
            } else {
                "${bodyPartDisplayName(top.key)} 부담을 먼저 확인하세요."
            },
            metrics = topParts.flatMap { item ->
                listOf(
                    MetricDisplayItem(bodyPartDisplayName(item.key), levelLabel(item.level)),
                    MetricDisplayItem("기준선 대비", item.pressure.formatRatio())
                )
            }.take(4),
            relatedCategories = topParts.map { item -> bodyPartDisplayName(item.key) },
            restrictedTargets = topParts
                .filter { item -> item.level >= FatigueLevel.HIGH }
                .map { item -> "${bodyPartDisplayName(item.key)} 고강도" }
        )
    }

    private fun recoverySection(recovery: RecoverySignalSnapshot): FatigueDetailSection =
        FatigueDetailSection(
            type = FatigueDetailType.RECOVERY,
            title = "회복 신호",
            level = recovery.overallRecoveryLevel,
            summary = recovery.reasons.firstOrNull() ?: "회복 입력은 큰 충돌이 없습니다.",
            metrics = listOf(
                MetricDisplayItem("수면", levelLabel(recovery.sleepSignal)),
                MetricDisplayItem("보정", "${recovery.recoveryPenalty}단계"),
                MetricDisplayItem("신뢰도", confidenceLabel(recovery.confidence))
            ),
            relatedCategories = emptyList(),
            restrictedTargets = emptyList()
        )

    private fun performanceSection(performance: PerformanceSignalSnapshot): FatigueDetailSection =
        FatigueDetailSection(
            type = FatigueDetailType.PERFORMANCE,
            title = "수행 저하 신호",
            level = performance.level,
            summary = performance.reasons.firstOrNull() ?: "비교 가능한 저하 신호는 크지 않습니다.",
            metrics = listOf(
                MetricDisplayItem("RPE 상승", yesNo(performance.sameLoadRpeIncrease)),
                MetricDisplayItem("반복 감소", yesNo(performance.sameLoadRepsDrop)),
                MetricDisplayItem("e1RM 하락", yesNo(performance.estimated1RmDrop)),
                MetricDisplayItem("신뢰도", confidenceLabel(performance.confidence))
            ),
            relatedCategories = emptyList(),
            restrictedTargets = emptyList()
        )

    private fun painSection(pain: PainGateSnapshot): FatigueDetailSection =
        FatigueDetailSection(
            type = FatigueDetailType.PAIN,
            title = "불편감/제한 신호",
            level = pain.level,
            summary = pain.reasons.firstOrNull() ?: "불편감 입력은 없습니다.",
            metrics = listOf(
                MetricDisplayItem("게이트", if (pain.isLimited) "작동" else "없음"),
                MetricDisplayItem("신뢰도", confidenceLabel(pain.confidence))
            ),
            relatedCategories = pain.restrictedTargets,
            restrictedTargets = pain.restrictedTargets
        )

    private fun adaptiveBaselineSection(adaptive: AdaptiveBaselineSnapshot): FatigueDetailSection {
        val topCategories = adaptive.toleranceByCategory.entries
            .sortedByDescending { (_, tolerance) -> tolerance }
            .take(2)
        return FatigueDetailSection(
            type = FatigueDetailType.ADAPTIVE_BASELINE,
            title = "개인화 기준선",
            level = when (adaptive.dataSufficiency) {
                AnalysisConfidence.HIGH,
                AnalysisConfidence.MEDIUM -> FatigueLevel.NORMAL
                AnalysisConfidence.MEDIUM_LOW -> FatigueLevel.ELEVATED
                AnalysisConfidence.LOW -> FatigueLevel.LOW
            },
            summary = adaptive.baselineAdjustmentNotes.firstOrNull()
                ?: "개인 기준선은 최근 기록으로 재계산됩니다.",
            metrics = topCategories.flatMap { (category, tolerance) ->
                listOf(
                    MetricDisplayItem(category.koreanLabel(), tolerance.formatNumber()),
                    MetricDisplayItem(
                        "성공/실패",
                        "${adaptive.successfulExposureCountByCategory[category] ?: 0}/" +
                            "${adaptive.failedExposureCountByCategory[category] ?: 0}"
                    )
                )
            }.take(4),
            relatedCategories = topCategories.map { (category, _) -> category.koreanLabel() },
            restrictedTargets = emptyList()
        )
    }

    private fun metrics(item: FatiguePressure): List<MetricDisplayItem> =
        listOf(
            MetricDisplayItem("기준선 대비", item.pressure.formatRatio()),
            MetricDisplayItem("최근 분위", item.percentile.formatPercentile()),
            MetricDisplayItem("잔존 피로", item.currentResidualLoad.formatNumber()),
            MetricDisplayItem("신뢰도", confidenceLabel(item.confidence))
        )

    private fun restrictionsFor(category: FatigueCategoryKey, level: FatigueLevel): List<String> {
        if (level < FatigueLevel.HIGH) return emptyList()
        return when (category) {
            FatigueCategoryKey.NEURAL_HEAVY -> listOf("고중량 하체", "실패근접 세트")
            FatigueCategoryKey.NEURAL_SPEED -> listOf("반응 드릴", "고속 풋워크")
            FatigueCategoryKey.DECELERATION -> listOf("방향전환", "홉 투 스틱")
            FatigueCategoryKey.ELASTIC_SSC -> listOf("홉/바운드/점프")
            FatigueCategoryKey.OVERHEAD_REPETITION -> listOf("오버헤드 반복")
            FatigueCategoryKey.GRIP_FOREARM -> listOf("그립 고반복")
            FatigueCategoryKey.BADMINTON_COURT -> listOf("고강도 풋워크")
            else -> emptyList()
        }
    }

    private fun emptyPressure(key: String): FatiguePressure =
        FatiguePressure(
            key = key,
            currentResidualLoad = 0.0,
            adaptiveTolerance = null,
            rollingMean = 0.0,
            rollingStd = 0.0,
            zScore = null,
            percentile = null,
            pressure = null,
            level = FatigueLevel.LOW,
            confidence = AnalysisConfidence.LOW,
            baselineTrend = BaselineTrend.INSUFFICIENT_DATA
        )

    private fun Double?.formatRatio(): String =
        this?.let { value -> String.format(Locale.US, "%.2fx", value) } ?: "기록 부족"

    private fun Double?.formatPercentile(): String =
        this?.let { value -> String.format(Locale.US, "%.0f%%", value) } ?: "기록 부족"

    private fun Double.formatNumber(): String =
        String.format(Locale.US, "%.1f", this)

    private fun levelLabel(level: FatigueLevel): String =
        when (level) {
            FatigueLevel.LOW -> "낮음"
            FatigueLevel.NORMAL -> "보통"
            FatigueLevel.ELEVATED -> "약간 높음"
            FatigueLevel.HIGH -> "높음"
            FatigueLevel.VERY_HIGH -> "매우 높음"
            FatigueLevel.LIMITED -> "조절"
        }

    private fun confidenceLabel(confidence: AnalysisConfidence): String =
        when (confidence) {
            AnalysisConfidence.LOW -> "낮음"
            AnalysisConfidence.MEDIUM_LOW -> "보통 이하"
            AnalysisConfidence.MEDIUM -> "보통"
            AnalysisConfidence.HIGH -> "높음"
        }

    private fun yesNo(value: Boolean): String =
        if (value) "있음" else "없음"
}

internal fun bodyPartDisplayName(part: String): String =
    when (part) {
        "quads" -> "대퇴사두"
        "hamstrings" -> "햄스트링"
        "glutes" -> "둔근"
        "calves_achilles" -> "종아리/아킬레스"
        "erectors_low_back" -> "허리/기립근"
        "chest" -> "가슴"
        "lats_upper_back" -> "등"
        "shoulders" -> "어깨"
        "rotator_cuff" -> "회전근개"
        "elbow_flexors" -> "팔꿈치 굴곡"
        "elbow_extensors" -> "팔꿈치 신전"
        "forearm_grip" -> "전완/그립"
        "core_abs_obliques" -> "코어"
        "hips_adductors_abductors" -> "고관절 안정화"
        else -> part
    }
