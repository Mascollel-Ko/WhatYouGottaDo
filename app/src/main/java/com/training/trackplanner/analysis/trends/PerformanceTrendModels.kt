package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import java.time.LocalDate

enum class ChartType {
    LINE,
    BAR,
    HORIZONTAL_BAR,
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
    val slices: List<PieSlice> = emptyList(),
    val scatterPoints: List<ScatterPoint> = emptyList(),
    val forecastRange: ForecastRange? = null,
    val emphasizeValue: Boolean = false
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
    val fatigueWeeks: List<FatigueWeekIndex> = emptyList(),
    val metricSeries: Map<TrendMetricId, List<TrendDataPoint>> = emptyMap()
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
