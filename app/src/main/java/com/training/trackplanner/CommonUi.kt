package com.training.trackplanner

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.CanonicalMuscleRole
import com.training.trackplanner.data.EffectiveMuscleMetadataResolver
import com.training.trackplanner.data.EffectiveMuscleMetadata
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseImageAssetMapping
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.localization.localizedExerciseName
import com.training.trackplanner.localization.localizedExerciseDescription
import com.training.trackplanner.localization.localizedUiText
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun CtaCard(
    title: String,
    body: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
internal fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun ScreenHeader(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun InfoCard(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 8.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun QuietChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, softWrap = false) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
internal fun DateSwitcher(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onPrevious) {
                Text("이전날")
            }
            Text(
                text = date.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(onClick = onNext) {
                Text("다음날")
            }
        }
    }
}

@Composable
internal fun InlineDateSwitcher(
    dateText: String,
    onDateChange: (String) -> Unit
) {
    val parsedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("plan-start-date"),
            value = dateText,
            onValueChange = onDateChange,
            singleLine = true,
            label = { Text("시작일") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        OutlinedButton(
            modifier = Modifier
                .weight(1f)
                .testTag("plan-start-date-minus"),
            enabled = parsedDate != null,
            onClick = { onDateChange(parsedDate!!.minusDays(1).toString()) }
        ) {
            Text("-1")
        }
        OutlinedButton(
            modifier = Modifier
                .weight(1f)
                .testTag("plan-start-date-plus"),
            enabled = parsedDate != null,
            onClick = { onDateChange(parsedDate!!.plusDays(1).toString()) }
        ) {
            Text("+1")
        }
        }
    }
}

@Composable
internal fun ExercisePickerDialog(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSelect: (Exercise) -> Unit,
    muscleMetadataByKey: Map<String, EffectiveMuscleMetadata> = emptyMap()
) {
    var query by rememberSaveable { mutableStateOf("") }
    val translator = rememberMetadataTranslator()
    val context = LocalContext.current
    val musclePresenter = remember(context) { CanonicalMusclePresentation.from(context) }
    // Program preview pickers have no ViewModel muscle map. Resolve built-ins
    // by stableKey from the verified manifest, not stale Exercise strings.
    val fallbackResolver = remember(context) {
        lazy(LazyThreadSafetyMode.NONE) {
            EffectiveMuscleMetadataResolver(
                CanonicalExerciseMetadataRepository(context).muscleRoleResolver()
            )
        }
    }
    val filtered = remember(exercises, query, translator, muscleMetadataByKey, musclePresenter, fallbackResolver) {
        if (query.isBlank()) exercises
        else exercises.filter { exercise ->
            val effective = muscleMetadataByKey[exercise.stableKey]
                ?: fallbackResolver.value.resolve(exercise, emptyList())
            exercise.matchesMetadataQuery(query, translator, effective, musclePresenter)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        title = { Text("운동 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("검색") },
                    singleLine = true
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = Exercise::stableKey) { exercise ->
                        ExerciseListItem(
                            exercise = exercise,
                            selected = false,
                            onClick = { onSelect(exercise) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun ExerciseListItem(
    exercise: Exercise,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null
) {
    val translator = rememberMetadataTranslator()
    val displayName = localizedExerciseName(exercise)
    val isEnglish = LocalConfiguration.current.locales[0].language == Locale.ENGLISH.language
    val summary = if (isEnglish && !exercise.isCustom) {
        listOf(
            localizedUiText(translator.translate("exercise.category", exercise.category).orEmpty()),
            translator.translate(
                "exercise.equipment",
                exercise.equipment.ifBlank { exercise.equipmentTags }
            ).orEmpty(),
            exercise.localizedRecordingMethod()
        )
    } else {
        listOf(
            translator.translate("exercise.category", exercise.category).orEmpty(),
            translator.translate("exercise.mode", exercise.mode).orEmpty()
        )
    }.filter(String::isNotBlank).joinToString(" / ")
    val cardModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MaterialText(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onInfo != null) {
                TextButton(onClick = onInfo) {
                    Text("i")
                }
            }
        }
    }
}

@Composable
internal fun ExerciseDetailCard(exercise: Exercise, muscleMetadata: EffectiveMuscleMetadata? = null) {
    val context = LocalContext.current
    val translator = rememberMetadataTranslator()
    val displayName = localizedExerciseName(exercise)
    val displayDescription = localizedExerciseDescription(exercise)
    val isEnglish = LocalConfiguration.current.locales[0].language == Locale.ENGLISH.language
    val presenter = remember(context, isEnglish) { CanonicalMusclePresentation.from(context) }
    val muscleFields = resolvedMuscleDisplayFields(muscleMetadata, presenter, translator, isEnglish)
    val summary = if (isEnglish && !exercise.isCustom) {
        listOf(localizedUiText(translator.translate("exercise.category", exercise.category).orEmpty())) +
            muscleFields.filter { it.label in setOf("Primary muscles", "Secondary muscles") }
                .map { it.value }
    } else {
        listOf(
            translator.translate("exercise.category", exercise.category).orEmpty(),
            translator.translate("exercise.detail1", exercise.detail1).orEmpty(),
            translator.translate("exercise.detail2", exercise.detail2).orEmpty()
        )
    }.filter(String::isNotBlank).joinToString(" / ")
    val resolvedImageAssetName = remember(exercise.stableKey, exercise.imageAssetName) {
        ExerciseImageAssetMapping.resolve(context, exercise.stableKey, exercise.imageAssetName)
    }
    val bitmap = remember(resolvedImageAssetName) {
        runCatching {
            if (resolvedImageAssetName.isBlank()) {
                null
            } else {
                context.assets.open(resolvedImageAssetName).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        }.getOrNull()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "이미지 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            MaterialText(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = summary,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (displayDescription.isNotBlank()) {
                Text(
                    text = displayDescription,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "휴식 ${translator.translate("exercise.defaultRestSeconds", exercise.defaultRestSeconds.toString()).orEmpty()}",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
internal fun ExerciseInfoDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    metadata: RuntimeExerciseMetadata? = null,
    onEditMetadata: (() -> Unit)? = null,
    muscleMetadata: EffectiveMuscleMetadata? = null
) {
    val translator = rememberMetadataTranslator()
    val context = LocalContext.current
    val isEnglish = LocalConfiguration.current.locales[0].language == Locale.ENGLISH.language
    val presenter = remember(context, isEnglish) { CanonicalMusclePresentation.from(context) }
    val displayExercise = metadata
        ?.exerciseName
        ?.takeIf { name -> name.isNotBlank() }
        ?.let { name -> exercise.copy(name = name) }
        ?: exercise
    val sections = remember(displayExercise, metadata, muscleMetadata, translator, presenter, isEnglish) {
        displayExercise.infoSections(metadata, muscleMetadata, translator, presenter, isEnglish)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        title = { Text("운동 정보") },
        dismissButton = {
            if (onEditMetadata != null) {
                TextButton(onClick = onEditMetadata) {
                    Text("메타데이터 수정")
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ExerciseDetailCard(displayExercise, muscleMetadata)
                }
                items(sections, key = ExerciseInfoSection::title) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        section.fields.forEach { field ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = field.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = field.value,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

private data class ExerciseInfoSection(
    val title: String,
    val fields: List<ExerciseInfoField>
)

private data class ExerciseInfoField(
    val label: String,
    val value: String
)

private fun Exercise.infoSections(
    metadata: RuntimeExerciseMetadata?,
    muscleMetadata: EffectiveMuscleMetadata?,
    translator: MetadataTranslator,
    presenter: CanonicalMusclePresentation,
    isEnglish: Boolean
): List<ExerciseInfoSection> =
    listOf(
        ExerciseInfoSection(
            title = "주요 동작",
            fields = listOf(
                ExerciseInfoField(
                    "동작 패턴",
                    translator.translate("exercise.movementPattern", movementPattern).orEmpty()
                ),
                ExerciseInfoField(
                    "동작 분류",
                    translator.translate("exercise.movementCategory", movementCategory).orEmpty()
                ),
                ExerciseInfoField(
                    "축성 부하",
                    translator.translate("exercise.axialLoadLevel", axialLoadLevel).orEmpty()
                ),
                ExerciseInfoField(
                    "좌우 사용",
                    translator.translate("exercise.laterality", laterality).orEmpty()
                )
            )
        ),
        ExerciseInfoSection(
            title = "사용 근육과 장비",
            fields = resolvedMuscleDisplayFields(muscleMetadata, presenter, translator, isEnglish) +
                ExerciseInfoField("장비", equipment.ifBlank { equipmentTags }.localizedTokens(translator, "exercise.equipment"))
        ),
        ExerciseInfoSection(
            title = "프로그램 활용",
            fields = listOf(
                ExerciseInfoField(
                    "프로그램 역할",
                    translator.translate("runtime.programSlot", metadata?.programSlot.orEmpty()).orEmpty()
                ),
                ExerciseInfoField(
                    "메타데이터 상태",
                    translator.translate("exercise.metadataConfidence", metadataConfidence).orEmpty()
                )
            )
        ),
        ExerciseInfoSection(
            title = "스트레스와 회복",
            fields = listOf(
                ExerciseInfoField(
                    "주 스트레스",
                    translator.translate(
                        "runtime.primaryStressProfile",
                        metadata?.primaryStressProfile.orEmpty()
                    ).orEmpty()
                ),
                ExerciseInfoField(
                    "근신경계 피로",
                    translator.translate(
                        "runtime.neuromuscularStressLevel",
                        metadata?.neuromuscularStressLevel.orEmpty()
                    ).orEmpty()
                ),
                ExerciseInfoField(
                    "회복 기간",
                    translator.translate(
                        "runtime.recoveryDurationClass",
                        metadata?.recoveryDurationClass.orEmpty()
                    ).orEmpty()
                )
            )
        ),
        ExerciseInfoSection(
            title = "배드민턴 전이",
            fields = listOf(
                ExerciseInfoField(
                    "전이 수준",
                    translator.translate(
                        "runtime.badmintonTransferLevel",
                        metadata?.badmintonTransferLevel.orEmpty()
                    ).orEmpty()
                ),
                ExerciseInfoField(
                    "전이 목적",
                    metadata?.badmintonTransferType?.values
                        .orEmpty()
                        .let { values -> translator.translateTokens("runtime.badmintonTransferType", values) }
                        .joinToString(" · ")
                ),
                ExerciseInfoField(
                    "직접 전이",
                    sportTransferDirect.localizedTokens(translator, "exercise.sportTransferDirect")
                ),
                ExerciseInfoField(
                    "보조 전이",
                    sportTransferSupportive.localizedTokens(translator, "exercise.sportTransferSupportive")
                )
            )
        )
    ).mapNotNull { section ->
        val visibleFields = section.fields.filterNot { field -> field.value.isBlank() }
        section.copy(fields = visibleFields).takeIf { visibleFields.isNotEmpty() }
    }


/**
 * MUS-01/02: No built-in Exercise.primaryMuscles/secondaryMuscles read.
 * The only legacy path is explicit, verified compatibility data from MUS-00.5.
 * Labels and values remain in a full-width Column to avoid narrow label/value rows.
 */
private fun resolvedMuscleDisplayFields(
    effective: EffectiveMuscleMetadata?,
    presenter: CanonicalMusclePresentation,
    translator: MetadataTranslator,
    isEnglish: Boolean
): List<ExerciseInfoField> {
    if (effective == null) return emptyList() // No data yet; never guess that there are no muscles.
    val primary = if (isEnglish) "Primary muscles" else "주동근"
    val secondary = if (isEnglish) "Secondary muscles" else "보조근"
    if (!effective.usesCanonicalRoles) {
        val legacy = effective.legacy ?: return emptyList()
        val pendingReview = effective.source ==
            com.training.trackplanner.data.EffectiveMuscleMetadataSource.LEGACY_CUSTOM_EXERCISE
        val prefix = if (pendingReview) {
            if (isEnglish) "Previous (review required): " else "이전 정보(확인 필요) · "
        } else ""
        return listOf(
            ExerciseInfoField(prefix + primary, legacy.primaryMuscles.localizedTokens(translator, "exercise.primaryMuscles")),
            ExerciseInfoField(prefix + secondary, legacy.secondaryMuscles.localizedTokens(translator, "exercise.secondaryMuscles"))
        ).filter { it.value.isNotBlank() }
    }
    val roles = listOf(
        CanonicalMuscleRole.PRIMARY to primary,
        CanonicalMuscleRole.SECONDARY to secondary,
        CanonicalMuscleRole.STABILIZER to (if (isEnglish) "Stabilizing muscles" else "안정화 근육"),
        CanonicalMuscleRole.GENERAL_PARTICIPATION to (if (isEnglish) "Participating muscles" else "일반 참여 근육")
    )
    return roles.mapNotNull { (role, label) ->
        presenter.optionsForRole(effective, role)
            .map(MetadataDisplayOption::label)
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .takeIf(String::isNotBlank)
            ?.let { ExerciseInfoField(label, it) }
    }
}

private fun String.localizedTokens(
    translator: MetadataTranslator,
    fieldKey: String
): String =
    translator.translateTokens(fieldKey, split(',', '|', '/', ';')).joinToString(" · ")

@Composable
private fun Exercise.localizedRecordingMethod(): String {
    val label = when {
        (mode.contains("무게") || mode.contains("중량")) &&
            (mode.contains("거리") || mode.contains("시간")) ->
            R.string.exercise_recording_loaded_distance_or_time
        mode.contains("거리") && (mode.contains("횟수") || mode.contains("반복")) ->
            R.string.exercise_recording_distance_or_reps
        mode.contains("시간") && mode.contains("거리") ->
            R.string.exercise_recording_time_or_distance
        mode.contains("시간") && (mode.contains("횟수") || mode.contains("반복")) ->
            R.string.exercise_recording_time_or_reps
        mode.contains("무게") || mode.contains("중량") || category == "근력운동" ->
            R.string.exercise_recording_weight_and_reps
        mode.contains("거리") -> R.string.exercise_recording_distance
        mode.contains("시간") -> R.string.exercise_recording_time
        mode.contains("횟수") || mode.contains("반복") -> R.string.exercise_recording_reps
        else -> R.string.exercise_recording_exercise_specific
    }
    return stringResource(label)
}

/**
 * MUS-03: built-in muscle search uses stableKey canonical relations, not the
 * materialized legacy fields. Non-canonical exercises retain explicit import
 * compatibility until their one-time migration.
 */
internal fun Exercise.matchesMetadataQuery(
    query: String,
    translator: MetadataTranslator,
    muscleMetadata: EffectiveMuscleMetadata?,
    presenter: CanonicalMusclePresentation
): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty() || name.contains(normalized, ignoreCase = true)) return true

    val ordinary = listOf(
        translator.option("exercise.category", category),
        translator.option("exercise.mode", mode)
    ) + equipment.ifBlank { equipmentTags }.split(',', '|', '/', ';')
        .map { translator.option("exercise.equipment", it.trim()) }

    val muscles = when {
        muscleMetadata == null -> emptyList()
        // A built-in's canonical identity remains searchable even with old
        // explicit two-field overrides; never search stale built-in aliases.
        !isCustom && muscleMetadata.canonical.hasCanonicalRelations ->
            presenter.searchableOptions(muscleMetadata)
        muscleMetadata.usesCanonicalRoles -> presenter.searchableOptions(muscleMetadata)
        muscleMetadata.source ==
            com.training.trackplanner.data.EffectiveMuscleMetadataSource.LEGACY_CUSTOM_EXERCISE ->
            emptyList()
        else -> muscleMetadata.legacy?.let { legacy ->
            legacy.primaryMuscles.split(',', '|', '/', ';')
                .map { translator.option("exercise.primaryMuscles", it.trim()) } +
                legacy.secondaryMuscles.split(',', '|', '/', ';')
                    .map { translator.option("exercise.secondaryMuscles", it.trim()) }
        }.orEmpty()
    }
    return (ordinary + muscles).any { option -> option.matches(normalized) }
}

@Composable
internal fun MetricCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

internal fun screenPadding() = PaddingValues(
    start = 20.dp,
    top = 20.dp,
    end = 20.dp,
    bottom = 32.dp
)

internal fun formatWeight(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

internal fun formatDecimal(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

internal fun formatSeconds(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0초"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}분 ${seconds}초"
        minutes > 0 -> "${minutes}분"
        else -> "${seconds}초"
    }
}

internal fun programDayLabel(weekNumber: Int, dayOfWeek: Int): String =
    "${weekNumber}주차 ${dayName(dayOfWeek)}"

internal fun dayName(dayOfWeek: Int): String =
    when (dayOfWeek) {
        1 -> "월"
        2 -> "화"
        3 -> "수"
        4 -> "목"
        5 -> "금"
        6 -> "토"
        else -> "일"
    }

internal fun String.isUnsignedInt(): Boolean =
    isBlank() || all { it.isDigit() }

internal fun isDecimalInput(value: String): Boolean =
    value.isBlank() || value.matches(Regex("""\d*\.?\d*"""))
