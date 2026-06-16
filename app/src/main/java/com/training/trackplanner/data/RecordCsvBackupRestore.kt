package com.training.trackplanner.data

import java.time.LocalDate
import java.util.Locale

data class RecordCsvTransferResult(
    val format: String,
    val exerciseCount: Int = 0,
    val dailyMetricCount: Int = 0,
    val profileCount: Int = 0,
    val entryCount: Int = 0,
    val setCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val warningCount: Int = 0
) {
    fun summaryText(action: String): String =
        "$action 완료: profile $profileCount, daily $dailyMetricCount, entry $entryCount, set $setCount, skip $skippedDuplicateCount"
}

sealed class RecordCsvImportData {
    data class Restore(
        val exerciseRows: List<RestoreExerciseRow>,
        val profileRows: List<RestoreProfileRow>,
        val dailyRows: List<RestoreDailyRow>,
        val setRows: List<RestoreSetRow>,
        val warningCount: Int
    ) : RecordCsvImportData()

    data class DailyTimeseries(
        val rows: List<DailyTimeseriesRow>,
        val warningCount: Int
    ) : RecordCsvImportData()
}

data class RestoreDailyRow(
    val date: String,
    val sleepHours: Double?,
    val bodyWeightKg: Double?
)

data class RestoreProfileRow(
    val key: String,
    val value: String
)

data class RestoreExerciseRow(
    val name: String,
    val stableKey: String,
    val category: String,
    val detail1: String,
    val detail2: String,
    val mode: String,
    val description: String,
    val defaultRestSeconds: Int,
    val imageAssetName: String,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val equipment: String,
    val movementPattern: String,
    val movementCategory: String,
    val forceType: String,
    val bodyRegion: String,
    val laterality: String,
    val plane: String,
    val trainingRole: String,
    val sportTransferDirect: String,
    val sportTransferSupportive: String,
    val loadProfile: String,
    val metadataConfidence: String,
    val isActive: Boolean,
    val isCustom: Boolean,
    val needsReview: Boolean
)

data class RestoreSetRow(
    val date: String,
    val entryKey: String,
    val entryOrder: Int,
    val exerciseName: String,
    val category: String,
    val confirmed: Boolean,
    val restSeconds: Int,
    val rpe: Double?,
    val maxReps: Int?,
    val notes: String,
    val setIndex: Int,
    val setConfirmed: Boolean,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val sleepHours: Double?,
    val bodyWeightKg: Double?
)

data class DailyTimeseriesRow(
    val date: String,
    val sleepHours: Double?,
    val bodyWeightKg: Double?,
    val totalEntries: Int,
    val confirmedEntries: Int,
    val plannedEntries: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalTonnageKg: Double,
    val totalSeconds: Int,
    val strengthEntries: Int,
    val functionalEntries: Int,
    val cardioEntries: Int,
    val sportsEntries: Int,
    val exercisesSummary: String
)

object RecordCsvBackupRestore {
    private val restoreHeader = listOf(
        "schema_version",
        "row_type",
        "date",
        "entry_key",
        "entry_order",
        "exercise_name",
        "category",
        "confirmed",
        "rest_seconds",
        "rpe",
        "max_reps",
        "notes",
        "set_index",
        "set_confirmed",
        "reps",
        "weight_kg",
        "seconds",
        "sleep_hours",
        "body_weight_kg",
        "stable_key",
        "description",
        "default_rest_seconds",
        "image_asset_name",
        "primary_muscles",
        "secondary_muscles",
        "equipment",
        "movement_pattern",
        "movement_category",
        "force_type",
        "body_region",
        "laterality",
        "plane",
        "training_role",
        "sport_transfer_direct",
        "sport_transfer_supportive",
        "load_profile",
        "metadata_confidence",
        "is_active",
        "is_custom",
        "needs_review",
        "detail1",
        "detail2",
        "mode",
        "profile_key",
        "profile_value"
    )

    fun buildRestoreCsv(
        entriesWithSets: List<WorkoutEntryWithSets>,
        metrics: List<DailyMetric>,
        exercises: List<Exercise> = emptyList(),
        initialProfile: InitialUserProfile? = null
    ): String {
        val builder = StringBuilder()
        builder.appendLine(restoreHeader.joinToString(","))
        initialProfile?.toCsvPairs()?.forEach { (key, value) ->
            builder.appendCsvRow(
                restoreHeader.map { column ->
                    when (column) {
                        "schema_version" -> "1"
                        "row_type" -> "profile"
                        "profile_key" -> key
                        "profile_value" -> value
                        else -> ""
                    }
                }
            )
        }
        exercises.sortedBy { exercise -> exercise.name }.forEach { exercise ->
            builder.appendCsvRow(
                listOf(
                    "1",
                    "exercise",
                    "",
                    "",
                    "",
                    exercise.name,
                    exercise.category,
                    "",
                    exercise.defaultRestSeconds.toString(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    exercise.stableKey,
                    exercise.description,
                    exercise.defaultRestSeconds.toString(),
                    exercise.imageAssetName,
                    exercise.primaryMuscles,
                    exercise.secondaryMuscles,
                    exercise.equipment.ifBlank { exercise.equipmentTags },
                    exercise.movementPattern,
                    exercise.movementCategory,
                    exercise.forceType,
                    exercise.bodyRegion,
                    exercise.laterality,
                    exercise.plane,
                    exercise.trainingRole,
                    exercise.sportTransferDirect,
                    exercise.sportTransferSupportive,
                    exercise.loadProfile,
                    exercise.metadataConfidence,
                    exercise.isActive.toCsvBool(),
                    exercise.isCustom.toCsvBool(),
                    exercise.needsReview.toCsvBool(),
                    exercise.detail1,
                    exercise.detail2,
                    exercise.mode
                )
            )
        }
        val metricsByDate = metrics.associateBy { metric -> metric.date }
        val dates = (entriesWithSets.map { item -> item.entry.date } + metrics.map { metric -> metric.date })
            .distinct()
            .sorted()
        dates.forEach { date ->
            val metric = metricsByDate[date]
            if (metric != null) {
                builder.appendCsvRow(
                    listOf(
                        "1",
                        "daily",
                        date,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        metric.sleepHours.formatOptional(),
                        metric.bodyWeightKg.formatOptional()
                    )
                )
            }
        }
        entriesWithSets
            .groupBy { item -> item.entry.date }
            .toSortedMap()
            .forEach { (_, entriesForDate) ->
                entriesForDate.forEachIndexed { entryIndex, item ->
                    val entry = item.entry
                    val entryConfirmed = item.sets.any { set -> set.confirmed }
                    val orderedSets = item.sets.sortedBy { set -> set.setIndex }
                    orderedSets.forEach { set ->
                        builder.appendCsvRow(
                            listOf(
                                "1",
                                "set",
                                entry.date,
                                entry.id.toString(),
                                (entryIndex + 1).toString(),
                                entry.exerciseName,
                                entry.category,
                                entryConfirmed.toCsvBool(),
                                entry.restSeconds.toString(),
                                (set.rpe ?: entry.rpe).formatOptional(),
                                entry.maxReps?.toString().orEmpty(),
                                entry.notes,
                                set.setIndex.toString(),
                                set.confirmed.toCsvBool(),
                                set.reps.toString(),
                                set.weightKg.formatNumber(),
                                set.seconds.toString(),
                                "",
                                ""
                            )
                        )
                    }
                }
            }
        return builder.toString()
    }

    fun parse(text: String): RecordCsvImportData {
        val rows = text.lineSequence()
            .filter { line -> line.isNotBlank() }
            .map(::parseCsvLine)
            .toList()
        if (rows.isEmpty()) {
            return RecordCsvImportData.Restore(emptyList(), emptyList(), emptyList(), emptyList(), warningCount = 1)
        }
        val header = rows.first().map { value -> value.trim() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        return if ("row_type" in index) {
            parseRestore(rows.drop(1), index)
        } else {
            parseDailyTimeseries(rows.drop(1), index)
        }
    }

    private fun parseRestore(
        rows: List<List<String>>,
        index: Map<String, Int>
    ): RecordCsvImportData.Restore {
        var warnings = 0
        val exerciseRows = mutableListOf<RestoreExerciseRow>()
        val profileRows = mutableListOf<RestoreProfileRow>()
        val dailyRows = mutableListOf<RestoreDailyRow>()
        val setRows = mutableListOf<RestoreSetRow>()
        rows.forEachIndexed { rowIndex, row ->
            val rowType = row.value(index, "row_type").trim().lowercase(Locale.US)
            if (rowType == "profile") {
                val key = row.value(index, "profile_key").trim()
                if (key.isBlank()) {
                    warnings += 1
                } else {
                    profileRows += RestoreProfileRow(
                        key = key,
                        value = row.value(index, "profile_value")
                    )
                }
                return@forEachIndexed
            }
            if (rowType == "exercise") {
                val name = row.value(index, "exercise_name").trim()
                if (name.isBlank()) {
                    warnings += 1
                } else {
                    exerciseRows += RestoreExerciseRow(
                        name = name,
                        stableKey = row.value(index, "stable_key"),
                        category = row.value(index, "category"),
                        detail1 = row.value(index, "detail1"),
                        detail2 = row.value(index, "detail2"),
                        mode = row.value(index, "mode"),
                        description = row.value(index, "description"),
                        defaultRestSeconds = row.safeInt(index, "default_rest_seconds")
                            ?: row.safeInt(index, "rest_seconds")
                            ?: 60,
                        imageAssetName = row.value(index, "image_asset_name"),
                        primaryMuscles = row.value(index, "primary_muscles"),
                        secondaryMuscles = row.value(index, "secondary_muscles"),
                        equipment = row.value(index, "equipment"),
                        movementPattern = row.value(index, "movement_pattern"),
                        movementCategory = row.value(index, "movement_category"),
                        forceType = row.value(index, "force_type"),
                        bodyRegion = row.value(index, "body_region"),
                        laterality = row.value(index, "laterality"),
                        plane = row.value(index, "plane"),
                        trainingRole = row.value(index, "training_role"),
                        sportTransferDirect = row.value(index, "sport_transfer_direct"),
                        sportTransferSupportive = row.value(index, "sport_transfer_supportive"),
                        loadProfile = row.value(index, "load_profile"),
                        metadataConfidence = row.value(index, "metadata_confidence"),
                        isActive = row.safeBool(index, "is_active") ?: true,
                        isCustom = row.safeBool(index, "is_custom") ?: false,
                        needsReview = row.safeBool(index, "needs_review") ?: false
                    )
                }
                return@forEachIndexed
            }
            val date = row.value(index, "date").trim()
            if (!date.isValidDate()) {
                warnings += 1
                return@forEachIndexed
            }
            when (rowType) {
                "daily" -> dailyRows += RestoreDailyRow(
                    date = date,
                    sleepHours = row.safeDouble(index, "sleep_hours"),
                    bodyWeightKg = row.safeDouble(index, "body_weight_kg")
                )
                "set" -> setRows += RestoreSetRow(
                    date = date,
                    entryKey = row.value(index, "entry_key").ifBlank { "fallback-$date-$rowIndex" },
                    entryOrder = row.safeInt(index, "entry_order") ?: rowIndex + 1,
                    exerciseName = row.value(index, "exercise_name").ifBlank { "CSV 복원 운동" },
                    category = row.value(index, "category").ifBlank { "근력운동" },
                    confirmed = row.safeBool(index, "confirmed") ?: true,
                    restSeconds = row.safeInt(index, "rest_seconds") ?: 60,
                    rpe = row.safeDouble(index, "rpe"),
                    maxReps = row.safeInt(index, "max_reps"),
                    notes = row.value(index, "notes"),
                    setIndex = row.safeInt(index, "set_index") ?: 1,
                    setConfirmed = row.safeBool(index, "set_confirmed")
                        ?: row.safeBool(index, "confirmed")
                        ?: true,
                    reps = row.safeInt(index, "reps") ?: 0,
                    weightKg = row.safeDouble(index, "weight_kg") ?: 0.0,
                    seconds = row.safeInt(index, "seconds") ?: 0,
                    sleepHours = row.safeDouble(index, "sleep_hours"),
                    bodyWeightKg = row.safeDouble(index, "body_weight_kg")
                )
                else -> warnings += 1
            }
        }
        return RecordCsvImportData.Restore(exerciseRows, profileRows, dailyRows, setRows, warnings)
    }

    private fun parseDailyTimeseries(
        rows: List<List<String>>,
        index: Map<String, Int>
    ): RecordCsvImportData.DailyTimeseries {
        var warnings = 0
        val parsed = rows.mapNotNull { row ->
            val date = row.value(index, "date").trim()
            if (!date.isValidDate()) {
                warnings += 1
                return@mapNotNull null
            }
            DailyTimeseriesRow(
                date = date,
                sleepHours = row.safeDouble(index, "sleep_hours"),
                bodyWeightKg = row.safeDouble(index, "body_weight_kg"),
                totalEntries = row.safeInt(index, "total_entries") ?: 0,
                confirmedEntries = row.safeInt(index, "confirmed_entries") ?: 0,
                plannedEntries = row.safeInt(index, "planned_entries") ?: 0,
                totalSets = row.safeInt(index, "total_sets") ?: 0,
                totalReps = row.safeInt(index, "total_reps") ?: 0,
                totalTonnageKg = row.safeDouble(index, "total_tonnage_kg") ?: 0.0,
                totalSeconds = row.safeInt(index, "total_seconds") ?: 0,
                strengthEntries = row.safeInt(index, "strength_entries") ?: 0,
                functionalEntries = row.safeInt(index, "functional_entries") ?: 0,
                cardioEntries = row.safeInt(index, "cardio_entries") ?: 0,
                sportsEntries = row.safeInt(index, "sports_entries") ?: 0,
                exercisesSummary = row.value(index, "exercises_summary")
            )
        }
        return RecordCsvImportData.DailyTimeseries(parsed, warnings)
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString()
        return values
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        appendLine(values.joinToString(",") { value -> value.escapeCsv() })
    }

    private fun String.escapeCsv(): String =
        if (contains(',') || contains('"') || contains('\n') || contains('\r')) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }

    private fun List<String>.value(index: Map<String, Int>, key: String): String =
        index[key]?.let { i -> getOrNull(i) }.orEmpty()

    private fun List<String>.safeDouble(index: Map<String, Int>, key: String): Double? =
        value(index, key).trim().takeIf { value -> value.isNotEmpty() }?.toDoubleOrNull()

    private fun List<String>.safeInt(index: Map<String, Int>, key: String): Int? =
        value(index, key).trim().takeIf { value -> value.isNotEmpty() }?.toIntOrNull()

    private fun List<String>.safeBool(index: Map<String, Int>, key: String): Boolean? =
        when (value(index, key).trim().lowercase(Locale.US)) {
            "1", "true", "yes", "y" -> true
            "0", "false", "no", "n" -> false
            else -> null
        }

    private fun String.isValidDate(): Boolean =
        runCatching { LocalDate.parse(this) }.isSuccess

    private fun Boolean.toCsvBool(): String = if (this) "1" else "0"

    private fun Double?.formatOptional(): String = this?.formatNumber().orEmpty()

    private fun Double.formatNumber(): String =
        if (this % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", this)
        } else {
            String.format(Locale.US, "%.3f", this).trimEnd('0').trimEnd('.')
        }

    private fun InitialUserProfile.toCsvPairs(): List<Pair<String, String>> =
        listOf(
            "bodyWeightKg" to bodyWeightKg.formatOptional(),
            "heightCm" to heightCm.formatOptional(),
            "birthYearOrAgeRange" to birthYearOrAgeRange,
            "gender" to gender,
            "birthYear" to birthYear?.toString().orEmpty(),
            "sex" to sex,
            "strengthSessionsPerWeek" to strengthSessionsPerWeek.formatOptional(),
            "strengthMinutesPerSession" to strengthMinutesPerSession?.toString().orEmpty(),
            "strengthAverageRpe" to strengthAverageRpe.formatOptional(),
            "badmintonSessionsPerWeek" to badmintonSessionsPerWeek.formatOptional(),
            "badmintonMinutesPerSession" to badmintonMinutesPerSession?.toString().orEmpty(),
            "badmintonAverageRpe" to badmintonAverageRpe.formatOptional(),
            "strengthTrainingAge" to strengthTrainingAge,
            "badmintonTrainingAge" to badmintonTrainingAge,
            "strengthTrainingYears" to strengthTrainingYears.formatOptional(),
            "badmintonTrainingYears" to badmintonTrainingYears.formatOptional(),
            "hadRecentTrainingBreak" to hadRecentTrainingBreak.toCsvBool(),
            "breakWeeks" to breakWeeks?.toString().orEmpty(),
            "breakDueToPain" to breakDueToPain.toCsvBool(),
            "trainingBreakCategory" to trainingBreakCategory,
            "trainingBreakReason" to trainingBreakReason,
            "squatLevel" to squatLevel,
            "deadliftLevel" to deadliftLevel,
            "benchPressLevel" to benchPressLevel,
            "pullUpLevel" to pullUpLevel,
            "squatKg" to squatKg.formatOptional(),
            "deadliftKg" to deadliftKg.formatOptional(),
            "benchPressKg" to benchPressKg.formatOptional(),
            "pullUpMaxReps" to pullUpMaxReps?.toString().orEmpty(),
            "pullUpAddedWeightKg" to pullUpAddedWeightKg.formatOptional(),
            "typicalSleepHours" to typicalSleepHours.formatOptional(),
            "usualSleepHours" to usualSleepHours.formatOptional(),
            "sleepQuality" to sleepQuality?.toString().orEmpty(),
            "currentFatigue" to currentFatigue?.toString().orEmpty(),
            "currentSoreness" to currentSoreness?.toString().orEmpty(),
            "currentStress" to currentStress?.toString().orEmpty(),
            "currentMood" to currentMood?.toString().orEmpty(),
            "currentCondition" to currentCondition?.toString().orEmpty(),
            "painAreas" to painAreas,
            "painAreaTags" to painAreaTags,
            "avoidedMovements" to avoidedMovements,
            "avoidMovementTags" to avoidMovementTags,
            "goals" to goals,
            "primaryGoal" to primaryGoal,
            "secondaryGoalTags" to secondaryGoalTags,
            "freeNote" to freeNote,
            "createdAt" to createdAt.toString(),
            "updatedAt" to updatedAt.toString()
        )
}
