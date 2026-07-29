package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.TrainingProgramItemSet

@Composable
internal fun ProgramDaySummarySection(
    weekNumber: Int,
    dayOfWeek: Int,
    items: List<TrainingProgramItem>,
    setsByItemId: Map<Long, List<TrainingProgramItemSet>>,
    onExerciseInfo: (String) -> Unit,
    availableExerciseKeys: Set<String>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = programDayLabel(weekNumber, dayOfWeek),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        items.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.exerciseName, fontWeight = FontWeight.SemiBold)
                            item.category.takeIf(String::isNotBlank)?.let { category ->
                                Text(
                                    category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (item.exerciseStableKey in availableExerciseKeys) {
                            IconButton(onClick = { onExerciseInfo(item.exerciseStableKey) }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "${item.exerciseName} 운동 정보"
                                )
                            }
                        }
                    }
                    programPrescriptionLines(
                        item,
                        setsByItemId[item.id].orEmpty()
                    ).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (item.restSeconds > 0) {
                        Text(
                            "세트 간 휴식 ${item.restSeconds}초",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.prescription.isNotBlank()) {
                        Text(
                            item.prescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
private fun programPrescriptionLines(
    item: TrainingProgramItem,
    storedSets: List<TrainingProgramItemSet>
): List<String> {
    val sets = ProgramSetPrescriptionResolver.resolve(item, storedSets)
    val uniform = sets.map { Triple(it.reps, it.weightKg, it.seconds) }.distinct().size == 1
    return if (uniform) {
        listOf("${sets.size}세트 · ${sets.first().displayText()}")
    } else {
        sets.map { set -> "${set.setIndex}세트 · ${set.displayText()}" }
    }
}

private fun ProgramSetPrescription.displayText(): String =
    buildList {
        if (reps > 0) add("${reps}회")
        if (weightKg > 0.0) add("${formatDecimal(weightKg)}kg")
        if (seconds > 0) add("${seconds}초")
    }.ifEmpty { listOf("처방 없음") }.joinToString(" · ")


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
