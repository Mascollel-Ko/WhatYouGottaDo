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
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ExerciseMetadataAdapter
import com.training.trackplanner.data.ProgressMetricRuntimeBehavior
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.deleteDraftItem
import com.training.trackplanner.data.resolvedWeekDaySchedule
import com.training.trackplanner.data.upsertDraftItem
import com.training.trackplanner.data.withWeekDays
import com.training.trackplanner.data.personalized.PersonalizedPlanningDecision
import com.training.trackplanner.localization.localizedExerciseName
import com.training.trackplanner.localization.localizedUiText

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        skeleton.personalizedDecision?.let { PersonalizedDecisionSummary(it) }
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
}

@Composable
private fun PersonalizedDecisionSummary(decision: PersonalizedPlanningDecision) {
    fun label(value: String): String = when (value) {
        "HIGH" -> "높음"
        "MODERATE" -> "보통"
        "LOW" -> "낮음"
        "HYPERTROPHY_DOMINANT" -> "근비대 중심"
        "STRENGTH_DOMINANT" -> "근력 중심"
        "MIXED_STRENGTH_HYPERTROPHY" -> "근력·근비대 혼합"
        "GENERAL_MIXED" -> "종합 혼합"
        "UNKNOWN", "UNRESOLVED" -> "판단 보류"
        "STRENGTH_SUPPORT" -> "근력 향상"
        "HYPERTROPHY", "HYPERTROPHY_SUPPORT" -> "근비대"
        "BADMINTON_SUPPORT" -> "배드민턴 보조"
        "TOP_SET_HYPERTROPHY" -> "탑세트 근비대"
        "TOP_SET_BACKOFF" -> "탑세트·백오프"
        "STRAIGHT_5X5" -> "동일중량 5×5"
        "STRAIGHT_STRENGTH_SETS" -> "동일중량 근력 세트"
        "MADCOW_LIKE_HLM_RAMPING" -> "Madcow형 H/L/M 램핑"
        "HEAVY_LIGHT_MEDIUM" -> "Heavy/Light/Medium"
        "DUP_LIKE_UNDULATING" -> "주간 파동형"
        "PRESERVE" -> "구성 유지"
        "PRESERVE_CORE_REBALANCE" -> "핵심 유지·재배분"
        "PARTIAL_CONTINUITY" -> "부분 연속성"
        "ROTATE_EMPHASIS" -> "강조점 전환"
        "MAINTAIN" -> "용량 유지"
        "REDUCE_SLIGHTLY" -> "용량 소폭 감소"
        "REDUCE_MODERATELY" -> "용량 중간 감소"
        else -> value.replace('_', ' ')
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MaterialText(localizedUiText("기록 기반 계획 요약"), fontWeight = FontWeight.Bold)
            MaterialText(localizedUiText("${decision.planningHorizonWeeks}주 계획 · 주 ${decision.weeklyFrequency}일 · 신뢰도 ${label(decision.confidence)}"))
            MaterialText(localizedUiText("현재 경향 ${label(decision.observedTrainingBehavior)} · 주목표 ${label(decision.primaryAdaptation)}"))
            if (decision.strengthStyle != "NONE") MaterialText(localizedUiText("근력 구성 ${label(decision.strengthStyle)}"))
            decision.anchorTransitions.take(4).forEach { transition ->
                MaterialText(
                    localizedUiText("${transition.stableKey}: 관찰 ${label(transition.observedStyle.name)} · 다음 블록 ${label(transition.structureTreatment.name)} · ${label(transition.doseTreatment.name)}"),
                    style = MaterialTheme.typography.bodySmall
                )
                val features = (transition.preservedFeatures.map { "유지 $it" } + transition.moderatedFeatures.map { "완화 $it" }).take(4)
                if (features.isNotEmpty()) MaterialText(localizedUiText(features.joinToString(" · ")), style = MaterialTheme.typography.bodySmall)
            }
            decision.planningBudget?.let { budget ->
                MaterialText(
                    localizedUiText("주간 저항 세트 ${budget.baselineResistanceSets.toInt()} → ${budget.plannedResistanceSets}/${budget.targetResistanceSets} · 구조화 배드민턴 ${budget.plannedStructuredBadmintonBouts}/${budget.targetStructuredBadmintonBouts}회"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (decision.secondaryTargets.isNotEmpty()) MaterialText(localizedUiText("보완 대상 ${decision.secondaryTargets.joinToString { label(it) }}"))
            decision.reasons.take(5).forEach { MaterialText("• ${localizedUiText(it)}", style = MaterialTheme.typography.bodySmall) }
            decision.constraints.take(3).forEach { MaterialText(localizedUiText("주의: $it"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                MaterialText(displayName, fontWeight = FontWeight.SemiBold)
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
        title = { MaterialText(displayName) },
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
                            stringResource(R.string.duration_label),
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

@Composable
private fun programSetSummaryLines(item: ProgramSkeletonItem): List<String> {
    val sets = ProgramSetPrescriptionResolver.resolve(item)
    val rest = item.restSeconds.takeIf { it > 0 }
        ?.let { stringResource(R.string.rest_seconds_suffix, it) }
        .orEmpty()
    if (sets.map { Triple(it.reps, it.weightKg, it.seconds) }.distinct().size == 1) {
        return listOf(
            "${pluralStringResource(R.plurals.set_count, sets.size, sets.size)} · " +
                "${sets.first().displayText()}$rest"
        )
    }
    return sets.map { set ->
        "${stringResource(R.string.set_ordinal, set.setIndex)} · ${set.displayText()}$rest"
    }
}

@Composable
private fun ProgramSetPrescription.displayText(): String =
    buildList {
        if (reps > 0) add(pluralStringResource(R.plurals.repetition_count, reps, reps))
        if (weightKg > 0.0) add("${formatDecimal(weightKg)}kg")
        if (seconds > 0) add(stringResource(R.string.seconds_short, seconds))
    }.ifEmpty { listOf(stringResource(R.string.prescription_none)) }.joinToString(" · ")

private fun draftItemForExercise(
    exercise: Exercise,
    metadata: RuntimeExerciseMetadata,
    weekNumber: Int,
    dayOfWeek: Int,
    orderIndex: Int
): ProgramSkeletonItem {
    val timed = ExerciseMetadataAdapter.progressMetricBehavior(metadata.progressMetricType) in setOf(
        ProgressMetricRuntimeBehavior.REPS_OR_TIME,
        ProgressMetricRuntimeBehavior.DISTANCE_OR_TIME,
        ProgressMetricRuntimeBehavior.SESSION_DURATION
    )
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

