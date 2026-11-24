package com.training.trackplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.WeekdayDropPosition

internal fun weekdayDropAt(x: Float, width: Int): Pair<Int, WeekdayDropPosition>? {
    if (width <= 0 || x < 0f || x >= width) return null
    val slot = x * 7f / width
    val index = slot.toInt().coerceIn(0, 6)
    val fraction = slot - index
    val position = when {
        fraction < 0.28f -> WeekdayDropPosition.BEFORE
        fraction > 0.72f -> WeekdayDropPosition.AFTER
        else -> WeekdayDropPosition.CENTER
    }
    return index + 1 to position
}

/** One fixed-width row, explicit long-press drag, and equivalent tap-only controls. */
@Composable
internal fun BCoachDraftWeekdayRow(
    active: Set<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int, WeekdayDropPosition) -> Unit,
    onToggle: (Int) -> Unit
) {
    var source by remember { mutableStateOf<Int?>(null) }
    var drop by remember { mutableStateOf<Pair<Int, WeekdayDropPosition>?>(null) }
    var showMove by remember { mutableStateOf(false) }
    val move by rememberUpdatedState(onMove)
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("요일을 길게 눌러 이동 · 모든 주에 반영", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().testTag("b_coach_draft_weekday_row")
            .pointerInput(active) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        source = weekdayDropAt(point.x, size.width)?.first?.takeIf { it in active }
                        drop = weekdayDropAt(point.x, size.width)
                    },
                    onDrag = { change, _ ->
                        if (source != null) {
                            change.consume()
                            drop = if (change.position.y >= -size.height && change.position.y < size.height * 2)
                                weekdayDropAt(change.position.x, size.width) else null
                        }
                    },
                    onDragEnd = {
                        val from = source
                        val to = drop
                        source = null
                        drop = null
                        if (from != null && to != null && from != to.first) move(from, to.first, to.second)
                    },
                    onDragCancel = { source = null; drop = null }
                )
            }) {
            for (day in 1..7) {
                val label = programWeekdayChipLabel(day)
                val description = programWeekdayLabel(day)
                val enabledDay = day in active
                val shape = RoundedCornerShape(8.dp)
                val marker = drop?.takeIf { source != null && it.first == day && source != day }
                Box(Modifier.weight(1f).padding(horizontal = 2.dp).height(48.dp)
                    .testTag("b_coach_draft_weekday_$day").clip(shape)
                    .background(if (enabledDay) colors.primary else colors.surface)
                    .border(if (selected == day || marker != null || source == day) 2.dp else 1.dp,
                        if (marker != null || source == day) colors.tertiary else if (selected == day)
                            colors.tertiary else colors.outline, shape)
                    .selectable(selected == day, role = Role.Button, onClick = { onSelect(day) })
                    .semantics { contentDescription = description + if (enabledDay) " · 훈련일" else " · 휴식일" },
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (enabledDay) colors.onPrimary else colors.onSurface,
                        style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        maxLines = 1, softWrap = false)
                    if (marker != null && marker.second != WeekdayDropPosition.CENTER) {
                        Box(Modifier.align(if (marker.second == WeekdayDropPosition.BEFORE)
                            Alignment.CenterStart else Alignment.CenterEnd).width(4.dp).height(38.dp)
                            .background(colors.tertiary))
                    }
                }
            }
        }
        val hint = drop?.takeIf { source != null && source != it.first }?.let {
            if (it.first !in active) "빈 요일로 이동" else when (it.second) {
                WeekdayDropPosition.CENTER -> "두 요일 교환"
                WeekdayDropPosition.BEFORE -> "왼쪽에 삽입"
                WeekdayDropPosition.AFTER -> "오른쪽에 삽입"
            }
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { showMove = true }, enabled = active.isNotEmpty()) { Text("요일 이동") }
            TextButton(onClick = { onToggle(selected) }) {
                Text(if (selected in active) "선택 요일 제거" else "선택 요일 추가")
            }
        }
    }
    if (showMove && active.isNotEmpty()) {
        var from by remember(active, selected) { mutableStateOf(selected.takeIf { it in active } ?: active.min()) }
        var to by remember(from) { mutableStateOf((1..7).first { it != from }) }
        var position by remember { mutableStateOf(WeekdayDropPosition.CENTER) }
        AlertDialog(onDismissRequest = { showMove = false },
            title = { Text("요일 이동 · 모든 주") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("이동할 훈련일")
                    ProgramTemporalSelector(active.sorted(), setOf(from), "b_coach_move_source",
                        { programWeekdayChipLabel(it) }, { from = it }, { programWeekdayLabel(it) })
                    Text("놓을 요일")
                    ProgramTemporalSelector((1..7).toList(), setOf(to), "b_coach_move_target",
                        { programWeekdayChipLabel(it) }, { to = it }, { programWeekdayLabel(it) })
                    if (to in active) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for ((value, label) in listOf(WeekdayDropPosition.BEFORE to "왼쪽 삽입",
                                WeekdayDropPosition.CENTER to "교환", WeekdayDropPosition.AFTER to "오른쪽 삽입")) {
                                TextButton(onClick = { position = value }, modifier = Modifier.weight(1f)) {
                                    Text(if (position == value) "$label ✓" else label, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    Text("운동 순서와 세트별 처방은 유지합니다. 변경된 요일로 회복 간격을 다시 확인하세요.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = from != to, onClick = {
                showMove = false
                onMove(from, to, position)
            }) { Text("이동 적용") } },
            dismissButton = { TextButton(onClick = { showMove = false }) { Text("취소") } })
    }
}
