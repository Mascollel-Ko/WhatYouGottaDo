package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.lab.AnalysisMetricDescriptor
import com.training.trackplanner.analysis.lab.AnalysisMetricRegistry
import com.training.trackplanner.analysis.lab.BayesianTimeSeriesAnalyzer
import com.training.trackplanner.analysis.lab.BayesianTimeSeriesModel
import com.training.trackplanner.analysis.lab.BayesianTimeSeriesResult
import com.training.trackplanner.analysis.lab.LaggedTimeSeriesAnalyzer
import com.training.trackplanner.analysis.lab.LaggedTimeSeriesResult
import com.training.trackplanner.analysis.lab.TimeSeriesAnalysisRequest
import com.training.trackplanner.analysis.lab.displayLabelKo
import com.training.trackplanner.analysis.trends.BarItem
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartType
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

@Composable
private fun LegacyLaggedTimeSeriesAnalysisContent(summary: PerformanceTrendSummary) {
    val xMetrics = remember(summary.metricSeries) {
        AnalysisMetricRegistry.timeSeriesXMetrics(summary.metricSeries)
    }
    val yMetrics = remember(summary.metricSeries) {
        AnalysisMetricRegistry.timeSeriesYMetrics(summary.metricSeries)
    }
    val controlMetrics = remember(summary.metricSeries) {
        AnalysisMetricRegistry.timeSeriesControlMetrics(summary.metricSeries)
    }
    val xIds = xMetrics.map { it.id }
    val yIds = yMetrics.map { it.id }
    val controlIds = controlMetrics.map { it.id }
    val defaultX = preferredMetric(TrendMetricId.BADMINTON_TRAINING, xIds, 0)
    val defaultY = preferredMetric(TrendMetricId.FATIGUE_COMPOSITE, yIds, 0)
    val defaultControls = remember(controlIds) { recommendedControlMetrics(controlIds) }
    var xMetric by rememberSaveable { mutableStateOf(defaultX) }
    var yMetric by rememberSaveable { mutableStateOf(defaultY) }
    val controls = remember(defaultControls) { mutableStateListOf<TrendMetricId>().apply { addAll(defaultControls) } }
    var showControls by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<LaggedTimeSeriesResult?>(null) }
    LaunchedEffect(xIds, yIds, controlIds) {
        if (xMetric !in xIds) xMetric = defaultX
        if (yMetric !in yIds || yMetric == xMetric) {
            yMetric = yIds.firstOrNull { it != xMetric } ?: defaultY
        }
        controls.removeAll { it !in controlIds || it == xMetric || it == yMetric }
        if (controls.isEmpty()) {
            controls.addAll(defaultControls.filter { it != xMetric && it != yMetric })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabLaggedIntroCard()
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("시계열 분석", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (xMetrics.isEmpty() || yMetrics.isEmpty()) {
                    InfoCard("시계열 분석에 사용할 주간 지표가 부족합니다.")
                    return@Column
                }
                MetricAxisDropdown("X 변수", xMetric, xMetrics) {
                    xMetric = it
                    if (yMetric == xMetric) yMetric = yIds.firstOrNull { id -> id != xMetric } ?: yMetric
                    controls.removeAll { id -> id == xMetric || id == yMetric }
                    result = null
                }
                MetricAxisDropdown("Y 변수", yMetric, yMetrics) {
                    yMetric = it
                    if (xMetric == yMetric) xMetric = xIds.firstOrNull { id -> id != yMetric } ?: xMetric
                    controls.removeAll { id -> id == xMetric || id == yMetric }
                    result = null
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showControls = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        text = controlSummary(controls, controlMetrics),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        result = LaggedTimeSeriesAnalyzer().analyze(
                            xMetric = xMetric,
                            yMetric = yMetric,
                            controls = controls.toList(),
                            metricSeries = summary.metricSeries
                        )
                    }
                ) {
                    Text("분석하기")
                }
                Text(
                    "변수 선택만으로는 계산하지 않습니다. 버튼을 누르면 1~4주 뒤 패턴만 탐색합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        result?.let { LaggedResultCard(it) }
    }
    if (showControls) {
        MetricMultiSelectDialog(
            title = "통제 변수 선택",
            metrics = controlMetrics.filter { it.id != xMetric && it.id != yMetric },
            selected = controls.toSet(),
            onDismiss = { showControls = false },
            onApply = { selected ->
                controls.clear()
                controls.addAll(selected.take(3))
                result = null
                showControls = false
            }
        )
    }
}

@Composable
internal fun LaggedTimeSeriesAnalysisContent(summary: PerformanceTrendSummary) {
    val xMetrics = remember(summary.metricSeries) { AnalysisMetricRegistry.timeSeriesXMetrics(summary.metricSeries) }
    val responseMetrics = remember(summary.metricSeries) { AnalysisMetricRegistry.timeSeriesYMetrics(summary.metricSeries) }
    val controlMetrics = remember(summary.metricSeries) { AnalysisMetricRegistry.timeSeriesControlMetrics(summary.metricSeries) }
    val defaultX = preferredMetric(TrendMetricId.BADMINTON_TRAINING, xMetrics.map { it.id }, 0)
    val defaultY = preferredMetric(TrendMetricId.FATIGUE_COMPOSITE, responseMetrics.map { it.id }, 0)
    var xMetric by rememberSaveable { mutableStateOf(defaultX) }
    val selectedY = remember { mutableStateListOf(defaultY) }
    val controls = remember { mutableStateListOf<TrendMetricId>() }
    var horizon by rememberSaveable { mutableStateOf(2) }
    var showYPicker by rememberSaveable { mutableStateOf(false) }
    var showControlPicker by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<BayesianTimeSeriesResult?>(null) }
    LaunchedEffect(xMetrics, responseMetrics, controlMetrics) {
        if (xMetric !in xMetrics.map { it.id }) xMetric = defaultX
        selectedY.removeAll { it == xMetric || it !in responseMetrics.map { item -> item.id } }
        if (selectedY.isEmpty()) responseMetrics.firstOrNull { it.id != xMetric }?.let { selectedY.add(it.id) }
        controls.removeAll { it == xMetric || it in selectedY || it !in controlMetrics.map { item -> item.id } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabLaggedIntroCard()
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bayesian 시계열 분석", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (xMetrics.isEmpty() || responseMetrics.isEmpty()) {
                    InfoCard("시계열 분석에 사용할 수 있는 주간 지표가 부족합니다.")
                    return@Column
                }
                MetricAxisDropdown("충격 변수 X", xMetric, xMetrics) {
                    xMetric = it
                    selectedY.removeAll { metric -> metric == it }
                    if (selectedY.isEmpty()) responseMetrics.firstOrNull { item -> item.id != it }?.let { item -> selectedY.add(item.id) }
                    controls.removeAll { metric -> metric == it || metric in selectedY }
                    result = null
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showYPicker = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        text = "응답 변수 Y: ${controlSummary(selectedY, responseMetrics)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showControlPicker = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        text = "외생 통제 Z: ${controlSummary(controls, controlMetrics)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("반응 확인 기간", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { horizon = (horizon - 1).coerceAtLeast(1) }, enabled = horizon > 1) { Text("-") }
                        Text("${horizon}주", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { horizon = (horizon + 1).coerceAtMost(8) }, enabled = horizon < 8) { Text("+") }
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedY.isNotEmpty(),
                    onClick = {
                        result = BayesianTimeSeriesAnalyzer().analyze(
                            TimeSeriesAnalysisRequest(xMetric, selectedY.toList(), controls.toList(), horizon),
                            summary.metricSeries
                        )
                    }
                ) { Text("분석하기") }
                Text(
                    "기본 horizon은 2주이며 1~8주에서 선택할 수 있습니다. 자료가 부족하면 가능한 기간으로 자동 축소합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        result?.let { analysis -> BayesianResultCard(analysis) }
    }
    if (showYPicker) {
        MetricMultiSelectDialog(
            title = "응답 변수 Y 선택",
            metrics = responseMetrics.filter { it.id != xMetric },
            selected = selectedY.toSet(),
            onDismiss = { showYPicker = false },
            onApply = { selected ->
                if (selected.isNotEmpty()) {
                    selectedY.clear()
                    selectedY.addAll(selected)
                    controls.removeAll { it in selectedY }
                    result = null
                }
                showYPicker = false
            }
        )
    }
    if (showControlPicker) {
        MetricMultiSelectDialog(
            title = "외생 통제 Z 선택",
            metrics = controlMetrics.filter { it.id != xMetric && it.id !in selectedY },
            selected = controls.toSet(),
            onDismiss = { showControlPicker = false },
            onApply = { selected ->
                controls.clear()
                controls.addAll(selected)
                result = null
                showControlPicker = false
            }
        )
    }
}

@Composable
private fun LabLaggedIntroCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("시차 관계 분석", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("한 지표가 변한 뒤 다른 지표가 몇 주 뒤 어떻게 움직였는지 봅니다.")
            Text(
                "인과관계 확정이 아니라 주간 기록 기반의 탐색적 패턴입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BayesianResultCard(result: BayesianTimeSeriesResult) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bayesian 시계열 분석 결과", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(result.summary, style = MaterialTheme.typography.bodyMedium)
            Text("사용 모델: ${bayesianModelLabel(result.model)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("요청 horizon: ${result.request.requestedHorizon}주 · 실제 horizon: ${result.usedHorizon}주", style = MaterialTheme.typography.labelSmall)
            result.lagPosterior?.let { posterior ->
                val labels = posterior.probabilities.entries.sortedBy { it.key }.joinToString(", ") { (lag, probability) -> "p=$lag ${formatAnalysisValue(probability)}" }
                Text("Bayesian lag posterior: $labels", style = MaterialTheme.typography.labelSmall)
            }
            result.cointegration?.let { diagnostic ->
                Text(
                    "공적분: ${diagnostic.message} (P(rank>0)=${formatAnalysisValue(diagnostic.posteriorProbabilityRankPositive)})",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            result.alignment?.let { alignment ->
                val period = alignment.weeks.firstOrNull()?.let { start ->
                    alignment.weeks.lastOrNull()?.let { end -> "$start~$end" }
                } ?: "-"
                Text("분석 기간: $period · 정렬 관측치: ${alignment.weeks.size}", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "외생 통제 Z: ${controlSummary(result.request.controls, AnalysisMetricRegistry.descriptors)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "자동 포함 내생변수: ${controlSummary(result.automaticEndogenous, AnalysisMetricRegistry.descriptors)}",
                style = MaterialTheme.typography.labelSmall
            )
            result.transformations.takeIf { it.isNotEmpty() }?.let { transformations ->
                val labels = transformations.entries.joinToString(", ") { (metric, transform) ->
                    "${AnalysisMetricRegistry.descriptor(metric)?.displayName ?: metric.name}: $transform"
                }
                Text("변환: $labels", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "Cholesky 순서: ${controlSummary(result.choleskyOrder, AnalysisMetricRegistry.descriptors)}",
                style = MaterialTheme.typography.labelSmall
            )
            result.choleskySensitivity?.let { sensitivity ->
                Text(sensitivity.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result.responses.isEmpty()) {
                InfoCard("현재 자료로는 안정적인 동적 반응을 추정할 수 없습니다. 기록을 더 쌓거나 변수를 줄여 주세요.")
            } else {
                result.responses.forEach { response ->
                    val label = AnalysisMetricRegistry.descriptor(response.yMetric)?.displayName ?: response.yMetric.name
                    Text("반응 Y: $label", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    AnalysisChartSpecView(
                        ChartSpec(
                            type = ChartType.HORIZONTAL_BAR,
                            title = "$label IRF",
                            bars = response.points.map { point -> BarItem("h=${point.horizonWeeks}", point.estimate) }
                        )
                    )
                    response.points.forEach { point ->
                        Text(
                            "${if (point.horizonWeeks == 0) "동시 반응" else "h=${point.horizonWeeks}"}: ${formatAnalysisValue(point.estimate)} (80% ${formatAnalysisValue(point.low80)}~${formatAnalysisValue(point.high80)}, n=${point.observations})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text("credible interval은 posterior 불확실성을 반영하며 인과관계를 확정하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.automaticSelectionDiagnostics.take(4).forEach { diagnostic ->
                Text(diagnostic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            result.warnings.forEach { warning -> Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun bayesianModelLabel(model: BayesianTimeSeriesModel): String = when (model) {
    BayesianTimeSeriesModel.BAYESIAN_LOCAL_PROJECTION -> "Bayesian Local Projection"
    BayesianTimeSeriesModel.BAYESIAN_VAR -> "Bayesian VAR"
    BayesianTimeSeriesModel.BAYESIAN_VECM -> "Bayesian VECM"
    BayesianTimeSeriesModel.UNAVAILABLE -> "추정 불가"
}

@Composable
private fun LaggedResultCard(result: LaggedTimeSeriesResult) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("시차 분석 결과", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(result.summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "자동 보정 변수: ${result.automaticAdjustments.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "선택 통제 변수: ${controlSummary(result.controls, AnalysisMetricRegistry.descriptors)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.points.isEmpty()) {
                InfoCard("분석 가능한 주간 데이터가 부족합니다. 최소 8~12주 이상의 기록이 필요합니다.")
            } else {
                AnalysisChartSpecView(
                    ChartSpec(
                        type = ChartType.HORIZONTAL_BAR,
                        title = "1~4주 뒤 표준화 반응",
                        bars = result.points.map { point -> BarItem("${point.horizonWeeks}주 후", point.estimate) }
                    )
                )
                result.points.forEach { point ->
                    Text(
                        "${point.horizonWeeks}주 후: ${formatAnalysisValue(point.estimate)} (80% ${formatAnalysisValue(point.low80)}~${formatAnalysisValue(point.high80)}, n=${point.observations})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabResultMetric("후보 주", "${result.candidateWeeks}")
                LabResultMetric("사용 주", "${result.observations}")
                LabResultMetric("신뢰도", analysisConfidenceLabel(result.confidence))
            }
            Text(
                "80% 구간은 잔차분산을 반영한 탐색적 근사 구간입니다. 값은 표준화된 변화량 기준이며, 표본이 적으면 방향만 참고하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            result.warnings.forEach { warning ->
                Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
                                    descriptor.category.displayLabelKo(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
private fun MetricMultiSelectDialog(
    title: String,
    metrics: List<AnalysisMetricDescriptor>,
    selected: Set<TrendMetricId>,
    onDismiss: () -> Unit,
    onApply: (List<TrendMetricId>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val draft = remember(selected) { mutableStateListOf<TrendMetricId>().apply { addAll(selected) } }
    val filtered = metrics.filter { descriptor ->
        query.isBlank() ||
            descriptor.displayName.contains(query, ignoreCase = true) ||
            descriptor.category.displayLabelKo().contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onApply(draft.toList()) }) {
                Text("적용")
            }
        },
        dismissButton = {
            TextButton(onClick = { draft.clear() }) {
                Text("초기화")
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("검색") }
                )
                Text(controlSummary(draft, metrics), style = MaterialTheme.typography.labelMedium)
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { descriptor ->
                        val checked = descriptor.id in draft
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (checked) draft.remove(descriptor.id) else draft.add(descriptor.id)
                            },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (descriptor.id !in draft) draft.add(descriptor.id)
                                    } else {
                                        draft.remove(descriptor.id)
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(descriptor.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    descriptor.category.displayLabelKo(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty()) Text("검색 결과가 없습니다.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}

@Composable
private fun LabResultMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun controlSummary(selected: Collection<TrendMetricId>, metrics: List<AnalysisMetricDescriptor>): String {
    if (selected.isEmpty()) return "통제 변수 없음"
    val names = selected.mapNotNull { id -> metrics.firstOrNull { it.id == id }?.displayName }
    return when {
        names.isEmpty() -> "통제 변수 없음"
        names.size <= 2 -> names.joinToString(", ")
        else -> "${names.take(2).joinToString(", ")} 외 ${names.size - 2}개"
    }
}

private fun recommendedControlMetrics(available: List<TrendMetricId>): List<TrendMetricId> =
    listOf(
        TrendMetricId.SLEEP_HOURS,
        TrendMetricId.FATIGUE_COMPOSITE,
        TrendMetricId.STRENGTH_VOLUME
    ).filter { it in available }.take(3)

@Composable
private fun LabMetricCatalogCard(metrics: List<AnalysisMetricDescriptor>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("사용 가능한 지표", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("현재 데이터가 있는 지표 ${metrics.size}개", style = MaterialTheme.typography.bodySmall)
            metrics.groupBy { it.category }.forEach { (category, descriptors) ->
                val preview = descriptors.take(6).joinToString { it.displayName }
                val suffix = if (descriptors.size > 6) " 외 ${descriptors.size - 6}개" else ""
                Text(
                    "${category.displayLabelKo()}: $preview$suffix",
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
                "체크인 지표 고도화",
                "자동 패턴 탐지",
                "다변량 분석",
                "다변량 시계열 분석",
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
