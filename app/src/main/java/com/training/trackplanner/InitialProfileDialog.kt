package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.InitialUserProfile

@Composable
internal fun InitialProfileDialog(
    profile: InitialUserProfile?,
    onDismiss: () -> Unit,
    onSave: (InitialUserProfile) -> Unit
) {
    val base = profile ?: InitialUserProfile()
    var bodyWeight by rememberSaveable { mutableStateOf(base.bodyWeightKg?.let(::formatDecimal).orEmpty()) }
    var height by rememberSaveable { mutableStateOf(base.heightCm?.let(::formatDecimal).orEmpty()) }
    var age by rememberSaveable { mutableStateOf(base.birthYearOrAgeRange) }
    var gender by rememberSaveable { mutableStateOf(base.gender) }
    var strengthSessions by rememberSaveable { mutableStateOf(base.strengthSessionsPerWeek?.let(::formatDecimal).orEmpty()) }
    var strengthMinutes by rememberSaveable { mutableStateOf(base.strengthMinutesPerSession?.toString().orEmpty()) }
    var strengthRpe by rememberSaveable { mutableStateOf(base.strengthAverageRpe?.let(::formatDecimal).orEmpty()) }
    var badmintonSessions by rememberSaveable { mutableStateOf(base.badmintonSessionsPerWeek?.let(::formatDecimal).orEmpty()) }
    var badmintonMinutes by rememberSaveable { mutableStateOf(base.badmintonMinutesPerSession?.toString().orEmpty()) }
    var badmintonRpe by rememberSaveable { mutableStateOf(base.badmintonAverageRpe?.let(::formatDecimal).orEmpty()) }
    var trainingAge by rememberSaveable { mutableStateOf(base.strengthTrainingAge.ifBlank { base.badmintonTrainingAge }) }
    var breakWeeks by rememberSaveable { mutableStateOf(base.breakWeeks?.toString().orEmpty()) }
    var breakByPain by rememberSaveable { mutableStateOf(base.breakDueToPain) }
    var sleep by rememberSaveable { mutableStateOf(base.typicalSleepHours?.let(::formatDecimal).orEmpty()) }
    var fatigue by rememberSaveable { mutableStateOf(base.currentFatigue?.toString().orEmpty()) }
    var soreness by rememberSaveable { mutableStateOf(base.currentSoreness?.toString().orEmpty()) }
    var stress by rememberSaveable { mutableStateOf(base.currentStress?.toString().orEmpty()) }
    var painAreas by rememberSaveable { mutableStateOf(base.painAreas) }
    var goals by rememberSaveable { mutableStateOf(base.goals) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("초기 프로필") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("체중", bodyWeight, Modifier.weight(1f)) { bodyWeight = it }
                        DecimalField("키", height, Modifier.weight(1f)) { height = it }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField("나이/출생연도", age, Modifier.weight(1f)) { age = it }
                        TextField("성별", gender, Modifier.weight(1f)) { gender = it }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("근력 주당", strengthSessions, Modifier.weight(1f)) { strengthSessions = it }
                        IntField("근력 분", strengthMinutes, Modifier.weight(1f)) { strengthMinutes = it }
                        DecimalField("근력 RPE", strengthRpe, Modifier.weight(1f)) { strengthRpe = it }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("배드민턴 주당", badmintonSessions, Modifier.weight(1f)) { badmintonSessions = it }
                        IntField("배드민턴 분", badmintonMinutes, Modifier.weight(1f)) { badmintonMinutes = it }
                        DecimalField("배드민턴 RPE", badmintonRpe, Modifier.weight(1f)) { badmintonRpe = it }
                    }
                }
                item {
                    TextField("운동 경력", trainingAge, Modifier.fillMaxWidth()) { trainingAge = it }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntField("공백 주", breakWeeks, Modifier.weight(1f)) { breakWeeks = it }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { breakByPain = !breakByPain }
                        ) {
                            Text(if (breakByPain) "통증 공백" else "일반 공백")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("평소 수면", sleep, Modifier.weight(1f)) { sleep = it }
                        IntField("피로 1-5", fatigue, Modifier.weight(1f)) { fatigue = it }
                        IntField("근육통 1-5", soreness, Modifier.weight(1f)) { soreness = it }
                        IntField("스트레스 1-5", stress, Modifier.weight(1f)) { stress = it }
                    }
                }
                item {
                    TextField("통증/주의 부위", painAreas, Modifier.fillMaxWidth()) { painAreas = it }
                }
                item {
                    TextField("목표", goals, Modifier.fillMaxWidth()) { goals = it }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        base.copy(
                            bodyWeightKg = bodyWeight.toDoubleOrNull(),
                            heightCm = height.toDoubleOrNull(),
                            birthYearOrAgeRange = age.trim(),
                            gender = gender.trim(),
                            strengthSessionsPerWeek = strengthSessions.toDoubleOrNull(),
                            strengthMinutesPerSession = strengthMinutes.toIntOrNull(),
                            strengthAverageRpe = strengthRpe.toDoubleOrNull()?.coerceIn(0.0, 10.0),
                            badmintonSessionsPerWeek = badmintonSessions.toDoubleOrNull(),
                            badmintonMinutesPerSession = badmintonMinutes.toIntOrNull(),
                            badmintonAverageRpe = badmintonRpe.toDoubleOrNull()?.coerceIn(0.0, 10.0),
                            strengthTrainingAge = trainingAge.trim(),
                            badmintonTrainingAge = trainingAge.trim(),
                            hadRecentTrainingBreak = breakWeeks.toIntOrNull()?.let { it > 0 } ?: false,
                            breakWeeks = breakWeeks.toIntOrNull(),
                            breakDueToPain = breakByPain,
                            typicalSleepHours = sleep.toDoubleOrNull(),
                            currentFatigue = fatigue.toIntOrNull()?.coerceIn(1, 5),
                            currentSoreness = soreness.toIntOrNull()?.coerceIn(1, 5),
                            currentStress = stress.toIntOrNull()?.coerceIn(1, 5),
                            painAreas = painAreas.trim(),
                            goals = goals.trim()
                        )
                    )
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun TextField(
    label: String,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
private fun DecimalField(
    label: String,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { if (isDecimalInput(it)) onValueChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun IntField(
    label: String,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { if (it.isUnsignedInt()) onValueChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}
