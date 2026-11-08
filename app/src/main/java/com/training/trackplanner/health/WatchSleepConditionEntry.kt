package com.training.trackplanner.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.training.trackplanner.TrainingViewModel
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lives in the existing Record daily-condition card, not a separate launcher.
 * Read on date entry once authorized; never overwrite an already-entered sleep value.
 */
@Composable
internal fun WatchSleepConditionEntry(
    date: String,
    savedHours: Double?,
    editorOpen: Boolean,
    viewModel: TrainingViewModel
) {
    val context = LocalContext.current
    val gateway = remember(context) { WatchHealthData(context) }
    val prefs = remember(context) { WatchImportPreferences(context) }
    val healthConnectAvailable = remember(gateway) { gateway.available() }
    var refresh by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(prefs.autoSleep) }
    var permitted by remember { mutableStateOf(false) }
    var sleep by remember(date) { mutableStateOf<WatchSleep?>(null) }
    var sessions by remember(date) { mutableStateOf<List<WatchSleep>>(emptyList()) }
    var selectedIds by remember(date) { mutableStateOf<Set<String>>(emptySet()) }
    var pickerOpen by remember(date) { mutableStateOf(false) }
    var status by remember(date) { mutableStateOf<String?>(null) }
    var applying by remember(date) { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        refresh++
    }

    LaunchedEffect(date, refresh) {
        sleep = null
        sessions = emptyList()
        if (!healthConnectAvailable) {
            status = "Health Connect를 사용할 수 없습니다."
            permitted = false
            return@LaunchedEffect
        }
        try {
            permitted = WatchHealthData.SLEEP_PERMISSION in gateway.permissions()
            if (!permitted) {
                status = null
                return@LaunchedEffect
            }
            if (LocalDate.parse(date).isAfter(LocalDate.now())) return@LaunchedEffect
            status = "워치 수면 확인 중…"
            val values = gateway.sleep(LocalDate.parse(date), forceRefresh = refresh > 0)
            sessions = values
            sleep = if (values.size == 1) values.single() else null
            status = when {
                values.isEmpty() -> "공유된 수면기록 없음"
                values.size > 1 -> "수면 기록 ${values.size}개 · 수면 구간을 선택해 주세요."
                !values.single().reliable -> "수면 단계가 부족해 자동 입력하지 않습니다."
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "수면 기록 조회 실패 · 다시 확인"
        }
    }

    LaunchedEffect(date, sleep, savedHours, enabled, editorOpen) {
        val candidate = sleep ?: return@LaunchedEffect
        if (sessions.size != 1 || !enabled || editorOpen || savedHours != null || applying ||
            prefs.hasHandledSleep(date) || !candidate.reliable) return@LaunchedEffect
        val minutes = candidate.sleepMinutes ?: return@LaunchedEffect
        if (minutes <= 0) return@LaunchedEffect
        applying = true
        viewModel.fillSleepHoursIfEmpty(date, minutes / 60.0) { successful ->
            if (successful) prefs.markSleepHandled(date)
            applying = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        when {
            !healthConnectAvailable -> Unit
            !permitted -> {
                TextButton(
                    onClick = {
                        // Granting the permission only enables reading. Auto-fill is a separate choice.
                        launcher.launch(setOf(WatchHealthData.SLEEP_PERMISSION))
                    }
                ) { Text("워치 수면 연동") }
            }
            else -> {
                val candidate = sleep
                if (candidate != null) {
                    val label = candidate.sleepMinutes ?: candidate.elapsedMinutes
                    Text(
                        if (candidate.reliable) "워치 수면: ${label / 60}시간 ${label % 60}분" else
                            "워치 수면 구간: ${label / 60}시간 ${label % 60}분 (실제 수면시간 미확인)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("출처: ${candidate.sourceName}", style = MaterialTheme.typography.labelSmall)
                    if (savedHours != null) {
                        Text("기존 수면시간을 보존했습니다.", style = MaterialTheme.typography.labelSmall)
                    }
                    if (savedHours == null && !candidate.reliable) {
                        Text("수면 단계가 부족해 자동 저장하지 않습니다. 컨디션에서 직접 입력할 수 있습니다.",
                            style = MaterialTheme.typography.labelSmall)
                    }
                } else if (status != null) {
                    Text(status.orEmpty(), style = MaterialTheme.typography.labelSmall)
                }
                if (sessions.size > 1) {
                    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd HH:mm") }
                    val zone = ZoneId.systemDefault()
                    if (savedHours == null && !editorOpen && !prefs.hasHandledSleep(date)) {
                        OutlinedButton(onClick = { pickerOpen = !pickerOpen }) {
                            Text(if (pickerOpen) "수면 선택 닫기" else "워치 수면 선택")
                        }
                        if (pickerOpen) {
                            Text("실제로 잔 구간만 선택하세요.", style = MaterialTheme.typography.bodySmall)
                            sessions.forEach { item ->
                                val eligible = item.reliable && item.sleepMinutes != null
                                Row {
                                    Checkbox(
                                        checked = item.sourceId in selectedIds,
                                        enabled = eligible && !applying,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + item.sourceId
                                                else selectedIds - item.sourceId
                                        }
                                    )
                                    val minutes = item.sleepMinutes
                                    Text(
                                        "${item.start.atZone(zone).format(formatter)} ~ " +
                                            "${item.end.atZone(zone).format(formatter)} · " +
                                            (if (minutes != null && eligible) "${minutes / 60}시간 ${minutes % 60}분"
                                            else "실제 수면시간 미확인") +
                                            " · ${item.sourceName}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            val chosen = sessions.filter { it.sourceId in selectedIds }
                            val combined = WatchSleepAggregate.combine(chosen)
                            if (chosen.isNotEmpty() && combined == null) {
                                Text("선택 구간이 겹치거나 불확실해 합산할 수 없습니다.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (combined != null) {
                                val total = combined.sleepMinutes ?: 0
                                Text("선택 합계: ${total / 60}시간 ${total % 60}분")
                            }
                            OutlinedButton(
                                enabled = combined != null && !applying,
                                onClick = {
                                    val minutes = combined?.sleepMinutes ?: return@OutlinedButton
                                    applying = true
                                    viewModel.fillSleepHoursIfEmpty(date, minutes / 60.0) { successful ->
                                        if (successful) {
                                            prefs.markSleepHandled(date)
                                            pickerOpen = false
                                        }
                                        applying = false
                                    }
                                }
                            ) { Text("선택한 수면시간 적용") }
                        }
                    } else if (savedHours != null) {
                        Text("기존 수면시간을 보존했습니다.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        prefs.autoSleep = !enabled
                        enabled = prefs.autoSleep
                    }) {
                        Text(if (enabled) "수면 자동 채우기 끄기" else "수면 자동 채우기 켜기")
                    }
                    TextButton(onClick = { refresh++ }) { Text("새로고침") }
                }
            }
        }
    }
}
