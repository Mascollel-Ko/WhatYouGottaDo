package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem

@Composable
internal fun ProgramSkeletonPreview(
    skeleton: GeneratedProgramSkeleton,
    onUpdateItem: (ProgramSkeletonItem) -> Unit,
    onDeleteItem: (ProgramSkeletonItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "생성된 프로그램 미리보기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "${skeleton.periodizationType.displayLabel()} · ${skeleton.items.size}개 운동 배치",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            skeleton.warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            skeleton.weekPlans.forEach { weekPlan ->
                Text(
                    text = "${weekPlan.weekIndex}주차 ${weekPlan.weekType} · volume ${formatDecimal(weekPlan.volumeMultiplier)} / intensity ${formatDecimal(weekPlan.intensityMultiplier)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                skeleton.items
                    .filter { it.weekNumber == weekPlan.weekIndex }
                    .groupBy { it.dayOfWeek }
                    .toSortedMap()
                    .forEach { (dayOfWeek, dayItems) ->
                        Text(
                            text = listOfNotNull(
                                programDayLabel(weekPlan.weekIndex, dayOfWeek),
                                dayItems.firstOrNull()?.trainingSlot?.toTrainingSlotLabel()
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        dayItems.sortedBy { it.orderIndex }.forEach { item ->
                            ProgramSkeletonItemEditor(
                                item = item,
                                onUpdateItem = onUpdateItem,
                                onDeleteItem = onDeleteItem
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun ProgramSkeletonItemEditor(
    item: ProgramSkeletonItem,
    onUpdateItem: (ProgramSkeletonItem) -> Unit,
    onDeleteItem: (ProgramSkeletonItem) -> Unit
) {
    var restText by rememberSaveable(item.localId) { mutableStateOf(item.restSeconds.toString()) }
    var setCountText by rememberSaveable(item.localId) { mutableStateOf(item.setCount.toString()) }
    var repsText by rememberSaveable(item.localId) { mutableStateOf(item.reps.toString()) }
    var weightText by rememberSaveable(item.localId) { mutableStateOf(formatDecimal(item.weightKg)) }
    var secondsText by rememberSaveable(item.localId) { mutableStateOf(item.seconds.toString()) }
    var prescriptionText by rememberSaveable(item.localId) { mutableStateOf(item.prescription) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${item.selectionReason} · ${item.weightSource}",
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
                label = { Text("처방 / RPE / 자동중량 출처") },
                minLines = 2
            )
        }
    }
}

private fun String.toTrainingSlotLabel(): String = when (this) {
    "FULL_BODY_BADMINTON_SUPPORT" -> "전신 배드민턴 보강"
    "LOWER_TRANSFER_FULL" -> "하체 전이"
    "UPPER_SCAP_CORE_FULL" -> "상체·견갑·코어"
    "LOWER_STRENGTH", "LOWER_STRENGTH_HEAVY" -> "하체 근력"
    "UPPER_STRENGTH", "UPPER_STRENGTH_SCAP" -> "상체 근력·견갑"
    "BADMINTON_TRANSFER", "BADMINTON_COD", "BADMINTON_COD_DECEL" -> "배드민턴 전이·감속"
    "POWER_REACTIVE", "POWER_REACTIVE_LIGHT" -> "파워·반응"
    "RECOVERY_PREHAB", "RECOVERY_WEAKPOINT", "MICRO_RECOVERY" -> "회복·프리햅"
    "WEAKPOINT_ACCESSORY" -> "약점 보강"
    else -> this
}
