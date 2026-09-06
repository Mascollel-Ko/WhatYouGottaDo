package com.training.trackplanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.*

@Composable
internal fun ProgressionDraftControl(item: ProgramSkeletonItem, items: List<ProgramSkeletonItem>, onChange: (ProgramSkeletonItem) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val settings = item.progressionSettings
    val role = settings?.role ?: item.progressionRole
    TextButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        MaterialText("${stringResource(R.string.progression_progression)}: ${progressionRoleLabel(role)} · ${progressionModeLabel(settings?.mode ?: ProgressionMode.APP)}",
            modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (show) {
        val signature = ProgressionTrackInference.signature(item.exerciseStableKey, ProgramSetPrescriptionResolver.resolve(item),
            style = item.progressionStyle, variant = item.progressionVariant, anchor = item.progressionAnchorSetIndex)
        val track = ProgramProgressionTrack(id = settings?.targetLocalId ?: item.localId, programStableKey = "draft", exerciseStableKey = item.exerciseStableKey,
            label = item.exerciseName, role = role, roleOverride = settings?.role ?: ProgressionRole.AUTO,
            mode = settings?.mode ?: ProgressionMode.APP, basePolicy = signature.basePolicy,
            anchorSetIndex = signature.anchorSetIndex, rule = settings?.rule ?: ProgressionRule())
        val options = items.filter { it.exerciseStableKey == item.exerciseStableKey }.map {
            track.copy(id = it.localId, label = "${it.exerciseName} · ${it.weekNumber}/${it.dayOfWeek} · ${it.orderIndex}")
        }
        ProgressionSettingsSheet(track, ProgramProgressionItem(0, trackId = track.id, signature = signature, linkMode = settings?.linkMode ?: ProgressionLinkMode.AUTO), options,
            onDismiss = { show = false }, onSave = { link, target, selectedRole, mode, rule ->
                onChange(item.copy(progressionSettings = ProgressionDraftSettings(link, target, selectedRole, mode, rule)))
                show = false
            })
    }
}

@Composable
internal fun progressionRoleLabel(role: ProgressionRole): String = stringResource(when (role) {
    ProgressionRole.AUTO -> R.string.progression_auto
    ProgressionRole.MAIN -> R.string.progression_main
    ProgressionRole.ASSISTANCE -> R.string.progression_assistance
})

@Composable
internal fun progressionModeLabel(mode: ProgressionMode): String = stringResource(when (mode) {
    ProgressionMode.APP -> R.string.progression_app
    ProgressionMode.CUSTOM -> R.string.progression_custom
    ProgressionMode.DIRECT -> R.string.progression_direct
    ProgressionMode.OFF -> R.string.progression_off
})

@Composable
internal fun ProgressionTrackControl(itemId: Long, viewModel: TrainingViewModel) {
    val bindings by viewModel.progressionItems.collectAsState(emptyList())
    val tracks by viewModel.progressionTracks.collectAsState(emptyList())
    val binding = bindings.firstOrNull { it.programItemId == itemId } ?: return
    val track = tracks.firstOrNull { it.id == binding.trackId } ?: return
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        MaterialText("${stringResource(R.string.progression_progression)}: ${track.label} · ${progressionRoleLabel(track.role)} · ${progressionModeLabel(track.mode)}",
            modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (track.needsReview) MaterialText(stringResource(R.string.progression_review), style = MaterialTheme.typography.bodySmall)
    if (show) ProgressionSettingsSheet(track, binding, tracks.filter { it.programStableKey == track.programStableKey && it.exerciseStableKey == track.exerciseStableKey },
        onDismiss = { show = false }, onSave = { link, id, role, mode, rule ->
            viewModel.configureProgression(itemId, link, id, role, mode, rule)
            show = false
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressionSettingsSheet(track: ProgramProgressionTrack, binding: ProgramProgressionItem, tracks: List<ProgramProgressionTrack>,
    onDismiss: () -> Unit, onSave: (ProgressionLinkMode, String?, ProgressionRole, ProgressionMode, ProgressionRule) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProgressionSettingsContent(track, binding, tracks, onSave)
    }
}

@Composable
internal fun ProgressionSettingsContent(track: ProgramProgressionTrack, binding: ProgramProgressionItem, tracks: List<ProgramProgressionTrack>,
    onSave: (ProgressionLinkMode, String?, ProgressionRole, ProgressionMode, ProgressionRule) -> Unit) {
    var link by remember { mutableStateOf(binding.linkMode) }
    var target by remember { mutableStateOf(track.id) }
    var role by remember { mutableStateOf(track.roleOverride) }
    var mode by remember { mutableStateOf(track.mode) }
    var rule by remember { mutableStateOf(track.rule) }
    var threshold by remember { mutableStateOf(rule.rpeThreshold.toString()) }
    var step by remember { mutableStateOf(rule.incrementKg?.toString().orEmpty()) }
    var successes by remember { mutableStateOf(rule.successesRequired.toString()) }
    var failures by remember { mutableStateOf(rule.failuresBeforeDecrease.toString()) }
    var decrease by remember { mutableStateOf(rule.decreasePercent.toString()) }
    val roleLabels = ProgressionRole.entries.associateWith { progressionRoleLabel(it) }
    val modeLabels = ProgressionMode.entries.associateWith { progressionModeLabel(it) }
    val linkLabels = listOf(R.string.progression_auto, R.string.progression_existing, R.string.progression_separate, R.string.progression_off).map { stringResource(it) }
    val yes = stringResource(R.string.progression_yes)
    val no = stringResource(R.string.progression_no)
    val hold = stringResource(R.string.progression_hold)
    val review = stringResource(R.string.progression_review_action)
    val maxRpe = stringResource(R.string.progression_max_rpe)
    val anchorRpe = stringResource(R.string.progression_anchor_rpe)
    val completionOnly = stringResource(R.string.progression_completion_only)
    val candidate = runCatching {
        rule.copy(rpeThreshold = threshold.toDouble(), incrementKg = step.takeIf { it.isNotBlank() }?.toDouble(),
            successesRequired = successes.toInt(), failuresBeforeDecrease = failures.toInt(), decreasePercent = decrease.toDouble()).also { it.validate() }
    }.getOrNull()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MaterialText(stringResource(R.string.progression_settings), style = MaterialTheme.typography.titleLarge)
        ProgramDropdown(stringResource(R.string.progression_link), link, ProgressionLinkMode.entries, { linkLabels[it.ordinal] }, { link = it })
        if (link == ProgressionLinkMode.EXISTING) ProgramDropdown(stringResource(R.string.progression_progression), target, tracks.map { it.id }, { id -> tracks.first { it.id == id }.label }, { target = it })
        ProgramDropdown(stringResource(R.string.progression_role), role, ProgressionRole.entries, { roleLabels.getValue(it) }, { role = it })
        ProgramDropdown(stringResource(R.string.progression_mode), mode, ProgressionMode.entries, { modeLabels.getValue(it) }, { mode = it })
        if (mode == ProgressionMode.CUSTOM) {
            ProgramDropdown(stringResource(R.string.progression_completion), rule.requireCompletion, listOf(true, false), { if (it) yes else no }, { rule = rule.copy(requireCompletion = it) })
            ProgramDropdown(stringResource(R.string.progression_rule_rpe), rule.rpePolicy,
                if (track.anchorSetIndex != null) ProgressionRpePolicy.entries else listOf(ProgressionRpePolicy.MAX_WORKING),
                { if (it == ProgressionRpePolicy.MAX_WORKING) maxRpe else anchorRpe }, { rule = rule.copy(rpePolicy = it) })
            ProgressionNumberField(R.string.progression_threshold, threshold) { threshold = it }
            ProgressionNumberField(R.string.progression_successes, successes) { successes = it }
            ProgressionNumberField(R.string.progression_step, step) { step = it }
            ProgramDropdown(stringResource(R.string.progression_first_failure), rule.firstFailure, FirstProgressionFailure.entries, { if (it == FirstProgressionFailure.HOLD) hold else review }, { rule = rule.copy(firstFailure = it) })
            ProgressionNumberField(R.string.progression_failures, failures) { failures = it }
            ProgressionNumberField(R.string.progression_decrease, decrease) { decrease = it }
            ProgramDropdown(stringResource(R.string.progression_missing), rule.missingRpe, MissingProgressionRpe.entries, { if (it == MissingProgressionRpe.HOLD) hold else completionOnly }, { rule = rule.copy(missingRpe = it) })
        }
        MaterialText(stringResource(R.string.progression_snapshot), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onSave(link, target, role, mode, if (mode == ProgressionMode.CUSTOM) candidate!! else ProgressionRule(rpeThreshold = if (role == ProgressionRole.ASSISTANCE) 9.0 else 8.0)) },
            enabled = mode != ProgressionMode.CUSTOM || candidate != null, modifier = Modifier.fillMaxWidth().testTag("progression-save")) {
            MaterialText(stringResource(R.string.progression_save), maxLines = 1)
        }
    }
}

@Composable
private fun ProgressionNumberField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { MaterialText(stringResource(label)) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgramRecordProvenance(link: ProgramWorkoutLink, suggestion: ProgressionSuggestion?,
    resolve: (String, ProgressionResolution, Double?, (Boolean) -> Unit) -> Unit) {
    var details by remember { mutableStateOf(false) }
    var showSuggestion by remember { mutableStateOf(false) }
    var stale by remember { mutableStateOf(false) }
    AssistChip(onClick = { details = true }, label = { MaterialText(stringResource(R.string.progression_program), maxLines = 1) })
    if (suggestion?.resolution == ProgressionResolution.PENDING) {
        TextButton(onClick = { showSuggestion = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
            MaterialText(stringResource(R.string.progression_pending), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (stale) MaterialText(stringResource(R.string.progression_stale))
    if (details) ModalBottomSheet(onDismissRequest = { details = false }) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialText(link.programName, style = MaterialTheme.typography.titleLarge)
            MaterialText(programDayLabel(link.weekNumber, link.dayOfWeek))
            MaterialText(link.trackLabel)
            MaterialText(progressionRoleLabel(link.role))
        }
    }
    if (showSuggestion && suggestion != null) ModalBottomSheet(onDismissRequest = { showSuggestion = false }) {
        ProgressionSuggestionContent(suggestion) { resolution, kg ->
            resolve(suggestion.id, resolution, kg) { success -> stale = !success; showSuggestion = false }
        }
    }
}

@Composable
internal fun ProgressionSuggestionContent(suggestion: ProgressionSuggestion, resolve: (ProgressionResolution, Double?) -> Unit) {
    var manual by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    val editableBase = suggestion.currentPlanKg?.let { it.isFinite() && it > 0 } == true
    val unavailable = stringResource(R.string.progression_unavailable)
    fun kg(value: Double?) = value?.let { "${formatDecimal(it)} kg" } ?: unavailable
    val direction = stringResource(when (suggestion.direction) {
        ProgressionDirection.INCREASE -> R.string.progression_increase
        ProgressionDirection.HOLD -> R.string.progression_hold
        ProgressionDirection.DECREASE -> R.string.progression_decrease_action
        ProgressionDirection.REVIEW -> R.string.progression_review_action
    })
    val reason = stringResource(when (suggestion.reasons) {
        "TARGET_COMPLETED_MANAGEABLE_EFFORT" -> R.string.progression_reason_success
        "FIRST_MISS_HOLD", "REPEATED_COMPARABLE_MISS", "FIRST_MISS_REVIEW" -> R.string.progression_reason_miss
        "ACTUAL_RPE_MISSING", "ACTUAL_EFFORT_HIGH" -> R.string.progression_reason_rpe
        "RELATED_LOCAL_TISSUE_RESTRICTION" -> R.string.progression_reason_local
        else -> R.string.progression_reason_review
    })
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MaterialText("${stringResource(R.string.progression_previous)}: ${kg(suggestion.previousActualKg)}")
        MaterialText("${stringResource(R.string.progression_current)}: ${kg(suggestion.currentPlanKg)}")
        MaterialText("${stringResource(R.string.progression_suggested)}: ${kg(suggestion.suggestedKg)}")
        val delta = if (suggestion.suggestedKg != null && suggestion.previousActualKg != null) suggestion.suggestedKg - suggestion.previousActualKg else null
        MaterialText("${stringResource(R.string.progression_delta)}: $direction ${delta?.let { if (it > 0) "+${kg(it)}" else kg(it) }.orEmpty()}")
        MaterialText("${stringResource(R.string.progression_rule_rpe)}: ${suggestion.judgmentRpe?.let(::formatDecimal) ?: "—"}")
        MaterialText(reason)
        MaterialText(stringResource(R.string.progression_step_note), style = MaterialTheme.typography.bodySmall)
        if (editableBase && suggestion.suggestedKg != null) Button(onClick = { resolve(ProgressionResolution.ACCEPTED, null) }, modifier = Modifier.fillMaxWidth().testTag("progression-apply")) {
            MaterialText(stringResource(R.string.progression_apply), maxLines = 1)
        }
        OutlinedButton(onClick = { resolve(ProgressionResolution.KEPT_CURRENT_PLAN, null) }, modifier = Modifier.fillMaxWidth().testTag("progression-keep")) {
            MaterialText(stringResource(R.string.progression_keep), maxLines = 1)
        }
        if (!editableBase) MaterialText(stringResource(R.string.progression_edit_sets))
        if (editableBase && suggestion.previousActualKg != null) OutlinedButton(onClick = { resolve(ProgressionResolution.MANUAL_OVERRIDE, suggestion.previousActualKg) }, modifier = Modifier.fillMaxWidth()) {
            MaterialText(stringResource(R.string.progression_use_previous), maxLines = 1)
        }
        if (editableBase) OutlinedButton(onClick = { manual = !manual }, modifier = Modifier.fillMaxWidth().testTag("progression-manual")) {
            MaterialText(stringResource(R.string.progression_manual), maxLines = 1)
        }
        if (manual) {
            ProgressionNumberField(R.string.progression_manual, weight) { weight = it }
            Button(onClick = { resolve(ProgressionResolution.MANUAL_OVERRIDE, weight.toDouble()) },
                enabled = weight.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true, modifier = Modifier.fillMaxWidth()) {
                MaterialText(stringResource(R.string.progression_save), maxLines = 1)
            }
        }
    }
}
