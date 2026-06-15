package com.training.trackplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.FatigueDetailSection
import com.training.trackplanner.analysis.readiness.FatigueLevel
import com.training.trackplanner.analysis.readiness.ReadinessStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.DetailChartMode
import com.training.trackplanner.analysis.trends.DetailChartSelector
import com.training.trackplanner.analysis.trends.PerformanceChartSpecBuilder
import com.training.trackplanner.analysis.trends.PerformanceDetailSection
import com.training.trackplanner.analysis.trends.PerformanceDetailSectionType
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.ScatterRelationshipAnalyzer
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.analysis.trends.label
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun AnalysisScreen(viewModel: TrainingViewModel) {
    val stats by viewModel.analysisStats.collectAsState()
    val readiness by viewModel.todayReadinessSummary.collectAsState()
    val performanceTrend by viewModel.performanceTrendSummary.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "분석",
                body = "확인한 세트만 실제 훈련 기록으로 보고, 오늘 상태와 성과 흐름을 함께 봅니다."
            )
        }
        item {
            readiness?.let { TodayReadinessCard(it) }
                ?: InfoCard("오늘 상태를 계산하고 있습니다.")
        }
        item {
            performanceTrend?.let { PerformanceTrendCard(it) }
                ?: InfoCard("성과 추세를 계산하고 있습니다.")
        }
        if (stats.confirmedSetCount == 0) {
            item {
                InfoCard("분석은 확인된 세트가 쌓인 뒤 정확해집니다. 먼저 기록 탭에서 수행한 세트를 확인하세요.")
            }
        }
        item {
            MetricCard(
                label = "확인된 세트",
                value = "${stats.confirmedSetCount}세트"
            )
        }
        item {
            MetricCard(
                label = "총 볼륨",
                value = "${formatWeight(stats.totalVolumeKg)} kg"
            )
        }
        item {
            MetricCard(
                label = "총 시간",
                value = formatSeconds(stats.totalSeconds)
            )
        }
    }
}

@Composable
private fun TodayReadinessCard(summary: TodayReadinessSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "오늘 상태",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = statusLabel(summary.status),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                ConfidencePill(summary.confidence)
            }
            Text(
                text = summary.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = summary.shortReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (summary.primaryReasons.isNotEmpty()) {
                CompactTextBlock("주요 이유", summary.primaryReasons)
            }
            if (summary.recommendedModes.isNotEmpty()) {
                CompactTextBlock("추천", summary.recommendedModes)
            }
            if (summary.restrictedModes.isNotEmpty()) {
                CompactTextBlock("조절", summary.restrictedModes)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "자세히 닫기" else "자세히 보기")
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    summary.detailSections.forEach { section ->
                        FatigueDetailSectionCard(section)
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceTrendCard(summary: PerformanceTrendSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "성과 추세 분석",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary.trendSentence,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConfidencePill(summary.confidence)
            }
            summary.dashboardChartSpecs.take(3).forEach { spec ->
                DashboardTrendChart(spec)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "자세히 닫기" else "자세히 보기")
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    summary.detailSections.forEach { section ->
                        PerformanceDetailSectionCard(section, summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTrendChart(spec: ChartSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = spec.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        TrendChartCanvas(spec = spec, modifier = Modifier.height(118.dp))
    }
}

@Composable
private fun PerformanceDetailSectionCard(
    section: PerformanceDetailSection,
    summary: PerformanceTrendSummary
) {
    var mode by rememberSaveable(section.type.name) { mutableStateOf(section.selectedMode) }
    val selectedMetrics = remember(section.type.name) {
        mutableStateListOf<TrendMetricId>().apply { addAll(section.selectedMetrics) }
    }
    var xMetric by rememberSaveable(section.type.name + "_x") { mutableStateOf(TrendMetricId.BADMINTON_TRAINING) }
    var yMetric by rememberSaveable(section.type.name + "_y") { mutableStateOf(TrendMetricId.FATIGUE_COMPOSITE) }
    val chartBuilder = remember { PerformanceChartSpecBuilder() }
    val scatterAnalyzer = remember { ScatterRelationshipAnalyzer() }
    val chartSpec = when (section.type) {
        PerformanceDetailSectionType.STRENGTH ->
            chartBuilder.strengthDetail(mode, selectedMetrics.toList(), summary.strengthWeeks)
        PerformanceDetailSectionType.BADMINTON ->
            chartBuilder.badmintonDetail(mode, selectedMetrics.toList(), summary.badmintonWeeks)
        PerformanceDetailSectionType.FATIGUE ->
            chartBuilder.fatigueDetail(mode, selectedMetrics.toList(), summary.fatigueWeeks)
        PerformanceDetailSectionType.RELATIONSHIP -> {
            val result = scatterAnalyzer.analyze(xMetric, yMetric, summary.metricSeries)
            chartBuilder.scatterSpec(result)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (section.type != PerformanceDetailSectionType.RELATIONSHIP) {
                ChipRow(
                    labels = section.availableModes.map { it.modeLabel() },
                    selected = section.availableModes.indexOf(mode).coerceAtLeast(0),
                    onSelect = { index ->
                        mode = section.availableModes[index]
                        selectedMetrics.clear()
                        selectedMetrics.add(section.availableMetrics.first())
                    }
                )
                MetricChipRow(
                    metrics = section.availableMetrics,
                    selectedMetrics = selectedMetrics.toList(),
                    allowMulti = mode == DetailChartMode.TREND,
                    onToggle = { metric ->
                        if (mode == DetailChartMode.TREND) {
                            if (metric in selectedMetrics) selectedMetrics.remove(metric) else selectedMetrics.add(metric)
                            if (selectedMetrics.isEmpty()) selectedMetrics.add(metric)
                        } else {
                            selectedMetrics.clear()
                            selectedMetrics.add(metric)
                        }
                    }
                )
            } else {
                RelationshipAxisSelectors(
                    xMetric = xMetric,
                    yMetric = yMetric,
                    onXChange = { xMetric = it },
                    onYChange = { yMetric = it }
                )
            }
            ChartSpecView(chartSpec)
            Text(
                text = if (section.type == PerformanceDetailSectionType.RELATIONSHIP) {
                    scatterAnalyzer.analyze(xMetric, yMetric, summary.metricSeries).interpretation
                } else {
                    section.shortInterpretation
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RelationshipAxisSelectors(
    xMetric: TrendMetricId,
    yMetric: TrendMetricId,
    onXChange: (TrendMetricId) -> Unit,
    onYChange: (TrendMetricId) -> Unit
) {
    val xOptions = listOf(
        TrendMetricId.FATIGUE_COMPOSITE,
        TrendMetricId.BADMINTON_TRAINING,
        TrendMetricId.STRENGTH_PERFORMANCE,
        TrendMetricId.STRENGTH_VOLUME_ONLY,
        TrendMetricId.STRENGTH_INTENSITY_ONLY
    )
    val yOptions = listOf(
        TrendMetricId.FATIGUE_COMPOSITE,
        TrendMetricId.STRENGTH_DELTA_NEXT,
        TrendMetricId.FATIGUE_DELTA_NEXT,
        TrendMetricId.STRENGTH_PERFORMANCE
    )
    Text("X축", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    MetricChipRow(xOptions, listOf(xMetric), allowMulti = false, onToggle = onXChange)
    Text("Y축", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    MetricChipRow(yOptions, listOf(yMetric), allowMulti = false, onToggle = onYChange)
}

@Composable
private fun ChartSpecView(spec: ChartSpec) {
    when (spec.type) {
        ChartType.LINE -> TrendChartCanvas(spec = spec, modifier = Modifier.height(150.dp))
        ChartType.BAR,
        ChartType.HORIZONTAL_BAR,
        ChartType.PIE -> BarChartList(spec.bars.ifEmpty {
            spec.slices.map { slice -> com.training.trackplanner.analysis.trends.BarItem(slice.label, slice.value) }
        })
        ChartType.SCATTER -> ScatterChartCanvas(spec = spec, modifier = Modifier.height(150.dp))
    }
}

@Composable
private fun TrendChartCanvas(spec: ChartSpec, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondLine = MaterialTheme.colorScheme.tertiary
    val thirdLine = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val forecastColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val allValues = spec.lineSeries.flatMap { series -> series.points.mapNotNull { point -> point.value } }
        .plus(spec.forecastRange?.points?.flatMap { point -> listOf(point.lower, point.upper) }.orEmpty())
    if (allValues.isEmpty()) {
        InfoCard("기록 부족")
        return
    }
    val min = (allValues.minOrNull() ?: 50.0).coerceAtMost(100.0) - 8.0
    val max = (allValues.maxOrNull() ?: 160.0).coerceAtLeast(100.0) + 8.0
    val colors = listOf(lineColor, secondLine, thirdLine)
    Canvas(modifier = modifier.fillMaxWidth()) {
        val chartWidth = size.width
        val chartHeight = size.height
        repeat(3) { index ->
            val y = chartHeight * (index + 1) / 4f
            drawLine(gridColor, Offset(0f, y), Offset(chartWidth, y), strokeWidth = 1f)
        }
        val historyCount = spec.lineSeries.firstOrNull()?.points?.size ?: 0
        val forecastCount = spec.forecastRange?.points?.size ?: 0
        val totalCount = (historyCount + forecastCount).coerceAtLeast(2)
        fun xAt(index: Int): Float =
            if (totalCount <= 1) 0f else chartWidth * index / (totalCount - 1)
        fun yAt(value: Double): Float {
            val ratio = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
            return (chartHeight - (chartHeight * ratio)).toFloat()
        }
        spec.forecastRange?.points?.takeIf { it.isNotEmpty() }?.let { forecast ->
            val path = Path()
            forecast.forEachIndexed { index, point ->
                val x = xAt(historyCount + index)
                val y = yAt(point.upper)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            forecast.asReversed().forEachIndexed { index, point ->
                val x = xAt(historyCount + forecast.lastIndex - index)
                path.lineTo(x, yAt(point.lower))
            }
            path.close()
            drawPath(path, forecastColor)
        }
        spec.lineSeries.forEachIndexed { seriesIndex, series ->
            val path = Path()
            var hasPoint = false
            series.points.forEachIndexed { index, point ->
                val value = point.value ?: return@forEachIndexed
                val x = xAt(index)
                val y = yAt(value)
                if (!hasPoint) {
                    path.moveTo(x, y)
                    hasPoint = true
                } else {
                    path.lineTo(x, y)
                }
                drawCircle(colors[seriesIndex % colors.size], radius = 4f, center = Offset(x, y))
            }
            drawPath(
                path = path,
                color = colors[seriesIndex % colors.size],
                style = Stroke(width = 4f)
            )
        }
    }
}

@Composable
private fun ScatterChartCanvas(spec: ChartSpec, modifier: Modifier = Modifier) {
    val pointColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val points = spec.scatterPoints
    if (points.size < 2) {
        InfoCard("관계 분석은 기록이 더 필요합니다.")
        return
    }
    val minX = points.minOf { point -> point.x }
    val maxX = points.maxOf { point -> point.x }
    val minY = points.minOf { point -> point.y }
    val maxY = points.maxOf { point -> point.y }
    Canvas(modifier = modifier.fillMaxWidth()) {
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 1f)
        points.forEach { point ->
            val xRatio = if (abs(maxX - minX) < 0.001) 0.5 else (point.x - minX) / (maxX - minX)
            val yRatio = if (abs(maxY - minY) < 0.001) 0.5 else (point.y - minY) / (maxY - minY)
            drawCircle(
                color = pointColor,
                radius = 5f,
                center = Offset(
                    x = (size.width * xRatio).toFloat(),
                    y = (size.height - size.height * yRatio).toFloat()
                )
            )
        }
    }
}

@Composable
private fun BarChartList(items: List<com.training.trackplanner.analysis.trends.BarItem>) {
    if (items.isEmpty()) {
        InfoCard("기록 부족")
        return
    }
    val max = items.maxOf { item -> abs(item.value) }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(6).forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.label, style = MaterialTheme.typography.labelMedium)
                    Text(formatTrendValue(item.value), style = MaterialTheme.typography.labelMedium)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth((abs(item.value) / max).coerceIn(0.04, 1.0).toFloat())
                        .height(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {}
            }
        }
    }
}

@Composable
private fun ChipRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            SelectableChip(
                label = label,
                selected = index == selected,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun MetricChipRow(
    metrics: List<TrendMetricId>,
    selectedMetrics: List<TrendMetricId>,
    allowMulti: Boolean,
    onToggle: (TrendMetricId) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metrics.forEach { metric ->
            SelectableChip(
                label = metric.label(),
                selected = metric in selectedMetrics,
                onClick = { onToggle(metric) }
            )
        }
    }
    if (!allowMulti && selectedMetrics.size > 1) {
        Text(
            text = "이 차트 유형은 하나의 지표만 표시합니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompactTextBlock(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        values.forEach { value ->
            Text(
                text = "- $value",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun FatigueDetailSectionCard(section: FatigueDetailSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LevelPill(section.level)
            }
            Text(
                text = section.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            section.metrics.take(4).forEach { metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = metric.value,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidencePill(confidence: AnalysisConfidence) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = "신뢰도 ${confidenceLabel(confidence)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LevelPill(level: FatigueLevel) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = levelLabel(level),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun statusLabel(status: ReadinessStatus): String =
    when (status) {
        ReadinessStatus.READY -> "READY"
        ReadinessStatus.CAUTION -> "CAUTION"
        ReadinessStatus.FATIGUED -> "FATIGUED"
        ReadinessStatus.LIMITED -> "LIMITED"
    }

private fun confidenceLabel(confidence: AnalysisConfidence): String =
    when (confidence) {
        AnalysisConfidence.LOW -> "낮음"
        AnalysisConfidence.MEDIUM_LOW -> "보통 이하"
        AnalysisConfidence.MEDIUM -> "보통"
        AnalysisConfidence.HIGH -> "높음"
    }

private fun levelLabel(level: FatigueLevel): String =
    when (level) {
        FatigueLevel.LOW -> "LOW"
        FatigueLevel.NORMAL -> "NORMAL"
        FatigueLevel.ELEVATED -> "ELEVATED"
        FatigueLevel.HIGH -> "HIGH"
        FatigueLevel.VERY_HIGH -> "VERY HIGH"
        FatigueLevel.LIMITED -> "LIMITED"
    }

private fun DetailChartMode.modeLabel(): String =
    when (this) {
        DetailChartMode.TREND -> "추세"
        DetailChartMode.COMPOSITION -> "비중"
        DetailChartMode.CONTRIBUTION -> "기여도"
        DetailChartMode.RANKING -> "랭킹"
        DetailChartMode.RELATIONSHIP -> "관계"
    }

private fun formatTrendValue(value: Double): String =
    String.format(Locale.US, "%.1f", value)
