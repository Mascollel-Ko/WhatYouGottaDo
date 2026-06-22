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
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget
import com.training.trackplanner.analysis.fatigue.ui.FatigueAnalysisSection
import com.training.trackplanner.analysis.readiness.ReadinessStatus
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
    fatigueAnalysis: FatigueAnalysisUiState,
    badmintonTransfer: BadmintonTransferSummary?,
    performanceTrend: PerformanceTrendSummary?,
    onPeriodChange: (FatigueAnalysisPeriod) -> Unit,
    onFatigueTargetToggle: (FatigueTarget) -> Unit,
    onContributionTargetChange: (FatigueTarget) -> Unit,
    onContributionGroupingChange: (ContributionGrouping) -> Unit,
    onContributionSourcesApply: (Set<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        readiness?.let { TodayReadinessCard(it) }
            ?: InfoCard("오늘 상태를 계산하고 있습니다.")
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
private fun TodayReadinessCard(summary: TodayReadinessSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("오늘 상태", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        readinessStatusLabel(summary.status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                AnalysisConfidencePill(summary.confidence)
            }
            Text(summary.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summary.shortReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary.primaryReasons.take(3).forEach { reason ->
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

@Composable
private fun ReadinessModeList(title: String, values: List<String>) {
    if (values.isEmpty()) return
    Text("$title: ${values.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
}

private fun readinessStatusLabel(status: ReadinessStatus): String = when (status) {
    ReadinessStatus.READY -> "진행 가능"
    ReadinessStatus.CAUTION -> "조절 권장"
    ReadinessStatus.FATIGUED -> "피로 누적"
    ReadinessStatus.LIMITED -> "제한 우선"
}

@Composable
private fun BadmintonTransferCard(summary: BadmintonTransferSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var chartMode by rememberSaveable { mutableStateOf(BadmintonTransferDetailChartMode.AXIS_SHARE) }
    BackHandler(enabled = expanded) { expanded = false }
    val chartItems = when (chartMode) {
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
                    labels = BadmintonTransferDetailChartMode.entries.map { it.displayName },
                    selected = BadmintonTransferDetailChartMode.entries.indexOf(chartMode),
                    onSelect = { chartMode = BadmintonTransferDetailChartMode.entries[it] }
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
        PerformanceDetailSectionType.BADMINTON -> chartBuilder.badmintonDetail(mode, selectedMetrics.toList(), summary.badmintonWeeks)
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
private fun TrainingDistributionSummary(stats: AnalysisStats) {
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
