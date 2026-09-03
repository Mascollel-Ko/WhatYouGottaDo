package com.training.trackplanner.data

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.VersionedDoubleArrayCodec
import com.training.trackplanner.analysis.strengthperformance.toPosterior

internal class BackupRestoreImportService(
    private val db: TrainingDatabase,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseRoleRelationDao: ExerciseRoleRelationDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val dailyStatusService: DailyStatusService,
    private val smashSpeedDao: SmashSpeedDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val exerciseMetadataUserOverrideDao: ExerciseMetadataUserOverrideDao,
    private val appMetaDao: AppMetaDao,
    private val workoutSourceIdentityProvider: WorkoutSourceIdentityProvider,
    private val strengthPosteriorDao: StrengthPosteriorDao,
    private val strengthPosteriorCoordinator: StrengthPosteriorUpdateCoordinator,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val canonicalMetadataRepository: CanonicalExerciseMetadataRepository,
    private val restorePlanner: BackupRestorePlanner,
    private val seedExercisesByStableKey: () -> Map<String, Exercise>,
    private val profileFromRows: (List<RestoreProfileRow>) -> InitialUserProfile?
) {
    suspend fun importRestoreCsv(data: RecordCsvImportData.Restore): RecordCsvTransferResult {
        val prepared = restorePlanner.prepare(data)
        return importRestorePlan(
            restorePlanner.plan(
                prepared = prepared,
                workoutMode = WorkoutRestoreMode.APPEND_TO_CURRENT,
                exerciseMode = ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES
            )
        )
    }

    suspend fun importRestorePlan(plan: BackupRestorePlan): RecordCsvTransferResult {
        val data = plan.prepared.data
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
            if (
                restorePlanner.currentFingerprint(
                    backupHash = plan.prepared.backupContentSha256,
                    workoutMode = plan.workoutMode,
                    exerciseMode = plan.exerciseMode
                ) != plan.contentFingerprint
            ) {
                throw DataTransferFormatException(
                    DataTransferDiagnosticCodes.RESTORE_PREFLIGHT_STALE,
                    "Restore preflight fingerprint no longer matches the target database."
                )
            }
            val seedByStableKey = seedExercisesByStableKey()
            val isFormat12 = (data.manifest?.formatVersion ?: 0) >= 12
            val runtimeMetadataByKey = data.runtimeMetadataRows.associateBy(RuntimeExerciseMetadata::stableKey)
            val snapshotsByKey = data.metadataSnapshotRows.groupBy(ExerciseMetadataSnapshotRow::stableKey)
            profileFromRows(data.profileRows)?.let { profile ->
                initialUserProfileDao.upsert(profile)
                profileCount = 1
            }
            data.portableAppMetaRows
                .filter { BackupAppMetaPolicy.isSourceOverwriteAllowed(it.key) }
                .forEach { appMetaDao.upsert(it) }
            data.exerciseRows.forEach { row ->
                val snapshots = snapshotsByKey[row.stableKey].orEmpty()
                val existingBefore = exerciseDao.findByStableKey(row.stableKey)
                val canonical = seedByStableKey[row.stableKey]
                val restoredFromBackup = if (isFormat12 && canonical != null) {
                    restoreCurrentBuiltInExercise(
                        canonical = canonical,
                        existing = existingBefore,
                        row = row,
                        applyBackupUserState = plan.exerciseMode ==
                            ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST
                    )
                } else {
                    restoreExercise(row, seedByStableKey, runtimeMetadataByKey[row.stableKey], snapshots)
                }
                val restored = if (
                    plan.exerciseMode == ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES &&
                    existingBefore != null && canonical == null
                ) {
                    val active = existingBefore.isActive || restoredFromBackup.exercise.isActive
                    restoredFromBackup.copy(
                        exercise = restoredFromBackup.exercise.copy(
                            isActive = active,
                            archivedAt = if (active) null else existingBefore.archivedAt,
                            needsReview = existingBefore.needsReview
                        )
                    )
                } else {
                    restoredFromBackup
                }
                val existing = exerciseDao.findByStableKey(restored.exercise.stableKey)
                if (existing == null) exerciseDao.insertExercise(restored.exercise)
                else exerciseDao.updateExercise(restored.exercise)
                runtimeExerciseMetadataDao.upsert(restored.runtimeMetadata.toEntity())
                exerciseCount += 1
                val legacy = LegacyTrainingRoleImportMapper.resolve(row.legacyTrainingRole)
                val trainingRoles = if (isFormat12 && canonical != null) {
                    canonicalMetadataRepository.trainingRoleRelations()
                        .filter { it.exerciseStableKey == row.stableKey }
                        .mapTo(sortedSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode)
                } else {
                    ExerciseMetadataFieldPolicyRegistry.relationValues(
                        snapshots,
                        "relation.trainingRoles"
                    ) ?: (row.trainingRoleCodes + legacy.trainingRoles).map(TrainingRole::name).toSet()
                        .takeIf(Set<String>::isNotEmpty)
                }
                val capabilities = if (isFormat12 && canonical != null) {
                    canonicalMetadataRepository.programSlotCapabilityRelations()
                        .filter { it.exerciseStableKey == row.stableKey }
                        .mapTo(sortedSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode)
                } else {
                    ExerciseMetadataFieldPolicyRegistry.relationValues(
                        snapshots,
                        "relation.programSlotCapabilities"
                    ) ?: (row.programSlotCapabilityCodes + legacy.programSlotCapabilities)
                        .map(ProgramSlotCapability::name).toSet().takeIf(Set<String>::isNotEmpty)
                }
                trainingRoles?.let { restoredRoles ->
                    exerciseRoleRelationDao.deleteTrainingRoles(row.stableKey)
                    exerciseRoleRelationDao.upsertTrainingRoles(restoredRoles.map { role ->
                    ExerciseTrainingRoleRelation(
                        exerciseStableKey = row.stableKey,
                        trainingRoleCode = role,
                        provenance = "BACKUP_RESTORE",
                        reviewStatus = "APPROVED",
                        notes = "Imported normalized relation"
                    )
                    })
                }
                capabilities?.let { restoredCapabilities ->
                    exerciseRoleRelationDao.deleteProgramSlotCapabilities(row.stableKey)
                    exerciseRoleRelationDao.upsertProgramSlotCapabilities(restoredCapabilities.map { capability ->
                    ExerciseProgramSlotCapabilityRelation(
                        exerciseStableKey = row.stableKey,
                        capabilityCode = capability,
                        provenance = "BACKUP_RESTORE",
                        reviewStatus = "APPROVED",
                        notes = "Imported normalized relation"
                    )
                    })
                }
            }
            if (isFormat12) {
                applyMetadataOverrides(data, plan.exerciseMode)
                if (plan.exerciseMode == ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST) {
                    val canonicalKeys = seedByStableKey.keys
                    val represented = plan.prepared.representedExerciseStableKeys
                    exerciseDao.allExercises()
                        .filter { exercise ->
                            exercise.stableKey !in represented && exercise.stableKey !in canonicalKeys && exercise.isActive
                        }
                        .forEach { exercise ->
                            exerciseDao.updateExercise(
                                exercise.copy(isActive = false, archivedAt = exercise.archivedAt ?: System.currentTimeMillis())
                            )
                        }
                }
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
                        jointTendonDiscomfortJointComplexKey =
                            row.jointTendonDiscomfortJointComplexKey,
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
            val currentBeforeRestore = workoutDao.allEntriesWithSets()
            val currentBySource = currentBeforeRestore.mapNotNull { item ->
                item.entry.backupSourceId?.let { source -> source to item }
            }.toMap()
            if (
                plan.workoutMode == WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES &&
                plan.prepared.overlappingDates.isNotEmpty()
            ) {
                val dates = plan.prepared.overlappingDates.toList()
                val replacedEntryIds = currentBeforeRestore
                    .filter { item -> item.entry.date in plan.prepared.overlappingDates }
                    .map { item -> item.entry.id }
                if (replacedEntryIds.isNotEmpty()) {
                    smashSpeedDao.deleteForParentWorkoutEntries(replacedEntryIds)
                }
                workoutDao.deleteSetsOnDates(dates)
                workoutDao.deleteEntriesOnDates(dates)
            }
            val restoredEntryIdsByBackupKey = mutableMapOf<String, Long>()
            val restoredEntryIdsBySource = mutableMapOf<String, Long>()
            plan.prepared.workoutGraphs.forEach { graph ->
                val existing = graph.sourceId?.let(currentBySource::get)
                if (existing != null) {
                    val sameContent = existing.toRestoreGraph().contentToken() == graph.contentToken()
                    val existingWasReplaced = plan.workoutMode == WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES &&
                        existing.entry.date in plan.prepared.overlappingDates
                    if (!existingWasReplaced) {
                        skipped += 1
                        restoredEntryIdsByBackupKey[graph.entryKey] = existing.entry.id
                        graph.sourceId?.let { restoredEntryIdsBySource[it] = existing.entry.id }
                        return@forEach
                    }
                    if (sameContent && existing.entry.date !in plan.prepared.overlappingDates) {
                        skipped += 1
                        return@forEach
                    }
                }
                val first = graph.sets.first()
                val legacyDuplicate = if (graph.sourceId == null) {
                    findLegacyDuplicateRestoreEntry(first, graph.sets)
                } else null
                if (legacyDuplicate != null) {
                    skipped += 1
                    restoredEntryIdsByBackupKey[graph.entryKey] = legacyDuplicate.entry.id
                    return@forEach
                }
                val exercise = requireNotNull(exerciseDao.findByStableKey(graph.stableKey)) {
                    "Restore exercise cannot be resolved after preflight: ${graph.stableKey}"
                }
                val confirmedCount = graph.sets.count(RestoreSetRow::setConfirmed)
                val sourceId = workoutSourceIdentityProvider.sourceIdForImport(graph.sourceId)
                val entryId = workoutDao.insertEntry(
                    WorkoutEntry(
                        date = graph.date,
                        exerciseStableKey = exercise.stableKey,
                        exerciseName = exercise.name,
                        category = graph.category,
                        restSeconds = graph.restSeconds,
                        notes = graph.notes,
                        rpe = graph.rpe,
                        maxReps = graph.maxReps,
                        createdAt = graph.createdAt ?: System.currentTimeMillis(),
                        completedAt = graph.completedAt
                            ?: if (confirmedCount > 0) System.currentTimeMillis() else null,
                        displayOrder = graph.displayOrder ?: first.entryOrder,
                        firstConfirmedAt = graph.firstConfirmedAt,
                        performedAt = graph.performedAt,
                        backupSourceId = sourceId
                    )
                )
                graph.sets.forEachIndexed { index, row ->
                    workoutDao.insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = row.setIndex,
                            reps = row.reps,
                            weightKg = row.weightKg,
                            seconds = row.seconds,
                            confirmed = row.setConfirmed,
                            manualWeight = row.setManualWeight ?: (row.weightKg > 0.0),
                            rpe = row.rpe,
                            restSecondsOverride = row.setRestSecondsOverride
                        )
                    )
                    setCount += 1
                }
                restoredEntryIdsByBackupKey[graph.entryKey] = entryId
                restoredEntryIdsBySource[sourceId] = entryId
                entryCount += 1
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
                    val parentEntryId = when {
                        row.parentWorkoutEntrySourceId != null ->
                            restoredEntryIdsBySource[row.parentWorkoutEntrySourceId]
                                ?: workoutDao.findEntryByBackupSourceId(row.parentWorkoutEntrySourceId)?.id
                        row.parentWorkoutEntryId != null -> restoredEntryIdsByBackupKey[row.parentWorkoutEntryId.toString()]
                        else -> null
                    }
                    require(
                        (row.parentWorkoutEntryId == null && row.parentWorkoutEntrySourceId == null) ||
                            parentEntryId != null
                    ) {
                        "Smash-speed parent workout entry could not be remapped from the backup graph."
                    }
                    smashSpeedDao.upsert(
                        SmashSpeedRecord(
                            date = row.date,
                            speedKmh = row.speedKmh,
                            attemptIndex = row.attemptIndex,
                            source = row.source ?: "external_app",
                            note = row.note,
                            parentWorkoutEntryId = parentEntryId,
                            createdAt = row.createdAt ?: now,
                            updatedAt = row.updatedAt ?: now
                        ).validated()
                    )
                    smashSpeedCount += 1
                }
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

    private fun restoreCurrentBuiltInExercise(
        canonical: Exercise,
        existing: Exercise?,
        row: RestoreExerciseRow,
        applyBackupUserState: Boolean
    ): RestoredExerciseState {
        val currentHistoryOnly = canonical.planningEligibility == "HISTORY_ONLY"
        val active = when {
            currentHistoryOnly -> false
            !applyBackupUserState && existing != null -> existing.isActive || row.isActive
            else -> row.isActive
        }
        val archivedAt = when {
            active -> null
            !applyBackupUserState && existing != null -> existing.archivedAt
            else -> row.archivedAt ?: existing?.archivedAt ?: System.currentTimeMillis()
        }
        val needsReview = if (!applyBackupUserState && existing != null) existing.needsReview else row.needsReview
        val exercise = canonical.copy(
            isActive = active,
            archivedAt = archivedAt,
            isCustom = false,
            needsReview = needsReview,
            planningEligibility = if (currentHistoryOnly) "HISTORY_ONLY" else canonical.planningEligibility
        )
        val runtime = requireNotNull(canonicalRuntimeMetadataCatalog.resolve(canonical)) {
            "Canonical runtime metadata is missing for ${canonical.stableKey}."
        }.copy(
            stableKey = canonical.stableKey,
            exerciseName = canonical.name,
            planningEligibility = if (currentHistoryOnly) "HISTORY_ONLY" else canonical.planningEligibility,
            safeForSeedMutation = false
        )
        return RestoredExerciseState(exercise, runtime)
    }

    private suspend fun applyMetadataOverrides(
        data: RecordCsvImportData.Restore,
        mode: ExerciseListRestoreMode
    ) {
        val rowsByStableKey = data.metadataUserOverrideRows
            .groupBy(ExerciseMetadataUserOverrideEntity::stableKey)
        when (mode) {
            ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES ->
                exerciseMetadataUserOverrideDao.upsertAll(data.metadataUserOverrideRows)
            ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST ->
                data.exerciseRows.map(RestoreExerciseRow::stableKey).distinct().forEach { stableKey ->
                    exerciseMetadataUserOverrideDao.replaceForStableKey(
                        stableKey,
                        rowsByStableKey[stableKey].orEmpty()
                    )
                }
        }
    }

    private suspend fun restoreExercise(
        row: RestoreExerciseRow,
        seedByStableKey: Map<String, Exercise>,
        legacyRuntimeMetadata: RuntimeExerciseMetadata?,
        snapshots: List<ExerciseMetadataSnapshotRow>
    ): RestoredExerciseState {
        val stableKey = row.stableKey.trim()
        require(stableKey.isNotBlank()) { "Restore exercise stableKey must be nonblank." }
        val current = seedByStableKey[stableKey]
        val existing = exerciseDao.findByStableKey(stableKey)
        val fallback = row.toFallbackExercise()
        val baseExercise = current ?: existing ?: fallback
        val canonicalRuntime = current?.let { canonical ->
            requireNotNull(canonicalRuntimeMetadataCatalog.resolve(canonical)) {
                "Canonical runtime metadata is missing for ${canonical.stableKey}."
            }
        } ?: canonicalRuntimeMetadataCatalog.resolve(baseExercise)
        val hasRuntimeSnapshot = snapshots.any {
            it.fieldScope == ExerciseMetadataFieldScope.RUNTIME_METADATA
        }
        val baseRuntime = if (!hasRuntimeSnapshot && legacyRuntimeMetadata != null) {
            legacyRuntimeMetadata
        } else {
            canonicalRuntime ?: RuntimeExerciseMetadataDefaults.forExercise(baseExercise)
        }
        val restored = ExerciseMetadataFieldPolicyRegistry.restore(baseExercise, baseRuntime, snapshots)
        val rowBacked = if (snapshots.isEmpty()) restored.exercise.mergeLegacyRow(row) else restored.exercise
        val historical = current == null && !row.isCustom
        val currentHistoryOnly = current?.planningEligibility == "HISTORY_ONLY"
        val exercise = rowBacked.copy(
            stableKey = stableKey,
            name = current?.name ?: row.name,
            isActive = when {
                historical || currentHistoryOnly -> false
                else -> rowBacked.isActive
            },
            archivedAt = when {
                historical || currentHistoryOnly -> rowBacked.archivedAt ?: System.currentTimeMillis()
                rowBacked.isActive -> null
                else -> rowBacked.archivedAt
            },
            isCustom = if (current != null) false else row.isCustom,
            planningEligibility = when {
                historical || currentHistoryOnly -> "HISTORY_ONLY"
                else -> rowBacked.planningEligibility
            },
            analysisEligibility = if (historical && snapshots.isEmpty()) "NONE" else rowBacked.analysisEligibility,
            needsReview = rowBacked.needsReview || historical
        )
        val runtime = restored.runtimeMetadata.copy(
            stableKey = stableKey,
            exerciseName = exercise.name,
            planningEligibility = if (exercise.planningEligibility == "HISTORY_ONLY") {
                "HISTORY_ONLY"
            } else {
                restored.runtimeMetadata.planningEligibility
            },
            analysisEligibility = if (historical && snapshots.isEmpty()) {
                MetadataTokenField.parse("NONE")
            } else {
                restored.runtimeMetadata.analysisEligibility
            },
            safeForSeedMutation = false,
            appCueProfile = canonicalRuntime?.appCueProfile.orEmpty()
        )
        return RestoredExerciseState(exercise, runtime)
    }

    private suspend fun findLegacyDuplicateRestoreEntry(
        first: RestoreSetRow,
        rows: List<RestoreSetRow>
    ): WorkoutEntryWithSets? = workoutDao.entriesWithSets(first.date).firstOrNull { existing ->
        existing.entry.exerciseStableKey == first.stableKey &&
            existing.entry.category == first.category &&
            existing.entry.restSeconds == first.restSeconds &&
            existing.entry.notes == first.notes &&
            existing.entry.rpe == first.rpe &&
            existing.entry.maxReps == first.maxReps &&
            existing.sets.sortedBy(WorkoutSet::setIndex).matchesRestoreRows(rows)
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

private data class RestoredExerciseState(
    val exercise: Exercise,
    val runtimeMetadata: RuntimeExerciseMetadata
)

private fun RestoreExerciseRow.toFallbackExercise(): Exercise = Exercise(
    stableKey = stableKey,
    name = name,
    category = category.ifBlank { "Historical" },
    detail1 = detail1,
    detail2 = detail2,
    mode = mode,
    description = description,
    defaultRestSeconds = defaultRestSeconds,
    movementPattern = movementPattern,
    movementCategory = movementCategory,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    equipmentTags = equipment,
    forceType = forceType,
    bodyRegion = bodyRegion,
    plane = plane,
    laterality = laterality,
    sportTransferDirect = sportTransferDirect,
    sportTransferSupportive = sportTransferSupportive,
    loadProfile = loadProfile,
    metadataConfidence = metadataConfidence.ifBlank { MetadataConfidence.LOW.name },
    imageAssetName = imageAssetName,
    isActive = isActive,
    archivedAt = if (isActive) null else archivedAt ?: System.currentTimeMillis(),
    isCustom = isCustom,
    needsReview = needsReview
)

private fun Exercise.mergeLegacyRow(row: RestoreExerciseRow): Exercise = copy(
    category = row.category,
    detail1 = row.detail1,
    detail2 = row.detail2,
    mode = row.mode,
    description = row.description,
    defaultRestSeconds = row.defaultRestSeconds,
    movementPattern = row.movementPattern,
    movementCategory = row.movementCategory,
    primaryMuscles = row.primaryMuscles,
    secondaryMuscles = row.secondaryMuscles,
    equipment = row.equipment,
    equipmentTags = row.equipment,
    forceType = row.forceType,
    bodyRegion = row.bodyRegion,
    plane = row.plane,
    laterality = row.laterality,
    sportTransferDirect = row.sportTransferDirect,
    sportTransferSupportive = row.sportTransferSupportive,
    loadProfile = row.loadProfile,
    metadataConfidence = row.metadataConfidence,
    imageAssetName = row.imageAssetName,
    isActive = row.isActive,
    archivedAt = row.archivedAt,
    isCustom = row.isCustom,
    needsReview = row.needsReview
)

private fun List<WorkoutSet>.matchesRestoreRows(rows: List<RestoreSetRow>): Boolean =
    size == rows.size && zip(rows).all { (set, row) ->
        set.confirmed == row.setConfirmed &&
            set.reps == row.reps &&
            kotlin.math.abs(set.weightKg - row.weightKg) < 0.001 &&
            set.seconds == row.seconds &&
            set.rpe == row.rpe &&
            (row.setManualWeight == null || set.manualWeight == row.setManualWeight) &&
            (row.setRestSecondsOverride == null || set.restSecondsOverride == row.setRestSecondsOverride)
    }

private fun ProgramBackupItem.logicalPosition(): String =
    "$programStableKey|$weekNumber|$dayOfWeek|$orderIndex"

private fun ProgramBackupItemSet.logicalPosition(): String =
    "$programStableKey|$weekNumber|$dayOfWeek|$orderIndex"
