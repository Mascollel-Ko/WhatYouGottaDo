package com.training.trackplanner.analysis.fatigue.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.fatigue.FatigueLoadItem
import com.training.trackplanner.analysis.fatigue.FatigueSeries
import com.training.trackplanner.analysis.fatigue.FatigueSimpleUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget

@Composable
internal fun FatigueSimpleView(state: FatigueSimpleUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("전체 피로도", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FatigueTrendChart(
            series = listOf(
                FatigueSeries(
                    key = FatigueTarget.OVERALL.name,
                    label = "OFI",
                    points = state.ofiSeries
                )
            ),
            selectedKeys = setOf(FatigueTarget.OVERALL.name)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LoadList("높은 부하", state.highLoadItems, Modifier.weight(1f))
            LoadList("여유 부하", state.availableLoadItems, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadList(title: String, items: List<FatigueLoadItem>, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        items.take(2).forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.label, style = MaterialTheme.typography.bodySmall)
                Text(item.score.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        if (items.isEmpty()) {
            Text("기록이 부족합니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
