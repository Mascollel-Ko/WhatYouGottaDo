package com.training.trackplanner

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.deleteDraftItem
import com.training.trackplanner.data.resolvedWeekDaySchedule
import com.training.trackplanner.data.upsertDraftItem
import com.training.trackplanner.data.withWeekDays
import com.training.trackplanner.localization.localizedExerciseName

@Composable
internal fun ProgramSkeletonPreview(
    skeleton: GeneratedProgramSkeleton,
    exercises: List<Exercise>,
    metadataByExerciseId: Map<String, RuntimeExerciseMetadata>,
    onSkeletonChange: (GeneratedProgramSkeleton) -> Unit
) {
    var selectedWeek by rememberSaveable(skeleton.suggestedName) { mutableStateOf(1) }
    var selectedDay by rememberSaveable(skeleton.suggestedName) { mutableStateOf(1) }
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ProgramSkeletonItem?>(null) }
    var removeDayTarget by remember { mutableStateOf<Int?>(null) }
    val schedule = skeleton.resolvedWeekDaySchedule()
    val selectedDays = schedule[selectedWeek].orEmpty().sorted()

    LaunchedEffect(schedule, selectedWeek) {
        if (selectedWeek !in schedule.keys) selectedWeek = schedule.keys.minOrNull() ?: 1
        val days = schedule[selectedWeek].orEmpty().sorted()
        selectedDay = days.firstOrNull { it == selectedDay } ?: days.firstOrNull() ?: 1
    }

    removeDayTarget?.let { day ->
        AlertDialog(
            onDismissRequest = { removeDayTarget = null },
            title = { Text("요일 제거") },
            text = { Text("이 요일의 운동도 함께 제거됩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        val nextDays = schedule[selectedWeek].orEmpty() - day
                        onSkeletonChange(skeleton.withWeekDays(selectedWeek, nextDays))
                        removeDayTarget = null
                    }
                ) { Text("제거") }
            },
            dismissButton = {
                TextButton(onClick = { removeDayTarget = null }) { Text("취소") }
            }
        )
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            exercises = exercises.filter(Exercise::isActive),
            onDismiss = { showExercisePicker = false },
            onSelect = { exercise ->
                val metadata = metadataByExerciseId[exercise.stableKey] ?: RuntimeExerciseMetadataDefaults.forExercise(exercise)
                val nextOrder = skeleton.items
                    .filter { it.weekNumber == selectedWeek && it.dayOfWeek == selectedDay }
                    .maxOfOrNull(ProgramSkeletonItem::orderIndex)
                    ?.plus(1)
                    ?: 1
                editingItem = draftItemForExercise(exercise, metadata, selectedWeek, selectedDay, nextOrder)
                showExercisePicker = false
            }
        )
    }

    editingItem?.let { item ->
        ProgramDraftItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                onSkeletonChange(skeleton.upsertDraftItem(updated))
                editingItem = null
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgramDraftEditTab(
                skeleton = skeleton,
                selectedWeek = selectedWeek,
                selectedDay = selectedDay,
                selectedDays = selectedDays,
                onSelectWeek = { selectedWeek = it },
                onSelectDay = { selectedDay = it },
                onToggleDay = { day ->
                    val currentDays = schedule[selectedWeek].orEmpty()
                    if (day in currentDays) {
                        val hasItems = skeleton.items.any { it.weekNumber == selectedWeek && it.dayOfWeek == day }
                        if (hasItems) {
                            removeDayTarget = day
                        } else {
                            onSkeletonChange(skeleton.withWeekDays(selectedWeek, currentDays - day))
                        }
                    } else {
                        onSkeletonChange(skeleton.withWeekDays(selectedWeek, currentDays + day))
                        selectedDay = day
                    }
                },
                onAddExercise = { showExercisePicker = true },
                onEditItem = { editingItem = it },
                onDeleteItem = { item -> onSkeletonChange(skeleton.deleteDraftItem(item.localId)) }
            )
        }
    }
}

@Composable
private fun ProgramDraftEditTab(
    skeleton: GeneratedProgramSkeleton,
    selectedWeek: Int,
    selectedDay: Int,
    selectedDays: List<Int>,
    onSelectWeek: (Int) -> Unit,
    onSelectDay: (Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onAddExercise: () -> Unit,
    onEditItem: (ProgramSkeletonItem) -> Unit,
    onDeleteItem: (ProgramSkeletonItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            skeleton.weekPlans.forEach { week ->
                if (week.weekIndex == selectedWeek) {
                    Button(onClick = { onSelectWeek(week.weekIndex) }) { Text("${week.weekIndex}주") }
                } else {
                    OutlinedButton(onClick = { onSelectWeek(week.weekIndex) }) { Text("${week.weekIndex}주") }
                }
            }
        }
        Text("${selectedWeek}주차 운동 요일", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..7).forEach { day ->
                if (day in selectedDays) {
                    Button(onClick = { onToggleDay(day) }) { Text(dayName(day)) }
                } else {
                    OutlinedButton(onClick = { onToggleDay(day) }) { Text(dayName(day)) }
                }
            }
        }
        if (selectedDays.isEmpty()) {
            Text("이 주차에 운동 요일을 선택하세요.")
            return@Column
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedDays.forEach { day ->
                if (day == selectedDay) {
                    Button(onClick = { onSelectDay(day) }) { Text(dayName(day)) }
                } else {
                    OutlinedButton(onClick = { onSelectDay(day) }) { Text(dayName(day)) }
                }
            }
        }
        val dayItems = skeleton.items
            .filter { it.weekNumber == selectedWeek && it.dayOfWeek == selectedDay }
            .sortedWith(compareBy<ProgramSkeletonItem> { it.orderIndex }.thenBy { it.localId })
        Text(dayName(selectedDay), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (dayItems.isEmpty()) {
            Text("이 요일에는 아직 운동이 없습니다.")
        } else {
            dayItems.forEach { item ->
                ProgramDraftItemRow(
                    item = item,
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item) }
                )
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onAddExercise) {
            Text("+ 운동 추가")
        }
    }
}

@Composable
private fun ProgramDraftItemRow(
    item: ProgramSkeletonItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val displayName = localizedExerciseName(item.exerciseStableKey, item.exerciseName)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, fontWeight = FontWeight.SemiBold)
                programSetSummaryLines(item).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onEdit) { Text("수정") }
            TextButton(onClick = onDelete) { Text("삭제") }
        }
    }
}

@Composable
private fun ProgramDraftItemDialog(
    item: ProgramSkeletonItem,
    onDismiss: () -> Unit,
    onSave: (ProgramSkeletonItem) -> Unit
) {
    val displayName = localizedExerciseName(item.exerciseStableKey, item.exerciseName)
    var sets by remember(item.localId) {
        mutableStateOf(ProgramSetPrescriptionResolver.resolve(item))
    }
    var restText by rememberSaveable(item.localId) { mutableStateOf(item.restSeconds.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sets.forEachIndexed { index, set ->
                    Text("${index + 1}세트", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramNumberField(
                            Modifier.weight(1f),
                            "반복",
                            set.reps.toString(),
                            onChange = { value ->
                                sets = sets.updated(
                                    index,
                                    set.copy(reps = (value.toIntOrNull() ?: 0).coerceAtLeast(0))
                                )
                            }
                        )
                        ProgramNumberField(
                            Modifier.weight(1f),
                            "시간",
                            set.seconds.toString(),
                            onChange = { value ->
                                sets = sets.updated(
                                    index,
                                    set.copy(seconds = (value.toIntOrNull() ?: 0).coerceAtLeast(0))
                                )
                            },
                            suffix = "초"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramDecimalField(
                            Modifier.weight(1f),
                            "중량",
                            formatDecimal(set.weightKg),
                            onChange = { value ->
                                sets = sets.updated(
                                    index,
                                    set.copy(weightKg = (value.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0))
                                )
                            },
                            suffix = "kg"
                        )
                        TextButton(
                            enabled = sets.size > 1,
                            onClick = {
                                sets = sets
                                    .filterIndexed { current, _ -> current != index }
                                    .mapIndexed { current, value -> value.copy(setIndex = current + 1) }
                            }
                        ) {
                            Text("삭제")
                        }
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val previous = sets.lastOrNull() ?: ProgramSetPrescription(1, 0, 0.0, 0)
                        sets = sets + previous.copy(setIndex = sets.size + 1)
                    }
                ) {
                    Text("세트 추가")
                }
                ProgramNumberField(Modifier.fillMaxWidth(), "휴식", restText, onChange = { restText = it }, suffix = "초")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedSets = sets.mapIndexed { index, set -> set.copy(setIndex = index + 1) }
                    val summary = ProgramSetPrescriptionResolver.summarize(normalizedSets)
                    onSave(
                        item.copy(
                            setCount = summary.setCount,
                            reps = summary.reps,
                            seconds = summary.seconds,
                            weightKg = summary.weightKg,
                            restSeconds = (restText.toIntOrNull() ?: 0).coerceAtLeast(0),
                            setPrescriptions = normalizedSets
                        )
                    )
                }
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

private fun List<ProgramSetPrescription>.updated(
    index: Int,
    value: ProgramSetPrescription
): List<ProgramSetPrescription> =
    mapIndexed { current, existing -> if (current == index) value else existing }

private fun programSetSummaryLines(item: ProgramSkeletonItem): List<String> {
    val sets = ProgramSetPrescriptionResolver.resolve(item)
    val rest = item.restSeconds.takeIf { it > 0 }?.let { " · 휴식 ${it}초" }.orEmpty()
    if (sets.map { Triple(it.reps, it.weightKg, it.seconds) }.distinct().size == 1) {
        return listOf("${sets.size}세트 · ${sets.first().displayText()}$rest")
    }
    return sets.map { set -> "${set.setIndex}세트 · ${set.displayText()}$rest" }
}

private fun ProgramSetPrescription.displayText(): String =
    buildList {
        if (reps > 0) add("${reps}회")
        if (weightKg > 0.0) add("${formatDecimal(weightKg)}kg")
        if (seconds > 0) add("${seconds}초")
    }.ifEmpty { listOf("처방 없음") }.joinToString(" · ")

private fun draftItemForExercise(
    exercise: Exercise,
    metadata: RuntimeExerciseMetadata,
    weekNumber: Int,
    dayOfWeek: Int,
    orderIndex: Int
): ProgramSkeletonItem {
    val timed = metadata.progressMetricType.contains("TIME", ignoreCase = true) ||
        exercise.mode.contains("시간") ||
        exercise.category in timedCategories
    return ProgramSkeletonItem(
        localId = "manual-$weekNumber-$dayOfWeek-${exercise.stableKey}-${System.nanoTime()}",
        weekNumber = weekNumber,
        dayOfWeek = dayOfWeek,
        orderIndex = orderIndex,
        exerciseStableKey = exercise.stableKey,
        exerciseName = exercise.name,
        category = exercise.category,
        restSeconds = exercise.defaultRestSeconds,
        prescription = "",
        setCount = 1,
        reps = if (timed) 0 else 10,
        weightKg = 0.0,
        seconds = if (timed) 30 else 0,
        selectionReason = "수동 추가",
        weightSource = "MANUAL_INPUT",
        trainingSlot = metadata.programSlot,
        stableKey = exercise.stableKey,
        movementFamily = metadata.movementFamily,
        movementSubtype = metadata.movementSubtype,
        metadataProgramSlot = metadata.programSlot,
        redundancyGroup = metadata.redundancyGroup,
        strengthProgressionGroup = metadata.strengthProgressionGroup,
        primaryStressProfile = metadata.primaryStressProfile,
        stressMagnitudeHint = metadata.stressMagnitudeHint,
        neuromuscularStressLevel = metadata.neuromuscularStressLevel,
        systemicMuscularStressLevel = metadata.systemicMuscularStressLevel,
        localMuscularStressLevel = metadata.localMuscularStressLevel,
        jointTendonImpactStressLevel = metadata.jointTendonImpactStressLevel,
        movementFocusDemandLevel = metadata.movementFocusDemandLevel,
        recoveryDurationClass = metadata.recoveryDurationClass,
        badmintonTransferLevel = metadata.badmintonTransferLevel,
        primarySlotCapabilities = metadata.badmintonTransferType.values
    )
}

private val timedCategories = setOf("유산소/운동", "스포츠")
