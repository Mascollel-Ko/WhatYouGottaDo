package com.training.trackplanner.data

import java.time.LocalDate
import java.util.Locale

data class RecordCsvTransferResult(
    val format: String,
    val exerciseCount: Int = 0,
    val dailyMetricCount: Int = 0,
    val dailyCheckInCount: Int = 0,
    val smashSpeedCount: Int = 0,
    val profileCount: Int = 0,
    val entryCount: Int = 0,
    val setCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val warningCount: Int = 0
) {
    fun summaryText(action: String): String =
        "$action 완료: profile $profileCount, daily $dailyMetricCount, check-in $dailyCheckInCount, entry $entryCount, set $setCount, skip $skippedDuplicateCount"
}

sealed class RecordCsvImportData {
    data class Restore(
        val exerciseRows: List<RestoreExerciseRow>,
        val profileRows: List<RestoreProfileRow>,
        val dailyRows: List<RestoreDailyRow>,
        val setRows: List<RestoreSetRow>,
        val warningCount: Int,
        val checkInRows: List<RestoreCheckInRow> = emptyList(),
        val smashSpeedRows: List<RestoreSmashSpeedRow> = emptyList(),
        val runtimeMetadataRows: List<RuntimeExerciseMetadata> = emptyList()
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

data class RestoreCheckInRow(
    val date: String,
    val sleepHours: Double?,
    val overallFatigue: Int?,
    val lowerBodyFatigue: Int?,
    val jointTendonDiscomfort: Int?,
    val focusMotivation: Int?,
    val note: String?,
    val createdAt: Long?,
    val updatedAt: Long?
)

data class RestoreSmashSpeedRow(
    val date: String,
    val smashSpeedId: Long?,
    val speedKmh: Double,
    val attemptIndex: Int?,
    val source: String?,
    val note: String?,
    val parentWorkoutEntryId: Long?,
    val createdAt: Long?,
    val updatedAt: Long?
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
    val stableKey: String,
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
        "profile_value",
        "overall_fatigue",
        "lower_body_fatigue",
        "joint_tendon_discomfort",
        "focus_motivation",
        "checkin_note",
        "checkin_created_at",
        "checkin_updated_at",
        "smash_speed_id",
        "speed_kmh",
        "attempt_index",
        "source",
        "smash_note",
        "parent_workout_entry_id",
        "smash_created_at",
        "smash_updated_at",
        "runtime_activity_kind",
        "runtime_planning_eligibility",
        "runtime_movement_family",
        "runtime_movement_subtype",
        "runtime_program_slot",
        "runtime_redundancy_group",
        "runtime_progress_metric_type",
        "runtime_strength_progression_group",
        "runtime_analysis_eligibility",
        "runtime_primary_stress_profile",
        "runtime_secondary_stress_tags",
        "runtime_tendon_stress_tags",
        "runtime_ligament_joint_stability_stress_tags",
        "runtime_joint_impact_stress_tags",
        "runtime_cognitive_stress_tags",
        "runtime_sport_context_tags",
        "runtime_recovery_decay_profile",
        "runtime_stress_magnitude_hint",
        "runtime_badminton_transfer_level",
        "runtime_badminton_transfer_type",
        "runtime_badminton_skill_targets",
        "runtime_badminton_physical_qualities",
        "runtime_transfer_confidence",
        "runtime_source_confidence_level",
        "runtime_final_source_status",
        "runtime_neuromuscular_stress_level",
        "runtime_systemic_muscular_stress_level",
        "runtime_local_muscular_stress_level",
        "runtime_joint_tendon_impact_stress_level",
        "runtime_movement_focus_demand_level",
        "runtime_recovery_duration_class",
        "runtime_app_cue_profile"
    )

    fun buildRestoreCsv(
        entriesWithSets: List<WorkoutEntryWithSets>,
        metrics: List<DailyMetric>,
        exercises: List<Exercise> = emptyList(),
        initialProfile: InitialUserProfile? = null,
        checkIns: List<DailyCheckIn> = emptyList(),
        smashSpeeds: List<SmashSpeedRecord> = emptyList(),
        runtimeMetadata: List<RuntimeExerciseMetadata> = emptyList()
    ): String {
        val builder = StringBuilder()
        val exercisesById = exercises.associateBy { exercise -> exercise.id }
        fun MetadataTokenField.exportRaw(): String = raw.ifBlank { values.joinToString("|") }
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
        runtimeMetadata
            .filter { metadata -> metadata.stableKey.isNotBlank() }
            .sortedBy { metadata -> metadata.stableKey }
            .forEach { metadata ->
                builder.appendCsvRow(
                    restoreHeader.map { column ->
                        when (column) {
                            "schema_version" -> "4"
                            "row_type" -> "runtime_metadata"
                            "exercise_name" -> metadata.exerciseName
                            "stable_key" -> metadata.stableKey
                            "runtime_activity_kind" -> metadata.activityKind
                            "runtime_planning_eligibility" -> metadata.planningEligibility
                            "runtime_movement_family" -> metadata.movementFamily
                            "runtime_movement_subtype" -> metadata.movementSubtype
                            "runtime_program_slot" -> metadata.programSlot
                            "runtime_redundancy_group" -> metadata.redundancyGroup
                            "runtime_progress_metric_type" -> metadata.progressMetricType
                            "runtime_strength_progression_group" -> metadata.strengthProgressionGroup
                            "runtime_analysis_eligibility" -> metadata.analysisEligibility.exportRaw()
                            "runtime_primary_stress_profile" -> metadata.primaryStressProfile
                            "runtime_secondary_stress_tags" -> metadata.secondaryStressTags.exportRaw()
                            "runtime_tendon_stress_tags" -> metadata.tendonStressTags.exportRaw()
                            "runtime_ligament_joint_stability_stress_tags" -> metadata.ligamentJointStabilityStressTags.exportRaw()
                            "runtime_joint_impact_stress_tags" -> metadata.jointImpactStressTags.exportRaw()
                            "runtime_cognitive_stress_tags" -> metadata.cognitiveStressTags.exportRaw()
                            "runtime_sport_context_tags" -> metadata.sportContextTags.exportRaw()
                            "runtime_recovery_decay_profile" -> metadata.recoveryDecayProfile
                            "runtime_stress_magnitude_hint" -> metadata.stressMagnitudeHint
                            "runtime_badminton_transfer_level" -> metadata.badmintonTransferLevel
                            "runtime_badminton_transfer_type" -> metadata.badmintonTransferType.exportRaw()
                            "runtime_badminton_skill_targets" -> metadata.badmintonSkillTargets.exportRaw()
                            "runtime_badminton_physical_qualities" -> metadata.badmintonPhysicalQualities.exportRaw()
                            "runtime_transfer_confidence" -> metadata.transferConfidence
                            "runtime_source_confidence_level" -> metadata.sourceConfidenceLevel
                            "runtime_final_source_status" -> metadata.finalSourceStatus
                            "runtime_neuromuscular_stress_level" -> metadata.neuromuscularStressLevel
                            "runtime_systemic_muscular_stress_level" -> metadata.systemicMuscularStressLevel
                            "runtime_local_muscular_stress_level" -> metadata.localMuscularStressLevel
                            "runtime_joint_tendon_impact_stress_level" -> metadata.jointTendonImpactStressLevel
                            "runtime_movement_focus_demand_level" -> metadata.movementFocusDemandLevel
                            "runtime_recovery_duration_class" -> metadata.recoveryDurationClass
                            "runtime_app_cue_profile" -> metadata.appCueProfile
                            else -> ""
                        }
                    }
                )
            }
        val metricsByDate = metrics.associateBy { metric -> metric.date }
        val checkInsByDate = checkIns.associateBy { checkIn -> checkIn.date }
        val dates = (
            entriesWithSets.map { item -> item.entry.date } +
                metrics.map { metric -> metric.date } +
                checkIns
                    .filter { checkIn -> checkIn.sleepHours != null || checkIn.bodyWeightKg != null }
                    .map { checkIn -> checkIn.date }
            )
            .distinct()
            .sorted()
        dates.forEach { date ->
            val metric = metricsByDate[date]
            val checkIn = checkInsByDate[date]
            if (metric != null || checkIn?.sleepHours != null || checkIn?.bodyWeightKg != null) {
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
                        (metric?.sleepHours ?: checkIn?.sleepHours).formatOptional(),
                        (metric?.bodyWeightKg ?: checkIn?.bodyWeightKg).formatOptional()
                    )
                )
            }
        }
        checkIns.sortedBy { checkIn -> checkIn.date }.forEach { checkIn ->
            builder.appendCsvRow(
                restoreHeader.map { column ->
                    when (column) {
                        "schema_version" -> "2"
                        "row_type" -> "check_in"
                        "date" -> checkIn.date
                        "sleep_hours" -> ""
                        "overall_fatigue" -> checkIn.overallFatigue?.toString().orEmpty()
                        "lower_body_fatigue" -> checkIn.lowerBodyFatigue?.toString().orEmpty()
                        "joint_tendon_discomfort" -> checkIn.jointTendonDiscomfort?.toString().orEmpty()
                        "focus_motivation" -> checkIn.focusMotivation?.toString().orEmpty()
                        "checkin_note" -> checkIn.note.orEmpty()
                        "checkin_created_at" -> checkIn.createdAt.toString()
                        "checkin_updated_at" -> checkIn.updatedAt.toString()
                        else -> ""
                    }
                }
            )
        }
        smashSpeeds
            .sortedWith(compareBy<SmashSpeedRecord> { it.date }.thenBy { it.attemptIndex ?: Int.MAX_VALUE }.thenBy { it.id })
            .forEach { record ->
                builder.appendCsvRow(
                    restoreHeader.map { column ->
                        when (column) {
                            "schema_version" -> "3"
                            "row_type" -> "smash_speed"
                            "date" -> record.date
                            "smash_speed_id" -> record.id.takeIf { it > 0 }?.toString().orEmpty()
                            "speed_kmh" -> record.speedKmh.formatNumber()
                            "attempt_index" -> record.attemptIndex?.toString().orEmpty()
                            "source" -> record.source
                            "smash_note" -> record.note.orEmpty()
                            "parent_workout_entry_id" -> record.parentWorkoutEntryId?.toString().orEmpty()
                            "smash_created_at" -> record.createdAt.toString()
                            "smash_updated_at" -> record.updatedAt.toString()
                            else -> ""
                        }
                    }
                )
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
                                "",
                                exercisesById[entry.exerciseId]?.stableKey.orEmpty()
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
        val checkInRows = mutableListOf<RestoreCheckInRow>()
        val smashSpeedRows = mutableListOf<RestoreSmashSpeedRow>()
        val runtimeMetadataRows = mutableListOf<RuntimeExerciseMetadata>()
        rows.forEachIndexed { rowIndex, row ->
            val rowType = row.value(index, "row_type").trim().lowercase(Locale.US)
            if (rowType == "runtime_metadata") {
                val stableKey = row.value(index, "stable_key").trim()
                val exerciseName = row.value(index, "exercise_name").trim()
                if (stableKey.isBlank()) {
                    warnings += 1
                } else {
                    val base = RuntimeExerciseMetadataDefaults.forIdentity(stableKey, exerciseName)
                    fun value(column: String, fallback: String): String =
                        row.value(index, column).ifBlank { fallback }
                    runtimeMetadataRows += base.copy(
                        activityKind = value("runtime_activity_kind", base.activityKind),
                        planningEligibility = value("runtime_planning_eligibility", base.planningEligibility),
                        movementFamily = value("runtime_movement_family", base.movementFamily),
                        movementSubtype = value("runtime_movement_subtype", base.movementSubtype),
                        programSlot = value("runtime_program_slot", base.programSlot),
                        redundancyGroup = value("runtime_redundancy_group", base.redundancyGroup),
                        progressMetricType = value("runtime_progress_metric_type", base.progressMetricType),
                        strengthProgressionGroup = value("runtime_strength_progression_group", base.strengthProgressionGroup),
                        analysisEligibility = MetadataTokenField.parse(value("runtime_analysis_eligibility", base.analysisEligibility.raw)),
                        primaryStressProfile = value("runtime_primary_stress_profile", base.primaryStressProfile),
                        secondaryStressTags = MetadataTokenField.parse(value("runtime_secondary_stress_tags", base.secondaryStressTags.raw)),
                        tendonStressTags = MetadataTokenField.parse(value("runtime_tendon_stress_tags", base.tendonStressTags.raw)),
                        ligamentJointStabilityStressTags = MetadataTokenField.parse(value("runtime_ligament_joint_stability_stress_tags", base.ligamentJointStabilityStressTags.raw)),
                        jointImpactStressTags = MetadataTokenField.parse(value("runtime_joint_impact_stress_tags", base.jointImpactStressTags.raw)),
                        cognitiveStressTags = MetadataTokenField.parse(value("runtime_cognitive_stress_tags", base.cognitiveStressTags.raw)),
                        sportContextTags = MetadataTokenField.parse(value("runtime_sport_context_tags", base.sportContextTags.raw)),
                        recoveryDecayProfile = value("runtime_recovery_decay_profile", base.recoveryDecayProfile),
                        stressMagnitudeHint = value("runtime_stress_magnitude_hint", base.stressMagnitudeHint),
                        badmintonTransferLevel = value("runtime_badminton_transfer_level", base.badmintonTransferLevel),
                        badmintonTransferType = MetadataTokenField.parse(value("runtime_badminton_transfer_type", base.badmintonTransferType.raw)),
                        badmintonSkillTargets = MetadataTokenField.parse(value("runtime_badminton_skill_targets", base.badmintonSkillTargets.raw)),
                        badmintonPhysicalQualities = MetadataTokenField.parse(value("runtime_badminton_physical_qualities", base.badmintonPhysicalQualities.raw)),
                        transferConfidence = value("runtime_transfer_confidence", base.transferConfidence),
                        sourceConfidenceLevel = value("runtime_source_confidence_level", base.sourceConfidenceLevel),
                        finalSourceStatus = value("runtime_final_source_status", base.finalSourceStatus),
                        neuromuscularStressLevel = value("runtime_neuromuscular_stress_level", base.neuromuscularStressLevel),
                        systemicMuscularStressLevel = value("runtime_systemic_muscular_stress_level", base.systemicMuscularStressLevel),
                        localMuscularStressLevel = value("runtime_local_muscular_stress_level", base.localMuscularStressLevel),
                        jointTendonImpactStressLevel = value("runtime_joint_tendon_impact_stress_level", base.jointTendonImpactStressLevel),
                        movementFocusDemandLevel = value("runtime_movement_focus_demand_level", base.movementFocusDemandLevel),
                        recoveryDurationClass = value("runtime_recovery_duration_class", base.recoveryDurationClass),
                        safeForSeedMutation = false,
                        appCueProfile = value("runtime_app_cue_profile", base.appCueProfile)
                    )
                }
                return@forEachIndexed
            }
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
                    sleepHours = row.safeSleepHours(index),
                    bodyWeightKg = row.safeDouble(index, "body_weight_kg")
                )
                "set" -> setRows += RestoreSetRow(
                    date = date,
                    entryKey = row.value(index, "entry_key").ifBlank { "fallback-$date-$rowIndex" },
                    entryOrder = row.safeInt(index, "entry_order") ?: rowIndex + 1,
                    exerciseName = row.value(index, "exercise_name").ifBlank { "CSV 복원 운동" },
                    stableKey = row.value(index, "stable_key"),
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
                    sleepHours = row.safeSleepHours(index),
                    bodyWeightKg = row.safeDouble(index, "body_weight_kg")
                )
                "check_in" -> {
                    val candidate = RestoreCheckInRow(
                        date = date,
                        sleepHours = row.safeSleepHours(index),
                        overallFatigue = row.safeInt(index, "overall_fatigue"),
                        lowerBodyFatigue = row.safeInt(index, "lower_body_fatigue"),
                        jointTendonDiscomfort = row.safeInt(index, "joint_tendon_discomfort"),
                        focusMotivation = row.safeInt(index, "focus_motivation"),
                        note = row.value(index, "checkin_note").ifBlank { null },
                        createdAt = row.safeLong(index, "checkin_created_at"),
                        updatedAt = row.safeLong(index, "checkin_updated_at")
                    )
                    if (candidate.hasValidValues()) checkInRows += candidate else warnings += 1
                }
                "smash_speed" -> {
                    val speedKmh = row.safeDouble(index, "speed_kmh")
                    if (speedKmh == null || speedKmh !in 1.0..500.0) {
                        warnings += 1
                    } else {
                        smashSpeedRows += RestoreSmashSpeedRow(
                            date = date,
                            smashSpeedId = row.safeLong(index, "smash_speed_id"),
                            speedKmh = speedKmh,
                            attemptIndex = row.safeInt(index, "attempt_index"),
                            source = row.value(index, "source").ifBlank { null },
                            note = row.value(index, "smash_note").ifBlank { null },
                            parentWorkoutEntryId = row.safeLong(index, "parent_workout_entry_id"),
                            createdAt = row.safeLong(index, "smash_created_at"),
                            updatedAt = row.safeLong(index, "smash_updated_at")
                        )
                    }
                }
                else -> warnings += 1
            }
        }
        return RecordCsvImportData.Restore(
            exerciseRows,
            profileRows,
            dailyRows,
            setRows,
            warnings,
            checkInRows,
            smashSpeedRows,
            runtimeMetadataRows
        )
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
                sleepHours = row.safeSleepHours(index),
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

    private fun List<String>.safeSleepHours(index: Map<String, Int>): Double? =
        safeDouble(index, "sleep_hours")?.takeIf { value -> value in 0.0..24.0 }

    private fun List<String>.safeInt(index: Map<String, Int>, key: String): Int? =
        value(index, key).trim().takeIf { value -> value.isNotEmpty() }?.toIntOrNull()

    private fun List<String>.safeLong(index: Map<String, Int>, key: String): Long? =
        value(index, key).trim().takeIf { value -> value.isNotEmpty() }?.toLongOrNull()

    private fun List<String>.safeBool(index: Map<String, Int>, key: String): Boolean? =
        when (value(index, key).trim().lowercase(Locale.US)) {
            "1", "true", "yes", "y" -> true
            "0", "false", "no", "n" -> false
            else -> null
        }

    private fun String.isValidDate(): Boolean =
        runCatching { LocalDate.parse(this) }.isSuccess

    private fun RestoreCheckInRow.hasValidValues(): Boolean =
        (sleepHours == null || sleepHours in 0.0..24.0) &&
            listOf(overallFatigue, lowerBodyFatigue, jointTendonDiscomfort, focusMotivation)
                .filterNotNull()
                .all { value -> value in 1..5 }

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
            "profileRecoveryScaleDirection" to "HIGH_IS_GOOD",
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
