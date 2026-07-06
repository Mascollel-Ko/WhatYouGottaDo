package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem

@Composable
internal fun ProgramDaySummarySection(
    weekNumber: Int,
    dayOfWeek: Int,
    items: List<TrainingProgramItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = programDayLabel(weekNumber, dayOfWeek),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (items.isEmpty()) {
                Text(
                    text = "- 휴식 또는 미지정",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    Text(
                        text = programItemSummary(item),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgramDaySection(
    weekNumber: Int,
    dayOfWeek: Int,
    items: List<TrainingProgramItem>,
    onAddExercise: () -> Unit,
    onUpdateItem: (TrainingProgramItem) -> Unit,
    onDeleteItem: (TrainingProgramItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = programDayLabel(weekNumber, dayOfWeek),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (items.isEmpty()) {
                InfoCard("이 요일에는 아직 운동이 없습니다.")
            } else {
                items.forEach { item ->
                    ProgramItemEditor(
                        item = item,
                        onUpdateItem = onUpdateItem,
                        onDeleteItem = onDeleteItem
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddExercise
            ) {
                Text("운동 추가")
            }
        }
    }
}

@Composable
private fun ProgramItemEditor(
    item: TrainingProgramItem,
    onUpdateItem: (TrainingProgramItem) -> Unit,
    onDeleteItem: (TrainingProgramItem) -> Unit
) {
    var restText by rememberSaveable(item.id) { mutableStateOf(item.restSeconds.toString()) }
    var setCountText by rememberSaveable(item.id) { mutableStateOf(item.setCount.toString()) }
    var repsText by rememberSaveable(item.id) { mutableStateOf(item.reps.toString()) }
    var weightText by rememberSaveable(item.id) { mutableStateOf(formatDecimal(item.weightKg)) }
    var secondsText by rememberSaveable(item.id) { mutableStateOf(item.seconds.toString()) }
    var prescriptionText by rememberSaveable(item.id) { mutableStateOf(item.prescription) }

    LaunchedEffect(item) {
        restText = item.restSeconds.toString()
        setCountText = item.setCount.toString()
        repsText = item.reps.toString()
        weightText = formatDecimal(item.weightKg)
        secondsText = item.seconds.toString()
        prescriptionText = item.prescription
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onDeleteItem(item) }) {
                    Text("삭제")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "세트",
                    value = setCountText,
                    onChange = {
                        setCountText = it
                        onUpdateItem(item.copy(setCount = (it.toIntOrNull() ?: 1).coerceAtLeast(1)))
                    }
                )
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "반복",
                    value = repsText,
                    onChange = {
                        repsText = it
                        onUpdateItem(item.copy(reps = it.toIntOrNull() ?: 0))
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramDecimalField(
                    modifier = Modifier.weight(1f),
                    label = "중량",
                    suffix = "kg",
                    value = weightText,
                    onChange = {
                        weightText = it
                        onUpdateItem(item.copy(weightKg = it.toDoubleOrNull() ?: 0.0))
                    }
                )
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "시간",
                    suffix = "초",
                    value = secondsText,
                    onChange = {
                        secondsText = it
                        onUpdateItem(item.copy(seconds = it.toIntOrNull() ?: 0))
                    }
                )
            }
            ProgramNumberField(
                modifier = Modifier.fillMaxWidth(),
                label = "휴식",
                suffix = "초",
                value = restText,
                onChange = {
                    restText = it
                    onUpdateItem(item.copy(restSeconds = it.toIntOrNull() ?: 0))
                }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = prescriptionText,
                onValueChange = {
                    prescriptionText = it
                    onUpdateItem(item.copy(prescription = it))
                },
                label = { Text("처방 메모") },
                minLines = 2
            )
        }
    }
}

@Composable
internal fun ProgramCard(
    program: TrainingProgram,
    onClick: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = program.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(onClick = onApply) {
                    Text("적용")
                }
            }
            Text(
                text = "${program.durationDays}일 프로그램",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (program.weeklyTrainingDays > 0) {
                Text(
                    text = "${program.weeklyTrainingDays}일/주",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onClick
                ) {
                    Text("열기")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onEdit
                ) {
                    Text("수정")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDelete
                ) {
                    Text("삭제")
                }
            }
        }
    }
}

private fun programItemSummary(item: TrainingProgramItem): String {
    val prescriptionParts = buildList {
        if (item.setCount > 0 && item.reps > 0) add("${item.setCount}세트 x ${item.reps}회")
        if (item.weightKg > 0.0) add("${formatDecimal(item.weightKg)}kg")
        if (item.seconds > 0) add("${item.seconds}초")
        if (item.restSeconds > 0) add("휴식 ${item.restSeconds}초")
        if (item.prescription.isNotBlank()) add(item.prescription)
    }
    return if (prescriptionParts.isEmpty()) {
        "- ${item.exerciseName}"
    } else {
        "- ${item.exerciseName}: ${prescriptionParts.joinToString(", ")}"
    }
}
