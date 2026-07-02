package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet


@Composable
internal fun WorkoutSetRow(
    entry: WorkoutEntry,
    set: WorkoutSet,
    sets: List<WorkoutSet>,
    showWeight: Boolean,
    isSportDurationInput: Boolean,
    canDelete: Boolean,
    onUpdateSet: (WorkoutSet) -> Unit,
    onDeleteSet: (WorkoutSet) -> Unit,
    timerState: RestTimerState,
    onStopRestTimer: () -> Unit,
    onPositiveWeightEdit: (WorkoutSet, Double) -> Unit,
    onStartRestTimer: (WorkoutSet, Int) -> Unit
) {
    var repsText by rememberSaveable(set.id) { mutableStateOf(set.reps.toString()) }
    var weightText by rememberSaveable(set.id) { mutableStateOf(formatDecimal(set.weightKg)) }
    var secondsText by rememberSaveable(set.id) { mutableStateOf(set.seconds.toString()) }
    var sportHoursText by rememberSaveable(set.id) { mutableStateOf(totalDurationToHoursMinutes(set.seconds).hours.toString()) }
    var sportMinutesText by rememberSaveable(set.id) { mutableStateOf(totalDurationToHoursMinutes(set.seconds).minutes.toString()) }
    var repsFocused by rememberSaveable(set.id) { mutableStateOf(false) }
    var weightFocused by rememberSaveable(set.id) { mutableStateOf(false) }
    var secondsFocused by rememberSaveable(set.id) { mutableStateOf(false) }
    var sportDurationFocused by rememberSaveable(set.id) { mutableStateOf(false) }
    var showRpeDialog by rememberSaveable(set.id) { mutableStateOf(false) }
    var showRestDialog by rememberSaveable(set.id) { mutableStateOf(false) }
    var isExpanded by rememberSaveable(set.id) { mutableStateOf(false) }
    var editField by rememberSaveable(set.id) { mutableStateOf<SetEditField?>(null) }
    val effectiveRestSeconds = set.restSecondsOverride ?: entry.restSeconds
    val isTimerTarget = timerState.targetEntryId == entry.id && timerState.targetSetId == set.id

    LaunchedEffect(set.reps, set.weightKg, set.seconds) {
        if (!repsFocused) repsText = set.reps.toString()
        if (!weightFocused) weightText = formatDecimal(set.weightKg)
        if (!secondsFocused) secondsText = set.seconds.toString()
        if (!sportDurationFocused) {
            val parts = totalDurationToHoursMinutes(set.seconds)
            sportHoursText = parts.hours.toString()
            sportMinutesText = parts.minutes.toString()
        }
    }

    fun applySportDuration(hoursText: String = sportHoursText, minutesText: String = sportMinutesText) {
        val seconds = hoursMinutesToStoredDuration(
            hours = hoursText.toIntOrNull() ?: 0,
            minutes = minutesText.toIntOrNull() ?: 0
        )
        onUpdateSet(set.copy(seconds = seconds))
    }

    fun normalizeSportDurationText() {
        val parts = normalizeHoursMinutes(
            hours = sportHoursText.toIntOrNull() ?: 0,
            minutes = sportMinutesText.toIntOrNull() ?: 0
        )
        sportHoursText = parts.hours.toString()
        sportMinutesText = parts.minutes.toString()
        onUpdateSet(set.copy(seconds = hoursMinutesToStoredDuration(parts.hours, parts.minutes)))
    }

    if (showRpeDialog) {
        RpeDialog(
            currentRpe = set.rpe,
            onDismiss = { showRpeDialog = false },
            onSelect = { selectedRpe ->
                onUpdateSet(set.copy(rpe = selectedRpe))
                showRpeDialog = false
            }
        )
    }
    if (showRestDialog) {
        RestOverrideDialog(
            entryRestSeconds = entry.restSeconds,
            currentOverride = set.restSecondsOverride,
            onDismiss = { showRestDialog = false },
            onApply = { override ->
                onUpdateSet(set.copy(restSecondsOverride = override))
                showRestDialog = false
            }
        )
    }
    editField?.let { field ->
        SetValueEditDialog(
            field = field,
            currentValue = when (field) {
                SetEditField.Weight -> formatDecimal(set.weightKg)
                SetEditField.Reps -> set.reps.toString()
                SetEditField.Seconds -> set.seconds.toString()
            },
            onDismiss = { editField = null },
            onApply = { value ->
                val updated = when (field) {
                    SetEditField.Weight -> {
                        val kg = value.toDoubleOrNull() ?: 0.0
                        set.copy(weightKg = kg, manualWeight = value.isNotBlank())
                    }
                    SetEditField.Reps -> set.copy(reps = value.toIntOrNull() ?: 0)
                    SetEditField.Seconds -> set.copy(seconds = value.toIntOrNull() ?: 0)
                }
                onUpdateSet(updated)
                if (field == SetEditField.Weight && updated.weightKg > 0.0 && updated.weightKg != set.weightKg) {
                    onPositiveWeightEdit(set, updated.weightKg)
                }
                editField = null
            }
        )
    }

    if (set.confirmed && !isExpanded) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        modifier = Modifier.size(36.dp),
                        checked = true,
                        onCheckedChange = { checked -> onUpdateSet(set.copy(confirmed = checked)) }
                    )
                    Text(
                        text = "${set.setIndex}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (showWeight) {
                        CompactChip(
                            text = "${formatWeight(set.weightKg)}kg",
                            onClick = { editField = SetEditField.Weight }
                        )
                    }
                    CompactChip(
                        text = if (set.seconds > 0 && !showWeight) {
                            if (isSportDurationInput) formatSportDurationLabel(set.seconds) else "${set.seconds}초"
                        } else {
                            "${set.reps}회"
                        },
                        onClick = {
                            if (isSportDurationInput) {
                                isExpanded = true
                            } else {
                                editField = if (set.seconds > 0 && !showWeight) {
                                    SetEditField.Seconds
                                } else {
                                    SetEditField.Reps
                                }
                            }
                        }
                    )
                    CompactChip(
                        text = set.rpe?.let { "RPE${formatRpe(it)}" } ?: "RPE-",
                        onClick = { showRpeDialog = true }
                    )
                    TextButton(
                        modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        onClick = { isExpanded = true }
                    ) {
                        Text("상세")
                    }
                }
                if (isTimerTarget && timerState.isActive) {
                    RestTimerSetChip(
                        state = timerState,
                        onStop = onStopRestTimer
                    )
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = if (set.confirmed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.width(24.dp),
                    text = set.setIndex.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                CompactNumberField(
                    modifier = Modifier.width(50.dp),
                    value = repsText,
                    suffix = "회",
                    keyboardType = KeyboardType.Number,
                    onValueChange = {
                        if (it.isUnsignedInt()) {
                            repsText = it
                            it.toIntOrNull()?.let { reps -> onUpdateSet(set.copy(reps = reps)) }
                        }
                    },
                    onFocusChanged = { focused ->
                        repsFocused = focused
                        repsText = if (focused) {
                            NumericInputTextPolicy.onFocus(repsText)
                        } else {
                            NumericInputTextPolicy.onBlur(repsText).also { restored ->
                                if (restored == "0") onUpdateSet(set.copy(reps = 0))
                            }
                        }
                    }
                )
                if (showWeight) {
                    CompactNumberField(
                        modifier = Modifier.width(62.dp),
                        value = weightText,
                        suffix = "kg",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = {
                            if (isDecimalInput(it)) {
                                weightText = it
                                it.toDoubleOrNull()?.let { kg ->
                                    onUpdateSet(
                                        set.copy(
                                            weightKg = kg,
                                            manualWeight = true
                                        )
                                    )
                                    if (kg > 0.0 && kg != set.weightKg) {
                                        onPositiveWeightEdit(set, kg)
                                    }
                                }
                            }
                        },
                        onFocusChanged = { focused ->
                            weightFocused = focused
                            weightText = if (focused) {
                                NumericInputTextPolicy.onFocus(weightText)
                            } else {
                                NumericInputTextPolicy.onBlur(weightText).also { restored ->
                                    if (restored == "0") {
                                        onUpdateSet(set.copy(weightKg = 0.0, manualWeight = false))
                                    }
                                }
                            }
                        }
                    )
                } else if (isSportDurationInput) {
                    CompactNumberField(
                        modifier = Modifier.width(62.dp),
                        value = sportHoursText,
                        suffix = "시간",
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            if (it.isUnsignedInt()) {
                                sportHoursText = it
                                applySportDuration(hoursText = it)
                            }
                        },
                        onFocusChanged = { focused ->
                            sportDurationFocused = focused
                            if (!focused) normalizeSportDurationText()
                        }
                    )
                    CompactNumberField(
                        modifier = Modifier.width(56.dp),
                        value = sportMinutesText,
                        suffix = "분",
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            if (it.isUnsignedInt()) {
                                sportMinutesText = it
                                applySportDuration(minutesText = it)
                            }
                        },
                        onFocusChanged = { focused ->
                            sportDurationFocused = focused
                            if (!focused) normalizeSportDurationText()
                        }
                    )
                } else {
                    CompactNumberField(
                        modifier = Modifier.width(56.dp),
                        value = secondsText,
                        suffix = "초",
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            if (it.isUnsignedInt()) {
                                secondsText = it
                                it.toIntOrNull()?.let { seconds -> onUpdateSet(set.copy(seconds = seconds)) }
                            }
                        },
                        onFocusChanged = { focused ->
                            secondsFocused = focused
                            secondsText = if (focused) {
                                NumericInputTextPolicy.onFocus(secondsText)
                            } else {
                                NumericInputTextPolicy.onBlur(secondsText).also { restored ->
                                    if (restored == "0") onUpdateSet(set.copy(seconds = 0))
                                }
                            }
                        }
                    )
                }
                CompactChip(
                    modifier = Modifier.width(58.dp),
                    text = set.rpe?.let { "RPE${formatRpe(it)}" } ?: "RPE-",
                    onClick = { showRpeDialog = true }
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Checkbox(
                        modifier = Modifier.size(40.dp),
                        checked = set.confirmed,
                        enabled = !isSportDurationInput || set.seconds > 0 || set.confirmed,
                        onCheckedChange = { checked ->
                            if (checked && isSportDurationInput && set.seconds <= 0) return@Checkbox
                            val updated = set.copy(confirmed = checked)
                            onUpdateSet(updated)
                            if (checked && !set.confirmed && effectiveRestSeconds > 0) {
                                onStartRestTimer(updated, effectiveRestSeconds)
                            }
                        }
                    )
                    Text(
                        text = if (set.confirmed) "완료" else "확인",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (isSportDurationInput && set.seconds <= 0) {
                Text(
                    text = "스포츠 기록은 0시간 0분으로 완료할 수 없습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showWeight) {
                    CompactNumberField(
                        modifier = Modifier.width(56.dp),
                        value = secondsText,
                        suffix = "초",
                        keyboardType = KeyboardType.Number,
                        onValueChange = {
                            if (it.isUnsignedInt()) {
                                secondsText = it
                                it.toIntOrNull()?.let { seconds -> onUpdateSet(set.copy(seconds = seconds)) }
                            }
                        },
                        onFocusChanged = { focused ->
                            secondsFocused = focused
                            secondsText = if (focused) {
                                NumericInputTextPolicy.onFocus(secondsText)
                            } else {
                                NumericInputTextPolicy.onBlur(secondsText).also { restored ->
                                    if (restored == "0") onUpdateSet(set.copy(seconds = 0))
                                }
                            }
                        }
                    )
                }
                CompactChip(
                    text = "휴${effectiveRestSeconds}",
                    onClick = { showRestDialog = true }
                )
                if (set.restSecondsOverride != null) {
                    Text(
                        text = "세트별",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    enabled = canDelete,
                    onClick = { onDeleteSet(set) }
                ) {
                    Text("삭제")
                }
                if (set.confirmed) {
                    TextButton(onClick = { isExpanded = false }) {
                        Text("접기")
                    }
                }
                if (isTimerTarget && timerState.isActive) {
                    RestTimerSetChip(
                        state = timerState,
                        onStop = onStopRestTimer
                    )
                }
            }
        }
    }
}

@Composable
private fun RestTimerSetChip(
    state: RestTimerState,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (state.isFinished) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (state.isFinished) "휴식 종료" else "휴식 ${formatTimerSeconds(state.remainingSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isFinished) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            Text(
                modifier = Modifier.clickable(onClick = onStop),
                text = "종료",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.isFinished) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}

@Composable
private fun CompactNumberField(
    modifier: Modifier,
    value: String,
    suffix: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    var wasFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (wasFocused != state.isFocused) {
                            wasFocused = state.isFocused
                            onFocusChanged(state.isFocused)
                        }
                    },
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            )
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RpeDialog(
    currentRpe: Double?,
    onDismiss: () -> Unit,
    onSelect: (Double?) -> Unit
) {
    var customText by rememberSaveable(currentRpe) {
        mutableStateOf(currentRpe?.let(::formatDecimal).orEmpty())
    }
    val customRpe = customText.toDoubleOrNull()?.coerceIn(0.0, 10.0)
    val options = listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("세트 RPE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(option) }
                            ) {
                                Text(formatRpe(option))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = customText,
                    onValueChange = { if (isDecimalInput(it)) customText = it },
                    label = { Text("직접 입력") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = customRpe != null,
                onClick = { onSelect(customRpe) }
            ) {
                Text("적용")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSelect(null) }) {
                    Text("비우기")
                }
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
            }
        }
    )
}

@Composable
private fun RestOverrideDialog(
    entryRestSeconds: Int,
    currentOverride: Int?,
    onDismiss: () -> Unit,
    onApply: (Int?) -> Unit
) {
    var restText by rememberSaveable(currentOverride) {
        mutableStateOf((currentOverride ?: entryRestSeconds).toString())
    }
    val rest = restText.toIntOrNull()?.coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("세트별 휴식") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "기본 휴식 ${entryRestSeconds}초",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 90, 120).forEach { option ->
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { restText = option.toString() }
                        ) {
                            Text("${option}초")
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = restText,
                    onValueChange = { if (it.isUnsignedInt()) restText = it },
                    label = { Text("휴식") },
                    suffix = { Text("초") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = rest != null,
                onClick = { onApply(rest) }
            ) {
                Text("적용")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(null) }) {
                    Text("기본값")
                }
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
            }
        }
    )
}

@Composable
private fun SetValueEditDialog(
    field: SetEditField,
    currentValue: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var text by rememberSaveable(field.name, currentValue) { mutableStateOf(currentValue) }
    val isWeight = field == SetEditField.Weight
    val isValid = if (isWeight) {
        text.isBlank() || text.toDoubleOrNull() != null
    } else {
        text.isUnsignedInt()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (field) {
                    SetEditField.Weight -> "kg 수정"
                    SetEditField.Reps -> "횟수 수정"
                    SetEditField.Seconds -> "시간 수정"
                }
            )
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        text = if (state.isFocused) {
                            NumericInputTextPolicy.onFocus(text)
                        } else {
                            NumericInputTextPolicy.onBlur(text)
                        }
                    },
                value = text,
                onValueChange = {
                    val allowed = if (isWeight) isDecimalInput(it) else it.isUnsignedInt()
                    if (allowed) text = it
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isWeight) KeyboardType.Decimal else KeyboardType.Number
                ),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = { onApply(text) }
            ) {
                Text("적용")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private enum class SetEditField {
    Weight,
    Reps,
    Seconds
}



private fun formatTimerSeconds(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return if (minutes > 0) {
        "%d:%02d".format(minutes, remainder)
    } else {
        "${remainder}초"
    }
}
