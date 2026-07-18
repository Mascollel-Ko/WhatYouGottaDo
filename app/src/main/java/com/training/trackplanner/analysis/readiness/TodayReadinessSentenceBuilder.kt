package com.training.trackplanner.analysis.readiness

class TodayReadinessSentenceBuilder {
    fun build(
        status: ReadinessStatus,
        pressure: FatiguePressureSnapshot,
        recovery: RecoverySignalSnapshot,
        performance: PerformanceSignalSnapshot,
        pain: PainGateSnapshot,
        adaptiveBaseline: AdaptiveBaselineSnapshot
    ): TodayReadinessSentence {
        val reasons = primaryReasons(pressure, recovery, performance, pain)
        val recommended = recommendedModes(status, pressure)
        val restricted = restrictedModes(status, pressure, pain)
        val headline = when (status) {
            ReadinessStatus.READY -> "오늘은 예정 훈련을 진행해도 좋습니다."
            ReadinessStatus.CAUTION -> "오늘은 강도를 조금 조절하세요."
            ReadinessStatus.FATIGUED -> "오늘은 고강도 훈련을 줄이세요."
            ReadinessStatus.LIMITED -> "오늘은 부담이 적은 운동이 낫습니다."
        }
        val shortReason = when {
            reasons.isNotEmpty() -> reasons.first()
            adaptiveBaseline.dataSufficiency == AnalysisConfidence.LOW ->
                "기록이 더 쌓이면 판단이 안정됩니다."
            else -> "현재 기록 기준으로 큰 충돌은 보이지 않습니다."
        }
        return TodayReadinessSentence(
            headline = headline.safe(),
            shortReason = shortReason.safe(),
            primaryReasons = reasons.take(3).map { reason -> reason.safe() },
            recommendedModes = recommended.take(4),
            restrictedModes = restricted.take(4),
            adaptiveBaselineNotes = adaptiveBaseline.baselineAdjustmentNotes.map { note -> note.safe() }
        )
    }

    private fun primaryReasons(
        pressure: FatiguePressureSnapshot,
        recovery: RecoverySignalSnapshot,
        performance: PerformanceSignalSnapshot,
        pain: PainGateSnapshot
    ): List<String> = buildList {
        if (pain.reasons.isNotEmpty()) addAll(pain.reasons)
        pressure.categoryPressures
            .filterValues { item -> item.level >= FatigueLevel.HIGH }
            .entries
            .sortedByDescending { entry -> entry.value.level.ordinal }
            .take(2)
            .forEach { (category, item) ->
                add("${category.koreanLabel()} 부담이 평소보다 높습니다.")
            }
        if (recovery.reasons.isNotEmpty()) addAll(recovery.reasons.take(1))
        if (performance.reasons.isNotEmpty()) addAll(performance.reasons.take(1))
        if (isEmpty()) {
            val elevated = pressure.categoryPressures
                .filterValues { item -> item.level == FatigueLevel.ELEVATED }
                .keys
                .firstOrNull()
            if (elevated != null) add("${elevated.koreanLabel()} 부담이 약간 높습니다.")
        }
    }

    private fun recommendedModes(
        status: ReadinessStatus,
        pressure: FatiguePressureSnapshot
    ): List<String> = buildList {
        when (status) {
            ReadinessStatus.READY -> add("예정 훈련 진행")
            ReadinessStatus.CAUTION -> add("기술연습")
            ReadinessStatus.FATIGUED -> add("회복성 운동")
            ReadinessStatus.LIMITED -> add("가동성 운동")
        }
        if (pressure.categoryPressures[FatigueCategoryKey.NEURAL_HEAVY]?.level.isAtLeast(FatigueLevel.HIGH)) {
            add("가벼운 상체")
        }
        if (pressure.categoryPressures[FatigueCategoryKey.BADMINTON_COURT]?.level.isAtLeast(FatigueLevel.HIGH)) {
            add("저강도 배드민턴")
        }
        add("가벼운 유산소")
        add("코어 안정화")
    }.distinct()

    private fun restrictedModes(
        status: ReadinessStatus,
        pressure: FatiguePressureSnapshot,
        pain: PainGateSnapshot
    ): List<String> = buildList {
        addAll(pain.restrictedTargets)
        val categoryPressures = pressure.categoryPressures
        if (categoryPressures[FatigueCategoryKey.NEURAL_HEAVY]?.level.isAtLeast(FatigueLevel.HIGH) ||
            pressure.baselineGroupPressures["HEAVY_LOWER"]?.level.isAtLeast(FatigueLevel.HIGH)
        ) {
            add("고중량 하체")
            add("데드리프트/RDL 계열")
        }
        if (pressure.baselineGroupPressures["SQUAT_PATTERN"]?.level.isAtLeast(FatigueLevel.HIGH)) {
            add("스쿼트 고중량")
        }
        if (categoryPressures[FatigueCategoryKey.DECELERATION]?.level.isAtLeast(FatigueLevel.HIGH) ||
            categoryPressures[FatigueCategoryKey.BADMINTON_COURT]?.level.isAtLeast(FatigueLevel.HIGH)
        ) {
            add("고강도 풋워크")
            add("랜덤 방향전환 드릴")
        }
        if (categoryPressures[FatigueCategoryKey.ELASTIC_SSC]?.level.isAtLeast(FatigueLevel.HIGH)) {
            add("홉/바운드/점프")
        }
        if (categoryPressures[FatigueCategoryKey.OVERHEAD_REPETITION]?.level.isAtLeast(FatigueLevel.HIGH) ||
            pressure.baselineGroupPressures["SHOULDER_OVERHEAD"]?.level.isAtLeast(FatigueLevel.HIGH)
        ) {
            add("오버헤드 반복")
        }
        if (categoryPressures[FatigueCategoryKey.GRIP_FOREARM]?.level.isAtLeast(FatigueLevel.HIGH) ||
            pressure.baselineGroupPressures["GRIP_FOREARM"]?.level.isAtLeast(FatigueLevel.HIGH)
        ) {
            add("그립 고반복")
        }
        if (status == ReadinessStatus.CAUTION && isEmpty()) add("실패근접 세트")
    }.distinct()

    private fun String.safe(): String {
        val banned = listOf("부상 위험", "다칠 수 있습니다", "과훈련", "위험합니다", "회복이 안 됐습니다", "진단")
        require(banned.none { bannedWord -> contains(bannedWord) }) {
            "Today readiness sentence contains a prohibited expression."
        }
        return this
    }
}

data class TodayReadinessSentence(
    val headline: String,
    val shortReason: String,
    val primaryReasons: List<String>,
    val recommendedModes: List<String>,
    val restrictedModes: List<String>,
    val adaptiveBaselineNotes: List<String>
)

internal fun FatigueCategoryKey.koreanLabel(): String =
    when (this) {
        FatigueCategoryKey.SYSTEMIC -> "전신"
        FatigueCategoryKey.NEURAL_HEAVY -> "고중량 신경계"
        FatigueCategoryKey.NEURAL_SPEED -> "고속"
        FatigueCategoryKey.LOCAL_MUSCLE -> "부위별"
        FatigueCategoryKey.DECELERATION -> "감속"
        FatigueCategoryKey.ELASTIC_SSC -> "탄성/점프"
        FatigueCategoryKey.ROTATION_POWER -> "회전 파워"
        FatigueCategoryKey.ANTI_ROTATION -> "항회전"
        FatigueCategoryKey.OVERHEAD_REPETITION -> "오버헤드"
        FatigueCategoryKey.GRIP_FOREARM -> "그립/전완"
        FatigueCategoryKey.BADMINTON_COURT -> "코트"
    }

private fun FatigueLevel?.isAtLeast(level: FatigueLevel): Boolean =
    this != null && this >= level
