package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence

class PerformanceTrendSentenceBuilder {
    fun dashboardSentence(
        strength: List<TrendDataPoint>,
        badminton: List<TrendDataPoint>,
        fatigue: List<TrendDataPoint>,
        confidence: AnalysisConfidence
    ): String {
        if (confidence == AnalysisConfidence.LOW || strength.countValues() < 4) {
            return "기록이 더 쌓이면 추세 판단이 안정됩니다."
        }
        val strengthTrend = trendLabel(strength)
        val badmintonTrend = trendLabel(badminton)
        val fatigueTrend = trendLabel(fatigue)
        return "근력운동은 $strengthTrend, 배드민턴 훈련량은 $badmintonTrend, 피로도는 $fatigueTrend 흐름입니다."
            .safeTrendSentence()
    }

    fun strengthInterpretation(latest: StrengthWeekIndex?): String =
        if (latest == null) {
            "기록이 더 쌓이면 근력운동 흐름을 볼 수 있습니다."
        } else {
            "최근 근력운동 퍼포먼스는 강도와 수행량의 상대 변화로 계산됩니다."
        }

    fun badmintonInterpretation(latest: BadmintonWeekIndex?): String =
        if (latest == null) {
            "기록이 더 쌓이면 배드민턴 관련 수행량을 볼 수 있습니다."
        } else {
            "배드민턴 훈련량은 셔틀 플레이 시간, 풋워크/반응, 보조훈련량을 합친 흐름입니다."
        }

    fun fatigueInterpretation(latest: FatigueWeekIndex?): String =
        if (latest == null) {
            "기록이 더 쌓이면 피로 부담 흐름을 볼 수 있습니다."
        } else {
            "피로도 종합지수는 개인 기준선 대비 부담을 차트용으로 압축한 값입니다."
        }

    private fun trendLabel(points: List<TrendDataPoint>): String {
        val values = points.mapNotNull { point -> point.value }.takeLast(4)
        if (values.size < 2) return "유지권"
        val delta = values.last() - values.first()
        return when {
            delta > PerformanceTrendConstants.TREND_STABLE_DELTA -> "상승"
            delta < -PerformanceTrendConstants.TREND_STABLE_DELTA -> "하락"
            else -> "유지권"
        }
    }

    private fun List<TrendDataPoint>.countValues(): Int =
        count { point -> point.value != null }

    private fun String.safeTrendSentence(): String {
        val banned = listOf(
            "부상 위험",
            "다칠 수 있습니다",
            "과훈련",
            "위험합니다",
            "진단",
            "실력 향상",
            "때문에",
            "원인입니다",
            "확실합니다"
        )
        require(banned.none { word -> contains(word) }) {
            "Performance trend sentence contains a prohibited expression."
        }
        return this
    }
}

internal fun TrendMetricId.label(): String =
    when (this) {
        TrendMetricId.STRENGTH_PERFORMANCE -> "근력운동 퍼포먼스"
        TrendMetricId.STRENGTH_INTENSITY -> "강도"
        TrendMetricId.STRENGTH_VOLUME -> "수행량"
        TrendMetricId.STRENGTH_EFFICIENCY -> "RPE 대비 운동량"
        TrendMetricId.BADMINTON_TRAINING -> "배드민턴 훈련량"
        TrendMetricId.COURT_VOLUME -> "셔틀 플레이 시간"
        TrendMetricId.FOOTWORK_REACTIVE -> "풋워크/반응"
        TrendMetricId.BADMINTON_SUPPORT -> "보조훈련량"
        TrendMetricId.FATIGUE_COMPOSITE -> "피로도 종합지수"
        TrendMetricId.SYSTEMIC_FATIGUE -> "전신 부담"
        TrendMetricId.STRENGTH_FATIGUE -> "근력운동 부담"
        TrendMetricId.BADMINTON_FATIGUE -> "배드민턴 부담"
        TrendMetricId.LOCAL_BODY_PART_FATIGUE -> "국소/부위 부담"
        TrendMetricId.RECOVERY_PERFORMANCE_PENALTY -> "회복/수행 보정"
        TrendMetricId.SLEEP_HOURS -> "수면시간"
        TrendMetricId.OVERALL_FATIGUE_CHECKIN -> "전신 피로 입력"
        TrendMetricId.LOWER_BODY_FATIGUE_CHECKIN -> "하체 피로 입력"
        TrendMetricId.JOINT_TENDON_DISCOMFORT_CHECKIN -> "관절/건 불편감"
        TrendMetricId.FOCUS_MOTIVATION_CHECKIN -> "집중력/의욕"
        TrendMetricId.RECOVERY_CHECKIN_COMPOSITE -> "회복 체크인 종합"
        TrendMetricId.SMASH_SPEED_TOP3_AVG -> "스매시 Top3 평균 속도"
        TrendMetricId.SMASH_SPEED_BEST -> "스매시 최고 속도"
        TrendMetricId.SMASH_SPEED_AVG -> "스매시 평균 속도"
        TrendMetricId.SMASH_ATTEMPT_COUNT -> "스매시 속도 시도 수"
        TrendMetricId.BENCH_PRESS_E1RM -> "주간 벤치프레스 e1RM 최고"
        TrendMetricId.SQUAT_E1RM -> "주간 스쿼트 e1RM 최고"
        TrendMetricId.DEADLIFT_E1RM -> "주간 데드리프트 e1RM 최고"
        TrendMetricId.MUSCLE_QUADS_LOAD_DAILY -> "주간 대퇴사두 운동량"
        TrendMetricId.MUSCLE_QUADS_LOAD_3D -> "주간 대퇴사두 최근 3일 운동량"
        TrendMetricId.MUSCLE_QUADS_LOAD_7D -> "주간 대퇴사두 최근 7일 운동량"
        TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_DAILY -> "주간 햄스트링 운동량"
        TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_3D -> "주간 햄스트링 최근 3일 운동량"
        TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_7D -> "주간 햄스트링 최근 7일 운동량"
        TrendMetricId.MUSCLE_GLUTES_LOAD_DAILY -> "주간 둔근 운동량"
        TrendMetricId.MUSCLE_GLUTES_LOAD_3D -> "주간 둔근 최근 3일 운동량"
        TrendMetricId.MUSCLE_GLUTES_LOAD_7D -> "주간 둔근 최근 7일 운동량"
        TrendMetricId.MUSCLE_CALVES_LOAD_DAILY -> "주간 종아리 운동량"
        TrendMetricId.MUSCLE_CALVES_LOAD_3D -> "주간 종아리 최근 3일 운동량"
        TrendMetricId.MUSCLE_CALVES_LOAD_7D -> "주간 종아리 최근 7일 운동량"
        TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_DAILY -> "주간 내전근/외전근 운동량"
        TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_3D -> "주간 내전근/외전근 최근 3일 운동량"
        TrendMetricId.MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_7D -> "주간 내전근/외전근 최근 7일 운동량"
        TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_DAILY -> "주간 후면사슬/척추기립근 운동량"
        TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_3D -> "주간 후면사슬/척추기립근 최근 3일 운동량"
        TrendMetricId.MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_7D -> "주간 후면사슬/척추기립근 최근 7일 운동량"
        TrendMetricId.MUSCLE_CHEST_LOAD_DAILY -> "주간 가슴 운동량"
        TrendMetricId.MUSCLE_CHEST_LOAD_3D -> "주간 가슴 최근 3일 운동량"
        TrendMetricId.MUSCLE_CHEST_LOAD_7D -> "주간 가슴 최근 7일 운동량"
        TrendMetricId.MUSCLE_BACK_LATS_LOAD_DAILY -> "주간 등/광배 운동량"
        TrendMetricId.MUSCLE_BACK_LATS_LOAD_3D -> "주간 등/광배 최근 3일 운동량"
        TrendMetricId.MUSCLE_BACK_LATS_LOAD_7D -> "주간 등/광배 최근 7일 운동량"
        TrendMetricId.MUSCLE_SHOULDERS_LOAD_DAILY -> "주간 어깨 운동량"
        TrendMetricId.MUSCLE_SHOULDERS_LOAD_3D -> "주간 어깨 최근 3일 운동량"
        TrendMetricId.MUSCLE_SHOULDERS_LOAD_7D -> "주간 어깨 최근 7일 운동량"
        TrendMetricId.MUSCLE_BICEPS_LOAD_DAILY -> "주간 이두 운동량"
        TrendMetricId.MUSCLE_BICEPS_LOAD_3D -> "주간 이두 최근 3일 운동량"
        TrendMetricId.MUSCLE_BICEPS_LOAD_7D -> "주간 이두 최근 7일 운동량"
        TrendMetricId.MUSCLE_TRICEPS_LOAD_DAILY -> "주간 삼두 운동량"
        TrendMetricId.MUSCLE_TRICEPS_LOAD_3D -> "주간 삼두 최근 3일 운동량"
        TrendMetricId.MUSCLE_TRICEPS_LOAD_7D -> "주간 삼두 최근 7일 운동량"
        TrendMetricId.MUSCLE_FOREARM_GRIP_LOAD_DAILY -> "주간 전완/그립 운동량"
        TrendMetricId.MUSCLE_FOREARM_GRIP_LOAD_3D -> "주간 전완/그립 최근 3일 운동량"
        TrendMetricId.MUSCLE_FOREARM_GRIP_LOAD_7D -> "주간 전완/그립 최근 7일 운동량"
        TrendMetricId.MUSCLE_ANTERIOR_CORE_LOAD_DAILY -> "주간 복근/전면코어 운동량"
        TrendMetricId.MUSCLE_ANTERIOR_CORE_LOAD_3D -> "주간 복근/전면코어 최근 3일 운동량"
        TrendMetricId.MUSCLE_ANTERIOR_CORE_LOAD_7D -> "주간 복근/전면코어 최근 7일 운동량"
        TrendMetricId.MUSCLE_LATERAL_CORE_LOAD_DAILY -> "주간 측면코어 운동량"
        TrendMetricId.MUSCLE_LATERAL_CORE_LOAD_3D -> "주간 측면코어 최근 3일 운동량"
        TrendMetricId.MUSCLE_LATERAL_CORE_LOAD_7D -> "주간 측면코어 최근 7일 운동량"
        TrendMetricId.MUSCLE_ROTATION_CORE_LOAD_DAILY -> "주간 항회전/회전코어 운동량"
        TrendMetricId.MUSCLE_ROTATION_CORE_LOAD_3D -> "주간 항회전/회전코어 최근 3일 운동량"
        TrendMetricId.MUSCLE_ROTATION_CORE_LOAD_7D -> "주간 항회전/회전코어 최근 7일 운동량"
        TrendMetricId.STRENGTH_DELTA_NEXT -> "다음 근력 변화"
        TrendMetricId.FATIGUE_DELTA_NEXT -> "다음 피로 변화"
        TrendMetricId.STRENGTH_VOLUME_ONLY -> "근력운동 수행량"
        TrendMetricId.STRENGTH_INTENSITY_ONLY -> "근력운동 강도"
    }
