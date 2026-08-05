package com.training.trackplanner.data

import java.time.LocalDate
import java.security.MessageDigest
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
    val posteriorEventCount: Int = 0,
    val posteriorHistoryCount: Int = 0,
    val posteriorStateCount: Int = 0,
    val posteriorCurveCount: Int = 0,
    val posteriorEvidenceCount: Int = 0,
    val posteriorRevisionCount: Int = 0,
    val posteriorLocalStateCount: Int = 0,
    val posteriorLocalHistoryCount: Int = 0,
    val posteriorProxyTransferCount: Int = 0,
    val programCount: Int = 0,
    val programItemCount: Int = 0,
    val programItemSetCount: Int = 0,
    val programTombstoneCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val warningCount: Int = 0
) {
    fun summaryText(action: String): String =
        "$action 완료: profile $profileCount, daily $dailyMetricCount, check-in $dailyCheckInCount, " +
            "entry $entryCount, set $setCount, program $programCount, program item $programItemCount, " +
            "program item set $programItemSetCount, program tombstone $programTombstoneCount, " +
            "skip $skippedDuplicateCount"
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
        val runtimeMetadataRows: List<RuntimeExerciseMetadata> = emptyList(),
        val metadataSnapshotRows: List<ExerciseMetadataSnapshotRow> = emptyList(),
        val backupSchemaVersion: Int = 1,
        val posteriorFormatPresent: Boolean = false,
        val posteriorBootstrapMarker: String? = null,
        val posteriorEvents: List<StrengthPosteriorEventEntity> = emptyList(),
        val posteriorHistory: List<StrengthPosteriorHistoryEntity> = emptyList(),
        val posteriorModelStates: List<StrengthPosteriorModelStateEntity> = emptyList(),
        val curvePosteriors: List<StrengthCurvePosteriorEntity> = emptyList(),
        val posteriorEvidence: List<StrengthPosteriorEvidenceEntity> = emptyList(),
        val posteriorRevisions: List<StrengthModelRevisionEntity> = emptyList(),
        val posteriorLocalStates: List<StrengthExercisePerformanceStateEntity> = emptyList(),
        val posteriorLocalHistory: List<StrengthExercisePerformanceHistoryEntity> = emptyList(),
        val posteriorProxyHistory: List<StrengthProxyTransferHistoryEntity> = emptyList(),
        val programSnapshot: RestoreProgramSnapshot? = null,
        val manifest: BackupManifest? = null
    ) : RecordCsvImportData()

    data class DailyTimeseries(
        val rows: List<DailyTimeseriesRow>,
        val warningCount: Int
    ) : RecordCsvImportData()
}

data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val entityCounts: Map<String, Int>,
    val contentSha256: String
)

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
    val legacyTrainingRole: String,
    val trainingRoleCodes: Set<TrainingRole>,
    val programSlotCapabilityCodes: Set<ProgramSlotCapability>,
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
    val bodyWeightKg: Double?,
    val entrySourceId: String? = null,
    val entryCreatedAt: Long? = null,
    val entryCompletedAt: Long? = null,
    val entryDisplayOrder: Int? = null,
    val entryFirstConfirmedAt: Long? = null,
    val entryPerformedAt: Long? = null,
    val setManualWeight: Boolean? = null,
    val setRestSecondsOverride: Int? = null
)

data class ProgramBackupItem(
    val programStableKey: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val exerciseStableKey: String,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val prescription: String,
    val setCount: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int,
    val trainingSlot: String?,
    val dayIntensity: String?,
    val weightSource: String?
)

data class ProgramBackupItemSet(
    val programStableKey: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val orderIndex: Int,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val seconds: Int
)

data class RestoreProgramSnapshot(
    val schemaVersion: Int,
    val programs: List<TrainingProgram>,
    val items: List<ProgramBackupItem>,
    val sets: List<ProgramBackupItemSet>,
    val tombstones: List<TrainingProgramTombstone>
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
    internal const val CURRENT_RESTORE_SCHEMA_VERSION = 10
    internal const val CURRENT_BACKUP_FORMAT_VERSION = 11
    internal const val CURRENT_PROGRAM_BACKUP_SCHEMA_VERSION = 2
    private const val MANIFEST_PREFIX = "#WGTD_BACKUP_MANIFEST"

    private val restoreHeader = listOf(
        "schema_version",
        "row_type",
        "date",
        "entry_key",
        "entry_source_id",
        "entry_order",
        "entry_created_at",
        "entry_completed_at",
        "entry_display_order",
        "entry_first_confirmed_at",
        "entry_performed_at",
        "exercise_name",
        "category",
        "confirmed",
        "rest_seconds",
        "rpe",
        "max_reps",
        "notes",
        "set_index",
        "set_confirmed",
        "set_manual_weight",
        "set_rest_seconds_override",
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
        "training_role_codes",
        "program_slot_capability_codes",
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
        "runtime_app_cue_profile",
        "metadata_field_key",
        "metadata_field_scope",
        "metadata_value_encoding",
        "metadata_value",
        "metadata_is_explicit_empty",
        "strength_event_uuid",
        "strength_target_key",
        "strength_completion_fingerprint",
        "strength_model_version",
        "strength_curve_version",
        "strength_factor_schema_version",
        "strength_posterior_payload",
        "program_backup_schema_version",
        "program_stable_key",
        "program_name",
        "program_duration_days",
        "program_created_at",
        "program_updated_at",
        "program_goal",
        "program_weekly_training_days",
        "program_session_minutes",
        "program_available_equipment",
        "program_excluded_exercise_text",
        "program_badminton_transfer_ratio",
        "program_sport_strength_ratio",
        "program_periodization_type",
        "program_week_number",
        "program_day_of_week",
        "program_order_index",
        "program_exercise_stable_key",
        "program_prescription",
        "program_set_count",
        "program_item_set_index",
        "program_training_slot",
        "program_day_intensity",
        "program_weight_source",
        "program_tombstone_deleted_at",
        "program_tombstone_seed_version"
    )

    fun buildRestoreCsv(
        entriesWithSets: List<WorkoutEntryWithSets>,
        metrics: List<DailyMetric>,
        exercises: List<Exercise> = emptyList(),
        initialProfile: InitialUserProfile? = null,
        checkIns: List<DailyCheckIn> = emptyList(),
        smashSpeeds: List<SmashSpeedRecord> = emptyList(),
        runtimeMetadata: List<RuntimeExerciseMetadata> = emptyList(),
        posteriorBootstrapMarker: String? = null,
        posteriorEvents: List<StrengthPosteriorEventEntity> = emptyList(),
        posteriorHistory: List<StrengthPosteriorHistoryEntity> = emptyList(),
        posteriorModelStates: List<StrengthPosteriorModelStateEntity> = emptyList(),
        curvePosteriors: List<StrengthCurvePosteriorEntity> = emptyList(),
        posteriorEvidence: List<StrengthPosteriorEvidenceEntity> = emptyList(),
        posteriorRevisions: List<StrengthModelRevisionEntity> = emptyList(),
        posteriorLocalStates: List<StrengthExercisePerformanceStateEntity> = emptyList(),
        posteriorLocalHistory: List<StrengthExercisePerformanceHistoryEntity> = emptyList(),
        posteriorProxyHistory: List<StrengthProxyTransferHistoryEntity> = emptyList(),
        programs: List<TrainingProgram> = emptyList(),
        programItems: List<ProgramBackupItem> = emptyList(),
        programItemSets: List<ProgramBackupItemSet> = emptyList(),
        programTombstones: List<TrainingProgramTombstone> = emptyList(),
        trainingRoleRelations: List<ExerciseTrainingRoleRelation> = emptyList(),
        programSlotCapabilityRelations: List<ExerciseProgramSlotCapabilityRelation> = emptyList(),
        metadataSnapshots: List<ExerciseMetadataSnapshotRow> = emptyList(),
        includeProgramSnapshot: Boolean = false
    ): String {
        val builder = StringBuilder()
        val exercisesById = exercises.associateBy { exercise -> exercise.stableKey }
        val trainingRolesByStableKey = trainingRoleRelations.groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
        val capabilitiesByStableKey = programSlotCapabilityRelations.groupBy(
            ExerciseProgramSlotCapabilityRelation::exerciseStableKey
        )
        fun MetadataTokenField.exportRaw(): String = raw.ifBlank { values.joinToString("|") }
        fun appendMappedRow(
            rowType: String,
            values: Map<String, String> = emptyMap()
        ) {
            builder.appendCsvRow(
                restoreHeader.map { column ->
                    when (column) {
                        "schema_version" -> CURRENT_RESTORE_SCHEMA_VERSION.toString()
                        "row_type" -> rowType
                        else -> values[column].orEmpty()
                    }
                }
            )
        }
        fun appendStrengthRow(
            rowType: String,
            payload: String,
            eventUuid: String = "",
            targetKey: String = "",
            completionFingerprint: String = "",
            modelVersion: String = "",
            curveVersion: String = "",
            factorSchemaVersion: String = ""
        ) {
            builder.appendCsvRow(
                restoreHeader.map { column ->
                    when (column) {
                        "schema_version" -> CURRENT_RESTORE_SCHEMA_VERSION.toString()
                        "row_type" -> rowType
                        "strength_event_uuid" -> eventUuid
                        "strength_target_key" -> targetKey
                        "strength_completion_fingerprint" -> completionFingerprint
                        "strength_model_version" -> modelVersion
                        "strength_curve_version" -> curveVersion
                        "strength_factor_schema_version" -> factorSchemaVersion
                        "strength_posterior_payload" -> payload
                        else -> ""
                    }
                }
            )
        }
        builder.appendLine(restoreHeader.joinToString(","))
        if (includeProgramSnapshot) {
            appendMappedRow(
                rowType = "program_snapshot",
                values = mapOf(
                    "program_backup_schema_version" to CURRENT_PROGRAM_BACKUP_SCHEMA_VERSION.toString()
                )
            )
            programs.sortedBy(TrainingProgram::stableKey).forEach { program ->
                appendMappedRow(
                    rowType = "program",
                    values = mapOf(
                        "program_stable_key" to program.stableKey,
                        "program_name" to program.name,
                        "program_duration_days" to program.durationDays.toString(),
                        "program_created_at" to program.createdAt.toString(),
                        "program_updated_at" to program.updatedAt.toString(),
                        "program_goal" to program.goal,
                        "program_weekly_training_days" to program.weeklyTrainingDays.toString(),
                        "program_session_minutes" to program.sessionMinutes.toString(),
                        "program_available_equipment" to program.availableEquipment,
                        "program_excluded_exercise_text" to program.excludedExerciseText,
                        "program_badminton_transfer_ratio" to program.badmintonTransferRatio.toString(),
                        "program_sport_strength_ratio" to program.sportStrengthRatio,
                        "program_periodization_type" to program.periodizationType
                    )
                )
            }
            programItems.sortedWith(
                compareBy(ProgramBackupItem::programStableKey)
                    .thenBy(ProgramBackupItem::weekNumber)
                    .thenBy(ProgramBackupItem::dayOfWeek)
                    .thenBy(ProgramBackupItem::orderIndex)
                    .thenBy(ProgramBackupItem::exerciseStableKey)
            ).forEach { item ->
                appendMappedRow(
                    rowType = "program_item",
                    values = mapOf(
                        "program_stable_key" to item.programStableKey,
                        "program_week_number" to item.weekNumber.toString(),
                        "program_day_of_week" to item.dayOfWeek.toString(),
                        "program_order_index" to item.orderIndex.toString(),
                        "program_exercise_stable_key" to item.exerciseStableKey,
                        "exercise_name" to item.exerciseName,
                        "category" to item.category,
                        "rest_seconds" to item.restSeconds.toString(),
                        "program_prescription" to item.prescription,
                        "program_set_count" to item.setCount.toString(),
                        "reps" to item.reps.toString(),
                        "weight_kg" to item.weightKg.formatNumber(),
                        "seconds" to item.seconds.toString(),
                        "program_training_slot" to item.trainingSlot.orEmpty(),
                        "program_day_intensity" to item.dayIntensity.orEmpty(),
                        "program_weight_source" to item.weightSource.orEmpty()
                    )
                )
            }
            programItemSets.sortedWith(
                compareBy(ProgramBackupItemSet::programStableKey)
                    .thenBy(ProgramBackupItemSet::weekNumber)
                    .thenBy(ProgramBackupItemSet::dayOfWeek)
                    .thenBy(ProgramBackupItemSet::orderIndex)
                    .thenBy(ProgramBackupItemSet::setIndex)
            ).forEach { set ->
                appendMappedRow(
                    rowType = "program_item_set",
                    values = mapOf(
                        "program_stable_key" to set.programStableKey,
                        "program_week_number" to set.weekNumber.toString(),
                        "program_day_of_week" to set.dayOfWeek.toString(),
                        "program_order_index" to set.orderIndex.toString(),
                        "program_item_set_index" to set.setIndex.toString(),
                        "reps" to set.reps.toString(),
                        "weight_kg" to set.weightKg.formatNumber(),
                        "seconds" to set.seconds.toString()
                    )
                )
            }
            programTombstones.sortedBy(TrainingProgramTombstone::programStableKey).forEach { tombstone ->
                appendMappedRow(
                    rowType = "program_tombstone",
                    values = mapOf(
                        "program_stable_key" to tombstone.programStableKey,
                        "program_tombstone_deleted_at" to tombstone.deletedAt.toString(),
                        "program_tombstone_seed_version" to tombstone.seedVersion?.toString().orEmpty()
                    )
                )
            }
        }
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
            appendMappedRow(
                rowType = "exercise",
                values = mapOf(
                    "exercise_name" to exercise.name,
                    "category" to exercise.category,
                    "rest_seconds" to exercise.defaultRestSeconds.toString(),
                    "stable_key" to exercise.stableKey,
                    "description" to exercise.description,
                    "default_rest_seconds" to exercise.defaultRestSeconds.toString(),
                    "image_asset_name" to exercise.imageAssetName,
                    "primary_muscles" to exercise.primaryMuscles,
                    "secondary_muscles" to exercise.secondaryMuscles,
                    "equipment" to exercise.equipment.ifBlank { exercise.equipmentTags },
                    "movement_pattern" to exercise.movementPattern,
                    "movement_category" to exercise.movementCategory,
                    "force_type" to exercise.forceType,
                    "body_region" to exercise.bodyRegion,
                    "laterality" to exercise.laterality,
                    "plane" to exercise.plane,
                    "training_role_codes" to trainingRolesByStableKey[exercise.stableKey].orEmpty()
                        .map(ExerciseTrainingRoleRelation::trainingRoleCode).distinct().sorted().joinToString("|"),
                    "program_slot_capability_codes" to capabilitiesByStableKey[exercise.stableKey].orEmpty()
                        .map(ExerciseProgramSlotCapabilityRelation::capabilityCode).distinct().sorted().joinToString("|"),
                    "sport_transfer_direct" to exercise.sportTransferDirect,
                    "sport_transfer_supportive" to exercise.sportTransferSupportive,
                    "load_profile" to exercise.loadProfile,
                    "metadata_confidence" to exercise.metadataConfidence,
                    "is_active" to exercise.isActive.toCsvBool(),
                    "is_custom" to exercise.isCustom.toCsvBool(),
                    "needs_review" to exercise.needsReview.toCsvBool(),
                    "detail1" to exercise.detail1,
                    "detail2" to exercise.detail2,
                    "mode" to exercise.mode
                )
            )
        }
        metadataSnapshots
            .sortedWith(compareBy(ExerciseMetadataSnapshotRow::stableKey).thenBy(ExerciseMetadataSnapshotRow::fieldKey))
            .forEach { snapshot ->
                appendMappedRow(
                    rowType = "exercise_metadata_snapshot",
                    values = mapOf(
                        "stable_key" to snapshot.stableKey,
                        "metadata_field_key" to snapshot.fieldKey,
                        "metadata_field_scope" to snapshot.fieldScope.name,
                        "metadata_value_encoding" to snapshot.valueEncoding.name,
                        "metadata_value" to snapshot.value,
                        "metadata_is_explicit_empty" to snapshot.isExplicitEmpty.toCsvBool()
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
                appendMappedRow(
                    rowType = "daily",
                    values = mapOf(
                        "date" to date,
                        "sleep_hours" to (metric?.sleepHours ?: checkIn?.sleepHours).formatOptional(),
                        "body_weight_kg" to (metric?.bodyWeightKg ?: checkIn?.bodyWeightKg).formatOptional()
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
                    val sourceId = entry.backupSourceId ?: "entry:${entry.id}:${entry.createdAt}"
                    orderedSets.forEach { set ->
                        appendMappedRow(
                            rowType = "set",
                            values = mapOf(
                                "date" to entry.date,
                                "entry_key" to entry.id.toString(),
                                "entry_source_id" to sourceId,
                                "entry_order" to (entryIndex + 1).toString(),
                                "entry_created_at" to entry.createdAt.toString(),
                                "entry_completed_at" to entry.completedAt?.toString().orEmpty(),
                                "entry_display_order" to entry.displayOrder.toString(),
                                "entry_first_confirmed_at" to entry.firstConfirmedAt?.toString().orEmpty(),
                                "entry_performed_at" to entry.performedAt?.toString().orEmpty(),
                                "exercise_name" to entry.exerciseName,
                                "category" to entry.category,
                                "confirmed" to entryConfirmed.toCsvBool(),
                                "rest_seconds" to entry.restSeconds.toString(),
                                "rpe" to (set.rpe ?: entry.rpe).formatOptional(),
                                "max_reps" to entry.maxReps?.toString().orEmpty(),
                                "notes" to entry.notes,
                                "set_index" to set.setIndex.toString(),
                                "set_confirmed" to set.confirmed.toCsvBool(),
                                "set_manual_weight" to set.manualWeight.toCsvBool(),
                                "set_rest_seconds_override" to set.restSecondsOverride?.toString().orEmpty(),
                                "reps" to set.reps.toString(),
                                "weight_kg" to set.weightKg.formatNumber(),
                                "seconds" to set.seconds.toString(),
                                "stable_key" to exercisesById[entry.exerciseStableKey]?.stableKey.orEmpty()
                            )
                        )
                    }
                }
            }
        appendStrengthRow(
            rowType = "strength_posterior_manifest",
            payload = StrengthPosteriorBackupCodec.encodeManifest(posteriorBootstrapMarker)
        )
        posteriorRevisions.sortedBy(StrengthModelRevisionEntity::createdAt).forEach { revision ->
            appendStrengthRow(
                rowType = "strength_model_revision",
                payload = StrengthPosteriorBackupCodec.encode(revision),
                modelVersion = revision.modelVersion,
                curveVersion = revision.curveVersion,
                factorSchemaVersion = revision.factorSchemaVersion
            )
        }
        val eventsByUuid = posteriorEvents.associateBy(StrengthPosteriorEventEntity::eventUuid)
        posteriorEvents.sortedWith(
            compareBy(StrengthPosteriorEventEntity::sessionDate, StrengthPosteriorEventEntity::createdAt, StrengthPosteriorEventEntity::eventUuid)
        ).forEach { event ->
            appendStrengthRow(
                rowType = "strength_posterior_event",
                payload = StrengthPosteriorBackupCodec.encode(event),
                eventUuid = event.eventUuid,
                completionFingerprint = event.completionFingerprint,
                modelVersion = event.modelVersion,
                curveVersion = event.curveVersion,
                factorSchemaVersion = event.factorSchemaVersion
            )
        }
        posteriorHistory.sortedWith(
            compareBy(StrengthPosteriorHistoryEntity::sessionDate, StrengthPosteriorHistoryEntity::createdAt,
                StrengthPosteriorHistoryEntity::eventUuid, StrengthPosteriorHistoryEntity::targetKey)
        ).forEach { history ->
            appendStrengthRow(
                rowType = "strength_posterior_history",
                payload = StrengthPosteriorBackupCodec.encode(history),
                eventUuid = history.eventUuid,
                targetKey = history.targetKey,
                completionFingerprint = eventsByUuid[history.eventUuid]?.completionFingerprint.orEmpty(),
                modelVersion = history.modelVersion,
                curveVersion = history.curveVersion,
                factorSchemaVersion = history.factorSchemaVersion
            )
        }
        posteriorModelStates.sortedBy(StrengthPosteriorModelStateEntity::modelInstanceKey).forEach { state ->
            appendStrengthRow(
                rowType = "strength_posterior_model_state",
                payload = StrengthPosteriorBackupCodec.encode(state),
                eventUuid = state.lastProcessedEventUuid.orEmpty(),
                modelVersion = state.modelVersion,
                curveVersion = state.curveVersion,
                factorSchemaVersion = state.factorSchemaVersion
            )
        }
        curvePosteriors.sortedBy(StrengthCurvePosteriorEntity::curveSubjectKey).forEach { posterior ->
            appendStrengthRow(
                rowType = "strength_curve_posterior",
                payload = StrengthPosteriorBackupCodec.encode(posterior),
                curveVersion = posterior.curveVersion
            )
        }
        posteriorEvidence.sortedWith(
            compareBy(StrengthPosteriorEvidenceEntity::sessionDate, StrengthPosteriorEvidenceEntity::exerciseStableKey,
                StrengthPosteriorEvidenceEntity::evidenceFingerprint)
        ).forEach { evidence ->
            appendStrengthRow(
                rowType = "strength_posterior_evidence",
                payload = StrengthPosteriorBackupCodec.encode(evidence),
                eventUuid = evidence.eventUuid,
                targetKey = evidence.directTargetKey.orEmpty(),
                completionFingerprint = eventsByUuid[evidence.eventUuid]?.completionFingerprint.orEmpty()
            )
        }
        posteriorLocalStates.sortedWith(
            compareBy(StrengthExercisePerformanceStateEntity::revisionKey, StrengthExercisePerformanceStateEntity::exerciseStableKey)
        ).forEach { state ->
            appendStrengthRow(
                rowType = "strength_exercise_performance_state",
                payload = StrengthPosteriorBackupCodec.encode(state),
                eventUuid = state.lastProcessedEventUuid,
                modelVersion = state.modelVersion,
                curveVersion = state.curveVersion
            )
        }
        posteriorLocalHistory.sortedWith(
            compareBy(
                StrengthExercisePerformanceHistoryEntity::revisionKey,
                StrengthExercisePerformanceHistoryEntity::sessionDate,
                StrengthExercisePerformanceHistoryEntity::eventUuid,
                StrengthExercisePerformanceHistoryEntity::exerciseStableKey
            )
        ).forEach { history ->
            appendStrengthRow(
                rowType = "strength_exercise_performance_history",
                payload = StrengthPosteriorBackupCodec.encode(history),
                eventUuid = history.eventUuid,
                modelVersion = history.modelVersion,
                curveVersion = history.curveVersion
            )
        }
        posteriorProxyHistory.sortedWith(
            compareBy(
                StrengthProxyTransferHistoryEntity::revisionKey,
                StrengthProxyTransferHistoryEntity::sessionDate,
                StrengthProxyTransferHistoryEntity::eventUuid,
                StrengthProxyTransferHistoryEntity::exerciseStableKey,
                StrengthProxyTransferHistoryEntity::targetKey
            )
        ).forEach { history ->
            appendStrengthRow(
                rowType = "strength_proxy_transfer_history",
                payload = StrengthPosteriorBackupCodec.encode(history),
                eventUuid = history.eventUuid,
                targetKey = history.targetKey,
                modelVersion = history.modelVersion
            )
        }
        return builder.toString()
    }

    fun wrapWithManifest(
        body: String,
        appVersion: String,
        exportedAt: Long,
        entityCounts: Map<String, Int>
    ): String {
        val normalizedBody = body.replace("\r\n", "\n").replace('\r', '\n')
        val counts = entityCounts.toSortedMap().entries.joinToString("|") { (key, value) -> "$key=$value" }
        val hash = sha256(normalizedBody)
        return listOf(
            MANIFEST_PREFIX,
            CURRENT_BACKUP_FORMAT_VERSION.toString(),
            appVersion,
            exportedAt.toString(),
            counts,
            hash
        ).joinToString(",") + "\n" + normalizedBody
    }

    fun parse(text: String): RecordCsvImportData {
        val (body, manifest) = unwrapManifest(text)
        val rows = parseCsvRows(body)
        if (rows.isEmpty()) {
            return RecordCsvImportData.Restore(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                warningCount = 1,
                manifest = manifest
            )
        }
        val header = rows.first().map { value -> value.trim() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        val parsed = if ("row_type" in index) {
            parseRestore(rows.drop(1), index, manifest)
        } else {
            parseDailyTimeseries(rows.drop(1), index)
        }
        if (parsed is RecordCsvImportData.Restore && manifest != null) {
            validateManifestCounts(manifest, parsed)
        }
        return parsed
    }

    private fun parseRestore(
        rows: List<List<String>>,
        index: Map<String, Int>,
        manifest: BackupManifest?
    ): RecordCsvImportData.Restore {
        var warnings = 0
        var backupSchemaVersion = 1
        var posteriorFormatPresent = false
        var posteriorBootstrapMarker: String? = null
        val exerciseRows = mutableListOf<RestoreExerciseRow>()
        val profileRows = mutableListOf<RestoreProfileRow>()
        val dailyRows = mutableListOf<RestoreDailyRow>()
        val setRows = mutableListOf<RestoreSetRow>()
        val checkInRows = mutableListOf<RestoreCheckInRow>()
        val smashSpeedRows = mutableListOf<RestoreSmashSpeedRow>()
        val runtimeMetadataRows = mutableListOf<RuntimeExerciseMetadata>()
        val metadataSnapshotRows = mutableListOf<ExerciseMetadataSnapshotRow>()
        val posteriorEvents = mutableListOf<StrengthPosteriorEventEntity>()
        val posteriorHistory = mutableListOf<StrengthPosteriorHistoryEntity>()
        val posteriorModelStates = mutableListOf<StrengthPosteriorModelStateEntity>()
        val curvePosteriors = mutableListOf<StrengthCurvePosteriorEntity>()
        val posteriorEvidence = mutableListOf<StrengthPosteriorEvidenceEntity>()
        val posteriorRevisions = mutableListOf<StrengthModelRevisionEntity>()
        val posteriorLocalStates = mutableListOf<StrengthExercisePerformanceStateEntity>()
        val posteriorLocalHistory = mutableListOf<StrengthExercisePerformanceHistoryEntity>()
        val posteriorProxyHistory = mutableListOf<StrengthProxyTransferHistoryEntity>()
        val programSnapshotVersions = mutableListOf<Int>()
        val programs = mutableListOf<TrainingProgram>()
        val programItems = mutableListOf<ProgramBackupItem>()
        val programItemSets = mutableListOf<ProgramBackupItemSet>()
        val programTombstones = mutableListOf<TrainingProgramTombstone>()
        rows.forEachIndexed { rowIndex, row ->
            backupSchemaVersion = maxOf(backupSchemaVersion, row.safeInt(index, "schema_version") ?: 1)
            val rowType = row.value(index, "row_type").trim().lowercase(Locale.US)
            val posteriorPayload = row.value(index, "strength_posterior_payload")
            when (rowType) {
                "exercise_metadata_snapshot" -> {
                    val snapshot = ExerciseMetadataSnapshotRow(
                        stableKey = row.value(index, "stable_key").trim(),
                        fieldKey = row.value(index, "metadata_field_key").trim(),
                        fieldScope = row.requiredEnum(index, "metadata_field_scope", rowType),
                        valueEncoding = row.requiredEnum(index, "metadata_value_encoding", rowType),
                        value = row.value(index, "metadata_value"),
                        isExplicitEmpty = row.safeBool(index, "metadata_is_explicit_empty")
                            ?: error("exercise_metadata_snapshot is missing metadata_is_explicit_empty.")
                    )
                    ExerciseMetadataFieldPolicyRegistry.validate(listOf(snapshot))
                    metadataSnapshotRows += snapshot
                    return@forEachIndexed
                }
                "program_snapshot" -> {
                    programSnapshotVersions += requireNotNull(
                        row.safeInt(index, "program_backup_schema_version")
                    ) { "program_snapshot is missing program_backup_schema_version." }
                    return@forEachIndexed
                }
                "program" -> {
                    programs += TrainingProgram(
                        stableKey = row.value(index, "program_stable_key").trim(),
                        name = row.value(index, "program_name"),
                        durationDays = row.requiredInt(index, "program_duration_days", rowType),
                        createdAt = row.requiredLong(index, "program_created_at", rowType),
                        goal = row.value(index, "program_goal"),
                        weeklyTrainingDays = row.requiredInt(index, "program_weekly_training_days", rowType),
                        sessionMinutes = row.requiredInt(index, "program_session_minutes", rowType),
                        availableEquipment = row.value(index, "program_available_equipment"),
                        excludedExerciseText = row.value(index, "program_excluded_exercise_text"),
                        badmintonTransferRatio = row.requiredDouble(
                            index,
                            "program_badminton_transfer_ratio",
                            rowType
                        ),
                        sportStrengthRatio = row.value(index, "program_sport_strength_ratio"),
                        periodizationType = row.value(index, "program_periodization_type"),
                        updatedAt = row.requiredLong(index, "program_updated_at", rowType)
                    )
                    return@forEachIndexed
                }
                "program_item" -> {
                    programItems += ProgramBackupItem(
                        programStableKey = row.value(index, "program_stable_key").trim(),
                        weekNumber = row.requiredInt(index, "program_week_number", rowType),
                        dayOfWeek = row.requiredInt(index, "program_day_of_week", rowType),
                        orderIndex = row.requiredInt(index, "program_order_index", rowType),
                        exerciseStableKey = row.value(index, "program_exercise_stable_key").trim(),
                        exerciseName = row.value(index, "exercise_name"),
                        category = row.value(index, "category"),
                        restSeconds = row.requiredInt(index, "rest_seconds", rowType),
                        prescription = row.value(index, "program_prescription"),
                        setCount = row.requiredInt(index, "program_set_count", rowType),
                        reps = row.requiredInt(index, "reps", rowType),
                        weightKg = row.requiredDouble(index, "weight_kg", rowType),
                        seconds = row.requiredInt(index, "seconds", rowType),
                        trainingSlot = row.value(index, "program_training_slot").ifBlank { null },
                        dayIntensity = row.value(index, "program_day_intensity").ifBlank { null },
                        weightSource = row.value(index, "program_weight_source").ifBlank { null }
                    )
                    return@forEachIndexed
                }
                "program_item_set" -> {
                    programItemSets += ProgramBackupItemSet(
                        programStableKey = row.value(index, "program_stable_key").trim(),
                        weekNumber = row.requiredInt(index, "program_week_number", rowType),
                        dayOfWeek = row.requiredInt(index, "program_day_of_week", rowType),
                        orderIndex = row.requiredInt(index, "program_order_index", rowType),
                        setIndex = row.requiredInt(index, "program_item_set_index", rowType),
                        reps = row.requiredInt(index, "reps", rowType),
                        weightKg = row.requiredDouble(index, "weight_kg", rowType),
                        seconds = row.requiredInt(index, "seconds", rowType)
                    )
                    return@forEachIndexed
                }
                "program_tombstone" -> {
                    programTombstones += TrainingProgramTombstone(
                        programStableKey = row.value(index, "program_stable_key").trim(),
                        deletedAt = row.requiredLong(index, "program_tombstone_deleted_at", rowType),
                        seedVersion = row.safeInt(index, "program_tombstone_seed_version")
                    )
                    return@forEachIndexed
                }
                "strength_posterior_manifest" -> {
                    posteriorFormatPresent = true
                    posteriorBootstrapMarker = StrengthPosteriorBackupCodec.decodeManifest(posteriorPayload)
                    return@forEachIndexed
                }
                "strength_model_revision" -> {
                    posteriorFormatPresent = true
                    posteriorRevisions += StrengthPosteriorBackupCodec.decodeRevision(posteriorPayload)
                    return@forEachIndexed
                }
                "strength_posterior_event" -> {
                    posteriorFormatPresent = true
                    val entity = StrengthPosteriorBackupCodec.decodeEvent(posteriorPayload)
                    requireVisibleValue(row, index, "strength_event_uuid", entity.eventUuid)
                    requireVisibleValue(row, index, "strength_completion_fingerprint", entity.completionFingerprint)
                    requireVisibleValue(row, index, "strength_model_version", entity.modelVersion)
                    requireVisibleValue(row, index, "strength_curve_version", entity.curveVersion)
                    requireVisibleValue(row, index, "strength_factor_schema_version", entity.factorSchemaVersion)
                    posteriorEvents += entity
                    return@forEachIndexed
                }
                "strength_posterior_history" -> {
                    posteriorFormatPresent = true
                    val entity = StrengthPosteriorBackupCodec.decodeHistory(posteriorPayload)
                    requireVisibleValue(row, index, "strength_event_uuid", entity.eventUuid)
                    requireVisibleValue(row, index, "strength_target_key", entity.targetKey)
                    requireVisibleValue(row, index, "strength_model_version", entity.modelVersion)
                    requireVisibleValue(row, index, "strength_curve_version", entity.curveVersion)
                    requireVisibleValue(row, index, "strength_factor_schema_version", entity.factorSchemaVersion)
                    posteriorHistory += entity
                    return@forEachIndexed
                }
                "strength_posterior_model_state" -> {
                    posteriorFormatPresent = true
                    val entity = StrengthPosteriorBackupCodec.decodeModelState(posteriorPayload)
                    requireVisibleValue(row, index, "strength_model_version", entity.modelVersion)
                    requireVisibleValue(row, index, "strength_curve_version", entity.curveVersion)
                    requireVisibleValue(row, index, "strength_factor_schema_version", entity.factorSchemaVersion)
                    posteriorModelStates += entity
                    return@forEachIndexed
                }
                "strength_curve_posterior" -> {
                    posteriorFormatPresent = true
                    val entity = StrengthPosteriorBackupCodec.decodeCurvePosterior(posteriorPayload)
                    requireVisibleValue(row, index, "strength_curve_version", entity.curveVersion)
                    curvePosteriors += entity
                    return@forEachIndexed
                }
                "strength_posterior_evidence" -> {
                    posteriorFormatPresent = true
                    val entity = StrengthPosteriorBackupCodec.decodeEvidence(posteriorPayload)
                    requireVisibleValue(row, index, "strength_event_uuid", entity.eventUuid)
                    entity.directTargetKey?.let { target ->
                        requireVisibleValue(row, index, "strength_target_key", target)
                    }
                    posteriorEvidence += entity
                    return@forEachIndexed
                }
                "strength_exercise_performance_state" -> {
                    posteriorFormatPresent = true
                    posteriorLocalStates += StrengthPosteriorBackupCodec.decodeLocalState(posteriorPayload)
                    return@forEachIndexed
                }
                "strength_exercise_performance_history" -> {
                    posteriorFormatPresent = true
                    val entity = StrengthPosteriorBackupCodec.decodeLocalHistory(posteriorPayload)
                    requireVisibleValue(row, index, "strength_event_uuid", entity.eventUuid)
                    posteriorLocalHistory += entity
                    return@forEachIndexed
                }
                "strength_proxy_transfer_history" -> {
                    posteriorFormatPresent = true
                    val entity = StrengthPosteriorBackupCodec.decodeProxyHistory(posteriorPayload)
                    requireVisibleValue(row, index, "strength_event_uuid", entity.eventUuid)
                    requireVisibleValue(row, index, "strength_target_key", entity.targetKey)
                    posteriorProxyHistory += entity
                    return@forEachIndexed
                }
            }
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
                    if ((manifest?.formatVersion ?: 0) < 11) {
                        metadataSnapshotRows += legacyRuntimeSnapshotRows(stableKey, row, index)
                    }
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
                    val exerciseRow = RestoreExerciseRow(
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
                        legacyTrainingRole = row.value(index, "training_role"),
                        trainingRoleCodes = row.value(index, "training_role_codes")
                            .parseEnumTokens<TrainingRole>("training_role_codes"),
                        programSlotCapabilityCodes = row.value(index, "program_slot_capability_codes")
                            .parseEnumTokens<ProgramSlotCapability>("program_slot_capability_codes"),
                        sportTransferDirect = row.value(index, "sport_transfer_direct"),
                        sportTransferSupportive = row.value(index, "sport_transfer_supportive"),
                        loadProfile = row.value(index, "load_profile"),
                        metadataConfidence = row.value(index, "metadata_confidence"),
                        isActive = row.safeBool(index, "is_active") ?: true,
                        isCustom = row.safeBool(index, "is_custom") ?: false,
                        needsReview = row.safeBool(index, "needs_review") ?: false
                    )
                    exerciseRows += exerciseRow
                    if ((manifest?.formatVersion ?: 0) < 11 && exerciseRow.stableKey.isNotBlank()) {
                        metadataSnapshotRows += legacyExerciseSnapshotRows(exerciseRow.stableKey, row, index)
                    }
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
                    bodyWeightKg = row.safeDouble(index, "body_weight_kg"),
                    entrySourceId = row.value(index, "entry_source_id").ifBlank { null },
                    entryCreatedAt = row.safeLong(index, "entry_created_at"),
                    entryCompletedAt = row.safeLong(index, "entry_completed_at"),
                    entryDisplayOrder = row.safeInt(index, "entry_display_order"),
                    entryFirstConfirmedAt = row.safeLong(index, "entry_first_confirmed_at"),
                    entryPerformedAt = row.safeLong(index, "entry_performed_at"),
                    setManualWeight = row.safeBool(index, "set_manual_weight"),
                    setRestSecondsOverride = row.safeInt(index, "set_rest_seconds_override")
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
        val programRowsWithoutMarker =
            programs.size + programItems.size + programItemSets.size + programTombstones.size
        val programSnapshot = if (programSnapshotVersions.isEmpty()) {
            if (programRowsWithoutMarker > 0) warnings += programRowsWithoutMarker
            null
        } else {
            require(programSnapshotVersions.distinct().size == 1) {
                "Conflicting program_snapshot schema versions."
            }
            val schemaVersion = programSnapshotVersions.first()
            require(schemaVersion in 1..CURRENT_PROGRAM_BACKUP_SCHEMA_VERSION) {
                "Unsupported program backup schema version: $schemaVersion."
            }
            validateProgramSnapshot(programs, programItems, programItemSets, programTombstones)
            RestoreProgramSnapshot(
                schemaVersion = schemaVersion,
                programs = programs,
                items = programItems,
                sets = programItemSets,
                tombstones = programTombstones
            )
        }
        ExerciseMetadataFieldPolicyRegistry.validate(metadataSnapshotRows)
        return RecordCsvImportData.Restore(
            exerciseRows = exerciseRows,
            profileRows = profileRows,
            dailyRows = dailyRows,
            setRows = setRows,
            warningCount = warnings,
            checkInRows = checkInRows,
            smashSpeedRows = smashSpeedRows,
            runtimeMetadataRows = runtimeMetadataRows,
            metadataSnapshotRows = metadataSnapshotRows,
            backupSchemaVersion = backupSchemaVersion,
            posteriorFormatPresent = posteriorFormatPresent,
            posteriorBootstrapMarker = posteriorBootstrapMarker,
            posteriorEvents = posteriorEvents,
            posteriorHistory = posteriorHistory,
            posteriorModelStates = posteriorModelStates,
            curvePosteriors = curvePosteriors,
            posteriorEvidence = posteriorEvidence,
            posteriorRevisions = posteriorRevisions,
            posteriorLocalStates = posteriorLocalStates,
            posteriorLocalHistory = posteriorLocalHistory,
            posteriorProxyHistory = posteriorProxyHistory,
            programSnapshot = programSnapshot,
            manifest = manifest
        )
    }

    private fun unwrapManifest(text: String): Pair<String, BackupManifest?> {
        if (!text.startsWith(MANIFEST_PREFIX)) return text to null
        val newline = text.indexOf('\n')
        if (newline < 0) {
            throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                "백업 manifest 뒤에 본문이 없습니다."
            )
        }
        val fields = text.substring(0, newline).trimEnd('\r').split(',', limit = 6)
        if (fields.size != 6 || fields[0] != MANIFEST_PREFIX) {
            throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                "백업 manifest 형식이 올바르지 않습니다."
            )
        }
        val formatVersion = fields[1].toIntOrNull()
            ?: throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                "백업 형식 버전을 읽을 수 없습니다."
            )
        if (formatVersion !in 8..CURRENT_BACKUP_FORMAT_VERSION) {
            throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_SCHEMA_UNSUPPORTED,
                "지원하지 않는 백업 형식 버전입니다: $formatVersion"
            )
        }
        val exportedAt = fields[3].toLongOrNull()
            ?: throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                "백업 생성 시각을 읽을 수 없습니다."
            )
        val counts = fields[4]
            .split('|')
            .filter(String::isNotBlank)
            .associate { token ->
                val parts = token.split('=', limit = 2)
                if (parts.size != 2 || parts[0].isBlank() || parts[1].toIntOrNull() == null) {
                    throw DataTransferFormatException(
                        DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                        "백업 개수 manifest가 올바르지 않습니다."
                    )
                }
                parts[0] to parts[1].toInt()
            }
        val body = text.substring(newline + 1).replace("\r\n", "\n").replace('\r', '\n')
        val actualHash = sha256(body)
        if (!actualHash.equals(fields[5], ignoreCase = true)) {
            throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_HASH_MISMATCH,
                "백업 본문 해시가 manifest와 일치하지 않습니다."
            )
        }
        return body to BackupManifest(
            formatVersion = formatVersion,
            appVersion = fields[2],
            exportedAt = exportedAt,
            entityCounts = counts,
            contentSha256 = fields[5]
        )
    }

    private fun validateManifestCounts(
        manifest: BackupManifest,
        data: RecordCsvImportData.Restore
    ) {
        val actual = restoreEntityCounts(data)
        val mismatches = manifest.entityCounts.filter { (key, expected) -> actual[key] != expected }
        if (mismatches.isNotEmpty()) {
            throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_COUNT_MISMATCH,
                "백업 manifest 개수와 본문 개수가 일치하지 않습니다: " +
                    mismatches.entries.joinToString { (key, value) -> "$key=$value/${actual[key]}" }
            )
        }
    }

    fun backupEntityCounts(
        exerciseCount: Int,
        dailyMetricCount: Int,
        dailyCheckInCount: Int,
        smashSpeedCount: Int,
        profileCount: Int,
        entryCount: Int,
        setCount: Int,
        runtimeMetadataCount: Int,
        programCount: Int,
        programItemCount: Int,
        programItemSetCount: Int = 0,
        programTombstoneCount: Int,
        metadataSnapshotCount: Int = 0
    ): Map<String, Int> = linkedMapOf(
        "exercise" to exerciseCount,
        "daily_metric" to dailyMetricCount,
        "daily_check_in" to dailyCheckInCount,
        "smash_speed" to smashSpeedCount,
        "initial_profile" to profileCount,
        "workout_entry" to entryCount,
        "workout_set" to setCount,
        "runtime_metadata" to runtimeMetadataCount,
        "exercise_metadata_snapshot" to metadataSnapshotCount,
        "program" to programCount,
        "program_item" to programItemCount,
        "program_item_set" to programItemSetCount,
        "program_tombstone" to programTombstoneCount
    )

    private fun restoreEntityCounts(data: RecordCsvImportData.Restore): Map<String, Int> =
        backupEntityCounts(
            exerciseCount = data.exerciseRows.size,
            dailyMetricCount = data.dailyRows.size,
            dailyCheckInCount = data.checkInRows.size,
            smashSpeedCount = data.smashSpeedRows.size,
            profileCount = if (data.profileRows.isEmpty()) 0 else 1,
            entryCount = data.setRows.map(RestoreSetRow::entryKey).distinct().size,
            setCount = data.setRows.size,
            runtimeMetadataCount = data.runtimeMetadataRows.size,
            programCount = data.programSnapshot?.programs?.size ?: 0,
            programItemCount = data.programSnapshot?.items?.size ?: 0,
            programItemSetCount = data.programSnapshot?.sets?.size ?: 0,
            programTombstoneCount = data.programSnapshot?.tombstones?.size ?: 0,
            metadataSnapshotCount = data.metadataSnapshotRows.size
        )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun legacyExerciseSnapshotRows(
        stableKey: String,
        row: List<String>,
        index: Map<String, Int>
    ): List<ExerciseMetadataSnapshotRow> {
        val columns = linkedMapOf(
            "is_custom" to "identity.isCustom",
            "category" to "exercise.category",
            "detail1" to "exercise.detail1",
            "detail2" to "exercise.detail2",
            "mode" to "exercise.mode",
            "description" to "exercise.description",
            "default_rest_seconds" to "exercise.defaultRestSeconds",
            "image_asset_name" to "exercise.imageAssetName",
            "primary_muscles" to "exercise.primaryMuscles",
            "secondary_muscles" to "exercise.secondaryMuscles",
            "equipment" to "exercise.equipment",
            "movement_pattern" to "exercise.movementPattern",
            "movement_category" to "exercise.movementCategory",
            "force_type" to "exercise.forceType",
            "body_region" to "exercise.bodyRegion",
            "laterality" to "exercise.laterality",
            "plane" to "exercise.plane",
            "sport_transfer_direct" to "exercise.sportTransferDirect",
            "sport_transfer_supportive" to "exercise.sportTransferSupportive",
            "load_profile" to "exercise.loadProfile",
            "metadata_confidence" to "exercise.metadataConfidence",
            "is_active" to "exercise.isActive",
            "needs_review" to "exercise.needsReview",
            "training_role_codes" to "relation.trainingRoles",
            "program_slot_capability_codes" to "relation.programSlotCapabilities"
        )
        return buildList {
            columns.forEach { (column, fieldKey) ->
                if (column !in index) return@forEach
                legacySnapshotRow(stableKey, fieldKey, row.value(index, column), row, index, column)?.let(::add)
            }
            if ("equipment" in index) {
                legacySnapshotRow(stableKey, "exercise.equipmentTags", row.value(index, "equipment"), row, index, "equipment")?.let(::add)
            }
        }
    }

    private fun legacyRuntimeSnapshotRows(
        stableKey: String,
        row: List<String>,
        index: Map<String, Int>
    ): List<ExerciseMetadataSnapshotRow> {
        val columns = linkedMapOf(
            "runtime_activity_kind" to "runtime.activityKind",
            "runtime_planning_eligibility" to "runtime.planningEligibility",
            "runtime_movement_family" to "runtime.movementFamily",
            "runtime_movement_subtype" to "runtime.movementSubtype",
            "runtime_program_slot" to "runtime.programSlot",
            "runtime_redundancy_group" to "runtime.redundancyGroup",
            "runtime_progress_metric_type" to "runtime.progressMetricType",
            "runtime_strength_progression_group" to "runtime.strengthProgressionGroup",
            "runtime_analysis_eligibility" to "runtime.analysisEligibility",
            "runtime_primary_stress_profile" to "runtime.primaryStressProfile",
            "runtime_secondary_stress_tags" to "runtime.secondaryStressTags",
            "runtime_tendon_stress_tags" to "runtime.tendonStressTags",
            "runtime_ligament_joint_stability_stress_tags" to "runtime.ligamentJointStabilityStressTags",
            "runtime_joint_impact_stress_tags" to "runtime.jointImpactStressTags",
            "runtime_cognitive_stress_tags" to "runtime.cognitiveStressTags",
            "runtime_sport_context_tags" to "runtime.sportContextTags",
            "runtime_recovery_decay_profile" to "runtime.recoveryDecayProfile",
            "runtime_stress_magnitude_hint" to "runtime.stressMagnitudeHint",
            "runtime_badminton_transfer_level" to "runtime.badmintonTransferLevel",
            "runtime_badminton_transfer_type" to "runtime.badmintonTransferType",
            "runtime_badminton_skill_targets" to "runtime.badmintonSkillTargets",
            "runtime_badminton_physical_qualities" to "runtime.badmintonPhysicalQualities",
            "runtime_transfer_confidence" to "runtime.transferConfidence",
            "runtime_source_confidence_level" to "runtime.sourceConfidenceLevel",
            "runtime_final_source_status" to "runtime.finalSourceStatus",
            "runtime_neuromuscular_stress_level" to "runtime.neuromuscularStressLevel",
            "runtime_systemic_muscular_stress_level" to "runtime.systemicMuscularStressLevel",
            "runtime_local_muscular_stress_level" to "runtime.localMuscularStressLevel",
            "runtime_joint_tendon_impact_stress_level" to "runtime.jointTendonImpactStressLevel",
            "runtime_movement_focus_demand_level" to "runtime.movementFocusDemandLevel",
            "runtime_recovery_duration_class" to "runtime.recoveryDurationClass"
        )
        return columns.mapNotNull { (column, fieldKey) ->
            column.takeIf(index::containsKey)?.let {
                legacySnapshotRow(stableKey, fieldKey, row.value(index, column), row, index, column)
            }
        }
    }

    private fun legacySnapshotRow(
        stableKey: String,
        fieldKey: String,
        rawValue: String,
        row: List<String>,
        index: Map<String, Int>,
        column: String
    ): ExerciseMetadataSnapshotRow? {
        val definition = requireNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey))
        val value = when (definition.valueEncoding) {
            ExerciseMetadataValueEncoding.BOOLEAN -> row.safeBool(index, column)?.toString() ?: return null
            ExerciseMetadataValueEncoding.INTEGER,
            ExerciseMetadataValueEncoding.DOUBLE -> rawValue.takeIf(String::isNotBlank) ?: return null
            else -> rawValue
        }
        return ExerciseMetadataSnapshotRow(
            stableKey = stableKey,
            fieldKey = fieldKey,
            fieldScope = definition.fieldScope,
            valueEncoding = definition.valueEncoding,
            value = value,
            isExplicitEmpty = value.isEmpty()
        )
    }

    private fun validateProgramSnapshot(
        programs: List<TrainingProgram>,
        items: List<ProgramBackupItem>,
        sets: List<ProgramBackupItemSet>,
        tombstones: List<TrainingProgramTombstone>
    ) {
        require(programs.all { program -> program.stableKey.isNotBlank() }) {
            "Program stable keys must be nonblank."
        }
        require(programs.map(TrainingProgram::stableKey).distinct().size == programs.size) {
            "Duplicate program stable key."
        }
        require(programs.all { program ->
            program.durationDays > 0 &&
                program.weeklyTrainingDays in 0..7 &&
                program.sessionMinutes in 0..1_440 &&
                program.badmintonTransferRatio.isFinite() &&
                program.badmintonTransferRatio in 0.0..1.0
        }) { "Invalid program settings in authoritative snapshot." }

        val programKeys = programs.mapTo(mutableSetOf(), TrainingProgram::stableKey)
        require(items.all { item -> item.programStableKey in programKeys }) {
            "Orphan program item."
        }
        require(items.all { item -> item.exerciseStableKey.isNotBlank() }) {
            "Program item exercise stable keys must be nonblank."
        }
        require(items.all { item ->
            item.weekNumber > 0 &&
                item.dayOfWeek in 1..7 &&
                item.orderIndex > 0 &&
                item.restSeconds in 0..3_600 &&
                item.setCount > 0 &&
                item.reps >= 0 &&
                item.weightKg.isFinite() &&
                item.weightKg >= 0.0 &&
                item.seconds >= 0
        }) { "Invalid program item values in authoritative snapshot." }
        require(
            items.map { item ->
                listOf(
                    item.programStableKey,
                    item.weekNumber.toString(),
                    item.dayOfWeek.toString(),
                    item.orderIndex.toString()
                ).joinToString("|")
            }.distinct().size == items.size
        ) { "Duplicate program item position." }

        val itemPositions = items.mapTo(mutableSetOf()) { item ->
            listOf(
                item.programStableKey,
                item.weekNumber.toString(),
                item.dayOfWeek.toString(),
                item.orderIndex.toString()
            ).joinToString("|")
        }
        require(sets.all { set ->
            listOf(
                set.programStableKey,
                set.weekNumber.toString(),
                set.dayOfWeek.toString(),
                set.orderIndex.toString()
            ).joinToString("|") in itemPositions
        }) { "Orphan program item set." }
        require(sets.all { set ->
            set.setIndex > 0 &&
                set.reps >= 0 &&
                set.weightKg.isFinite() &&
                set.weightKg >= 0.0 &&
                set.seconds >= 0
        }) { "Invalid program item set values in authoritative snapshot." }
        sets.groupBy { set ->
            listOf(
                set.programStableKey,
                set.weekNumber.toString(),
                set.dayOfWeek.toString(),
                set.orderIndex.toString()
            ).joinToString("|")
        }.values.forEach { itemSets ->
            val indices = itemSets.map(ProgramBackupItemSet::setIndex).sorted()
            require(indices == (1..indices.size).toList()) {
                "Program item set indices must be unique and contiguous."
            }
        }

        require(tombstones.all { tombstone -> tombstone.programStableKey.isNotBlank() }) {
            "Program tombstone stable keys must be nonblank."
        }
        require(
            tombstones.map(TrainingProgramTombstone::programStableKey).distinct().size == tombstones.size
        ) { "Duplicate program tombstone stable key." }
        require(programKeys.intersect(tombstones.mapTo(mutableSetOf(), TrainingProgramTombstone::programStableKey)).isEmpty()) {
            "Program and tombstone rows contradict each other."
        }
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

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && inQuotes && index + 1 < text.length && text[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index += 1
                    }
                    values += current.toString()
                    current.clear()
                    if (values.any(String::isNotBlank)) rows += values.toList()
                    values.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        require(!inQuotes) { "CSV contains an unterminated quoted field." }
        if (current.isNotEmpty() || values.isNotEmpty()) {
            values += current.toString()
            if (values.any(String::isNotBlank)) rows += values.toList()
        }
        return rows
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

    private fun List<String>.requiredInt(
        index: Map<String, Int>,
        key: String,
        rowType: String
    ): Int = requireNotNull(safeInt(index, key)) { "$rowType has invalid $key." }

    private fun List<String>.requiredLong(
        index: Map<String, Int>,
        key: String,
        rowType: String
    ): Long = requireNotNull(safeLong(index, key)) { "$rowType has invalid $key." }

    private fun List<String>.requiredDouble(
        index: Map<String, Int>,
        key: String,
        rowType: String
    ): Double = requireNotNull(safeDouble(index, key)) { "$rowType has invalid $key." }

    private inline fun <reified T : Enum<T>> List<String>.requiredEnum(
        index: Map<String, Int>,
        key: String,
        rowType: String
    ): T {
        val value = value(index, key).trim()
        return enumValues<T>().firstOrNull { it.name == value }
            ?: error("$rowType has invalid $key: $value")
    }

    private fun List<String>.safeBool(index: Map<String, Int>, key: String): Boolean? =
        when (value(index, key).trim().lowercase(Locale.US)) {
            "1", "true", "yes", "y" -> true
            "0", "false", "no", "n" -> false
            else -> null
        }

    private fun requireVisibleValue(
        row: List<String>,
        index: Map<String, Int>,
        column: String,
        expected: String
    ) {
        val visible = row.value(index, column)
        require(visible.isBlank() || visible == expected) {
            "Strength posterior backup metadata does not match its payload: $column"
        }
    }

    private fun String.isValidDate(): Boolean =
        runCatching { LocalDate.parse(this) }.isSuccess

    private fun RestoreCheckInRow.hasValidValues(): Boolean =
        (sleepHours == null || sleepHours in 0.0..24.0) &&
            listOf(overallFatigue, lowerBodyFatigue, jointTendonDiscomfort, focusMotivation)
                .filterNotNull()
                .all { value -> value in 1..5 }

    private fun Boolean.toCsvBool(): String = if (this) "1" else "0"

    private inline fun <reified T : Enum<T>> String.parseEnumTokens(column: String): Set<T> =
        split('|', ',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapTo(linkedSetOf()) { code ->
                enumValues<T>().firstOrNull { it.name == code }
                    ?: throw IllegalArgumentException("Invalid $column code: $code")
            }

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
            "habitualTrainingIntensity" to habitualTrainingIntensity.orEmpty(),
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
