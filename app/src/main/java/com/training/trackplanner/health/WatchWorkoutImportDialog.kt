package com.training.trackplanner.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Product-side candidate selector. One or more explicit sessions; never creates or confirms a set. */
@Composable
internal fun WatchWorkoutImportDialog(
    target: WorkoutEntryWithSets,
    date: String,
    automatic: Boolean,
    onDismiss: () -> Unit,
    onApply: (entryId: Long, setId: Long, previousSeconds: Int, newSeconds: Int, onResult: (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val gateway = remember(context) { WatchHealthData(context) }
    val prefs = remember(context) { WatchImportPreferences(context) }
    var tick by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var sessions by remember(target.entry.id, date) { mutableStateOf(emptyList<WatchSession>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember(target.entry.id, date) { mutableStateOf(emptySet<String>()) }
    var duration by remember(target.entry.id, date) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { tick++ }

    LaunchedEffect(target.entry.id, date, tick) {
        if (!gateway.available()) {
            loading = false
            error = "이 기기는 Health Connect를 지원하지 않습니다."
            return@LaunchedEffect
        }
        try {
            granted = WatchHealthData.EXERCISE_PERMISSION in gateway.permissions()
            if (!granted) {
                loading = false
                if (automatic) onDismiss()
                return@LaunchedEffect
            }
            if (LocalDate.parse(date).isAfter(LocalDate.now())) {
                loading = false
                onDismiss()
                return@LaunchedEffect
            }
            loading = true
            error = null
            val results = gateway.exercises(LocalDate.parse(date))
            sessions = results
            loading = false
            val qualified = results.filter { WatchSportPolicy.matches(target.entry, it) &&
                (prefs.linkedTarget(it.sourceId) == null ||
                    prefs.linkedTarget(it.sourceId) == target.entry.backupSourceId) }
            if (automatic && qualified.isEmpty()) {
                onDismiss()
            } else if (automatic && qualified.size == 1) {
                selected = setOf(qualified.single().sourceId)
                duration = qualified.single().minutes.toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loading = false
            error = "운동 기록 조회 실패: " + e.javaClass.simpleName
            if (automatic) onDismiss()
        }
    }

    val picked = sessions.filter { it.sourceId in selected }.sortedBy { it.start }
    val overlapping = picked.zipWithNext().any { (a, b) -> b.start < a.end }
    val summedMinutes = picked.sumOf { it.minutes }
    val elapsedMinutes = if (picked.isEmpty()) 0 else
        ((picked.maxOf { it.end }.epochSecond - picked.minOf { it.start }.epochSecond + 30) / 60).toInt()
    val set = target.sets.sortedWith(compareBy({ it.confirmed }, { it.setIndex })).firstOrNull()
    val seconds = duration.toIntOrNull()?.takeIf { it in 1..1440 }?.times(60)
    val anyWalking = picked.any { it.walking }
    val allowed = !saving && set != null && seconds != null && picked.isNotEmpty() && !overlapping &&
        picked.all { prefs.linkedTarget(it.sourceId) == null ||
            prefs.linkedTarget(it.sourceId) == target.entry.backupSourceId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("워치 운동시간 가져오기") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(8f))
            ) {
                Text("${date} · ${target.entry.exerciseName}", fontWeight = FontWeight.SemiBold)
                when {
                    loading -> Text("Health Connect에서 조회 중…")
                    !gateway.available() -> Text(error.orEmpty())
                    !granted -> Text("운동 세션을 읽으려면 Health Connect 운동 읽기 권한이 필요합니다.")
                    error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    sessions.isEmpty() -> Text("이 날짜에 공유된 운동 세션이 없습니다. 삼성 헬스 동기화 후 다시 확인하세요.")
                    else -> {
                        Text("기록할 구간을 선택하세요. 일상 걷기는 자동 선택하지 않습니다.")
                        sessions.forEach { session ->
                            val usedBy = prefs.linkedTarget(session.sourceId)
                            val unavailable = usedBy != null && usedBy != target.entry.backupSourceId
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(
                                androidx.compose.ui.unit.Dp(6f)
                            )) {
                                Checkbox(
                                    checked = session.sourceId in selected,
                                    enabled = !unavailable && !saving,
                                    onCheckedChange = { check ->
                                        selected = if (check) selected + session.sourceId else selected - session.sourceId
                                        val chosen = sessions.filter { it.sourceId in selected }
                                        duration = chosen.sumOf { it.minutes }.toString()
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${WatchSportPolicy.label(session)} · ${session.minutes}분")
                                    Text(
                                        "${watchClock(session.start)}~${watchClock(session.end)} · ${session.sourceName}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (unavailable) Text("다른 운동 기록에 사용됨", color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (picked.isNotEmpty()) {
                            Text("선택 구간 합계 ${summedMinutes}분 · 첫 시작~마지막 종료 ${elapsedMinutes}분",
                                style = MaterialTheme.typography.bodySmall)
                            if (anyWalking) Text(
                                "선택한 구간에는 워치가 걷기로 감지한 기록이 포함돼 있습니다. 실제 운동시간만 확인해 주세요.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (overlapping) Text(
                                "서로 겹치는 세션은 중복 계산 위험이 있어 함께 적용할 수 없습니다.",
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedTextField(
                                value = duration,
                                onValueChange = { value -> if (value.isEmpty() || value.all(Char::isDigit)) duration = value },
                                label = { Text("최종 운동시간 (분)") },
                                singleLine = true
                            )
                            if (set != null) Text(
                                "기존 ${set.seconds / 60}분 → ${duration}분 · ${if (set.confirmed) "확인된 세트 수정" else "미확인 세트"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("운동 확인 여부와 RPE는 바꾸지 않습니다.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!granted && gateway.available()) {
                TextButton(onClick = { launcher.launch(setOf(WatchHealthData.EXERCISE_PERMISSION)) }) {
                    Text("운동 권한 허용")
                }
            } else {
                TextButton(
                    enabled = allowed,
                    onClick = {
                        val targetSet = set ?: return@TextButton
                        val targetSeconds = seconds ?: return@TextButton
                        val sources = picked.map { it.sourceId }
                        saving = true
                        onApply(target.entry.id, targetSet.id, targetSet.seconds, targetSeconds) { success ->
                            saving = false
                            if (success) {
                                prefs.markSessionsUsed(sources, target.entry.backupSourceId ?: target.entry.id.toString())
                                onDismiss()
                            } else {
                                error = "다른 기록에서 시간이 변경되었거나 저장에 실패했습니다. 다시 열어 확인해 주세요."
                            }
                        }
                    }
                ) { Text(if (saving) "저장 중…" else "운동시간 적용") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("직접 입력 / 취소") } }
    )
}

internal object WatchSportPolicy {
    fun matches(entry: WorkoutEntry, session: WatchSession): Boolean {
        val badminton = entry.exerciseStableKey in setOf("ex_ae9ecdbc", "ex_badminton_lesson")
        if (badminton) return session.badminton
        val name = entry.exerciseName.lowercase()
        return when {
            "달리기" in name || "러닝" in name || "running" in name -> session.type ==
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "걷기" in name || "walking" in name -> false // Low-intensity automatic prompts excluded.
            else -> false // Unknown types require manual selection.
        }
    }

    fun label(session: WatchSession): String = when {
        session.badminton -> "배드민턴"
        session.walking -> "걷기(자동 제안 제외)"
        session.type == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "달리기"
        else -> "기타 운동 (종류 코드 ${session.type})"
    }
}

private fun watchClock(value: Instant): String =
    value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
