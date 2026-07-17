package com.training.trackplanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.training.trackplanner.analysis.fatigue.HomeTodaySummaryState
import com.training.trackplanner.analysis.fatigue.MiniTrendPoint
import com.training.trackplanner.data.InitialUserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeScreen(
    viewModel: TrainingViewModel,
    onNavigate: (AppTab) -> Unit,
    onOpenAppExplanation: () -> Unit
) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val summary by viewModel.homeTodaySummary.collectAsState()
    val transferMessage by viewModel.recordTransferMessage.collectAsState()
    val initialProfile by viewModel.initialUserProfile.collectAsState()
    val todayCheckIn by viewModel.todayCheckIn.collectAsState()
    var showInitialProfile by rememberSaveable { mutableStateOf(false) }
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "오늘 무엇을 할까요?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            CompactHomeActionGroup(
                onRecord = { onNavigate(AppTab.Record) },
                onProgram = { onNavigate(AppTab.Plan) },
                onOpenAppExplanation = onOpenAppExplanation
            )
        }
        item {
            TodaySummaryCard(summary)
        }
        item {
            HomeDailyCheckInCard(
                checkIn = todayCheckIn,
                onSave = viewModel::saveDailyCheckIn
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
        item {
            InitialProfileCard(
                profile = initialProfile,
                onOpen = { showInitialProfile = true }
            )
        }
    }

    if (showInitialProfile) {
        InitialProfileDialog(
            profile = initialProfile,
            onDismiss = { showInitialProfile = false },
            onSave = { profile ->
                viewModel.saveInitialUserProfile(profile)
                showInitialProfile = false
            }
        )
    }
}

@Composable
internal fun CompactHomeActionGroup(
    onRecord: () -> Unit,
    onProgram: () -> Unit,
    onOpenAppExplanation: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactHomeActionButton(
            title = "오늘 운동 기록하기",
            body = "오늘 계획과 수행 기록을 바로 확인합니다.",
            onClick = onRecord
        )
        CompactHomeActionButton(
            title = "프로그램으로 시작하기",
            body = "프로그램을 선택해 날짜별 계획을 만듭니다.",
            onClick = onProgram
        )
        CompactInfoNavigationRow(
            label = stringResource(R.string.home_app_explanation_label),
            description = stringResource(R.string.home_app_explanation_description),
            onClick = onOpenAppExplanation,
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
private fun CompactHomeActionButton(
    title: String,
    body: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TodaySummaryCard(summary: HomeTodaySummaryState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "오늘 요약",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildString {
                    append("현재 상태\n")
                    summary.fatigueCard.phaseLabel?.let { label ->
                        append(label)
                        append("\n")
                    }
                    append("현재 상태: ")
                    append(summary.fatigueCard.primary.score)
                    append(" · ")
                    append(summary.fatigueCard.primary.label)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            val planText = summary.fatigueCard.projection?.let { projection ->
                "${summary.fatigueCard.projectionPrefix}: ${projection.score} · ${projection.label}"
            } ?: summary.fatigueCard.statusText.orEmpty()
            if (planText.isNotBlank()) {
                Text(
                    text = planText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.fatigueCard.axisMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.fatigueCard.levelCountMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            summary.fatigueCard.headline?.let { headline ->
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            summary.fatigueCard.actionLabel?.let { action ->
                Text(
                    text = "판단: $action",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            summary.fatigueCard.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "오늘 계획",
                    value = summary.plannedExerciseCount.toString()
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "완료 세트",
                    value = summary.confirmedSetCount.toString()
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "미확인 세트",
                    value = summary.unconfirmedSetCount.toString()
                )
            }
            MiniTrendChart(
                title = "최근 훈련량",
                currentPoints = summary.recentTrainingLoadSeries,
                projectedPoints = summary.projectedTrainingLoadSeries
            )
            MiniTrendChart(
                title = "피로도 변화",
                currentPoints = summary.recentFatigueSeries,
                projectedPoints = summary.projectedFatigueSeries
            )
        }
    }
}

@Composable
private fun MiniTrendChart(
    title: String,
    currentPoints: List<MiniTrendPoint>,
    projectedPoints: List<MiniTrendPoint>? = null
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val projectedColor = MaterialTheme.colorScheme.tertiary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (projectedPoints != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("현재", style = MaterialTheme.typography.labelSmall, color = lineColor)
                    Text("끝나면 예상", style = MaterialTheme.typography.labelSmall, color = projectedColor)
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            drawLine(
                color = guideColor,
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1f
            )
            if (currentPoints.isEmpty()) return@Canvas
            val allPoints = currentPoints + projectedPoints.orEmpty()
            val min = allPoints.minOf { it.value }
            val max = allPoints.maxOf { it.value }
            val range = (max - min).takeIf { it > 0.0 } ?: 1.0
            fun pathFor(points: List<MiniTrendPoint>): Path {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = if (points.size == 1) size.width / 2f else {
                        size.width * index / (points.size - 1).toFloat()
                    }
                    val normalized = ((point.value - min) / range).toFloat()
                    val y = size.height - 4.dp.toPx() - normalized * (size.height - 8.dp.toPx())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }
            drawPath(
                path = pathFor(currentPoints),
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            projectedPoints?.let { points ->
                drawPath(
                    path = pathFor(points),
                    color = projectedColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                        )
                    )
                )
            }
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

@Composable
private fun InitialProfileCard(
    profile: InitialUserProfile?,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "초기 프로필",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (profile == null) {
                    "기록이 적은 기간의 피로 기준을 설정합니다. 건너뛰어도 됩니다."
                } else {
                    "초기 프로필이 저장되어 있습니다. 언제든 수정할 수 있습니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpen
            ) {
                Text(if (profile == null) "입력하기" else "수정하기")
            }
        }
    }
}
