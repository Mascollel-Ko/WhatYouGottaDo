package com.training.trackplanner.data

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.VersionedDoubleArrayCodec
import com.training.trackplanner.analysis.strengthperformance.toPosterior

internal class BackupRestoreImportService(
    private val db: TrainingDatabase,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val dailyStatusService: DailyStatusService,
    private val smashSpeedDao: SmashSpeedDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val appMetaDao: AppMetaDao,
    private val strengthPosteriorDao: StrengthPosteriorDao,
    private val strengthPosteriorCoordinator: StrengthPosteriorUpdateCoordinator,
    private val seedExercisesByStableKey: () -> Map<String, Exercise>,
    private val profileFromRows: (List<RestoreProfileRow>) -> InitialUserProfile?,
    private val upsertRestoredExercise: suspend (RestoreExerciseRow, Map<String, Exercise>, Set<String>) -> Boolean,
    private val hasDuplicateRestoreEntry: suspend (RestoreSetRow, List<RestoreSetRow>) -> Boolean,
    private val findOrCreateImportedExercise: suspend (String, String, String, Map<String, Exercise>) -> Exercise
) {
    suspend fun importRestoreCsv(data: RecordCsvImportData.Restore): RecordCsvTransferResult {
        var exerciseCount = 0
        var dailyCount = 0
        var checkInCount = 0
        var smashSpeedCount = 0
        var profileCount = 0
        var entryCount = 0
        var setCount = 0
        var programCount = 0
        var programItemCount = 0
        var programItemSetCount = 0
        var programTombstoneCount = 0
        var posteriorCounts = PosteriorRestoreCounts()
        var skipped = 0
        db.withTransaction {
            val seedByStableKey = seedExercisesByStableKey()
            val runtimeMetadataRows = data.runtimeMetadataRows
            val restoredRuntimeOverrideKeys = ExerciseMetadataOverrideBackupMapper.overrideKeys(runtimeMetadataRows)
            profileFromRows(data.profileRows)?.let { profile ->
                initialUserProfileDao.upsert(profile)
                profileCount = 1
            }
            data.exerciseRows.forEach { row ->
                if (upsertRestoredExercise(row, seedByStableKey, restoredRuntimeOverrideKeys)) {
                    exerciseCount += 1
                }
            }
            runtimeMetadataRows.forEach { metadata ->
                runtimeExerciseMetadataDao.upsert(
                    metadata.copy(safeForSeedMutation = false).toEntity()
                )
            }
            data.programSnapshot?.let { snapshot ->
                val counts = restoreProgramSnapshot(snapshot)
                programCount = counts.programs
                programItemCount = counts.items
                programItemSetCount = counts.sets
                programTombstoneCount = counts.tombstones
            }
            val importedDailyMetrics = mutableMapOf<String, DailyMetric>()
            data.dailyRows.forEach { row ->
                if (row.sleepHours != null || row.bodyWeightKg != null) {
                    dailyStatusService.saveDailyMetricInTransaction(
                        date = row.date,
                        sleepHours = row.sleepHours,
                        bodyWeightKg = row.bodyWeightKg
                    )
                    importedDailyMetrics[row.date] = dailyMetricDao.metric(row.date)!!
                    dailyCount += 1
                }
            }
            data.checkInRows.forEach { row ->
                val now = System.currentTimeMillis()
                val canonicalMetric = importedDailyMetrics[row.date]
                val existingCheckIn = dailyCheckInDao.getForDate(row.date)
                dailyStatusService.upsertInTransaction(
                    DailyCheckIn(
                        date = row.date,
                        sleepHours = canonicalMetric?.sleepHours ?: row.sleepHours ?: existingCheckIn?.sleepHours,
                        bodyWeightKg = canonicalMetric?.bodyWeightKg ?: existingCheckIn?.bodyWeightKg,
                        overallFatigue = row.overallFatigue,
                        lowerBodyFatigue = row.lowerBodyFatigue,
                        jointTendonDiscomfort = row.jointTendonDiscomfort,
                        focusMotivation = row.focusMotivation,
                        note = row.note,
                        createdAt = row.createdAt ?: now,
                        updatedAt = row.updatedAt ?: now
                    ),
                    preserveUpdatedAt = true
                )
                if (canonicalMetric == null && row.sleepHours != null) dailyCount += 1
                dailyMetricDao.metric(row.date)?.let { metric ->
                    importedDailyMetrics[row.date] = metric
                }
                checkInCount += 1
            }
            data.smashSpeedRows.forEach { row ->
                val existing = smashSpeedDao.forDate(row.date)
                val duplicate = existing.any { record ->
                    record.attemptIndex == row.attemptIndex &&
                        kotlin.math.abs(record.speedKmh - row.speedKmh) < 0.001 &&
                        record.note == row.note
                }
                if (duplicate) {
                    skipped += 1
                } else {
                    val now = System.currentTimeMillis()
                    smashSpeedDao.upsert(
                        SmashSpeedRecord(
                            date = row.date,
                            speedKmh = row.speedKmh,
                            attemptIndex = row.attemptIndex,
                            source = row.source ?: "external_app",
                            note = row.note,
                            parentWorkoutEntryId = row.parentWorkoutEntryId,
                            createdAt = row.createdAt ?: now,
                            updatedAt = row.updatedAt ?: now
                        ).validated()
                    )
                    smashSpeedCount += 1
                }
            }
            data.setRows
                .filter { row -> row.sleepHours != null || row.bodyWeightKg != null }
                .distinctBy { row -> row.date }
                .forEach { row ->
                    val existingMetric = importedDailyMetrics[row.date] ?: dailyMetricDao.metric(row.date)
                    dailyStatusService.saveDailyMetricInTransaction(
                        date = row.date,
                        sleepHours = row.sleepHours ?: existingMetric?.sleepHours,
                        bodyWeightKg = row.bodyWeightKg ?: existingMetric?.bodyWeightKg
                    )
                    importedDailyMetrics[row.date] = dailyMetricDao.metric(row.date)!!
                    dailyCount += 1
                }
            data.setRows
                .groupBy { row -> row.entryKey }
                .values
                .sortedWith(
                    compareBy<List<RestoreSetRow>> { rows -> rows.first().date }
                        .thenBy { rows -> rows.first().entryOrder }
                )
                .forEach { rows ->
                    val first = rows.first()
                    val importedSets = rows.sortedBy { row -> row.setIndex }
                    if (hasDuplicateRestoreEntry(first, importedSets)) {
                        skipped += 1
                        return@forEach
                    }
                    val exercise = findOrCreateImportedExercise(
                        first.exerciseName,
                        first.category,
                        first.stableKey,
                        seedByStableKey
                    )
                    val confirmedCount = importedSets.count { row -> row.setConfirmed }
                    val entryId = workoutDao.insertEntry(
                        WorkoutEntry(
                            date = first.date,
                            exerciseStableKey = exercise.stableKey,
                            exerciseName = exercise.name,
                            category = first.category,
                            restSeconds = first.restSeconds,
                            notes = first.notes,
                            rpe = first.rpe,
                            maxReps = first.maxReps,
                            completedAt = if (confirmedCount > 0) System.currentTimeMillis() else null
                        )
                    )
                    importedSets.forEachIndexed { index, row ->
                        workoutDao.insertSet(
                            WorkoutSet(
                                entryId = entryId,
                                setIndex = index + 1,
                                reps = row.reps,
                                weightKg = row.weightKg,
                                seconds = row.seconds,
                                confirmed = row.setConfirmed,
                                manualWeight = row.weightKg > 0.0,
                                rpe = row.rpe
                            )
                        )
                        setCount += 1
                    }
                    entryCount += 1
                }
            validateRestoredExerciseReferences()
            strengthPosteriorCoordinator.scheduleDerivedResetRebuild()
        }
        skipped += data.posteriorEvents.size +
            data.posteriorHistory.size +
            data.posteriorModelStates.size +
            data.curvePosteriors.size +
            data.posteriorEvidence.size +
            data.posteriorRevisions.size +
            data.posteriorLocalStates.size +
            data.posteriorLocalHistory.size +
            data.posteriorProxyHistory.size
        val strengthLifecycle = strengthPosteriorCoordinator.ensureCurrentRevision()
        if (strengthLifecycle.status == StrengthAnalysisLifecycleStatus.CURRENT) {
            val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
            posteriorCounts = PosteriorRestoreCounts(
                events = strengthPosteriorDao.eventsForRevision(revisionKey).size,
                history = strengthPosteriorDao.historyForRevision(revisionKey).size,
                states = strengthPosteriorDao.modelState(
                    StrengthModelRevisionPolicy.modelInstanceKey(revisionKey)
                )?.let { 1 } ?: 0,
                curves = strengthPosteriorDao.allCurvePosteriors()
                    .count { row -> row.curveSubjectKey.startsWith("$revisionKey|") },
                evidence = strengthPosteriorDao.evidenceForRevision(revisionKey).size,
                revisions = strengthPosteriorDao.allRevisions().size,
                localStates = strengthPosteriorDao.localStates(revisionKey).size,
                localHistory = strengthPosteriorDao.localHistory(revisionKey).size,
                proxyHistory = strengthPosteriorDao.proxyHistory(revisionKey).size
            )
            appMetaDao.upsert(
                AppMeta(
                    key = StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY,
                    value = "RAW_BACKUP_CURRENT_REBUILD|${System.currentTimeMillis()}|events=${posteriorCounts.events}"
                )
            )
        }
        return RecordCsvTransferResult(
            format = "restore",
            exerciseCount = exerciseCount,
            dailyMetricCount = dailyCount,
            dailyCheckInCount = checkInCount,
            smashSpeedCount = smashSpeedCount,
            profileCount = profileCount,
            entryCount = entryCount,
            setCount = setCount,
            posteriorEventCount = posteriorCounts.events,
            posteriorHistoryCount = posteriorCounts.history,
            posteriorStateCount = posteriorCounts.states,
            posteriorCurveCount = posteriorCounts.curves,
            posteriorEvidenceCount = posteriorCounts.evidence,
            posteriorRevisionCount = posteriorCounts.revisions,
            posteriorLocalStateCount = posteriorCounts.localStates,
            posteriorLocalHistoryCount = posteriorCounts.localHistory,
            posteriorProxyTransferCount = posteriorCounts.proxyHistory,
            programCount = programCount,
            programItemCount = programItemCount,
            programItemSetCount = programItemSetCount,
            programTombstoneCount = programTombstoneCount,
            skippedDuplicateCount = skipped,
            warningCount = data.warningCount +
                if (strengthLifecycle.status == StrengthAnalysisLifecycleStatus.REBUILD_FAILED) 1 else 0
        )
    }

    private suspend fun restoreProgramSnapshot(
        snapshot: RestoreProgramSnapshot
    ): ProgramRestoreCounts {
        val exercisesByStableKey = exerciseDao.allExercises()
            .filter { exercise -> exercise.stableKey.isNotBlank() }
            .associateBy(Exercise::stableKey)
        val resolvedItems = snapshot.items.map { item ->
            item to requireNotNull(exercisesByStableKey[item.exerciseStableKey]) {
                "Program item exercise stable key cannot be resolved: ${item.exerciseStableKey}"
            }
        }
        val itemPositions = snapshot.items.associateBy(ProgramBackupItem::logicalPosition)
        require(snapshot.sets.all { set -> set.logicalPosition() in itemPositions }) {
            "Program item set parent cannot be resolved."
        }

        programDao.deleteAllProgramItemSets()
        programDao.deleteAllProgramItems()
        programDao.deleteAllPrograms()
        programDao.deleteAllProgramTombstones()
        snapshot.tombstones
            .sortedBy(TrainingProgramTombstone::programStableKey)
            .forEach { tombstone -> programDao.upsertProgramTombstone(tombstone) }

        val localProgramIds = snapshot.programs
            .sortedBy(TrainingProgram::stableKey)
            .associate { program ->
                program.stableKey to programDao.insertProgram(program.copy(id = 0))
            }
        val localItemIds = resolvedItems.sortedWith(
            compareBy<Pair<ProgramBackupItem, Exercise>> { (item, _) -> item.programStableKey }
                .thenBy { (item, _) -> item.weekNumber }
                .thenBy { (item, _) -> item.dayOfWeek }
                .thenBy { (item, _) -> item.orderIndex }
                .thenBy { (item, _) -> item.exerciseStableKey }
        ).associate { (item, exercise) ->
            item.logicalPosition() to programDao.insertProgramItem(
                TrainingProgramItem(
                    programId = checkNotNull(localProgramIds[item.programStableKey]),
                    weekNumber = item.weekNumber,
                    dayOfWeek = item.dayOfWeek,
                    orderIndex = item.orderIndex,
                    exerciseStableKey = exercise.stableKey,
                    exerciseName = item.exerciseName.ifBlank { exercise.name },
                    category = item.category.ifBlank { exercise.category },
                    restSeconds = item.restSeconds,
                    prescription = item.prescription,
                    setCount = item.setCount,
                    reps = item.reps,
                    weightKg = item.weightKg,
                    seconds = item.seconds,
                    trainingSlot = item.trainingSlot,
                    dayIntensity = item.dayIntensity,
                    weightSource = item.weightSource
                )
            )
        }
        snapshot.sets
            .sortedWith(
                compareBy(ProgramBackupItemSet::programStableKey)
                    .thenBy(ProgramBackupItemSet::weekNumber)
                    .thenBy(ProgramBackupItemSet::dayOfWeek)
                    .thenBy(ProgramBackupItemSet::orderIndex)
                    .thenBy(ProgramBackupItemSet::setIndex)
            )
            .forEach { set ->
                programDao.insertProgramItemSets(
                    listOf(
                        TrainingProgramItemSet(
                            programItemId = checkNotNull(localItemIds[set.logicalPosition()]),
                            setIndex = set.setIndex,
                            reps = set.reps,
                            weightKg = set.weightKg,
                            seconds = set.seconds
                        )
                    )
                )
            }
        return ProgramRestoreCounts(
            programs = snapshot.programs.size,
            items = snapshot.items.size,
            sets = snapshot.sets.size,
            tombstones = snapshot.tombstones.size
        )
    }

    private suspend fun restorePosteriorRows(data: RecordCsvImportData.Restore): PosteriorRestoreCounts {
        var counts = PosteriorRestoreCounts()
        data.posteriorRevisions.forEach { incoming ->
            val existing = strengthPosteriorDao.revision(incoming.revisionKey)
            if (existing == null) {
                strengthPosteriorDao.insertRevisionStrict(incoming)
                counts = counts.copy(revisions = counts.revisions + 1)
            } else {
                require(existing == incoming) {
                    "Strength model revision conflict: ${incoming.revisionKey}"
                }
                counts = counts.copy(skipped = counts.skipped + 1)
            }
        }
        data.posteriorEvents.forEach { incoming ->
            val byUuid = strengthPosteriorDao.eventByUuid(incoming.eventUuid)
            if (byUuid != null) {
                require(byUuid.completionFingerprint == incoming.completionFingerprint) {
                    "Strength posterior event UUID conflict: ${incoming.eventUuid}"
                }
                require(byUuid == incoming) {
                    "Strength posterior event is not an exact duplicate: ${incoming.eventUuid}"
                }
                counts = counts.copy(skipped = counts.skipped + 1)
                return@forEach
            }
            strengthPosteriorDao.eventByCompletionFingerprint(incoming.completionFingerprint)?.let { existing ->
                require(existing.eventUuid == incoming.eventUuid) {
                    "Strength posterior completion fingerprint conflict: ${incoming.completionFingerprint}"
                }
            }
            strengthPosteriorDao.eventBySessionKeyAndRevision(incoming.sessionKey, incoming.revisionKey)?.let { existing ->
                require(existing == incoming) { "Strength posterior session conflict: ${incoming.sessionKey}" }
            }
            strengthPosteriorDao.insertEventStrict(incoming)
            counts = counts.copy(events = counts.events + 1)
        }

        data.posteriorHistory.groupBy(StrengthPosteriorHistoryEntity::eventUuid).forEach { (eventUuid, rows) ->
            require(strengthPosteriorDao.eventByUuid(eventUuid) != null) {
                "Strength posterior history has no event: $eventUuid"
            }
            val unique = rows.distinct()
            require(unique.distinctBy(StrengthPosteriorHistoryEntity::targetKey).size == unique.size) {
                "Conflicting immutable target history in backup: $eventUuid"
            }
            val incoming = unique.sortedBy(StrengthPosteriorHistoryEntity::targetKey)
            val existing = strengthPosteriorDao.historyForEvent(eventUuid)
            if (existing.isEmpty()) {
                strengthPosteriorDao.insertHistoryStrict(incoming)
                counts = counts.copy(
                    history = counts.history + incoming.size,
                    skipped = counts.skipped + rows.size - unique.size
                )
            } else {
                require(existing == incoming) { "Immutable strength posterior history conflict: $eventUuid" }
                counts = counts.copy(skipped = counts.skipped + rows.size)
            }
        }

        data.posteriorEvidence.forEach { incoming ->
            require(strengthPosteriorDao.eventByUuid(incoming.eventUuid) != null) {
                "Strength posterior evidence has no event: ${incoming.eventUuid}"
            }
            val existing = strengthPosteriorDao.evidenceByFingerprint(incoming.evidenceFingerprint)
            if (existing == null) {
                strengthPosteriorDao.insertEvidenceStrict(listOf(incoming))
                counts = counts.copy(evidence = counts.evidence + 1)
            } else {
                require(existing == incoming) {
                    "Immutable strength posterior evidence conflict: ${incoming.evidenceFingerprint}"
                }
                counts = counts.copy(skipped = counts.skipped + 1)
            }
        }

        data.posteriorLocalHistory
            .groupBy { history -> history.revisionKey to history.eventUuid }
            .forEach { (key, rows) ->
                val (revisionKey, eventUuid) = key
                require(strengthPosteriorDao.eventByUuid(eventUuid)?.revisionKey == revisionKey) {
                    "Strength exercise-local history has no matching revision event: $revisionKey/$eventUuid"
                }
                val incoming = rows.distinct().sortedBy(StrengthExercisePerformanceHistoryEntity::exerciseStableKey)
                require(incoming.size == rows.size) {
                    "Duplicate strength exercise-local history in backup: $revisionKey/$eventUuid"
                }
                val existing = strengthPosteriorDao.localHistory(revisionKey)
                    .filter { history -> history.eventUuid == eventUuid }
                if (existing.isEmpty()) {
                    strengthPosteriorDao.insertLocalHistoryStrict(incoming)
                    counts = counts.copy(localHistory = counts.localHistory + incoming.size)
                } else {
                    require(existing == incoming) {
                        "Immutable strength exercise-local history conflict: $revisionKey/$eventUuid"
                    }
                    counts = counts.copy(skipped = counts.skipped + rows.size)
                }
            }

        data.posteriorProxyHistory
            .groupBy { history -> history.revisionKey to history.eventUuid }
            .forEach { (key, rows) ->
                val (revisionKey, eventUuid) = key
                require(strengthPosteriorDao.eventByUuid(eventUuid)?.revisionKey == revisionKey) {
                    "Strength proxy history has no matching revision event: $revisionKey/$eventUuid"
                }
                rows.forEach { history ->
                    val loading = VersionedDoubleArrayCodec.decode(history.sharedLoadingVectorEncoded)
                    val keys = history.orderedSharedFactorKeys.split('|').filter(String::isNotBlank)
                    require(loading.size == keys.size && history.transferFingerprint.isNotBlank())
                    if (revisionKey == StrengthModelRevisionPolicy.CURRENT_REVISION_KEY) {
                        require(history.targetSpecificContribution == 0.0)
                    }
                }
                val incoming = rows.distinct().sortedWith(
                    compareBy(StrengthProxyTransferHistoryEntity::exerciseStableKey, StrengthProxyTransferHistoryEntity::targetKey)
                )
                require(incoming.size == rows.size) {
                    "Duplicate strength proxy history in backup: $revisionKey/$eventUuid"
                }
                val existing = strengthPosteriorDao.proxyHistory(revisionKey)
                    .filter { history -> history.eventUuid == eventUuid }
                if (existing.isEmpty()) {
                    strengthPosteriorDao.insertProxyHistoryStrict(incoming)
                    counts = counts.copy(proxyHistory = counts.proxyHistory + incoming.size)
                } else {
                    require(existing == incoming) {
                        "Immutable strength proxy history conflict: $revisionKey/$eventUuid"
                    }
                    counts = counts.copy(skipped = counts.skipped + rows.size)
                }
            }

        data.curvePosteriors.forEach { incoming ->
            incoming.toPosterior()
            val existing = strengthPosteriorDao.curvePosterior(incoming.curveSubjectKey)
            if (existing == null) {
                strengthPosteriorDao.upsertCurvePosterior(incoming)
                counts = counts.copy(curves = counts.curves + 1)
            } else {
                require(existing == incoming) {
                    "Strength curve-posterior conflict: ${incoming.curveSubjectKey}"
                }
                counts = counts.copy(skipped = counts.skipped + 1)
            }
        }

        data.posteriorModelStates.forEach { incoming ->
            StrengthPosteriorModel.fromEntity(incoming)
            val existing = strengthPosteriorDao.modelState(incoming.modelInstanceKey)
            if (existing == null) {
                strengthPosteriorDao.upsertModelState(incoming)
                counts = counts.copy(states = counts.states + 1)
            } else {
                require(existing == incoming) {
                    "Strength posterior model-state conflict: ${incoming.modelInstanceKey}"
                }
                counts = counts.copy(skipped = counts.skipped + 1)
            }
        }

        data.posteriorLocalStates.forEach { incoming ->
            incoming.toLocalState()
            val existing = strengthPosteriorDao.localStates(incoming.revisionKey)
                .firstOrNull { state -> state.exerciseStableKey == incoming.exerciseStableKey }
            if (existing == null) {
                strengthPosteriorDao.upsertLocalStates(listOf(incoming))
                counts = counts.copy(localStates = counts.localStates + 1)
            } else {
                require(existing == incoming) {
                    "Strength exercise-local state conflict: ${incoming.revisionKey}/${incoming.exerciseStableKey}"
                }
                counts = counts.copy(skipped = counts.skipped + 1)
            }
        }

        val activeRevisions = strengthPosteriorDao.allRevisions()
            .filter { revision -> revision.status == StrengthModelRevisionPolicy.STATUS_ACTIVE }
        require(activeRevisions.size <= 1) { "Backup restores more than one active strength model revision." }
        activeRevisions.singleOrNull()
            ?.takeIf { revision -> revision.revisionKey == StrengthModelRevisionPolicy.CURRENT_REVISION_KEY }
            ?.let { revision ->
                require(StrengthModelRevisionPolicy.isCompatible(revision)) {
                    "Active corrected strength model revision is incompatible."
                }
            }
        return counts
    }

    private suspend fun validateRestoredExerciseReferences() {
        val exerciseKeys = exerciseDao.allExercises().mapTo(mutableSetOf(), Exercise::stableKey)
        val invalidEntries = workoutDao.allEntries().filter { entry ->
            entry.exerciseStableKey.isBlank() || entry.exerciseStableKey !in exerciseKeys
        }
        require(invalidEntries.isEmpty()) {
            "Post-restore workout exercise validation failed: ${invalidEntries.map(WorkoutEntry::id)}"
        }
        val invalidProgramItems = programDao.allProgramItems().filter { item ->
            item.exerciseStableKey.isBlank() || item.exerciseStableKey !in exerciseKeys
        }
        require(invalidProgramItems.isEmpty()) {
            "Post-restore program exercise validation failed: ${invalidProgramItems.map(TrainingProgramItem::id)}"
        }
    }

    private data class PosteriorRestoreCounts(
        val events: Int = 0,
        val history: Int = 0,
        val states: Int = 0,
        val curves: Int = 0,
        val evidence: Int = 0,
        val revisions: Int = 0,
        val localStates: Int = 0,
        val localHistory: Int = 0,
        val proxyHistory: Int = 0,
        val skipped: Int = 0
    )

}

private data class ProgramRestoreCounts(
    val programs: Int,
    val items: Int,
    val sets: Int,
    val tombstones: Int
)

private fun ProgramBackupItem.logicalPosition(): String =
    "$programStableKey|$weekNumber|$dayOfWeek|$orderIndex"

private fun ProgramBackupItemSet.logicalPosition(): String =
    "$programStableKey|$weekNumber|$dayOfWeek|$orderIndex"
