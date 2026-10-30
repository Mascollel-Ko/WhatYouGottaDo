package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.CanonicalMuscleRole
import com.training.trackplanner.data.EffectiveMuscleMetadata
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata

internal enum class ExerciseFilterFacet(val title: String, val displayField: MetadataDisplayField?) {
    CATEGORY("운동 분류", MetadataDisplayField.EXERCISE_CATEGORY),
    SUBCATEGORY("세부 분류", null),
    MUSCLE("근육군", MetadataDisplayField.MUSCLE_GROUP),
    MUSCLE_ROLE("근육 역할", null),
    FORCE("힘의 방향", MetadataDisplayField.FORCE_TYPE),
    PATTERN("동작 패턴", MetadataDisplayField.MOVEMENT_PATTERN),
    EQUIPMENT("운동 기구", MetadataDisplayField.EQUIPMENT),
    PURPOSE("훈련 목적", MetadataDisplayField.MOVEMENT_CATEGORY),
    SLOT("프로그램 역할", MetadataDisplayField.PROGRAM_SLOT)
}

internal data class ExerciseAdvancedSelection(
    val values: Map<ExerciseFilterFacet, Set<String>> = emptyMap()
) {
    fun toggle(facet: ExerciseFilterFacet, value: String): ExerciseAdvancedSelection {
        val next = values.toMutableMap()
        val choices = next[facet].orEmpty()
        val changed = if (value in choices) choices - value else choices + value
        if (changed.isEmpty()) next.remove(facet) else next[facet] = changed
        return copy(values = next)
    }

    fun matches(
        exercise: Exercise,
        muscle: EffectiveMuscleMetadata?,
        runtime: RuntimeExerciseMetadata?
    ): Boolean {
        if (values.isEmpty()) return true
        val available = exerciseFacetValues(exercise, muscle, runtime)
        if (!values.all { (facet, chosen) ->
            chosen.isEmpty() || available[facet].orEmpty().any { it in chosen }
        }) return false

        // Muscle and role must refer to the SAME canonical relation, not
        // different muscles in the same exercise.
        val muscles = values[ExerciseFilterFacet.MUSCLE].orEmpty()
        val roles = values[ExerciseFilterFacet.MUSCLE_ROLE].orEmpty()
        if (muscles.isNotEmpty() && roles.isNotEmpty()) {
            return muscle?.takeIf { it.usesCanonicalRoles }?.canonical?.relations
                ?.any { row ->
                    row.muscleCode in muscles && row.role.name in roles &&
                        row.role in searchableMuscleRoles
                } == true
        }
        return true
    }
}

private val searchableMuscleRoles = setOf(
    CanonicalMuscleRole.PRIMARY,
    CanonicalMuscleRole.SECONDARY,
    CanonicalMuscleRole.STABILIZER,
    CanonicalMuscleRole.GENERAL_PARTICIPATION
)

private fun splitEquipment(raw: String): Set<String> =
    raw.split(',', '|', ';').map { it.trim() }.filter(String::isNotBlank).toSet()

internal fun exerciseFacetValues(
    exercise: Exercise,
    muscle: EffectiveMuscleMetadata?,
    runtime: RuntimeExerciseMetadata?
): Map<ExerciseFilterFacet, Set<String>> {
    val relations = muscle?.takeIf { it.usesCanonicalRoles }?.canonical?.relations.orEmpty()
        .filter { it.role in searchableMuscleRoles }
    val subcategories = ExerciseSubcategoryMapper.categoriesFor(exercise, runtime, muscle)
    return mapOf(
        ExerciseFilterFacet.CATEGORY to setOf(exercise.category),
        ExerciseFilterFacet.SUBCATEGORY to subcategories.map { it.name }.toSet(),
        ExerciseFilterFacet.MUSCLE to relations.map { it.muscleCode }.toSet(),
        ExerciseFilterFacet.MUSCLE_ROLE to relations.map { it.role.name }.toSet(),
        ExerciseFilterFacet.FORCE to exercise.forceType.split('|', ',').map { it.trim() }
            .filter(String::isNotBlank).toSet(),
        ExerciseFilterFacet.PATTERN to exercise.movementPattern.split('|', ',').map { it.trim() }
            .filter(String::isNotBlank).toSet(),
        ExerciseFilterFacet.EQUIPMENT to (
            splitEquipment(exercise.equipment) + splitEquipment(exercise.equipmentTags)
        ),
        ExerciseFilterFacet.PURPOSE to listOf(exercise.movementCategory)
            .filter(String::isNotBlank).toSet(),
        ExerciseFilterFacet.SLOT to listOfNotNull(runtime?.programSlot)
            .filter(String::isNotBlank).toSet()
    )
}

private fun filterLabel(
    facet: ExerciseFilterFacet,
    code: String,
    catalogue: MetadataDisplayCatalogue
): String = when (facet) {
    ExerciseFilterFacet.SUBCATEGORY ->
        ExerciseSubcategory.entries.firstOrNull { it.name == code }?.label ?: code
    ExerciseFilterFacet.MUSCLE_ROLE -> when (code) {
        CanonicalMuscleRole.PRIMARY.name -> "주동근"
        CanonicalMuscleRole.SECONDARY.name -> "보조근"
        CanonicalMuscleRole.STABILIZER.name -> "안정화 근육"
        CanonicalMuscleRole.GENERAL_PARTICIPATION.name -> "일반 참여"
        else -> code
    }
    else -> facet.displayField?.let { catalogue.registeredLabel(it, code) } ?: code
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseAdvancedFilterPanel(
    exercises: List<Exercise>,
    muscleMetadataByKey: Map<String, EffectiveMuscleMetadata>,
    runtimeMetadataByKey: Map<String, RuntimeExerciseMetadata>,
    selected: ExerciseAdvancedSelection,
    onSelectedChange: (ExerciseAdvancedSelection) -> Unit
) {
    val context = LocalContext.current
    val catalogue = remember(context) { MetadataDisplayCatalogue.from(context) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var openFacet by remember { mutableStateOf<ExerciseFilterFacet?>(null) }
    val options = remember(exercises, muscleMetadataByKey, runtimeMetadataByKey) {
        val fields = ExerciseFilterFacet.entries.associateWith { linkedSetOf<String>() }
        exercises.forEach { exercise ->
            val values = exerciseFacetValues(
                exercise, muscleMetadataByKey[exercise.stableKey],
                runtimeMetadataByKey[exercise.stableKey]
            )
            values.forEach { (facet, codes) -> fields.getValue(facet).addAll(codes) }
        }
        fields.mapValues { (_, codes) -> codes.sorted() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (expanded) "상세 필터 접기" else "상세 필터 펼치기 (${selected.values.values.sumOf { it.size }})")
        }
        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExerciseFilterFacet.entries.forEach { facet ->
                    val candidates = options[facet].orEmpty().filter { code ->
                        if (facet != ExerciseFilterFacet.SUBCATEGORY) true else {
                            val categories = selected.values[ExerciseFilterFacet.CATEGORY].orEmpty()
                            categories.isEmpty() || ExerciseSubcategory.entries.any {
                                it.name == code && it.broadCategory in categories
                            }
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        Box {
                            OutlinedButton(onClick = { openFacet = facet }) {
                                val count = selected.values[facet].orEmpty().size
                                Text("${facet.title}${if (count > 0) " · $count" else ""} ▾")
                            }
                            DropdownMenu(
                                expanded = openFacet == facet,
                                onDismissRequest = { openFacet = null },
                                modifier = Modifier.heightIn(max = 370.dp)
                            ) {
                                candidates.forEach { code ->
                                    val checked = code in selected.values[facet].orEmpty()
                                    DropdownMenuItem(
                                        text = {
                                            Text("${if (checked) "✓ " else ""}${filterLabel(facet, code, catalogue)}")
                                        },
                                        onClick = { onSelectedChange(selected.toggle(facet, code)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (selected.values.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selected.values.forEach { (facet, codes) ->
                        codes.forEach { code ->
                            FilterChip(
                                selected = true,
                                onClick = { onSelectedChange(selected.toggle(facet, code)) },
                                label = { Text("${filterLabel(facet, code, catalogue)} ×") }
                            )
                        }
                    }
                    TextButton(onClick = { onSelectedChange(ExerciseAdvancedSelection()) }) {
                        Text("조건 초기화")
                    }
                }
            }
            Text(
                text = "다른 조건끼리는 모두 충족, 같은 조건 내에서는 하나 이상 일치",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
