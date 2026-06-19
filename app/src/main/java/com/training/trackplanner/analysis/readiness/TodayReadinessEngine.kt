package com.training.trackplanner.analysis.readiness

import java.time.LocalDateTime

class TodayReadinessEngine(
    private val aggregator: DailyAnalysisLoadAggregator = DailyAnalysisLoadAggregator(),
    private val residualCalculator: ResidualFatigueCalculator = ResidualFatigueCalculator(),
    private val statisticalBaselineCalculator: StatisticalBaselineCalculator = StatisticalBaselineCalculator(),
    private val adaptiveBaselineCalculator: AdaptiveBaselineCalculator = AdaptiveBaselineCalculator(),
    private val pressureCalculator: FatiguePressureCalculator = FatiguePressureCalculator(),
    private val recoverySignalInterpreter: RecoverySignalInterpreter = RecoverySignalInterpreter(),
    private val performanceDropDetector: PerformanceDropDetector = PerformanceDropDetector(),
    private val painGateEvaluator: PainGateEvaluator = PainGateEvaluator(),
    private val decisionEngine: TodayReadinessDecisionEngine = TodayReadinessDecisionEngine(),
    private val detailSectionBuilder: FatigueDetailSectionBuilder = FatigueDetailSectionBuilder(),
    private val initialProfileAdjuster: InitialProfileReadinessAdjuster = InitialProfileReadinessAdjuster()
) {
    fun analyze(input: TodayReadinessEngineInput): TodayReadinessSummary {
        val exerciseMap = input.exercises.associateBy { exercise -> exercise.id }
        val completedEntriesUntilToday = input.entriesWithSets.filter { record ->
            val date = runCatching { java.time.LocalDate.parse(record.entry.date) }.getOrNull()
            date != null && date <= input.today && record.sets.any { set -> set.confirmed }
        }
        val dailyLoads = aggregator.aggregate(
            entriesWithSets = completedEntriesUntilToday,
            exerciseMap = exerciseMap,
            runtimeMetadataCatalog = input.runtimeMetadataCatalog
        )
        val residual = residualCalculator.calculate(dailyLoads, input.today)
        val statisticalBaseline = statisticalBaselineCalculator.calculate(
            dailyLoads = dailyLoads,
            residual = residual,
            today = input.today
        )
        val adaptiveSignals = input.adaptiveOutcomeSignals.ifEmpty {
            outcomeSignalsFromMetrics(dailyLoads, input.dailyMetrics)
        }
        val adaptiveBaseline = adaptiveBaselineCalculator.calculate(
            dailyLoads = dailyLoads,
            stats = statisticalBaseline,
            outcomeSignals = adaptiveSignals
        )
        val readinessBaseline = initialProfileAdjuster.adjustBaseline(
            residual = residual,
            adaptiveBaseline = adaptiveBaseline,
            today = input.today,
            dailyLoads = dailyLoads,
            initialProfile = input.initialProfile
        )
        val pressure = pressureCalculator.calculate(
            residual = residual,
            stats = statisticalBaseline,
            adaptiveBaseline = readinessBaseline
        )
        val recovery = recoverySignalInterpreter.interpret(
            today = input.today,
            dailyMetrics = input.dailyMetrics
        )
        val performance = performanceDropDetector.detect(
            entriesWithSets = completedEntriesUntilToday,
            exerciseMap = exerciseMap,
            today = input.today,
            runtimeMetadataCatalog = input.runtimeMetadataCatalog
        )
        val pain = painGateEvaluator.evaluate(
            today = input.today,
            painInputs = input.painInputs
        )
        val decision = decisionEngine.decide(
            pressure = pressure,
            recovery = recovery,
            performance = performance,
            pain = pain,
            adaptiveBaseline = readinessBaseline
        )
        val detailSections = detailSectionBuilder.build(
            pressure = pressure,
            recovery = recovery,
            performance = performance,
            pain = pain,
            adaptiveBaseline = readinessBaseline
        )

        val summary = TodayReadinessSummary(
            status = decision.status,
            headline = decision.sentence.headline,
            shortReason = decision.sentence.shortReason,
            primaryReasons = decision.sentence.primaryReasons,
            recommendedModes = decision.sentence.recommendedModes,
            restrictedModes = decision.sentence.restrictedModes,
            confidence = decision.confidence,
            detailSections = detailSections,
            adaptiveBaselineNotes = decision.sentence.adaptiveBaselineNotes,
            generatedAt = LocalDateTime.now()
        )
        return initialProfileAdjuster.adjustSummary(
            summary = summary,
            today = input.today,
            dailyLoads = dailyLoads,
            pressure = pressure,
            initialProfile = input.initialProfile
        )
    }

    private fun outcomeSignalsFromMetrics(
        dailyLoads: List<DailyAnalysisLoad>,
        dailyMetrics: List<com.training.trackplanner.data.DailyMetric>
    ): List<AdaptiveOutcomeSignal> {
        if (dailyMetrics.isEmpty()) return emptyList()
        val metricsByDate = dailyMetrics.associateBy { metric -> metric.date }
        return dailyLoads.flatMap { daily ->
            daily.categoryLoads.keys.mapNotNull { category ->
                val nextThreeDays = (1..3).map { offset -> daily.date.plusDays(offset.toLong()).toString() }
                val nextMetrics = nextThreeDays.mapNotNull { date -> metricsByDate[date] }
                if (nextMetrics.isEmpty()) return@mapNotNull null
                val sleepStable = nextMetrics.mapNotNull { metric -> metric.sleepHours }.all { hours -> hours >= 6.5 }
                AdaptiveOutcomeSignal(
                    date = daily.date,
                    category = category,
                    recoveryStable = sleepStable,
                    painPresent = false,
                    performanceDrop = false,
                    fatigueIncrease = !sleepStable
                )
            }
        }
    }
}
