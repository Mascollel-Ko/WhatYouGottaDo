package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.InitialUserProfile
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun InitialProfileDialog(
    profile: InitialUserProfile?,
    onDismiss: () -> Unit,
    onSave: (InitialUserProfile) -> Unit
) {
    val base = profile ?: InitialUserProfile()
    var bodyWeight by rememberSaveable { mutableStateOf(base.bodyWeightKg?.let(::formatDecimal).orEmpty()) }
    var height by rememberSaveable { mutableStateOf(base.heightCm?.let(::formatDecimal).orEmpty()) }
    var birthYear by rememberSaveable { mutableStateOf(base.birthYear?.toString().orEmpty()) }
    var birthYearError by rememberSaveable { mutableStateOf<String?>(null) }
    var sex by rememberSaveable { mutableStateOf(normalizeSex(base.sex.ifBlank { base.gender })) }
    var strengthSessions by rememberSaveable { mutableStateOf(base.strengthSessionsPerWeek?.let(::formatDecimal).orEmpty()) }
    var strengthMinutes by rememberSaveable { mutableStateOf(base.strengthMinutesPerSession?.toString().orEmpty()) }
    var strengthRpe by rememberSaveable { mutableStateOf(base.strengthAverageRpe?.roundToInt()?.toString().orEmpty()) }
    var badmintonSessions by rememberSaveable { mutableStateOf(base.badmintonSessionsPerWeek?.let(::formatDecimal).orEmpty()) }
    var badmintonMinutes by rememberSaveable { mutableStateOf(base.badmintonMinutesPerSession?.toString().orEmpty()) }
    var badmintonRpe by rememberSaveable { mutableStateOf(base.badmintonAverageRpe?.roundToInt()?.toString().orEmpty()) }
    var strengthYears by rememberSaveable { mutableStateOf((base.strengthTrainingYears ?: parseTrainingYears(base.strengthTrainingAge))?.let(::formatDecimal).orEmpty()) }
    var badmintonYears by rememberSaveable { mutableStateOf((base.badmintonTrainingYears ?: parseTrainingYears(base.badmintonTrainingAge))?.let(::formatDecimal).orEmpty()) }
    var breakCategory by rememberSaveable { mutableStateOf(base.trainingBreakCategory.ifBlank { breakWeeksToCategory(base.breakWeeks) }) }
    var breakReason by rememberSaveable { mutableStateOf(base.trainingBreakReason.ifBlank { if (base.breakDueToPain) "PAIN_OR_INJURY" else "NONE" }) }
    var squatKg by rememberSaveable { mutableStateOf(base.squatKg?.let(::formatDecimal).orEmpty()) }
    var deadliftKg by rememberSaveable { mutableStateOf(base.deadliftKg?.let(::formatDecimal).orEmpty()) }
    var benchKg by rememberSaveable { mutableStateOf(base.benchPressKg?.let(::formatDecimal).orEmpty()) }
    var pullUpReps by rememberSaveable { mutableStateOf(base.pullUpMaxReps?.toString().orEmpty()) }
    var pullUpWeight by rememberSaveable { mutableStateOf(base.pullUpAddedWeightKg?.let(::formatDecimal).orEmpty()) }
    var sleepHours by rememberSaveable { mutableStateOf((base.usualSleepHours ?: base.typicalSleepHours)?.let(::formatDecimal).orEmpty()) }
    var sleepQuality by rememberSaveable { mutableStateOf(base.sleepQuality?.toString().orEmpty()) }
    var fatigue by rememberSaveable { mutableStateOf(base.currentFatigue?.toString().orEmpty()) }
    var soreness by rememberSaveable { mutableStateOf(base.currentSoreness?.toString().orEmpty()) }
    var stress by rememberSaveable { mutableStateOf(base.currentStress?.toString().orEmpty()) }
    var condition by rememberSaveable { mutableStateOf((base.currentCondition ?: base.currentMood)?.toString().orEmpty()) }
    var painTags by rememberSaveable { mutableStateOf(base.painAreaTags.toTagSet()) }
    var avoidTags by rememberSaveable { mutableStateOf(base.avoidMovementTags.toTagSet()) }
    var goal by rememberSaveable { mutableStateOf(base.primaryGoal.ifBlank { "MIXED" }) }
    var freeNote by rememberSaveable { mutableStateOf(base.freeNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("초기 프로필") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("체중 kg", bodyWeight, Modifier.weight(1f), 0.0, 300.0) { bodyWeight = it }
                        DecimalField("키 cm", height, Modifier.weight(1f), 0.0, 250.0) { height = it }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntField("출생연도", birthYear, Modifier.weight(1f), 1900, LocalDate.now().year) {
                            birthYear = it
                            birthYearError = null
                        }
                    }
                    birthYearError?.let { Text(it) }
                }
                item { SingleChoice("성별", sexOptions, sex) { sex = it } }
                item {
                    Text("최근 4주 평균 운동 강도입니다. 1은 매우 쉬움, 10은 한계 수준입니다.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("근력 주당", strengthSessions, Modifier.weight(1f), 0.0, 14.0) { strengthSessions = it }
                        IntField("근력 분", strengthMinutes, Modifier.weight(1f), 0, 600) { strengthMinutes = it }
                    }
                }
                item { SingleChoice("근력 RPE", rpeOptions, strengthRpe) { strengthRpe = it } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("배드민턴 주당", badmintonSessions, Modifier.weight(1f), 0.0, 14.0) { badmintonSessions = it }
                        IntField("배드민턴 분", badmintonMinutes, Modifier.weight(1f), 0, 600) { badmintonMinutes = it }
                    }
                }
                item { SingleChoice("배드민턴 RPE", rpeOptions, badmintonRpe) { badmintonRpe = it } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("근력 경력 년", strengthYears, Modifier.weight(1f), 0.0, 80.0) { strengthYears = it }
                        DecimalField("배드민턴 경력 년", badmintonYears, Modifier.weight(1f), 0.0, 80.0) { badmintonYears = it }
                    }
                }
                item { SingleChoice("최근 운동 공백", breakOptions, breakCategory) { breakCategory = it } }
                item { SingleChoice("공백 이유", breakReasonOptions, breakReason) { breakReason = it } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("스쿼트 kg", squatKg, Modifier.weight(1f), 0.0, 500.0) { squatKg = it }
                        DecimalField("데드 kg", deadliftKg, Modifier.weight(1f), 0.0, 500.0) { deadliftKg = it }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("벤치 kg", benchKg, Modifier.weight(1f), 0.0, 500.0) { benchKg = it }
                        IntField("풀업 회", pullUpReps, Modifier.weight(1f), 0, 200) { pullUpReps = it }
                    }
                }
                item {
                    DecimalField("풀업 추가 kg", pullUpWeight, Modifier.fillMaxWidth(), 0.0, 200.0) { pullUpWeight = it }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("평소 수면 h", sleepHours, Modifier.weight(1f), 0.0, 24.0) { sleepHours = it }
                    }
                }
                item { SingleChoice("수면 질", scale5Options, sleepQuality) { sleepQuality = it } }
                item { SingleChoice("현재 피로감", scale5Options, fatigue) { fatigue = it } }
                item { SingleChoice("현재 근육통", scale5Options, soreness) { soreness = it } }
                item { SingleChoice("현재 스트레스", scale5Options, stress) { stress = it } }
                item { SingleChoice("현재 컨디션", scale5Options, condition) { condition = it } }
                item {
                    MultiChoice("통증/주의 부위", painOptions, painTags) { painTags = it }
                }
                item {
                    MultiChoice("피하고 싶은 움직임", avoidOptions, avoidTags) { avoidTags = it }
                }
                item { SingleChoice("주요 목표", goalOptions, goal) { goal = it } }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = freeNote,
                        onValueChange = { freeNote = it },
                        label = { Text("기타 메모") },
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val year = birthYear.toIntOrNull()
                    if (birthYear.isNotBlank() && (year == null || year !in 1900..LocalDate.now().year)) {
                        birthYearError = "출생연도는 1900년부터 올해까지만 저장됩니다."
                        return@Button
                    }
                    onSave(
                        base.copy(
                            bodyWeightKg = bodyWeight.toDoubleOrNull(),
                            heightCm = height.toDoubleOrNull(),
                            birthYearOrAgeRange = "",
                            gender = sex,
                            birthYear = year,
                            sex = sex,
                            strengthSessionsPerWeek = strengthSessions.toDoubleOrNull(),
                            strengthMinutesPerSession = strengthMinutes.toIntOrNull(),
                            strengthAverageRpe = strengthRpe.toDoubleOrNull(),
                            badmintonSessionsPerWeek = badmintonSessions.toDoubleOrNull(),
                            badmintonMinutesPerSession = badmintonMinutes.toIntOrNull(),
                            badmintonAverageRpe = badmintonRpe.toDoubleOrNull(),
                            strengthTrainingAge = "",
                            badmintonTrainingAge = "",
                            strengthTrainingYears = strengthYears.toDoubleOrNull(),
                            badmintonTrainingYears = badmintonYears.toDoubleOrNull(),
                            hadRecentTrainingBreak = breakCategory != "NONE",
                            breakWeeks = breakCategoryToWeeks(breakCategory),
                            breakDueToPain = breakReason == "PAIN_OR_INJURY",
                            trainingBreakCategory = breakCategory,
                            trainingBreakReason = breakReason,
                            squatKg = squatKg.toDoubleOrNull(),
                            deadliftKg = deadliftKg.toDoubleOrNull(),
                            benchPressKg = benchKg.toDoubleOrNull(),
                            pullUpMaxReps = pullUpReps.toIntOrNull(),
                            pullUpAddedWeightKg = pullUpWeight.toDoubleOrNull(),
                            typicalSleepHours = sleepHours.toDoubleOrNull(),
                            usualSleepHours = sleepHours.toDoubleOrNull(),
                            sleepQuality = sleepQuality.toIntOrNull(),
                            currentFatigue = fatigue.toIntOrNull(),
                            currentSoreness = soreness.toIntOrNull(),
                            currentStress = stress.toIntOrNull(),
                            currentMood = condition.toIntOrNull(),
                            currentCondition = condition.toIntOrNull(),
                            painAreas = "",
                            painAreaTags = painTags.toStoredTags(),
                            avoidedMovements = "",
                            avoidMovementTags = avoidTags.toStoredTags(),
                            goals = "",
                            primaryGoal = goal.ifBlank { "MIXED" },
                            freeNote = freeNote.trim()
                        )
                    )
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun SingleChoice(
    title: String,
    options: List<ProfileOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title)
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = selected == option.key,
                        onClick = { onSelect(option.key) },
                        label = { Text(option.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiChoice(
    title: String,
    options: List<ProfileOption>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title)
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = option.key in selected,
                        onClick = {
                            onChange(toggleTag(selected, option.key))
                        },
                        label = { Text(option.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: String,
    modifier: Modifier,
    min: Double,
    max: Double,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = {
            if (isDecimalInput(it) && (it.isBlank() || (it.toDoubleOrNull()?.let { number -> number in min..max } == true))) {
                onValueChange(it)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun IntField(
    label: String,
    value: String,
    modifier: Modifier,
    min: Int,
    max: Int,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = {
            if (it.isUnsignedInt() && (it.isBlank() || (it.toIntOrNull()?.let { number -> number in min..max } == true))) {
                onValueChange(it)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

private data class ProfileOption(val key: String, val label: String)

private val sexOptions = listOf(
    ProfileOption("UNSPECIFIED", "선택 안 함"),
    ProfileOption("MALE", "남성"),
    ProfileOption("FEMALE", "여성")
)
private val rpeOptions = (1..10).map { ProfileOption(it.toString(), it.toString()) }
private val scale5Options = (1..5).map { ProfileOption(it.toString(), it.toString()) }
private val breakOptions = listOf(
    ProfileOption("NONE", "없음"),
    ProfileOption("LESS_THAN_1_WEEK", "1주 미만"),
    ProfileOption("ONE_TO_TWO_WEEKS", "1~2주"),
    ProfileOption("THREE_TO_FOUR_WEEKS", "3~4주"),
    ProfileOption("FIVE_TO_EIGHT_WEEKS", "5~8주"),
    ProfileOption("MORE_THAN_EIGHT_WEEKS", "8주 이상")
)
private val breakReasonOptions = listOf(
    ProfileOption("NONE", "해당 없음"),
    ProfileOption("SCHEDULE", "바쁨/일정"),
    ProfileOption("FATIGUE", "피로 누적"),
    ProfileOption("PAIN_OR_INJURY", "통증/부상"),
    ProfileOption("ILLNESS", "질병"),
    ProfileOption("OTHER", "기타")
)
private val painOptions = listOf(
    ProfileOption("NONE", "없음"),
    ProfileOption("NECK", "목"),
    ProfileOption("SHOULDER", "어깨"),
    ProfileOption("ELBOW", "팔꿈치"),
    ProfileOption("WRIST_HAND", "손목/손"),
    ProfileOption("UPPER_BACK", "등 상부"),
    ProfileOption("LOW_BACK", "허리"),
    ProfileOption("HIP", "고관절"),
    ProfileOption("HAMSTRING", "햄스트링"),
    ProfileOption("KNEE", "무릎"),
    ProfileOption("CALF_ACHILLES", "종아리/아킬레스"),
    ProfileOption("ANKLE_FOOT", "발목/발"),
    ProfileOption("OTHER", "기타")
)
private val avoidOptions = listOf(
    ProfileOption("NONE", "없음"),
    ProfileOption("HEAVY_SQUAT", "무거운 스쿼트"),
    ProfileOption("HEAVY_DEADLIFT", "무거운 데드리프트"),
    ProfileOption("BENCH_OR_PUSH", "벤치/상체 푸시"),
    ProfileOption("OVERHEAD_PRESS", "오버헤드 프레스"),
    ProfileOption("JUMP_LANDING", "점프/착지"),
    ProfileOption("LUNGE_DECELERATION", "런지/감속"),
    ProfileOption("ROTATION", "회전 동작"),
    ProfileOption("LONG_BADMINTON", "장시간 배드민턴"),
    ProfileOption("HIGH_INTENSITY_INTERVAL", "고강도 인터벌"),
    ProfileOption("OTHER", "기타")
)
private val goalOptions = listOf(
    ProfileOption("BADMINTON_PERFORMANCE", "배드민턴 경기력"),
    ProfileOption("STRENGTH_GAIN", "근력 향상"),
    ProfileOption("STRENGTH_MAINTENANCE", "근력 유지"),
    ProfileOption("HYPERTROPHY_PHYSIQUE", "근비대/체형"),
    ProfileOption("RECOVERY_INJURY_PREVENTION", "회복/부상 방지"),
    ProfileOption("WEIGHT_MANAGEMENT", "체중 관리"),
    ProfileOption("MIXED", "혼합")
)

private fun normalizeSex(value: String): String =
    when (value.trim().lowercase()) {
        "male", "m", "남", "남성", "MALE".lowercase() -> "MALE"
        "female", "f", "여", "여성", "FEMALE".lowercase() -> "FEMALE"
        else -> "UNSPECIFIED"
    }

private fun String.toTagSet(): Set<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
        .ifEmpty { setOf("NONE") }

private fun Set<String>.toStoredTags(): String =
    if (isEmpty() || this == setOf("NONE")) "NONE" else filter { it != "NONE" }.sorted().joinToString(",")

private fun toggleTag(selected: Set<String>, key: String): Set<String> {
    if (key == "NONE") return setOf("NONE")
    val next = selected.filter { it != "NONE" }.toMutableSet()
    if (!next.add(key)) next.remove(key)
    return next.ifEmpty { setOf("NONE") }
}

private fun breakWeeksToCategory(weeks: Int?): String =
    when {
        weeks == null || weeks <= 0 -> "NONE"
        weeks < 1 -> "LESS_THAN_1_WEEK"
        weeks <= 2 -> "ONE_TO_TWO_WEEKS"
        weeks <= 4 -> "THREE_TO_FOUR_WEEKS"
        weeks <= 8 -> "FIVE_TO_EIGHT_WEEKS"
        else -> "MORE_THAN_EIGHT_WEEKS"
    }

private fun breakCategoryToWeeks(category: String): Int? =
    when (category) {
        "LESS_THAN_1_WEEK" -> 1
        "ONE_TO_TWO_WEEKS" -> 2
        "THREE_TO_FOUR_WEEKS" -> 4
        "FIVE_TO_EIGHT_WEEKS" -> 8
        "MORE_THAN_EIGHT_WEEKS" -> 9
        else -> null
    }

private fun parseTrainingYears(value: String): Double? {
    val normalized = value.trim().lowercase()
    if (normalized == "반년") return 0.5
    return Regex("""\d+(\.\d+)?""").find(normalized)?.value?.toDoubleOrNull()
}
