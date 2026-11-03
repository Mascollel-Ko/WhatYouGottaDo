package com.training.trackplanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.v22.BBlockDefinition
import com.training.trackplanner.data.v22.BBlockPeriodization
import com.training.trackplanner.data.v22.BVolumePattern

@Composable
internal fun BBlockPatternPicker(
    weeks: Int,
    selected: BVolumePattern,
    onSelect: (BVolumePattern) -> Unit,
    enabled: Boolean = true
) {
    val choices = listOf(
        BVolumePattern.STEADY to "유지형",
        BVolumePattern.PROGRESSIVE to "점진형",
        BVolumePattern.WAVE to "파동형",
        BVolumePattern.ACCUMULATE_RECOVER to "축적·회복형"
    ).filter { (pattern, _) -> runCatching { BBlockDefinition(weeks, pattern = pattern) }.isSuccess }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (pattern, name) ->
                val active = pattern == selected
                val lineColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Card(
                    modifier = Modifier.width(136.dp).clickable(enabled = enabled) { onSelect(pattern) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val stages = BBlockPeriodization.steps(BBlockDefinition(weeks, pattern = pattern))
                        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                            val bottom = size.height - 7f
                            val ys = stages.map { (bottom - 10f - (it + 1) * 8f).coerceIn(4f, bottom) }
                            drawLine(lineColor.copy(alpha = .35f), Offset(4f, 4f), Offset(4f, bottom), 1f)
                            drawLine(lineColor.copy(alpha = .35f), Offset(4f, bottom), Offset(size.width - 3f, bottom), 1f)
                            val pts = ys.mapIndexed { i, y ->
                                Offset(8f + i * (size.width - 16f) / (stages.size - 1).coerceAtLeast(1), y)
                            }
                            pts.zipWithNext { a, b -> drawLine(lineColor, a, b, strokeWidth = 3f) }
                            pts.forEach { drawCircle(lineColor, radius = 3f, center = it) }
                        }
                        Text(name, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("세로축: 볼륨", style = MaterialTheme.typography.labelSmall)
            Text("가로축: 주차", style = MaterialTheme.typography.labelSmall)
        }
    }
}
