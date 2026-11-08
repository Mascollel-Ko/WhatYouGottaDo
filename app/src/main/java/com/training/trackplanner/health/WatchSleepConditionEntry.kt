package com.training.trackplanner.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
    var manualLookup by remember(date) { mutableStateOf(false) }
    var lookupDialog by remember(date) { mutableStateOf(false) }
    var lookupPending by remember(date) { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(prefs.autoSleep) }
    var permitted by remember { mutableStateOf(false) }
    var sleep by remember(date) { mutableStateOf<WatchSleep?>(null) }
    var sessions by remember(date) { mutableStateOf<List<WatchSleep>>(emptyList()) }
    var selectedIds by remember(date) { mutableStateOf<Set<String>>(emptySet()) }
    var replacementMinutes by remember(date) { mutableStateOf<Int?>(null) }
    var status by remember(date) { mutableStateOf<String?>(null) }
    var applying by remember(date) { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (WatchHealthData.SLEEP_PERMISSION in granted) {
            permitted = true
            manualLookup = true
            lookupDialog = true
            refresh++
        } else {
            lookupPending = false
            lookupDialog = true
            status = "수면 기록을 읽으려면 Health Connect 수면 권한이 필요합니다."
        }
    }

    LaunchedEffect(date, refresh, manualLookup) {
        if (!healthConnectAvailable) {
            permitted = false
            lookupPending = false
            status = "Health Connect를 사용할 수 없습니다."
            return@LaunchedEffect
        }
        try {
            permitted = WatchHealthData.SLEEP_PERMISSION in gateway.permissions()
            if (!permitted) {
                lookupPending = false
                status = "수면 기록 조회 권한이 필요합니다."
                return@LaunchedEffect
            }
            if (savedHours != null && !manualLookup) {
                // Manual replacement remains available, but stored sleep causes no automatic read.
                sessions = emptyList()
                sleep = null
                status = null
                lookupPending = false
                return@LaunchedEffect
            }
            sleep = null
            sessions = emptyList()
            if (LocalDate.parse(date).isAfter(LocalDate.now())) {
                status = "미래 날짜의 수면 기록은 조회할 수 없습니다."
                lookupPending = false
                return@LaunchedEffect
            }
            status = "워치 수면 확인 중…"
            val values = gateway.sleep(LocalDate.parse(date), forceRefresh = refresh > 0)
            sessions = values
            sleep = if (values.size == 1) values.single() else null
            lookupPending = false
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
            lookupPending = false
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

                if (replacementMinutes != null) {
                    val minutes = replacementMinutes ?: 0
                    AlertDialog(
                        onDismissRequest = { replacementMinutes = null },
                        title = { Text("수면시간 교체") },
                        text = { Text("기존 수면시간을 워치에서 선택한 ${minutes / 60}시간 ${minutes % 60}분으로 교체할까요? 다른 컨디션 기록은 유지됩니다.") },
                        confirmButton = {
                            TextButton(enabled = !applying, onClick = {
                                applying = true
                                viewModel.replaceSleepHoursFromWatch(date, minutes / 60.0) { successful ->
                                    if (successful) {
                                        prefs.markSleepHandled(date)
                                        replacementMinutes = null
                                    }
                                    applying = false
                                }
                            }) { Text("교체") }
                        },
                        dismissButton = {
                            TextButton(onClick = { replacementMinutes = null }) { Text("취소") }
                        }
                    )
                }

    if (lookupDialog) {
        AlertDialog(
            onDismissRequest = { lookupDialog = false },
            title = { Text("워치 수면 기록") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        lookupPending -> Text("수면 기록을 조회하고 있습니다.")
                        sessions.isEmpty() -> Text(
                            if (status == "공유된 수면기록 없음") "수면 기록이 없습니다."
                            else status ?: "수면 기록이 없습니다."
                        )
                        sessions.size == 1 -> {
                            val item = sessions.single()
                            val minutes = item.sleepMinutes
                            Text(if (item.reliable && minutes != null)
                                "수면 기록 1개: ${minutes / 60}시간 ${minutes % 60}분"
                                else "수면 단계가 불충분해 실제 수면시간을 확인할 수 없습니다.")
                            if (item.reliable && minutes != null) {
                                TextButton(enabled = !applying, onClick = {
                                    if (savedHours != null) {
                                        lookupDialog = false
                                        replacementMinutes = minutes
                                    } else {
                                        applying = true
                                        viewModel.fillSleepHoursIfEmpty(date, minutes / 60.0) { successful ->
                                            if (successful) {
                                                prefs.markSleepHandled(date)
                                                lookupDialog = false
                                            } else {
                                                status = "수면시간 저장에 실패했습니다. 현재 기록을 확인해 주세요."
                                            }
                                            applying = false
                                        }
                                    }
                                }) { Text(if (savedHours == null) "수면시간 적용" else "기존 수면시간 교체") }
                            }
                        }
                        else -> {
                            val formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")
                            val zone = ZoneId.systemDefault()
                            Text("실제로 잔 구간을 선택하세요.")
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
                                    Text("${item.start.atZone(zone).format(formatter)} ~ " +
                                        "${item.end.atZone(zone).format(formatter)} · " +
                                        (item.sleepMinutes?.let { "${it / 60}시간 ${it % 60}분" } ?: "수면시간 미확인"),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            val chosen = sessions.filter { it.sourceId in selectedIds }
                            val combined = WatchSleepAggregate.combine(chosen)
                            if (chosen.isNotEmpty() && combined == null) {
                                Text("선택 구간이 겹치거나 불확실해 합산할 수 없습니다.")
                            }
                            if (combined != null) {
                                val minutes = combined.sleepMinutes ?: 0
                                Text("선택 합계: ${minutes / 60}시간 ${minutes % 60}분")
                                TextButton(enabled = !applying, onClick = {
                                    if (savedHours != null) {
                                        lookupDialog = false
                                        replacementMinutes = minutes
                                    } else {
                                        applying = true
                                        viewModel.fillSleepHoursIfEmpty(date, minutes / 60.0) { successful ->
                                            if (successful) {
                                                prefs.markSleepHandled(date)
                                                lookupDialog = false
                                            } else {
                                                status = "저장하지 못했습니다. 기존 수면시간을 확인해 주세요."
                                            }
                                            applying = false
                                        }
                                    }
                                }) { Text(if (savedHours == null) "선택한 수면시간 적용" else "기존 수면시간 교체") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lookupDialog = false }) { Text("닫기") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        OutlinedButton(
            enabled = !applying && !lookupPending,
            onClick = {
                selectedIds = emptySet()
                status = null
                lookupPending = true
                lookupDialog = true
                if (!healthConnectAvailable) {
                    lookupPending = false
                    status = "Health Connect를 사용할 수 없습니다."
                } else if (permitted) {
                    manualLookup = true
                    refresh++
                } else {
                    launcher.launch(setOf(WatchHealthData.SLEEP_PERMISSION))
                }
            }
        ) {
            Text(if (savedHours != null) "워치 수면 다시 선택" else "워치 수면 연동")
        }
        if (savedHours != null) {
            Text("현재 수면시간은 워치 기록을 선택하고 교체를 확인하기 전까지 유지됩니다.",
                style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(onClick = {
            prefs.autoSleep = !enabled
            enabled = prefs.autoSleep
        }) {
            Text(if (enabled) "수면 자동 채우기 끄기" else "수면 자동 채우기 켜기")
        }
    }
}
