package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.features.AnalysisExerciseDisplayNameResolver
import com.training.trackplanner.analysis.readiness.AdaptiveBaselineCalculator
import com.training.trackplanner.analysis.readiness.DailyAnalysisLoadAggregator
import com.training.trackplanner.analysis.readiness.FatiguePressureCalculator
import com.training.trackplanner.analysis.readiness.PainGateEvaluator
import com.training.trackplanner.analysis.readiness.PerformanceDropDetector
import com.training.trackplanner.analysis.readiness.RecoverySignalInterpreter
import com.training.trackplanner.analysis.readiness.ResidualFatigueCalculator
import com.training.trackplanner.analysis.readiness.StatisticalBaselineCalculator
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveExampleResolver
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveStimulusCalculator
import com.training.trackplanner.analysis.badminton.BadmintonPracticeLoadCalculator
import com.training.trackplanner.analysis.badminton.BadmintonPracticeWeekPoint
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.core.CoreStimulusCalculator
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class PerformanceTrendEngine(
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
    private val canonicalCoreCatalog: CanonicalCoreCatalog = CanonicalCoreCatalog.EMPTY,
    private val badmintonObjectiveCatalog: CanonicalBadmintonObjectiveCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
    private val weeklyAggregator: WeeklyAnalysisAggregator = WeeklyAnalysisAggregator(),
    private val strengthCalculator: StrengthPerformanceIndexCalculator =
        StrengthPerformanceIndexCalculator(runtimeMetadataCatalog),
    private val badmintonPracticeCalculator: BadmintonPracticeLoadCalculator =
        BadmintonPracticeLoadCalculator(runtimeMetadataCatalog),
    private val badmintonObjectiveCalculator: BadmintonObjectiveStimulusCalculator =
        BadmintonObjectiveStimulusCalculator(badmintonObjectiveCatalog),
    private val badmintonObjectiveExampleResolver: BadmintonObjectiveExampleResolver =
        BadmintonObjectiveExampleResolver(badmintonObjectiveCatalog),
    private val fatigueCalculator: FatigueCompositeIndexCalculator = FatigueCompositeIndexCalculator(),
    private val forecastCalculator: TrendForecastRangeCalculator = TrendForecastRangeCalculator(),
    private val chartSpecBuilder: PerformanceChartSpecBuilder = PerformanceChartSpecBuilder(),
    private val scatterAnalyzer: ScatterRelationshipAnalyzer = ScatterRelationshipAnalyzer(),
    private val sentenceBuilder: PerformanceTrendSentenceBuilder = PerformanceTrendSentenceBuilder()
) {
    fun analyze(
        today: LocalDate,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        dailyMetrics: List<DailyMetric>,
        canonicalPosteriorIntensity: CanonicalStrengthPosteriorWeeklyIntensity =
            CanonicalStrengthPosteriorWeeklyIntensity.EMPTY
    ): PerformanceTrendSummary {
        val exerciseMap = exercises.associateBy { exercise -> exercise.stableKey }
        val exerciseDisplayNamesByStableKey = exerciseDisplayNamesByStableKey(exercises, entriesWithSets)
        val weeks = weeklyAggregator.aggregate(today, entriesWithSets, dailyMetrics)
        val strengthWeeks = strengthCalculator.calculate(
            weeks,
            exerciseMap,
            dailyMetrics,
            canonicalPosteriorIntensity
        )
        val badmintonPracticeWeeks = badmintonPracticeCalculator.weeklyLoads(weeks, exerciseMap)
        val badmintonPracticeDailyLoads = badmintonPracticeCalculator.dailyLoads(entriesWithSets, exerciseMap)
        val badmintonObjectiveDailyStimulus = badmintonObjectiveCalculator.daily(entriesWithSets, exerciseMap)
        val badmintonObjectiveExamples = badmintonObjectiveExampleResolver.resolve(
            entriesWithSets,
            exerciseMap,
            exerciseDisplayNamesByStableKey
        )
        val coreStimulus = CoreStimulusCalculator(canonicalCoreCatalog).calculate(entriesWithSets, exerciseMap)
        val fatigueWeeks = fatigueWeeks(today, weeks, entriesWithSets, exercises, dailyMetrics)
        val repRangeWeeks = repRangeWeeks(weeks)
        val metricSeries = metricSeries(strengthWeeks, badmintonPracticeWeeks, fatigueWeeks)
        val confidence = TrendMath.combineConfidence(
            listOf(
                strengthWeeks.lastOrNull()?.confidence,
                badmintonPracticeWeeks.lastOrNull()?.confidence,
                fatigueWeeks.lastOrNull()?.confidence
            ).filterNotNull()
        )

        val strengthSeries = compositeSeries(
            title = "근력운동 퍼포먼스",
            metricId = TrendMetricId.STRENGTH_PERFORMANCE,
            points = metricSeries.getValue(TrendMetricId.STRENGTH_PERFORMANCE),
            confidence = strengthWeeks.lastOrNull()?.confidence ?: com.training.trackplanner.analysis.readiness.AnalysisConfidence.LOW
        )
        val badmintonSeries = compositeSeries(
            title = "배드민턴 연습 훈련량",
            metricId = TrendMetricId.BADMINTON_PRACTICE_LOAD,
            points = metricSeries.getValue(TrendMetricId.BADMINTON_PRACTICE_LOAD),
            confidence = badmintonPracticeWeeks.lastOrNull()?.confidence ?: com.training.trackplanner.analysis.readiness.AnalysisConfidence.LOW
        )
        val fatigueSeries = compositeSeries(
            title = "이번 주 누적 부담",
            metricId = TrendMetricId.FATIGUE_COMPOSITE,
            points = metricSeries.getValue(TrendMetricId.FATIGUE_COMPOSITE),
            confidence = fatigueWeeks.lastOrNull()?.confidence ?: com.training.trackplanner.analysis.readiness.AnalysisConfidence.LOW
        )
        val forecasts = listOf(strengthSeries, badmintonSeries, fatigueSeries)
            .mapNotNull { series -> series.forecastRange?.let { range -> series.metricId to range } }
            .toMap()

        val detailSections = detailSections(strengthWeeks, badmintonPracticeWeeks, fatigueWeeks, metricSeries)
        val provisional = PerformanceTrendSummary(
            strengthPerformanceSeries = strengthSeries,
            badmintonPracticeSeries = badmintonSeries,
            fatigueCompositeSeries = fatigueSeries,
            forecastRanges = forecasts,
            trendSentence = sentenceBuilder.dashboardSentence(
                strengthSeries.dataPoints,
                badmintonSeries.dataPoints,
                fatigueSeries.dataPoints,
                confidence
            ),
            confidence = confidence,
            detailSections = detailSections,
            dashboardChartSpecs = emptyList(),
            strengthWeeks = strengthWeeks,
            badmintonPracticeWeeks = badmintonPracticeWeeks,
            badmintonPracticeDailyLoads = badmintonPracticeDailyLoads,
            badmintonObjectiveDailyStimulus = badmintonObjectiveDailyStimulus,
            fatigueWeeks = fatigueWeeks,
            repRangeWeeks = repRangeWeeks,
            metricSeries = metricSeries,
            badmintonObjectiveExamples = badmintonObjectiveExamples,
            coreStimulus = coreStimulus
        )
        return provisional.copy(
            dashboardChartSpecs = chartSpecBuilder.dashboardSpecs(provisional)
        )
    }

    private fun fatigueWeeks(
        today: LocalDate,
        weeks: List<WeeklyTrainingData>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        dailyMetrics: List<DailyMetric>
    ): List<FatigueWeekIndex> {
        val exerciseMap = exercises.associateBy { exercise -> exercise.stableKey }
        val completedEntries = entriesWithSets.filter { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull()
            date != null && date <= today && record.sets.any { set -> set.confirmed }
        }
        val dailyLoads = DailyAnalysisLoadAggregator().aggregate(
            completedEntries,
            exerciseMap,
            runtimeMetadataCatalog,
            dailyMetrics
        )
        return weeks.map { week ->
            val loadsUntilWeek = dailyLoads.filter { load -> load.date <= week.weekEnd }
            val entriesUntilWeek = completedEntries.filter { record -> LocalDate.parse(record.entry.date) <= week.weekEnd }
            val metricsUntilWeek = dailyMetrics.filter { metric ->
                runCatching { LocalDate.parse(metric.date) }.getOrNull()?.let { date -> date <= week.weekEnd } == true
            }
            val residual = ResidualFatigueCalculator().calculate(loadsUntilWeek, week.weekEnd)
            val stats = StatisticalBaselineCalculator().calculate(loadsUntilWeek, residual, week.weekEnd)
            val adaptive = AdaptiveBaselineCalculator().calculate(loadsUntilWeek, stats)
            val pressure = FatiguePressureCalculator().calculate(residual, stats, adaptive)
            val recovery = RecoverySignalInterpreter().interpret(week.weekEnd, metricsUntilWeek)
            val performance = PerformanceDropDetector().detect(
                entriesUntilWeek,
                exerciseMap,
                week.weekEnd,
                runtimeMetadataCatalog
            )
            val pain = PainGateEvaluator().evaluate(week.weekEnd, emptyList())
            fatigueCalculator.calculate(
                weekStart = week.weekStart,
                pressure = pressure,
                recovery = recovery,
                performance = performance,
                pain = pain
            )
        }
    }

    private fun compositeSeries(
        title: String,
        metricId: TrendMetricId,
        points: List<TrendDataPoint>,
        confidence: com.training.trackplanner.analysis.readiness.AnalysisConfidence
    ): CompositeTrendSeries =
        CompositeTrendSeries(
            title = title,
            metricId = metricId,
            dataPoints = points,
            forecastRange = forecastCalculator.calculate(points, confidence),
            confidence = confidence,
            dataSufficiency = dataSufficiency(points),
            lineCount = 1,
            emphasizeValue = false
        )

    private fun metricSeries(
        strengthWeeks: List<StrengthWeekIndex>,
        badmintonPracticeWeeks: List<BadmintonPracticeWeekPoint>,
        fatigueWeeks: List<FatigueWeekIndex>
    ): Map<TrendMetricId, List<TrendDataPoint>> {
        val strengthPerformance = strengthWeeks.map { week -> TrendDataPoint(week.weekStart, week.performanceIndex) }
        val badmintonPractice = badmintonPracticeWeeks.map { week -> TrendDataPoint(week.weekStart, week.practiceLoad) }
        val fatigueComposite = fatigueWeeks.map { week -> TrendDataPoint(week.weekStart, week.compositeIndex) }
        return mapOf(
            TrendMetricId.STRENGTH_PERFORMANCE to strengthPerformance,
            TrendMetricId.STRENGTH_INTENSITY to strengthWeeks.map { week -> TrendDataPoint(week.weekStart, week.intensityIndex) },
            TrendMetricId.STRENGTH_VOLUME to strengthWeeks.map { week -> TrendDataPoint(week.weekStart, week.volumeIndex) },
            TrendMetricId.STRENGTH_EFFICIENCY to strengthWeeks.map { week -> TrendDataPoint(week.weekStart, week.efficiencyIndex) },
            TrendMetricId.BADMINTON_PRACTICE_LOAD to badmintonPractice,
            TrendMetricId.FATIGUE_COMPOSITE to fatigueComposite,
            TrendMetricId.SYSTEMIC_FATIGUE to fatigueWeeks.map { week -> TrendDataPoint(week.weekStart, week.systemicGroupScore) },
            TrendMetricId.STRENGTH_FATIGUE to fatigueWeeks.map { week -> TrendDataPoint(week.weekStart, week.strengthGroupScore) },
            TrendMetricId.BADMINTON_FATIGUE to fatigueWeeks.map { week -> TrendDataPoint(week.weekStart, week.badmintonGroupScore) },
            TrendMetricId.LOCAL_BODY_PART_FATIGUE to fatigueWeeks.map { week -> TrendDataPoint(week.weekStart, week.localBodyPartGroupScore) },
            TrendMetricId.RECOVERY_PERFORMANCE_PENALTY to fatigueWeeks.map { week -> TrendDataPoint(week.weekStart, week.recoveryPerformancePenaltyScore) },
            TrendMetricId.STRENGTH_DELTA_NEXT to nextDelta(strengthPerformance),
            TrendMetricId.FATIGUE_DELTA_NEXT to nextDelta(fatigueComposite),
            TrendMetricId.STRENGTH_VOLUME_ONLY to strengthWeeks.map { week -> TrendDataPoint(week.weekStart, week.volumeIndex) },
            TrendMetricId.STRENGTH_INTENSITY_ONLY to strengthWeeks.map { week -> TrendDataPoint(week.weekStart, week.intensityIndex) }
        )
    }

    private fun repRangeWeeks(weeks: List<WeeklyTrainingData>): List<RepRangeWeekShare> =
        weeks.map { week ->
            val reps = week.entries
                .flatMap { record -> record.sets }
                .filter { set -> set.confirmed && set.reps > 0 }
                .map { set -> set.reps }
            val total = reps.size
            if (total == 0) {
                RepRangeWeekShare(week.weekStart, 0.0, 0.0, 0.0, 0)
            } else {
                RepRangeWeekShare(
                    weekStart = week.weekStart,
                    lowRepShare = reps.count { it in 1..5 }.toDouble() / total * 100.0,
                    moderateRepShare = reps.count { it in 6..9 }.toDouble() / total * 100.0,
                    highRepShare = reps.count { it >= 10 }.toDouble() / total * 100.0,
                    confirmedSetCount = total
                )
            }
        }.dropWhile { week -> week.confirmedSetCount == 0 }
            .dropLastWhile { week -> week.confirmedSetCount == 0 }

    private fun nextDelta(points: List<TrendDataPoint>): List<TrendDataPoint> =
        points.mapIndexed { index, point ->
            val next = points.getOrNull(index + 1)?.value
            val current = point.value
            TrendDataPoint(
                weekStart = point.weekStart,
                value = if (current != null && next != null) next - current else null
            )
        }

    private fun detailSections(
        strengthWeeks: List<StrengthWeekIndex>,
        badmintonPracticeWeeks: List<BadmintonPracticeWeekPoint>,
        fatigueWeeks: List<FatigueWeekIndex>,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
    ): List<PerformanceDetailSection> {
        val scatter = scatterAnalyzer.analyze(
            TrendMetricId.BADMINTON_PRACTICE_LOAD,
            TrendMetricId.FATIGUE_COMPOSITE,
            metricSeries
        )
        return listOf(
            PerformanceDetailSection(
                type = PerformanceDetailSectionType.STRENGTH,
                title = "근력운동 해설",
                availableModes = listOf(DetailChartMode.TREND, DetailChartMode.COMPOSITION, DetailChartMode.CONTRIBUTION),
                selectedMode = DetailChartMode.TREND,
                availableMetrics = listOf(TrendMetricId.STRENGTH_INTENSITY, TrendMetricId.STRENGTH_VOLUME, TrendMetricId.STRENGTH_EFFICIENCY),
                selectedMetrics = listOf(TrendMetricId.STRENGTH_INTENSITY),
                chartSpec = chartSpecBuilder.strengthDetail(DetailChartMode.TREND, listOf(TrendMetricId.STRENGTH_INTENSITY), strengthWeeks),
                shortInterpretation = sentenceBuilder.strengthInterpretation(strengthWeeks.lastOrNull()),
                confidence = strengthWeeks.lastOrNull()?.confidence ?: com.training.trackplanner.analysis.readiness.AnalysisConfidence.LOW
            ),
            PerformanceDetailSection(
                type = PerformanceDetailSectionType.BADMINTON,
                title = "배드민턴 훈련 해설",
                availableModes = listOf(DetailChartMode.TREND),
                selectedMode = DetailChartMode.TREND,
                availableMetrics = listOf(TrendMetricId.BADMINTON_PRACTICE_LOAD),
                selectedMetrics = listOf(TrendMetricId.BADMINTON_PRACTICE_LOAD),
                chartSpec = chartSpecBuilder.badmintonPracticeDetail(badmintonPracticeWeeks),
                shortInterpretation = sentenceBuilder.badmintonInterpretation(badmintonPracticeWeeks.lastOrNull()),
                confidence = badmintonPracticeWeeks.lastOrNull()?.confidence ?: com.training.trackplanner.analysis.readiness.AnalysisConfidence.LOW
            ),
            PerformanceDetailSection(
                type = PerformanceDetailSectionType.FATIGUE,
                title = "피로도 해설",
                availableModes = listOf(DetailChartMode.TREND, DetailChartMode.COMPOSITION, DetailChartMode.CONTRIBUTION),
                selectedMode = DetailChartMode.TREND,
                availableMetrics = listOf(
                    TrendMetricId.SYSTEMIC_FATIGUE,
                    TrendMetricId.STRENGTH_FATIGUE,
                    TrendMetricId.BADMINTON_FATIGUE,
                    TrendMetricId.LOCAL_BODY_PART_FATIGUE,
                    TrendMetricId.RECOVERY_PERFORMANCE_PENALTY
                ),
                selectedMetrics = listOf(TrendMetricId.SYSTEMIC_FATIGUE),
                chartSpec = chartSpecBuilder.fatigueDetail(DetailChartMode.TREND, listOf(TrendMetricId.SYSTEMIC_FATIGUE), fatigueWeeks),
                shortInterpretation = sentenceBuilder.fatigueInterpretation(fatigueWeeks.lastOrNull()),
                confidence = fatigueWeeks.lastOrNull()?.confidence ?: com.training.trackplanner.analysis.readiness.AnalysisConfidence.LOW
            ),
            PerformanceDetailSection(
                type = PerformanceDetailSectionType.RELATIONSHIP,
                title = "관계 분석",
                availableModes = listOf(DetailChartMode.RELATIONSHIP),
                selectedMode = DetailChartMode.RELATIONSHIP,
                availableMetrics = listOf(TrendMetricId.BADMINTON_PRACTICE_LOAD, TrendMetricId.FATIGUE_COMPOSITE),
                selectedMetrics = listOf(TrendMetricId.BADMINTON_PRACTICE_LOAD, TrendMetricId.FATIGUE_COMPOSITE),
                chartSpec = chartSpecBuilder.scatterSpec(scatter),
                shortInterpretation = scatter.interpretation,
                confidence = scatter.confidence
            )
        )
    }

    private fun dataSufficiency(points: List<TrendDataPoint>): String {
        val count = points.count { point -> point.value != null }
        return when {
            count < 2 -> "차트 제한"
            count < 4 -> "예상 범위 숨김"
            count < 8 -> "보수적 해석"
            else -> "추세 표시 가능"
        }
    }

    private fun exerciseDisplayNamesByStableKey(
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>
    ): Map<String, String> {
        val exerciseMap = exercises.associateBy { exercise -> exercise.stableKey }
        val defaults = exercises.associate { exercise ->
            exercise.stableKey to AnalysisExerciseDisplayNameResolver.resolve(null, exercise, runtimeMetadataCatalog)
        }
        val fromEntries = entriesWithSets.mapNotNull { record ->
            val exercise = exerciseMap[record.entry.exerciseStableKey] ?: return@mapNotNull null
            exercise.stableKey to AnalysisExerciseDisplayNameResolver.resolve(record.entry, exercise, runtimeMetadataCatalog)
        }.toMap()
        return defaults + fromEntries
    }
}
