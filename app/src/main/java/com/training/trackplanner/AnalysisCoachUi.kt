package com.training.trackplanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.training.trackplanner.analysis.badminton.BadmintonTransferBarItem
import com.training.trackplanner.analysis.badminton.BadmintonTransferDetailChartMode
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.coach.BadmintonTransferAxisStatus
import com.training.trackplanner.analysis.coach.CoachAnalysisInsightSummary
import com.training.trackplanner.analysis.coach.CoachFatigueCauseSummary
import com.training.trackplanner.analysis.coach.CoachingSignalSeverity
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget
import com.training.trackplanner.analysis.fatigue.FatigueThresholds
import com.training.trackplanner.analysis.fatigue.ui.FatigueAnalysisSection
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.TodayFatigueStatusLabeler
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.DetailChartMode
import com.training.trackplanner.analysis.trends.DetailChartSelector
import com.training.trackplanner.analysis.trends.PerformanceChartSpecBuilder
import com.training.trackplanner.analysis.trends.PerformanceDetailSection
import com.training.trackplanner.analysis.trends.PerformanceDetailSectionType
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.AnalysisStats
import kotlin.math.abs

@Composable
internal fun CoachAnalysisContent(
    stats: AnalysisStats,
    readiness: TodayReadinessSummary?,
    todayStatus: PhaseAwareTodayStatus?,
    fatigueAnalysis: FatigueAnalysisUiState,
    badmintonTransfer: BadmintonTransferSummary?,
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
        CoachFatigueCauseCard(
            summary = coachInsight.fatigueCauses,
            combinedHeadline = coachInsight.combinedHeadline,
            checkInGuidance = coachInsight.checkInGuidance
        )
        BadmintonTransferCoverageCard(coachInsight)
        CoachingSignalsCard(coachingSignals)
        FatigueAnalysisSection(
            state = fatigueAnalysis,
            onPeriodChange = onPeriodChange,
            onFatigueTargetToggle = onFatigueTargetToggle,
            onContributionTargetChange = onContributionTargetChange,
            onContributionGroupingChange = onContributionGroupingChange,
            onContributionSourcesApply = onContributionSourcesApply
        )
        badmintonTransfer?.let { BadmintonTransferCard(it) }
            ?: InfoCard("배드민턴 전이 분석을 계산하고 있습니다.")
        performanceTrend?.let { PerformanceTrendCard(it) }
            ?: InfoCard("성과 추세를 계산하고 있습니다.")
        if (stats.confirmedSetCount == 0) {
            InfoCard("확인된 세트가 쌓이면 코치 분석의 신뢰도가 높아집니다.")
        }
        TrainingDistributionSummary(stats)
    }
}

@Composable
internal fun CoachingSignalsCard(summary: CoachingSignalsSummary) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("수면 보정 코칭 신호", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            CoachingSignalRow(
                label = "수면",
                severity = summary.sleep.severity,
                headline = summary.sleep.headline,
                detail = summary.sleep.detail
            )
            val optionalRows = listOfNotNull(
                summary.rpe?.let { signal ->
                    CoachingSignalRowData(
                        label = "RPE 대비 수행",
                        severity = signal.severity,
                        headline = signal.headline,
                        detail = listOfNotNull(signal.detail, signal.sleepContext).joinToString(" ")
                    )
                },
                summary.jointTendon?.let { signal ->
                    CoachingSignalRowData(
                        label = "관절/건 주의",
                        severity = signal.severity,
                        headline = signal.headline,
                        detail = listOfNotNull(
                            signal.detail,
                            signal.relatedStressLabels.takeIf { it.isNotEmpty() }?.joinToString(prefix = "관련 부하: "),
                            signal.sleepContext
                        ).joinToString(" ")
                    )
                },
                summary.courtRecovery?.let { signal ->
                    CoachingSignalRowData(
                        label = "코트 시간 회복 반응",
                        severity = signal.severity,
                        headline = signal.headline,
                        detail = listOfNotNull(signal.detail, signal.sleepContext).joinToString(" ")
                    )
                }
            )
            if (optionalRows.isEmpty()) {
                Text("추가 신호는 기록이 더 쌓이면 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                optionalRows.take(3).forEach { row ->
                    CoachingSignalRow(row.label, row.severity, row.headline, row.detail)
                }
            }
        }
    }
}

private data class CoachingSignalRowData(
    val label: String,
    val severity: CoachingSignalSeverity,
    val headline: String,
    val detail: String
)

@Composable
private fun CoachingSignalRow(
    label: String,
    severity: CoachingSignalSeverity,
    headline: String,
    detail: String
) {
    val severityText = when (severity) {
        CoachingSignalSeverity.NONE -> "기록 부족"
        CoachingSignalSeverity.INFO -> "참고"
        CoachingSignalSeverity.WATCH -> "주의"
        CoachingSignalSeverity.CAUTION -> "강한 주의"
    }
    val severityColor = when (severity) {
        CoachingSignalSeverity.CAUTION -> MaterialTheme.colorScheme.error
        CoachingSignalSeverity.WATCH -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(severityText, style = MaterialTheme.typography.labelSmall, color = severityColor)
            }
            Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoachFatigueCauseCard(
    summary: CoachFatigueCauseSummary,
    combinedHeadline: String?,
    checkInGuidance: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("피로 상승 원인", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!summary.isDataSufficient) {
                Text(summary.headline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(
                combinedHeadline ?: summary.headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            checkInGuidance.take(3).forEach { guidance ->
                Text(
                    text = guidance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            summary.causes.take(5).forEach { cause ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${cause.rank}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(cause.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            cause.affectedAxes.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(cause.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadmintonTransferCoverageCard(insight: CoachAnalysisInsightSummary) {
    val summary = insight.transferCoverage
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("배드민턴 전이 점검", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(summary.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (!summary.isDataSufficient) return@Column
            if (summary.lowAxes.isNotEmpty()) {
                Text("부족축", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                summary.lowAxes.take(4).forEach { TransferAxisRow(it) }
            }
            if (summary.cautionAxes.isNotEmpty()) {
                Text("과잉·주의축", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                summary.cautionAxes.take(3).forEach { TransferAxisRow(it) }
            }
            if (summary.lowAxes.isEmpty() && summary.cautionAxes.isEmpty()) {
                Text("최근 전이 축은 대체로 균형적입니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun TransferAxisRow(axis: BadmintonTransferAxisStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(axis.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(axis.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TodayReadinessCard(status: PhaseAwareTodayStatus) {
    TodayReadinessCard(
        summary = status.current,
        phaseLabel = status.phaseLabel,
        headline = status.headline,
        detail = status.detail,
        actionLabel = status.actionLabel,
        projected = status.projected,
        keyAxes = status.keyAxes
    )
}

@Composable
internal fun TodayReadinessCard(
    summary: TodayReadinessSummary,
    phaseLabel: String? = null,
    headline: String = summary.headline,
    detail: String = summary.shortReason,
    actionLabel: String? = null,
    projected: TodayReadinessSummary? = null,
    keyAxes: List<String> = emptyList()
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("오늘 상태", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    phaseLabel?.let { label ->
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "현재 상태: ${TodayFatigueStatusLabeler.label(summary)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                AnalysisConfidencePill(summary.confidence)
            }
            projected?.let { projectedSummary ->
                Text(
                    "운동 후 예상 부하: ${projectedReadinessLoadLabel(projectedSummary)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actionLabel?.let { action ->
                Text("판단: $action", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            keyAxes.take(3).forEach { axis ->
                Text("• $axis", style = MaterialTheme.typography.bodySmall)
            }
            if (keyAxes.isEmpty()) summary.primaryReasons.take(3).forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "세부 판단 닫기" else "세부 판단 보기")
            }
            if (expanded) {
                ReadinessModeList("권장", summary.recommendedModes)
                ReadinessModeList("제한", summary.restrictedModes)
                summary.detailSections.forEach { section ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(section.summary, style = MaterialTheme.typography.bodySmall)
                            section.metrics.take(4).forEach { metric ->
                                Text("${metric.label}: ${metric.value}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun projectedReadinessLoadLabel(summary: TodayReadinessSummary): String {
    val score = summary.fatiguePresentation?.overallScore
    return when {
        score == null -> TodayFatigueStatusLabeler.label(summary)
        score >= FatigueThresholds.OFI_HIGH_START -> "회복 우선 확인"
        score >= FatigueThresholds.OFI_CAUTION_START -> "회복 확인 필요"
        score >= FatigueThresholds.OFI_ELEVATED_START -> "예상 부하 증가"
        else -> "예상 부하 보통"
    }
}

@Composable
private fun ReadinessModeList(title: String, values: List<String>) {
    if (values.isEmpty()) return
    Text("$title: ${values.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun BadmintonTransferCard(summary: BadmintonTransferSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var chartMode by rememberSaveable { mutableStateOf(BadmintonTransferDetailChartMode.AXIS_SHARE) }
    val allowedChartModes = listOf(
        BadmintonTransferDetailChartMode.AXIS_SHARE,
        BadmintonTransferDetailChartMode.WINDOW_COMPARISON
    )
    val effectiveChartMode = chartMode.takeIf { it in allowedChartModes }
        ?: BadmintonTransferDetailChartMode.AXIS_SHARE
    BackHandler(enabled = expanded) { expanded = false }
    val chartItems = when (effectiveChartMode) {
        BadmintonTransferDetailChartMode.AXIS_SHARE -> summary.chartData.axisShareBars
        BadmintonTransferDetailChartMode.TRANSFER_TYPE_SHARE -> summary.chartData.transferTypeShareBars
        BadmintonTransferDetailChartMode.WINDOW_COMPARISON -> summary.chartData.windowComparisonBars
        BadmintonTransferDetailChartMode.TOP_EXERCISES -> summary.chartData.topExerciseBars
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("배드민턴 전이 분석", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AnalysisConfidencePill(summary.confidence)
            }
            Text(summary.metrics.recommendationSentence, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "자세히 닫기" else "자세히 보기") }
            if (expanded) {
                Text(summary.metrics.detailInsightText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnalysisChipRow(
                    labels = allowedChartModes.map { it.displayName },
                    selected = allowedChartModes.indexOf(effectiveChartMode).coerceAtLeast(0),
                    onSelect = { chartMode = allowedChartModes[it] }
                )
                TransferBarList(chartItems)
                if (summary.metrics.recommendedExerciseCandidates.isNotEmpty()) {
                    Text("추천축 후보 운동", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    summary.metrics.recommendedExerciseCandidates.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun PerformanceTrendCard(summary: PerformanceTrendSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("성과 추세", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(summary.trendSentence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnalysisConfidencePill(summary.confidence)
            }
            summary.dashboardChartSpecs.take(3).forEach { DashboardTrendChart(it) }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "자세히 닫기" else "자세히 보기") }
            if (expanded) {
                summary.detailSections
                    .filter { it.type != PerformanceDetailSectionType.RELATIONSHIP }
                    .forEach { PerformanceDetailSectionView(it, summary) }
            }
        }
    }
}

@Composable
private fun DashboardTrendChart(spec: ChartSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(spec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        AnalysisTrendChart(spec = spec, modifier = Modifier.height(118.dp))
    }
}

@Composable
private fun PerformanceDetailSectionView(section: PerformanceDetailSection, summary: PerformanceTrendSummary) {
    var mode by rememberSaveable(section.type.name) { mutableStateOf(section.selectedMode) }
    val selectedMetrics = remember(section.type.name) {
        mutableStateListOf<TrendMetricId>().apply { addAll(section.selectedMetrics) }
    }
    val chartBuilder = remember { PerformanceChartSpecBuilder() }
    val chartSpec = when (section.type) {
        PerformanceDetailSectionType.STRENGTH -> chartBuilder.strengthDetail(mode, selectedMetrics.toList(), summary.strengthWeeks)
        PerformanceDetailSectionType.BADMINTON -> chartBuilder.badmintonDetail(
            mode,
            selectedMetrics.toList(),
            summary.badmintonWeeks,
            summary.exerciseDisplayNamesById
        )
        PerformanceDetailSectionType.FATIGUE -> chartBuilder.fatigueDetail(mode, selectedMetrics.toList(), summary.fatigueWeeks)
        PerformanceDetailSectionType.RELATIONSHIP -> return
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AnalysisChipRow(
                labels = section.availableModes.map { it.analysisLabel() },
                selected = section.availableModes.indexOf(mode).coerceAtLeast(0),
                onSelect = { index ->
                    mode = section.availableModes[index]
                    selectedMetrics.clear()
                    section.availableMetrics.firstOrNull()?.let(selectedMetrics::add)
                }
            )
            if (mode == DetailChartMode.TREND) {
                AnalysisMetricChipRow(
                    metrics = section.availableMetrics,
                    selectedMetrics = selectedMetrics.toList(),
                    onToggle = { metric ->
                        if (metric in selectedMetrics) selectedMetrics.remove(metric) else selectedMetrics.add(metric)
                        if (selectedMetrics.isEmpty()) selectedMetrics.add(metric)
                    }
                )
            }
            AnalysisChartSpecView(chartSpec)
            Text(section.shortInterpretation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransferBarList(items: List<BadmintonTransferBarItem>) {
    if (items.isEmpty()) {
        InfoCard("전이 자극 기록이 부족합니다.")
        return
    }
    val max = items.maxOf { abs(it.value) }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, style = MaterialTheme.typography.labelMedium)
                    Text(item.valueLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth((abs(item.value) / max).coerceIn(0.04, 1.0).toFloat()).height(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {}
            }
        }
    }
}

@Composable
internal fun TrainingDistributionSummary(stats: AnalysisStats) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("훈련 분포 요약", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CompactMetric("확인 세트", "${stats.confirmedSetCount}")
                CompactMetric("총 볼륨", "${formatWeight(stats.totalVolumeKg)} kg")
                CompactMetric("총 시간", formatSeconds(stats.totalSeconds))
            }
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
