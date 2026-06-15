package com.training.trackplanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate

@Composable
internal fun RecordScreen(
    viewModel: TrainingViewModel,
    restTimerSessionController: RestTimerSessionController,
    target: RestTimerTarget?,
    onOpenPlan: () -> Unit
) {
    val exercises by viewModel.exercises.collectAsState()
    val timerState by restTimerSessionController.state.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        restTimerSessionController.refreshPermissions()
    }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = remember(selectedDate) { LocalDate.parse(selectedDate) }
    val entries by remember(selectedDate) {
        viewModel.entriesForDate(selectedDate)
    }.collectAsState(initial = emptyList())
    val sortedEntries = remember(entries) { entries.sortedForRecordDisplay() }
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(target) {
        target?.let { selectedDate = it.recordDate }
    }

    if (showCalendar) {
        BackHandler {
            showCalendar = false
        }
        RecordCalendarScreen(
            viewModel = viewModel,
            selectedDate = selectedDate,
            onDateSelected = {
                selectedDate = it
                showCalendar = false
            },
            onBack = { showCalendar = false }
        )
        return
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            exercises = exercises.filter { exercise -> exercise.isActive },
            onDismiss = { showExercisePicker = false },
            onSelect = { exercise ->
                viewModel.addWorkout(selectedDate, exercise.id)
                showExercisePicker = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = "기록",
                body = "세트 입력과 확인을 우선합니다. 확인한 세트만 실제 수행 기록입니다."
            )
        }
        if (timerState.isActive &&
            (timerState.notificationPermissionNeeded || !timerState.overlayPermissionGranted)
        ) {
            item {
                RestTimerPermissionHint(
                    notificationPermissionNeeded = timerState.notificationPermissionNeeded,
                    overlayPermissionGranted = timerState.overlayPermissionGranted,
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenOverlaySettings = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }
        item {
            RecordDateSwitcher(
                date = date,
                onPrevious = { selectedDate = date.minusDays(1).toString() },
                onNext = { selectedDate = date.plusDays(1).toString() },
                onOpenCalendar = { showCalendar = true }
            )
        }
        item {
            DailyMetricCard(
                date = selectedDate,
                viewModel = viewModel,
                onAddExercise = { showExercisePicker = true }
            )
        }
        if (entries.isEmpty()) {
            item {
                EmptyRecordState(
                    onAddExercise = { showExercisePicker = true },
                    onOpenPlan = onOpenPlan
                )
            }
        } else {
            items(sortedEntries, key = { it.entry.id }) { entryWithSets ->
                WorkoutEntryCard(
                    selectedDate = selectedDate,
                    entryWithSets = entryWithSets,
                    restTimerSessionController = restTimerSessionController,
                    timerState = timerState,
                    onUpdateEntry = viewModel::updateWorkoutEntry,
                    onAddSet = { viewModel.addSet(entryWithSets.entry) },
                    onUpdateSet = viewModel::updateSet,
                    onDeleteSet = viewModel::deleteSet,
                    onStopRestTimer = restTimerSessionController::stop
                )
            }
        }
    }
}

@Composable
private fun DailyMetricCard(
    date: String,
    viewModel: TrainingViewModel,
    onAddExercise: () -> Unit
) {
    val metric by remember(date) {
        viewModel.metricForDate(date)
    }.collectAsState(initial = null)
    var expanded by rememberSaveable(date) { mutableStateOf(false) }
    var sleepText by rememberSaveable(date) { mutableStateOf("") }
    var bodyWeightText by rememberSaveable(date) { mutableStateOf("") }

    LaunchedEffect(metric) {
        sleepText = metric?.sleepHours?.let(::formatDecimal).orEmpty()
        bodyWeightText = metric?.bodyWeightKg?.let(::formatDecimal).orEmpty()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { expanded = !expanded }
                ) {
                    Text(conditionSummary(metric?.sleepHours, metric?.bodyWeightKg))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onAddExercise
                ) {
                    Text("운동 추가")
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = sleepText,
                        onValueChange = { if (isDecimalInput(it)) sleepText = it },
                        label = { Text("수면") },
                        suffix = { Text("h") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = bodyWeightText,
                        onValueChange = { if (isDecimalInput(it)) bodyWeightText = it },
                        label = { Text("체중") },
                        suffix = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.saveDailyMetric(
                            date = date,
                            sleepHours = sleepText.toDoubleOrNull(),
                            bodyWeightKg = bodyWeightText.toDoubleOrNull()
                        )
                        expanded = false
                    }
                ) {
                    Text("저장")
                }
            }
        }
    }
}

@Composable
private fun RecordDateSwitcher(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        val dateButtonModifier = Modifier.defaultMinSize(minWidth = 0.dp)
        val dateButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onPrevious,
                modifier = dateButtonModifier,
                contentPadding = dateButtonPadding
            ) {
                Text("이전날")
            }
            Text(
                modifier = Modifier.weight(1f),
                text = date.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            OutlinedButton(
                onClick = onNext,
                modifier = dateButtonModifier,
                contentPadding = dateButtonPadding
            ) {
                Text("다음날")
            }
            OutlinedButton(
                onClick = onOpenCalendar,
                modifier = dateButtonModifier,
                contentPadding = dateButtonPadding
            ) {
                Text("달력")
            }
        }
    }
}

@Composable
private fun EmptyRecordState(
    onAddExercise: () -> Unit,
    onOpenPlan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "이 날짜에는 아직 운동이 없습니다.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddExercise
            ) {
                Text("운동 추가")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenPlan
            ) {
                Text("프로그램에서 가져오기")
            }
        }
    }
}

@Composable
private fun WorkoutEntryCard(
    selectedDate: String,
    entryWithSets: WorkoutEntryWithSets,
    restTimerSessionController: RestTimerSessionController,
    timerState: RestTimerState,
    onUpdateEntry: (WorkoutEntry) -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (WorkoutSet) -> Unit,
    onDeleteSet: (WorkoutSet) -> Unit,
    onStopRestTimer: () -> Unit
) {
    val entry = entryWithSets.entry
    val sets = entryWithSets.sets.sortedBy { it.setIndex }
    val showWeight = shouldShowWeight(entry)
    var showBulkEditDialog by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showDetails by rememberSaveable(entry.id) { mutableStateOf(false) }
    var pendingWeightSuggestion by remember { mutableStateOf<WeightSuggestion?>(null) }

    if (showBulkEditDialog) {
        BulkEditDialog(
            entry = entry,
            sets = sets,
            showWeight = showWeight,
            onDismiss = { showBulkEditDialog = false },
            onUpdateSet = onUpdateSet
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${planSummary(sets, showWeight)} · 휴식 ${formatSeconds(entry.restSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "접기" else "상세")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip("${sets.count { it.confirmed }}/${sets.size} 완료")
                if (entry.notes.isNotBlank()) StatusChip("메모 있음")
                entry.maxReps?.let { StatusChip("최대 ${it}회") }
                entry.rpe?.let { StatusChip("전체 RPE ${formatRpe(it)}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onAddSet
                ) {
                    Text("세트 +")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showBulkEditDialog = true }
                ) {
                    Text("일괄")
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
                    canDelete = sets.size > 1,
                    onUpdateSet = onUpdateSet,
                    onDeleteSet = onDeleteSet,
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
private fun WorkoutSetRow(
    entry: WorkoutEntry,
    set: WorkoutSet,
    sets: List<WorkoutSet>,
    showWeight: Boolean,
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
    var showRpeDialog by rememberSaveable(set.id) { mutableStateOf(false) }
    var showRestDialog by rememberSaveable(set.id) { mutableStateOf(false) }
    val effectiveRestSeconds = set.restSecondsOverride ?: entry.restSeconds
    val isTimerTarget = timerState.targetEntryId == entry.id && timerState.targetSetId == set.id

    LaunchedEffect(set) {
        repsText = set.reps.toString()
        weightText = formatDecimal(set.weightKg)
        secondsText = set.seconds.toString()
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
                            onUpdateSet(set.copy(reps = it.toIntOrNull() ?: 0))
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
                                val kg = it.toDoubleOrNull() ?: 0.0
                                onUpdateSet(
                                    set.copy(
                                        weightKg = kg,
                                        manualWeight = it.isNotBlank()
                                    )
                                )
                                if (kg > 0.0 && kg != set.weightKg) {
                                    onPositiveWeightEdit(set, kg)
                                }
                            }
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
                                onUpdateSet(set.copy(seconds = it.toIntOrNull() ?: 0))
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
                        onCheckedChange = { checked ->
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
                                onUpdateSet(set.copy(seconds = it.toIntOrNull() ?: 0))
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
    onValueChange: (String) -> Unit
) {
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
                modifier = Modifier.weight(1f),
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
private fun BulkEditDialog(
    entry: WorkoutEntry,
    sets: List<WorkoutSet>,
    showWeight: Boolean,
    onDismiss: () -> Unit,
    onUpdateSet: (WorkoutSet) -> Unit
) {
    var operation by remember { mutableStateOf<BulkOperation?>(null) }
    var valueText by rememberSaveable(operation?.label.orEmpty()) { mutableStateOf("") }
    var includeConfirmed by rememberSaveable { mutableStateOf(false) }
    val value = valueText.toDoubleOrNull()
    val hasConfirmed = sets.any { it.confirmed }
    val lastPositiveKg = sets.firstOrNull { it.weightKg > 0.0 }?.weightKg
    val operations = buildList {
        if (showWeight) {
            add(BulkOperation("kg 설정", BulkField.Weight, BulkMode.Set))
            add(BulkOperation("kg +", BulkField.Weight, BulkMode.Increase))
            add(BulkOperation("kg -", BulkField.Weight, BulkMode.Decrease))
        }
        add(BulkOperation("횟수 +", BulkField.Reps, BulkMode.Increase))
        add(BulkOperation("횟수 -", BulkField.Reps, BulkMode.Decrease))
        add(BulkOperation("휴식 설정", BulkField.Rest, BulkMode.Set))
        add(BulkOperation("휴식 +", BulkField.Rest, BulkMode.Increase))
        add(BulkOperation("휴식 -", BulkField.Rest, BulkMode.Decrease))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일괄 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                operation?.let { selected ->
                    Text(selected.label)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = valueText,
                        onValueChange = {
                            val allowed = when (selected.field) {
                                BulkField.Weight -> isDecimalInput(it)
                                BulkField.Reps, BulkField.Rest -> it.isUnsignedInt()
                            }
                            if (allowed) valueText = it
                        },
                        label = {
                            Text(
                                when (selected.field) {
                                    BulkField.Weight -> "kg"
                                    BulkField.Reps -> "횟수"
                                    BulkField.Rest -> "초"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (selected.field == BulkField.Weight) {
                                KeyboardType.Decimal
                            } else {
                                KeyboardType.Number
                            }
                        ),
                        singleLine = true
                    )
                    QuickValueRow(selected.field) { valueText = it }
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
            val selected = operation
            Button(
                enabled = selected != null && value != null,
                onClick = {
                    if (selected != null && value != null) {
                        sets.filter { includeConfirmed || !it.confirmed }
                            .forEach { set -> onUpdateSet(applyBulkOperation(entry, set, selected, value)) }
                    }
                    onDismiss()
                }
            ) {
                Text(if (includeConfirmed) "완료 포함 적용" else "적용")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (operation == null) onDismiss() else operation = null
                }
            ) {
                Text(if (operation == null) "닫기" else "뒤로")
            }
        }
    )
}

@Composable
private fun QuickValueRow(field: BulkField, onSelect: (String) -> Unit) {
    val values = when (field) {
        BulkField.Weight -> listOf("2.5", "5")
        BulkField.Reps -> listOf("1", "2")
        BulkField.Rest -> listOf("15", "30", "60")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onSelect(value) }
            ) {
                Text(value)
            }
        }
    }
}

private enum class BulkField {
    Weight,
    Reps,
    Rest
}

private enum class BulkMode {
    Set,
    Increase,
    Decrease
}

private data class BulkOperation(
    val label: String,
    val field: BulkField,
    val mode: BulkMode
)

private fun defaultBulkValue(operation: BulkOperation): String =
    when (operation.field) {
        BulkField.Weight -> if (operation.mode == BulkMode.Set) "" else "2.5"
        BulkField.Reps -> "1"
        BulkField.Rest -> if (operation.mode == BulkMode.Set) "60" else "30"
    }

private fun applyBulkOperation(
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
            }.coerceAtLeast(0.0)
            set.copy(weightKg = newWeight, manualWeight = true)
        }
        BulkField.Reps -> {
            val delta = rawValue.toInt()
            val newReps = when (operation.mode) {
                BulkMode.Set -> delta
                BulkMode.Increase -> set.reps + delta
                BulkMode.Decrease -> set.reps - delta
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
            }.coerceAtLeast(0)
            set.copy(restSecondsOverride = nextRest)
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

private fun conditionSummary(sleepHours: Double?, bodyWeightKg: Double?): String =
    if (sleepHours == null && bodyWeightKg == null) {
        "컨디션"
    } else {
        buildList {
            sleepHours?.let { add("수면 ${formatDecimal(it)}h") }
            bodyWeightKg?.let { add("${formatDecimal(it)}kg") }
        }.joinToString(" · ", prefix = "컨디션 · ")
    }

private fun shouldShowWeight(entry: WorkoutEntry): Boolean =
    entry.category != "스포츠" && entry.category != "유산소운동"

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

private fun formatRpe(value: Double): String =
    formatDecimal(value.coerceIn(0.0, 10.0))

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

private fun List<WorkoutEntryWithSets>.sortedForRecordDisplay(): List<WorkoutEntryWithSets> =
    sortedWith(
        compareBy<WorkoutEntryWithSets> {
            when {
                it.sets.isNotEmpty() && it.sets.all { set -> set.confirmed } -> 0
                it.sets.any { set -> set.confirmed } -> 1
                else -> 2
            }
        }.thenBy { it.entry.createdAt }
            .thenBy { it.entry.id }
    )
