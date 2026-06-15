package com.training.trackplanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.CalendarConflictMode
import com.training.trackplanner.data.CalendarConflictSummary
import com.training.trackplanner.data.DailyRecordSummary
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordCalendarScreen(
    viewModel: TrainingViewModel,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    var visibleMonth by rememberSaveable(selectedDate) {
        mutableStateOf(YearMonth.from(LocalDate.parse(selectedDate)).toString())
    }
    val month = remember(visibleMonth) { YearMonth.parse(visibleMonth) }
    val startDate = remember(month) { month.atDay(1).toString() }
    val endDate = remember(month) { month.atEndOfMonth().toString() }
    val summaries by remember(startDate, endDate) {
        viewModel.dailySummaries(startDate, endDate)
    }.collectAsState(initial = emptyList())
    val summaryByDate = remember(summaries) { summaries.associateBy { it.date } }
    val selected = remember(selectedDate) { LocalDate.parse(selectedDate) }
    var actionMenuDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<CalendarPendingAction?>(null) }
    var pendingConflict by remember { mutableStateOf<PendingConflict?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var pendingRangeDelete by remember { mutableStateOf<PendingRangeDelete?>(null) }
    var rangeCopy by remember { mutableStateOf<CalendarRangeCopy?>(null) }
    var rangeDelete by remember { mutableStateOf<CalendarRangeDelete?>(null) }

    BackHandler {
        when {
            actionMenuDate != null -> actionMenuDate = null
            pendingDelete != null -> pendingDelete = null
            pendingRangeDelete != null -> pendingRangeDelete = null
            pendingConflict != null -> pendingConflict = null
            pendingAction != null -> pendingAction = null
            rangeCopy != null -> rangeCopy = null
            rangeDelete != null -> rangeDelete = null
            else -> onBack()
        }
    }

    actionMenuDate?.let { sourceDate ->
        CalendarActionDialog(
            date = sourceDate,
            onDismiss = { actionMenuDate = null },
            onCopyPlan = {
                pendingAction = CalendarPendingAction(sourceDate, CalendarActionType.CopyPlan)
                actionMenuDate = null
            },
            onCopyWithState = {
                pendingAction = CalendarPendingAction(sourceDate, CalendarActionType.CopyWithState)
                actionMenuDate = null
            },
            onMove = {
                pendingAction = CalendarPendingAction(sourceDate, CalendarActionType.Move)
                actionMenuDate = null
            },
            onDelete = {
                val summary = summaryByDate[sourceDate]
                pendingDelete = PendingDelete(sourceDate, summary)
                actionMenuDate = null
            },
            onRangeCopy = {
                rangeCopy = CalendarRangeCopy(sourceStart = sourceDate)
                actionMenuDate = null
            },
            onRangeDelete = {
                rangeDelete = CalendarRangeDelete(sourceStart = sourceDate)
                actionMenuDate = null
            }
        )
    }

    pendingDelete?.let { delete ->
        DeleteDateDialog(
            pendingDelete = delete,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteDate(delete.date)
                pendingDelete = null
            }
        )
    }

    pendingRangeDelete?.let { delete ->
        DeleteDateRangeDialog(
            pendingDelete = delete,
            onDismiss = { pendingRangeDelete = null },
            onDeleteUnconfirmedOnly = {
                viewModel.deleteDateRange(
                    startDate = delete.startDate,
                    endDate = delete.endDate,
                    includeConfirmed = false
                )
                pendingRangeDelete = null
                rangeDelete = null
            },
            onDeleteAll = {
                viewModel.deleteDateRange(
                    startDate = delete.startDate,
                    endDate = delete.endDate,
                    includeConfirmed = true
                )
                pendingRangeDelete = null
                rangeDelete = null
            }
        )
    }

    pendingConflict?.let { conflict ->
        CalendarConflictDialog(
            conflict = conflict,
            onDismiss = { pendingConflict = null },
            onAppend = {
                executeCalendarAction(viewModel, conflict, CalendarConflictMode.Append)
                pendingConflict = null
                pendingAction = null
                rangeCopy = null
            },
            onOverwrite = {
                executeCalendarAction(viewModel, conflict, CalendarConflictMode.Overwrite)
                pendingConflict = null
                pendingAction = null
                rangeCopy = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${month.year}년 ${month.monthValue}월",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onBack) {
                    Text("기록")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { visibleMonth = month.minusMonths(1).toString() }
                ) {
                    Text("이전달")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val today = LocalDate.now()
                        visibleMonth = YearMonth.from(today).toString()
                        onDateSelected(today.toString())
                    }
                ) {
                    Text("오늘")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { visibleMonth = month.plusMonths(1).toString() }
                ) {
                    Text("다음달")
                }
            }
        }
        item {
            pendingAction?.let { action ->
                ActionModeCard(
                    text = "${action.sourceDate} ${action.label()} 대상 날짜 선택",
                    onCancel = { pendingAction = null }
                )
            }
            rangeCopy?.let { state ->
                val text = when {
                    state.sourceEnd == null -> "${state.sourceStart}부터 복사할 끝 날짜 선택"
                    else -> "${state.sourceStart}~${state.sourceEnd} 붙여넣을 시작 날짜 선택"
                }
                ActionModeCard(
                    text = text,
                    onCancel = { rangeCopy = null }
                )
            }
            rangeDelete?.let { state ->
                ActionModeCard(
                    text = "${state.sourceStart}부터 삭제할 끝 날짜 선택",
                    onCancel = { rangeDelete = null }
                )
            }
        }
        item {
            CalendarGrid(
                month = month,
                selectedDate = selected,
                summaries = summaryByDate,
                onDateSelected = { date ->
                    handleCalendarDateClick(
                        date = date,
                        pendingAction = pendingAction,
                        rangeCopy = rangeCopy,
                        rangeDelete = rangeDelete,
                        viewModel = viewModel,
                        onPlainDateSelected = onDateSelected,
                        onPendingConflict = { pendingConflict = it },
                        onPendingRangeDelete = { pendingRangeDelete = it },
                        onPendingActionChange = { pendingAction = it },
                        onRangeCopyChange = { rangeCopy = it },
                        onRangeDeleteChange = { rangeDelete = it }
                    )
                },
                onDateLongClick = { actionMenuDate = it }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    summaries: Map<String, DailyRecordSummary>,
    onDateSelected: (String) -> Unit,
    onDateLongClick: (String) -> Unit
) {
    val cells = remember(month) { monthCells(month) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEach { label ->
                Text(
                    modifier = Modifier.weight(1f),
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(92.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {}
                    } else {
                        CalendarDayCell(
                            modifier = Modifier.weight(1f),
                            date = date,
                            selected = date == selectedDate,
                            today = date == LocalDate.now(),
                            summary = summaries[date.toString()],
                            onClick = { onDateSelected(date.toString()) },
                            onLongClick = { onDateLongClick(date.toString()) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    summary: DailyRecordSummary?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val hasRecord = summary != null && summary.confirmedSets > 0
    val hasPlanOnly = summary != null && summary.confirmedSets == 0 && summary.plannedSets > 0
    val container = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        hasRecord -> MaterialTheme.colorScheme.secondaryContainer
        hasPlanOnly -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        hasRecord -> MaterialTheme.colorScheme.onSecondaryContainer
        hasPlanOnly -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .height(92.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        border = if (today) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = content
            )
            if (summary != null) {
                Text(
                    text = if (hasRecord) compactCategory(summary.categorySummary) else "계획",
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasRecord) "${summary.confirmedSets}세트" else "${summary.plannedSets}예정",
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                    maxLines = 1
                )
                if (hasRecord) {
                    Text(
                        text = compactVolume(summary.totalVolumeKg),
                        style = MaterialTheme.typography.labelSmall,
                        color = content,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionModeCard(
    text: String,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            TextButton(onClick = onCancel) {
                Text("취소")
            }
        }
    }
}

@Composable
private fun CalendarActionDialog(
    date: String,
    onDismiss: () -> Unit,
    onCopyPlan: () -> Unit,
    onCopyWithState: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRangeCopy: () -> Unit,
    onRangeDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onCopyPlan) {
                    Text("계획으로 복사")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCopyWithState) {
                    Text("기록상태까지 복사")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onMove) {
                    Text("이동")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRangeCopy) {
                    Text("선택복사")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRangeDelete) {
                    Text("선택 삭제")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onDelete) {
                    Text("삭제")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun CalendarConflictDialog(
    conflict: PendingConflict,
    onDismiss: () -> Unit,
    onAppend: () -> Unit,
    onOverwrite: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(conflict.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (conflict.summary.hasExistingEntries) {
                    Text("대상 날짜에 기존 기록이 있습니다.")
                    Text("대상 날짜 ${conflict.summary.affectedDateCount}일")
                    Text("기존 운동 ${conflict.summary.existingEntryCount}개")
                    Text("기존 세트 ${conflict.summary.existingSetCount}개")
                    Text("완료 세트 ${conflict.summary.existingConfirmedSetCount}개")
                    Text("덮어쓰면 기존 기록이 삭제됩니다.")
                } else {
                    Text("대상 날짜에 기존 기록이 없습니다.")
                    if (conflict.execution is CalendarExecution.MoveDate) {
                        Text("이동하면 원본 날짜의 운동 기록이 삭제됩니다.")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (conflict.summary.hasExistingEntries) {
                        onOverwrite()
                    } else {
                        onAppend()
                    }
                }
            ) {
                Text(if (conflict.summary.hasExistingEntries) "덮어쓰기" else conflict.confirmLabel())
            }
        },
        dismissButton = {
            Row {
                if (conflict.summary.hasExistingEntries) {
                    TextButton(onClick = onAppend) {
                        Text("추가")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
            }
        }
    )
}

@Composable
private fun DeleteDateDialog(
    pendingDelete: PendingDelete,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val summary = pendingDelete.summary
    val confirmedSets = summary?.confirmedSets ?: 0
    val plannedSets = summary?.plannedSets ?: 0
    val totalSets = confirmedSets + plannedSets
    val entries = summary?.entryCount ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 날짜의 운동 기록을 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pendingDelete.date)
                Text("운동 ${entries}개, 세트 ${totalSets}개가 삭제됩니다.")
                Text("완료 세트 ${confirmedSets}개가 포함되어 있습니다.")
                Text("DailyMetric 수면/체중은 삭제하지 않습니다.")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun DeleteDateRangeDialog(
    pendingDelete: PendingRangeDelete,
    onDismiss: () -> Unit,
    onDeleteUnconfirmedOnly: () -> Unit,
    onDeleteAll: () -> Unit
) {
    val summary = pendingDelete.summary
    val confirmedSets = summary.existingConfirmedSetCount
    val unconfirmedSets = (summary.existingSetCount - confirmedSets).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("선택한 날짜 범위를 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${pendingDelete.startDate} ~ ${pendingDelete.endDate}")
                Text("대상 날짜 ${summary.affectedDateCount}일")
                Text("운동 ${summary.existingEntryCount}개, 세트 ${summary.existingSetCount}개")
                Text("확인 세트 ${confirmedSets}개, 미확인 세트 ${unconfirmedSets}개")
                Text("수면/체중 기록은 삭제하지 않습니다.")
            }
        },
        confirmButton = {
            Button(onClick = onDeleteAll) {
                Text("확인 포함 삭제")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDeleteUnconfirmedOnly) {
                    Text("미확인만 삭제")
                }
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
            }
        }
    )
}

private fun handleCalendarDateClick(
    date: String,
    pendingAction: CalendarPendingAction?,
    rangeCopy: CalendarRangeCopy?,
    rangeDelete: CalendarRangeDelete?,
    viewModel: TrainingViewModel,
    onPlainDateSelected: (String) -> Unit,
    onPendingConflict: (PendingConflict) -> Unit,
    onPendingRangeDelete: (PendingRangeDelete) -> Unit,
    onPendingActionChange: (CalendarPendingAction?) -> Unit,
    onRangeCopyChange: (CalendarRangeCopy?) -> Unit,
    onRangeDeleteChange: (CalendarRangeDelete?) -> Unit
) {
    if (pendingAction != null) {
        if (pendingAction.sourceDate == date) {
            onPendingActionChange(null)
            return
        }
        val execution = when (pendingAction.type) {
            CalendarActionType.CopyPlan -> CalendarExecution.CopyDate(
                sourceDate = pendingAction.sourceDate,
                targetDate = date,
                keepConfirmed = false
            )
            CalendarActionType.CopyWithState -> CalendarExecution.CopyDate(
                sourceDate = pendingAction.sourceDate,
                targetDate = date,
                keepConfirmed = true
            )
            CalendarActionType.Move -> CalendarExecution.MoveDate(
                sourceDate = pendingAction.sourceDate,
                targetDate = date
            )
        }
        viewModel.loadCalendarConflictSummary(listOf(date)) { summary ->
            if (summary.hasExistingEntries || pendingAction.type == CalendarActionType.Move) {
                onPendingConflict(
                    PendingConflict(
                        title = pendingAction.label(),
                        execution = execution,
                        summary = summary
                    )
                )
            } else {
                executeCalendarExecution(viewModel, execution, CalendarConflictMode.Append)
                onPendingActionChange(null)
            }
        }
        return
    }

    if (rangeCopy != null) {
        if (rangeCopy.sourceEnd == null) {
            onRangeCopyChange(rangeCopy.copy(sourceEnd = date))
        } else {
            val targetDates = shiftedTargetDates(
                sourceStart = rangeCopy.sourceStart,
                sourceEnd = rangeCopy.sourceEnd,
                targetStart = date
            )
            val execution = CalendarExecution.RangeCopy(
                sourceStart = rangeCopy.sourceStart,
                sourceEnd = rangeCopy.sourceEnd,
                targetStart = date
            )
            viewModel.loadCalendarConflictSummary(targetDates) { summary ->
                if (summary.hasExistingEntries) {
                    onPendingConflict(
                        PendingConflict(
                            title = "선택복사",
                            execution = execution,
                            summary = summary
                        )
                    )
                } else {
                    executeCalendarExecution(viewModel, execution, CalendarConflictMode.Append)
                    onRangeCopyChange(null)
                }
            }
        }
        return
    }

    if (rangeDelete != null) {
        val dates = calendarDateRange(rangeDelete.sourceStart, date)
        viewModel.loadCalendarConflictSummary(dates) { summary ->
            onPendingRangeDelete(
                PendingRangeDelete(
                    startDate = dates.firstOrNull() ?: rangeDelete.sourceStart,
                    endDate = dates.lastOrNull() ?: date,
                    summary = summary
                )
            )
        }
        onRangeDeleteChange(null)
        return
    }

    onPlainDateSelected(date)
}

private fun executeCalendarAction(
    viewModel: TrainingViewModel,
    conflict: PendingConflict,
    conflictMode: CalendarConflictMode
) {
    executeCalendarExecution(viewModel, conflict.execution, conflictMode)
}

private fun executeCalendarExecution(
    viewModel: TrainingViewModel,
    execution: CalendarExecution,
    conflictMode: CalendarConflictMode
) {
    when (execution) {
        is CalendarExecution.CopyDate -> viewModel.copyDate(
            sourceDate = execution.sourceDate,
            targetDate = execution.targetDate,
            keepConfirmed = execution.keepConfirmed,
            conflictMode = conflictMode
        )
        is CalendarExecution.MoveDate -> viewModel.moveDate(
            sourceDate = execution.sourceDate,
            targetDate = execution.targetDate,
            conflictMode = conflictMode
        )
        is CalendarExecution.RangeCopy -> viewModel.copyDateRangeAsPlan(
            sourceStart = execution.sourceStart,
            sourceEnd = execution.sourceEnd,
            targetStart = execution.targetStart,
            conflictMode = conflictMode
        )
    }
}

private fun shiftedTargetDates(
    sourceStart: String,
    sourceEnd: String,
    targetStart: String
): List<String> {
    val sourceDates = calendarDateRange(sourceStart, sourceEnd)
    val target = LocalDate.parse(targetStart)
    return sourceDates.mapIndexed { index, _ -> target.plusDays(index.toLong()).toString() }
}

private fun calendarDateRange(startDate: String, endDate: String): List<String> {
    val start = LocalDate.parse(startDate)
    val end = LocalDate.parse(endDate)
    val first = minOf(start, end)
    val last = maxOf(start, end)
    val days = last.toEpochDay() - first.toEpochDay()
    return (0L..days).map { offset -> first.plusDays(offset).toString() }
}

private enum class CalendarActionType {
    CopyPlan,
    CopyWithState,
    Move
}

private data class CalendarPendingAction(
    val sourceDate: String,
    val type: CalendarActionType
) {
    fun label(): String =
        when (type) {
            CalendarActionType.CopyPlan -> "계획으로 복사"
            CalendarActionType.CopyWithState -> "기록상태까지 복사"
            CalendarActionType.Move -> "이동"
        }
}

private data class CalendarRangeCopy(
    val sourceStart: String,
    val sourceEnd: String? = null
)

private data class CalendarRangeDelete(
    val sourceStart: String
)

private data class PendingConflict(
    val title: String,
    val execution: CalendarExecution,
    val summary: CalendarConflictSummary
) {
    fun confirmLabel(): String =
        when (execution) {
            is CalendarExecution.CopyDate -> "복사"
            is CalendarExecution.MoveDate -> "이동"
            is CalendarExecution.RangeCopy -> "복사"
        }
}

private data class PendingDelete(
    val date: String,
    val summary: DailyRecordSummary?
)

private data class PendingRangeDelete(
    val startDate: String,
    val endDate: String,
    val summary: CalendarConflictSummary
)

private sealed interface CalendarExecution {
    data class CopyDate(
        val sourceDate: String,
        val targetDate: String,
        val keepConfirmed: Boolean
    ) : CalendarExecution

    data class MoveDate(
        val sourceDate: String,
        val targetDate: String
    ) : CalendarExecution

    data class RangeCopy(
        val sourceStart: String,
        val sourceEnd: String,
        val targetStart: String
    ) : CalendarExecution
}

private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val prefix = first.dayOfWeek.value - 1
    val dates = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = MutableList<LocalDate?>(prefix) { null }
    cells += dates
    while (cells.size % 7 != 0) {
        cells += null
    }
    return cells
}

private fun compactCategory(raw: String): String {
    val categories = raw.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map {
            when (it) {
                "근력운동" -> "근력"
                "기능성운동" -> "기능"
                "유산소운동" -> "유산소"
                else -> it
            }
        }
        .distinct()
    return categories.take(2).joinToString("·").ifBlank { "기록" }
}

private fun compactVolume(volumeKg: Double): String =
    if (volumeKg >= 1000.0) {
        "${formatDecimal(volumeKg / 1000.0)}t"
    } else {
        "${formatWeight(volumeKg)}kg"
    }
