package com.training.trackplanner

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn

import android.widget.Toast
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
import com.training.trackplanner.data.v22.newBBalancedGenerationRequest
import com.training.trackplanner.data.v22.restoredBBalancedWeekPlans
import com.training.trackplanner.localization.localizedProgramName
import java.time.LocalDate
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
                onCancel = {
                    creatingProgram = false
                    editingProgramId = null
                },
                onSaved = { programId ->
                    creatingProgram = false
                    editingProgramId = null
                    selectedProgramId = programId
                }
            )
        }
        selectedProgram == null -> {
        ProgramListScreen(
            programs = programs,
                viewModel = viewModel,
                onCreateProgram = { creatingProgram = true },
                onSelectProgram = { selectedProgramId = it.id },
                onEditProgram = { editingProgramId = it.id },
                onDeleteProgram = { program ->
                    viewModel.deleteProgram(program.id) {
                        if (selectedProgramId == program.id) selectedProgramId = null
                    }
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
                onEdit = { editingProgramId = selectedProgram.id },
                onDeleted = { selectedProgramId = null }
        )
        }
    }
}
@Composable
private fun ProgramListScreen(
    programs: List<TrainingProgram>,
    viewModel: TrainingViewModel,
    onCreateProgram: () -> Unit,
    onSelectProgram: (TrainingProgram) -> Unit,
    onEditProgram: (TrainingProgram) -> Unit,
    onDeleteProgram: (TrainingProgram) -> Unit,
    onShareProgram: (TrainingProgram) -> Unit,
    onOpenRecord: () -> Unit,
    tutorialApplyRequest: Int,
    showOnboardingApplyTarget: Boolean,
    onApplyTargetPositioned: (Rect) -> Unit
) {
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<TrainingProgram?>(null) }
    var applyTarget by remember { mutableStateOf<TrainingProgram?>(null) }
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
                        onDeleteProgram(program)
                        deleteTarget = null
                        Toast.makeText(context, "운동 프로그램을 삭제했습니다.", Toast.LENGTH_SHORT).show()
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
    onCancel: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val exercises by viewModel.exercises.collectAsState()
    val runtimeMetadataByExerciseId by viewModel.exerciseRuntimeMetadata.collectAsState()
    val muscleMetadataByKey by viewModel.exerciseMuscleMetadata.collectAsState()
    var progressionContext by remember { mutableStateOf<com.training.trackplanner.data.ProgressionDraftContext?>(null) }
    val progressionEligibleKeys = progressionContext?.eligibleKeys.orEmpty()
    var nameText by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(program?.name ?: "")
    }
    var nameError by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(false)
    }
    var durationWeeks by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(
            program?.let { ((it.durationDays.coerceAtLeast(1) + 6) / 7).coerceAtLeast(1) } ?: 4
        )
    }
    var weeklyDays by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.weeklyTrainingDays ?: 3).takeIf { it > 0 } ?: 3)
    }
    var blankSessionMinutes by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.sessionMinutes ?: 60).takeIf { it > 0 } ?: 60)
    }
    var v22SessionMinutes by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.sessionMinutes ?: 60).takeIf { it > 0 } ?: 60)
    }
    var personalizedDraft by remember(program?.id) {
        mutableStateOf<GeneratedProgramSkeleton?>(null)
    }
    val hasDraftItems = personalizedDraft?.items?.isNotEmpty() == true
    val buildProgress by viewModel.programBuildProgress.collectAsState()
    val generationRunning = buildProgress is ProgramBuildProgressState.Running
    var lastGenerationWasV22 by rememberSaveable { mutableStateOf(false) }
    var v22Days by rememberSaveable(program?.id ?: 0L) { mutableStateOf(4) }
    var bAllowFourthDay by rememberSaveable(program?.id ?: 0L) { mutableStateOf(true) }
    var bStrengthImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var bHypertrophyImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var bCoreImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var bSportImportance by rememberSaveable(program?.id ?: 0L) { mutableStateOf(0) }
    var v22SkeletonCreated by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }

    LaunchedEffect(program?.id) {
        if (program != null && personalizedDraft == null) personalizedDraft = skeletonFromProgram(viewModel.programEditorSnapshot(program.id)).withResolvedWeekDaySchedule()
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

    fun blankProgramRequest(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = normalizedProgramName(),
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = weeklyDays,
            sessionMinutes = blankSessionMinutes,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = durationWeeks
        )

    fun startBlankProgram() {
        if (!requireProgramName()) return
        if (viewModel.programBuildProgress.value is ProgramBuildProgressState.Running) return
        lastGenerationWasV22 = false
        v22SkeletonCreated = false
        personalizedDraft = emptyProgramSkeleton(
            request = blankProgramRequest(),
            weekDaySchedule = defaultProgramWeekDaySchedule(durationWeeks, weeklyDays)
        )
    }

    fun generateV22() {
        if (!requireProgramName()) return
        if (viewModel.programBuildProgress.value is ProgramBuildProgressState.Running) return
        lastGenerationWasV22 = true
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
                )
            )
        ) { generated ->
            personalizedDraft = generated.skeleton.withResolvedWeekDaySchedule()
            v22SkeletonCreated = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = if (program == null) "프로그램 새로 만들기" else "프로그램 수정",
                body = "빈 프로그램을 직접 구성하거나 12주 균형형 프로그램을 만든 뒤, 저장 전에 세트와 중량을 조정합니다."
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
                        enabled = !generationRunning,
                        supportingText = {
                            if (nameError) Text("프로그램명을 입력하세요.")
                        },
                        singleLine = true
                    )
                    if (program == null) {
                        Text(
                            text = "빈 프로그램 직접 구성",
                            style = MaterialTheme.typography.titleMedium
                        )
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "프로그램 기간",
                                selected = durationWeeks,
                                options = (3..8).toList(),
                                optionLabel = { programWeekLabel(it) },
                                onSelect = { durationWeeks = it },
                                enabled = !generationRunning
                            )
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "주당 운동일수",
                                selected = weeklyDays,
                                options = (3..7).toList(),
                                optionLabel = { stringResource(R.string.program_days_per_week, it) },
                                onSelect = { weeklyDays = it },
                                enabled = !generationRunning
                            )
                        }
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "하루 운동시간",
                                selected = blankSessionMinutes,
                                options = listOf(30, 45, 60),
                                optionLabel = { stringResource(R.string.program_minutes, it) },
                                onSelect = { blankSessionMinutes = it },
                                enabled = !generationRunning
                            )
                        }
                        Text(
                            text = "기간과 운동일수에 따라 빈 계획을 만듭니다. 운동 종목은 직접 추가하세요.",
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
                            text = "12주 균형형 운동 프로그램",
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
                        Text(
                            text = "12주 고정 · 중요도 0은 기록 추천입니다. 4일 분산은 필요할 때만 적용하고, 삭제 후보는 감사 정보로만 기록합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !generationRunning,
                            onClick = { generateV22() }
                        ) {
                            Text(if (v22SkeletonCreated) "12주 균형형 프로그램 다시 만들기" else "12주 균형형 프로그램 만들기")
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
                    onSkeletonChange = { personalizedDraft = it }
                )
            }
        }
        if (personalizedDraft == null) item {
            InfoCard(
                if (program == null) {
                    "빈 프로그램으로 시작하거나 12주 균형형 프로그램을 만들면 저장 전 미리보기와 수정 항목이 표시됩니다."
                } else {
                    "아직 저장된 운동 구성이 없습니다."
                }
            )
        }
        item {
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
                            onSaved = onSaved
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
                        viewModel.deleteProgram(program.id) {
                            Toast.makeText(context, "운동 프로그램을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        }
                        confirmDelete = false
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
        items(groupedKeys, key = { "${it.first}-${it.second}" }) { key ->
            val dayItems = items
                .filter { it.weekNumber == key.first && it.dayOfWeek == key.second }
                .sortedWith(compareBy<TrainingProgramItem> { it.orderIndex }.thenBy { it.id })
            ProgramDaySummarySection(
                weekNumber = key.first,
                dayOfWeek = key.second,
                items = dayItems,
                setsByItemId = itemSets.groupBy(TrainingProgramItemSet::programItemId),
                onExerciseInfo = { stableKey -> infoExerciseKey = stableKey },
                availableExerciseKeys = exercises.mapTo(mutableSetOf()) { it.stableKey },
                progressionControl = { itemId -> ProgressionTrackControl(itemId, viewModel, items, itemSets) }
            )
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
    val canApply = runCatching { LocalDate.parse(startDateText) }.isSuccess

    fun apply(mode: ProgramApplyMode) {
        viewModel.applyProgram(program.id, startDateText, mode)
        conflictSummary = null
        onApplied()
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
                    Button(onClick = { apply(ProgramApplyMode.Overwrite) }) {
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
                onDateChange = { startDateText = it }
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canApply,
                onClick = {
                    viewModel.loadProgramApplyConflictSummary(
                        programId = program.id,
                        startDate = startDateText
                    ) { summary ->
                        if (summary.hasExistingEntries) {
                            conflictSummary = summary
                        } else {
                            apply(ProgramApplyMode.Append)
                        }
                    }
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
