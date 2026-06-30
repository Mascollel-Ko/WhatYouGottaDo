package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.AnalysisStats

@Composable
internal fun AnalysisHubContent(
    stats: AnalysisStats,
    onFatigueClick: () -> Unit,
    onBadmintonClick: () -> Unit,
    onStrengthClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnalysisHubCard(
            title = "오늘 컨디션 및 피로도 분석",
            body = "오늘 상태, 피로 축별 기여 운동, 최근 피로도 흐름",
            onClick = onFatigueClick
        )
        AnalysisHubCard(
            title = "배드민턴 전이 분석",
            body = "전이 점검, 배드민턴 관련 훈련량의 일별·주별 흐름",
            onClick = onBadmintonClick
        )
        AnalysisHubCard(
            title = "근력운동 추이 분석",
            body = "벤치·스쿼트·데드리프트 e1RM, 근육군과 반복수 비중",
            onClick = onStrengthClick
        )
        TrainingDistributionSummary(stats)
    }
}

@Composable
private fun AnalysisHubCard(title: String, body: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AnalysisBackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("← 분석 허브로")
    }
}
