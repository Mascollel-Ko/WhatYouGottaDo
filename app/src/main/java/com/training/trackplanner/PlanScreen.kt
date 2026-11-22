package com.training.trackplanner

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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
import com.training.trackplanner.data.v22.BCoachMultiobjectiveProposal
import com.training.trackplanner.data.v22.BCoachPlanAlternative
import com.training.trackplanner.data.v22.BCoachV2Policy
import com.training.trackplanner.data.v22.BCoachConstraintStatus
import com.training.trackplanner.data.v22.BCoachProductionStatus
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.training.trackplanner.data.ProgramLoadState
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BCoachProductionReviewDialog(
    changes: List<String>,
    unresolvedCodes: List<String>,
    unreliableExerciseCount: Int,
    alternatives: List<BCoachPlanAlternative> = emptyList(),
    selectedCandidateId: String? = alternatives.firstOrNull { it.recommended }?.candidateId,
    candidateForReview: (String) -> GeneratedProgramSkeleton? = { null },
    sportShadowAvailable: Boolean = false,
    onOpenSportShadow: () -> Unit = {},
    onSelectCandidate: (String) -> Unit = {},
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedAlternative = alternatives.firstOrNull { it.candidateId == selectedCandidateId }
    val selectedPlan = selectedCandidateId?.let(candidateForReview)
    val recommendedPlan = alternatives.firstOrNull { it.recommended }?.candidateId
        ?.let(candidateForReview)
    fun comparedToRecommended(candidateId: String): String {
        val plan = candidateForReview(candidateId) ?: return "후보 세부 정보 없음"
        if (candidateId == alternatives.firstOrNull { it.recommended }?.candidateId)
            return "목표 훈련량과 회복 부담을 함께 고려해 추천"
        val reference = recommendedPlan ?: return "추천안 비교 불가"
        fun visible(item: ProgramSkeletonItem?): String = item?.let { exercise ->
            exercise.orderIndex.toString() + "/" + exercise.restSeconds + ":" +
                ProgramSetPrescriptionResolver.resolve(exercise).joinToString(";") { set ->
                    "${set.reps}/${set.seconds}/${set.loadState}/" +
                        "${(set.weightKg * 10).toInt()}/" +
                        "${set.estimatedLoadKg?.let { (it * 10).toInt() }}/" +
                        "${set.targetRpeMin?.let { (it * 10).toInt() }}/" +
                        "${set.expectedRir?.let { (it * 10).toInt() }}"
                }
        } ?: "없음"
        fun slots(skeleton: GeneratedProgramSkeleton) = skeleton.items.associateBy {
            Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
        }
        val prior = slots(reference)
        val later = slots(plan)
        val differences = (prior.keys + later.keys).toSet()
            .filter { key -> visible(prior[key]) != visible(later[key]) }
            .sortedWith(compareBy<Triple<Int, Int, String>> { it.first }
                .thenBy { it.second }.thenBy { it.third })
        val first = differences.firstOrNull()
        return if (first == null) "추천안과 실제 운동 처방 동일" else {
            val oldItem = prior[first]
            val newItem = later[first]
            val label = newItem?.exerciseName ?: oldItem?.exerciseName ?: "운동"
            val change = if (oldItem?.setCount != newItem?.setCount)
                "${oldItem?.setCount ?: 0}→${newItem?.setCount ?: 0}세트"
            else "세트별 반복·중량·휴식 변경"
            "추천안 대비 ${differences.size}곳 차이 · ${first.first}주 ${first.second}요일 $label $change"
        }
    }
    val weeks = selectedPlan?.items.orEmpty().map(ProgramSkeletonItem::weekNumber).distinct().sorted()
    var selectedWeek by remember(selectedCandidateId, weeks) { mutableStateOf(weeks.firstOrNull()) }
    val days = selectedPlan?.items.orEmpty().filter { it.weekNumber == selectedWeek }
        .map(ProgramSkeletonItem::dayOfWeek).distinct().sorted()
    var selectedDay by remember(selectedCandidateId, selectedWeek, days) { mutableStateOf(days.firstOrNull()) }
    var showDiagnostics by remember(selectedCandidateId) { mutableStateOf(false) }
    fun changeText(code: String): String {
        val fields = code.substringAfter(':', "").split(':')
        val slot = fields.firstOrNull()?.split('/').orEmpty()
        val location = slot.takeIf { it.size >= 2 }?.let { "${it[0]}주 ${it[1]}요일" }.orEmpty()
        val key = fields.getOrNull(1)
        val exerciseName = selectedPlan?.items?.firstOrNull { it.exerciseStableKey == key }?.exerciseName
        val exercise = exerciseName ?: "운동"
        return when {
            code.startsWith("ADD:") || code.startsWith("INSERT:") -> "$location · $exercise 추가"
            code.startsWith("REMOVE:") || code.startsWith("REMOVE_EXERCISE:") -> "$location · 기존 운동 제외"
            code.startsWith("PRESCRIPTION:") -> "$location · $exercise 세트·반복 조정"
            code.startsWith("ADD_SET:") -> "$location · $exercise 세트 추가"
            code.startsWith("REMOVE_SET:") -> "$location · $exercise 세트 감소"
            code.startsWith("MOVE:") -> "$exercise 훈련 요일 조정"
            code.startsWith("WEEKDAYS:") -> "주간 훈련 요일 조정"
            else -> "계획 세부 조정"
        }
    }
    fun loadText(set: com.training.trackplanner.data.ProgramSetPrescription): String = when {
        set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && set.estimatedLoadKg != null ->
            "약 ${"%.1f".format(set.estimatedLoadKg)}kg · 장비 단위 확인 필요"
        set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED -> "중량 직접 확인 필요"
        set.loadState == ProgramLoadState.NOT_APPLICABLE -> "중량 적용 안 함"
        else -> "${"%.1f".format(set.weightKg)}kg"
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize().testTag("b_coach_review_dialog")) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                Text("B-Coach 전체 블록 후보 검토", style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("b_coach_review_content"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Column {
                Text(BCoachV2Policy.DISCLAIMER)
                Text(
                    "아래 추천안과 대안은 모든 주차의 시간·요일 제약을 통과한 실제 저장 후보입니다. " +
                        "선택한 안은 승인 전까지 저장되지 않습니다." +
                        (unreliableExerciseCount.takeIf { it > 0 }
                            ?.let { " 기록 부족 운동 ${it}개가 포함됩니다." } ?: ""),
                    modifier = Modifier.padding(top = 8.dp)
                )
                  }
                if (sportShadowAvailable) Column {
                    OutlinedButton(
                        onClick = onOpenSportShadow,
                        modifier = Modifier.fillMaxWidth().testTag("b_coach_review_open_sport_shadow")
                    ) { Text("후보별 스포츠 훈련량 비교") }
                }
                if (alternatives.isNotEmpty()) Column {
                    Text("계획 선택", modifier = Modifier.padding(top = 12.dp))
                    Column(Modifier.fillMaxWidth()) {
                    if (alternatives.size == 1) {
                        Text("검증된 후보 중 실질적으로 다른 처방이 없어 추천안만 표시합니다.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    alternatives.forEachIndexed { index, alternative ->
                        val selected = alternative.candidateId == selectedCandidateId
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectCandidate(alternative.candidateId) },
                                    modifier = Modifier.testTag("b_coach_candidate_${alternative.candidateId}")
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (alternative.recommended) "추천안 A"
                                        else "대안 ${('A'.code + index).toChar()}"
                                    )
                                    Text(
                                        comparedToRecommended(alternative.candidateId) +
                                            " · 주당 평균 ${"%.1f".format(alternative.weeklySetAverage)}세트",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                        }
                    }
                    }
                }
                selectedPlan?.let { plan ->
                    Column {
                        val weeklySets = plan.items.sumOf { ProgramSetPrescriptionResolver.resolve(it).size }
                            .toDouble() / plan.request.durationWeeks.coerceAtLeast(1)
                        val trainingDays = plan.weekDaySchedule.values.flatten().distinct().sorted()
                        val restDays = (1..7).filterNot(trainingDays::contains)
                        val estimatedMinutes = plan.items.groupBy { it.weekNumber to it.dayOfWeek }.values
                            .map { session -> session.sumOf { item ->
                                ProgramSetPrescriptionResolver.resolve(item).size * (item.restSeconds + 45)
                            } / 60.0 }.average().toInt().coerceAtLeast(1)
                        val weekStages = plan.weekPlans.groupBy { it.weekType }.entries.joinToString(" · ") {
                            (type, rows) -> "${rows.minOf { it.weekIndex }}~${rows.maxOf { it.weekIndex }}주 $type"
                        }
                        Text("${plan.request.durationWeeks}주 · 주 ${plan.request.weeklyTrainingDays}일 · " +
                            "주당 평균 ${"%.1f".format(weeklySets)}세트")
                        Text("훈련 요일: ${trainingDays.joinToString(" · ") { "${it}요일" }}")
                        Text("휴식 요일: ${restDays.joinToString(" · ") { "${it}요일" }}")
                        Text("세션 목표 ${plan.request.sessionMinutes}분 · 예상 약 ${estimatedMinutes}분(세트·휴식 기준)")
                        if (weekStages.isNotBlank()) Text("주차 단계: $weekStages")
                    }
                    Column {
                        Text("주차", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            weeks.forEach { week ->
                                if (week == selectedWeek) Button({ selectedWeek = week }) { Text("${week}주") }
                                else OutlinedButton({ selectedWeek = week }) { Text("${week}주") }
                            }
                        }
                        val daySets = plan.items.filter { it.weekNumber == selectedWeek && it.dayOfWeek == selectedDay }
                            .sumOf { ProgramSetPrescriptionResolver.resolve(it).size }
                        Text("요일 · 선택한 날 총 ${daySets}세트", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            days.forEach { day ->
                                if (day == selectedDay) Button({ selectedDay = day }) { Text("${day}요일") }
                                else OutlinedButton({ selectedDay = day }) { Text("${day}요일") }
                            }
                        }
                    }
                    plan.items.filter { it.weekNumber == selectedWeek && it.dayOfWeek == selectedDay }
                            .sortedBy(ProgramSkeletonItem::orderIndex).forEach { exercise ->
                        Card(Modifier.fillMaxWidth().testTag("b_coach_review_exercise_${exercise.localId}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(exercise.exerciseName, fontWeight = FontWeight.Bold)
                                val purpose = when {
                                    "PEAK" in exercise.selectionRole -> "수행 능력 확인"
                                    "MAIN" in exercise.selectionRole -> "주요 근력 훈련"
                                    exercise.directSportSession -> "스포츠 보조 훈련"
                                    else -> exercise.category
                                }
                                if (purpose.isNotBlank()) Text(purpose, style = MaterialTheme.typography.bodySmall)
                                ProgramSetPrescriptionResolver.resolve(exercise).forEach { set ->
                                    val effort = set.expectedRir?.let { "예상 RIR ${"%.1f".format(it)}" }
                                        ?: set.targetRpeMin?.let { "목표 RPE ${"%.1f".format(it)}" }
                                        ?: "RIR/RPE 확인 필요"
                                    val volume = when {
                                        set.seconds > 0 -> "${set.seconds}초"
                                        set.reps > 0 -> "${set.reps}회"
                                        else -> "수행 시간·거리 확인 필요"
                                    }
                                    Text("${set.setIndex}세트 · $volume · ${loadText(set)} · $effort · 휴식 ${exercise.restSeconds}초",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                val displayedChanges = selectedAlternative?.changeSummary?.ifEmpty { changes } ?: changes
                if (displayedChanges.isNotEmpty()) Column {
                    Text("변경 내용", modifier = Modifier.padding(top = 12.dp))
                    displayedChanges.forEach { change -> Text("• ${changeText(change)}") }
                }
                if (unresolvedCodes.isNotEmpty()) Column {
                    Text("주의사항", modifier = Modifier.padding(top = 12.dp))
                    Text("• 자동 판단이 필요한 항목 ${unresolvedCodes.distinct().size}개를 보수적으로 처리했습니다.")
                }
                selectedAlternative?.let { alternative ->
                    Column {
                        TextButton({ showDiagnostics = !showDiagnostics },
                            modifier = Modifier.testTag("b_coach_review_diagnostics")) {
                            Text(if (showDiagnostics) "고급 진단 접기" else "고급 진단 펼치기")
                        }
                        if (showDiagnostics) {
                            Text("후보 ID ${alternative.candidateId}", style = MaterialTheme.typography.bodySmall)
                            Text("robust regret ${"%.3f".format(alternative.robustRegret)} · Pareto ${alternative.paretoFrontier}",
                                style = MaterialTheme.typography.bodySmall)
                            Text(alternative.axisScores.entries.joinToString(" · ") {
                                "${it.key.name}=${"%.2f".format(it.value)}"
                            }, style = MaterialTheme.typography.bodySmall)
                            Text(alternative.scenarioLosses.entries.joinToString(" · ") {
                                "${it.key}=${"%.3f".format(it.value)}"
                            }, style = MaterialTheme.typography.bodySmall)
                            if (unresolvedCodes.isNotEmpty()) Text(
                                "unresolved=${unresolvedCodes.distinct().joinToString(",")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onDismiss, Modifier.fillMaxWidth().testTag("b_coach_review_keep")) {
                        Text("검토 취소 · 이전 초안 유지")
                    }
                    Button(
                        onClick = onApply,
                        modifier = Modifier.fillMaxWidth().testTag("b_coach_review_apply"),
                        enabled = alternatives.isEmpty() || selectedCandidateId != null
                    ) {
                        Text("선택 후보 적용")
                    }
                }
            }
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
    var deleteError by remember { mutableStateOf<String?>(null) }
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

    deleteError?.let { detail ->
        ProgramDeleteErrorDialog(detail) { deleteError = null }
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
                            { message -> deleteError = message }
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
    var bFreeWeightsAvailable by rememberSaveable(program?.id ?: 0L) { mutableStateOf(true) }
    var bMachinesAvailable by rememberSaveable(program?.id ?: 0L) { mutableStateOf(true) }
    var bCourtShuttleAvailable by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }
    var bMarkersAvailable by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }
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
    var pendingBProductionProposal by remember(program?.id) {
        mutableStateOf<BCoachMultiobjectiveProposal?>(null)
    }
    var pendingBPreviousDraft by remember(program?.id, draftId) {
        mutableStateOf<GeneratedProgramSkeleton?>(null)
    }
    var pendingBSelectedCandidateId by remember(program?.id) { mutableStateOf<String?>(null) }
    var lastSportShadowProposal by remember(program?.id, draftId) {
        mutableStateOf<BCoachMultiobjectiveProposal?>(null)
    }
    var showSportShadow by remember(program?.id, draftId) { mutableStateOf(false) }
    var pendingLoadOverwrite by remember(program?.id) {
        mutableStateOf<GeneratedProgramSkeleton?>(null)
    }
    var pendingLoadOverwriteCount by remember { mutableStateOf(0) }
    var showBReselectionLog by remember(program?.id) { mutableStateOf(false) }

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
    LaunchedEffect(personalizedDraft, progressionContext, pendingBProductionProposal) {
        if (pendingBProductionProposal == null) progressionContext?.let { context ->
            personalizedDraft = personalizedDraft?.reconcileProgression(context.eligibleKeys, context.oneRmSnapshots)
        }
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
        pendingBProductionProposal = null
        pendingBSelectedCandidateId = null
        pendingBPreviousDraft = null
        personalizedDraft = emptyProgramSkeleton(
            request = blankProgramRequest(),
            weekDaySchedule = defaultProgramWeekDaySchedule(4, 3)
        )
    }

    fun generateV22() {
        if (!requireProgramName()) return
        if (viewModel.programBuildProgress.value is ProgramBuildProgressState.Running) return
        lastGenerationWasV22 = true
        pendingConservativeBBlock = null
        pendingBReview = null
        pendingBPreviousDraft = personalizedDraft
        pendingBProductionProposal = null
        pendingBSelectedCandidateId = null
        lastSportShadowProposal = null
        showSportShadow = false
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
                availableEquipment = buildSet {
                    addAll(listOf("BODYWEIGHT", "맨몸"))
                    if (bFreeWeightsAvailable) addAll(listOf(
                        "BARBELL", "바벨", "플레이트", "랙", "벤치",
                        "DUMBBELL", "덤벨", "KETTLEBELL", "케틀벨"
                    ))
                    if (bMachinesAvailable) addAll(listOf(
                        "MACHINE", "머신", "케이블", "케이블 머신", "PULL-UP BAR", "철봉"
                    ))
                    if (bCourtShuttleAvailable) addAll(listOf("셔틀콕", "코트"))
                    if (bMarkersAvailable) addAll(listOf("콘", "마커", "콘/마커"))
                },
                blockProgram = blockLayout,
                sourceProgramStableKey = program?.stableKey,
                shadowPlateOneSideKg = smallestPlateText.toDoubleOrNull(),
                shadowDumbbellIncrementKg = dumbbellUnitText.toDoubleOrNull(),
                shadowMachineIncrementKg = machineUnitText.toDoubleOrNull()
            )
        ) { generated ->
            val proposed = generated.skeleton.withResolvedWeekDaySchedule()
            val review = generated.balanced12WeekPlan?.let {
                BBlockConfidenceReview.evaluate(it, blockLayout)
            }
            val productionProposal = generated.bCoachMultiobjectiveProposal
            lastSportShadowProposal = productionProposal
            if (generated.bCoachMultiobjectiveDecision?.status == BCoachProductionStatus.INPUT_CONFLICT) {
                Toast.makeText(
                    context,
                    "고정 요일·시간·운동 조건을 동시에 만족할 수 없습니다. 입력 조건을 확인해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
                pendingBReview = null
                pendingBProductionProposal = null
                pendingBSelectedCandidateId = null
                pendingBPreviousDraft = null
            } else if (productionProposal != null) {
                // Persisted/draft state remains the safe fallback until this exact
                // candidate fingerprint is accepted against the unchanged fallback.
                personalizedDraft = proposed
                v22SkeletonCreated = true
                pendingBReview = review
                pendingBProductionProposal = productionProposal
                pendingBSelectedCandidateId = productionProposal.decision.recommendedCandidateId
            } else if (review?.requiresApproval == true) {
                // Review is exercise-scoped: even partly missing records need user approval.
                pendingBReview = review
                pendingConservativeBBlock = proposed
                pendingBPreviousDraft = null
            } else {
                personalizedDraft = proposed
                v22SkeletonCreated = true
                pendingBPreviousDraft = null
            }
        }
    }

    val activeSportShadow = lastSportShadowProposal?.sportShadowReport
    if (showSportShadow && activeSportShadow != null) {
        BCoachSportShadowDialog(
            report = activeSportShadow,
            candidateForReview = { id -> lastSportShadowProposal?.shadowCandidateForReview(id) },
            onClose = { showSportShadow = false }
        )
    }
    pendingBProductionProposal?.let { proposal ->
        val decision = proposal.decision
        if (!showSportShadow || activeSportShadow == null) BCoachProductionReviewDialog(
            changes = decision.changes,
            unresolvedCodes = decision.unknowns + decision.hardConstraints
                .filter { it.status == BCoachConstraintStatus.UNKNOWN }.map { it.id },
            unreliableExerciseCount = pendingBReview?.unreliableExerciseKeys?.size ?: 0,
            alternatives = decision.alternatives,
            selectedCandidateId = pendingBSelectedCandidateId,
            candidateForReview = proposal::candidateForReview,
            sportShadowAvailable = activeSportShadow?.available == true,
            onOpenSportShadow = { showSportShadow = true },
            onSelectCandidate = { pendingBSelectedCandidateId = it },
            onDismiss = {
                personalizedDraft = pendingBPreviousDraft
                pendingBProductionProposal = null
                pendingBSelectedCandidateId = null
                pendingBReview = null
                pendingBPreviousDraft = null
                // A discarded proposal is not a valid post-review shadow snapshot.
                lastSportShadowProposal = null
                showSportShadow = false
            },
            onApply = {
                val current = personalizedDraft
                val fingerprint = decision.proposalFingerprint
                val chosen = pendingBSelectedCandidateId ?: decision.recommendedCandidateId.orEmpty()
                val acceptance = if (current == null || fingerprint == null) null
                    else proposal.accept(fingerprint, current, chosen)
                if (acceptance?.accepted == true && acceptance.skeleton != null) {
                    personalizedDraft = acceptance.skeleton.withResolvedWeekDaySchedule()
                    pendingBProductionProposal = null
                    pendingBSelectedCandidateId = null
                    pendingBReview = null
                    pendingBPreviousDraft = null
                } else {
                    val reasonText = when (acceptance?.reason) {
                        "PROPOSAL_FINGERPRINT_MISMATCH" -> "검토 중인 제안 식별값이 달라졌습니다. 다시 생성해 주세요."
                        "STALE_INPUT_REGENERATE_REQUIRED" -> "검토하는 동안 초안의 처방이 변경되었습니다. 다시 생성해 주세요."
                        "UNKNOWN_CANDIDATE" -> "선택한 후보를 찾을 수 없습니다. 다른 후보를 선택하거나 다시 생성해 주세요."
                        "PROPOSAL_CONTENT_MISMATCH" -> "후보 내용 검증에 실패했습니다. 다시 생성해 주세요."
                        else -> "후보를 적용하지 못했습니다. 다시 생성해 주세요."
                    }
                    Toast.makeText(
                        context,
                        reasonText,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
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
                        Text("사용 가능한 운동 장비", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "선택하지 않은 장비가 필요한 새 운동은 후보에서 제외됩니다. 맨몸 운동은 항상 사용할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "프리웨이트",
                                selected = bFreeWeightsAvailable,
                                options = listOf(true, false),
                                optionLabel = { if (it) "바벨·덤벨 있음" else "없음" },
                                onSelect = { bFreeWeightsAvailable = it },
                                enabled = !generationRunning
                            )
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "머신·케이블",
                                selected = bMachinesAvailable,
                                options = listOf(true, false),
                                optionLabel = { if (it) "사용 가능" else "없음" },
                                onSelect = { bMachinesAvailable = it },
                                enabled = !generationRunning
                            )
                        }
                        ProgramOptionRow {
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "배드민턴 코트·셔틀콕",
                                selected = bCourtShuttleAvailable,
                                options = listOf(true, false),
                                optionLabel = { if (it) "사용 가능" else "없음" },
                                onSelect = { bCourtShuttleAvailable = it },
                                enabled = !generationRunning
                            )
                            ProgramDropdown(
                                modifier = Modifier.weight(1f).widthIn(min = 180.dp),
                                label = "콘·마커",
                                selected = bMarkersAvailable,
                                options = listOf(true, false),
                                optionLabel = { if (it) "사용 가능" else "없음" },
                                onSelect = { bMarkersAvailable = it },
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
            if (lastGenerationWasV22 && pendingBProductionProposal == null &&
                lastSportShadowProposal?.sportShadowReport?.available == true) item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().testTag("b_coach_open_saved_sport_shadow"),
                    onClick = { showSportShadow = true }
                ) { Text("후보별 스포츠 훈련량 비교") }
                Text("원래 생성 시점의 후보를 비교합니다. 직접 수정 후에는 결과가 무효화됩니다.",
                    style = MaterialTheme.typography.bodySmall)
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
                        lastSportShadowProposal = null
                        showSportShadow = false
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
    var deleteError by remember { mutableStateOf<String?>(null) }
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

    deleteError?.let { detail ->
        ProgramDeleteErrorDialog(detail) { deleteError = null }
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
                                deleteError = message
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

/** Full details are needed because a generic Toast conceals the failed backup invariant. */
@Composable
private fun ProgramDeleteErrorDialog(message: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("프로그램 삭제 실패 원인") },
        text = { androidx.compose.material3.Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { androidx.compose.material3.Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("프로그램 삭제 실패 원인", message)
                )
            }) {
                androidx.compose.material3.Text("원인 복사")
            }
        }
    )
}
