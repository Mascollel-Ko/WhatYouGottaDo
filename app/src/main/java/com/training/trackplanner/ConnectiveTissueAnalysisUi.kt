package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.tissue.TissueAnalysisUiMapper
import com.training.trackplanner.analysis.tissue.TissueCurrentState
import com.training.trackplanner.analysis.tissue.TissueEducationalInfo

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
    var showAllJoints by rememberSaveable { mutableStateOf(false) }
    var selectedInfoKey by rememberSaveable { mutableStateOf<String?>(null) }
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
        ui.visibleJoints(showAllJoints).forEach { joint ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(joint.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TissueInfoButton(
                                contentDescription = joint.infoContentDescription,
                                onClick = { selectedInfoKey = joint.info.stableKey }
                            )
                        }
                        Text(joint.status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Text("높은 하위 단위 ${joint.highChildCount}개 · 최고 ${joint.highestChild}", style = MaterialTheme.typography.bodySmall)
                    Text("주요 기여: ${joint.contributors}", style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = {
                            expandedJoint = if (expandedJoint == joint.key) null else joint.key
                        }
                    ) {
                        Text(if (expandedJoint == joint.key) "접기" else "하위 조직 보기")
                    }
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            child.name,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        TissueInfoButton(
                                            contentDescription = child.infoContentDescription,
                                            onClick = { selectedInfoKey = child.info.stableKey }
                                        )
                                    }
                                    Text("${child.status} · 현재 모델 범위 ${child.recoveryRange}")
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
        if (ui.joints.size > 3) {
            TextButton(onClick = { showAllJoints = !showAllJoints }) {
                Text(if (showAllJoints) "접기" else "나머지 부위 보기")
            }
        }
        if (ui.diagnostics.isNotEmpty()) {
            InfoCard(ui.diagnostics.take(3).joinToString("\n"))
        }
    }
    selectedInfoKey?.let(ui::info)?.let { info ->
        TissueEducationalInfoDialog(
            info = info,
            onDismiss = { selectedInfoKey = null }
        )
    }
}

@Composable
private fun TissueInfoButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = contentDescription
        )
    }
}

@Composable
private fun TissueEducationalInfoDialog(
    info: TissueEducationalInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.displayNameKo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TissueInfoField("표시명", info.displayNameKo)
                TissueInfoField("위치", info.anatomicalLocationKo)
                TissueInfoField("주요 기능", info.primaryFunctionsKo.joinToString(" · "))
                TissueInfoField("주로 사용되는 동작", info.commonLoadContextsKo.joinToString(" · "))
                info.shortDescriptionKo?.let { TissueInfoField("설명", it) }
                Text(
                    "이 설명은 운동 부하를 이해하기 위한 일반 정보이며 의학적 진단이 아닙니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun TissueInfoField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
