package com.training.trackplanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.RecordEntryOrdering
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import kotlinx.coroutines.delay

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
    val smashSpeeds by remember(selectedDate) {
        viewModel.smashSpeedsForDate(selectedDate)
    }.collectAsState(initial = emptyList())
    val sortedEntries = remember(entries) { entries.sortedForRecordDisplay() }
    val listState = rememberLazyListState()
    val exerciseMap = remember(exercises) { exercises.associateBy { exercise -> exercise.stableKey } }
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var calendarSearchQuery by rememberSaveable { mutableStateOf("") }
    var pendingSearchJump by remember { mutableStateOf<RecordSearchJumpRequest?>(null) }
    var highlightedEntryId by remember { mutableStateOf<Long?>(null) }
    var pendingAddedEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingAddedAfterConfirmed by rememberSaveable { mutableStateOf(false) }
    val showPermissionHint = timerState.isActive &&
        (timerState.notificationPermissionNeeded || !timerState.overlayPermissionGranted)

    LaunchedEffect(pendingAddedEntryId, sortedEntries, showPermissionHint) {
        val entryId = pendingAddedEntryId ?: return@LaunchedEffect
        val entryIndex = sortedEntries.indexOfFirst { it.entry.id == entryId }
        if (entryIndex < 0) return@LaunchedEffect
        if (pendingAddedAfterConfirmed) {
            val leadingItems = 4 + if (showPermissionHint) 1 else 0
            listState.animateScrollToItem((leadingItems + entryIndex - 1).coerceAtLeast(0))
        } else {
            listState.animateScrollToItem(0)
        }
        pendingAddedEntryId = null
    }

    LaunchedEffect(target) {
        target?.let {
            pendingSearchJump = null
            selectedDate = it.recordDate
        }
    }

    LaunchedEffect(pendingSearchJump, selectedDate, sortedEntries, exerciseMap, showPermissionHint) {
        val request = pendingSearchJump ?: return@LaunchedEffect
        val entryId = RecordSearchNavigation.firstConfirmedMatch(
            request = request,
            selectedDate = selectedDate,
            records = sortedEntries,
            currentExerciseNames = exerciseMap.mapValues { (_, exercise) -> exercise.name }
        ) ?: return@LaunchedEffect
        pendingSearchJump = null
        val entryIndex = sortedEntries.indexOfFirst { record -> record.entry.id == entryId }
        val leadingItems = 4 + if (showPermissionHint) 1 else 0
        listState.animateScrollToItem(leadingItems + entryIndex)
        highlightedEntryId = entryId
        delay(1_200L)
        if (highlightedEntryId == entryId) highlightedEntryId = null
    }

    if (showCalendar) {
        BackHandler {
            showCalendar = false
        }
        RecordCalendarScreen(
            viewModel = viewModel,
            selectedDate = selectedDate,
            exerciseSearchQuery = calendarSearchQuery,
            onExerciseSearchQueryChange = { calendarSearchQuery = it },
            onDateSelected = { date, tappedMatchingResult ->
                pendingSearchJump = RecordSearchNavigation.request(
                    date = date,
                    query = calendarSearchQuery,
                    tappedMatchingResult = tappedMatchingResult
                )
                selectedDate = date
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
                val hadConfirmedSet = entries.any { record -> record.sets.any(WorkoutSet::confirmed) }
                viewModel.addWorkout(selectedDate, exercise.stableKey) { addedEntryId ->
                    pendingAddedAfterConfirmed = hadConfirmedSet
                    pendingAddedEntryId = addedEntryId
                }
                showExercisePicker = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = "기록",
                body = "세트 입력과 확인을 우선합니다. 확인한 세트만 실제 수행 기록입니다."
            )
        }
        if (showPermissionHint) {
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
                onPrevious = {
                    pendingSearchJump = null
                    selectedDate = date.minusDays(1).toString()
                },
                onNext = {
                    pendingSearchJump = null
                    selectedDate = date.plusDays(1).toString()
                },
                onOpenCalendar = { showCalendar = true }
            )
        }
        item {
            RecordDailyConditionCard(
                date = selectedDate,
                viewModel = viewModel,
                onAddExercise = { showExercisePicker = true }
            )
        }
        item {
            SmashSpeedCard(
                date = selectedDate,
                records = smashSpeeds,
                onAdd = { speed -> viewModel.addSmashSpeed(selectedDate, speed) },
                onDelete = viewModel::deleteSmashSpeed
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
                    exercise = exerciseMap[entryWithSets.entry.exerciseStableKey],
                    highlighted = highlightedEntryId == entryWithSets.entry.id,
                    restTimerSessionController = restTimerSessionController,
                    timerState = timerState,
                    onUpdateEntry = viewModel::updateWorkoutEntry,
                    onAddSet = { viewModel.addSet(entryWithSets.entry) },
                    onUpdateSet = viewModel::updateSet,
                    onDeleteSet = viewModel::deleteSet,
                    onDeleteEntry = { viewModel.deleteWorkoutEntry(entryWithSets.entry) },
                    onStopRestTimer = restTimerSessionController::stop
                )
            }
        }
    }
}


@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecordDailyConditionCard(
    date: String,
    viewModel: TrainingViewModel,
    onAddExercise: () -> Unit
) {
    val checkIn by remember(date) {
        viewModel.checkInForDate(date)
    }.collectAsState(initial = null)
    var showEditor by rememberSaveable(date) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { showEditor = true }
                ) {
                    Text(
                        checkIn?.compactSummary()?.let { summary -> "컨디션 · $summary" }
                            ?: "컨디션 입력",
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Button(
                    onClick = onAddExercise
                ) {
                    Text("운동 추가", maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (showEditor) {
        DailyConditionEditorDialog(
            targetDate = LocalDate.parse(date),
            checkIn = checkIn,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                viewModel.saveDailyCheckIn(saved)
                showEditor = false
            }
        )
    }
}


private fun List<WorkoutEntryWithSets>.sortedForRecordDisplay(): List<WorkoutEntryWithSets> =
    RecordEntryOrdering.ordered(this)
