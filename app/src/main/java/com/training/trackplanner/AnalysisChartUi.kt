package com.training.trackplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import com.training.trackplanner.analysis.badminton.BadmintonTransferColorPalette
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.AnalysisChartTemporalPolicy
import com.training.trackplanner.analysis.trends.BarItem
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.DetailChartMode
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.analysis.trends.label
import java.util.Locale
import java.time.LocalDate
import kotlin.math.abs

@Composable
internal fun AnalysisChartSpecView(spec: ChartSpec) {
    when (spec.type) {
        ChartType.LINE -> AnalysisTrendChart(spec = spec, modifier = Modifier.height(150.dp))
        ChartType.BAR,
        ChartType.HORIZONTAL_BAR,
        ChartType.PIE -> AnalysisBarList(spec.bars.ifEmpty {
            spec.slices.map { slice -> BarItem(slice.label, slice.value) }
        })
        ChartType.STACKED_BAR -> AnalysisStackedBarChart(spec = spec, modifier = Modifier.height(170.dp))
        ChartType.SCATTER -> AnalysisScatterChart(spec = spec, modifier = Modifier.height(170.dp))
    }
}

@Composable
internal fun analysisChartPalette(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.outline,
        MaterialTheme.colorScheme.primaryContainer
    )

@Composable
internal fun AnalysisTrendChart(spec: ChartSpec, modifier: Modifier = Modifier) {
    val colors = analysisChartPalette()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val forecastColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val allValues = spec.lineSeries.flatMap { series -> series.points.mapNotNull { point -> point.value?.takeIf(Double::isFinite) } }
        .plus(spec.forecastRange?.points?.flatMap { point -> listOf(point.lower, point.upper) }.orEmpty())
        .filter(Double::isFinite)
    if (allValues.isEmpty()) {
        InfoCard("기록 부족")
        return
    }
    val min = spec.yMin ?: ((allValues.minOrNull() ?: 50.0).coerceAtMost(100.0) - 8.0)
    val max = spec.yMax ?: ((allValues.maxOrNull() ?: 160.0).coerceAtLeast(100.0) + 8.0)
    val domain = AnalysisChartTemporalPolicy.domain(spec)
    val domainIndex = domain.withIndex().associate { (index, date) -> date to index }
    val accessibility = analysisChartContentDescription(spec)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibility
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = modifier.fillMaxWidth()) {
            repeat(3) { index ->
                val y = size.height * (index + 1) / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val totalCount = domain.size.coerceAtLeast(2)
            fun xAt(index: Int): Float =
                if (domain.size <= 1) size.width / 2f else size.width * index / (totalCount - 1)
            fun xAt(date: LocalDate): Float = xAt(domainIndex[date] ?: 0)
            fun yAt(value: Double): Float {
                val ratio = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
                return (size.height - (size.height * ratio)).toFloat()
            }
            spec.forecastRange?.points?.takeIf { it.isNotEmpty() }?.let { forecast ->
                val path = Path()
                forecast.forEachIndexed { index, point ->
                    val x = xAt(point.weekStart)
                    val y = yAt(point.upper)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                forecast.asReversed().forEach { point ->
                    path.lineTo(xAt(point.weekStart), yAt(point.lower))
                }
                path.close()
                drawPath(path, forecastColor)
            }
            spec.lineSeries.forEachIndexed { seriesIndex, series ->
                val path = Path()
                var previousDomainIndex: Int? = null
                var hasPoint = false
                series.points.sortedBy { point -> point.weekStart }.forEach { point ->
                    val value = point.value?.takeIf(Double::isFinite)
                    val index = domainIndex[point.weekStart]
                    if (value == null || index == null) {
                        previousDomainIndex = null
                        return@forEach
                    }
                    val x = xAt(index)
                    val y = yAt(value)
                    if (previousDomainIndex?.plus(1) == index) path.lineTo(x, y) else path.moveTo(x, y)
                    hasPoint = true
                    previousDomainIndex = index
                    drawCircle(colors[seriesIndex % colors.size], radius = 4f, center = Offset(x, y))
                }
                if (hasPoint) {
                    drawPath(path, colors[seriesIndex % colors.size], style = Stroke(width = 4f))
                }
            }
        }
        spec.timeGranularity?.let { granularity ->
            AnalysisTimeAxisLabels(domain, granularity)
        }
    }
}

@Composable
private fun AnalysisStackedBarChart(spec: ChartSpec, modifier: Modifier = Modifier) {
    val groups = spec.stackedBars.filter { group -> group.segments.any { it.value > 0.0 } }
    if (groups.isEmpty()) {
        InfoCard("주별로 표시할 배드민턴 관련 훈련 기록이 없습니다.")
        return
    }
    val colors = analysisChartPalette()
    val labels = groups.flatMap { group -> group.segments.map { it.label } }.distinct()
    val colorIndexByLabel = groups
        .flatMap { group -> group.segments }
        .groupBy { segment -> segment.label }
        .mapValues { (_, segments) -> segments.firstNotNullOfOrNull { it.colorIndex } }
    val colorKeyByLabel = groups
        .flatMap { group -> group.segments }
        .groupBy { segment -> segment.label }
        .mapValues { (_, segments) -> segments.firstNotNullOfOrNull { it.colorKey } }
    val maxTotal = groups.maxOf { group -> group.segments.sumOf { it.value } }.coerceAtLeast(1.0)
    val domain = AnalysisChartTemporalPolicy.domain(spec)
    val domainIndex = domain.withIndex().associate { (index, date) -> date to index }
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = analysisChartContentDescription(spec)
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = modifier.fillMaxWidth()) {
            val slotCount = if (domain.isNotEmpty()) domain.size else groups.size
            val slot = size.width / slotCount.coerceAtLeast(1)
            val barWidth = slot * 0.62f
            groups.forEachIndexed { groupIndex, group ->
                val index = group.weekStart?.let(domainIndex::get) ?: groupIndex
                var bottom = size.height
                group.segments.forEach { segment ->
                    val height = (size.height * (segment.value / maxTotal)).toFloat()
                    val colorIndex = segment.colorIndex ?: labels.indexOf(segment.label).coerceAtLeast(0)
                    val color = segment.colorKey?.let { Color(BadmintonTransferColorPalette.colorForKey(it)) }
                        ?: colors[colorIndex % colors.size]
                    drawRect(
                        color = color,
                        topLeft = Offset(index * slot + (slot - barWidth) / 2f, bottom - height),
                        size = Size(barWidth, height)
                    )
                    bottom -= height
                }
            }
        }
        spec.timeGranularity?.let { granularity ->
            AnalysisTimeAxisLabels(domain, granularity)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val colorIndex = colorIndexByLabel[label] ?: index
                val color = colorKeyByLabel[label]?.let { Color(BadmintonTransferColorPalette.colorForKey(it)) }
                    ?: colors[colorIndex % colors.size]
                Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.22f)) {
                    Text(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), text = label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AnalysisTimeAxisLabels(
    domain: List<LocalDate>,
    granularity: ChartTimeGranularity
) {
    if (domain.isEmpty()) return
    val visible = AnalysisChartTemporalPolicy.axisLabelDates(domain, granularity)
    val dates = domain.filter { it in visible }
    Layout(
        content = {
            dates.forEach { date ->
                Text(
                    text = AnalysisChartTemporalPolicy.compactAxisLabel(
                        date = date,
                        granularity = granularity,
                        domain = domain,
                        includeWeekday = granularity == ChartTimeGranularity.DAILY && domain.size <= 7
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val maxLabelWidth = 72.dp.roundToPx()
        val placeables = measurables.map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = maxLabelWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
            )
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val domainIndex = domain.indexOf(dates[index])
                val center = if (domain.size <= 1) {
                    constraints.maxWidth / 2
                } else {
                    constraints.maxWidth * domainIndex / domain.lastIndex
                }
                val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                val x = (center - placeable.width / 2).coerceIn(0, maxX)
                placeable.placeRelative(x, 0)
            }
        }
    }
}

internal fun analysisChartPeriodLabel(spec: ChartSpec): String? =
    spec.timeGranularity?.let { granularity ->
        AnalysisChartTemporalPolicy.periodLabel(AnalysisChartTemporalPolicy.domain(spec), granularity)
    }

internal fun analysisChartContentDescription(spec: ChartSpec): String {
    val granularity = spec.timeGranularity ?: return spec.title
    val domain = AnalysisChartTemporalPolicy.domain(spec)
    val lineDescriptions = spec.lineSeries.flatMap { series ->
        series.points.mapNotNull { point ->
            val value = point.value?.takeIf(Double::isFinite) ?: return@mapNotNull null
            val date = AnalysisChartTemporalPolicy.detailLabel(point.weekStart, granularity, domain)
            "$date, ${series.label} ${formatChartAccessibilityValue(value, spec.valueUnit)}"
        }
    }
    val stackedDescriptions = spec.stackedBars.flatMap { group ->
        val weekStart = group.weekStart ?: return@flatMap emptyList()
        val date = AnalysisChartTemporalPolicy.detailLabel(weekStart, granularity, domain)
        group.segments.map { segment ->
            "$date, ${segment.label} ${formatChartAccessibilityValue(segment.value, spec.valueUnit)}"
        }
    }
    return (listOf(spec.title) + lineDescriptions + stackedDescriptions).joinToString(". ")
}

private fun formatChartAccessibilityValue(value: Double, unit: String?): String {
    val rendered = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
    val spokenUnit = when (unit) {
        "kg" -> "킬로그램"
        "%" -> "퍼센트"
        null, "" -> ""
        else -> unit
    }
    return "$rendered$spokenUnit"
}

@Composable
private fun AnalysisScatterChart(spec: ChartSpec, modifier: Modifier = Modifier) {
    val pointColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val points = spec.scatterPoints
    if (points.size < 2) {
        InfoCard("관계 분석은 기록이 더 필요합니다.")
        return
    }
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    Canvas(modifier = modifier.fillMaxWidth()) {
        repeat(3) { index ->
            val position = (index + 1) / 4f
            drawLine(gridColor, Offset(size.width * position, 0f), Offset(size.width * position, size.height))
            drawLine(gridColor, Offset(0f, size.height * position), Offset(size.width, size.height * position))
        }
        points.forEach { point ->
            val xRatio = if (abs(maxX - minX) < 0.001) 0.5 else (point.x - minX) / (maxX - minX)
            val yRatio = if (abs(maxY - minY) < 0.001) 0.5 else (point.y - minY) / (maxY - minY)
            drawCircle(
                color = pointColor,
                radius = 5f,
                center = Offset((size.width * xRatio).toFloat(), (size.height - size.height * yRatio).toFloat())
            )
        }
    }
}

@Composable
private fun AnalysisBarList(items: List<BarItem>) {
    if (items.isEmpty()) {
        InfoCard("기록 부족")
        return
    }
    val max = items.maxOf { abs(it.value) }.coerceAtLeast(1.0)
    val colors = analysisChartPalette()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(6).forEachIndexed { index, item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.label, style = MaterialTheme.typography.labelMedium)
                    Text(formatAnalysisValue(item.value), style = MaterialTheme.typography.labelMedium)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth((abs(item.value) / max).coerceIn(0.04, 1.0).toFloat()).height(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = item.colorKey?.let { Color(BadmintonTransferColorPalette.colorForKey(it)) }
                        ?: colors[(item.colorIndex ?: index) % colors.size]
                ) {}
            }
        }
    }
}

@Composable
internal fun AnalysisChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            AnalysisSelectableChip(label, index == selected) { onSelect(index) }
        }
    }
}

@Composable
internal fun AnalysisMetricChipRow(
    metrics: List<TrendMetricId>,
    selectedMetrics: List<TrendMetricId>,
    onToggle: (TrendMetricId) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metrics.forEach { metric ->
            AnalysisSelectableChip(metric.label(), metric in selectedMetrics) { onToggle(metric) }
        }
    }
}

@Composable
internal fun AnalysisSelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    QuietChoiceChip(label = label, selected = selected, onClick = onClick)
}

@Composable
internal fun AnalysisConfidencePill(confidence: AnalysisConfidence) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = "신뢰도 ${analysisConfidenceLabel(confidence)}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

internal fun analysisConfidenceLabel(confidence: AnalysisConfidence): String = when (confidence) {
    AnalysisConfidence.LOW -> "낮음"
    AnalysisConfidence.MEDIUM_LOW -> "보통 이하"
    AnalysisConfidence.MEDIUM -> "보통"
    AnalysisConfidence.HIGH -> "높음"
}

internal fun DetailChartMode.analysisLabel(): String = when (this) {
    DetailChartMode.TREND -> "추세"
    DetailChartMode.COMPOSITION -> "비중"
    DetailChartMode.CONTRIBUTION -> "기여도"
    DetailChartMode.RANKING -> "랭킹"
    DetailChartMode.RELATIONSHIP -> "관계"
}

internal fun formatAnalysisValue(value: Double): String = String.format(Locale.US, "%.2f", value)
