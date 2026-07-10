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
    onStrengthClick: () -> Unit,
    onRelationshipLabClick: () -> Unit,
    onLaggedLabClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnalysisHubCard(
            title = "오늘 컨디션 및 피로도 분석",
            body = "주의할 피로 축, 오늘 조절할 운동, 피로도 변화",
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
        Text("실험실", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AnalysisHubCard(
            title = "관계 탐색",
            body = "두 지표의 같은 기간 관계를 살펴봅니다.",
            onClick = onRelationshipLabClick
        )
        AnalysisHubCard(
            title = "시계열 분석",
            body = "한 지표 변화 뒤 다른 지표가 몇 주 뒤 어떻게 움직였는지 봅니다.",
            onClick = onLaggedLabClick
        )
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
