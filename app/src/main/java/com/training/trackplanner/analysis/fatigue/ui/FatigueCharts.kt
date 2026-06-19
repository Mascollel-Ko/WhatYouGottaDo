package com.training.trackplanner.analysis.fatigue.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.fatigue.FatigueContributionSeries
import com.training.trackplanner.analysis.fatigue.FatigueSeries
import com.training.trackplanner.analysis.fatigue.FatigueTimePoint
import java.time.format.DateTimeFormatter

private data class ChartLine(
    val key: String,
    val points: List<FatigueTimePoint>
)

@Composable
internal fun FatigueTrendChart(
    series: List<FatigueSeries>,
    selectedKeys: Set<String>,
    emptyMessage: String = "운동 기록이 쌓이면 표시됩니다.",
    modifier: Modifier = Modifier
) {
    val lines = series.filter { it.key in selectedKeys }.map { ChartLine(it.key, it.points) }
    FatigueLineChart(lines, fixedMaximum = 100.0, emptyMessage = emptyMessage, modifier = modifier)
}

@Composable
internal fun FatigueContributionChart(
    series: List<FatigueContributionSeries>,
    selectedKeys: Set<String>,
    emptyMessage: String = "운동 기록이 쌓이면 표시됩니다.",
    modifier: Modifier = Modifier
) {
    val lines = series.filter { it.sourceKey in selectedKeys }.map { ChartLine(it.sourceKey, it.points) }
    FatigueLineChart(lines, fixedMaximum = null, emptyMessage = emptyMessage, modifier = modifier)
}

@Composable
private fun FatigueLineChart(
    lines: List<ChartLine>,
    fixedMaximum: Double?,
    emptyMessage: String,
    modifier: Modifier
) {
    val points = lines.flatMap { it.points }
    if (points.isEmpty()) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        Color(0xFF00897B),
        Color(0xFF6D4C41),
        Color(0xFF5E35B1)
    )
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val maximum = fixedMaximum ?: points.maxOf { it.value }.coerceAtLeast(1.0) * 1.08
    val dates = points.map { it.date }.distinct().sorted()
    val firstDate = dates.first()
    val lastDate = dates.last()
    val formatter = DateTimeFormatter.ofPattern("M.d")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = modifier.fillMaxWidth().height(170.dp)) {
            repeat(3) { index ->
                val y = size.height * (index + 1) / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            lines.forEachIndexed { lineIndex, line ->
                val path = Path()
                var started = false
                line.points.forEach { point ->
                    val dateSpan = (lastDate.toEpochDay() - firstDate.toEpochDay()).coerceAtLeast(1L)
                    val x = size.width * (point.date.toEpochDay() - firstDate.toEpochDay()).toFloat() / dateSpan
                    val y = size.height * (1f - (point.value / maximum).coerceIn(0.0, 1.0).toFloat())
                    if (started) path.lineTo(x, y) else {
                        path.moveTo(x, y)
                        started = true
                    }
                    drawCircle(colors[lineIndex % colors.size], radius = 3.5f, center = Offset(x, y))
                }
                if (started) {
                    drawPath(path, colors[lineIndex % colors.size], style = Stroke(width = 3f))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(firstDate.format(formatter), style = MaterialTheme.typography.labelSmall)
            Text(lastDate.format(formatter), style = MaterialTheme.typography.labelSmall)
        }
    }
}
