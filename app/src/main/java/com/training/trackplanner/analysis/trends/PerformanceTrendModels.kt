package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import java.time.LocalDate

enum class ChartType {
    LINE,
    BAR,
    HORIZONTAL_BAR,
    STACKED_BAR,
    PIE,
    SCATTER
}

enum class DetailChartMode {
    TREND,
    COMPOSITION,
    CONTRIBUTION,
    RANKING,
    RELATIONSHIP
}

enum class PerformanceDetailSectionType {
    STRENGTH,
    BADMINTON,
    FATIGUE,
    RELATIONSHIP
}

enum class TrendMetricId {
    STRENGTH_PERFORMANCE,
    STRENGTH_INTENSITY,
    STRENGTH_VOLUME,
    STRENGTH_EFFICIENCY,
    BADMINTON_TRAINING,
    COURT_VOLUME,
    FOOTWORK_REACTIVE,
    BADMINTON_SUPPORT,
    FATIGUE_COMPOSITE,
    SYSTEMIC_FATIGUE,
    STRENGTH_FATIGUE,
    BADMINTON_FATIGUE,
    LOCAL_BODY_PART_FATIGUE,
    RECOVERY_PERFORMANCE_PENALTY,
    SLEEP_HOURS,
    OVERALL_FATIGUE_CHECKIN,
    LOWER_BODY_FATIGUE_CHECKIN,
    JOINT_TENDON_DISCOMFORT_CHECKIN,
    FOCUS_MOTIVATION_CHECKIN,
    RECOVERY_CHECKIN_COMPOSITE,
    SMASH_SPEED_TOP3_AVG,
    SMASH_SPEED_BEST,
    SMASH_SPEED_AVG,
    SMASH_ATTEMPT_COUNT,
    BENCH_PRESS_E1RM,
    SQUAT_E1RM,
    DEADLIFT_E1RM,
    MUSCLE_QUADS_LOAD_DAILY,
    MUSCLE_QUADS_LOAD_3D,
    MUSCLE_QUADS_LOAD_7D,
    MUSCLE_HAMSTRINGS_LOAD_DAILY,
    MUSCLE_HAMSTRINGS_LOAD_3D,
    MUSCLE_HAMSTRINGS_LOAD_7D,
    MUSCLE_GLUTES_LOAD_DAILY,
    MUSCLE_GLUTES_LOAD_3D,
    MUSCLE_GLUTES_LOAD_7D,
    MUSCLE_CALVES_LOAD_DAILY,
    MUSCLE_CALVES_LOAD_3D,
    MUSCLE_CALVES_LOAD_7D,
    MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_DAILY,
    MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_3D,
    MUSCLE_ADDUCTOR_ABDUCTOR_LOAD_7D,
    MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_DAILY,
    MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_3D,
    MUSCLE_POSTERIOR_CHAIN_ERECTORS_LOAD_7D,
    MUSCLE_CHEST_LOAD_DAILY,
    MUSCLE_CHEST_LOAD_3D,
    MUSCLE_CHEST_LOAD_7D,
    MUSCLE_BACK_LATS_LOAD_DAILY,
    MUSCLE_BACK_LATS_LOAD_3D,
    MUSCLE_BACK_LATS_LOAD_7D,
    MUSCLE_SHOULDERS_LOAD_DAILY,
    MUSCLE_SHOULDERS_LOAD_3D,
    MUSCLE_SHOULDERS_LOAD_7D,
    MUSCLE_BICEPS_LOAD_DAILY,
    MUSCLE_BICEPS_LOAD_3D,
    MUSCLE_BICEPS_LOAD_7D,
    MUSCLE_TRICEPS_LOAD_DAILY,
    MUSCLE_TRICEPS_LOAD_3D,
    MUSCLE_TRICEPS_LOAD_7D,
    MUSCLE_FOREARM_GRIP_LOAD_DAILY,
    MUSCLE_FOREARM_GRIP_LOAD_3D,
    MUSCLE_FOREARM_GRIP_LOAD_7D,
    MUSCLE_ANTERIOR_CORE_LOAD_DAILY,
    MUSCLE_ANTERIOR_CORE_LOAD_3D,
    MUSCLE_ANTERIOR_CORE_LOAD_7D,
    MUSCLE_LATERAL_CORE_LOAD_DAILY,
    MUSCLE_LATERAL_CORE_LOAD_3D,
    MUSCLE_LATERAL_CORE_LOAD_7D,
    MUSCLE_ROTATION_CORE_LOAD_DAILY,
    MUSCLE_ROTATION_CORE_LOAD_3D,
    MUSCLE_ROTATION_CORE_LOAD_7D,
    STRENGTH_DELTA_NEXT,
    FATIGUE_DELTA_NEXT,
    STRENGTH_VOLUME_ONLY,
    STRENGTH_INTENSITY_ONLY
}

data class TrendDataPoint(
    val weekStart: LocalDate,
    val value: Double?
)

data class ForecastPoint(
    val weekStart: LocalDate,
    val lower: Double,
    val center: Double,
    val upper: Double
)

data class ForecastRange(
    val points: List<ForecastPoint>,
    val confidence: AnalysisConfidence
)

data class ChartSeries(
    val label: String,
    val points: List<TrendDataPoint>
)

data class BarItem(
    val label: String,
    val value: Double
)

data class PieSlice(
    val label: String,
    val value: Double
)

data class StackedBarSegment(
    val label: String,
    val value: Double
)

data class StackedBarGroup(
    val label: String,
    val segments: List<StackedBarSegment>
)

data class ScatterPoint(
    val x: Double,
    val y: Double,
    val label: String
)

data class ChartSpec(
    val type: ChartType,
    val title: String,
    val lineSeries: List<ChartSeries> = emptyList(),
    val bars: List<BarItem> = emptyList(),
    val stackedBars: List<StackedBarGroup> = emptyList(),
    val slices: List<PieSlice> = emptyList(),
    val scatterPoints: List<ScatterPoint> = emptyList(),
    val forecastRange: ForecastRange? = null,
    val emphasizeValue: Boolean = false,
    val yMin: Double? = null,
    val yMax: Double? = null
) {
    val visibleLineCount: Int
        get() = lineSeries.size
}

data class CompositeTrendSeries(
    val title: String,
    val metricId: TrendMetricId,
    val dataPoints: List<TrendDataPoint>,
    val forecastRange: ForecastRange?,
    val confidence: AnalysisConfidence,
    val dataSufficiency: String,
    val lineCount: Int = 1,
    val emphasizeValue: Boolean = false
)

data class WeeklyTrainingData(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val entries: List<com.training.trackplanner.data.WorkoutEntryWithSets>,
    val dailyMetrics: List<com.training.trackplanner.data.DailyMetric>
)

data class StrengthWeekIndex(
    val weekStart: LocalDate,
    val intensityIndex: Double,
    val volumeIndex: Double,
    val efficiencyIndex: Double,
    val performanceIndex: Double,
    val confidence: AnalysisConfidence,
    val rawVolume: Double,
    val effectiveSets: Int,
    val exerciseScores: Map<Long, Double>,
    val patternVolumes: Map<String, Double>
)

data class BadmintonWeekIndex(
    val weekStart: LocalDate,
    val courtVolumeIndex: Double,
    val footworkReactiveIndex: Double,
    val supportIndex: Double,
    val trainingIndex: Double,
    val confidence: AnalysisConfidence,
    val courtRaw: Double,
    val footworkReactiveRaw: Double,
    val supportRaw: Double,
    val itemScores: Map<Long, Double>
)

data class FatigueWeekIndex(
    val weekStart: LocalDate,
    val systemicGroupScore: Double,
    val strengthGroupScore: Double,
    val badmintonGroupScore: Double,
    val localBodyPartGroupScore: Double,
    val recoveryPerformancePenaltyScore: Double,
    val compositeIndex: Double,
    val confidence: AnalysisConfidence,
    val categoryScores: Map<String, Double>,
    val bodyPartScores: Map<String, Double>
)

data class BadmintonDailyLoadPoint(
    val date: LocalDate,
    val courtRaw: Double,
    val footworkReactiveRaw: Double,
    val supportRaw: Double,
    val methodRaw: Map<String, Double> = emptyMap()
) {
    val totalRaw: Double
        get() = courtRaw + footworkReactiveRaw + supportRaw
}

data class RepRangeWeekShare(
    val weekStart: LocalDate,
    val lowRepShare: Double,
    val moderateRepShare: Double,
    val highRepShare: Double,
    val confirmedSetCount: Int
)

data class PerformanceTrendSummary(
    val strengthPerformanceSeries: CompositeTrendSeries,
    val badmintonTrainingSeries: CompositeTrendSeries,
    val fatigueCompositeSeries: CompositeTrendSeries,
    val forecastRanges: Map<TrendMetricId, ForecastRange>,
    val trendSentence: String,
    val confidence: AnalysisConfidence,
    val detailSections: List<PerformanceDetailSection>,
    val dashboardChartSpecs: List<ChartSpec>,
    val strengthWeeks: List<StrengthWeekIndex> = emptyList(),
    val badmintonWeeks: List<BadmintonWeekIndex> = emptyList(),
    val badmintonDailyLoads: List<BadmintonDailyLoadPoint> = emptyList(),
    val fatigueWeeks: List<FatigueWeekIndex> = emptyList(),
    val repRangeWeeks: List<RepRangeWeekShare> = emptyList(),
    val metricSeries: Map<TrendMetricId, List<TrendDataPoint>> = emptyMap(),
    val badmintonMethodExamples: Map<String, List<String>> = emptyMap(),
    val exerciseDisplayNamesById: Map<Long, String> = emptyMap()
)

data class PerformanceDetailSection(
    val type: PerformanceDetailSectionType,
    val title: String,
    val availableModes: List<DetailChartMode>,
    val selectedMode: DetailChartMode,
    val availableMetrics: List<TrendMetricId>,
    val selectedMetrics: List<TrendMetricId>,
    val chartSpec: ChartSpec,
    val shortInterpretation: String,
    val confidence: AnalysisConfidence
)

data class ScatterAnalysisResult(
    val xMetric: TrendMetricId,
    val yMetric: TrendMetricId,
    val dataPoints: List<ScatterPoint>,
    val correlation: Double?,
    val interpretation: String,
    val confidence: AnalysisConfidence,
    val dataSufficiency: String
)
