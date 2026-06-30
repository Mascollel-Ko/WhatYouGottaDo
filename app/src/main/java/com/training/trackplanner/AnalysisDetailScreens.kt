package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.coach.CoachAnalysisInsightSummary
import com.training.trackplanner.analysis.coach.CoachFatigueCauseSummary
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget
import com.training.trackplanner.analysis.fatigue.ui.FatigueAnalysisSection
import com.training.trackplanner.analysis.lab.AnalysisMetricRegistry
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.trends.BarItem
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.RepRangeWeekShare
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.analysis.trends.label

@Composable
internal fun FatigueAndConditionAnalysisContent(
    readiness: TodayReadinessSummary?,
    todayStatus: PhaseAwareTodayStatus?,
    fatigueAnalysis: FatigueAnalysisUiState,
    coachInsight: CoachAnalysisInsightSummary,
    coachingSignals: CoachingSignalsSummary,
    performanceTrend: PerformanceTrendSummary?,
    onPeriodChange: (FatigueAnalysisPeriod) -> Unit,
    onFatigueTargetToggle: (FatigueTarget) -> Unit,
    onContributionTargetChange: (FatigueTarget) -> Unit,
    onContributionGroupingChange: (ContributionGrouping) -> Unit,
    onContributionSourcesApply: (Set<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        todayStatus?.let { TodayReadinessCard(it) }
            ?: readiness?.let { TodayReadinessCard(it) }
            ?: InfoCard("오늘 상태를 계산하고 있습니다.")
        FatigueAxisCauseCard(fatigueAnalysis, coachInsight.fatigueCauses, coachInsight.combinedHeadline, coachInsight.checkInGuidance)
        CoachingSignalsCard(coachingSignals)
        FatigueAnalysisSection(
            state = fatigueAnalysis,
            onPeriodChange = onPeriodChange,
            onFatigueTargetToggle = onFatigueTargetToggle,
            onContributionTargetChange = onContributionTargetChange,
            onContributionGroupingChange = onContributionGroupingChange,
            onContributionSourcesApply = onContributionSourcesApply
        )
        performanceTrend?.let { summary ->
            AnalysisSectionChart(
                title = "피로도 종합지수(주별)",
                spec = ChartSpec(
                    type = ChartType.LINE,
                    title = "피로도 종합지수(주별)",
                    lineSeries = listOf(ChartSeries("피로도 종합지수", summary.fatigueCompositeSeries.dataPoints))
                ),
                note = "주 단위로 집계한 종합 피로 흐름입니다."
            )
        } ?: InfoCard("피로도 추세를 계산하고 있습니다.")
    }
}

@Composable
internal fun BadmintonTransferAnalysisContent(
    coachInsight: CoachAnalysisInsightSummary,
    badmintonTransfer: BadmintonTransferSummary?,
    performanceTrend: PerformanceTrendSummary?
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BadmintonTransferCoverageSentenceCard(coachInsight)
        badmintonTransfer?.let { BadmintonTransferCard(it) }
            ?: InfoCard("배드민턴 전이 분석을 계산하고 있습니다.")
        performanceTrend?.let { BadmintonTrainingLoadCharts(it) }
            ?: InfoCard("배드민턴 훈련량 추세를 계산하고 있습니다.")
    }
}

@Composable
internal fun StrengthTrendAnalysisContent(performanceTrend: PerformanceTrendSummary?) {
    if (performanceTrend == null) {
        InfoCard("근력운동 추이를 계산하고 있습니다.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MainLiftE1rmCard(performanceTrend)
        MuscleLoadShareCard(performanceTrend)
        MuscleLoadShareTrendCard(performanceTrend)
        RepRangeShareCard(performanceTrend.repRangeWeeks)
        RepRangeShareTrendCard(performanceTrend.repRangeWeeks)
    }
}

@Composable
private fun FatigueAxisCauseCard(
    fatigueAnalysis: FatigueAnalysisUiState,
    summary: CoachFatigueCauseSummary,
    combinedHeadline: String?,
    checkInGuidance: List<String>
) {
    val latestByAxis = fatigueAnalysis.detail.fatigueTrendSeries
        .filter { series -> series.key != FatigueTarget.OVERALL.name }
        .associate { series -> series.label to series.points.lastOrNull()?.value }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("피로도별 현재 상태와 주요 기여 운동", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                combinedHeadline ?: summary.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            checkInGuidance.take(2).forEach { guidance ->
                Text(guidance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FatigueTarget.entries.filter { it != FatigueTarget.OVERALL }.forEach { target ->
                val score = latestByAxis[target.label]
                val causes = summary.causes
                    .filter { cause -> target.label in cause.affectedAxes }
                    .take(2)
                    .map { cause -> cause.label }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(target.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(fatigueScoreLabel(score), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "주요 기여 운동: ${causes.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "최근 기여 운동 없음"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun fatigueScoreLabel(score: Double?): String = when {
    score == null -> "기록 부족"
    score >= 70.0 -> "현재 높음"
    score >= 45.0 -> "보통"
    else -> "낮음"
}

@Composable
private fun BadmintonTransferCoverageSentenceCard(insight: CoachAnalysisInsightSummary) {
    val summary = insight.transferCoverage
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(summary.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!summary.isDataSufficient) return@Column
            if (summary.lowAxes.isNotEmpty()) {
                Text("부족축", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                summary.lowAxes.take(4).forEach { TransferAxisRow(it) }
            }
            if (summary.cautionAxes.isNotEmpty()) {
                Text("과잉·주의축", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                summary.cautionAxes.take(3).forEach { TransferAxisRow(it) }
            }
        }
    }
}

@Composable
private fun BadmintonTrainingLoadCharts(summary: PerformanceTrendSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnalysisSectionChart(
            title = "배드민턴 관련 훈련량(일별)",
            spec = ChartSpec(
                type = ChartType.LINE,
                title = "배드민턴 관련 훈련량(일별)",
                lineSeries = listOf(
                    ChartSeries(
                        "일별 총량",
                        summary.badmintonDailyLoads.map { point -> TrendDataPoint(point.date, point.totalRaw) }
                    )
                )
            ),
            note = "확인된 기록만 사용한 일별 관련 훈련량입니다."
        )
        AnalysisSectionChart(
            title = "배드민턴 관련 훈련량(주별)",
            spec = ChartSpec(
                type = ChartType.LINE,
                title = "배드민턴 관련 훈련량(주별)",
                lineSeries = listOf(
                    ChartSeries("직접 배드민턴 훈련량", summary.badmintonWeeks.map { week -> TrendDataPoint(week.weekStart, week.courtRaw) }),
                    ChartSeries("풋워크/반응 훈련량", summary.badmintonWeeks.map { week -> TrendDataPoint(week.weekStart, week.footworkReactiveRaw) }),
                    ChartSeries("배드민턴 전이 훈련량", summary.badmintonWeeks.map { week -> TrendDataPoint(week.weekStart, week.supportRaw) })
                )
            ),
            note = "월별이 아니라 각 주마다 하나의 포인트를 찍는 주별 차트입니다."
        )
    }
}

@Composable
private fun MainLiftE1rmCard(summary: PerformanceTrendSummary) {
    AnalysisSectionChart(
        title = "주요 운동 e1RM",
        spec = metricLineSpec(
            title = "주요 운동 e1RM",
            summary = summary,
            metrics = listOf(
                TrendMetricId.BENCH_PRESS_E1RM,
                TrendMetricId.SQUAT_E1RM,
                TrendMetricId.DEADLIFT_E1RM
            )
        ),
        note = "확인된 실제 수행 세트만 사용하며, 기록 없는 주는 0으로 채우지 않습니다."
    )
}

@Composable
private fun MuscleLoadShareCard(summary: PerformanceTrendSummary) {
    AnalysisSectionChart(
        title = "근육군별 운동량 비율",
        spec = ChartSpec(
            type = ChartType.HORIZONTAL_BAR,
            title = "근육군별 운동량 비율",
            bars = latestMuscleShare(summary)
        ),
        note = "근육군별 운동량은 운동 기록과 메타데이터를 바탕으로 추정한 상대 지표입니다."
    )
}

@Composable
private fun MuscleLoadShareTrendCard(summary: PerformanceTrendSummary) {
    val buckets = StrengthAndMuscleMetricSeriesBuilder.MuscleBucket.values().toList()
    val available = buckets.filter { bucket -> summary.metricSeries[bucket.dailyMetric].orEmpty().any { it.value != null } }
    val defaults = available.take(3).map { it.dailyMetric }.toSet()
    val selected = remember { mutableStateListOf<TrendMetricId>().apply { addAll(defaults) } }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("근육군별 운동량 비율 추이", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (available.isEmpty()) {
                InfoCard("근육군별 운동량 기록이 부족합니다.")
                return@Column
            }
            AnalysisMetricChipRow(
                metrics = available.map { it.dailyMetric },
                selectedMetrics = selected.toList(),
                onToggle = { metric ->
                    if (metric in selected) selected.remove(metric) else selected.add(metric)
                    if (selected.isEmpty()) selected.add(metric)
                }
            )
            AnalysisChartSpecView(muscleShareTrendSpec(summary, selected.toList()))
        }
    }
}

@Composable
private fun RepRangeShareCard(weeks: List<RepRangeWeekShare>) {
    AnalysisSectionChart(
        title = "반복수 기반 강도 구간 비중",
        spec = ChartSpec(
            type = ChartType.HORIZONTAL_BAR,
            title = "반복수 기반 강도 구간 비중",
            bars = latestRepRangeShare(weeks)
        ),
        note = "1~5회는 저반복/고강도, 6~9회는 중반복/중강도, 10회 이상은 고반복/볼륨으로 분류합니다."
    )
}

@Composable
private fun RepRangeShareTrendCard(weeks: List<RepRangeWeekShare>) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val labels = listOf("저반복/고강도", "중반복/중강도", "고반복/볼륨")
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("반복수 구간 비중 추이", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AnalysisChipRow(labels = labels, selected = selected, onSelect = { selected = it })
            AnalysisChartSpecView(
                ChartSpec(
                    type = ChartType.LINE,
                    title = "반복수 구간 비중 추이",
                    lineSeries = listOf(
                        ChartSeries(
                            labels[selected],
                            weeks.map { week ->
                                TrendDataPoint(
                                    week.weekStart,
                                    when (selected) {
                                        0 -> week.lowRepShare
                                        1 -> week.moderateRepShare
                                        else -> week.highRepShare
                                    }.takeIf { week.confirmedSetCount > 0 }
                                )
                            }
                        )
                    )
                )
            )
        }
    }
}

@Composable
private fun AnalysisSectionChart(title: String, spec: ChartSpec, note: String? = null) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AnalysisChartSpecView(spec)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun metricLineSpec(
    title: String,
    summary: PerformanceTrendSummary,
    metrics: List<TrendMetricId>
): ChartSpec = ChartSpec(
    type = ChartType.LINE,
    title = title,
    lineSeries = metrics.mapNotNull { metric ->
        val points = summary.metricSeries[metric].orEmpty()
        if (points.none { it.value != null }) null else ChartSeries(metric.label(), points)
    }
)

private fun latestMuscleShare(summary: PerformanceTrendSummary): List<BarItem> {
    val raw = StrengthAndMuscleMetricSeriesBuilder.MuscleBucket.values().mapNotNull { bucket ->
        val value = summary.metricSeries[bucket.dailyMetric].orEmpty().lastOrNull { it.value != null }?.value ?: return@mapNotNull null
        bucket.label to value
    }
    val total = raw.sumOf { (_, value) -> value }
    if (total <= 0.0) return emptyList()
    return raw.sortedByDescending { (_, value) -> value }
        .map { (label, value) -> BarItem(label, value / total * 100.0) }
}

private fun muscleShareTrendSpec(summary: PerformanceTrendSummary, selectedMetrics: List<TrendMetricId>): ChartSpec {
    val buckets = StrengthAndMuscleMetricSeriesBuilder.MuscleBucket.values()
    val weekStarts = buckets.flatMap { bucket -> summary.metricSeries[bucket.dailyMetric].orEmpty().map { it.weekStart } }
        .distinct()
        .sorted()
    val totalsByWeek = weekStarts.associateWith { week ->
        buckets.sumOf { bucket ->
            summary.metricSeries[bucket.dailyMetric].orEmpty().firstOrNull { it.weekStart == week }?.value ?: 0.0
        }
    }
    return ChartSpec(
        type = ChartType.LINE,
        title = "근육군별 운동량 비율 추이",
        lineSeries = selectedMetrics.mapNotNull { metric ->
            val descriptor = AnalysisMetricRegistry.descriptor(metric) ?: return@mapNotNull null
            ChartSeries(
                descriptor.displayName.removePrefix("주간 ").removeSuffix(" 운동량"),
                weekStarts.map { week ->
                    val value = summary.metricSeries[metric].orEmpty().firstOrNull { it.weekStart == week }?.value ?: 0.0
                    val total = totalsByWeek[week] ?: 0.0
                    TrendDataPoint(week, if (total > 0.0) value / total * 100.0 else null)
                }
            )
        }
    )
}

private fun latestRepRangeShare(weeks: List<RepRangeWeekShare>): List<BarItem> {
    val latest = weeks.lastOrNull { week -> week.confirmedSetCount > 0 } ?: return emptyList()
    return listOf(
        BarItem("저반복/고강도(1~5회)", latest.lowRepShare),
        BarItem("중반복/중강도(6~9회)", latest.moderateRepShare),
        BarItem("고반복/볼륨(10회 이상)", latest.highRepShare)
    )
}
