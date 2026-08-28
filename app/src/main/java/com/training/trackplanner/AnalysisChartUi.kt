package com.training.trackplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.training.trackplanner.localization.LocalizedPresentation
import com.training.trackplanner.localization.localizedUiText
import java.util.Locale
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class PlotChartViewport(
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double
) {
    val xSpan: Double get() = xMax - xMin
    val ySpan: Double get() = yMax - yMin

    fun xAtFraction(fraction: Double): Double = xMin + xSpan * fraction.coerceIn(0.0, 1.0)
    fun yAtFraction(fraction: Double): Double = yMax - ySpan * fraction.coerceIn(0.0, 1.0)
}

internal object PlotChartZoomPolicy {
    fun auto(domainSize: Int, yMin: Double, yMax: Double): PlotChartViewport {
        val xBounds = if (domainSize <= 1) -0.5 to 0.5 else 0.0 to (domainSize - 1).toDouble()
        val safeYBounds = if (yMin.isFinite() && yMax.isFinite() && yMax > yMin) {
            yMin to yMax
        } else {
            val center = listOf(yMin, yMax).firstOrNull(Double::isFinite) ?: 0.0
            center - 0.5 to center + 0.5
        }
        return PlotChartViewport(
            xMin = xBounds.first,
            xMax = xBounds.second,
            yMin = safeYBounds.first,
            yMax = safeYBounds.second
        )
    }

    fun update(
        current: PlotChartViewport,
        full: PlotChartViewport,
        gestureScale: Float,
        focalXFraction: Double,
        focalYFraction: Double,
        panXFraction: Double = 0.0,
        panYFraction: Double = 0.0
    ): PlotChartViewport {
        val base = current.takeIf(::isValid) ?: full
        if (!isValid(full) || !gestureScale.isFinite() || gestureScale <= 0f) return base

        val scale = gestureScale.toDouble()
        val focalX = focalXFraction.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.5
        val focalY = focalYFraction.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.5
        val panX = panXFraction.takeIf(Double::isFinite) ?: 0.0
        val panY = panYFraction.takeIf(Double::isFinite) ?: 0.0
        val minXSpan = max(full.xSpan * 0.05, 0.01).coerceAtMost(full.xSpan)
        val minYSpan = max(full.ySpan * 0.05, 0.01).coerceAtMost(full.ySpan)
        val xSpan = (base.xSpan / scale).coerceIn(minXSpan, full.xSpan)
        val ySpan = (base.ySpan / scale).coerceIn(minYSpan, full.ySpan)
        val focalXValue = base.xAtFraction(focalX)
        val focalYValue = base.yAtFraction(focalY)
        val rawXMin = focalXValue - focalX * xSpan - panX * xSpan
        val rawYMin = focalYValue - (1.0 - focalY) * ySpan + panY * ySpan
        val xMin = rawXMin.coerceIn(full.xMin, full.xMax - xSpan)
        val yMin = rawYMin.coerceIn(full.yMin, full.yMax - ySpan)
        return PlotChartViewport(xMin, xMin + xSpan, yMin, yMin + ySpan)
    }

    fun isAuto(viewport: PlotChartViewport, full: PlotChartViewport): Boolean =
        abs(viewport.xMin - full.xMin) < 1e-9 &&
            abs(viewport.xMax - full.xMax) < 1e-9 &&
            abs(viewport.yMin - full.yMin) < 1e-9 &&
            abs(viewport.yMax - full.yMax) < 1e-9

    fun bounds(xMin: Double, xMax: Double, yMin: Double, yMax: Double): PlotChartViewport {
        fun safe(min: Double, max: Double): Pair<Double, Double> =
            if (min.isFinite() && max.isFinite() && max > min) min to max
            else (listOf(min, max).firstOrNull(Double::isFinite) ?: 0.0).let { it - 0.5 to it + 0.5 }
        val x = safe(xMin, xMax)
        val y = safe(yMin, yMax)
        return PlotChartViewport(x.first, x.second, y.first, y.second)
    }

    private fun isValid(viewport: PlotChartViewport): Boolean =
        viewport.xMin.isFinite() && viewport.xMax.isFinite() && viewport.xSpan > 0.0 &&
            viewport.yMin.isFinite() && viewport.yMax.isFinite() && viewport.ySpan > 0.0
}

internal data class ChartInspectionValue(val label: String, val value: Double, val xValue: Double? = null)
internal data class ChartInspection(val domainLabel: String, val values: List<ChartInspectionValue>)
internal data class InspectionLabelPlacement(
    val originalIndex: Int,
    val desiredY: Float,
    val placedY: Float
) {
    val needsLeaderLine: Boolean get() = abs(desiredY - placedY) > 0.5f
}

internal fun placeInspectionLabels(
    desiredYs: List<Float>,
    top: Float,
    bottom: Float,
    minimumGap: Float
): List<InspectionLabelPlacement> {
    if (desiredYs.isEmpty() || bottom <= top) return emptyList()
    val sorted = desiredYs.withIndex().sortedWith(compareBy({ it.value }, { it.index }))
    val placed = FloatArray(sorted.size)
    sorted.forEachIndexed { index, item ->
        placed[index] = max(item.value.coerceIn(top, bottom), if (index == 0) top else placed[index - 1] + minimumGap)
    }

    if (placed.last() > bottom) {
        placed[placed.lastIndex] = bottom
        for (index in placed.lastIndex - 1 downTo 0) {
            placed[index] = minOf(placed[index], placed[index + 1] - minimumGap)
        }
    }
    if (placed.first() < top) {
        placed[0] = top
        for (index in 1..placed.lastIndex) placed[index] = max(placed[index], placed[index - 1] + minimumGap)
    }
    return sorted.mapIndexed { index, item ->
        InspectionLabelPlacement(item.index, item.value, placed[index].coerceIn(top, bottom))
    }.sortedBy(InspectionLabelPlacement::originalIndex)
}

private fun Modifier.cartesianChartGestures(
    fullViewport: PlotChartViewport,
    viewport: PlotChartViewport,
    onViewportChange: (PlotChartViewport) -> Unit,
    onInspect: (Offset) -> Unit
): Modifier = testTag("analysis-cartesian-chart")
    .pointerInput(fullViewport) {
        awaitEachGesture {
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    val scale = event.calculateZoom()
                    val centroid = event.calculateCentroid(useCurrent = true)
                    val pan = event.calculatePan()
                    if (scale != 1f || pan.x != 0f || pan.y != 0f) {
                        onViewportChange(
                            PlotChartZoomPolicy.update(
                                viewport, fullViewport, scale,
                                centroid.x.toDouble() / size.width.coerceAtLeast(1),
                                centroid.y.toDouble() / size.height.coerceAtLeast(1),
                                pan.x.toDouble() / size.width.coerceAtLeast(1),
                                pan.y.toDouble() / size.height.coerceAtLeast(1)
                            )
                        )
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(fullViewport, viewport) {
        detectTapGestures(onLongPress = { offset ->
            onInspect(
                Offset(
                    offset.x / size.width.coerceAtLeast(1),
                    offset.y / size.height.coerceAtLeast(1)
                )
            )
        })
    }

@Composable
private fun ChartInspectionPanel(inspection: ChartInspection?, unit: String?) {
    inspection ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("chart-value-inspection"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(localizedUiText(inspection.domainLabel), style = MaterialTheme.typography.labelMedium)
            inspection.values.forEach { item ->
                val xPrefix = item.xValue?.let { "x ${formatAnalysisValue(it)} · " }.orEmpty()
                Text(
                    "$xPrefix${localizedUiText(item.label)} ${formatInspectionValue(item.value, unit)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatInspectionValue(value: Double, unit: String?): String {
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
    return if (unit.isNullOrBlank()) number else "$number $unit"
}

internal fun inspectLineChart(
    spec: ChartSpec,
    domain: List<LocalDate>,
    viewport: PlotChartViewport,
    xFraction: Double
): ChartInspection? {
    if (domain.isEmpty()) return null
    val index = viewport.xAtFraction(xFraction).roundToInt().coerceIn(0, domain.lastIndex)
    if (index.toDouble() !in viewport.xMin..viewport.xMax) return null
    val date = domain[index]
    val values = spec.lineSeries.mapNotNull { series ->
        series.points.firstOrNull { it.weekStart == date }?.value?.takeIf(Double::isFinite)
            ?.let { ChartInspectionValue(series.label, it) }
    }
    if (values.isEmpty()) return null
    val label = spec.timeGranularity?.let { AnalysisChartTemporalPolicy.detailLabel(date, it, domain) }
        ?: date.toString()
    return ChartInspection(label, values)
}

internal fun inspectStackedAreaChart(
    spec: ChartSpec,
    domain: List<LocalDate>,
    viewport: PlotChartViewport,
    xFraction: Double
): ChartInspection? {
    if (domain.isEmpty()) return null
    val index = viewport.xAtFraction(xFraction).roundToInt().coerceIn(0, domain.lastIndex)
    val date = domain[index]
    val values = spec.stackedAreaLayers.mapNotNull { layer ->
        layer.points.firstOrNull { it.weekStart == date }?.value?.takeIf(Double::isFinite)
            ?.let { ChartInspectionValue(layer.label, it) }
    }
    if (values.isEmpty()) return null
    val label = spec.timeGranularity?.let { AnalysisChartTemporalPolicy.detailLabel(date, it, domain) }
        ?: date.toString()
    return ChartInspection(label, values)
}

internal fun inspectStackedBarChart(
    spec: ChartSpec,
    groups: List<com.training.trackplanner.analysis.trends.StackedBarGroup>,
    domain: List<LocalDate>,
    viewport: PlotChartViewport,
    xFraction: Double
): ChartInspection? {
    if (groups.isEmpty()) return null
    val domainIndex = domain.withIndex().associate { it.value to it.index }
    val targetIndex = viewport.xAtFraction(xFraction).roundToInt()
    val group = groups.minByOrNull { group ->
        abs((group.weekStart?.let(domainIndex::get) ?: groups.indexOf(group)) - targetIndex)
    } ?: return null
    val label = group.weekStart?.let { date ->
        spec.timeGranularity?.let { AnalysisChartTemporalPolicy.detailLabel(date, it, domain) }
    } ?: group.label
    return ChartInspection(label, group.segments.map { ChartInspectionValue(it.label, it.value) })
}

internal fun inspectScatterChart(
    spec: ChartSpec,
    viewport: PlotChartViewport,
    xFraction: Double,
    yFraction: Double
): ChartInspection? {
    val point = spec.scatterPoints.asSequence()
        .filter { it.x in viewport.xMin..viewport.xMax && it.y in viewport.yMin..viewport.yMax }
        .minByOrNull {
            hypot(
                (it.x - viewport.xMin) / viewport.xSpan - xFraction,
                1.0 - (it.y - viewport.yMin) / viewport.ySpan - yFraction
            )
        } ?: return null
    return ChartInspection(point.label, listOf(ChartInspectionValue("Y", point.y, point.x)))
}

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
        ChartType.STACKED_AREA -> AnalysisStackedAreaChart(spec = spec, modifier = Modifier.height(180.dp))
        ChartType.SCATTER -> AnalysisScatterChart(spec = spec, modifier = Modifier.height(170.dp))
    }
}

@Composable
private fun AnalysisStackedAreaChart(spec: ChartSpec, modifier: Modifier = Modifier) {
    val layers = spec.stackedAreaLayers
    val domain = AnalysisChartTemporalPolicy.domain(spec)
    if (layers.isEmpty() || domain.isEmpty()) {
        InfoCard("기록 부족")
        return
    }
    val valuesByLayer = layers.map { layer ->
        layer.points.associate { point -> point.weekStart to (point.value?.takeIf(Double::isFinite) ?: 0.0) }
    }
    val maxTotal = domain.maxOf { date -> valuesByLayer.sumOf { values -> values[date] ?: 0.0 } }
        .coerceAtLeast(1.0)
    val fullViewport = PlotChartZoomPolicy.auto(domain.size, 0.0, maxTotal)
    var viewport by remember(spec) { mutableStateOf(fullViewport) }
    var inspection by remember(spec) { mutableStateOf<ChartInspection?>(null) }
    val colors = analysisChartPalette()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val accessibility = localizedAnalysisChartContentDescription(spec)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = accessibility },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = modifier.fillMaxWidth().cartesianChartGestures(
                fullViewport,
                viewport,
                onViewportChange = { viewport = it; inspection = null },
                onInspect = { point -> inspection = inspectStackedAreaChart(spec, domain, viewport, point.x.toDouble()) }
            )
        ) {
            repeat(3) { index ->
                val y = size.height * (index + 1) / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            fun xAt(index: Int): Float =
                (size.width * ((index - viewport.xMin) / viewport.xSpan)).toFloat()
            fun yAt(value: Double): Float =
                (size.height - size.height * ((value - viewport.yMin) / viewport.ySpan)).toFloat()

            clipRect {
              val lowerByDate = DoubleArray(domain.size)
              layers.forEachIndexed { layerIndex, _ ->
                val upperByDate = DoubleArray(domain.size) { index ->
                    lowerByDate[index] + (valuesByLayer[layerIndex][domain[index]] ?: 0.0)
                }
                val area = Path()
                domain.indices.forEach { index ->
                    val x = xAt(index)
                    val y = yAt(upperByDate[index])
                    if (index == 0) area.moveTo(x, y) else area.lineTo(x, y)
                }
                domain.indices.reversed().forEach { index ->
                    area.lineTo(xAt(index), yAt(lowerByDate[index]))
                }
                area.close()
                val color = colors[layerIndex % colors.size]
                drawPath(area, color.copy(alpha = 0.52f))
                val boundary = Path()
                domain.indices.forEach { index ->
                    val x = xAt(index)
                    val y = yAt(upperByDate[index])
                    if (index == 0) boundary.moveTo(x, y) else boundary.lineTo(x, y)
                }
                drawPath(boundary, color, style = Stroke(width = 3f))
                upperByDate.copyInto(lowerByDate)
              }
            }
        }
        spec.timeGranularity?.let { AnalysisTimeAxisLabels(domain, it, viewport) }
        ChartInspectionPanel(inspection, spec.valueUnit)
        if (!PlotChartZoomPolicy.isAuto(viewport, fullViewport)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewport = fullViewport; inspection = null }) { Text("축 맞춤") }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            layers.forEachIndexed { index, layer ->
                Surface(shape = RoundedCornerShape(8.dp), color = colors[index % colors.size].copy(alpha = 0.22f)) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        text = localizedUiText(layer.label),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
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
    val referenceColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val intervalBands = spec.intervalBands + listOfNotNull(spec.intervalBand)
    val allValues = spec.lineSeries.flatMap { series -> series.points.mapNotNull { point -> point.value?.takeIf(Double::isFinite) } }
        .plus(spec.forecastRange?.points?.flatMap { point -> listOf(point.lower, point.upper) }.orEmpty())
        .plus(intervalBands.flatMap { band -> band.points.flatMap { point -> listOf(point.lower, point.upper) } })
        .plus(spec.horizontalReferenceValues)
        .filter(Double::isFinite)
    if (allValues.isEmpty()) {
        InfoCard("기록 부족")
        return
    }
    val domain = AnalysisChartTemporalPolicy.domain(spec)
    val fullViewport = PlotChartZoomPolicy.auto(
        domainSize = domain.size,
        yMin = spec.yMin ?: ((allValues.minOrNull() ?: 50.0).coerceAtMost(100.0) - 8.0),
        yMax = spec.yMax ?: ((allValues.maxOrNull() ?: 160.0).coerceAtLeast(100.0) + 8.0)
    )
    var viewport by remember(spec) { mutableStateOf(fullViewport) }
    var inspection by remember(spec) { mutableStateOf<ChartInspection?>(null) }
    val visibleViewport = viewport
    val min = visibleViewport.yMin
    val max = visibleViewport.yMax
    val domainIndex = domain.withIndex().associate { (index, date) -> date to index }
    val accessibility = localizedAnalysisChartContentDescription(spec)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibility
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val zoomModifier = Modifier.cartesianChartGestures(
            fullViewport = fullViewport,
            viewport = viewport,
            onViewportChange = {
                viewport = it
                inspection = null
            },
            onInspect = { offset ->
                inspection = inspectLineChart(
                    spec, domain, viewport,
                    offset.x.toDouble()
                )
            }
        )
        Canvas(modifier = modifier.fillMaxWidth().then(zoomModifier)) {
            repeat(3) { index ->
                val y = size.height * (index + 1) / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            fun xAt(index: Int): Float =
                (size.width * ((index - visibleViewport.xMin) / visibleViewport.xSpan)).toFloat()
            fun xAt(date: LocalDate): Float = xAt(domainIndex[date] ?: 0)
            fun yAt(value: Double): Float {
                val ratio = (value - min) / (max - min)
                return (size.height - (size.height * ratio)).toFloat()
            }
            clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
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
                intervalBands.forEachIndexed { bandIndex, band ->
                    val interval = band.points
                    if (interval.isEmpty()) return@forEachIndexed
                    val path = Path()
                    interval.forEachIndexed { index, point ->
                        val x = xAt(point.date)
                        val y = yAt(point.upper)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    interval.asReversed().forEach { point ->
                        path.lineTo(xAt(point.date), yAt(point.lower))
                    }
                    path.close()
                    val color = band.colorKey?.let(::strengthPerformanceTargetColor)
                        ?: colors[bandIndex % colors.size]
                    drawPath(path, color.copy(alpha = band.alpha.coerceIn(0f, 1f)))
                }
                spec.horizontalReferenceValues.filter { value -> value in min..max }.forEach { value ->
                    val y = yAt(value)
                    drawLine(
                        color = referenceColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f
                    )
                }
                spec.lineSeries.forEachIndexed { seriesIndex, series ->
                    val path = Path()
                    var previousDomainIndex: Int? = null
                    var hasPoint = false
                    val seriesColor = series.colorKey?.let(::strengthPerformanceTargetColor)
                        ?: colors[seriesIndex % colors.size]
                    series.points.sortedBy { point -> point.weekStart }.forEach { point ->
                        val value = point.value?.takeIf(Double::isFinite)
                        val index = domainIndex[point.weekStart]
                        if (value == null || index == null) {
                            previousDomainIndex = null
                            return@forEach
                        }
                        val x = xAt(index)
                        val y = yAt(value)
                        if (
                            previousDomainIndex != null &&
                            (series.connectAcrossDomainGaps || previousDomainIndex?.plus(1) == index)
                        ) {
                            path.lineTo(x, y)
                        } else {
                            path.moveTo(x, y)
                        }
                        hasPoint = true
                        previousDomainIndex = index
                        drawCircle(
                            color = seriesColor,
                            radius = 4f,
                            center = Offset(x, y),
                            style = if (series.hollowPoints) {
                                Stroke(width = 2f)
                            } else {
                                androidx.compose.ui.graphics.drawscope.Fill
                            }
                        )
                    }
                    if (hasPoint && series.connectPoints) {
                        drawPath(path, seriesColor, style = Stroke(width = 4f))
                    }
                }
            }
        }
        spec.timeGranularity?.let { granularity ->
            AnalysisTimeAxisLabels(
                domain = domain,
                granularity = granularity,
                xViewport = visibleViewport
            )
        }
        ChartInspectionPanel(inspection, spec.valueUnit)
        if (!PlotChartZoomPolicy.isAuto(viewport, fullViewport)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = localizedUiText("확대됨"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { viewport = fullViewport }) {
                    Text(localizedUiText("축 맞춤"))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AnalysisStackedBarChart(spec: ChartSpec, modifier: Modifier = Modifier) {
    val groups = if (spec.preserveZeroStackedBarCategories) {
        spec.stackedBars
    } else {
        spec.stackedBars.filter { group -> group.segments.any { it.value > 0.0 } }
    }
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
    val slotCount = if (domain.isNotEmpty()) domain.size else groups.size
    val fullViewport = PlotChartZoomPolicy.auto(slotCount, 0.0, maxTotal)
    var viewport by remember(spec) { mutableStateOf(fullViewport) }
    var inspection by remember(spec) { mutableStateOf<ChartInspection?>(null) }
    val accessibility = localizedAnalysisChartContentDescription(spec)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibility
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = modifier.fillMaxWidth().cartesianChartGestures(
                fullViewport,
                viewport,
                onViewportChange = { viewport = it; inspection = null },
                onInspect = { point ->
                    inspection = inspectStackedBarChart(spec, groups, domain, viewport, point.x.toDouble())
                }
            )
        ) {
            val slot = size.width / viewport.xSpan.coerceAtLeast(1.0).toFloat()
            val barWidth = slot * 0.62f
            clipRect {
              groups.forEachIndexed { groupIndex, group ->
                val index = group.weekStart?.let(domainIndex::get) ?: groupIndex
                val centerX = (size.width * ((index - viewport.xMin) / viewport.xSpan)).toFloat()
                var cumulative = 0.0
                group.segments.forEach { segment ->
                    val lowerY = size.height - size.height * ((cumulative - viewport.yMin) / viewport.ySpan).toFloat()
                    cumulative += segment.value
                    val upperY = size.height - size.height * ((cumulative - viewport.yMin) / viewport.ySpan).toFloat()
                    val colorIndex = segment.colorIndex ?: labels.indexOf(segment.label).coerceAtLeast(0)
                    val color = segment.colorKey?.let { Color(BadmintonTransferColorPalette.colorForKey(it)) }
                        ?: colors[colorIndex % colors.size]
                    drawRect(
                        color = color,
                        topLeft = Offset(centerX - barWidth / 2f, upperY),
                        size = Size(barWidth, lowerY - upperY)
                    )
                }
              }
            }
        }
        spec.timeGranularity?.let { granularity ->
            AnalysisTimeAxisLabels(domain, granularity, viewport)
        }
        ChartInspectionPanel(inspection, spec.valueUnit)
        if (!PlotChartZoomPolicy.isAuto(viewport, fullViewport)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewport = fullViewport; inspection = null }) { Text("축 맞춤") }
            }
        }
        if (spec.wrapStackedBarLegend) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    AnalysisStackedBarLegendItem(
                        label = label,
                        color = stackedBarLegendColor(
                            label = label,
                            fallbackIndex = index,
                            colorIndexByLabel = colorIndexByLabel,
                            colorKeyByLabel = colorKeyByLabel,
                            colors = colors
                        )
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    AnalysisStackedBarLegendItem(
                        label = label,
                        color = stackedBarLegendColor(
                            label = label,
                            fallbackIndex = index,
                            colorIndexByLabel = colorIndexByLabel,
                            colorKeyByLabel = colorKeyByLabel,
                            colors = colors
                        )
                    )
                }
            }
        }
    }
}

private fun stackedBarLegendColor(
    label: String,
    fallbackIndex: Int,
    colorIndexByLabel: Map<String, Int?>,
    colorKeyByLabel: Map<String, String?>,
    colors: List<Color>
): Color {
    val colorIndex = colorIndexByLabel[label] ?: fallbackIndex
    return colorKeyByLabel[label]?.let { Color(BadmintonTransferColorPalette.colorForKey(it)) }
        ?: colors[colorIndex % colors.size]
}

@Composable
private fun AnalysisStackedBarLegendItem(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.22f)) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = localizedUiText(label),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AnalysisTimeAxisLabels(
    domain: List<LocalDate>,
    granularity: ChartTimeGranularity,
    xViewport: PlotChartViewport? = null
) {
    if (domain.isEmpty()) return
    val indexedDomain = domain.withIndex().filter { indexed ->
        xViewport == null || indexed.index.toDouble() in xViewport.xMin..xViewport.xMax
    }
    if (indexedDomain.isEmpty()) return
    val visibleDomain = indexedDomain.map { indexed -> indexed.value }
    Layout(
        content = {
            visibleDomain.forEach { date ->
                Text(
                    text = AnalysisChartTemporalPolicy.compactAxisLabel(
                        date = date,
                        granularity = granularity,
                        domain = visibleDomain,
                        includeWeekday = granularity == ChartTimeGranularity.DAILY && visibleDomain.size <= 7
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
            )
        }
        val visibleIndices = AnalysisChartTemporalPolicy.visibleAxisLabelIndices(
            domain = visibleDomain,
            granularity = granularity,
            labelWidths = placeables.map { it.width },
            availableWidth = constraints.maxWidth,
            minimumGap = 4.dp.roundToPx()
        )
        val height = visibleIndices.maxOfOrNull { placeables[it].height } ?: 0
        layout(constraints.maxWidth, height) {
            visibleIndices.forEach { domainIndex ->
                val placeable = placeables[domainIndex]
                val originalIndex = indexedDomain[domainIndex].index
                val center = if (xViewport != null) {
                    (constraints.maxWidth *
                        ((originalIndex - xViewport.xMin) / xViewport.xSpan)).toInt()
                } else if (domain.size <= 1) {
                    constraints.maxWidth / 2
                } else {
                    constraints.maxWidth * originalIndex / domain.lastIndex
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

@Composable
private fun localizedAnalysisChartContentDescription(spec: ChartSpec): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(spec, locale) {
        analysisChartContentDescription(
            spec = spec,
            localize = { source -> LocalizedPresentation.uiText(context, source) },
            range = { lower, upper -> context.getString(R.string.chart_accessibility_range, lower, upper) }
        )
    }
}

internal fun analysisChartContentDescription(
    spec: ChartSpec,
    localize: (String) -> String = { it },
    range: (String, String) -> String = { lower, upper -> "$lower–$upper" }
): String {
    val granularity = spec.timeGranularity ?: return localize(spec.title)
    val domain = AnalysisChartTemporalPolicy.domain(spec)
    val lineDescriptions = spec.lineSeries.flatMap { series ->
        series.points.mapNotNull { point ->
            val value = point.value?.takeIf(Double::isFinite) ?: return@mapNotNull null
            val date = localize(AnalysisChartTemporalPolicy.detailLabel(point.weekStart, granularity, domain))
            "$date, ${localize(series.label)} ${formatChartAccessibilityValue(value, spec.valueUnit, localize)}"
        }
    }
    val stackedDescriptions = spec.stackedBars.flatMap { group ->
        val weekStart = group.weekStart ?: return@flatMap emptyList()
        val date = localize(AnalysisChartTemporalPolicy.detailLabel(weekStart, granularity, domain))
        group.segments.map { segment ->
            "$date, ${localize(segment.label)} ${formatChartAccessibilityValue(segment.value, spec.valueUnit, localize)}"
        }
    }
    val stackedAreaDescriptions = spec.stackedAreaLayers.flatMap { layer ->
        layer.points.mapNotNull { point ->
            val value = point.value?.takeIf(Double::isFinite) ?: return@mapNotNull null
            val date = localize(AnalysisChartTemporalPolicy.detailLabel(point.weekStart, granularity, domain))
            "$date, ${localize(layer.label)} ${formatChartAccessibilityValue(value, spec.valueUnit, localize)}"
        }
    }
    val intervalDescriptions = (spec.intervalBands + listOfNotNull(spec.intervalBand)).flatMap { band ->
        band.points.map { point ->
            val date = localize(AnalysisChartTemporalPolicy.detailLabel(point.date, granularity, domain))
            val lower = formatChartAccessibilityValue(point.lower, spec.valueUnit, localize)
            val upper = formatChartAccessibilityValue(point.upper, spec.valueUnit, localize)
            "$date, ${localize(band.label)}, ${range(lower, upper)}"
        }
    }
    return (listOf(localize(spec.title)) + lineDescriptions + intervalDescriptions + stackedDescriptions + stackedAreaDescriptions).joinToString(". ")
}

private fun formatChartAccessibilityValue(
    value: Double,
    unit: String?,
    localize: (String) -> String = { it }
): String {
    val rendered = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
    val spokenUnit = when (unit) {
        "kg" -> localize("킬로그램")
        "%" -> localize("퍼센트")
        null, "" -> ""
        else -> localize(unit)
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
    val fullViewport = PlotChartZoomPolicy.bounds(minX, maxX, minY, maxY)
    var viewport by remember(spec) { mutableStateOf(fullViewport) }
    var inspection by remember(spec) { mutableStateOf<ChartInspection?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Canvas(
        modifier = modifier.fillMaxWidth().cartesianChartGestures(
            fullViewport,
            viewport,
            onViewportChange = { viewport = it; inspection = null },
            onInspect = { point ->
                inspection = inspectScatterChart(spec, viewport, point.x.toDouble(), point.y.toDouble())
            }
        )
      ) {
        clipRect {
          repeat(3) { index ->
              val position = (index + 1) / 4f
              drawLine(gridColor, Offset(size.width * position, 0f), Offset(size.width * position, size.height))
              drawLine(gridColor, Offset(0f, size.height * position), Offset(size.width, size.height * position))
          }
          points.forEach { point ->
            val xRatio = (point.x - viewport.xMin) / viewport.xSpan
            val yRatio = (point.y - viewport.yMin) / viewport.ySpan
            drawCircle(
                color = pointColor,
                radius = 5f,
                center = Offset((size.width * xRatio).toFloat(), (size.height - size.height * yRatio).toFloat())
            )
          }
        }
      }
      ChartInspectionPanel(inspection, spec.valueUnit)
      if (!PlotChartZoomPolicy.isAuto(viewport, fullViewport)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = { viewport = fullViewport; inspection = null }) { Text("축 맞춤") }
          }
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
        items.forEachIndexed { index, item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(localizedUiText(item.label), style = MaterialTheme.typography.labelMedium)
                    Text(formatAnalysisValue(item.value), style = MaterialTheme.typography.labelMedium)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth((abs(item.value) / max).coerceIn(0.0, 1.0).toFloat()).height(8.dp),
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
