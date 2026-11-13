package com.training.trackplanner

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn

import android.widget.Toast
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.SavedProgramDraft
import com.training.trackplanner.data.ProgramDraftSummary
import com.training.trackplanner.data.ProgramDraftIdentity
import com.training.trackplanner.data.ProgramBuildProgressState
import com.training.trackplanner.data.ProgramApplyConflictSummary
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramItemRestoreMetadataParser
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeLevel
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.TrainingProgramItemSet
import com.training.trackplanner.data.defaultProgramWeekDaySchedule
import com.training.trackplanner.data.emptyProgramSkeleton
import com.training.trackplanner.data.withResolvedWeekDaySchedule
import com.training.trackplanner.data.ProgramEditorSnapshot
import com.training.trackplanner.data.hydrateProgression
import com.training.trackplanner.data.reconcileProgression
import com.training.trackplanner.data.personalized.PersonalizedPlanningAnswers
import com.training.trackplanner.data.personalized.PersonalizedGenerationConstraints
import com.training.trackplanner.data.personalized.PersonalizedPlanningPreflight
import com.training.trackplanner.data.v22.BBalancedGoalImportance
import com.training.trackplanner.data.v22.BBlockProgramDefinition
import com.training.trackplanner.data.v22.BBlockEquipmentIncrements
import com.training.trackplanner.data.v22.BCoachSquatShadowProgramComparison
import com.training.trackplanner.data.v22.BCoachExerciseShadowCoverageRow
import com.training.trackplanner.data.v22.BBlockDefinition
import com.training.trackplanner.data.v22.BBlockGoal
import com.training.trackplanner.data.v22.BPeakTestMode
import com.training.trackplanner.data.v22.BBlockConfidenceReview
import com.training.trackplanner.data.v22.BBlockRelation
import com.training.trackplanner.data.v22.active
import com.training.trackplanner.data.v22.BVolumeAdjustment
import com.training.trackplanner.data.v22.BVolumePattern
import com.training.trackplanner.data.v22.propagateBBlockStartLoad
import com.training.trackplanner.data.v22.newBBalancedGenerationRequest
import com.training.trackplanner.data.v22.restoredBBalancedWeekPlans
import com.training.trackplanner.localization.localizedProgramName
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import org.json.JSONObject
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.training.trackplanner.data.personalized.*

internal fun personalizedQuestionShortLabel(question: PersonalizedPlanningQuestion): String = when (question.id) {
    QUESTION_STRENGTH_INTENT -> "근력 목표"
    QUESTION_BADMINTON_INTENT -> "배드민턴 구조화 훈련"
    QUESTION_FREE_WEIGHT -> "프리웨이트·편측운동"
    QUESTION_INTERRUPTION_FREQUENCY -> "일정 변동 빈도"
    else -> {
        val start = question.id.takeIf { it.startsWith(QUESTION_WEEK_CAUSE_PREFIX) }
            ?.removePrefix(QUESTION_WEEK_CAUSE_PREFIX)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start == null) question.prompt else {
            val end = start.plusDays(6)
            "${start.monthValue}/${start.dayOfMonth}~${end.monthValue}/${end.dayOfMonth} 운동량 감소 이유"
        }
    }
}

@Composable
internal fun PersonalizedPlanningQuestionDialog(
    questions: List<com.training.trackplanner.data.personalized.PersonalizedPlanningQuestion>,
    answers: Map<String, String>,
    onAnswer: (String, String) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    var submitted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("기록 기반 계획 사전 확인") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).testTag("preflight_questions"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                questions.forEach { question ->
                    androidx.compose.runtime.key(question.id) {
                        var expanded by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().testTag("question_row_${question.id}"), verticalAlignment = Alignment.CenterVertically) {
                            Text(personalizedQuestionShortLabel(question), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.weight(1f)) {
                                TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("question_answer_${question.id}")) {
                                    Text(question.options.firstOrNull { it.value == answers[question.id] }?.label ?: "선택 안 함",
                                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(" ▾", maxLines = 1)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    question.options.forEach { option ->
                                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                            onAnswer(question.id, option.value)
                                            expanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (!submitted) { submitted = true; onGenerate() } }, enabled = !submitted && questions.isNotEmpty() && questions.all { question ->
                question.options.any { it.value == answers[question.id] && it.value != "UNRESOLVED" }
            }) { Text("이 답변으로 생성") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
internal fun PlanScreen(
    viewModel: TrainingViewModel,
    onOpenRecord: () -> Unit,
    onShareProgram: (TrainingProgram) -> Unit = {},
    tutorialApplyRequest: Int = 0,
    showOnboardingApplyTarget: Boolean = false,
    onApplyTargetPositioned: (Rect) -> Unit = {}
) {
    val programs by viewModel.programs.collectAsState()
    val savedDrafts by viewModel.savedProgramDrafts.collectAsState()
    var activeDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var resumeDraft by rememberSaveable { mutableStateOf(false) }
    var selectedProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingProgram by rememberSaveable { mutableStateOf(false) }
    val selectedProgram = programs.firstOrNull { it.id == selectedProgramId }
    val editingProgram = programs.firstOrNull { it.id == editingProgramId }

    LaunchedEffect(programs) {
        if (selectedProgramId != null && selectedProgram == null) {
            selectedProgramId = null
        }
        if (editingProgramId != null && editingProgram == null) {
            editingProgramId = null
        }
    }

    BackHandler(enabled = creatingProgram || editingProgramId != null || selectedProgramId != null) {
        when {
            creatingProgram || editingProgramId != null -> {
                creatingProgram = false
                editingProgramId = null
            }
            selectedProgramId != null -> {
                selectedProgramId = null
            }
        }
    }

    when {
        creatingProgram || editingProgramId != null -> {
            ProgramEditorScreen(
                program = editingProgram,
                viewModel = viewModel,
                draftId = requireNotNull(activeDraftId),
                resumeDraft = resumeDraft,
                onCancel = {
                    creatingProgram = false
                    editingProgramId = null
                    activeDraftId = null
                    resumeDraft = false
                },
                onSaved = { programId ->
                    creatingProgram = false
                    editingProgramId = null
                    selectedProgramId = programId
                    activeDraftId = null
                    resumeDraft = false
                }
            )
        }
        selectedProgram == null -> {
        ProgramListScreen(
            programs = programs,
                savedDrafts = savedDrafts,
                viewModel = viewModel,
                onCreateProgram = {
                    val id = UUID.randomUUID().toString()
                    viewModel.activateProgramDraft(id)
                    activeDraftId = id
                    resumeDraft = false
                    creatingProgram = true
                },
                onContinueDraft = { draft ->
                    val current = programs.firstOrNull {
                        it.id == draft.sourceProgramId && it.stableKey == draft.sourceProgramStableKey
                    }
                    viewModel.activateProgramDraft(draft.draftId)
                    activeDraftId = draft.draftId
                    resumeDraft = true
                    editingProgramId = current?.id
                    creatingProgram = current == null
                },
                onSelectProgram = { selectedProgramId = it.id },
                onEditProgram = {
                    val id = ProgramDraftIdentity.forExistingProgram(it.stableKey)
                    viewModel.activateProgramDraft(id)
                    activeDraftId = id
                    resumeDraft = true
                    editingProgramId = it.id
                },
                onDeleteProgram = { program, onSuccess, onError ->
                    viewModel.deleteProgram(
                        program.id,
                        onDeleted = {
                            if (selectedProgramId == program.id) selectedProgramId = null
                            onSuccess()
                        },
                        onError = onError
                    )
                },
                onShareProgram = onShareProgram,
                onOpenRecord = onOpenRecord,
                tutorialApplyRequest = tutorialApplyRequest,
                showOnboardingApplyTarget = showOnboardingApplyTarget,
                onApplyTargetPositioned = onApplyTargetPositioned
        )
        }
        else -> {
        ProgramDetailScreen(
            program = selectedProgram,
            viewModel = viewModel,
            onBack = { selectedProgramId = null },
                onEdit = {
                    val id = ProgramDraftIdentity.forExistingProgram(selectedProgram.stableKey)
                    viewModel.activateProgramDraft(id)
                    activeDraftId = id
                    resumeDraft = true
                    editingProgramId = selectedProgram.id
                },
                onDeleted = { selectedProgramId = null }
        )
        }
    }
}
@Composable
private fun ProgramListScreen(
    programs: List<TrainingProgram>,
    savedDrafts: List<ProgramDraftSummary>,
    viewModel: TrainingViewModel,
    onCreateProgram: () -> Unit,
    onContinueDraft: (ProgramDraftSummary) -> Unit,
    onSelectProgram: (TrainingProgram) -> Unit,
    onEditProgram: (TrainingProgram) -> Unit,
    onDeleteProgram: (TrainingProgram, () -> Unit, (String) -> Unit) -> Unit,
    onShareProgram: (TrainingProgram) -> Unit,
    onOpenRecord: () -> Unit,
    tutorialApplyRequest: Int,
    showOnboardingApplyTarget: Boolean,
    onApplyTargetPositioned: (Rect) -> Unit
) {
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<TrainingProgram?>(null) }
    var applyTarget by remember { mutableStateOf<TrainingProgram?>(null) }
    var showDraftPicker by remember { mutableStateOf(false) }
    var handledTutorialApplyRequest by rememberSaveable { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(showOnboardingApplyTarget, programs) {
        if (showOnboardingApplyTarget && programs.isNotEmpty()) {
            listState.animateScrollToItem(4)
        }
    }

    LaunchedEffect(tutorialApplyRequest, programs) {
        if (tutorialApplyRequest > handledTutorialApplyRequest && programs.isNotEmpty()) {
            handledTutorialApplyRequest = tutorialApplyRequest
            applyTarget = programs.first()
        }
    }

    deleteTarget?.let { program ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("운동 프로그램 삭제") },
            text = { Text("이 운동 프로그램을 삭제할까요? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        // Report success only after the repository confirms completion.
                        onDeleteProgram(
                            program,
                            { Toast.makeText(context, "운동 프로그램을 삭제했습니다.", Toast.LENGTH_SHORT).show() },
                            { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                        )
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("취소")
                }
            }
        )
    }

    applyTarget?.let { program ->
        val displayName = localizedProgramName(program)
        AlertDialog(
            onDismissRequest = { applyTarget = null },
            title = { Text("$displayName 적용") },
            text = {
                ProgramApplyCard(
                    program = program,
                    viewModel = viewModel,
                    onApplied = {
                        applyTarget = null
                        onOpenRecord()
                    }
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { applyTarget = null }) {
                    Text("닫기")
                }
            }
        )
    }

    if (showDraftPicker) {
        AlertDialog(
            onDismissRequest = { showDraftPicker = false },
            title = { Text("임시저장한 프로그램") },
            text = {
                if (savedDrafts.isEmpty()) {
                    Text("아직 임시저장한 프로그램이 없습니다.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedDrafts, key = { it.draftId }) { draft ->
                            val modified = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
                                .withZone(ZoneId.systemDefault())
                                .format(Instant.ofEpochMilli(draft.updatedAt))
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showDraftPicker = false
                                    onContinueDraft(draft)
                                }
                            ) {
                                Column {
                                    Text(draft.programName.ifBlank { "이름 없는 프로그램" })
                                    Text(
                                        "마지막 수정: ${modified}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDraftPicker = false }) { Text("닫기") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "계획",
                body = "프로그램을 고른 뒤 시작일을 적용하면 날짜별 계획 세트가 만들어집니다."
            )
        }
        item {
            InfoCard("처음이라면 기본 프로그램을 고른 뒤 시작일을 적용하세요.")
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateProgram
            ) {
                Text("프로그램 새로 만들기")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDraftPicker = true }
            ) {
                Text("임시저장한 곳에서 계속하기")
            }
        }
        item {
            Text(
                text = "기본 프로그램",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (programs.isEmpty()) {
            item {
                InfoCard("기본 프로그램을 준비하는 중입니다. 잠시 후 다시 확인하세요.")
            }
        } else {
            items(programs, key = { it.id }) { program ->
                ProgramCard(
                    program = program,
                    onClick = { onSelectProgram(program) },
                    onApply = { applyTarget = program },
                    onEdit = { onEditProgram(program) },
                    onDelete = { deleteTarget = program },
                    onShare = { onShareProgram(program) },
                    applyModifier = if (program.id == programs.first().id) {
                        Modifier.onGloballyPositioned { coordinates ->
                            onApplyTargetPositioned(coordinates.boundsInRoot())
                        }
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramEditorScreen(
    program: TrainingProgram?,
    viewModel: TrainingViewModel,
    draftId: String,
    resumeDraft: Boolean,
    onCancel: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val exercises by viewModel.exercises.collectAsState()
    val initialProfile by viewModel.initialUserProfile.collectAsState()
    val runtimeMetadataByExerciseId by viewModel.exerciseRuntimeMetadata.collectAsState()
    val muscleMetadataByKey by viewModel.exerciseMuscleMetadata.collectAsState()
    var progressionContext by remember { mutableStateOf<com.training.trackplanner.data.ProgressionDraftContext?>(null) }
    val progressionEligibleKeys = progressionContext?.eligibleKeys.orEmpty()
    val staged = remember(draftId) { viewModel.stagedProgramDraft(draftId) }
    var nameText by rememberSaveable(program?.id ?: 0L, draftId) {
        mutableStateOf(staged?.programName ?: program?.name.orEmpty())
    }
    var nameError by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(false)
    }
    var v22SessionMinutes by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.sessionMinutes ?: 60).takeIf { it > 0 } ?: 60)
    }
    var personalizedDraft by remember(program?.id, draftId) {
        mutableStateOf<GeneratedProgramSkeleton?>(staged?.skeleton)
    }
    var initialDraftLoaded by remember(draftId) {
        mutableStateOf(staged != null || (program == null && !resumeDraft))
    }
    val finalized = remember(draftId) { mutableStateOf(false) }
    val hasDraftItems = personalizedDraft?.items?.isNotEmpty() == true
    val buildProgress by viewModel.programBuildProgress.collectAsState()
    val generationRunning = buildProgress is ProgramBuildProgressState.Running
    var lastGenerationWasV22 by rememberSaveable(draftId) {
        mutableStateOf(staged?.wasBBlockGenerated ?: false)
    }
    var v22Days by rememberSaveable(program?.id ?: 0L) { mutableStateOf(4) }
    var bAllowFourthDay by rememberSaveable(program?.id ?: 0L) { mutableStateOf(true) }
    var bStrengthImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var bHypertrophyImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var bCoreImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var bSportImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var bBlockPattern by rememberSaveable(program?.id ?: 0L) { mutableStateOf(BVolumePattern.STEADY) }
    var bAdvancedOptions by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }
    var bBasicAdvancedBlock by remember { mutableStateOf(BBlockDefinition()) }
    var bCustomizeBlocks by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }
    var bBlocks by remember { mutableStateOf(listOf(BBlockDefinition())) }
    var bRecoveryBetween by remember { mutableStateOf(emptyList<Int>()) }
    var smallestPlateText by rememberSaveable { mutableStateOf((initialProfile?.smallestPlateKg ?: 1.25).toString()) }
    var dumbbellUnitText by rememberSaveable { mutableStateOf((initialProfile?.dumbbellIncrementKg ?: 1.0).toString()) }
    var machineUnitText by rememberSaveable { mutableStateOf((initialProfile?.machineIncrementKg ?: 2.5).toString()) }
    var equipmentUnitError by remember { mutableStateOf<String?>(null) }
    // Profile changes from the home profile dialog or a restore refresh this screen.
    LaunchedEffect(initialProfile?.smallestPlateKg,
        initialProfile?.dumbbellIncrementKg, initialProfile?.machineIncrementKg) {
        smallestPlateText = (initialProfile?.smallestPlateKg ?: 1.25).toString()
        dumbbellUnitText = (initialProfile?.dumbbellIncrementKg ?: 1.0).toString()
        machineUnitText = (initialProfile?.machineIncrementKg ?: 2.5).toString()
    }
    var v22SkeletonCreated by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }
    var pendingConservativeBBlock by remember(program?.id) {
        mutableStateOf<GeneratedProgramSkeleton?>(null)
    }
    var pendingBReview by remember(program?.id) {
        mutableStateOf<BBlockConfidenceReview.Decision?>(null)
    }
    var pendingLoadOverwrite by remember(program?.id) {
        mutableStateOf<GeneratedProgramSkeleton?>(null)
    }
    var pendingLoadOverwriteCount by remember { mutableStateOf(0) }
    var showBReselectionLog by remember(program?.id) { mutableStateOf(false) }
    var squatShadowComparison by remember(program?.id, draftId) {
        mutableStateOf<BCoachSquatShadowProgramComparison?>(null)
    }
    var showSquatShadowComparison by remember { mutableStateOf(false) }
    var otherExerciseShadowCoverage by remember(program?.id, draftId) {
        mutableStateOf<List<BCoachExerciseShadowCoverageRow>>(emptyList())
    }
    var showOtherExerciseShadowCoverage by remember { mutableStateOf(false) }

    LaunchedEffect(draftId, program?.id, resumeDraft) {
        val saved = if (resumeDraft) viewModel.loadSavedProgramDraft(draftId) else null
        if (saved != null) {
            nameText = saved.programName
            personalizedDraft = saved.skeleton?.withResolvedWeekDaySchedule()
            lastGenerationWasV22 = saved.wasBBlockGenerated
        } else if (program != null && personalizedDraft == null) {
            personalizedDraft = skeletonFromProgram(
                viewModel.programEditorSnapshot(program.id)
            ).withResolvedWeekDaySchedule()
        }
        initialDraftLoaded = true
    }
    val currentSnapshot by rememberUpdatedState(
        SavedProgramDraft(
            draftId = draftId,
            sourceProgramId = program?.id,
            sourceProgramStableKey = program?.stableKey,
            programName = nameText.ifBlank { "새 프로그램" },
            skeleton = personalizedDraft?.let {
                val currentName = nameText.ifBlank { "새 프로그램" }
                it.copy(
                    suggestedName = currentName,
                    request = it.request.copy(name = currentName)
                )
            },
            wasBBlockGenerated = lastGenerationWasV22
        )
    )
    val currentName by rememberUpdatedState(nameText)
    val loadedState by rememberUpdatedState(initialDraftLoaded)
    SideEffect {
        if (initialDraftLoaded && !finalized.value) {
            viewModel.stageProgramDraft(currentSnapshot)
        }
    }
    // Observe actual editor changes, not a periodic timer: every new edit
    // cancels the pending write. The first loaded snapshot is not an edit.
    LaunchedEffect(draftId) {
        snapshotFlow { if (loadedState) currentSnapshot else null }
            .filterNotNull()
            .drop(1)
            .collectLatest { edited ->
                delay(ProgramDraftIdentity.AUTO_SAVE_DEBOUNCE_MILLIS)
                if (!finalized.value &&
                    (edited.skeleton != null || edited.programName.isNotBlank())
                ) {
                    viewModel.saveProgramDraft(edited)
                }
            }
    }
    DisposableEffect(draftId) {
        onDispose {
            if (loadedState && !finalized.value &&
                (currentSnapshot.skeleton != null || currentName.isNotBlank())
            ) {
                viewModel.saveProgramDraft(currentSnapshot)
            }
        }
    }
    LaunchedEffect(exercises, runtimeMetadataByExerciseId) {
        progressionContext = viewModel.progressionDraftContext()
    }
    LaunchedEffect(personalizedDraft, progressionContext) {
        progressionContext?.let { context -> personalizedDraft = personalizedDraft?.reconcileProgression(context.eligibleKeys, context.oneRmSnapshots) }
    }
    fun normalizedProgramName(): String = nameText.trim()

    fun requireProgramName(): Boolean {
        val valid = normalizedProgramName().isNotEmpty()
        nameError = !valid
        if (!valid) {
            Toast.makeText(context, "프로그램명을 입력하세요.", Toast.LENGTH_SHORT).show()
        }
        return valid
    }

    // A blank draft starts without setup questions; its days and exercises
    // remain editable in the existing week/day preview.
    fun blankProgramRequest(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = normalizedProgramName().ifBlank { "새 프로그램" },
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 4
        )

    fun startBlankProgram() {
        if (viewModel.programBuildProgress.value is ProgramBuildProgressState.Running) return
        if (nameText.isBlank()) nameText = "새 프로그램"
        nameError = false
        lastGenerationWasV22 = false
        v22SkeletonCreated = false
        squatShadowComparison = null
        otherExerciseShadowCoverage = emptyList()
        personalizedDraft = emptyProgramSkeleton(
            request = blankProgramRequest(),
            weekDaySchedule = defaultProgramWeekDaySchedule(4, 3)
        )
    }

    fun generateV22() {
        if (!requireProgramName()) return
        if (viewModel.programBuildProgress.value is ProgramBuildProgressState.Running) return
        lastGenerationWasV22 = true
        squatShadowComparison = null
        otherExerciseShadowCoverage = emptyList()
        pendingConservativeBBlock = null
        pendingBReview = null
        val blockLayout = if (bCustomizeBlocks) BBlockProgramDefinition(bBlocks, bRecoveryBetween)
            else BBlockProgramDefinition(blocks = listOf(bBasicAdvancedBlock.copy(pattern = bBlockPattern)))
        viewModel.generateV22Program(
            newBBalancedGenerationRequest(
                name = normalizedProgramName(),
                days = v22Days,
                asOf = LocalDate.now(),
                sessionMinutes = v22SessionMinutes,
                allowFourthDay = bAllowFourthDay,
                importance = BBalancedGoalImportance(
                    strength = bStrengthImportance.takeIf { it > 0 },
                    hypertrophy = bHypertrophyImportance.takeIf { it > 0 },
                    core = bCoreImportance.takeIf { it > 0 },
                    sportSupport = bSportImportance.takeIf { it > 0 }
                ),
                blockProgram = blockLayout,
                sourceProgramStableKey = program?.stableKey,
                shadowPlateOneSideKg = smallestPlateText.toDoubleOrNull(),
                shadowDumbbellIncrementKg = dumbbellUnitText.toDoubleOrNull(),
                shadowMachineIncrementKg = machineUnitText.toDoubleOrNull()
            )
        ) { generated ->
            squatShadowComparison = generated.squatShadowComparison
            otherExerciseShadowCoverage = generated.otherExerciseShadowCoverage
            val proposed = generated.skeleton.withResolvedWeekDaySchedule()
            val review = generated.balanced12WeekPlan?.let {
                BBlockConfidenceReview.evaluate(it, blockLayout)
            }
            if (review?.requiresApproval == true) {
                // Review is exercise-scoped: even partly missing records need user approval.
                pendingBReview = review
                pendingConservativeBBlock = proposed
            } else {
                personalizedDraft = proposed
                v22SkeletonCreated = true
            }
        }
    }

    pendingConservativeBBlock?.let { proposed ->
        AlertDialog(
            onDismissRequest = { pendingConservativeBBlock = null; pendingBReview = null },
            title = { Text("기록 부족 · 처방 확인 필요") },
            text = { Text(
                "일부 운동은 비교 가능한 최근 기록이 충분하지 않습니다. " +
                    "검토 대상: ${pendingBReview?.unreliableExerciseKeys?.size ?: 0}개 운동. " +
                    "중량은 자동 지정하지 않으며, 세트·반복은 보수적인 초안입니다. " +
                    "실제 수행 여유에 맞춰 수정할 것을 전제로 미리보기를 승인하시겠습니까?"
            ) },
            confirmButton = {
                Button(onClick = {
                    personalizedDraft = proposed
                    v22SkeletonCreated = true
                    pendingConservativeBBlock = null
                    pendingBReview = null
                }) { Text("초안 승인 후 검토") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConservativeBBlock = null; pendingBReview = null }) { Text("설정 수정") }
            }
        )
    }

    pendingLoadOverwrite?.let { replacement ->
        AlertDialog(
            onDismissRequest = { pendingLoadOverwrite = null },
            title = { Text("이후 주차의 중량 재계산") },
            text = { Text(
                "1주차 중량을 변경하면 이후 주차가 다시 계산됩니다. " +
                    "이미 직접 수정한 중량 ${pendingLoadOverwriteCount}개도 변경됩니다. " +
                    "1주차 중량을 먼저 확정한 뒤 2주차 이후를 수정하세요. 계속 적용할까요?"
            ) },
            confirmButton = {
                Button(onClick = {
                    personalizedDraft = replacement
                    pendingLoadOverwrite = null
                }) { Text("이후 주차도 재계산") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLoadOverwrite = null }) { Text("취소") }
            }
        )
    }

    if (showSquatShadowComparison) {
        squatShadowComparison?.let { report ->
            val details = buildString {
                appendLine("상태: ${report.status}")
                appendLine("기준일: ${report.snapshotDate ?: "확인 불가"}")
                appendLine("최근 확인 세트: ${report.observedReference ?: "확인 불가"}")
                appendLine()
                appendLine("[기존 B 생성 결과: 스쿼트 모든 주차]")
                if (report.plannedSquatRows.isEmpty()) appendLine("백스쿼트 배치 없음")
                report.plannedSquatRows.forEach { row ->
                    appendLine("${row.week}주차·${row.day}요일: ${row.sets}세트×${row.reps}회" +
                        " · 중량 ${row.loadKg?.let { "${it}kg" } ?: "사용자 입력 대기"}")
                }
                appendLine()
                appendLine("[Shadow 단기 후보: 전체 주차 처방 아님]")
                if (report.experimentCandidates.isEmpty()) appendLine("계산 가능한 후보 없음")
                report.experimentCandidates.forEach { candidate ->
                    appendLine("${candidate.action}: ${candidate.loadKg}kg×${candidate.reps}회×${candidate.sets}세트" +
                        " · 앵커 RPE기반 RIR 적합질량 ${candidate.targetRirMass?.let { "%.1f%%".format(it * 100.0) } ?: "불확실"}" +
                        " · 후속 미예측 ${candidate.followUpSetsUnmodeled}세트")
                }
                appendLine()
                appendLine("※ 위 질량은 마지막 확인 세트의 RPE→RIR 가정에 따른 비중으로, 미래 성공확률이나 수행능력 posterior 확률이 아닙니다.")
                appendLine("※ HOLD는 최근 확인 세트 수준 유지이며, 기존 B 생성 처방과 별개입니다.")
                report.curveRanking?.let { ranking ->
                    appendLine("[Gate F: e1RM 곡선 기반 단일 후보 순위 — 저장에 미반영]")
                    appendLine("기록일: ${ranking.referenceDate ?: "확인 불가"} · 상태 ${ranking.status}")
                    appendLine("목표 RIR: ${"%.1f".format(ranking.targetRir)} · 계획상 세트 목표: ${ranking.doseTargetSets ?: "미확정"}")
                    appendLine("과거 확인 세트 기반 e1RM: ${ranking.historicalE1rmKg?.let { "%.1fkg".format(it) } ?: "평가 불가"}")
                    appendLine("잠정 1순위: ${ranking.selectedAction ?: "판단 보류"}")
                    ranking.ranked.forEachIndexed { i, candidate ->
                        appendLine(
                            "${i + 1}. ${candidate.action}: ${candidate.loadKg}kg×${candidate.reps}회×${candidate.sets}세트" +
                                " · 필요 e1RM ${candidate.requiredE1rmKg?.let { "%.1fkg".format(it) } ?: "-"}" +
                                " · e1RM 기록차 ${candidate.historicalE1rmLogGap?.let { "%.3f".format(it) } ?: "-"}" +
                                " · 보수적 후회 ${candidate.worstCaseRegret?.let { "%.3f".format(it) } ?: "미평가"}" +
                                " · 명목 훈련량 변화 ${"%.1f%%".format(candidate.volumeLoadChange * 100.0)}" +
                                " · ${if (candidate.eligible) "비교 가능" else "선정 제외: ${candidate.reason}"}"
                        )
                    }
                    appendLine("※ 기존 곡선과 과거 확인 세트로 계산합니다. 실제 다음 세션 성공확률이나 미래 2~4주차 처방이 아닙니다.")
                    appendLine("※ 세트 증가의 목표치는 생성 계획의 구조를 참고하며, 근비대 효과가 검증된 수치가 아닙니다.")
                    appendLine()
                }
                appendLine("[기존 Gate C: 가중치 민감도 비교(참고용)]")
                appendLine("실험계수 1=유지 중심, 2=발전 중심, 3=강도·급증 중심 (목표 RIR 1~3 가정)")
                report.experiments.forEach { trial ->
                    appendLine("${trial.scenario.replace("CENTRAL_80", "RPE-RIR 중앙80%").replace("CENTRAL_90", "RPE-RIR 중앙90%")} · ${trial.coefficientPolicy}: ${trial.tentativeWinner}")
                }
                appendLine()
                report.notices.forEach { appendLine("• ${it}") }
            }
            AlertDialog(
                onDismissRequest = { showSquatShadowComparison = false },
                title = { Text("백스쿼트: 기존 생성 vs Shadow") },
                text = {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                        Text("이 비교 결과는 저장될 중량·반복·세트를 수정하지 않습니다.")
                        Text(details)
                    }
                },
                confirmButton = { TextButton(onClick = { showSquatShadowComparison = false }) { Text("닫기") } }
            )
        }
    }

    if (showOtherExerciseShadowCoverage) {
        val reportText = buildString {
            appendLine("이 화면은 타 운동의 진행 모델 적용 가능 범위를 점검합니다.")
            appendLine("실제 중량·반복·세트 처방은 변경하거나 승인하지 않습니다.")
            appendLine("스쿼트 이외 운동의 RIR 예측/최적 후보 선택은 아직 구현되지 않았습니다.")
            appendLine()
            otherExerciseShadowCoverage.take(40).forEach { row ->
                appendLine("[${row.tier}] ${row.exerciseName}")
                appendLine("상태: ${row.status}")
                appendLine("최근 기록: ${row.confirmedObservation ?: "비교 가능한 기록 없음"}")
                appendLine("가능한 검토 방향: ${row.unrankedAlternatives.joinToString(" · ").ifBlank { "근거 부족" }}")
                appendLine(row.explanation)
                appendLine()
            }
            if (otherExerciseShadowCoverage.size > 40)
                appendLine("나머지 ${otherExerciseShadowCoverage.size - 40}개 운동은 표시 생략")
        }
        AlertDialog(
            onDismissRequest = { showOtherExerciseShadowCoverage = false },
            title = { Text("타 운동 Shadow 근거 점검") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(reportText)
                }
            },
            confirmButton = {
                TextButton(onClick = { showOtherExerciseShadowCoverage = false }) { Text("닫기") }
            }
        )
    }

    if (showBReselectionLog) {
        val readable = personalizedDraft?.planningMetadataJson?.let { raw ->
            runCatching {
                val metadata = JSONObject(raw)
                val logs = metadata.optJSONArray("reselectionAudit")
                val selectionText = if (logs == null) "이 프로그램에는 운동 재선정 로그가 없습니다."
                else (0 until logs.length()).joinToString("\n\n") { index ->
                    val row = logs.getJSONObject(index)
                    val changes = row.optJSONArray("changeDetails")
                    val details = if (changes == null || changes.length() == 0) "선정 운동 변경 없음"
                        else (0 until changes.length()).joinToString("\n") { change ->
                            val entry = changes.getJSONObject(change)
                            val target = entry.optJSONArray("primaryMuscles")
                            val muscles = if (target == null) "" else
                                (0 until target.length()).joinToString(", ") { target.getString(it) }
                            "${entry.getString("from")} → ${entry.getString("to")}" +
                                if (muscles.isBlank()) "" else " · 관련 주동근: $muscles"
                        }
                    "블록 ${row.getInt("block")} · ${row.optString("relation")}" +
                        "\n변경 ${row.optInt("changes")}개" +
                        "\n${details}"
                }
                val joint = metadata.optJSONArray("jointExerciseSetAudit")
                val jointText = if (joint == null) "이 프로그램에는 공동 최적화 기록이 없습니다."
                else (0 until joint.length()).joinToString("\n\n") { index ->
                    val row = joint.getJSONObject(index)
                    val actions = row.optJSONArray("changes")
                    val details = if (actions == null || actions.length() == 0) "운동/세트 변경 없음"
                        else (0 until actions.length()).joinToString("\n") { action ->
                            actions.getString(action)
                        }
                    "블록 ${row.optInt("block")} · ${row.optString("status")}" +
                        "\n후보 평가 ${row.optInt("evaluatedCandidates")}개" +
                        " · 점수 ${row.optDouble("baselineScore")} → ${row.optDouble("finalScore")}" +
                        "\n${details}"
                }
                val coach = metadata.optJSONArray("coachWholeBlockAudit")
                val coachText = if (coach == null) "전체 블록 공동 탐색 기록 없음"
                else (0 until coach.length()).joinToString("\n\n") { index ->
                    val row = coach.getJSONObject(index)
                    val evidence = row.optJSONObject("actualEvidence")
                    val alternatives = row.optJSONArray("alternatives")
                    val ranked = if (alternatives == null || alternatives.length() == 0) "실행 가능한 대안 기록 없음"
                        else (0 until alternatives.length()).joinToString("\n") { rank ->
                            val alt = alternatives.getJSONObject(rank)
                            val days = alt.optJSONArray("weekdays")
                            val dayText = if (days == null) "-" else
                                (0 until days.length()).joinToString("·") {
                                    listOf("월", "화", "수", "목", "금", "토", "일")[days.getInt(it) - 1]
                                }
                            "  " + (rank + 1) + ". " + dayText +
                                " · " + alt.optString("strategy") +
                                " · " + alt.optInt("weeklySets") + "세트" +
                                " · " + "%.1f".format(alt.optDouble("peakMinutes")) + "분" +
                                " · 점수 " + "%.3f".format(alt.optDouble("score"))
                        }
                    val completionLabel = if (evidence == null ||
                        evidence.isNull("plannedCompletionRate")) "확인 불가"
                    else "%.1f%%".format(evidence.optDouble("plannedCompletionRate") * 100.0)
                    val actions = row.optJSONArray("changes")
                    val changes = if (actions == null || actions.length() == 0) "원안 유지"
                        else (0 until actions.length()).take(20).joinToString("\n") { actions.getString(it) }
                    "블록 " + row.optInt("block") + " · 선택 전략 " + row.optString("strategy") +
                        (if (row.optBoolean("conditionalFuturePlan")) " (미래 조건부 계획)" else "") +
                        "\n" + row.optString("status") +
                        "\n평가 후보 " + row.optInt("evaluated") +
                        " · 점수 " + "%.3f".format(row.optDouble("originalScore")) +
                        " → " + "%.3f".format(row.optDouble("finalScore")) +
                        "\n실제 기록 " + (evidence?.optInt("observedSets28d") ?: 0) +
                        "세트 / 최근 28일, 운동일 " +
                        (evidence?.optInt("observedTrainingDates28d") ?: 0) +
                        "일 (계획 대비 완료율 " + completionLabel + ")" +
                        "\n상위 대안:\n" + ranked +
                        "\n변경 사항:\n" + changes
                }
                "[코치형 블록 최적화]\n" + coachText +
                    "\n\n[운동 선정]\n" + selectionText +
                    "\n\n[기존 운동·세트 탐색]\n" + jointText
            }.getOrElse { "운동 선택·세트 최적화 로그를 읽을 수 없습니다. 메타데이터를 확인하세요." }
        } ?: "운동 재선정 로그가 없습니다."
        AlertDialog(
            onDismissRequest = { showBReselectionLog = false },
            title = { Text("운동 선정 상세 로그") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("프로그램 생성 당시 계획과 메타데이터에 따른 선정 결과입니다. 실제 훈련 효과를 의미하지 않습니다.")
                    Text(readable)
                }
            },
            confirmButton = { TextButton(onClick = { showBReselectionLog = false }) { Text("닫기") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = if (program == null) "프로그램 새로 만들기" else "프로그램 수정",
                body = "빈 프로그램을 직접 구성하거나 4주 기본 블록 프로그램을 만든 뒤, 저장 전에 세트와 중량을 조정합니다."
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = nameText,
                        onValueChange = {
                            if (generationRunning) return@OutlinedTextField
                            nameText = it
                            if (nameError && it.isNotBlank()) nameError = false
                        },
                        label = { Text("프로그램명") },
                        isError = nameError,
                        enabled = !generationRunning && initialDraftLoaded,
                        supportingText = {
                            if (nameError) Text("프로그램명을 입력하세요.")
                        },
                        singleLine = true
                    )
                    if (program == null) {
                        Text(
                            text = "빈 프로그램",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "기본 4주 · 주 3일(월·수·금)로 빈 계획을 엽니다. 요일과 운동은 직접 수정하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !generationRunning,
                            onClick = { startBlankProgram() }
                        ) {
                            Text("빈 프로그램으로 시작")
                        }
                        Text(
                            text = "4주 기본 운동 프로그램",
                            style = MaterialTheme.typography.titleMedium
                        )
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "주당 운동일수",
                                selected = v22Days,
                                options = (1..7).toList(),
                                optionLabel = { stringResource(R.string.program_days_per_week, it) },
                                onSelect = { v22Days = it },
                                enabled = !generationRunning
                            )
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "하루 운동시간",
                                selected = v22SessionMinutes,
                                options = listOf(30, 45, 60),
                                optionLabel = { stringResource(R.string.program_minutes, it) },
                                onSelect = { v22SessionMinutes = it },
                                enabled = !generationRunning
                            )
                        }
                        if (v22Days == 3) {
                            ProgramOptionRow {
                                ProgramDropdown(
                                    modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                    label = "최대 운동일수",
                                    selected = bAllowFourthDay,
                                    options = listOf(true, false),
                                    optionLabel = { if (it) "필요하면 4일로 분산" else "3일 고정" },
                                    onSelect = { bAllowFourthDay = it },
                                    enabled = !generationRunning
                                )
                            }
                        }
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "근력 중요도",
                                selected = bStrengthImportance,
                                options = (0..5).toList(),
                                optionLabel = { if (it == 0) "기록 추천" else "$it / 5" },
                                onSelect = { bStrengthImportance = it },
                                enabled = !generationRunning
                            )
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "근비대 중요도",
                                selected = bHypertrophyImportance,
                                options = (0..5).toList(),
                                optionLabel = { if (it == 0) "기록 추천" else "$it / 5" },
                                onSelect = { bHypertrophyImportance = it },
                                enabled = !generationRunning
                            )
                        }
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "코어 중요도",
                                selected = bCoreImportance,
                                options = (0..5).toList(),
                                optionLabel = { if (it == 0) "기록 추천" else "$it / 5" },
                                onSelect = { bCoreImportance = it },
                                enabled = !generationRunning
                            )
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "스포츠 보조 중요도",
                                selected = bSportImportance,
                                options = (0..5).toList(),
                                optionLabel = { if (it == 0) "기록 추천" else "$it / 5" },
                                onSelect = { bSportImportance = it },
                                enabled = !generationRunning
                            )
                        }
                        Text("내 장비의 최소 증량 단위 (프로필과 동기화)", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = smallestPlateText, onValueChange = { smallestPlateText = it; equipmentUnitError = null },
                            label = { Text("원판 1장 무게 (kg) · 바벨 증량은 2배") },
                            singleLine = true, enabled = !generationRunning
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = dumbbellUnitText, onValueChange = { dumbbellUnitText = it; equipmentUnitError = null },
                            label = { Text("덤벨 최소 증량 (표시 중량 kg)") },
                            singleLine = true, enabled = !generationRunning
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = machineUnitText, onValueChange = { machineUnitText = it; equipmentUnitError = null },
                            label = { Text("머신 최소 증량 (표시 중량 kg)") },
                            singleLine = true, enabled = !generationRunning
                        )
                        equipmentUnitError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(), enabled = !generationRunning,
                            onClick = {
                                val plate = smallestPlateText.toDoubleOrNull()
                                val dumbbell = dumbbellUnitText.toDoubleOrNull()
                                val machine = machineUnitText.toDoubleOrNull()
                                if (!BBlockPlateProfileSettings.isValid(plate) ||
                                    dumbbell == null || !dumbbell.isFinite() || dumbbell !in 0.1..50.0 ||
                                    machine == null || !machine.isFinite() || machine !in 0.1..100.0) {
                                    equipmentUnitError = "세 증량 단위 모두 올바른 양수로 입력해 주세요."
                                } else {
                                    viewModel.saveInitialUserProfile(
                                        (initialProfile ?: InitialUserProfile()).copy(
                                            smallestPlateKg = plate!!,
                                            dumbbellIncrementKg = dumbbell,
                                            machineIncrementKg = machine
                                        )
                                    )
                                    equipmentUnitError = null
                                }
                            }
                        ) { Text("증량 단위 프로필에 저장") }
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "블록 직접 구성",
                                selected = bCustomizeBlocks,
                                options = listOf(false, true),
                                optionLabel = { if (it) "직접 구성" else "기본 4주" },
                                onSelect = { bCustomizeBlocks = it },
                                enabled = !generationRunning
                            )
                        }

                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "주기화 고급 설정",
                                selected = bAdvancedOptions,
                                options = listOf(false, true),
                                optionLabel = { if (it) "표시" else "기본 자동 설정" },
                                onSelect = { bAdvancedOptions = it },
                                enabled = !generationRunning
                            )
                        }
                        if (bCustomizeBlocks) {
                            ProgramOptionRow {
                                ProgramDropdown(
                                    modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                    label = "블록 개수",
                                    selected = bBlocks.size,
                                    options = (1..4).toList(),
                                    optionLabel = { it.toString() + "개" },
                                    onSelect = { count ->
                                        val updated = bBlocks.take(count) +
                                            List((count - bBlocks.size).coerceAtLeast(0)) { BBlockDefinition() }
                                        bBlocks = updated.map { block ->
                                            block.copy(relationToPrevious = block.relationToPrevious.active())
                                        }
                                        bRecoveryBetween = List((count - 1).coerceAtLeast(0)) { index ->
                                            bRecoveryBetween.getOrElse(index) { 0 }
                                        }
                                    },
                                    enabled = !generationRunning
                                )
                            }
                            bBlocks.forEachIndexed { blockIndex, block ->
                                Text("블록 " + (blockIndex + 1), style = MaterialTheme.typography.titleMedium)
                                ProgramOptionRow {
                                    ProgramDropdown(
                                        modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                        label = "블록 목적",
                                        selected = block.goal,
                                        options = BBlockGoal.entries.toList(),
                                        optionLabel = { goal -> when (goal) {
                                            BBlockGoal.AUTO -> "자동 결정"
                                            BBlockGoal.STRENGTH -> "근력"
                                            BBlockGoal.HYPERTROPHY -> "근비대"
                                            BBlockGoal.SPORT_SUPPORT -> "스포츠 보조"
                                            BBlockGoal.BALANCED -> "균형"
                                            BBlockGoal.PEAK_EVALUATION -> "피크·평가"
                                        } },
                                        onSelect = { goal ->
                                            bBlocks = bBlocks.toMutableList().also { it[blockIndex] = block.copy(goal = goal) }
                                        },
                                        enabled = !generationRunning
                                    )
                                    ProgramDropdown(
                                        modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                        label = "블록 기간",
                                        selected = block.weeks,
                                        options = (1..6).toList(),
                                        optionLabel = { it.toString() + "주" },
                                        onSelect = { weeks ->
                                            val pattern = if (weeks == 1 ||
                                                (weeks == 2 && block.pattern in listOf(BVolumePattern.WAVE,
                                                    BVolumePattern.ACCUMULATE_RECOVER)))
                                                BVolumePattern.STEADY else block.pattern
                                            bBlocks = bBlocks.toMutableList().also {
                                                it[blockIndex] = block.copy(weeks = weeks, pattern = pattern)
                                            }
                                        },
                                        enabled = !generationRunning
                                    )
                                }
                                if (block.goal == BBlockGoal.PEAK_EVALUATION) {
                                    ProgramOptionRow {
                                        ProgramDropdown(
                                            modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                            label = "최종 평가 반복 방식",
                                            selected = block.peakTestMode,
                                            options = BPeakTestMode.entries.toList(),
                                            optionLabel = { when (it) {
                                                BPeakTestMode.USER_CHOICE -> "미리보기에서 직접 결정"
                                                BPeakTestMode.ONE_REP -> "1회 평가"
                                                BPeakTestMode.THREE_REPS -> "3회 평가"
                                            } },
                                            onSelect = { mode ->
                                                bBlocks = bBlocks.toMutableList().also {
                                                    it[blockIndex] = block.copy(peakTestMode = mode)
                                                }
                                            },
                                            enabled = !generationRunning
                                        )
                                    }
                                }
                                val relationChoices = BBlockRelation.entries.filter { relation ->
                                    relation != BBlockRelation.PREPARE_NEXT &&
                                        (blockIndex > 0 || relation == BBlockRelation.INDEPENDENT)
                                }
                                ProgramOptionRow {
                                    ProgramDropdown(
                                        modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                        label = "다른 블록과의 관계",
                                        selected = block.relationToPrevious.active(),
                                        options = relationChoices,
                                        optionLabel = { relation -> when (relation) {
                                            BBlockRelation.INDEPENDENT -> "독립"
                                            BBlockRelation.COMPLEMENT_PLANNED_GAPS -> "이전 블록 부족분 보완"
                                            BBlockRelation.DEVELOP_PREVIOUS -> "이전 블록 발전"
                                            BBlockRelation.PREPARE_NEXT -> "종료된 설정(독립형으로 처리)"
                                        } },
                                        onSelect = { relation ->
                                            bBlocks = bBlocks.toMutableList().also {
                                                it[blockIndex] = block.copy(relationToPrevious = relation)
                                            }
                                        },
                                        enabled = !generationRunning
                                    )
                                }
                                Text("볼륨 주기화", style = MaterialTheme.typography.titleSmall)
                                BBlockPatternPicker(block.weeks, block.pattern, onSelect = { pattern ->
                                    bBlocks = bBlocks.toMutableList().also {
                                        it[blockIndex] = block.copy(pattern = pattern)
                                    }
                                }, enabled = !generationRunning)
                                if (bAdvancedOptions) {
                                    BBlockAdvancedControls(
                                        block = block,
                                        enabled = !generationRunning,
                                        onChange = { next ->
                                            bBlocks = bBlocks.toMutableList().also { it[blockIndex] = next }
                                        }
                                    )
                                }
                                if (blockIndex < bBlocks.lastIndex) {
                                    ProgramOptionRow {
                                        ProgramDropdown(
                                            modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                            label = "다음 블록 전 회복 기간",
                                            selected = bRecoveryBetween[blockIndex],
                                            options = (0..7).toList(),
                                            optionLabel = { if (it == 0) "없음" else it.toString() + "일" },
                                            onSelect = { days ->
                                                bRecoveryBetween = bRecoveryBetween.toMutableList().also {
                                                    it[blockIndex] = days
                                                }
                                            },
                                            enabled = !generationRunning
                                        )
                                    }
                                }
                            }
                            Text(
                                "전체 " + (bBlocks.sumOf { it.weeks * 7 } + bRecoveryBetween.sum()) +
                                    "일 · 블록별 운동 재선정 및 회복 기간을 포함합니다.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text("4주 블록 · 볼륨 주기화", style = MaterialTheme.typography.titleSmall)
                            BBlockPatternPicker(
                                weeks = 4, selected = bBlockPattern,
                                onSelect = { bBlockPattern = it }, enabled = !generationRunning
                            )
                            if (bAdvancedOptions) {
                                BBlockAdvancedControls(
                                    block = bBasicAdvancedBlock,
                                    onChange = { bBasicAdvancedBlock = it },
                                    enabled = !generationRunning
                                )
                            }
                        }
                        Text(
                            text = "기본 4주 · 중요도 0은 기록 추천입니다. 4일 분산은 필요할 때만 적용하고, 삭제 후보는 감사 정보로만 기록합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !generationRunning,
                            onClick = { generateV22() }
                        ) {
                            Text(if (v22SkeletonCreated) "4주 기본 프로그램 다시 만들기" else "4주 기본 프로그램 만들기")
                        }
                    } else {
                        Text(
                            text = "기존 구성은 아래 목록에서 수정합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (lastGenerationWasV22 && buildProgress !is ProgramBuildProgressState.Idle) {
            item {
                ProgramBuildProgressCard(
                    progress = buildProgress,
                    onRetry = {
                        generateV22()
                    }
                )
            }
        }
        personalizedDraft?.let { currentSkeleton ->
            if (lastGenerationWasV22) item {
                InfoCard("볼륨 주기화와 RPE는 참고 계획입니다. 통증·피로·수행 여유에 맞춰 사용자가 중량·세트·반복을 조절하세요.")
            }
            if (lastGenerationWasV22 && squatShadowComparison != null) item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showSquatShadowComparison = true }
                ) { Text("백스쿼트 생성 결과와 Shadow 비교") }
                Text(
                    "스쿼트 단기 실험 결과이며, 타 운동 진행은 아직 자동 선정하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (lastGenerationWasV22 && otherExerciseShadowCoverage.isNotEmpty()) item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showOtherExerciseShadowCoverage = true }
                ) { Text("타 운동 Shadow 근거·진행 후보 점검") }
                Text(
                    "운동 유형별 적용 가능성과 미확정 사항만 표시합니다. 자동 증량이나 저장 변경은 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (currentSkeleton.planningMetadataJson?.contains("\"reselectionAudit\"") == true) item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showBReselectionLog = true }
                ) { Text("운동 선정 로그 자세히 보기") }
            }
            if (currentSkeleton.warnings.any { it.startsWith(PROGRAM_ITEM_RESTORE_WARNING) }) {
                item {
                    InfoCard("일부 프로그램 메타데이터를 복원하지 못했습니다. 저장 전 구성을 확인하세요.")
                }
            }
            item {
                ProgramSkeletonPreview(
                    skeleton = currentSkeleton,
                    exercises = exercises,
                    metadataByExerciseId = runtimeMetadataByExerciseId,
                    muscleMetadataByKey = muscleMetadataByKey,
                    progressionEligibleKeys = progressionEligibleKeys,
                    onSkeletonChange = { nextDraft ->
                        squatShadowComparison = null // comparing an edited draft with the old shadow is invalid
                        otherExerciseShadowCoverage = emptyList() // edited plans invalidate coverage evidence
                        val previousDraft = personalizedDraft
                        // Default equipment units work even before onboarding;
                        // current visible entries take precedence over older profile state.
                        val units = runCatching {
                            BBlockEquipmentIncrements(
                                smallestPlateText.toDoubleOrNull() ?: initialProfile?.smallestPlateKg ?: 1.25,
                                dumbbellUnitText.toDoubleOrNull() ?: initialProfile?.dumbbellIncrementKg ?: 1.0,
                                machineUnitText.toDoubleOrNull() ?: initialProfile?.machineIncrementKg ?: 2.5
                            )
                        }.getOrNull()
                        val propagated = if (previousDraft != null && lastGenerationWasV22 && units != null)
                            nextDraft.propagateBBlockStartLoad(previousDraft, units)
                            else nextDraft
                        val autoChanged = propagated.items.filter { item ->
                            nextDraft.items.firstOrNull { it.localId == item.localId }
                                ?.setPrescriptions?.map { it.weightKg } !=
                                item.setPrescriptions.map { it.weightKg }
                        }.mapTo(linkedSetOf()) { it.localId }
                        val overrides = previousDraft?.items.orEmpty().count { item ->
                            item.localId in autoChanged && item.weightSource == "B_BLOCK_USER_OVERRIDE"
                        }
                        if (overrides > 0) {
                            pendingLoadOverwriteCount = overrides
                            pendingLoadOverwrite = propagated
                        } else personalizedDraft = propagated
                    }
                )
            }
        }
        if (personalizedDraft == null) item {
            InfoCard(
                if (program == null) {
                    "빈 프로그램으로 시작하거나 4주 기본 프로그램을 만들면 저장 전 미리보기와 수정 항목이 표시됩니다."
                } else {
                    "아직 저장된 운동 구성이 없습니다."
                }
            )
        }
        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = initialDraftLoaded && !generationRunning &&
                    (personalizedDraft != null || nameText.isNotBlank()),
                onClick = {
                    viewModel.saveProgramDraft(currentSnapshot) { success ->
                        Toast.makeText(
                            context,
                            if (success) "임시저장했습니다." else "임시저장에 실패했습니다. 다시 시도해 주세요.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Text("임시저장")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                ) {
                    Text("취소")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = hasDraftItems && !generationRunning,
                    onClick = {
                        if (!requireProgramName()) return@Button
                        val current = personalizedDraft ?: return@Button
                        val name = normalizedProgramName()
                        viewModel.saveGeneratedProgram(
                            existingProgramId = program?.id,
                            skeleton = current.copy(
                                suggestedName = name,
                                request = current.request.copy(name = name)
                            ),
                            onSaved = { programId ->
                                finalized.value = true
                                viewModel.deleteProgramDraftAfterFinalSave(draftId)
                                onSaved(programId)
                            }
                        )
                    }
                ) {
                    Text("저장")
                }
            }
        }
    }
}

@Composable
internal fun ProgramBuildProgressCard(
    progress: ProgramBuildProgressState,
    onRetry: () -> Unit
) {
    when (progress) {
        ProgramBuildProgressState.Idle -> Unit
        is ProgramBuildProgressState.Running -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                    Text("잠시만 기다려 주세요.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        is ProgramBuildProgressState.Completed -> {
            val notices = progress.summary.notices
            if (notices.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.program_result_heading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        notices.forEach { notice ->
                            ProgramUserNoticeRow(notice)
                        }
                    }
                }
            }
        }
        is ProgramBuildProgressState.Failed -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onRetry) {
                        Text("다시 시도")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramUserNoticeRow(notice: ProgramUserNotice) {
    val context = LocalContext.current
    val levelLabel = when (notice.level) {
        ProgramUserNoticeLevel.SUCCESS -> stringResource(R.string.program_notice_success_description)
        ProgramUserNoticeLevel.INFO -> stringResource(R.string.program_notice_info_description)
        ProgramUserNoticeLevel.WARNING -> stringResource(R.string.program_notice_warning_description)
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = levelLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = context.programUserNoticeText(notice),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProgramDetailScreen(
    program: TrainingProgram,
    viewModel: TrainingViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val displayName = localizedProgramName(program)
    val context = LocalContext.current
    val items by remember(program.id) {
        viewModel.programItems(program.id)
    }.collectAsState(initial = emptyList())
    val itemSets by remember(program.id) {
        viewModel.programItemSets(program.id)
    }.collectAsState(initial = emptyList())
    val exercises by viewModel.exercises.collectAsState()
    val runtimeMetadata by viewModel.exerciseRuntimeMetadata.collectAsState()
    val muscleMetadataByKey by viewModel.exerciseMuscleMetadata.collectAsState()
    // Subscribe once per program screen, not twice for every visible exercise row.
    val progressionBindings by viewModel.progressionItems.collectAsState(emptyList())
    val progressionTracks by viewModel.progressionTracks.collectAsState(emptyList())
    val dayItemsByKey = remember(items) {
        items.groupBy { it.weekNumber to it.dayOfWeek }
            .mapValues { (_, rows) ->
                rows.sortedWith(compareBy<TrainingProgramItem> { it.orderIndex }.thenBy { it.id })
            }
    }
    val setsByItemId = remember(itemSets) {
        itemSets.groupBy(TrainingProgramItemSet::programItemId)
    }
    val availableExerciseKeys = remember(exercises) {
        exercises.mapTo(mutableSetOf()) { it.stableKey }
    }
    val progressionIndex = remember(items, setsByItemId, progressionBindings, progressionTracks) {
        ProgramProgressionDisplayIndex.build(items, setsByItemId, progressionBindings, progressionTracks)
    }
    val metadataTranslator = rememberMetadataTranslator()
    var infoExerciseKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val groupedKeys = remember(items) {
        items.map { it.weekNumber to it.dayOfWeek }
            .distinct()
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
    }

    infoExerciseKey?.let { stableKey ->
        exercises.firstOrNull { it.stableKey == stableKey }?.let { exercise ->
            ExerciseInfoDialog(
                exercise = exercise,
                metadata = runtimeMetadata[stableKey],
                muscleMetadata = muscleMetadataByKey[stableKey],
                onEditMetadata = null,
                onDismiss = { infoExerciseKey = null }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("운동 프로그램 삭제") },
            text = { Text("이 운동 프로그램을 삭제할까요? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteProgram(
                            program.id,
                            onDeleted = {
                                Toast.makeText(context, "운동 프로그램을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                                onDeleted()
                            },
                            onError = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("취소")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onBack
                ) {
                    Text("목록")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onEdit
                ) {
                    Text("수정")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { confirmDelete = true }
                ) {
                    Text("삭제")
                }
            }
        }
        item {
            ScreenHeader(
                title = displayName,
                body = "주차와 요일별 운동 구성을 텍스트 요약으로 확인합니다."
            )
        }
        // A full day used to be one LazyColumn item. Rendering a long day's
        // every interactive exercise card during one fling caused frame stalls.
        groupedKeys.forEach { key ->
            item(key = "day-${key.first}-${key.second}", contentType = "day-header") {
                ProgramDaySummaryHeader(key.first, key.second)
            }
            items(
                items = dayItemsByKey[key].orEmpty(),
                key = { exercise -> "program-item-${exercise.id}" },
                contentType = { "program-exercise" }
            ) { exercise ->
                ProgramDaySummaryExerciseCard(
                    item = exercise,
                    storedSets = setsByItemId[exercise.id].orEmpty(),
                    onExerciseInfo = { stableKey -> infoExerciseKey = stableKey },
                    availableExerciseKeys = availableExerciseKeys,
                    translator = metadataTranslator,
                    progressionControl = { itemId ->
                        ProgressionTrackControl(itemId, progressionIndex, viewModel::configureProgression)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgramApplyCard(
    program: TrainingProgram,
    viewModel: TrainingViewModel,
    onApplied: () -> Unit
) {
    var startDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var conflictSummary by remember { mutableStateOf<ProgramApplyConflictSummary?>(null) }
    var applying by remember { mutableStateOf(false) }
    var applyError by remember { mutableStateOf<String?>(null) }
    val canApply = runCatching { LocalDate.parse(startDateText) }.isSuccess

    fun apply(mode: ProgramApplyMode) {
        if (applying) return
        applying = true
        applyError = null
        viewModel.applyProgram(program.id, startDateText, mode) { error ->
            applying = false
            if (error == null) {
                conflictSummary = null
                onApplied()
            } else {
                conflictSummary = null
                applyError = error
            }
        }
    }

    conflictSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { conflictSummary = null },
            title = { Text("기존 계획이 있습니다") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("기간: ${summary.startDate} ~ ${summary.endDate}")
                    Text("기존 계획: ${summary.existingEntryCount}개")
                    Text("새 계획: ${summary.newPlannedEntryCount}개")
                    Text("보존되는 완료 세트: ${summary.existingConfirmedSetCount}세트")
                    Text(
                        text = "덮어쓰기는 미확인 계획만 삭제합니다. 실제 완료 기록은 삭제되지 않습니다.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { conflictSummary = null }) {
                        Text("취소")
                    }
                    Button(enabled = !applying, onClick = { apply(ProgramApplyMode.Overwrite) }) {
                        Text("덮어쓰기")
                    }
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "날짜에 적용",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            InlineDateSwitcher(
                dateText = startDateText,
                onDateChange = {
                    if (!applying) {
                        startDateText = it
                        applyError = null
                    }
                }
            )
            applyError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (applying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("계획을 적용하는 중입니다.", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canApply && !applying,
                onClick = {
                    if (applying) return@Button
                    applying = true
                    applyError = null
                    viewModel.loadProgramApplyConflictSummary(
                        programId = program.id,
                        startDate = startDateText,
                        onResult = { summary ->
                        applying = false
                        if (summary.hasExistingEntries) {
                            conflictSummary = summary
                        } else {
                            apply(ProgramApplyMode.Append)
                        }
                        },
                        onError = { error ->
                            applying = false
                            applyError = error
                        }
                    )
                }
            ) {
                Text("시작일 적용")
            }
        }
    }
}

internal fun skeletonFromProgram(snapshot: ProgramEditorSnapshot): GeneratedProgramSkeleton {
    val program = snapshot.program
    val items = snapshot.items
    val itemSets = snapshot.sets
    val restoredItems = items.map { item ->
        item to ProgramItemRestoreMetadataParser.resolve(item)
    }
    val request = ProgramSkeletonRequest(
        name = program.name,
        goal = ProgramGoal.BADMINTON_SUPPORT,
        weeklyTrainingDays = (program.weeklyTrainingDays.takeIf { it > 0 } ?: 3),
        sessionMinutes = (program.sessionMinutes.takeIf { it > 0 } ?: 60),
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = program.badmintonTransferRatio.coerceIn(0.0, 0.90),
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = ((program.durationDays.coerceAtLeast(1) + 6) / 7).coerceAtLeast(1)
    )
    val durationWeeks = ((program.durationDays.coerceAtLeast(1) + 6) / 7).coerceAtLeast(1)
    val setsByItemId = itemSets.groupBy(TrainingProgramItemSet::programItemId)
    val weekPlans = restoredBBalancedWeekPlans(program.planningMetadataJson, durationWeeks) ?: (1..durationWeeks).map { week ->
        ProgramWeekPlan(
            weekIndex = week,
            weekType = if (week % 4 == 0) "기존 디로드" else "기존 구성",
            volumeMultiplier = if (week % 4 == 0) 0.70 else 1.0,
            intensityMultiplier = if (week % 4 == 0) 0.75 else 0.90,
            heavyExposureLimit = if (week % 4 == 0) 1 else 2,
            lowerBodyFatigueLimit = if (week % 4 == 0) 5.0 else 8.0,
            axialLoadLimit = if (week % 4 == 0) 1 else 2,
            plyometricLimit = if (week % 4 == 0) 0 else 1,
            deloadFlag = week % 4 == 0
        )
    }
    return GeneratedProgramSkeleton(
        suggestedName = program.name,
        durationDays = program.durationDays,
        request = request,
        periodizationType = ProgramPeriodizationType.AUTO,
        weekPlans = weekPlans,
        items = restoredItems.map { (item, parseResult) ->
            val metadata = parseResult.metadata
            ProgramSkeletonItem(
                localId = "existing-${item.id}",
                weekNumber = item.weekNumber,
                dayOfWeek = item.dayOfWeek,
                orderIndex = item.orderIndex,
                exerciseStableKey = item.exerciseStableKey,
                exerciseName = item.exerciseName,
                category = item.category,
                restSeconds = item.restSeconds,
                prescription = item.prescription,
                setCount = item.setCount,
                reps = item.reps,
                weightKg = item.weightKg,
                seconds = item.seconds,
                selectionReason = "기존 프로그램",
                weightSource = metadata.weightSource,
                selectionRole = item.selectionRole.orEmpty(),
                trainingSlot = metadata.trainingSlot,
                dayIntensity = metadata.dayIntensity,
                setPrescriptions = ProgramSetPrescriptionResolver.resolve(
                    item,
                    setsByItemId[item.id].orEmpty()
                ),
                taskProtocolSemanticsJson = item.taskProtocolSemanticsJson
            )
        },
        planningMetadataJson = program.planningMetadataJson,
        warnings = buildList {
            if (program.planningMetadataJson?.contains("B_BALANCED_12_WEEK_V1") == true) {
                add("B_BALANCED_12_WEEK_V1_RESTORED_WITHOUT_SYNTHETIC_DELOAD")
            }
            if (restoredItems.any { (_, result) -> result.legacyFields.isNotEmpty() }) {
                add("$PROGRAM_ITEM_LEGACY_RESTORE_WARNING: legacy prescription metadata used")
            }
            if (restoredItems.any { (_, result) -> result.unresolvedFields.isNotEmpty() }) {
                add("$PROGRAM_ITEM_RESTORE_WARNING: fallback metadata used")
            }
        }
    ).hydrateProgression(snapshot)
}

private const val PROGRAM_ITEM_RESTORE_WARNING = "PROGRAM_ITEM_RESTORE_METADATA_FALLBACK"
private const val PROGRAM_ITEM_LEGACY_RESTORE_WARNING = "PROGRAM_ITEM_RESTORE_LEGACY_FALLBACK"
