package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.ActivityKind
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import com.training.trackplanner.data.resolvedActivityKind


@Composable
internal fun WorkoutEntryCard(
    selectedDate: String,
    entryWithSets: WorkoutEntryWithSets,
    exercise: Exercise?,
    restTimerSessionController: RestTimerSessionController,
    timerState: RestTimerState,
    onUpdateEntry: (WorkoutEntry) -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (WorkoutSet) -> Unit,
    onDeleteSet: (WorkoutSet) -> Unit,
    onDeleteEntry: () -> Unit,
    onStopRestTimer: () -> Unit
) {
    val entry = entryWithSets.entry
    val sets = entryWithSets.sets.sortedBy { it.setIndex }
    val showWeight = shouldShowWeight(entry)
    val isSportDurationInput = shouldUseSportDurationInput(entry, exercise, showWeight)
    val hasConfirmedSet = sets.any { set -> set.confirmed }
    var showBulkEditDialog by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showDetails by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showDeleteEntryDialog by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showExerciseInfo by rememberSaveable(entry.id) { mutableStateOf(false) }
    var pendingWeightSuggestion by remember { mutableStateOf<WeightSuggestion?>(null) }

    if (showExerciseInfo && exercise != null) {
        ExerciseInfoDialog(
            exercise = exercise,
            onDismiss = { showExerciseInfo = false }
        )
    }

    if (showBulkEditDialog) {
        BulkEditDialog(
            entry = entry,
            sets = sets,
            showWeight = showWeight,
            onDismiss = { showBulkEditDialog = false },
            onUpdateSet = onUpdateSet
        )
    }

    if (showDeleteEntryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteEntryDialog = false },
            title = { Text("운동 삭제") },
            text = { Text("${entry.exerciseName}을(를) 선택한 날짜 기록에서 삭제할까요? 세트 기록도 함께 삭제됩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteEntryDialog = false
                        onStopRestTimer()
                        onDeleteEntry()
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEntryDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = entry.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                TextButton(
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    onClick = {
                        if (hasConfirmedSet) {
                            showDeleteEntryDialog = true
                        } else {
                            onStopRestTimer()
                            onDeleteEntry()
                        }
                    }
                ) {
                    Text("운동 삭제")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (exercise != null) {
                    TextButton(
                        modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        onClick = { showExerciseInfo = true }
                    ) {
                        Text("i")
                    }
                }
                TextButton(
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    onClick = onAddSet
                ) {
                    Text("세트+")
                }
                TextButton(
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    onClick = { showBulkEditDialog = true }
                ) {
                    Text("일괄")
                }
                TextButton(
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    onClick = { showDetails = !showDetails }
                ) {
                    Text(if (showDetails) "접기" else "상세")
                }
            }
            pendingWeightSuggestion?.let { suggestion ->
                WeightSuggestionCard(
                    suggestion = suggestion,
                    onApply = {
                        sets.filter { set ->
                            set.id != suggestion.sourceSetId &&
                                set.weightKg == 0.0 &&
                                !set.manualWeight &&
                                !set.confirmed
                        }.forEach { set ->
                            onUpdateSet(set.copy(weightKg = suggestion.kg, manualWeight = true))
                        }
                        pendingWeightSuggestion = null
                    },
                    onDismiss = { pendingWeightSuggestion = null }
                )
            }
            HorizontalDivider()
            sets.forEach { set ->
                WorkoutSetRow(
                    entry = entry,
                    set = set,
                    sets = sets,
                    showWeight = showWeight,
                    isSportDurationInput = isSportDurationInput,
                    canDelete = true,
                    onUpdateSet = onUpdateSet,
                    onDeleteSet = { targetSet ->
                        if (sets.size <= 1) {
                            onStopRestTimer()
                            onDeleteEntry()
                        } else {
                            onDeleteSet(targetSet)
                        }
                    },
                    timerState = timerState,
                    onStopRestTimer = onStopRestTimer,
                    onPositiveWeightEdit = { sourceSet, kg ->
                        val hasEmptyTargets = sets.any { candidate ->
                            candidate.id != sourceSet.id &&
                                candidate.weightKg == 0.0 &&
                                !candidate.manualWeight &&
                                !candidate.confirmed
                        }
                        if (hasEmptyTargets) {
                            pendingWeightSuggestion = WeightSuggestion(
                                sourceSetId = sourceSet.id,
                                kg = kg
                            )
                        }
                    },
                    onStartRestTimer = { confirmedSet, effectiveRestSeconds ->
                        restTimerSessionController.start(
                            durationSeconds = effectiveRestSeconds,
                            exerciseName = entry.exerciseName,
                            nextHint = nextRestHint(entry, sets, confirmedSet),
                            targetRecordDate = selectedDate,
                            targetEntryId = entry.id,
                            targetSetId = confirmedSet.id
                        )
                    }
                )
            }
            if (showDetails) {
                HorizontalDivider()
                EntryMetaEditor(
                    entry = entry,
                    onUpdateEntry = onUpdateEntry
                )
            }
        }
    }
}

@Composable
private fun EntryMetaEditor(
    entry: WorkoutEntry,
    onUpdateEntry: (WorkoutEntry) -> Unit
) {
    val isSport = entry.category == "스포츠"
    var restText by rememberSaveable(entry.id) { mutableStateOf(entry.restSeconds.toString()) }
    var rpeText by rememberSaveable(entry.id) { mutableStateOf(entry.rpe?.let(::formatDecimal).orEmpty()) }
    var maxRepsText by rememberSaveable(entry.id) { mutableStateOf(entry.maxReps?.toString().orEmpty()) }
    var notesText by rememberSaveable(entry.id) { mutableStateOf(entry.notes) }

    LaunchedEffect(entry) {
        restText = entry.restSeconds.toString()
        rpeText = entry.rpe?.let(::formatDecimal).orEmpty()
        maxRepsText = entry.maxReps?.toString().orEmpty()
        notesText = entry.notes
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "상세 입력",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = restText,
                onValueChange = {
                    if (it.isUnsignedInt()) {
                        restText = it
                        onUpdateEntry(entry.copy(restSeconds = it.toIntOrNull() ?: 0))
                    }
                },
                label = { Text("기본 휴식") },
                suffix = { Text("초") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            if (!isSport) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = rpeText,
                    onValueChange = {
                        if (isDecimalInput(it)) {
                            rpeText = it
                            onUpdateEntry(entry.copy(rpe = it.toDoubleOrNull()?.coerceIn(0.0, 10.0)))
                        }
                    },
                    label = { Text("전체 RPE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        }
        if (!isSport) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = maxRepsText,
                onValueChange = {
                    if (it.isUnsignedInt()) {
                        maxRepsText = it
                        onUpdateEntry(entry.copy(maxReps = it.toIntOrNull()))
                    }
                },
                label = { Text("최대 횟수") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = notesText,
            onValueChange = {
                notesText = it
                onUpdateEntry(entry.copy(notes = it))
            },
            label = { Text("운동 메모") },
            minLines = 1
        )
    }
}



@Composable
private fun WeightSuggestionCard(
    suggestion: WeightSuggestion,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "빈 세트에도 ${formatWeight(suggestion.kg)}kg 적용?",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onApply
                ) {
                    Text("적용")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                ) {
                    Text("안 함")
                }
            }
        }
    }
}

private data class WeightSuggestion(
    val sourceSetId: Long,
    val kg: Double
)

private fun shouldShowWeight(entry: WorkoutEntry): Boolean =
    entry.category != "스포츠" && entry.category != "유산소운동"

private fun shouldUseSportDurationInput(
    entry: WorkoutEntry,
    exercise: Exercise?,
    showWeight: Boolean
): Boolean {
    if (showWeight) return false
    if (exercise?.resolvedActivityKind() == ActivityKind.SPORT_SESSION) return true
    return entry.category == "스포츠" || entry.category.equals("sport", ignoreCase = true)
}

private fun planSummary(sets: List<WorkoutSet>, showWeight: Boolean): String {
    if (sets.isEmpty()) return "계획 0세트"
    val reps = sets.map { it.reps }.distinct().singleOrNull()
    val weight = sets.map { it.weightKg }.distinct().singleOrNull()
    val seconds = sets.map { it.seconds }.distinct().singleOrNull()
    return buildList {
        add("계획 ${sets.size}세트")
        reps?.takeIf { it > 0 }?.let { add("${it}회") }
        if (showWeight) {
            weight?.takeIf { it > 0.0 }?.let { add("${formatWeight(it)}kg") }
        }
        seconds?.takeIf { it > 0 }?.let { add("${it}초") }
    }.joinToString(" · ")
}

private fun nextRestHint(
    entry: WorkoutEntry,
    sets: List<WorkoutSet>,
    currentSet: WorkoutSet
): String {
    val nextSet = sets.firstOrNull {
        it.setIndex > currentSet.setIndex && !it.confirmed
    }
    return if (nextSet != null) {
        "${entry.exerciseName} ${nextSet.setIndex}세트 준비"
    } else {
        ""
    }
}
