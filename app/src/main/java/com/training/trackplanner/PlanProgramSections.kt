package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.TrainingProgramItemSet
import com.training.trackplanner.localization.localizedExerciseName
import com.training.trackplanner.localization.localizedProgramName

@Composable
internal fun ProgramDaySummarySection(
    weekNumber: Int,
    dayOfWeek: Int,
    items: List<TrainingProgramItem>,
    setsByItemId: Map<Long, List<TrainingProgramItemSet>>,
    onExerciseInfo: (String) -> Unit,
    availableExerciseKeys: Set<String>
) {
    val translator = rememberMetadataTranslator()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = programDayLabel(weekNumber, dayOfWeek),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        items.forEach { item ->
            val displayName = localizedExerciseName(item.exerciseStableKey, item.exerciseName)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            MaterialText(displayName, fontWeight = FontWeight.SemiBold)
                            item.category.takeIf(String::isNotBlank)?.let { category ->
                                Text(
                                    translator.translate("exercise.category", category).orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (item.exerciseStableKey in availableExerciseKeys) {
                            IconButton(onClick = { onExerciseInfo(item.exerciseStableKey) }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(
                                        R.string.exercise_info_content_description,
                                        displayName
                                    )
                                )
                            }
                        }
                    }
                    programPrescriptionLines(
                        item,
                        setsByItemId[item.id].orEmpty()
                    ).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (item.restSeconds > 0) {
                        Text(
                            stringResource(R.string.inter_set_rest, item.restSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.prescription.isNotBlank()) {
                        Text(
                            item.prescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun programPrescriptionLines(
    item: TrainingProgramItem,
    storedSets: List<TrainingProgramItemSet>
): List<String> {
    val sets = ProgramSetPrescriptionResolver.resolve(item, storedSets)
    val uniform = sets.map { Triple(it.reps, it.weightKg, it.seconds) }.distinct().size == 1
    return if (uniform) {
        listOf(
            "${pluralStringResource(R.plurals.set_count, sets.size, sets.size)} · " +
                sets.first().displayText()
        )
    } else {
        sets.map { set -> "${stringResource(R.string.set_ordinal, set.setIndex)} · ${set.displayText()}" }
    }
}

@Composable
private fun ProgramSetPrescription.displayText(): String =
    buildList {
        if (reps > 0) add(pluralStringResource(R.plurals.repetition_count, reps, reps))
        if (weightKg > 0.0) add("${formatDecimal(weightKg)}kg")
        if (seconds > 0) add(stringResource(R.string.seconds_short, seconds))
    }.ifEmpty { listOf(stringResource(R.string.prescription_none)) }.joinToString(" · ")


@Composable
internal fun ProgramCard(
    program: TrainingProgram,
    onClick: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    applyModifier: Modifier = Modifier
) {
    val displayName = localizedProgramName(program)
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
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(modifier = applyModifier, onClick = onApply) {
                    Text("적용")
                }
            }
            Text(
                text = "${program.durationDays}일 프로그램",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (program.weeklyTrainingDays > 0) {
                Text(
                    text = "${program.weeklyTrainingDays}일/주",
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
