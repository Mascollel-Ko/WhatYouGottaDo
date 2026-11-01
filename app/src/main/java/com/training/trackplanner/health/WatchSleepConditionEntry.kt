package com.training.trackplanner.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    var refresh by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(prefs.autoSleep) }
    var permitted by remember { mutableStateOf(false) }
    var sleep by remember(date) { mutableStateOf<WatchSleep?>(null) }
    var multiple by remember(date) { mutableStateOf(false) }
    var status by remember(date) { mutableStateOf<String?>(null) }
    var applying by remember(date) { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        refresh++
    }

    LaunchedEffect(date, refresh) {
        sleep = null
        multiple = false
        if (!gateway.available()) {
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
            val values = gateway.sleep(LocalDate.parse(date))
            // One unambiguous nightly sleep is required for unattended auto-fill.
            multiple = values.size > 1
            sleep = values.singleOrNull()
            status = if (values.isEmpty()) "공유된 수면기록 없음" else if (multiple) {
                "여러 수면 기록이 있어 자동 입력하지 않습니다."
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "수면 기록 조회 실패 · 다시 확인"
        }
    }

    LaunchedEffect(date, sleep, savedHours, enabled, editorOpen) {
        val candidate = sleep ?: return@LaunchedEffect
        if (!enabled || editorOpen || savedHours != null || applying ||
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
            !gateway.available() -> Unit
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
