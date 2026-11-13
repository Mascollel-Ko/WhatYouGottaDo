package com.training.trackplanner

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.plannedRpeLabel
import com.training.trackplanner.data.programEffortDisplay
import com.training.trackplanner.data.ExerciseMetadataAdapter
import com.training.trackplanner.data.ProgressMetricRuntimeBehavior
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.deleteDraftItem
import com.training.trackplanner.data.resolvedWeekDaySchedule
import com.training.trackplanner.data.upsertDraftItem
import com.training.trackplanner.data.withWeekDays
import com.training.trackplanner.data.ProgramEditScope
import com.training.trackplanner.data.ProgramScopedEditor
import com.training.trackplanner.data.PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE
import com.training.trackplanner.data.v22.reassessBBalancedAfterEdit
import androidx.compose.ui.platform.testTag
import com.training.trackplanner.localization.localizedExerciseName
import com.training.trackplanner.localization.localizedUiText

@Composable
internal fun ProgramSkeletonPreview(
    skeleton: GeneratedProgramSkeleton,
    exercises: List<Exercise>,
    metadataByExerciseId: Map<String, RuntimeExerciseMetadata>,
    progressionEligibleKeys: Set<String> = emptySet(),
    muscleMetadataByKey: Map<String, com.training.trackplanner.data.EffectiveMuscleMetadata> = emptyMap(),
    onSkeletonChange: (GeneratedProgramSkeleton) -> Unit
) {
    var selectedWeek by rememberSaveable(skeleton.suggestedName) { mutableStateOf(1) }
    var selectedDay by rememberSaveable(skeleton.suggestedName) { mutableStateOf(1) }
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ProgramSkeletonItem?>(null) }
    var removeDayTarget by remember { mutableStateOf<Int?>(null) }
    var allWeeks by rememberSaveable(skeleton.suggestedName) { mutableStateOf(false) }
    var confirmCopyToAll by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val showAllWeeksMismatch = {
        Toast.makeText(context, PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE, Toast.LENGTH_SHORT).show()
    }
    fun applyScoped(change: () -> GeneratedProgramSkeleton) {
        runCatching(change).onSuccess(onSkeletonChange).onFailure { showAllWeeksMismatch() }
    }
    val recordBased=skeleton.personalizedDecision!=null || (skeleton.planningMetadataJson?.contains("B_BALANCED_12_WEEK_V1")==true ||
        skeleton.planningMetadataJson?.contains("B_BALANCED_BLOCK_PROGRAM_V1")==true)
    val supportsAll=remember(skeleton) { ProgramScopedEditor.supportsAll(skeleton) }
    val scope=if(recordBased && allWeeks && supportsAll) ProgramEditScope.ALL_WEEKS else ProgramEditScope.INDIVIDUAL_WEEK
    val editWeek=if(scope==ProgramEditScope.ALL_WEEKS) 1 else selectedWeek
    LaunchedEffect(supportsAll) { if(!supportsAll) allWeeks=false }
    val schedule = skeleton.resolvedWeekDaySchedule()
    val selectedDays = schedule[editWeek].orEmpty().sorted()

    LaunchedEffect(schedule, selectedWeek) {
        if (selectedWeek !in schedule.keys) selectedWeek = schedule.keys.minOrNull() ?: 1
        val days = schedule[selectedWeek].orEmpty().sorted()
        selectedDay = days.firstOrNull { it == selectedDay } ?: days.firstOrNull() ?: 1
    }

    if (confirmCopyToAll) {
        AlertDialog(
            onDismissRequest = { confirmCopyToAll = false },
            title = { MaterialText("1주차 구성을 모든 주에 복사") },
            text = { MaterialText(
                "1주차의 요일·운동·세트·반복·중량을 2~${skeleton.request.durationWeeks}주차에 복사합니다. " +
                    "다른 주차의 기존 구성과 주기화 차이는 사라집니다. " +
                    "아직 기록 달력에 적용한 운동은 변경하지 않습니다. 계속할까요?"
            ) },
            confirmButton = {
                Button(onClick = {
                    applyScoped { ProgramScopedEditor.copyBWeekToAll(skeleton, 1) }
                    confirmCopyToAll = false
                    allWeeks = false
                    selectedWeek = 1
                }) { MaterialText("모든 주 구성 덮어쓰기") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCopyToAll = false }) { MaterialText("취소") }
            }
        )
    }

    removeDayTarget?.let { day ->
        AlertDialog(
            onDismissRequest = { removeDayTarget = null },
            title = { Text("요일 제거") },
            text = { Text("이 요일의 운동도 함께 제거됩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        val nextDays = schedule[editWeek].orEmpty() - day
                        applyScoped { if(recordBased) ProgramScopedEditor.days(skeleton,editWeek,nextDays,scope) else skeleton.withWeekDays(editWeek,nextDays) }
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
            muscleMetadataByKey = muscleMetadataByKey,
            onDismiss = { showExercisePicker = false },
            onSelect = { exercise ->
                val metadata = metadataByExerciseId[exercise.stableKey] ?: RuntimeExerciseMetadataDefaults.forExercise(exercise)
                val nextOrder = skeleton.items
                    .filter { it.weekNumber == editWeek && it.dayOfWeek == selectedDay }
                    .maxOfOrNull(ProgramSkeletonItem::orderIndex)
                    ?.plus(1)
                    ?: 1
                editingItem = draftItemForExercise(exercise, metadata, editWeek, selectedDay, nextOrder)
                showExercisePicker = false
            }
        )
    }

    editingItem?.let { item ->
        ProgramDraftItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                val next = runCatching {
                    when {
                        recordBased && skeleton.items.none { it.localId == updated.localId } ->
                            ProgramScopedEditor.add(skeleton, updated, scope)
                        recordBased -> ProgramScopedEditor.update(skeleton, updated.localId, updated, scope)
                        else -> skeleton.upsertDraftItem(updated).reassessBBalancedAfterEdit()
                    }
                }.getOrElse {
                    showAllWeeksMismatch()
                    return@ProgramDraftItemDialog
                }
                onSkeletonChange(next)
                editingItem = null
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        skeleton.personalizedDecision?.let { decision ->
            PlanningSummaryCard(remember(decision) { PlanningSummaryPresenter.present(decision) })
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
                if(recordBased) {
                    ProgramEditScopeControl(scope,supportsAll) { allWeeks=it==ProgramEditScope.ALL_WEEKS }
                    MaterialText(
                        if (scope == ProgramEditScope.ALL_WEEKS)
                            "이 모드는 지금부터 수정하는 항목만 모든 주에 반영합니다. 기존 주차의 운동 목록을 자동으로 복사하지 않습니다."
                        else "현재 ${editWeek}주차의 운동만 수정합니다. 다른 주차는 그대로 유지됩니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (supportsAll && skeleton.planningMetadataJson?.contains("B_BALANCED") == true) {
                        OutlinedButton(
                            onClick = { confirmCopyToAll = true },
                            modifier = Modifier.fillMaxWidth().testTag("scope-copy-week-to-all")
                        ) { MaterialText("1주차 전체 운동 구성을 모든 주에 복사") }
                        MaterialText(
                            "위 버튼은 이후 수정 범위 설정과 별개입니다. 확인 후 2주차부터 실제 운동 목록까지 변경됩니다.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                ProgramDraftEditTab(
                    skeleton = skeleton,
                    progressionEligibleKeys = progressionEligibleKeys,
                    selectedWeek = editWeek,
                    showWeekSelector = scope!=ProgramEditScope.ALL_WEEKS,
                    selectedDay = selectedDay,
                    selectedDays = selectedDays,
                    onSelectWeek = { selectedWeek = it },
                    onSelectDay = { selectedDay = it },
                    onToggleDay = { day ->
                        val currentDays = schedule[editWeek].orEmpty()
                        if (day in currentDays) {
                            val hasItems = skeleton.items.any { it.weekNumber == editWeek && it.dayOfWeek == day }
                            if (hasItems) {
                                removeDayTarget = day
                            } else {
                                applyScoped { if(recordBased) ProgramScopedEditor.days(skeleton,editWeek,currentDays-day,scope) else skeleton.withWeekDays(editWeek,currentDays-day) }
                            }
                        } else {
                            applyScoped { if(recordBased) ProgramScopedEditor.days(skeleton,editWeek,currentDays+day,scope) else skeleton.withWeekDays(editWeek,currentDays+day) }
                            selectedDay = day
                        }
                    },
                    onAddExercise = { showExercisePicker = true },
                    onEditItem = { editingItem = it },
                    canEditItem = { item ->
                        scope != ProgramEditScope.ALL_WEEKS || ProgramScopedEditor.canEditAll(skeleton, item.localId)
                    },
                    onBlockedItem = showAllWeeksMismatch,
                    onProgressionChange = onSkeletonChange,
                    onDeleteItem = { item -> applyScoped { if(recordBased) ProgramScopedEditor.delete(skeleton,item.localId,scope) else skeleton.deleteDraftItem(item.localId) } },
                    onMoveItem = if(recordBased) { item,day -> applyScoped { ProgramScopedEditor.move(skeleton,item.localId,day,scope) } } else null,
                    onReorderItem = if(recordBased) { item,offset -> applyScoped { ProgramScopedEditor.reorder(skeleton,item.localId,offset,scope) } } else null
                )
            }
        }
    }
}

@Composable
internal fun ProgramEditScopeControl(scope: ProgramEditScope,supportsAll: Boolean,onChange: (ProgramEditScope)->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        // Full-width choices keep Korean readable at narrow widths and increased font scale.
        OutlinedButton(onClick={onChange(ProgramEditScope.ALL_WEEKS)},enabled=supportsAll,
            modifier=Modifier.fillMaxWidth().testTag("scope-all")) {
            MaterialText("이후 수정사항을 모든 주에 반영"+(if(scope==ProgramEditScope.ALL_WEEKS) " ✓" else ""))
        }
        OutlinedButton(onClick={onChange(ProgramEditScope.INDIVIDUAL_WEEK)},modifier=Modifier.fillMaxWidth().testTag("scope-week")) {
            MaterialText(stringResource(R.string.program_scope_week)+(if(scope==ProgramEditScope.INDIVIDUAL_WEEK) " ✓" else ""))
        }
        if(!supportsAll) MaterialText(stringResource(R.string.program_scope_diverged),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProgramDraftEditTab(
    skeleton: GeneratedProgramSkeleton,
    progressionEligibleKeys: Set<String>,
    selectedWeek: Int,
    showWeekSelector: Boolean,
    selectedDay: Int,
    selectedDays: List<Int>,
    onSelectWeek: (Int) -> Unit,
    onSelectDay: (Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onAddExercise: () -> Unit,
    onEditItem: (ProgramSkeletonItem) -> Unit,
    canEditItem: (ProgramSkeletonItem) -> Boolean,
    onBlockedItem: () -> Unit,
    onProgressionChange: (GeneratedProgramSkeleton) -> Unit,
    onDeleteItem: (ProgramSkeletonItem) -> Unit,
    onMoveItem: ((ProgramSkeletonItem,Int)->Unit)? = null,
    onReorderItem: ((ProgramSkeletonItem,Int)->Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if(showWeekSelector) ProgramTemporalSelector(skeleton.weekPlans.map { it.weekIndex }, setOf(selectedWeek),
            "program-week", { programWeekChipLabel(it) }, onSelectWeek, { programWeekLabel(it) })
        MaterialText(stringResource(R.string.program_week_training_days, selectedWeek), fontWeight = FontWeight.SemiBold)
        ProgramTemporalSelector((1..7).toList(), selectedDays.toSet(),
            "program-day-toggle", { programWeekdayChipLabel(it) }, onToggleDay, { programWeekdayLabel(it) })
        if (selectedDays.isEmpty()) {
            Text("이 주차에 운동 요일을 선택하세요.")
            return@Column
        }
        ProgramTemporalSelector(selectedDays, setOf(selectedDay),
            "program-day-view", { programWeekdayChipLabel(it) }, onSelectDay, { programWeekdayLabel(it) })
        val dayItems = skeleton.items
            .filter { it.weekNumber == selectedWeek && it.dayOfWeek == selectedDay }
            .sortedWith(compareBy<ProgramSkeletonItem> { it.orderIndex }.thenBy { it.localId })
        MaterialText(programWeekdayLabel(selectedDay), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (dayItems.isEmpty()) {
            Text("이 요일에는 아직 운동이 없습니다.")
        } else {
            dayItems.forEach { item ->
                val canEdit = canEditItem(item)
                ProgramDraftItemRow(
                    item = item,
                    enabled = canEdit,
                    onBlocked = onBlockedItem,
                    onEdit = { if (canEdit) onEditItem(item) else onBlockedItem() },
                    onDelete = { if (canEdit) onDeleteItem(item) else onBlockedItem() }
                )
                if(onMoveItem!=null) {
                    ProgramTemporalSelector(selectedDays,setOf(item.dayOfWeek),"move-${item.localId}",
                        { programWeekdayChipLabel(it) },
                        { if (canEdit) onMoveItem(item,it) else onBlockedItem() },
                        { programWeekdayLabel(it) })
                }
                if(onReorderItem!=null) Row {
                    TextButton(onClick={if(canEdit) onReorderItem(item,-1) else onBlockedItem()},enabled=item!=dayItems.first()) { MaterialText(stringResource(R.string.program_move_up)) }
                    TextButton(onClick={if(canEdit) onReorderItem(item,1) else onBlockedItem()},enabled=item!=dayItems.last()) { MaterialText(stringResource(R.string.program_move_down)) }
                }
                if (canEdit && item.exerciseStableKey in progressionEligibleKeys && item.progressionBinding != null) {
                    ProgressionDraftControl(item, skeleton, onProgressionChange)
                }
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
    enabled: Boolean,
    onBlocked: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val displayName = localizedExerciseName(item.exerciseStableKey, item.exerciseName)
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else .68f)
            .clickable(enabled = !enabled, onClick = onBlocked),
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
                    MaterialText(
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
                    MaterialText(stringResource(R.string.set_ordinal, index + 1), fontWeight = FontWeight.SemiBold)
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
                            suffix = stringResource(R.string.program_unit_seconds)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgramDecimalField(
                            Modifier.weight(1f),
                            "중량",
                            if (set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED) "" else formatDecimal(set.weightKg),
                            onChange = { value ->
                                val parsed = value.toDoubleOrNull()
                                if (value.isBlank() || parsed != null) {
                                    val weight = parsed?.coerceAtLeast(0.0) ?: 0.0
                                    sets = sets.updated(index, set.copy(
                                        weightKg = weight,
                                        loadState = when {
                                            value.isBlank() -> ProgramLoadState.USER_CALIBRATION_REQUIRED
                                            weight == 0.0 -> ProgramLoadState.REAL_ZERO_LOAD
                                            else -> ProgramLoadState.EXPLICIT_LOAD
                                        }
                                    ))
                                }
                            },
                            suffix = stringResource(R.string.program_unit_kg)
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
                ProgramNumberField(Modifier.fillMaxWidth(), "휴식", restText, onChange = { restText = it }, suffix = stringResource(R.string.program_unit_seconds))
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
                            weightSource = if (ProgramSetPrescriptionResolver.resolve(item)
                                    .map { it.weightKg } != normalizedSets.map { it.weightKg } &&
                                    item.selectionRole.startsWith("B12_FOCUS_"))
                                "B_BLOCK_USER_OVERRIDE" else item.weightSource,
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
    if (!item.taskProtocolSemanticsJson.isNullOrBlank()) {
        return listOf(item.prescription)
    }
    val sets = ProgramSetPrescriptionResolver.resolve(item)
    val effort = programEffortDisplay(sets)
    val rest = item.restSeconds.takeIf { it > 0 }
        ?.let { stringResource(R.string.rest_seconds_suffix, it) }
        .orEmpty()
    if (sets.map { listOf(it.reps, it.weightKg, it.seconds, it.loadState) }.distinct().size == 1) {
        return listOf(
                "${pluralStringResource(R.plurals.set_count, sets.size, sets.size)} · " +
                "${sets.first().displayText()}$rest${effort.commonTargetRpeMin.plannedRpeSuffix()}"
        ) + item.periodizationPreviewLine()
    }
    return sets.mapIndexed { index, set ->
        val suffix = effort.commonTargetRpeMin?.plannedRpeSuffix()
            ?: effort.perSetTargetRpeMin.getOrNull(index).plannedRpeSuffix()
        "${stringResource(R.string.set_ordinal, set.setIndex)} · ${set.displayText()}$rest${if (index == 0) suffix else if (effort.commonTargetRpeMin != null) "" else suffix}"
    } + item.periodizationPreviewLine()
}

private fun ProgramSkeletonItem.periodizationPreviewLine(): List<String> =
    listOfNotNull(prescription.takeIf { it.startsWith("주기화") })

@Composable
private fun ProgramSetPrescription.displayText(): String =
    buildList {
        if (reps > 0) add(pluralStringResource(R.plurals.repetition_count, reps, reps))
        when (loadState) {
            ProgramLoadState.USER_CALIBRATION_REQUIRED -> add(stringResource(R.string.cold_start_choose_weight))
            ProgramLoadState.NOT_APPLICABLE -> Unit
            ProgramLoadState.EXPLICIT_LOAD -> if (weightKg > 0.0) add("${formatDecimal(weightKg)}kg")
            ProgramLoadState.REAL_ZERO_LOAD -> add("${formatDecimal(weightKg)}kg")
        }
        if (seconds > 0) add(stringResource(R.string.seconds_short, seconds))
    }.ifEmpty { listOf(stringResource(R.string.prescription_none)) }.joinToString(" · ")

private fun Double?.plannedRpeSuffix(): String = plannedRpeLabel(this)?.let { " · $it" }.orEmpty()

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

