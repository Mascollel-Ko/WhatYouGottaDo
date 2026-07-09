package com.training.trackplanner

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.defaultProgramWeekDaySchedule
import com.training.trackplanner.data.emptyProgramSkeleton
import com.training.trackplanner.data.withResolvedWeekDaySchedule
import java.time.LocalDate

@Composable
internal fun PlanScreen(
    viewModel: TrainingViewModel,
    onOpenRecord: () -> Unit
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
                onOpenRecord = onOpenRecord
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
    onOpenRecord: () -> Unit
) {
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<TrainingProgram?>(null) }
    var applyTarget by remember { mutableStateOf<TrainingProgram?>(null) }

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
        AlertDialog(
            onDismissRequest = { applyTarget = null },
            title = { Text("${program.name} 적용") },
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
                    onDelete = { deleteTarget = program }
                )
            }
        }
    }
}

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
    val existingItems by if (program != null) {
        remember(program.id) { viewModel.programItems(program.id) }.collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    var nameText by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(program?.name ?: "")
    }
    var nameError by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(false)
    }
    var durationWeeks by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(((program?.durationDays ?: 28) / 7).coerceIn(3, 8))
    }
    var weeklyDays by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.weeklyTrainingDays ?: 3).takeIf { it > 0 } ?: 3)
    }
    var sessionMinutes by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.sessionMinutes ?: 60).takeIf { it > 0 } ?: 60)
    }
    var badmintonRatio by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.badmintonTransferRatio ?: 0.70).coerceIn(0.0, 0.90))
    }
    var skeleton by remember(program?.id) {
        mutableStateOf<GeneratedProgramSkeleton?>(null)
    }
    val buildProgress by viewModel.programBuildProgress.collectAsState()
    val generationRunning = buildProgress is ProgramBuildProgressState.Running
    var confirmRegenerate by rememberSaveable { mutableStateOf(false) }
    var showSkeletonOptions by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }
    var autoSkeletonCreated by rememberSaveable(program?.id ?: 0L) { mutableStateOf(false) }

    LaunchedEffect(program?.id, existingItems) {
        if (program != null && skeleton == null && existingItems.isNotEmpty()) {
            skeleton = skeletonFromProgram(program, existingItems).withResolvedWeekDaySchedule()
        }
    }

    fun normalizedProgramName(): String = nameText.trim()

    fun currentRequest(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = normalizedProgramName(),
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = weeklyDays,
            sessionMinutes = sessionMinutes,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = badmintonRatio,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = durationWeeks
        )

    fun requireProgramName(): Boolean {
        val valid = normalizedProgramName().isNotEmpty()
        nameError = !valid
        if (!valid) {
            Toast.makeText(context, "프로그램명을 입력하세요.", Toast.LENGTH_SHORT).show()
        }
        return valid
    }

    fun startBlankProgram() {
        if (!requireProgramName()) return
        val request = currentRequest()
        skeleton = emptyProgramSkeleton(
            request = request,
            weekDaySchedule = defaultProgramWeekDaySchedule(durationWeeks, weeklyDays)
        )
        autoSkeletonCreated = false
        showSkeletonOptions = false
    }

    fun generateSkeleton() {
        if (!requireProgramName()) return
        val request = currentRequest()
        viewModel.generateProgramSkeleton(request) { generated ->
            skeleton = generated.copy(
                suggestedName = request.name,
                request = generated.request.copy(name = request.name)
            ).withResolvedWeekDaySchedule()
            autoSkeletonCreated = true
            showSkeletonOptions = true
        }
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text("자동 골자로 대체") },
            text = { Text("현재 작성 중인 구성이 자동 골자로 대체됩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRegenerate = false
                        generateSkeleton()
                    }
                ) { Text("대체") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = false }) { Text("취소") }
            }
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
                body = "조건을 고른 뒤 자동 골자를 만들고, 저장 전에 세트와 중량을 조정합니다."
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
                        if (showSkeletonOptions) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "프로그램 기간",
                            selected = durationWeeks,
                            options = (3..8).toList(),
                            optionLabel = { "${it}주" },
                            onSelect = { durationWeeks = it },
                            enabled = !generationRunning
                        )
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "주당 운동일수",
                            selected = weeklyDays,
                            options = (3..7).toList(),
                            optionLabel = { "주 ${it}일" },
                            onSelect = { weeklyDays = it },
                            enabled = !generationRunning
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "하루 운동시간",
                            selected = sessionMinutes,
                            options = listOf(30, 45, 60),
                            optionLabel = { "${it}분" },
                            onSelect = { sessionMinutes = it },
                            enabled = !generationRunning
                        )
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "배드민턴 : 근력",
                            selected = badmintonRatio,
                            options = badmintonRatioOptions.keys.toList(),
                            optionLabel = { ratio -> badmintonRatioOptions.getValue(ratio) },
                            onSelect = { badmintonRatio = it },
                            enabled = !generationRunning
                        )
                    }
                    Text(
                        text = "배드민턴 특이 훈련과 일반 근력의 우선순위를 조절합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                        }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generationRunning,
                        onClick = { startBlankProgram() }
                    ) {
                        Text("빈 프로그램으로 시작")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generationRunning,
                        onClick = {
                            if (!showSkeletonOptions) {
                                showSkeletonOptions = true
                            } else if (skeleton?.items?.isNotEmpty() == true) {
                                confirmRegenerate = true
                            } else {
                                generateSkeleton()
                            }
                        }
                    ) {
                        Text(if (autoSkeletonCreated) "자동 골자 다시 만들기" else "자동 골자 만들기")
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
        if (buildProgress !is ProgramBuildProgressState.Idle) {
            item {
                ProgramBuildProgressCard(
                    progress = buildProgress,
                    onRetry = { generateSkeleton() }
                )
            }
        }
        skeleton?.let { currentSkeleton ->
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
                    onSkeletonChange = { skeleton = it }
                )
            }
        } ?: item {
            InfoCard(
                if (program == null) {
                    "자동 골자를 만들면 저장 전 미리보기와 수정 항목이 표시됩니다."
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
                    enabled = skeleton?.items?.isNotEmpty() == true && !generationRunning,
                    onClick = {
                        if (!requireProgramName()) return@Button
                        val current = skeleton ?: return@Button
                        val request = currentRequest()
                        viewModel.saveGeneratedProgram(
                            existingProgramId = program?.id,
                            skeleton = current.copy(
                                suggestedName = request.name,
                                request = request
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
private fun ProgramBuildProgressCard(
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
            val messages = progress.summary.messages
            if (messages.isNotEmpty()) {
                InfoCard(messages.joinToString("\n"))
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
private fun ProgramDetailScreen(
    program: TrainingProgram,
    viewModel: TrainingViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val items by remember(program.id) {
        viewModel.programItems(program.id)
    }.collectAsState(initial = emptyList())
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val groupedKeys = remember(items) {
        if (items.isEmpty()) {
            listOf(1 to 1)
        } else {
            items.map { it.weekNumber to it.dayOfWeek }
                .distinct()
                .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
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
                title = program.name,
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
                items = dayItems
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

private fun skeletonFromProgram(
    program: TrainingProgram,
    items: List<TrainingProgramItem>
): GeneratedProgramSkeleton {
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
        durationWeeks = (program.durationDays / 7).coerceIn(3, 8)
    )
    val durationWeeks = (program.durationDays / 7).coerceIn(3, 8)
    val weekPlans = (1..durationWeeks).map { week ->
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
                exerciseId = item.exerciseId,
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
                trainingSlot = metadata.trainingSlot,
                dayIntensity = metadata.dayIntensity
            )
        },
        warnings = buildList {
            if (restoredItems.any { (_, result) -> result.legacyFields.isNotEmpty() }) {
                add("$PROGRAM_ITEM_LEGACY_RESTORE_WARNING: legacy prescription metadata used")
            }
            if (restoredItems.any { (_, result) -> result.unresolvedFields.isNotEmpty() }) {
                add("$PROGRAM_ITEM_RESTORE_WARNING: fallback metadata used")
            }
        }
    )
}

private const val PROGRAM_ITEM_RESTORE_WARNING = "PROGRAM_ITEM_RESTORE_METADATA_FALLBACK"
private const val PROGRAM_ITEM_LEGACY_RESTORE_WARNING = "PROGRAM_ITEM_RESTORE_LEGACY_FALLBACK"
