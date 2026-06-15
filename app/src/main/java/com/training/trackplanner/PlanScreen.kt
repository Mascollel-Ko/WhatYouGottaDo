package com.training.trackplanner

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramApplyConflictSummary
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import java.time.LocalDate

@Composable
internal fun PlanScreen(
    viewModel: TrainingViewModel,
    onOpenRecord: () -> Unit
) {
    val programs by viewModel.programs.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    var selectedProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedProgram = programs.firstOrNull { it.id == selectedProgramId }

    LaunchedEffect(programs) {
        if (selectedProgramId != null && selectedProgram == null) {
            selectedProgramId = null
        }
    }

    if (selectedProgram == null) {
        ProgramListScreen(
            programs = programs,
            onCreateProgram = viewModel::createProgram,
            onSelectProgram = { selectedProgramId = it.id }
        )
    } else {
        ProgramDetailScreen(
            program = selectedProgram,
            exercises = exercises,
            viewModel = viewModel,
            onBack = { selectedProgramId = null },
            onOpenRecord = onOpenRecord
        )
    }
}

@Composable
private fun ProgramListScreen(
    programs: List<TrainingProgram>,
    onCreateProgram: () -> Unit,
    onSelectProgram: (TrainingProgram) -> Unit
) {
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
                    onClick = { onSelectProgram(program) }
                )
            }
        }
    }
}

@Composable
private fun ProgramDetailScreen(
    program: TrainingProgram,
    exercises: List<Exercise>,
    viewModel: TrainingViewModel,
    onBack: () -> Unit,
    onOpenRecord: () -> Unit
) {
    val items by remember(program.id) {
        viewModel.programItems(program.id)
    }.collectAsState(initial = emptyList())
    var addTarget by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    val groupedKeys = remember(items) {
        if (items.isEmpty()) {
            listOf(1 to 1)
        } else {
            items.map { it.weekNumber to it.dayOfWeek }
                .distinct()
                .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        }
    }

    addTarget?.let { target ->
        ExercisePickerDialog(
            exercises = exercises,
            onDismiss = { addTarget = null },
            onSelect = { exercise ->
                viewModel.addExerciseToProgram(
                    programId = program.id,
                    weekNumber = target.first,
                    dayOfWeek = target.second,
                    exerciseId = exercise.id
                )
                addTarget = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedButton(onClick = onBack) {
                Text("프로그램 목록")
            }
        }
        item {
            ScreenHeader(
                title = program.name,
                body = "주차와 요일별 처방을 확인하고 운동, 세트, 반복, 중량, 시간, 휴식을 조정하세요."
            )
        }
        item {
            ProgramApplyCard(
                program = program,
                viewModel = viewModel,
                onApplied = onOpenRecord
            )
        }
        items(groupedKeys, key = { "${it.first}-${it.second}" }) { key ->
            val dayItems = items
                .filter { it.weekNumber == key.first && it.dayOfWeek == key.second }
                .sortedWith(compareBy<TrainingProgramItem> { it.orderIndex }.thenBy { it.id })
            ProgramDaySection(
                weekNumber = key.first,
                dayOfWeek = key.second,
                items = dayItems,
                onAddExercise = { addTarget = key },
                onUpdateItem = viewModel::updateProgramItem,
                onDeleteItem = viewModel::deleteProgramItem
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
            title = { Text("기존 기록이 있습니다") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("영향을 받는 날짜 수: ${summary.affectedDateCount}일")
                    Text("기존 운동 기록: ${summary.existingEntryCount}개")
                    Text("기존 완료 세트: ${summary.existingConfirmedSetCount}세트")
                    Text(
                        text = "덮어쓰기를 선택하면 기존 기록과 완료 세트가 삭제됩니다.",
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
                    OutlinedButton(onClick = { apply(ProgramApplyMode.Append) }) {
                        Text("추가")
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
    onClick: () -> Unit
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
            Text(
                text = program.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${program.durationDays}일 프로그램",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick
            ) {
                Text("열기")
            }
        }
    }
}
