package com.training.trackplanner.analysis.trends

class PerformanceChartSpecBuilder {
    fun dashboardSpecs(summary: PerformanceTrendSummary): List<ChartSpec> =
        listOf(
            dashboardLineSpec(summary.strengthPerformanceSeries),
            dashboardLineSpec(summary.badmintonTrainingSeries),
            dashboardLineSpec(summary.fatigueCompositeSeries)
        )

    fun dashboardLineSpec(series: CompositeTrendSeries): ChartSpec =
        ChartSpec(
            type = ChartType.LINE,
            title = series.title,
            lineSeries = listOf(ChartSeries(series.title, series.dataPoints)),
            forecastRange = series.forecastRange,
            emphasizeValue = false,
            timeGranularity = ChartTimeGranularity.WEEKLY
        )

    fun strengthDetail(
        mode: DetailChartMode,
        selectedMetrics: List<TrendMetricId>,
        strengthWeeks: List<StrengthWeekIndex>
    ): ChartSpec {
        val sanitized = DetailChartSelector.sanitizeSelection(
            mode,
            selectedMetrics,
            listOf(TrendMetricId.STRENGTH_INTENSITY)
        )
        return when (mode) {
            DetailChartMode.TREND -> ChartSpec(
                type = ChartType.LINE,
                title = "근력운동 해설",
                lineSeries = sanitized.map { metric ->
                    ChartSeries(metric.label(), strengthWeeks.map { week -> TrendDataPoint(week.weekStart, week.valueFor(metric)) })
                },
                timeGranularity = ChartTimeGranularity.WEEKLY
            )
            DetailChartMode.COMPOSITION -> compositionSpec(
                title = "강도/수행량/RPE 대비 운동량 구성",
                items = latestStrengthComposition(strengthWeeks)
            )
            DetailChartMode.CONTRIBUTION -> barSpec(
                title = "최근 변화 기여",
                items = strengthContribution(strengthWeeks)
            )
            DetailChartMode.RANKING -> horizontalBarSpec(
                title = "최근 패턴별 볼륨",
                items = strengthWeeks.lastOrNull()?.patternVolumes
                    ?.entries
                    ?.sortedByDescending { entry -> entry.value }
                    ?.take(6)
                    ?.map { entry -> BarItem(entry.key, entry.value) }
                    .orEmpty()
            )
            DetailChartMode.RELATIONSHIP -> ChartSpec(ChartType.SCATTER, "관계 분석")
        }
    }

    fun badmintonDetail(
        mode: DetailChartMode,
        selectedMetrics: List<TrendMetricId>,
        badmintonWeeks: List<BadmintonWeekIndex>,
        exerciseDisplayNamesById: Map<Long, String> = emptyMap()
    ): ChartSpec {
        val sanitized = DetailChartSelector.sanitizeSelection(
            mode,
            selectedMetrics,
            listOf(TrendMetricId.COURT_VOLUME)
        )
        return when (mode) {
            DetailChartMode.TREND -> ChartSpec(
                type = ChartType.LINE,
                title = "배드민턴 훈련 해설",
                lineSeries = sanitized.map { metric ->
                    ChartSeries(metric.label(), badmintonWeeks.map { week -> TrendDataPoint(week.weekStart, week.valueFor(metric)) })
                },
                timeGranularity = ChartTimeGranularity.WEEKLY
            )
            DetailChartMode.COMPOSITION -> compositionSpec(
                title = "배드민턴 훈련 구성",
                items = latestBadmintonComposition(badmintonWeeks)
            )
            DetailChartMode.CONTRIBUTION -> barSpec(
                title = "최근 변화 기여",
                items = badmintonContribution(badmintonWeeks)
            )
            DetailChartMode.RANKING -> horizontalBarSpec(
                title = "최근 관련 훈련",
                items = badmintonWeeks.lastOrNull()?.itemScores
                    ?.entries
                    ?.sortedByDescending { entry -> entry.value }
                    ?.take(6)
                    ?.map { entry -> BarItem(exerciseDisplayNamesById[entry.key] ?: "운동 ${entry.key}", entry.value) }
                    .orEmpty()
            )
            DetailChartMode.RELATIONSHIP -> ChartSpec(ChartType.SCATTER, "관계 분석")
        }
    }

    fun fatigueDetail(
        mode: DetailChartMode,
        selectedMetrics: List<TrendMetricId>,
        fatigueWeeks: List<FatigueWeekIndex>
    ): ChartSpec {
        val sanitized = DetailChartSelector.sanitizeSelection(
            mode,
            selectedMetrics,
            listOf(TrendMetricId.SYSTEMIC_FATIGUE)
        )
        return when (mode) {
            DetailChartMode.TREND -> ChartSpec(
                type = ChartType.LINE,
                title = "피로도 해설",
                lineSeries = sanitized.map { metric ->
                    ChartSeries(metric.label(), fatigueWeeks.map { week -> TrendDataPoint(week.weekStart, week.valueFor(metric)) })
                },
                timeGranularity = ChartTimeGranularity.WEEKLY
            )
            DetailChartMode.COMPOSITION -> compositionSpec(
                title = "현재 피로도 구성",
                items = latestFatigueComposition(fatigueWeeks)
            )
            DetailChartMode.CONTRIBUTION -> barSpec(
                title = "최근 피로도 변화 기여",
                items = fatigueContribution(fatigueWeeks)
            )
            DetailChartMode.RANKING -> horizontalBarSpec(
                title = "현재 부담 상위 부위",
                items = fatigueWeeks.lastOrNull()?.bodyPartScores
                    ?.entries
                    ?.sortedByDescending { entry -> entry.value }
                    ?.take(6)
                    ?.map { entry -> BarItem(entry.key, entry.value) }
                    .orEmpty()
            )
            DetailChartMode.RELATIONSHIP -> ChartSpec(ChartType.SCATTER, "관계 분석")
        }
    }

    fun scatterSpec(result: ScatterAnalysisResult): ChartSpec =
        ChartSpec(
            type = ChartType.SCATTER,
            title = "관계 분석",
            scatterPoints = result.dataPoints
        )

    private fun compositionSpec(title: String, items: List<BarItem>): ChartSpec =
        horizontalBarSpec(title, items)

    private fun barSpec(title: String, items: List<BarItem>): ChartSpec =
        ChartSpec(type = ChartType.BAR, title = title, bars = items)

    private fun horizontalBarSpec(title: String, items: List<BarItem>): ChartSpec =
        ChartSpec(type = ChartType.HORIZONTAL_BAR, title = title, bars = items)

    private fun latestStrengthComposition(weeks: List<StrengthWeekIndex>): List<BarItem> {
        val latest = weeks.lastOrNull() ?: return emptyList()
        val components = listOf(
            "강도" to PerformanceTrendConstants.STRENGTH_PERFORMANCE_INTENSITY_WEIGHT * latest.intensityIndex,
            "수행량" to PerformanceTrendConstants.STRENGTH_PERFORMANCE_VOLUME_WEIGHT * latest.volumeIndex,
            "RPE 대비 운동량" to PerformanceTrendConstants.STRENGTH_PERFORMANCE_EFFICIENCY_WEIGHT * latest.efficiencyIndex
        )
        return shareItems(components)
    }

    private fun latestBadmintonComposition(weeks: List<BadmintonWeekIndex>): List<BarItem> {
        val latest = weeks.lastOrNull() ?: return emptyList()
        val components = listOf(
            "셔틀 플레이" to PerformanceTrendConstants.BADMINTON_COURT_WEIGHT * latest.courtVolumeIndex,
            "풋워크/반응" to PerformanceTrendConstants.BADMINTON_FOOTWORK_WEIGHT * latest.footworkReactiveIndex,
            "보조훈련" to PerformanceTrendConstants.BADMINTON_SUPPORT_WEIGHT * latest.supportIndex
        )
        return shareItems(components)
    }

    private fun latestFatigueComposition(weeks: List<FatigueWeekIndex>): List<BarItem> {
        val latest = weeks.lastOrNull() ?: return emptyList()
        val components = listOf(
            "전신" to latest.systemicGroupScore,
            "근력운동" to latest.strengthGroupScore,
            "배드민턴" to latest.badmintonGroupScore,
            "국소/부위" to latest.localBodyPartGroupScore,
            "회복/수행" to latest.recoveryPerformancePenaltyScore
        )
        return shareItems(components)
    }

    private fun strengthContribution(weeks: List<StrengthWeekIndex>): List<BarItem> {
        if (weeks.size < 2) return emptyList()
        val current = weeks.takeLast(4)
        val previous = weeks.dropLast(4).takeLast(4)
        return listOf(
            contributionItem("강도", current.map { it.intensityIndex }, previous.map { it.intensityIndex }, 0.50),
            contributionItem("수행량", current.map { it.volumeIndex }, previous.map { it.volumeIndex }, 0.40),
            contributionItem("RPE 대비 운동량", current.map { it.efficiencyIndex }, previous.map { it.efficiencyIndex }, 0.10)
        )
    }

    private fun badmintonContribution(weeks: List<BadmintonWeekIndex>): List<BarItem> {
        if (weeks.size < 2) return emptyList()
        val current = weeks.takeLast(4)
        val previous = weeks.dropLast(4).takeLast(4)
        return listOf(
            contributionItem("셔틀 플레이", current.map { it.courtVolumeIndex }, previous.map { it.courtVolumeIndex }, 0.60),
            contributionItem("풋워크", current.map { it.footworkReactiveIndex }, previous.map { it.footworkReactiveIndex }, 0.25),
            contributionItem("보조", current.map { it.supportIndex }, previous.map { it.supportIndex }, 0.15)
        )
    }

    private fun fatigueContribution(weeks: List<FatigueWeekIndex>): List<BarItem> {
        if (weeks.size < 2) return emptyList()
        val current = weeks.takeLast(4)
        val previous = weeks.dropLast(4).takeLast(4)
        return listOf(
            contributionItem("전신", current.map { it.systemicGroupScore }, previous.map { it.systemicGroupScore }, 0.15),
            contributionItem("근력운동", current.map { it.strengthGroupScore }, previous.map { it.strengthGroupScore }, 0.15),
            contributionItem("배드민턴", current.map { it.badmintonGroupScore }, previous.map { it.badmintonGroupScore }, 0.15),
            contributionItem("국소/부위", current.map { it.localBodyPartGroupScore }, previous.map { it.localBodyPartGroupScore }, 0.15),
            contributionItem("회복/수행", current.map { it.recoveryPerformancePenaltyScore }, previous.map { it.recoveryPerformancePenaltyScore }, 0.15)
        )
    }

    private fun contributionItem(
        label: String,
        current: List<Double>,
        previous: List<Double>,
        weight: Double
    ): BarItem =
        BarItem(
            label = label,
            value = weight * (TrendMath.mean(current) - TrendMath.mean(previous))
        )

    private fun shareItems(components: List<Pair<String, Double>>): List<BarItem> {
        val total = components.sumOf { (_, value) -> value }.takeIf { it > PerformanceTrendConstants.EPSILON }
            ?: return components.map { (label, _) -> BarItem(label, 0.0) }
        return components.map { (label, value) -> BarItem(label, value / total * 100.0) }
    }

    private fun StrengthWeekIndex.valueFor(metric: TrendMetricId): Double =
        when (metric) {
            TrendMetricId.STRENGTH_INTENSITY,
            TrendMetricId.STRENGTH_INTENSITY_ONLY -> intensityIndex
            TrendMetricId.STRENGTH_VOLUME,
            TrendMetricId.STRENGTH_VOLUME_ONLY -> volumeIndex
            TrendMetricId.STRENGTH_EFFICIENCY -> efficiencyIndex
            else -> performanceIndex
        }

    private fun BadmintonWeekIndex.valueFor(metric: TrendMetricId): Double =
        when (metric) {
            TrendMetricId.COURT_VOLUME -> courtVolumeIndex
            TrendMetricId.FOOTWORK_REACTIVE -> footworkReactiveIndex
            TrendMetricId.BADMINTON_SUPPORT -> supportIndex
            else -> trainingIndex
        }

    private fun FatigueWeekIndex.valueFor(metric: TrendMetricId): Double =
        when (metric) {
            TrendMetricId.SYSTEMIC_FATIGUE -> systemicGroupScore
            TrendMetricId.STRENGTH_FATIGUE -> strengthGroupScore
            TrendMetricId.BADMINTON_FATIGUE -> badmintonGroupScore
            TrendMetricId.LOCAL_BODY_PART_FATIGUE -> localBodyPartGroupScore
            TrendMetricId.RECOVERY_PERFORMANCE_PENALTY -> recoveryPerformancePenaltyScore
            else -> compositeIndex
        }
}
