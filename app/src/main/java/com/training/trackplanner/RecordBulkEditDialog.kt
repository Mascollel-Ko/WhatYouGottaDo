package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet


@Composable
internal fun BulkEditDialog(
    entry: WorkoutEntry,
    sets: List<WorkoutSet>,
    showWeight: Boolean,
    onDismiss: () -> Unit,
    onUpdateSet: (WorkoutSet) -> Unit
) {
    var operation by remember { mutableStateOf<BulkOperation?>(null) }
    var valueText by rememberSaveable(operation?.label.orEmpty()) { mutableStateOf("") }
    var includeConfirmed by rememberSaveable { mutableStateOf(false) }
    val selected = operation
    val hasConfirmed = sets.any { it.confirmed }
    val lastPositiveKg = sets.firstOrNull { it.weightKg > 0.0 }?.weightKg
    val copySource = sets.firstOrNull()
    val operations = buildList {
        if (showWeight) {
            add(BulkOperation("kg 설정", BulkField.Weight, BulkMode.Set))
            add(BulkOperation("kg +", BulkField.Weight, BulkMode.Increase))
            add(BulkOperation("kg -", BulkField.Weight, BulkMode.Decrease))
        }
        add(BulkOperation("횟수 +", BulkField.Reps, BulkMode.Increase))
        add(BulkOperation("횟수 -", BulkField.Reps, BulkMode.Decrease))
        add(BulkOperation("RPE 설정", BulkField.Rpe, BulkMode.Set))
        add(BulkOperation("휴식 설정", BulkField.Rest, BulkMode.Set))
        add(BulkOperation("휴식 +", BulkField.Rest, BulkMode.Increase))
        add(BulkOperation("휴식 -", BulkField.Rest, BulkMode.Decrease))
        add(BulkOperation("선택 복사", BulkField.Copy, BulkMode.CopyValues))
        add(BulkOperation("기록 상태까지 선택 복사", BulkField.Copy, BulkMode.CopyValuesAndStatus))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일괄 편집") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selected?.let { selected ->
                    Text(selected.label)
                    BulkOperationEditor(
                        operation = selected,
                        valueText = valueText,
                        sourceSet = copySource,
                        onValueChange = { valueText = it }
                    )
                    if (hasConfirmed) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { includeConfirmed = !includeConfirmed }
                        ) {
                            Text(if (includeConfirmed) "완료 세트 포함" else "미확인 세트만")
                        }
                        if (includeConfirmed) {
                            Text(
                                text = "완료 세트도 수정됩니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } ?: run {
                    operations.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        operation = item
                                        valueText = defaultBulkValue(item)
                                    }
                                ) {
                                    Text(item.label)
                                }
                            }
                            if (row.size == 1) {
                                Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                    if (showWeight && lastPositiveKg != null) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                sets.filter { set ->
                                    set.weightKg == 0.0 &&
                                        !set.manualWeight &&
                                        !set.confirmed
                                }.forEach { set ->
                                    onUpdateSet(set.copy(weightKg = lastPositiveKg, manualWeight = true))
                                }
                                onDismiss()
                            }
                        ) {
                            Text("빈 kg에 ${formatWeight(lastPositiveKg)}kg 적용")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selected != null) {
                Button(
                    enabled = canApplyBulkOperation(selected, valueText, copySource),
                    onClick = {
                        applyBulkOperationToTargets(
                            entry = entry,
                            sets = sets,
                            operation = selected,
                            valueText = valueText,
                            includeConfirmed = includeConfirmed,
                            onUpdateSet = onUpdateSet
                        )
                        onDismiss()
                    }
                ) {
                    Text(if (includeConfirmed) "완료 포함 적용" else "적용")
                }
            }
        },
        dismissButton = {
            Row {
                if (selected != null) {
                    TextButton(onClick = { valueText = defaultBulkValue(selected) }) {
                        Text("초기화")
                    }
                    TextButton(onClick = { operation = null }) {
                        Text("메뉴")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(if (selected == null) "닫기" else "취소")
                }
            }
        }
    )
}

@Composable
private fun BulkOperationEditor(
    operation: BulkOperation,
    valueText: String,
    sourceSet: WorkoutSet?,
    onValueChange: (String) -> Unit
) {
    when {
        operation.field == BulkField.Copy -> {
            Text(
                text = sourceSet?.let { source ->
                    "기준: ${source.setIndex}세트 · ${source.reps}회 · ${formatWeight(source.weightKg)}kg · RPE ${source.rpe?.let(::formatRpe) ?: "-"} · ${if (source.confirmed) "완료" else "미확인"}"
                } ?: "복사할 기준 세트가 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        operation.field == BulkField.Rpe -> {
            Text(
                text = "적용 예정: ${bulkRpeLabel(valueText)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            BulkQuickValueRow(listOf("6", "7", "8", "9", "10")) { onValueChange(it) }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onValueChange(BULK_CLEAR_RPE) }
            ) {
                Text("RPE 없음")
            }
        }
        operation.mode == BulkMode.Increase || operation.mode == BulkMode.Decrease -> {
            Text(
                text = "적용 예정: ${bulkDeltaLabel(operation, valueText)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            BulkQuickValueRow(quickValues(operation.field)) { value ->
                onValueChange(addBulkDelta(valueText, value))
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = valueText,
                onValueChange = { value ->
                    val allowed = if (operation.field == BulkField.Weight) isDecimalInput(value) else value.isUnsignedInt()
                    if (allowed) onValueChange(value)
                },
                label = { Text(if (operation.field == BulkField.Weight) "누적 kg" else "누적 횟수") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (operation.field == BulkField.Weight) KeyboardType.Decimal else KeyboardType.Number
                ),
                singleLine = true
            )
        }
        else -> {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = valueText,
                onValueChange = { value ->
                    val allowed = when (operation.field) {
                        BulkField.Weight -> isDecimalInput(value)
                        BulkField.Reps, BulkField.Rest -> value.isUnsignedInt()
                        BulkField.Rpe -> isDecimalInput(value)
                        BulkField.Copy -> true
                    }
                    if (allowed) onValueChange(value)
                },
                label = {
                    Text(
                        when (operation.field) {
                            BulkField.Weight -> "kg"
                            BulkField.Reps -> "횟수"
                            BulkField.Rest -> "초"
                            BulkField.Rpe -> "RPE"
                            BulkField.Copy -> ""
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (operation.field == BulkField.Weight || operation.field == BulkField.Rpe) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Number
                    }
                ),
                singleLine = true
            )
            BulkQuickValueRow(quickValues(operation.field)) { onValueChange(it) }
        }
    }
}

@Composable
private fun BulkQuickValueRow(values: List<String>, onSelect: (String) -> Unit) {
    values.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { value ->
                OutlinedButton(
                    modifier = Modifier.weight(1f).defaultMinSize(minWidth = 0.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    onClick = { onSelect(value) }
                ) {
                    Text(value)
                }
            }
            repeat(4 - row.size) {
                Column(modifier = Modifier.weight(1f)) {}
            }
        }
    }
}

private fun quickValues(field: BulkField): List<String> = when (field) {
    BulkField.Weight -> listOf("1", "2", "2.5", "5")
    BulkField.Reps -> listOf("1", "2")
    BulkField.Rest -> listOf("15", "30", "60")
    BulkField.Rpe -> listOf("6", "7", "8", "9", "10")
    BulkField.Copy -> emptyList()
}

internal enum class BulkField {
    Weight,
    Reps,
    Rest,
    Rpe,
    Copy
}

internal enum class BulkMode {
    Set,
    Increase,
    Decrease,
    CopyValues,
    CopyValuesAndStatus
}

internal data class BulkOperation(
    val label: String,
    val field: BulkField,
    val mode: BulkMode
)

private const val BULK_CLEAR_RPE = "__CLEAR_RPE__"

internal fun defaultBulkValue(operation: BulkOperation): String =
    when (operation.field) {
        BulkField.Weight -> if (operation.mode == BulkMode.Set) "" else "2.5"
        BulkField.Reps -> "1"
        BulkField.Rest -> if (operation.mode == BulkMode.Set) "60" else "30"
        BulkField.Rpe,
        BulkField.Copy -> ""
    }

internal fun addBulkDelta(current: String, option: String): String {
    val sum = (current.toDoubleOrNull() ?: 0.0) + (option.toDoubleOrNull() ?: 0.0)
    return formatWeight(sum)
}

private fun bulkDeltaLabel(operation: BulkOperation, valueText: String): String {
    val value = valueText.toDoubleOrNull() ?: 0.0
    val sign = if (operation.mode == BulkMode.Decrease) "-" else "+"
    val unit = when (operation.field) {
        BulkField.Weight -> "kg"
        BulkField.Reps -> "회"
        else -> ""
    }
    return "$sign${formatWeight(value)}$unit"
}

private fun bulkRpeLabel(valueText: String): String =
    if (valueText == BULK_CLEAR_RPE) "RPE 없음" else valueText.toDoubleOrNull()?.let(::formatRpe) ?: "-"

internal fun canApplyBulkOperation(
    operation: BulkOperation,
    valueText: String,
    sourceSet: WorkoutSet?
): Boolean = when (operation.field) {
    BulkField.Copy -> sourceSet != null
    BulkField.Rpe -> valueText == BULK_CLEAR_RPE || valueText.toDoubleOrNull()?.let { it in 0.0..10.0 } == true
    BulkField.Weight -> valueText.toDoubleOrNull() != null
    BulkField.Reps,
    BulkField.Rest -> valueText.toIntOrNull() != null
}

private fun applyBulkOperationToTargets(
    entry: WorkoutEntry,
    sets: List<WorkoutSet>,
    operation: BulkOperation,
    valueText: String,
    includeConfirmed: Boolean,
    onUpdateSet: (WorkoutSet) -> Unit
) {
    val source = sets.firstOrNull()
    val value = valueText.toDoubleOrNull()
    sets.filter { includeConfirmed || !it.confirmed }
        .filter { operation.field != BulkField.Copy || it.id != source?.id }
        .forEach { set ->
            val updated = when (operation.field) {
                BulkField.Copy -> source?.let {
                    copyBulkSetValues(it, set, includeStatus = operation.mode == BulkMode.CopyValuesAndStatus)
                } ?: set
                BulkField.Rpe -> set.copy(rpe = if (valueText == BULK_CLEAR_RPE) null else value?.coerceIn(0.0, 10.0))
                else -> value?.let { applyBulkOperation(entry, set, operation, it) } ?: set
            }
            onUpdateSet(updated)
        }
}

internal fun copyBulkSetValues(
    source: WorkoutSet,
    target: WorkoutSet,
    includeStatus: Boolean
): WorkoutSet =
    target.copy(
        reps = source.reps,
        weightKg = source.weightKg,
        seconds = source.seconds,
        manualWeight = source.manualWeight,
        rpe = source.rpe,
        restSecondsOverride = source.restSecondsOverride,
        confirmed = if (includeStatus) source.confirmed else target.confirmed
    )

internal fun applyBulkOperation(
    entry: WorkoutEntry,
    set: WorkoutSet,
    operation: BulkOperation,
    rawValue: Double
): WorkoutSet =
    when (operation.field) {
        BulkField.Weight -> {
            val newWeight = when (operation.mode) {
                BulkMode.Set -> rawValue
                BulkMode.Increase -> set.weightKg + rawValue
                BulkMode.Decrease -> set.weightKg - rawValue
                BulkMode.CopyValues,
                BulkMode.CopyValuesAndStatus -> set.weightKg
            }.coerceAtLeast(0.0)
            set.copy(weightKg = newWeight, manualWeight = true)
        }
        BulkField.Reps -> {
            val delta = rawValue.toInt()
            val newReps = when (operation.mode) {
                BulkMode.Set -> delta
                BulkMode.Increase -> set.reps + delta
                BulkMode.Decrease -> set.reps - delta
                BulkMode.CopyValues,
                BulkMode.CopyValuesAndStatus -> set.reps
            }.coerceAtLeast(0)
            set.copy(reps = newReps)
        }
        BulkField.Rest -> {
            val delta = rawValue.toInt()
            val base = set.restSecondsOverride ?: entry.restSeconds
            val nextRest = when (operation.mode) {
                BulkMode.Set -> delta
                BulkMode.Increase -> base + delta
                BulkMode.Decrease -> base - delta
                BulkMode.CopyValues,
                BulkMode.CopyValuesAndStatus -> base
            }.coerceAtLeast(0)
            set.copy(restSecondsOverride = nextRest)
        }
        BulkField.Rpe,
        BulkField.Copy -> set
    }

