package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.tissue.TissueAnalysisUiMapper
import com.training.trackplanner.analysis.tissue.TissueCurrentState

@Composable
internal fun ConnectiveTissueSummaryCard(
    state: TissueCurrentState?,
    onClick: () -> Unit
) {
    val ui = TissueAnalysisUiMapper.summary(state)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ui.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(ui.supportingText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.status?.let { Text("연결조직 상태: $it", fontWeight = FontWeight.SemiBold) }
            ui.topAreas?.let { Text("주요 부위: $it", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onClick) {
                Text(ui.actionLabel)
            }
        }
    }
}

@Composable
internal fun ConnectiveTissueAnalysisContent(state: TissueCurrentState?) {
    if (state == null) {
        InfoCard("연결조직 상태를 계산하고 있습니다.")
        return
    }
    val ui = TissueAnalysisUiMapper.map(state)
    var expandedJoint by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("현재 연결조직 상태: ${ui.status}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("주요 부위: ${ui.topAreas}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "개인 기록 56일 전에는 보정 중으로 표시합니다. 이 값은 손상률이나 진단이 아닙니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ui.joints.forEach { joint ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    expandedJoint = if (expandedJoint == joint.key) null else joint.key
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(joint.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(joint.status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "점수 ${joint.score} · 높은 하위 단위 ${joint.highChildCount}개 · 최고 ${joint.highestChild}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("주요 기여: ${joint.contributors}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (expandedJoint == joint.key) "접기" else "하위 조직 보기",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (expandedJoint == joint.key) {
                        joint.children.forEach { child ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(child.name, fontWeight = FontWeight.SemiBold)
                                    Text("${child.status} · 점수 ${child.score} · 현재 모델 범위 ${child.recoveryRange}")
                                    Text(child.recoveryTrend, style = MaterialTheme.typography.bodySmall)
                                    Text("기여 운동: ${child.contributors}", style = MaterialTheme.typography.bodySmall)
                                    if (child.diagnostics.isNotBlank()) {
                                        Text(
                                            child.diagnostics,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (ui.diagnostics.isNotEmpty()) {
            InfoCard(ui.diagnostics.take(3).joinToString("\n"))
        }
    }
}
