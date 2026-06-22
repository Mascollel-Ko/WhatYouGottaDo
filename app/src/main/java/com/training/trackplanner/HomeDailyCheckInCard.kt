package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.DailyCheckIn
import java.time.LocalDate

@Composable
internal fun HomeDailyCheckInCard(
    checkIn: DailyCheckIn?,
    onSave: (DailyCheckIn) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("오늘 컨디션", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    checkIn?.compactSummary() ?: "아직 입력하지 않았습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { showEditor = true }) {
                Text(if (checkIn == null) "입력" else "수정")
            }
        }
    }

    if (showEditor) {
        DailyCheckInDialog(
            checkIn = checkIn,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                onSave(saved)
                showEditor = false
            }
        )
    }
}

@Composable
private fun DailyCheckInDialog(
    checkIn: DailyCheckIn?,
    onDismiss: () -> Unit,
    onSave: (DailyCheckIn) -> Unit
) {
    var sleepHours by remember { mutableStateOf("") }
    var overallFatigue by remember { mutableStateOf<Int?>(null) }
    var lowerBodyFatigue by remember { mutableStateOf<Int?>(null) }
    var jointTendonDiscomfort by remember { mutableStateOf<Int?>(null) }
    var focusMotivation by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(checkIn?.updatedAt) {
        sleepHours = checkIn?.sleepHours?.let(::formatHours).orEmpty()
        overallFatigue = checkIn?.overallFatigue
        lowerBodyFatigue = checkIn?.lowerBodyFatigue
        jointTendonDiscomfort = checkIn?.jointTendonDiscomfort
        focusMotivation = checkIn?.focusMotivation
    }

    val parsedSleep = sleepHours.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val sleepValid = sleepHours.isBlank() || (parsedSleep != null && parsedSleep in 0.0..24.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오늘 컨디션") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    "운동 부하와 함께 코치 분석에 반영되는 훈련 조절 참고 신호입니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = sleepHours,
                    onValueChange = { value -> sleepHours = value.filter { it.isDigit() || it == '.' }.take(4) },
                    modifier = Modifier.width(150.dp),
                    label = { Text("수면시간") },
                    suffix = { Text("시간") },
                    singleLine = true,
                    isError = !sleepValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                CheckInScoreRow("전신 피로", overallFatigue) { overallFatigue = it }
                CheckInScoreRow("하체 피로", lowerBodyFatigue) { lowerBodyFatigue = it }
                CheckInScoreRow("관절/건 불편감", jointTendonDiscomfort) { jointTendonDiscomfort = it }
                CheckInScoreRow("집중력/의욕", focusMotivation) { focusMotivation = it }
                Text(
                    "피로·불편감은 1이 낮음, 5가 높음입니다. 집중력/의욕은 5가 좋음입니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        DailyCheckIn(
                            date = LocalDate.now().toString(),
                            sleepHours = parsedSleep,
                            overallFatigue = overallFatigue,
                            lowerBodyFatigue = lowerBodyFatigue,
                            jointTendonDiscomfort = jointTendonDiscomfort,
                            focusMotivation = focusMotivation,
                            createdAt = checkIn?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = sleepValid
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun CheckInScoreRow(label: String, selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..5).forEach { score ->
                FilterChip(
                    selected = selected == score,
                    onClick = { onSelect(score.takeUnless { it == selected }) },
                    label = { Text(score.toString()) }
                )
            }
        }
    }
}

private fun DailyCheckIn.compactSummary(): String = buildList {
    sleepHours?.let { add("수면 ${formatHours(it)}시간") }
    overallFatigue?.let { add("전신 $it") }
    lowerBodyFatigue?.let { add("하체 $it") }
    jointTendonDiscomfort?.let { add("불편감 $it") }
    focusMotivation?.let { add("집중/의욕 $it") }
}.joinToString(" · ").ifBlank { "입력값 없음" }

private fun formatHours(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else "%.1f".format(hours)
