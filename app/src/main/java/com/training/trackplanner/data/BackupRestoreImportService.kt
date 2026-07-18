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
            skippedDuplicateCount = skipped,
            warningCount = data.warningCount
        )
    }

    private fun canonicalImportedStableKey(stableKey: String): String =
        if (stableKey.trim() == "imported_배드민턴") "ex_ae9ecdbc" else stableKey
}
