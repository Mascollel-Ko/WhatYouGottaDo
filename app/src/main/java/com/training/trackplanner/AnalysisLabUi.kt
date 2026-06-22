package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.lab.AnalysisMetricDescriptor
import com.training.trackplanner.analysis.lab.AnalysisMetricRegistry
import com.training.trackplanner.analysis.trends.PerformanceChartSpecBuilder
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.ScatterRelationshipAnalyzer
import com.training.trackplanner.analysis.trends.TrendMetricId

@Composable
internal fun AnalysisLabContent(summary: PerformanceTrendSummary) {
    val availableMetrics = remember(summary.metricSeries) {
        AnalysisMetricRegistry.scatterMetrics(summary.metricSeries)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabIntroductionCard()
        if (availableMetrics.none { descriptor -> descriptor.id in checkInMetricIds }) {
            InfoCard("체크인 기록이 쌓이면 회복/컨디션 지표도 실험실에서 비교할 수 있습니다.")
        }
        RelationshipExplorerCard(summary, availableMetrics)
        LabMetricCatalogCard(availableMetrics)
        LabFutureExpansionCard()
    }
}

private val checkInMetricIds = setOf(
    TrendMetricId.SLEEP_HOURS,
    TrendMetricId.OVERALL_FATIGUE_CHECKIN,
    TrendMetricId.LOWER_BODY_FATIGUE_CHECKIN,
    TrendMetricId.JOINT_TENDON_DISCOMFORT_CHECKIN,
    TrendMetricId.FOCUS_MOTIVATION_CHECKIN,
    TrendMetricId.RECOVERY_CHECKIN_COMPOSITE
)

@Composable
private fun LabIntroductionCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("실험실", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("훈련 지표 사이의 관계를 탐색합니다. 이 결과는 인과관계가 아니라 패턴 탐색용입니다.")
            Text(
                "여러 지표를 반복 비교하면 우연히 관계처럼 보일 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RelationshipExplorerCard(
    summary: PerformanceTrendSummary,
    availableMetrics: List<AnalysisMetricDescriptor>
) {
    val availableIds = availableMetrics.map { it.id }
    val defaultX = preferredMetric(TrendMetricId.BADMINTON_TRAINING, availableIds, 0)
    val defaultY = preferredMetric(TrendMetricId.FATIGUE_COMPOSITE, availableIds, 1)
    var xMetric by rememberSaveable { mutableStateOf(defaultX) }
    var yMetric by rememberSaveable { mutableStateOf(defaultY) }
    LaunchedEffect(availableIds) {
        if (xMetric !in availableIds) xMetric = defaultX
        if (yMetric !in availableIds || yMetric == xMetric) {
            yMetric = availableIds.firstOrNull { it != xMetric } ?: defaultY
        }
    }
    val analyzer = remember { ScatterRelationshipAnalyzer() }
    val chartBuilder = remember { PerformanceChartSpecBuilder() }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("관계 탐색기", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (availableMetrics.size < 2) {
                InfoCard("관계를 비교할 주간 지표 기록이 부족합니다.")
                return@Column
            }
            MetricAxisDropdown("X축", xMetric, availableMetrics) { xMetric = it }
            MetricAxisDropdown("Y축", yMetric, availableMetrics) { yMetric = it }
            val result = analyzer.analyze(xMetric, yMetric, summary.metricSeries)
            AnalysisChartSpecView(chartBuilder.scatterSpec(result))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabResultMetric("데이터 수", "${result.dataPoints.size}")
                LabResultMetric("상관계수", result.correlation?.let(::formatAnalysisValue) ?: "판단 보류")
                LabResultMetric("신뢰도", analysisConfidenceLabel(result.confidence))
            }
            Text(result.dataSufficiency, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(result.interpretation, style = MaterialTheme.typography.bodySmall)
            Text(
                relationshipWarning(result.dataPoints.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "반복 비교에서는 우연 상관이 포함될 수 있습니다. 결과를 훈련 처방의 단독 근거로 사용하지 마세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricAxisDropdown(
    label: String,
    selected: TrendMetricId,
    metrics: List<AnalysisMetricDescriptor>,
    onSelect: (TrendMetricId) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDescriptor = metrics.firstOrNull { it.id == selected }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { expanded = true }) {
                Text(selectedDescriptor?.displayName ?: "지표 선택")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                metrics.forEach { descriptor ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(descriptor.displayName)
                                Text(
                                    descriptor.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(descriptor.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LabResultMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LabMetricCatalogCard(metrics: List<AnalysisMetricDescriptor>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("사용 가능한 지표", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("현재 데이터가 있는 주간 지표 ${metrics.size}개", style = MaterialTheme.typography.bodySmall)
            metrics.groupBy { it.category }.forEach { (category, descriptors) ->
                Text(
                    "${category.name}: ${descriptors.joinToString { it.displayName }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LabFutureExpansionCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("확장 기반", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            listOf(
                "다변량 분석",
                "다변량 시계열 분석",
                "자동 패턴 탐지",
                "주관적 컨디션 체크인 연동",
                "배드민턴 세션 후 피드백 연동"
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                "향후 분석을 위한 지표 registry 구조가 준비되어 있습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun preferredMetric(
    preferred: TrendMetricId,
    available: List<TrendMetricId>,
    fallbackIndex: Int
): TrendMetricId = preferred.takeIf { it in available }
    ?: available.getOrNull(fallbackIndex)
    ?: available.firstOrNull()
    ?: preferred

private fun relationshipWarning(sampleSize: Int): String = when {
    sampleSize < 8 -> "8주 미만 기록은 관계 판단에 사용하지 않습니다."
    sampleSize < 12 -> "소표본 결과입니다. 산점도와 상관계수는 참고용입니다."
    sampleSize < 20 -> "낮은 신뢰도 구간입니다. 우연히 보이는 관계일 수 있습니다."
    sampleSize < 30 -> "보통 수준의 탐색 결과이며 인과관계를 의미하지 않습니다."
    else -> "표본은 비교적 안정적이지만 인과관계나 효과를 증명하지 않습니다."
}
