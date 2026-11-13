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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.training.trackplanner.health.WatchWorkoutImportDialog
import com.training.trackplanner.health.WatchSleepConditionEntry
import com.training.trackplanner.health.WatchSleepLookupPolicy
import com.training.trackplanner.data.RecordEntryOrdering
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import com.training.trackplanner.data.isAnalysisEligibleCompletedSet
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

internal enum class RecordDetailOrigin { Normal, Calendar }

@Composable
internal fun RecordScreen(
    viewModel: TrainingViewModel,
    restTimerSessionController: RestTimerSessionController,
    target: RestTimerTarget?,
    onOpenPlan: () -> Unit,
    showOnboardingWorkoutTarget: Boolean = false,
    onWorkoutTargetPositioned: (Rect) -> Unit = {}
) {
    val exercises by viewModel.exercises.collectAsState()
    val muscleMetadataByKey by viewModel.exerciseMuscleMetadata.collectAsState()
    val runtimeMetadataByKey by viewModel.exerciseRuntimeMetadata.collectAsState()
    val programLinks by viewModel.programWorkoutLinks.collectAsState(emptyList())
    val progressionSuggestions by viewModel.progressionSuggestions.collectAsState(emptyList())
    val bCoachPostConfirmOutcomes by viewModel.bCoachPostConfirmOutcomes.collectAsState()
    val programPrescriptions by viewModel.programPrescriptions.collectAsState(emptyList())
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
    val sortedEntryIds = remember(sortedEntries) { sortedEntries.map { it.entry.id } }
    val visibleEntryIdSet = remember(sortedEntryIds) { sortedEntryIds.toHashSet() }
    val programLinkByEntryId = remember(programLinks) { programLinks.associateBy { it.entryId } }
    val pendingSuggestionByEntryId = remember(progressionSuggestions) {
        progressionSuggestions
            .filter { it.resolution == com.training.trackplanner.data.ProgressionResolution.PENDING }
            .associateBy { it.targetEntryId }
    }
    val prescriptionsByEntryId = remember(programPrescriptions) {
        programPrescriptions.groupBy { it.entryId }
    }
    // Retain existing progression refresh authority: only row-level presentation is cached.
    LaunchedEffect(entries, selectedDate) { viewModel.refreshProgression() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val exerciseMap = remember(exercises) { exercises.associateBy { exercise -> exercise.stableKey } }
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    var watchImportEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var watchImportAutomatic by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedDate) { watchImportEntryId = null }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var detailOrigin by rememberSaveable { mutableStateOf(RecordDetailOrigin.Normal) }
    var calendarSearchQuery by rememberSaveable { mutableStateOf("") }
    var pendingSearchJump by remember { mutableStateOf<RecordSearchJumpRequest?>(null) }
    var pendingTimerTarget by remember { mutableStateOf<RestTimerTarget?>(null) }
    var visibleTimerTarget by remember { mutableStateOf<RestTimerTarget?>(null) }
    var highlightedEntryId by remember { mutableStateOf<Long?>(null) }
    var draggedEntryId by remember(selectedDate) { mutableStateOf<Long?>(null) }
    var dragTargetEntryId by remember(selectedDate) { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember(selectedDate) { mutableFloatStateOf(0f) }
    var pendingAddedEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingAddedAfterConfirmed by rememberSaveable { mutableStateOf(false) }
    val showPermissionHint = timerState.isActive &&
        (timerState.notificationPermissionNeeded || timerState.exactAlarmPermissionNeeded || !timerState.overlayPermissionGranted)

    LaunchedEffect(showOnboardingWorkoutTarget, sortedEntries, showPermissionHint) {
        if (showOnboardingWorkoutTarget && sortedEntries.isNotEmpty()) {
            val leadingItems = 4 + if (showPermissionHint) 1 else 0
            listState.animateScrollToItem(leadingItems)
        }
    }

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
            detailOrigin = RecordDetailOrigin.Normal
            pendingSearchJump = null
            pendingTimerTarget = it
            selectedDate = it.recordDate
        }
    }

    BackHandler(enabled = !showCalendar && detailOrigin == RecordDetailOrigin.Calendar) {
        showCalendar = true
    }

    LaunchedEffect(pendingTimerTarget, selectedDate, sortedEntries, showPermissionHint) {
        val timerTarget = pendingTimerTarget ?: return@LaunchedEffect
        if (timerTarget.recordDate != selectedDate) return@LaunchedEffect
        val entryIndex = sortedEntries.indexOfFirst { record -> record.entry.id == timerTarget.entryId }
        if (entryIndex < 0) return@LaunchedEffect
        val leadingItems = 4 + if (showPermissionHint) 1 else 0
        listState.animateScrollToItem(leadingItems + entryIndex)
        visibleTimerTarget = timerTarget
        highlightedEntryId = timerTarget.entryId
        delay(1_200L)
        if (highlightedEntryId == timerTarget.entryId) highlightedEntryId = null
        pendingTimerTarget = null
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
                detailOrigin = RecordDetailOrigin.Calendar
                showCalendar = false
            },
            onBack = {
                detailOrigin = RecordDetailOrigin.Normal
                showCalendar = false
            }
        )
        return
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            exercises = exercises.filter { exercise -> exercise.isActive },
            muscleMetadataByKey = muscleMetadataByKey,
            runtimeMetadataByKey = runtimeMetadataByKey,
            onDismiss = { showExercisePicker = false },
            onSelect = { exercise ->
                val hadConfirmedSet = entries.any { record -> record.sets.any(WorkoutSet::isAnalysisEligibleCompletedSet) }
                viewModel.addWorkout(selectedDate, exercise.stableKey) { addedEntryId ->
                    pendingAddedAfterConfirmed = hadConfirmedSet
                    pendingAddedEntryId = addedEntryId
                    if (exercise.category == "스포츠" || exercise.category == "유산소운동") {
                        watchImportAutomatic = true
                        watchImportEntryId = addedEntryId
                    }
                }
                showExercisePicker = false
            }
        )
    }

    sortedEntries.firstOrNull { it.entry.id == watchImportEntryId }?.let { watchTarget ->
        WatchWorkoutImportDialog(
            target = watchTarget,
            date = selectedDate,
            automatic = watchImportAutomatic,
            onDismiss = { watchImportEntryId = null },
            onApply = viewModel::applyWatchDurationIfUnchanged
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
                    exactAlarmPermissionNeeded = timerState.exactAlarmPermissionNeeded,
                    overlayPermissionGranted = timerState.overlayPermissionGranted,
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}")
                            )
                            runCatching { context.startActivity(intent) }
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
            items(sortedEntries, key = { it.entry.id }, contentType = { "workout-entry" }) { entryWithSets ->
                val entryId = entryWithSets.entry.id
                val isDragging = draggedEntryId == entryId
                val dragModifier = Modifier.pointerInput(entryId, selectedDate, sortedEntryIds) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggedEntryId = entryId
                            dragTargetEntryId = entryId
                            dragOffsetY = 0f
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = {
                            draggedEntryId = null
                            dragTargetEntryId = null
                            dragOffsetY = 0f
                        },
                        onDragEnd = {
                            val currentIds = sortedEntryIds
                            val reorderedIds = RecordExerciseDragPolicy.reorderedEntryIds(
                                orderedEntryIds = currentIds,
                                movingEntryId = entryId,
                                targetEntryId = dragTargetEntryId
                            )
                            if (reorderedIds != currentIds) {
                                viewModel.reorderWorkoutEntries(selectedDate, reorderedIds)
                            }
                            draggedEntryId = null
                            dragTargetEntryId = null
                            dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            val draggedItem = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { item -> item.key == entryId }
                            if (draggedItem != null) {
                                val center = draggedItem.offset + (draggedItem.size / 2f) + dragOffsetY
                                val visibleEntries = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                                    val visibleEntryId = item.key as? Long
                                    if (visibleEntryId != null && visibleEntryId in visibleEntryIdSet) {
                                        VisibleRecordEntry(
                                            entryId = visibleEntryId,
                                            top = item.offset,
                                            bottom = item.offset + item.size
                                        )
                                    } else {
                                        null
                                    }
                                }
                                dragTargetEntryId = RecordExerciseDragPolicy.targetEntryId(visibleEntries, center)
                                val edge = 72.dp.toPx()
                                val scrollDelta = when {
                                    center < listState.layoutInfo.viewportStartOffset + edge -> -20f
                                    center > listState.layoutInfo.viewportEndOffset - edge -> 20f
                                    else -> 0f
                                }
                                if (scrollDelta != 0f) {
                                    coroutineScope.launch { listState.scrollBy(scrollDelta) }
                                }
                            }
                        }
                    )
                }
                WorkoutEntryCard(
                    selectedDate = selectedDate,
                    entryWithSets = entryWithSets,
                    exercise = exerciseMap[entryWithSets.entry.exerciseStableKey],
                    muscleMetadata = muscleMetadataByKey[entryWithSets.entry.exerciseStableKey],
                    highlighted = highlightedEntryId == entryId ||
                        (draggedEntryId != null && dragTargetEntryId == entryId && !isDragging),
                    cardModifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffsetY }
                    } else {
                        Modifier
                    },
                    headerDragModifier = dragModifier,
                    dragging = isDragging,
                    targetSetId = visibleTimerTarget
                        ?.takeIf { timerTarget -> timerTarget.entryId == entryId }
                        ?.setId,
                    targetNavigationRequestId = visibleTimerTarget?.navigationRequestId ?: 0L,
                    restTimerSessionController = restTimerSessionController,
                    timerState = timerState,
                    onOverviewTargetPositioned = if (entryId == sortedEntries.first().entry.id) {
                        onWorkoutTargetPositioned
                    } else {
                        {}
                    },
                    onUpdateEntry = viewModel::updateWorkoutEntry,
                    onAddSet = { viewModel.addSet(entryWithSets.entry) },
                    onUpdateSet = viewModel::updateSet,
                    onDeleteSet = viewModel::deleteSet,
                    onDeleteEntry = { viewModel.deleteWorkoutEntry(entryWithSets.entry) },
                    onWatchImport = {
                        watchImportAutomatic = false
                        watchImportEntryId = entryId
                    },
                    onStopRestTimer = restTimerSessionController::stop,
                    programProvenance = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            programLinkByEntryId[entryId]?.let { link ->
                                ProgramRecordProvenance(
                                    link,
                                    pendingSuggestionByEntryId[entryId],
                                    prescriptionsByEntryId[entryId].orEmpty(),
                                    viewModel::resolveProgression
                                )
                                if (entryWithSets.entry.exerciseStableKey == "barbell_back_squat" &&
                                    selectedDate >= LocalDate.now().toString() &&
                                    entryWithSets.sets.isNotEmpty() &&
                                    entryWithSets.sets.none { it.confirmed }
                                ) {
                                    BCoachLinkedSquatNextWeekControl(viewModel, entryWithSets)
                                }
                            }
                            if (entryWithSets.entry.exerciseStableKey == "barbell_back_squat" &&
                                entryWithSets.sets.isNotEmpty() &&
                                entryWithSets.sets.all { it.confirmed }
                            ) {
                                val status = bCoachPostConfirmOutcomes[entryId]
                                val appliedKg = progressionSuggestions.firstOrNull { suggestion ->
                                    suggestion.sourceEntryId == entryId &&
                                        suggestion.reasons.startsWith("B_COACH_GATE_H_AUTO_AFTER_CONFIRM") &&
                                        suggestion.resolution == com.training.trackplanner.data.ProgressionResolution.ACCEPTED
                                }?.suggestedKg
                                if (status != null || appliedKg != null) {
                                    BCoachConfirmedSquatProgressionNotice(
                                        outcome = status,
                                        appliedKg = appliedKg,
                                        retry = { viewModel.retryBCoachAfterConfirmedSquat(entryId) }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}


@Composable
private fun BCoachConfirmedSquatProgressionNotice(
    outcome: com.training.trackplanner.data.v22.BCoachPostConfirmOutcome?,
    appliedKg: Double?,
    retry: () -> Unit
) {
    // Visible on the confirmed source workout, not hidden in next week's future screen.
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (appliedKg != null) {
            Text("B-Coach · 다음 주 스쿼트 자동 반영 완료 (${appliedKg}kg)",
                style = MaterialTheme.typography.bodySmall)
            if (outcome?.wasApplied == true) {
                Text("${outcome.nextLoadKg}kg × ${outcome.nextReps}회 × ${outcome.nextSets}세트 · ${outcome.selectedAction}",
                    style = MaterialTheme.typography.bodySmall)
            }
        } else if (outcome != null) {
            val explanation = when (outcome.status) {
                "NEXT_WEEK_UPDATED" -> "다음 주 계획에 자동 반영했습니다."
                "WAITING_FOR_CONFIRMED_RPE_SETS" -> "모든 세트의 확인과 RPE 기록이 필요합니다."
                "SOURCE_NOT_PROGRAM_LINKED" -> "이 기록이 적용 프로그램과 연결되지 않았습니다."
                "NO_NEXT_WEEK_SQUAT" -> "같은 프로그램의 다음 주 스쿼트를 찾지 못했습니다."
                "MULTIPLE_NEXT_WEEK_SQUATS" -> "다음 주 스쿼트가 여러 개라 연결을 결정할 수 없습니다."
                "EQUIPMENT_SNAPSHOT_MISSING" -> "이전 버전의 프로그램에 원판 간격이 저장돼 있지 않습니다. 새 B 프로그램이 필요합니다."
                "PREVIOUS_WEEK_NOT_FULLY_SUCCESSFUL" -> "기록한 세트가 프로그램 목표를 모두 달성하지 못했습니다."
                "CANONICAL_MODEL_NOT_READY", "MODEL_UPDATE_FAILED" ->
                    "새 운동기록을 반영한 근력 추정치가 준비되지 않았습니다."
                "LATEST_CANONICAL_ANCHOR_NOT_LINKED_SOURCE" ->
                    "최신 근력 기록과 이 프로그램의 직전 주차가 일치하지 않습니다."
                "TARGET_STRUCTURE_CHANGED", "USER_EDIT_PROTECTED" ->
                    "다음 주차의 사용자 수정 사항을 보호해 자동 변경하지 않았습니다."
                "LINK_OR_MODE_NOT_ELIGIBLE", "NO_LINKED_PREVIOUS_WEEK" ->
                    "프로그램의 주차·세션 연결이 불명확합니다."
                "TARGET_ALREADY_STARTED", "TARGET_DATE_NOT_FUTURE" ->
                    "다음 주 운동이 시작됐거나 과거 날짜여서 보호했습니다."
                "RANKING_NOT_AVAILABLE" -> "중량·반복·세트 후보를 선정할 근거가 부족합니다."
                "PRIOR_EXPLICIT_PROGRESSION_DECISION" ->
                    "다음 주차에 이미 확정된 진행 결정을 보호했습니다."
                "STALE_OR_EDITED_TARGET" -> "계산 후 기록이 변경되어 자동 적용을 취소했습니다."
                else -> "안전 조건을 충족하지 않아 자동 반영을 보류했습니다 (${outcome.status})."
            }
            Text("B-Coach · 다음 주 자동 조정 보류: $explanation",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("B-Coach · 마지막 세트를 확인하면 다음 주 계획을 재평가합니다.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (appliedKg == null) {
            OutlinedButton(onClick = retry, modifier = Modifier.fillMaxWidth()) {
                Text("다음 주 B-Coach 재평가", maxLines = 1)
            }
        }
    }
}

/**
 * Explicit approval only: the ranking chooses automatically, but does not overwrite plans
 * while a user merely views the record page. Show only program-linked squat entries.
 */
@Composable
private fun BCoachLinkedSquatNextWeekControl(
    viewModel: TrainingViewModel,
    record: WorkoutEntryWithSets
) {
    val entryId = record.entry.id
    var report by remember(entryId) {
        mutableStateOf<com.training.trackplanner.data.v22.BCoachLinkedSquatNextWeekProposal?>(null)
    }
    var busy by remember(entryId) { mutableStateOf(false) }
    var applied by remember(entryId) { mutableStateOf(false) }
    LaunchedEffect(entryId, record.entry.date, record.sets) {
        report = runCatching { viewModel.inspectBCoachLinkedSquatNextWeek(entryId) }.getOrNull()
    }
    val proposal = report ?: return
    if (applied) {
        Text("B-Coach: 다음 주 스쿼트 계획을 적용했습니다.", style = MaterialTheme.typography.bodySmall)
        return
    }
    if (proposal.canApply) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("B-Coach · 다음 주 스쿼트 후보 (실험)", style = MaterialTheme.typography.labelLarge)
            Text("${proposal.sourceWeek}주차 실제 기록: ${proposal.sourceText ?: "-"}",
                style = MaterialTheme.typography.bodySmall)
            Text("기존 ${proposal.targetWeek}주차: ${proposal.originalPlanText ?: "-"}",
                style = MaterialTheme.typography.bodySmall)
            Text("선정: ${proposal.proposedLoadKg}kg × ${proposal.proposedReps}회 × ${proposal.proposedSets}세트" +
                " (${proposal.selectedAction})", style = MaterialTheme.typography.bodySmall)
            Text("실제 완료·RPE와 e1RM 곡선을 비교한 잠정 결과입니다. 해당 미완료 운동만 수정합니다.",
                style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !busy,
                onClick = {
                    val hash = proposal.evidenceFingerprint ?: return@Button
                    busy = true
                    viewModel.approveBCoachLinkedSquatNextWeek(entryId, hash) { ok ->
                        busy = false
                        applied = ok
                        if (!ok) report = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("이번 주차 계획에 적용", maxLines = 1)
            }
        }
    } else if (proposal.status in setOf(
        "USER_EDIT_PROTECTED", "TARGET_STRUCTURE_CHANGED",
        "SOURCE_NOT_SUCCESSFUL", "PREVIOUS_WEEK_NOT_FULLY_SUCCESSFUL",
        "LATEST_CANONICAL_ANCHOR_NOT_LINKED_SOURCE", "SET_COUNT_REQUIRES_REVIEW",
        "CANONICAL_MODEL_NOT_READY", "PRIOR_EXPLICIT_PROGRESSION_DECISION"
    )) {
        val reason = when (proposal.status) {
            "EQUIPMENT_SNAPSHOT_MISSING" -> "저장된 원판 규격이 없어 자동 계획 변경을 보류합니다."
            "USER_EDIT_PROTECTED" -> "사용자가 수정한 처방은 자동 변경하지 않습니다."
            "TARGET_STRUCTURE_CHANGED" -> "세트 구성이나 현재 계획이 변경되어 재검토가 필요합니다."
            "PREVIOUS_WEEK_NOT_FULLY_SUCCESSFUL" -> "이전 주차 세트·반복·RPE 성공 조건이 충족되지 않았습니다."
            "LATEST_CANONICAL_ANCHOR_NOT_LINKED_SOURCE" -> "최근 근력 기록이 이 프로그램의 직전 주차와 일치하지 않습니다."
            "SET_COUNT_REQUIRES_REVIEW" -> "다음 주 계획의 세트 수 변경은 별도 검토가 필요합니다."
            "CANONICAL_MODEL_NOT_READY" -> "최신 근력 모델 계산을 확인해야 합니다."
            else -> "이미 진행 결정을 했거나 재검토가 필요합니다."
        }
        Text("B-Coach 다음 주 자동 조정 보류: $reason",
            style = MaterialTheme.typography.bodySmall)
    }
}


@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RecordDailyConditionCard(
    date: String,
    viewModel: TrainingViewModel,
    onAddExercise: () -> Unit
) {
    // The database's initial null state is not the same as a loaded day with no check-in.
    // Do not issue Health Connect requests until the authoritative value is known.
    val checkInSnapshot by remember(date) {
        viewModel.checkInForDate(date).map { loaded -> true to loaded }
    }.collectAsState(initial = false to null)
    val (checkInLoaded, checkIn) = checkInSnapshot
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
                        checkIn?.compactSummary()?.let { summary ->
                            stringResource(R.string.record_condition_summary, summary)
                        } ?: stringResource(R.string.record_condition_add),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Button(
                    onClick = onAddExercise
                ) {
                    Text(stringResource(R.string.record_add_exercise), maxLines = 1, softWrap = false)
                }
            }
            // Keep the manual watch selection available even when sleep was entered.
            // The child component still prevents unattended overwrite of saved values.
            if (checkInLoaded) {
                WatchSleepConditionEntry(
                    date = date,
                    savedHours = checkIn?.sleepHours,
                    editorOpen = showEditor,
                    viewModel = viewModel
                )
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
