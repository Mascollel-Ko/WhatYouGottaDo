package com.training.trackplanner.data

import androidx.room.withTransaction

internal class BackupRestoreImportService(
    private val db: TrainingDatabase,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val workoutDao: WorkoutDao,
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
        var posteriorCounts = PosteriorRestoreCounts()
        var skipped = 0
        db.withTransaction {
            val seedByStableKey = seedExercisesByStableKey()
            val runtimeMetadataRows = data.runtimeMetadataRows.map { metadata ->
                metadata.copy(stableKey = canonicalImportedStableKey(metadata.stableKey))
            }
            val restoredRuntimeOverrideKeys = ExerciseMetadataOverrideBackupMapper.overrideKeys(runtimeMetadataRows)
            profileFromRows(data.profileRows)?.let { profile ->
                initialUserProfileDao.upsert(profile)
                profileCount = 1
            }
            data.exerciseRows.forEach { row ->
                val normalized = row.copy(stableKey = canonicalImportedStableKey(row.stableKey))
                if (upsertRestoredExercise(normalized, seedByStableKey, restoredRuntimeOverrideKeys)) {
                    exerciseCount += 1
                }
            }
            runtimeMetadataRows.forEach { metadata ->
                runtimeExerciseMetadataDao.upsert(
                    metadata.copy(safeForSeedMutation = false).toEntity()
                )
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
                        canonicalImportedStableKey(first.stableKey),
                        seedByStableKey
                    )
                    val confirmedCount = importedSets.count { row -> row.setConfirmed }
                    val entryId = workoutDao.insertEntry(
                        WorkoutEntry(
                            date = first.date,
                            exerciseId = exercise.id,
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
            if (data.posteriorFormatPresent) {
                posteriorCounts = restorePosteriorRows(data)
                skipped += posteriorCounts.skipped
                appMetaDao.upsert(
                    AppMeta(
                        key = StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY,
                        value = data.posteriorBootstrapMarker
                            ?: "completed|RESTORED_POSTERIOR_BACKUP|${System.currentTimeMillis()}"
                    )
                )
                appMetaDao.upsert(
                    AppMeta(
                        key = StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY,
                        value = "PERSISTED_POSTERIOR_BACKUP|${System.currentTimeMillis()}|events=${posteriorCounts.events}"
                    )
                )
            } else {
                appMetaDao.delete(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY)
                appMetaDao.delete(StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY)
            }
        }
        if (!data.posteriorFormatPresent) {
            check(
                strengthPosteriorCoordinator.bootstrapIfNeeded(
                    StrengthPosteriorUpdateCoordinator.REASON_LEGACY_BACKUP_BOOTSTRAP
                )
            ) { "Legacy backup posterior bootstrap did not complete." }
            appMetaDao.upsert(
                AppMeta(
                    key = StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY,
                    value = "LEGACY_BACKUP_FORWARD_BOOTSTRAP|${System.currentTimeMillis()}"
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
            skippedDuplicateCount = skipped,
            warningCount = data.warningCount
        )
    }

    private suspend fun restorePosteriorRows(data: RecordCsvImportData.Restore): PosteriorRestoreCounts {
        var counts = PosteriorRestoreCounts()
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
            strengthPosteriorDao.eventBySessionKey(incoming.sessionKey)?.let { existing ->
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

        data.posteriorModelStates.forEach { incoming ->
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

        data.curvePosteriors.forEach { incoming ->
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
        return counts
    }

    private data class PosteriorRestoreCounts(
        val events: Int = 0,
        val history: Int = 0,
        val states: Int = 0,
        val curves: Int = 0,
        val evidence: Int = 0,
        val skipped: Int = 0
    )

    private fun canonicalImportedStableKey(stableKey: String): String =
        if (stableKey.trim() == "imported_배드민턴") "ex_ae9ecdbc" else stableKey
}
