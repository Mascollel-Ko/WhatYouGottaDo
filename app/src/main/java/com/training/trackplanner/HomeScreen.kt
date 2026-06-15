package com.training.trackplanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeScreen(
    viewModel: TrainingViewModel,
    onNavigate: (AppTab) -> Unit
) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val entryCount by remember(today) { viewModel.entryCount(today) }.collectAsState(initial = 0)
    val unconfirmedCount by remember(today) { viewModel.plannedSetCount(today) }.collectAsState(initial = 0)
    val confirmedCount by remember(today) { viewModel.confirmedSetCount(today) }.collectAsState(initial = 0)
    val transferMessage by viewModel.recordTransferMessage.collectAsState()
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(viewModel::backupRecords)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::restoreRecords)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "오늘 무엇을 할까요?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            CtaCard(
                title = "오늘 운동 기록하기",
                body = "계획 세트를 확인하거나 오늘 수행한 운동을 바로 기록합니다.",
                onClick = { onNavigate(AppTab.Record) }
            )
        }
        item {
            CtaCard(
                title = "프로그램으로 시작하기",
                body = "기본 프로그램을 고르고 시작일을 적용해 날짜별 계획을 만듭니다.",
                onClick = { onNavigate(AppTab.Plan) }
            )
        }
        item {
            CtaCard(
                title = "운동 목록 둘러보기",
                body = "운동 분류와 기록 방식을 확인하고 계획에 넣을 항목을 고릅니다.",
                onClick = { onNavigate(AppTab.Exercise) }
            )
        }
        item {
            TodaySummaryCard(
                entryCount = entryCount,
                confirmedCount = confirmedCount,
                unconfirmedCount = unconfirmedCount
            )
        }
        item {
            RecordManagementCard(
                message = transferMessage,
                onBackup = {
                    backupLauncher.launch("whatyougottatrain_backup_$today.csv")
                },
                onRestore = {
                    restoreLauncher.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "application/vnd.ms-excel",
                            "text/*"
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun TodaySummaryCard(
    entryCount: Int,
    confirmedCount: Int,
    unconfirmedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "오늘 요약",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "오늘 계획",
                    value = entryCount.toString()
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "완료 세트",
                    value = confirmedCount.toString()
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "미확인 세트",
                    value = unconfirmedCount.toString()
                )
            }
            Text(
                text = "최근 분석 요약은 확인된 세트가 쌓이면 더 선명해집니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordManagementCard(
    message: String?,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "기록 관리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onBackup
                ) {
                    Text("기록 백업")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRestore
                ) {
                    Text("기록 복원")
                }
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
