package com.training.trackplanner

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import com.training.trackplanner.localization.AppLanguage
import com.training.trackplanner.localization.AppLanguageRegistry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeDailyCheckInCard(
    checkIn: DailyCheckIn?,
    onSave: (DailyCheckIn) -> Unit
) {
    var showEditor by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.daily_condition_home_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    checkIn?.compactSummary() ?: stringResource(R.string.daily_condition_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { showEditor = true }) {
                Text(stringResource(if (checkIn == null) R.string.daily_condition_add else R.string.daily_condition_edit))
            }
        }
    }

    if (showEditor) {
        DailyConditionEditorDialog(
            targetDate = LocalDate.now(),
            checkIn = checkIn,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                onSave(saved)
                showEditor = false
            }
        )
    }
}

@Composable
internal fun DailyConditionEditorDialog(
    targetDate: LocalDate,
    checkIn: DailyCheckIn?,
    onDismiss: () -> Unit,
    onSave: (DailyCheckIn) -> Unit
) {
    val context = LocalContext.current
    var sleepHours by rememberSaveable(targetDate.toString(), checkIn?.updatedAt) {
        mutableStateOf(checkIn?.sleepHours?.let(::formatConditionNumber).orEmpty())
    }
    var bodyWeightKg by rememberSaveable(targetDate.toString(), checkIn?.updatedAt) {
        mutableStateOf(checkIn?.bodyWeightKg?.let(::formatConditionNumber).orEmpty())
    }
    var overallFatigue by rememberSaveable(targetDate.toString(), checkIn?.updatedAt) {
        mutableStateOf(checkIn?.overallFatigue)
    }
    var lowerBodyFatigue by rememberSaveable(targetDate.toString(), checkIn?.updatedAt) {
        mutableStateOf(checkIn?.lowerBodyFatigue)
    }
    var jointTendonDiscomfort by rememberSaveable(targetDate.toString(), checkIn?.updatedAt) {
        mutableStateOf(checkIn?.jointTendonDiscomfort)
    }
    var jointTendonDiscomfortJointComplexKey by rememberSaveable(targetDate.toString(), checkIn?.updatedAt) {
        mutableStateOf(checkIn?.jointTendonDiscomfortJointComplexKey)
    }
    var showJointComplexSelector by rememberSaveable { mutableStateOf(false) }
    val jointComplexOptions = rememberJointComplexOptions()
    var focusMotivation by rememberSaveable(targetDate.toString(), checkIn?.updatedAt) {
        mutableStateOf(checkIn?.focusMotivation)
    }

    val parsedSleep = parseDailyConditionNumber(sleepHours)
    val parsedBodyWeight = parseDailyConditionNumber(bodyWeightKg)
    val sleepValid = sleepHours.isBlank() || (parsedSleep != null && parsedSleep in 0.0..24.0)
    val bodyWeightValid = isValidDailyBodyWeightInput(bodyWeightKg)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dailyConditionEditorTitle(context, targetDate)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    stringResource(R.string.daily_condition_description),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = sleepHours,
                    onValueChange = { sleepHours = it },
                    modifier = Modifier.width(150.dp),
                    label = { Text(stringResource(R.string.daily_condition_sleep)) },
                    suffix = { Text(stringResource(R.string.sleep_hours_unit)) },
                    singleLine = true,
                    isError = !sleepValid,
                    supportingText = {
                        if (!sleepValid) Text(stringResource(R.string.daily_condition_sleep_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                val bodyWeightDescription = stringResource(R.string.daily_condition_body_weight)
                OutlinedTextField(
                    value = bodyWeightKg,
                    onValueChange = { bodyWeightKg = it },
                    modifier = Modifier
                        .width(150.dp)
                        .semantics { contentDescription = bodyWeightDescription },
                    label = { Text(stringResource(R.string.daily_condition_body_weight)) },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = !bodyWeightValid,
                    supportingText = {
                        if (!bodyWeightValid) Text(stringResource(R.string.daily_condition_body_weight_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                CheckInScoreRow(stringResource(R.string.daily_condition_overall_fatigue), overallFatigue) { overallFatigue = it }
                CheckInScoreRow(stringResource(R.string.daily_condition_lower_body_fatigue), lowerBodyFatigue) { lowerBodyFatigue = it }
                CheckInScoreRow(stringResource(R.string.daily_condition_discomfort), jointTendonDiscomfort) { jointTendonDiscomfort = it }
                OutlinedButton(onClick = { showJointComplexSelector = true }) {
                    Text(
                        jointComplexOptions.firstOrNull {
                            it.stableKey == jointTendonDiscomfortJointComplexKey
                        }?.name ?: stringResource(R.string.daily_check_in_joint_complex_none)
                    )
                }
                Text(
                    stringResource(R.string.daily_check_in_joint_complex_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CheckInScoreRow(stringResource(R.string.daily_condition_focus), focusMotivation) { focusMotivation = it }
                Text(
                    stringResource(R.string.daily_condition_scale_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        (checkIn ?: DailyCheckIn(date = targetDate.toString())).copy(
                            date = targetDate.toString(),
                            sleepHours = parsedSleep,
                            bodyWeightKg = parsedBodyWeight,
                            overallFatigue = overallFatigue,
                            lowerBodyFatigue = lowerBodyFatigue,
                            jointTendonDiscomfort = jointTendonDiscomfort,
                            jointTendonDiscomfortJointComplexKey =
                                jointTendonDiscomfortJointComplexKey.takeIf { jointTendonDiscomfort != null },
                            focusMotivation = focusMotivation
                        )
                    )
                },
                enabled = sleepValid && bodyWeightValid
            ) { Text(stringResource(R.string.daily_condition_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.daily_condition_cancel)) } }
    )

    if (showJointComplexSelector) {
        AlertDialog(
            onDismissRequest = { showJointComplexSelector = false },
            title = { Text(stringResource(R.string.daily_check_in_joint_complex_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = {
                            jointTendonDiscomfortJointComplexKey = null
                            showJointComplexSelector = false
                        }
                    ) { Text(stringResource(R.string.daily_check_in_joint_complex_clear)) }
                    jointComplexOptions.forEach { option ->
                        TextButton(
                            onClick = {
                                jointTendonDiscomfortJointComplexKey = option.stableKey
                                showJointComplexSelector = false
                            }
                        ) { Text(option.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showJointComplexSelector = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

private data class JointComplexOption(val stableKey: String, val name: String)

@Composable
private fun rememberJointComplexOptions(): List<JointComplexOption> {
    val context = LocalContext.current
    val language = AppLanguageRegistry.effectiveLanguage(LocalConfiguration.current)
    return remember(context.applicationContext, language) {
        TissueRcvAssetRepository.fromAssets(context.applicationContext).catalog.jointComplexes.values.map {
            JointComplexOption(
                stableKey = it.stableKey,
                name = if (language == AppLanguage.ENGLISH) it.nameEn else it.nameKo
            )
        }
    }
}

@Composable
private fun CheckInScoreRow(label: String, selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..5).forEach { score ->
                FilterChip(
                    selected = selected == score,
                    onClick = { onSelect(score.takeUnless { it == selected }) },
                    label = { Text(score.toString()) }
                )
            }
        }
    }
}

@Composable
internal fun DailyCheckIn.compactSummary(): String {
    val context = LocalContext.current
    val selectedJointName = rememberJointComplexOptions().firstOrNull {
        it.stableKey == jointTendonDiscomfortJointComplexKey
    }?.name
    return buildList {
        sleepHours?.let { add(context.getString(R.string.daily_check_in_summary_sleep, formatConditionNumber(it))) }
        bodyWeightKg?.let {
            add(context.getString(R.string.daily_check_in_summary_body_weight, formatConditionNumber(it)))
        }
        overallFatigue?.let { add(context.getString(R.string.daily_check_in_summary_overall_fatigue, it)) }
        lowerBodyFatigue?.let { add(context.getString(R.string.daily_check_in_summary_lower_body_fatigue, it)) }
        jointTendonDiscomfort?.let {
            add(context.getString(R.string.daily_check_in_summary_discomfort, it))
            selectedJointName?.let(::add)
        }
        focusMotivation?.let { add(context.getString(R.string.daily_check_in_summary_focus, it)) }
    }.joinToString(" · ").ifBlank { context.getString(R.string.daily_check_in_summary_empty) }
}

internal fun dailyConditionEditorTitle(
    context: Context,
    targetDate: LocalDate,
    today: LocalDate = LocalDate.now()
): String =
    if (targetDate == today) {
        context.getString(R.string.daily_check_in_today_title)
    } else {
        val locale = context.resources.configuration.locales[0]
        val date = targetDate.format(
            DateTimeFormatter.ofPattern(context.getString(R.string.localized_date_pattern), locale)
        )
        context.getString(R.string.daily_check_in_date_title, date)
    }

internal fun parseDailyConditionNumber(value: String): Double? =
    value.trim().replace(',', '.').takeIf(String::isNotEmpty)?.toDoubleOrNull()

internal fun isValidDailyBodyWeightInput(value: String): Boolean {
    val parsed = parseDailyConditionNumber(value)
    return value.isBlank() || (parsed != null && parsed.isFinite() && parsed > 0.0)
}

private fun formatConditionNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')
