package com.training.trackplanner.data

import java.security.MessageDigest

enum class WorkoutRestoreMode {
    REPLACE_OVERLAPPING_DATES,
    APPEND_TO_CURRENT
}

enum class ExerciseListRestoreMode {
    PRESERVE_CURRENT_ACTIVE_EXERCISES,
    APPLY_BACKUP_ACTIVE_EXERCISE_LIST
}

data class BackupRestoreImpact(
    val overlappingWorkoutDateCount: Int = 0,
    val currentEntriesOnOverlappingDates: Int = 0,
    val backupEntriesOnOverlappingDates: Int = 0,
    val currentSetsOnOverlappingDates: Int = 0,
    val backupSetsOnOverlappingDates: Int = 0,
    val sameSourceIdentityDifferentContentCount: Int = 0,
    val outOfScopeSameSourceIdentityDivergenceCount: Int = 0,
    val activeExercisesThatWouldDisappearCount: Int = 0,
    val activeExercisesThatWouldBeAddedCount: Int = 0,
    val referencedExercisesRequiringInternalRetentionCount: Int = 0,
    val targetOnlyNonCanonicalExercisesDeactivatedCount: Int = 0,
    val currentMetadataOverrideFieldsThatWouldBeRemovedCount: Int = 0,
    val currentExercisesWhoseMetadataEditsWouldBeRemovedCount: Int = 0,
    val backupOverrideFieldsThatWouldReplaceCurrentCount: Int = 0,
    val sameStableKeyCustomDefinitionsThatWouldBeReplacedCount: Int = 0,
    val representedExerciseCount: Int = 0,
    val targetCurrentExercisesNotRepresentedByBackupCount: Int = 0,
    val numericSourceReferencesRequiringRemapCount: Int = 0,
    val unresolvedNumericSourceReferenceCount: Int = 0,
    val malformedInternalSourceIdentityConflictCount: Int = 0
)

data class BackupRestorePreparation(
    val hasOverlappingWorkoutDates: Boolean,
    val impact: BackupRestoreImpact
)

internal data class RestoreWorkoutGraph(
    val entryKey: String,
    val sourceId: String?,
    val date: String,
    val stableKey: String,
    val exerciseName: String,
    val category: String,
    val restSeconds: Int,
    val notes: String,
    val rpe: Double?,
    val maxReps: Int?,
    val createdAt: Long?,
    val completedAt: Long?,
    val displayOrder: Int?,
    val firstConfirmedAt: Long?,
    val performedAt: Long?,
    val sets: List<RestoreSetRow>
) {
    fun contentToken(): String = listOf(
        date,
        stableKey,
        exerciseName,
        category,
        restSeconds,
        notes,
        rpe,
        maxReps,
        createdAt,
        completedAt,
        displayOrder,
        firstConfirmedAt,
        performedAt,
        sets.sortedBy(RestoreSetRow::setIndex).joinToString(";") { row ->
            listOf(
                row.setIndex,
                row.setConfirmed,
                row.reps,
                row.weightKg,
                row.seconds,
                row.rpe,
                row.setManualWeight,
                row.setRestSecondsOverride
            ).joinToString("|")
        }
    ).joinToString("\u001f")
}

internal data class BackupRestorePrepared(
    val data: RecordCsvImportData.Restore,
    val backupContentSha256: String,
    val workoutGraphs: List<RestoreWorkoutGraph>,
    val overlappingDates: Set<String>,
    val representedExerciseStableKeys: Set<String>,
    val baseImpact: BackupRestoreImpact
)

internal data class BackupRestorePlan(
    val prepared: BackupRestorePrepared,
    val workoutMode: WorkoutRestoreMode,
    val exerciseMode: ExerciseListRestoreMode,
    val impact: BackupRestoreImpact,
    val contentFingerprint: String
)

internal class BackupRestorePlanner(
    private val initialUserProfileDao: InitialUserProfileDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val smashSpeedDao: SmashSpeedDao,
    private val runtimeMetadataDao: RuntimeExerciseMetadataDao,
    private val relationDao: ExerciseRoleRelationDao,
    private val overrideDao: ExerciseMetadataUserOverrideDao,
    private val appMetaDao: AppMetaDao,
    private val canonicalExercises: () -> Map<String, Exercise>,
    private val semanticRevision: () -> String
) {
    suspend fun prepare(data: RecordCsvImportData.Restore): BackupRestorePrepared {
        val rawGraphs = data.toWorkoutGraphs()
        val malformedSources = rawGraphs.filter { it.sourceId != null }
            .groupBy(RestoreWorkoutGraph::sourceId)
            .count { (_, rows) -> rows.map(RestoreWorkoutGraph::contentToken).distinct().size > 1 }
        require(malformedSources == 0) {
            "Backup contains contradictory workout rows for one immutable source identity."
        }
        val graphs = rawGraphs.distinctBy { graph -> graph.sourceId ?: "legacy:${graph.entryKey}" }
        if ((data.manifest?.formatVersion ?: 0) >= 12) {
            require(graphs.all { it.sourceId?.isNotBlank() == true }) {
                "Backup format 12 workout rows require immutable source identities."
            }
        }
        val numericReferences = data.smashSpeedRows.filter {
            it.parentWorkoutEntrySourceId == null && it.parentWorkoutEntryId != null
        }
        val backupEntryKeys = graphs.mapTo(mutableSetOf(), RestoreWorkoutGraph::entryKey)
        val unresolvedNumeric = numericReferences.count { it.parentWorkoutEntryId.toString() !in backupEntryKeys }
        require(unresolvedNumeric == 0) {
            "Backup contains a smash-speed parent ID that cannot be resolved inside the backup workout graph."
        }

        val currentGraphs = workoutDao.allEntriesWithSets().map(WorkoutEntryWithSets::toRestoreGraph)
        val backupDates = graphs.mapTo(mutableSetOf(), RestoreWorkoutGraph::date)
        val currentDates = currentGraphs.mapTo(mutableSetOf(), RestoreWorkoutGraph::date)
        val overlap = backupDates intersect currentDates
        val currentBySource = currentGraphs.mapNotNull { graph -> graph.sourceId?.let { it to graph } }.toMap()
        val divergent = graphs.count { graph ->
            graph.sourceId?.let(currentBySource::get)?.contentToken()?.let { it != graph.contentToken() } == true
        }
        val outOfScope = graphs.count { graph ->
            val current = graph.sourceId?.let(currentBySource::get) ?: return@count false
            current.date !in overlap && current.contentToken() != graph.contentToken()
        }
        val represented = data.exerciseRows.mapTo(sortedSetOf()) { it.stableKey.trim() }.filter(String::isNotBlank).toSet()
        val currentExercises = exerciseDao.allExercises()
        val currentOverrides = overrideDao.all()
        val backupOverrides = data.metadataUserOverrideRows
        val currentByKey = currentExercises.associateBy(Exercise::stableKey)
        val backupByKey = data.exerciseRows.associateBy(RestoreExerciseRow::stableKey)
        val customReplacements = represented.count { key ->
            val current = currentByKey[key]
            val backup = backupByKey[key]
            current?.isCustom == true && backup?.isCustom == true && current.toAuthorityToken() != backup.toAuthorityToken()
        }
        val replacements = backupOverrides.count { backup ->
            currentOverrides.any { current ->
                current.stableKey == backup.stableKey && current.fieldScope == backup.fieldScope &&
                    current.fieldKey == backup.fieldKey && current != backup
            }
        }
        val base = BackupRestoreImpact(
            overlappingWorkoutDateCount = overlap.size,
            currentEntriesOnOverlappingDates = currentGraphs.count { it.date in overlap },
            backupEntriesOnOverlappingDates = graphs.count { it.date in overlap },
            currentSetsOnOverlappingDates = currentGraphs.filter { it.date in overlap }.sumOf { it.sets.size },
            backupSetsOnOverlappingDates = graphs.filter { it.date in overlap }.sumOf { it.sets.size },
            sameSourceIdentityDifferentContentCount = divergent,
            outOfScopeSameSourceIdentityDivergenceCount = outOfScope,
            backupOverrideFieldsThatWouldReplaceCurrentCount = replacements,
            sameStableKeyCustomDefinitionsThatWouldBeReplacedCount = customReplacements,
            representedExerciseCount = represented.size,
            targetCurrentExercisesNotRepresentedByBackupCount = currentExercises.count { it.stableKey !in represented },
            numericSourceReferencesRequiringRemapCount = numericReferences.size,
            unresolvedNumericSourceReferenceCount = unresolvedNumeric,
            malformedInternalSourceIdentityConflictCount = malformedSources
        )
        return BackupRestorePrepared(
            data = data,
            backupContentSha256 = data.manifest?.contentSha256.orEmpty().ifBlank {
                sha256(graphs.joinToString("\n") { it.contentToken() })
            },
            workoutGraphs = graphs,
            overlappingDates = overlap,
            representedExerciseStableKeys = represented,
            baseImpact = base
        )
    }

    suspend fun plan(
        prepared: BackupRestorePrepared,
        workoutMode: WorkoutRestoreMode,
        exerciseMode: ExerciseListRestoreMode
    ): BackupRestorePlan {
        val currentExercises = exerciseDao.allExercises()
        val currentByKey = currentExercises.associateBy(Exercise::stableKey)
        val backupByKey = prepared.data.exerciseRows.associateBy(RestoreExerciseRow::stableKey)
        val canonicalKeys = canonicalExercises().keys
        val currentOverrides = overrideDao.all()
        val backupOverrideKeys = prepared.data.metadataUserOverrideRows
            .mapTo(mutableSetOf()) { Triple(it.stableKey, it.fieldScope, it.fieldKey) }
        val represented = prepared.representedExerciseStableKeys
        val projectedWorkoutKeys = projectedWorkoutStableKeys(prepared, workoutMode)
        val programKeys = prepared.data.programSnapshot?.items
            ?.mapTo(mutableSetOf(), ProgramBackupItem::exerciseStableKey)
            ?: programDao.allProgramItems().mapTo(mutableSetOf(), TrainingProgramItem::exerciseStableKey)
        val requiredKeys = projectedWorkoutKeys + programKeys
        val disappearing = if (exerciseMode == ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST) {
            currentExercises.count { current ->
                current.isActive && current.stableKey in represented && backupByKey[current.stableKey]?.isActive == false
            }
        } else 0
        val added = backupByKey.values.count { row ->
            row.isActive && currentByKey[row.stableKey]?.isActive != true
        }
        val targetOnlyNonCanonical = if (
            exerciseMode == ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST
        ) {
            currentExercises.count { exercise ->
                exercise.isActive && exercise.stableKey !in represented && exercise.stableKey !in canonicalKeys
            }
        } else 0
        val removedOverrides = if (
            exerciseMode == ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST &&
            (prepared.data.manifest?.formatVersion ?: 0) >= 12
        ) {
            currentOverrides.count { override ->
                override.stableKey in represented &&
                    Triple(override.stableKey, override.fieldScope, override.fieldKey) !in backupOverrideKeys
            }
        } else 0
        val removedOverrideExercises = if (removedOverrides == 0) 0 else currentOverrides
            .filter { override ->
                override.stableKey in represented &&
                    Triple(override.stableKey, override.fieldScope, override.fieldKey) !in backupOverrideKeys
            }
            .map(ExerciseMetadataUserOverrideEntity::stableKey)
            .distinct()
            .size
        val impact = prepared.baseImpact.copy(
            activeExercisesThatWouldDisappearCount = disappearing,
            activeExercisesThatWouldBeAddedCount = added,
            referencedExercisesRequiringInternalRetentionCount = requiredKeys.count { key ->
                backupByKey[key]?.isActive == false || (key !in backupByKey && currentByKey[key]?.isActive == false)
            },
            targetOnlyNonCanonicalExercisesDeactivatedCount = targetOnlyNonCanonical,
            currentMetadataOverrideFieldsThatWouldBeRemovedCount = removedOverrides,
            currentExercisesWhoseMetadataEditsWouldBeRemovedCount = removedOverrideExercises
        )
        val fingerprint = currentFingerprint(
            backupHash = prepared.backupContentSha256,
            workoutMode = workoutMode,
            exerciseMode = exerciseMode
        )
        return BackupRestorePlan(prepared, workoutMode, exerciseMode, impact, fingerprint)
    }

    suspend fun currentFingerprint(
        backupHash: String,
        workoutMode: WorkoutRestoreMode,
        exerciseMode: ExerciseListRestoreMode
    ): String {
        val tokens = buildList {
            add("backup=$backupHash")
            add("workoutMode=${workoutMode.name}")
            add("exerciseMode=${exerciseMode.name}")
            add("semanticRevision=${semanticRevision()}")
            workoutDao.allEntriesWithSets().map(WorkoutEntryWithSets::toRestoreGraph)
                .sortedBy { it.sourceId ?: it.entryKey }.forEach { add("workout=${it.sourceId}|${it.contentToken()}") }
            exerciseDao.allExercises().sortedBy(Exercise::stableKey).forEach { add("exercise=${it.toAuthorityToken()}") }
            runtimeMetadataDao.all().sortedBy(RuntimeExerciseMetadataEntity::stableKey).forEach { add("runtime=$it") }
            relationDao.allTrainingRoles().forEach { add("role=$it") }
            relationDao.allProgramSlotCapabilities().forEach { add("slot=$it") }
            overrideDao.all().forEach { add("override=$it") }
            programDao.allPrograms().forEach { add("program=$it") }
            programDao.allProgramItems().forEach { add("programItem=$it") }
            programDao.allProgramItemSets().forEach { add("programSet=$it") }
            programDao.allProgramTombstones().forEach { add("programTombstone=$it") }
            initialUserProfileDao.profile()?.let { add("profile=$it") }
            dailyMetricDao.allMetrics().forEach { add("dailyMetric=$it") }
            dailyCheckInDao.all().forEach { add("dailyCheckIn=$it") }
            smashSpeedDao.all().forEach { add("smashSpeed=$it") }
            appMetaDao.all()
                .filterNot { it.key.startsWith("data_transfer_report_") }
                .forEach { add("appMeta=${it.key}|${it.value}|${it.updatedAt}") }
        }
        return sha256(tokens.joinToString("\n"))
    }

    private suspend fun projectedWorkoutStableKeys(
        prepared: BackupRestorePrepared,
        mode: WorkoutRestoreMode
    ): Set<String> {
        val current = workoutDao.allEntriesWithSets().map(WorkoutEntryWithSets::toRestoreGraph)
        val retained = if (mode == WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES) {
            current.filter { it.date !in prepared.overlappingDates }
        } else current
        return (retained + prepared.workoutGraphs).mapTo(mutableSetOf(), RestoreWorkoutGraph::stableKey)
    }
}

internal fun RecordCsvImportData.Restore.toWorkoutGraphs(): List<RestoreWorkoutGraph> = setRows
    .groupBy(RestoreSetRow::entryKey)
    .values
    .map { rows ->
        val first = rows.first()
        require(rows.all { it.date == first.date && it.stableKey == first.stableKey }) {
            "One backup entry key contains contradictory workout rows."
        }
        RestoreWorkoutGraph(
            entryKey = first.entryKey,
            sourceId = first.entrySourceId,
            date = first.date,
            stableKey = first.stableKey,
            exerciseName = first.exerciseName,
            category = first.category,
            restSeconds = first.restSeconds,
            notes = first.notes,
            rpe = first.rpe,
            maxReps = first.maxReps,
            createdAt = first.entryCreatedAt,
            completedAt = first.entryCompletedAt,
            displayOrder = first.entryDisplayOrder,
            firstConfirmedAt = first.entryFirstConfirmedAt,
            performedAt = first.entryPerformedAt,
            sets = rows.sortedBy(RestoreSetRow::setIndex)
        )
    }
    .sortedWith(compareBy(RestoreWorkoutGraph::date, RestoreWorkoutGraph::entryKey))

internal fun WorkoutEntryWithSets.toRestoreGraph(): RestoreWorkoutGraph = RestoreWorkoutGraph(
    entryKey = entry.id.toString(),
    sourceId = entry.backupSourceId,
    date = entry.date,
    stableKey = entry.exerciseStableKey,
    exerciseName = entry.exerciseName,
    category = entry.category,
    restSeconds = entry.restSeconds,
    notes = entry.notes,
    rpe = sets.sortedBy(WorkoutSet::setIndex).firstOrNull()?.rpe ?: entry.rpe,
    maxReps = entry.maxReps,
    createdAt = entry.createdAt,
    completedAt = entry.completedAt,
    displayOrder = entry.displayOrder,
    firstConfirmedAt = entry.firstConfirmedAt,
    performedAt = entry.performedAt,
    sets = sets.sortedBy(WorkoutSet::setIndex).map { set ->
        RestoreSetRow(
            date = entry.date,
            entryKey = entry.id.toString(),
            entryOrder = entry.displayOrder,
            exerciseName = entry.exerciseName,
            stableKey = entry.exerciseStableKey,
            category = entry.category,
            confirmed = sets.any(WorkoutSet::confirmed),
            restSeconds = entry.restSeconds,
            rpe = set.rpe ?: entry.rpe,
            maxReps = entry.maxReps,
            notes = entry.notes,
            setIndex = set.setIndex,
            setConfirmed = set.confirmed,
            reps = set.reps,
            weightKg = set.weightKg,
            seconds = set.seconds,
            sleepHours = null,
            bodyWeightKg = null,
            entrySourceId = entry.backupSourceId,
            entryCreatedAt = entry.createdAt,
            entryCompletedAt = entry.completedAt,
            entryDisplayOrder = entry.displayOrder,
            entryFirstConfirmedAt = entry.firstConfirmedAt,
            entryPerformedAt = entry.performedAt,
            setManualWeight = set.manualWeight,
            setRestSecondsOverride = set.restSecondsOverride
        )
    }
)

private fun Exercise.toAuthorityToken(): String = listOf(
    stableKey,
    name,
    category,
    description,
    defaultRestSeconds,
    movementPattern,
    movementCategory,
    primaryMuscles,
    secondaryMuscles,
    equipment,
    isActive,
    archivedAt,
    isCustom,
    needsReview
).joinToString("|")

private fun RestoreExerciseRow.toAuthorityToken(): String = listOf(
    stableKey,
    name,
    category,
    description,
    defaultRestSeconds,
    movementPattern,
    movementCategory,
    primaryMuscles,
    secondaryMuscles,
    equipment,
    isActive,
    archivedAt,
    isCustom,
    needsReview
).joinToString("|")

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
