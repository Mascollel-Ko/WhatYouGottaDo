package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId

object AnalysisMetricRegistry {
    val descriptors: List<AnalysisMetricDescriptor> = listOf(
        metric(TrendMetricId.STRENGTH_PERFORMANCE, "근력운동 퍼포먼스", "강도, 수행량, 효율을 합친 주간 근력 지수", AnalysisMetricCategory.STRENGTH, true),
        metric(TrendMetricId.STRENGTH_INTENSITY, "강도", "주간 근력운동 강도 지수", AnalysisMetricCategory.STRENGTH, true),
        metric(TrendMetricId.STRENGTH_VOLUME, "수행량", "주간 유효 근력운동 수행량", AnalysisMetricCategory.VOLUME, true),
        metric(TrendMetricId.STRENGTH_EFFICIENCY, "RPE 대비 운동량", "자각 강도 대비 수행 효율", AnalysisMetricCategory.STRENGTH, true),
        metric(TrendMetricId.BADMINTON_TRAINING, "배드민턴 훈련량", "코트, 풋워크, 보조훈련을 합친 주간 지수", AnalysisMetricCategory.BADMINTON, true),
        metric(TrendMetricId.COURT_VOLUME, "셔틀 플레이 시간", "주간 코트 훈련량", AnalysisMetricCategory.BADMINTON, true),
        metric(TrendMetricId.FOOTWORK_REACTIVE, "풋워크/반응", "주간 풋워크 및 반응 훈련량", AnalysisMetricCategory.TRANSFER, true),
        metric(TrendMetricId.BADMINTON_SUPPORT, "보조훈련량", "배드민턴 지원 목적의 주간 훈련량", AnalysisMetricCategory.TRANSFER, true),
        metric(TrendMetricId.FATIGUE_COMPOSITE, "이번 주 누적 부담", "주간 canonical OFI 기반 누적 부담 지수", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.SYSTEMIC_FATIGUE, "전신 부담", "주간 전신성 피로 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.STRENGTH_FATIGUE, "근력운동 부담", "근력운동에서 발생한 주간 피로 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.BADMINTON_FATIGUE, "배드민턴 부담", "배드민턴 훈련에서 발생한 주간 피로 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.LOCAL_BODY_PART_FATIGUE, "국소/부위 부담", "가장 높은 국소 부위의 주간 부담", AnalysisMetricCategory.FATIGUE, false),
        metric(TrendMetricId.RECOVERY_PERFORMANCE_PENALTY, "회복/수행 보정", "회복 상태와 수행 저하를 반영한 주간 지수", AnalysisMetricCategory.RECOVERY, false),
        metric(TrendMetricId.SLEEP_HOURS, "수면시간", "체크인에 기록한 주간 평균 수면시간", AnalysisMetricCategory.RECOVERY, true, "시간"),
        metric(TrendMetricId.OVERALL_FATIGUE_CHECKIN, "전신 피로 입력", "주간 평균 전신 피로 체크인", AnalysisMetricCategory.RECOVERY, false, "1~5"),
        metric(TrendMetricId.LOWER_BODY_FATIGUE_CHECKIN, "하체 피로 입력", "주간 평균 하체 피로 체크인", AnalysisMetricCategory.RECOVERY, false, "1~5"),
        metric(TrendMetricId.JOINT_TENDON_DISCOMFORT_CHECKIN, "관절/건 불편감", "주간 평균 관절/건 불편감 체크인", AnalysisMetricCategory.RECOVERY, false, "1~5"),
        metric(TrendMetricId.FOCUS_MOTIVATION_CHECKIN, "집중력/의욕", "주간 평균 집중력/의욕 체크인", AnalysisMetricCategory.RECOVERY, true, "1~5"),
        metric(TrendMetricId.RECOVERY_CHECKIN_COMPOSITE, "회복 체크인 종합", "수면과 주관적 체크인을 좋은 방향으로 정렬한 주간 종합값", AnalysisMetricCategory.RECOVERY, true, "1~5"),
        metric(TrendMetricId.SMASH_SPEED_TOP3_AVG, "스매시 Top3 평균 속도", "주간 스매시 속도 상위 3회 평균", AnalysisMetricCategory.SMASH_SPEED, true, "km/h"),
        metric(TrendMetricId.SMASH_SPEED_BEST, "스매시 최고 속도", "주간 스매시 최고 속도", AnalysisMetricCategory.SMASH_SPEED, true, "km/h"),
        metric(TrendMetricId.SMASH_SPEED_AVG, "스매시 평균 속도", "주간 스매시 평균 속도", AnalysisMetricCategory.SMASH_SPEED, true, "km/h"),
        metric(TrendMetricId.SMASH_ATTEMPT_COUNT, "스매시 속도 시도 수", "주간 스매시 속도 기록 횟수", AnalysisMetricCategory.SMASH_SPEED, true, "회"),
        metric(TrendMetricId.BENCH_PRESS_E1RM, "주간 벤치프레스 e1RM 최고", "대표 벤치프레스 confirmed set 기준 주간 최고 추정 1RM", AnalysisMetricCategory.PERFORMANCE, true, "kg"),
        metric(TrendMetricId.SQUAT_E1RM, "주간 스쿼트 e1RM 최고", "대표 스쿼트 confirmed set 기준 주간 최고 추정 1RM", AnalysisMetricCategory.PERFORMANCE, true, "kg"),
        metric(TrendMetricId.DEADLIFT_E1RM, "주간 데드리프트 e1RM 최고", "컨벤셔널 데드리프트 confirmed set 기준 주간 최고 추정 1RM", AnalysisMetricCategory.PERFORMANCE, true, "kg"),
        metric(TrendMetricId.STRENGTH_DELTA_NEXT, "다음 근력 변화", "다음 주 근력 지수의 변화량", AnalysisMetricCategory.DERIVED, null),
        metric(TrendMetricId.FATIGUE_DELTA_NEXT, "다음 피로 변화", "다음 주 피로 지수의 변화량", AnalysisMetricCategory.DERIVED, null)
    ) + muscleLoadMetrics()

    fun descriptor(id: TrendMetricId): AnalysisMetricDescriptor? =
        descriptors.firstOrNull { descriptor -> descriptor.id == id }

    fun scatterMetrics(
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        requireAvailableData: Boolean = true
    ): List<AnalysisMetricDescriptor> = descriptors.filter { descriptor ->
        descriptor.supportsScatter && (!requireAvailableData || metricSeries[descriptor.id].orEmpty().any { it.value != null })
    }

    fun timeSeriesXMetrics(metricSeries: Map<TrendMetricId, List<TrendDataPoint>>): List<AnalysisMetricDescriptor> =
        usableTimeSeriesMetrics(metricSeries).filter { descriptor ->
            descriptor.category in setOf(
                AnalysisMetricCategory.BADMINTON,
                AnalysisMetricCategory.TRANSFER,
                AnalysisMetricCategory.VOLUME,
                AnalysisMetricCategory.MUSCLE_LOAD,
                AnalysisMetricCategory.FATIGUE,
                AnalysisMetricCategory.RECOVERY,
                AnalysisMetricCategory.STRENGTH
            )
        }

    fun timeSeriesYMetrics(metricSeries: Map<TrendMetricId, List<TrendDataPoint>>): List<AnalysisMetricDescriptor> =
        usableTimeSeriesMetrics(metricSeries).filter { descriptor ->
            descriptor.category in setOf(
                AnalysisMetricCategory.PERFORMANCE,
                AnalysisMetricCategory.SMASH_SPEED,
                AnalysisMetricCategory.FATIGUE,
                AnalysisMetricCategory.RECOVERY,
                AnalysisMetricCategory.STRENGTH
            )
        }

    fun timeSeriesControlMetrics(metricSeries: Map<TrendMetricId, List<TrendDataPoint>>): List<AnalysisMetricDescriptor> =
        usableTimeSeriesMetrics(metricSeries).filter { descriptor ->
            descriptor.category in setOf(
                AnalysisMetricCategory.RECOVERY,
                AnalysisMetricCategory.VOLUME,
                AnalysisMetricCategory.BADMINTON,
                AnalysisMetricCategory.FATIGUE,
                AnalysisMetricCategory.MUSCLE_LOAD
            )
        }

    private fun usableTimeSeriesMetrics(
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        minPoints: Int = 8
    ): List<AnalysisMetricDescriptor> = descriptors.filter { descriptor ->
        descriptor.supportsTimeSeries && metricSeries[descriptor.id].orEmpty()
            .mapNotNull { point -> point.value }
            .let { values ->
                values.size >= minPoints && ((values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)) > 1e-9
            }
    }

    private fun metric(
        id: TrendMetricId,
        displayName: String,
        description: String,
        category: AnalysisMetricCategory,
        higherIsBetter: Boolean?,
        unit: String = "지수",
        timeGrain: AnalysisTimeGrain = AnalysisTimeGrain.WEEKLY
    ) = AnalysisMetricDescriptor(
        id = id,
        displayName = displayName,
        description = description,
        category = category,
        unit = unit,
        timeGrain = timeGrain,
        supportsScatter = true,
        supportsTimeSeries = true,
        supportsMultivariate = true,
        higherIsBetter = higherIsBetter
    )

    private fun muscleLoadMetrics(): List<AnalysisMetricDescriptor> =
        StrengthAndMuscleMetricSeriesBuilder.MuscleBucket.values().flatMap { bucket ->
            listOf(
                metric(
                    bucket.dailyMetric,
                    "주간 ${bucket.label} 운동량",
                    "${bucket.label} 주간 운동량 지수",
                    AnalysisMetricCategory.MUSCLE_LOAD,
                    false,
                    "운동량 지수"
                ),
                metric(
                    bucket.threeDayMetric,
                    "주간 ${bucket.label} 최근 3일 운동량",
                    "${bucket.label} 주간 최근 3일 누적 운동량 지수",
                    AnalysisMetricCategory.MUSCLE_LOAD,
                    false,
                    "운동량 지수"
                ),
                metric(
                    bucket.sevenDayMetric,
                    "주간 ${bucket.label} 최근 7일 운동량",
                    "${bucket.label} 주간 최근 7일 누적 운동량 지수",
                    AnalysisMetricCategory.MUSCLE_LOAD,
                    false,
                    "운동량 지수"
                )
            )
        }
}
