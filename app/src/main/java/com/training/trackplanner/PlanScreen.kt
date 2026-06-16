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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramApplyConflictSummary
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
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
    var goal by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(program?.goal.toProgramGoal())
    }
    var weeklyDays by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.weeklyTrainingDays ?: 3).takeIf { it > 0 } ?: 3)
    }
    var sessionMinutes by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.sessionMinutes ?: 60).takeIf { it > 0 } ?: 60)
    }
    var badmintonRatio by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf((program?.badmintonTransferRatio ?: 0.40).coerceIn(0.25, 0.70))
    }
    var sportStrengthRatio by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(program?.sportStrengthRatio?.ifBlank { "AUTO" } ?: "AUTO")
    }
    var periodizationType by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(program?.periodizationType.toProgramPeriodizationType())
    }
    var excludedText by rememberSaveable(program?.id ?: 0L) {
        mutableStateOf(program?.excludedExerciseText.orEmpty())
    }
    var selectedEquipment by remember(program?.id) {
        mutableStateOf<Set<String>>(program?.availableEquipment.toEquipmentSet().ifEmpty { defaultProgramEquipmentTokens })
    }
    var skeleton by remember(program?.id) {
        mutableStateOf<GeneratedProgramSkeleton?>(null)
    }

    LaunchedEffect(program?.id, existingItems) {
        if (program != null && skeleton == null && existingItems.isNotEmpty()) {
            skeleton = skeletonFromProgram(program, existingItems)
        }
    }

    fun normalizedProgramName(): String = nameText.trim()

    fun currentRequest(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = normalizedProgramName(),
            goal = goal,
            weeklyTrainingDays = weeklyDays,
            sessionMinutes = sessionMinutes,
            availableEquipment = selectedEquipment,
            excludedExerciseText = excludedText,
            badmintonTransferRatio = badmintonRatio,
            sportStrengthRatio = sportStrengthRatio,
            periodizationType = periodizationType
        )

    fun requireProgramName(): Boolean {
        val valid = normalizedProgramName().isNotEmpty()
        nameError = !valid
        if (!valid) {
            Toast.makeText(context, "프로그램명을 입력하세요.", Toast.LENGTH_SHORT).show()
        }
        return valid
    }

    fun generateSkeleton(clearExisting: Boolean) {
        if (!requireProgramName()) return
        val request = currentRequest()
        if (clearExisting) skeleton = null
        viewModel.generateProgramSkeleton(request) { generated ->
            skeleton = generated.copy(
                suggestedName = request.name,
                request = generated.request.copy(name = request.name)
            )
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
                            nameText = it
                            if (nameError && it.isNotBlank()) nameError = false
                        },
                        label = { Text("프로그램명") },
                        isError = nameError,
                        supportingText = {
                            if (nameError) Text("프로그램명을 입력하세요.")
                        },
                        singleLine = true
                    )
                    if (program == null) {
                    ProgramDropdown(
                        label = "프로그램 목적",
                        selected = goal,
                        options = ProgramGoal.entries,
                        optionLabel = { it.displayLabel() },
                        onSelect = { goal = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "주당 운동일수",
                            selected = weeklyDays,
                            options = listOf(2, 3, 4, 5),
                            optionLabel = { "주 ${it}일" },
                            onSelect = { weeklyDays = it }
                        )
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "하루 운동시간",
                            selected = sessionMinutes,
                            options = listOf(30, 45, 60, 75),
                            optionLabel = { "${it}분" },
                            onSelect = { sessionMinutes = it }
                        )
                    }
                    ProgramDropdown(
                        label = "배드민턴 지원 훈련 비중",
                        selected = badmintonRatio,
                        options = badmintonRatioOptions.keys.toList(),
                        optionLabel = { ratio -> badmintonRatioOptions.getValue(ratio) },
                        onSelect = { badmintonRatio = it }
                    )
                    Text(
                        text = "배드민턴 경기 자체가 아니라 전이성이 높은 웨이트/보조운동 비중을 조절합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "스포츠:근력 비율",
                            selected = sportStrengthRatio,
                            options = sportStrengthRatioOptions,
                            optionLabel = { it },
                            onSelect = { sportStrengthRatio = it }
                        )
                        ProgramDropdown(
                            modifier = Modifier.weight(1f),
                            label = "주기화",
                            selected = periodizationType,
                            options = ProgramPeriodizationType.entries,
                            optionLabel = { it.displayLabel() },
                            onSelect = { periodizationType = it }
                        )
                    }
                    Text(
                        text = "스포츠 활동 자체를 계획에 넣지 않고, 스포츠 전이성 보조운동과 일반 근력운동의 구성 비율만 조절합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EquipmentToggleGrid(
                        selected = selectedEquipment,
                        onChange = { selectedEquipment = it }
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = excludedText,
                        onValueChange = { excludedText = it },
                        label = { Text("제외 운동 / 통증 메모") },
                        minLines = 2
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { generateSkeleton(clearExisting = false) }
                    ) {
                        Text("자동으로 골자 만들기")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { generateSkeleton(clearExisting = true) }
                    ) {
                        Text("전부 새로 만들기")
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
        skeleton?.let { currentSkeleton ->
            item {
                ProgramSkeletonPreview(
                    skeleton = currentSkeleton,
                    onUpdateItem = { updated ->
                        skeleton = currentSkeleton.copy(
                            items = currentSkeleton.items.map { item ->
                                if (item.localId == updated.localId) updated else item
                            }
                        )
                    },
                    onDeleteItem = { target ->
                        skeleton = currentSkeleton.copy(
                            items = currentSkeleton.items.filterNot { it.localId == target.localId }
                        )
                    }
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
                    enabled = skeleton?.items?.isNotEmpty() == true,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ProgramDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EquipmentToggleGrid(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "사용 가능한 운동기구",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        defaultProgramEquipment.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { equipment ->
                    val isSelected = equipment.token in selected
                    val buttonModifier = Modifier.weight(1f)
                    if (isSelected) {
                        Button(
                            modifier = buttonModifier,
                            onClick = { onChange(selected - equipment.token) }
                        ) {
                            Text(equipment.label)
                        }
                    } else {
                        OutlinedButton(
                            modifier = buttonModifier,
                            onClick = { onChange(selected + equipment.token) }
                        ) {
                            Text(equipment.label)
                        }
                    }
                }
                if (row.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun ProgramSkeletonPreview(
    skeleton: GeneratedProgramSkeleton,
    onUpdateItem: (ProgramSkeletonItem) -> Unit,
    onDeleteItem: (ProgramSkeletonItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "생성된 프로그램 미리보기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "${skeleton.periodizationType.displayLabel()} · ${skeleton.items.size}개 운동 배치",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            skeleton.warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            skeleton.weekPlans.forEach { weekPlan ->
                Text(
                    text = "${weekPlan.weekIndex}주차 ${weekPlan.weekType} · volume ${formatDecimal(weekPlan.volumeMultiplier)} / intensity ${formatDecimal(weekPlan.intensityMultiplier)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                skeleton.items
                    .filter { it.weekNumber == weekPlan.weekIndex }
                    .groupBy { it.dayOfWeek }
                    .toSortedMap()
                    .forEach { (dayOfWeek, dayItems) ->
                        Text(
                            text = programDayLabel(weekPlan.weekIndex, dayOfWeek),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        dayItems.sortedBy { it.orderIndex }.forEach { item ->
                            ProgramSkeletonItemEditor(
                                item = item,
                                onUpdateItem = onUpdateItem,
                                onDeleteItem = onDeleteItem
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun ProgramSkeletonItemEditor(
    item: ProgramSkeletonItem,
    onUpdateItem: (ProgramSkeletonItem) -> Unit,
    onDeleteItem: (ProgramSkeletonItem) -> Unit
) {
    var restText by rememberSaveable(item.localId) { mutableStateOf(item.restSeconds.toString()) }
    var setCountText by rememberSaveable(item.localId) { mutableStateOf(item.setCount.toString()) }
    var repsText by rememberSaveable(item.localId) { mutableStateOf(item.reps.toString()) }
    var weightText by rememberSaveable(item.localId) { mutableStateOf(formatDecimal(item.weightKg)) }
    var secondsText by rememberSaveable(item.localId) { mutableStateOf(item.seconds.toString()) }
    var prescriptionText by rememberSaveable(item.localId) { mutableStateOf(item.prescription) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.exerciseName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${item.selectionReason} · ${item.weightSource}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onDeleteItem(item) }) {
                    Text("삭제")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "세트",
                    value = setCountText,
                    onChange = {
                        setCountText = it
                        onUpdateItem(item.copy(setCount = (it.toIntOrNull() ?: 1).coerceAtLeast(1)))
                    }
                )
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "반복",
                    value = repsText,
                    onChange = {
                        repsText = it
                        onUpdateItem(item.copy(reps = it.toIntOrNull() ?: 0))
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramDecimalField(
                    modifier = Modifier.weight(1f),
                    label = "중량",
                    suffix = "kg",
                    value = weightText,
                    onChange = {
                        weightText = it
                        onUpdateItem(item.copy(weightKg = it.toDoubleOrNull() ?: 0.0))
                    }
                )
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "시간",
                    suffix = "초",
                    value = secondsText,
                    onChange = {
                        secondsText = it
                        onUpdateItem(item.copy(seconds = it.toIntOrNull() ?: 0))
                    }
                )
            }
            ProgramNumberField(
                modifier = Modifier.fillMaxWidth(),
                label = "휴식",
                suffix = "초",
                value = restText,
                onChange = {
                    restText = it
                    onUpdateItem(item.copy(restSeconds = it.toIntOrNull() ?: 0))
                }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = prescriptionText,
                onValueChange = {
                    prescriptionText = it
                    onUpdateItem(item.copy(prescription = it))
                },
                label = { Text("처방 / RPE / 자동중량 출처") },
                minLines = 2
            )
        }
    }
}

private data class ProgramEquipmentOption(
    val token: String,
    val label: String
)

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
private fun ProgramDaySummarySection(
    weekNumber: Int,
    dayOfWeek: Int,
    items: List<TrainingProgramItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = programDayLabel(weekNumber, dayOfWeek),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (items.isEmpty()) {
                Text(
                    text = "- 휴식 또는 미지정",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    Text(
                        text = programItemSummary(item),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun programItemSummary(item: TrainingProgramItem): String {
    val prescriptionParts = buildList {
        if (item.setCount > 0 && item.reps > 0) add("${item.setCount}세트 x ${item.reps}회")
        if (item.weightKg > 0.0) add("${formatDecimal(item.weightKg)}kg")
        if (item.seconds > 0) add("${item.seconds}초")
        if (item.restSeconds > 0) add("휴식 ${item.restSeconds}초")
        if (item.prescription.isNotBlank()) add(item.prescription)
    }
    return if (prescriptionParts.isEmpty()) {
        "- ${item.exerciseName}"
    } else {
        "- ${item.exerciseName}: ${prescriptionParts.joinToString(", ")}"
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

@Composable
private fun ProgramDaySection(
    weekNumber: Int,
    dayOfWeek: Int,
    items: List<TrainingProgramItem>,
    onAddExercise: () -> Unit,
    onUpdateItem: (TrainingProgramItem) -> Unit,
    onDeleteItem: (TrainingProgramItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = programDayLabel(weekNumber, dayOfWeek),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (items.isEmpty()) {
                InfoCard("이 요일에는 아직 운동이 없습니다.")
            } else {
                items.forEach { item ->
                    ProgramItemEditor(
                        item = item,
                        onUpdateItem = onUpdateItem,
                        onDeleteItem = onDeleteItem
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddExercise
            ) {
                Text("운동 추가")
            }
        }
    }
}

@Composable
private fun ProgramItemEditor(
    item: TrainingProgramItem,
    onUpdateItem: (TrainingProgramItem) -> Unit,
    onDeleteItem: (TrainingProgramItem) -> Unit
) {
    var restText by rememberSaveable(item.id) { mutableStateOf(item.restSeconds.toString()) }
    var setCountText by rememberSaveable(item.id) { mutableStateOf(item.setCount.toString()) }
    var repsText by rememberSaveable(item.id) { mutableStateOf(item.reps.toString()) }
    var weightText by rememberSaveable(item.id) { mutableStateOf(formatDecimal(item.weightKg)) }
    var secondsText by rememberSaveable(item.id) { mutableStateOf(item.seconds.toString()) }
    var prescriptionText by rememberSaveable(item.id) { mutableStateOf(item.prescription) }

    LaunchedEffect(item) {
        restText = item.restSeconds.toString()
        setCountText = item.setCount.toString()
        repsText = item.reps.toString()
        weightText = formatDecimal(item.weightKg)
        secondsText = item.seconds.toString()
        prescriptionText = item.prescription
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onDeleteItem(item) }) {
                    Text("삭제")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "세트",
                    value = setCountText,
                    onChange = {
                        setCountText = it
                        onUpdateItem(item.copy(setCount = (it.toIntOrNull() ?: 1).coerceAtLeast(1)))
                    }
                )
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "반복",
                    value = repsText,
                    onChange = {
                        repsText = it
                        onUpdateItem(item.copy(reps = it.toIntOrNull() ?: 0))
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramDecimalField(
                    modifier = Modifier.weight(1f),
                    label = "중량",
                    suffix = "kg",
                    value = weightText,
                    onChange = {
                        weightText = it
                        onUpdateItem(item.copy(weightKg = it.toDoubleOrNull() ?: 0.0))
                    }
                )
                ProgramNumberField(
                    modifier = Modifier.weight(1f),
                    label = "시간",
                    suffix = "초",
                    value = secondsText,
                    onChange = {
                        secondsText = it
                        onUpdateItem(item.copy(seconds = it.toIntOrNull() ?: 0))
                    }
                )
            }
            ProgramNumberField(
                modifier = Modifier.fillMaxWidth(),
                label = "휴식",
                suffix = "초",
                value = restText,
                onChange = {
                    restText = it
                    onUpdateItem(item.copy(restSeconds = it.toIntOrNull() ?: 0))
                }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = prescriptionText,
                onValueChange = {
                    prescriptionText = it
                    onUpdateItem(item.copy(prescription = it))
                },
                label = { Text("처방 메모") },
                minLines = 2
            )
        }
    }
}

@Composable
private fun ProgramNumberField(
    modifier: Modifier,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { if (it.isUnsignedInt()) onChange(it) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
private fun ProgramDecimalField(
    modifier: Modifier,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { if (isDecimalInput(it)) onChange(it) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun ProgramCard(
    program: TrainingProgram,
    onClick: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = program.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(onClick = onApply) {
                    Text("적용")
                }
            }
            Text(
                text = "${program.durationDays}일 프로그램",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (program.goal.isNotBlank() || program.periodizationType.isNotBlank()) {
                Text(
                    text = listOfNotNull(
                        program.goal.toProgramGoal().displayLabel(),
                        program.periodizationType.toProgramPeriodizationType().displayLabel(),
                        "${program.weeklyTrainingDays.takeIf { it > 0 } ?: 0}일/주".takeIf { program.weeklyTrainingDays > 0 }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onClick
                ) {
                    Text("열기")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onEdit
                ) {
                    Text("수정")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDelete
                ) {
                    Text("삭제")
                }
            }
        }
    }
}

private fun skeletonFromProgram(
    program: TrainingProgram,
    items: List<TrainingProgramItem>
): GeneratedProgramSkeleton {
    val request = ProgramSkeletonRequest(
        name = program.name,
        goal = program.goal.toProgramGoal(),
        weeklyTrainingDays = (program.weeklyTrainingDays.takeIf { it > 0 } ?: 3),
        sessionMinutes = (program.sessionMinutes.takeIf { it > 0 } ?: 60),
        availableEquipment = program.availableEquipment.toEquipmentSet().ifEmpty { defaultProgramEquipmentTokens },
        excludedExerciseText = program.excludedExerciseText,
        badmintonTransferRatio = program.badmintonTransferRatio.coerceIn(0.25, 0.70),
        sportStrengthRatio = program.sportStrengthRatio.ifBlank { "AUTO" },
        periodizationType = program.periodizationType.toProgramPeriodizationType()
    )
    val periodization = request.periodizationType.takeIf { it != ProgramPeriodizationType.AUTO }
        ?: ProgramPeriodizationType.STEP_DELOAD
    val weekPlans = (1..4).map { week ->
        ProgramWeekPlan(
            weekIndex = week,
            weekType = if (week == 4) "기존 디로드" else "기존 구성",
            volumeMultiplier = if (week == 4) 0.70 else 1.0,
            intensityMultiplier = if (week == 4) 0.75 else 0.90,
            heavyExposureLimit = if (week == 4) 1 else 2,
            lowerBodyFatigueLimit = if (week == 4) 5.0 else 8.0,
            axialLoadLimit = if (week == 4) 1 else 2,
            plyometricLimit = if (week == 4) 0 else 1,
            deloadFlag = week == 4
        )
    }
    return GeneratedProgramSkeleton(
        suggestedName = program.name,
        durationDays = program.durationDays,
        request = request,
        periodizationType = periodization,
        weekPlans = weekPlans,
        items = items.map { item ->
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
                weightSource = item.prescription.substringAfterLast(" · ", "MANUAL_OR_EXISTING")
            )
        }
    )
}

private fun ProgramGoal.displayLabel(): String =
    when (this) {
        ProgramGoal.BADMINTON_SUPPORT -> "배드민턴 지원 웨이트"
        ProgramGoal.STRENGTH -> "스트렝스"
        ProgramGoal.BODYBUILDING -> "보디빌딩"
        ProgramGoal.FUNCTIONAL_CONDITIONING -> "기능성/컨디셔닝"
    }

private fun String?.toProgramGoal(): ProgramGoal =
    runCatching { ProgramGoal.valueOf(orEmpty()) }.getOrNull() ?: ProgramGoal.BADMINTON_SUPPORT

private fun ProgramPeriodizationType.displayLabel(): String =
    when (this) {
        ProgramPeriodizationType.AUTO -> "자동 추천"
        ProgramPeriodizationType.STEP_DELOAD -> "3주 누적 + 1주 디로드"
        ProgramPeriodizationType.BADMINTON_WAVE -> "배드민턴 병행 파형"
        ProgramPeriodizationType.DAILY_UNDULATING -> "일간 변동 주기화"
        ProgramPeriodizationType.LINEAR_STRENGTH -> "선형 스트렝스형"
    }

private fun String?.toProgramPeriodizationType(): ProgramPeriodizationType =
    runCatching { ProgramPeriodizationType.valueOf(orEmpty()) }.getOrNull() ?: ProgramPeriodizationType.AUTO

private fun String?.toEquipmentSet(): Set<String> =
    orEmpty()
        .split('|', ',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private val badmintonRatioOptions = linkedMapOf(
    0.25 to "낮음 25%",
    0.40 to "보통 40%",
    0.55 to "높음 55%",
    0.70 to "매우 높음 70%"
)

private val sportStrengthRatioOptions = listOf(
    "AUTO",
    "90/10",
    "70/30",
    "50/50",
    "30/70",
    "0/100"
)

private val defaultProgramEquipment = listOf(
    ProgramEquipmentOption("BARBELL", "바벨"),
    ProgramEquipmentOption("DUMBBELL", "덤벨"),
    ProgramEquipmentOption("CABLE", "케이블"),
    ProgramEquipmentOption("MACHINE", "머신"),
    ProgramEquipmentOption("KETTLEBELL", "케틀벨"),
    ProgramEquipmentOption("LANDMINE", "랜드마인"),
    ProgramEquipmentOption("BAND", "밴드"),
    ProgramEquipmentOption("BODYWEIGHT", "맨몸")
)

private val defaultProgramEquipmentTokens = defaultProgramEquipment.map { it.token }.toSet()
