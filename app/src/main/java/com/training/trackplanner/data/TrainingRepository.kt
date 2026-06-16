package com.training.trackplanner.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.training.trackplanner.analysis.badminton.BadmintonTransferAnalysisEngine
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.core.AnalysisInputCollector
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.engine.AnalysisEngineV3
import com.training.trackplanner.analysis.readiness.TodayReadinessEngine
import com.training.trackplanner.analysis.readiness.TodayReadinessEngineInput
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.trends.PerformanceTrendEngine
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ProgramApplyMode {
    Append,
    Overwrite
}

enum class CalendarConflictMode {
    Append,
    Overwrite
}

data class ProgramApplyConflictSummary(
    val affectedDateCount: Int = 0,
    val existingEntryCount: Int = 0,
    val existingConfirmedSetCount: Int = 0,
    val startDate: String = "",
    val endDate: String = "",
    val newPlannedEntryCount: Int = 0
) {
    val hasExistingEntries: Boolean
        get() = existingEntryCount > 0
}

data class ExerciseDeleteResult(
    val deleted: Boolean,
    val referenced: Boolean
)

class TrainingRepository(
    private val db: TrainingDatabase,
    private val context: Context
) {
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()
    private val programDao = db.programDao()
    private val dailyMetricDao = db.dailyMetricDao()
    private val appMetaDao = db.appMetaDao()
    private val initialUserProfileDao = db.initialUserProfileDao()

    val exercises: Flow<List<Exercise>> = exerciseDao.observeExercises()
    val programs: Flow<List<TrainingProgram>> = programDao.observePrograms()
    val analysisStats: Flow<AnalysisStats> = workoutDao.observeAnalysisStats()
    val initialUserProfile: Flow<InitialUserProfile?> = initialUserProfileDao.observeProfile()

    suspend fun todayReadinessSummary(): TodayReadinessSummary = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = exerciseDao.allExercises(),
                entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
                dailyMetrics = dailyMetricDao.metricsUntil(todayString),
                initialProfile = initialUserProfileDao.profile()
            )
        )
    }

    suspend fun performanceTrendSummary(): PerformanceTrendSummary = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        PerformanceTrendEngine().analyze(
            today = today,
            exercises = exerciseDao.allExercises(),
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        )
    }

    suspend fun badmintonTransferSummary(
        readinessSummary: TodayReadinessSummary? = null
    ): BadmintonTransferSummary = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = exerciseDao.allExercises(),
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            readinessSummary = readinessSummary
        )
    }

    fun entriesForDate(date: String): Flow<List<WorkoutEntryWithSets>> =
        workoutDao.observeEntriesWithSets(date)

    suspend fun exportRecordsBackup(uri: Uri): RecordCsvTransferResult = withContext(Dispatchers.IO) {
        val entries = workoutDao.allEntriesWithSets()
        val metrics = dailyMetricDao.allMetrics()
        val exercises = exerciseDao.allExercises()
        val profile = initialUserProfileDao.profile()
        val csv = RecordCsvBackupRestore.buildRestoreCsv(entries, metrics, exercises, profile)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(csv)
        } ?: error("백업 파일을 열 수 없습니다.")
        RecordCsvTransferResult(
            format = "restore",
            exerciseCount = exercises.size,
            dailyMetricCount = metrics.size,
            profileCount = if (profile != null) 1 else 0,
            entryCount = entries.size,
            setCount = entries.sumOf { item -> item.sets.size }
        )
    }

    suspend fun importRecordsBackup(uri: Uri): RecordCsvTransferResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.readText()
        } ?: error("복원 파일을 열 수 없습니다.")
        when (val data = RecordCsvBackupRestore.parse(text)) {
            is RecordCsvImportData.Restore -> importRestoreCsv(data)
            is RecordCsvImportData.DailyTimeseries -> importDailyTimeseriesCsv(data)
        }
    }

    fun entryCount(date: String): Flow<Int> =
        workoutDao.observeEntryCount(date)

    fun plannedSetCount(date: String): Flow<Int> =
        workoutDao.observePlannedSetCount(date)

    fun confirmedSetCount(date: String): Flow<Int> =
        workoutDao.observeConfirmedSetCount(date)

    fun dailySummaries(startDate: String, endDate: String): Flow<List<DailyRecordSummary>> =
        workoutDao.observeDailySummariesBetween(startDate, endDate)

    fun programItems(programId: Long): Flow<List<TrainingProgramItem>> =
        programDao.observeItems(programId)

    fun metricForDate(date: String): Flow<DailyMetric?> =
        dailyMetricDao.observeMetric(date)

    suspend fun saveInitialUserProfile(profile: InitialUserProfile) = withContext(Dispatchers.IO) {
        val existing = initialUserProfileDao.profile()
        initialUserProfileDao.upsert(
            profile.copy(
                id = 1,
                createdAt = existing?.createdAt ?: profile.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val exerciseSeedVersion = appMetaDao.intValue(META_EXERCISE_SEED_VERSION)
        val programSeedVersion = appMetaDao.intValue(META_PROGRAM_SEED_VERSION)

        if (exerciseSeedVersion < EXERCISE_SEED_VERSION || exerciseDao.countExercises() == 0) {
            upsertSeedExercises()
            appMetaDao.upsert(
                AppMeta(
                    key = META_EXERCISE_SEED_VERSION,
                    value = EXERCISE_SEED_VERSION.toString()
                )
            )
        }
        refreshExerciseAnalysisMetadata()

        if (programSeedVersion < PROGRAM_SEED_VERSION) {
            seedMissingPrograms()
            appMetaDao.upsert(
                AppMeta(
                    key = META_PROGRAM_SEED_VERSION,
                    value = PROGRAM_SEED_VERSION.toString()
                )
            )
        }

        logDebugSummary()
    }

    private suspend fun upsertSeedExercises() {
        SeedData.exercises(context).forEach { seed ->
            val existing = exerciseDao.findByStableKey(seed.stableKey)
                ?: exerciseDao.findByName(seed.name)
            if (existing == null) {
                exerciseDao.insertExercise(seed)
            } else if (!existing.isCustom) {
                exerciseDao.updateExercise(
                    seed.copy(
                        id = existing.id,
                        stableKey = existing.stableKey,
                        imageAssetName = seed.imageAssetName.ifBlank { existing.imageAssetName },
                        isActive = existing.isActive,
                        archivedAt = existing.archivedAt,
                        isCustom = existing.isCustom,
                        needsReview = existing.needsReview || seed.needsReview
                    )
                )
            }
        }
    }

    suspend fun setExerciseActive(exerciseId: Long, active: Boolean) = withContext(Dispatchers.IO) {
        val exercise = exerciseDao.findById(exerciseId) ?: return@withContext
        exerciseDao.updateExercise(
            exercise.copy(
                isActive = active,
                archivedAt = if (active) null else System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteExerciseIfUnused(exerciseId: Long): ExerciseDeleteResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            val exercise = exerciseDao.findById(exerciseId) ?: return@withTransaction ExerciseDeleteResult(
                deleted = false,
                referenced = false
            )
            val referenced = workoutDao.countEntriesForExercise(exerciseId) > 0 ||
                programDao.countProgramItemsForExercise(exerciseId) > 0
            if (referenced || !exercise.isCustom) {
                return@withTransaction ExerciseDeleteResult(deleted = false, referenced = true)
            }
            exerciseDao.deleteExercise(exercise)
            ExerciseDeleteResult(deleted = true, referenced = false)
        }
    }

    suspend fun addWorkoutEntry(date: String, exerciseId: Long) = withContext(Dispatchers.IO) {
        val exercise = exerciseDao.findById(exerciseId) ?: return@withContext
        val entryId = workoutDao.insertEntry(
            WorkoutEntry(
                date = date,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = exercise.defaultRestSeconds
            )
        )
        workoutDao.insertSet(defaultSet(entryId, 1, exercise))
    }

    suspend fun updateWorkoutEntry(entry: WorkoutEntry) = withContext(Dispatchers.IO) {
        workoutDao.updateEntry(entry)
        refreshEntryCompletion(entry.id)
    }

    suspend fun addSet(entry: WorkoutEntry) = withContext(Dispatchers.IO) {
        val currentSets = workoutDao.setsForEntry(entry.id)
        val nextIndex = currentSets.size + 1
        val nextSet = currentSets.lastOrNull()
            ?.copy(
                id = 0,
                setIndex = nextIndex,
                confirmed = false,
                rpe = null,
                restSecondsOverride = null
            )
            ?: defaultSet(entry.id, nextIndex, exerciseDao.findById(entry.exerciseId))
        workoutDao.insertSet(nextSet)
        refreshEntryCompletion(entry.id)
    }

    suspend fun updateSet(set: WorkoutSet) = withContext(Dispatchers.IO) {
        workoutDao.updateSet(set)
        refreshEntryCompletion(set.entryId)
    }

    suspend fun deleteSet(set: WorkoutSet): Boolean = withContext(Dispatchers.IO) {
        if (workoutDao.setCount(set.entryId) <= 1) return@withContext false
        workoutDao.deleteSet(set)
        workoutDao.setsForEntry(set.entryId).forEachIndexed { index, remainingSet ->
            workoutDao.updateSetIndex(remainingSet.id, index + 1)
        }
        refreshEntryCompletion(set.entryId)
        true
    }

    suspend fun createProgram() = withContext(Dispatchers.IO) {
        programDao.insertProgram(
            TrainingProgram(
                name = "새 프로그램",
                durationDays = 28
            )
        )
    }

    suspend fun generateProgramSkeleton(request: ProgramSkeletonRequest): GeneratedProgramSkeleton =
        withContext(Dispatchers.IO) {
            ProgramSkeletonGenerator().generate(
                request = request,
                exercises = exerciseDao.allExercises(),
                history = workoutDao.allEntriesWithSets()
            )
        }

    suspend fun saveGeneratedProgram(
        existingProgramId: Long?,
        skeleton: GeneratedProgramSkeleton
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val request = skeleton.request
            val program = TrainingProgram(
                id = existingProgramId ?: 0,
                name = skeleton.suggestedName.ifBlank { request.name.ifBlank { "새 프로그램" } },
                durationDays = skeleton.durationDays,
                createdAt = existingProgramId?.let { programDao.findProgram(it)?.createdAt } ?: now,
                goal = request.goal.name,
                weeklyTrainingDays = request.weeklyTrainingDays,
                sessionMinutes = request.sessionMinutes,
                availableEquipment = request.availableEquipment.joinToString("|"),
                excludedExerciseText = request.excludedExerciseText,
                badmintonTransferRatio = request.badmintonTransferRatio,
                sportStrengthRatio = request.sportStrengthRatio,
                periodizationType = skeleton.periodizationType.name,
                updatedAt = now
            )
            val programId = if (existingProgramId != null && programDao.findProgram(existingProgramId) != null) {
                programDao.updateProgram(program)
                programDao.deleteProgramItems(existingProgramId)
                existingProgramId
            } else {
                programDao.insertProgram(program)
            }
            programDao.insertProgramItems(
                skeleton.items.map { item ->
                    TrainingProgramItem(
                        programId = programId,
                        weekNumber = item.weekNumber,
                        dayOfWeek = item.dayOfWeek,
                        orderIndex = item.orderIndex,
                        exerciseId = item.exerciseId,
                        exerciseName = item.exerciseName,
                        category = item.category,
                        restSeconds = item.restSeconds,
                        prescription = item.prescription,
                        setCount = item.setCount.coerceAtLeast(1),
                        reps = item.reps,
                        weightKg = item.weightKg,
                        seconds = item.seconds
                    )
                }
            )
            programId
        }
    }

    suspend fun deleteProgram(programId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            programDao.deleteProgramItems(programId)
            programDao.deleteProgram(programId)
        }
    }

    suspend fun addExerciseToProgram(
        programId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        exerciseId: Long
    ) = withContext(Dispatchers.IO) {
        val exercise = exerciseDao.findById(exerciseId) ?: return@withContext
        val nextOrder = (programDao.itemsForProgramDay(programId, weekNumber, dayOfWeek)
            .maxOfOrNull { it.orderIndex } ?: 0) + 1
        programDao.insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = weekNumber,
                dayOfWeek = dayOfWeek,
                orderIndex = nextOrder,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = exercise.defaultRestSeconds,
                prescription = "",
                setCount = 1,
                reps = 0,
                weightKg = 0.0,
                seconds = if (exercise.mode.contains("시간") || exercise.category in timedCategories) 30 else 0
            )
        )
    }

    suspend fun updateProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programDao.updateProgramItem(item)
    }

    suspend fun deleteProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programDao.deleteProgramItem(item)
        reindexProgramDay(item.programId, item.weekNumber, item.dayOfWeek)
    }

    suspend fun programHasDateConflicts(programId: Long, startDate: String): Boolean =
        withContext(Dispatchers.IO) {
            programApplyConflictSummary(programId, startDate).hasExistingEntries
        }

    suspend fun programApplyConflictSummary(
        programId: Long,
        startDate: String
    ): ProgramApplyConflictSummary = withContext(Dispatchers.IO) {
        val program = programDao.findProgram(programId)
        val programItems = programDao.itemsForProgram(programId)
        val range = program?.dateRangeFor(startDate)
        if (program == null || programItems.isEmpty() || range == null) {
            ProgramApplyConflictSummary()
        } else {
            ProgramApplyConflictSummary(
                affectedDateCount = program.durationDays,
                existingEntryCount = workoutDao.countPlannedOnlyEntriesBetween(range.first, range.second),
                existingConfirmedSetCount = workoutDao.countConfirmedSetsBetween(range.first, range.second),
                startDate = range.first,
                endDate = range.second,
                newPlannedEntryCount = programItems.size
            )
        }
    }

    suspend fun applyProgramToDates(
        programId: Long,
        startDate: String,
        mode: ProgramApplyMode
    ) = withContext(Dispatchers.IO) {
        val program = programDao.findProgram(programId) ?: return@withContext
        val items = programDao.itemsForProgram(program.id)
        if (items.isEmpty()) return@withContext
        val range = program.dateRangeFor(startDate) ?: return@withContext
        db.withTransaction {
            if (mode == ProgramApplyMode.Overwrite) {
                workoutDao.deletePlannedOnlySetsBetween(range.first, range.second)
                workoutDao.deletePlannedOnlyEntriesBetween(range.first, range.second)
            }

            val now = System.currentTimeMillis()
            items.forEachIndexed { index, item ->
                val entryId = workoutDao.insertEntry(
                    WorkoutEntry(
                        date = dateForProgramItem(startDate, item),
                        exerciseId = item.exerciseId,
                        exerciseName = item.exerciseName,
                        category = item.category,
                        restSeconds = item.restSeconds,
                        notes = noteFromPrescription(item.prescription),
                        createdAt = now + index
                    )
                )
                repeat(item.setCount.coerceAtLeast(1)) { setIndex ->
                    workoutDao.insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = setIndex + 1,
                            reps = item.reps,
                            weightKg = item.weightKg,
                            seconds = item.seconds,
                            confirmed = false,
                            manualWeight = item.weightKg > 0.0
                        )
                    )
                }
            }
        }
    }

    suspend fun calendarConflictSummary(dates: List<String>): CalendarConflictSummary =
        withContext(Dispatchers.IO) {
            if (dates.isEmpty()) {
                CalendarConflictSummary()
            } else {
                CalendarConflictSummary(
                    affectedDateCount = dates.size,
                    existingDateCount = workoutDao.countDatesWithEntries(dates),
                    existingEntryCount = workoutDao.countEntriesOnDates(dates),
                    existingSetCount = workoutDao.countSetsOnDates(dates),
                    existingConfirmedSetCount = workoutDao.countConfirmedSetsOnDates(dates)
                )
            }
        }

    suspend fun deleteDate(date: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            workoutDao.deleteSetsOnDates(listOf(date))
            workoutDao.deleteEntriesOnDates(listOf(date))
        }
    }

    suspend fun deleteDateRange(
        startDate: String,
        endDate: String,
        includeConfirmed: Boolean
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val dates = dateRange(startDate, endDate)
            if (dates.isEmpty()) return@withTransaction
            if (includeConfirmed) {
                workoutDao.deleteSetsOnDates(dates)
                workoutDao.deleteEntriesOnDates(dates)
            } else {
                val entries = dates.flatMap { date -> workoutDao.entriesWithSets(date) }
                entries.forEach { entryWithSets ->
                    entryWithSets.sets
                        .filter { set -> !set.confirmed }
                        .forEach { set -> workoutDao.deleteSet(set) }

                    val remainingSets = workoutDao.setsForEntry(entryWithSets.entry.id)
                        .sortedBy { set -> set.setIndex }
                    if (remainingSets.isEmpty()) {
                        workoutDao.deleteEntryById(entryWithSets.entry.id)
                    } else {
                        remainingSets.forEachIndexed { index, set ->
                            val nextIndex = index + 1
                            if (set.setIndex != nextIndex) {
                                workoutDao.updateSetIndex(set.id, nextIndex)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun copyDate(
        sourceDate: String,
        targetDate: String,
        keepConfirmed: Boolean,
        conflictMode: CalendarConflictMode
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val sourceEntries = workoutDao.entriesWithSets(sourceDate)
            if (sourceEntries.isEmpty()) return@withTransaction
            if (conflictMode == CalendarConflictMode.Overwrite) {
                workoutDao.deleteSetsOnDates(listOf(targetDate))
                workoutDao.deleteEntriesOnDates(listOf(targetDate))
            }
            copyEntriesToDate(
                sourceEntries = sourceEntries,
                targetDate = targetDate,
                keepConfirmed = keepConfirmed,
                baseCreatedAt = nextCreatedAt()
            )
        }
    }

    suspend fun moveDate(
        sourceDate: String,
        targetDate: String,
        conflictMode: CalendarConflictMode
    ) = withContext(Dispatchers.IO) {
        if (sourceDate == targetDate) return@withContext
        db.withTransaction {
            val sourceEntries = workoutDao.entriesWithSets(sourceDate)
            if (sourceEntries.isEmpty()) return@withTransaction
            if (conflictMode == CalendarConflictMode.Overwrite) {
                workoutDao.deleteSetsOnDates(listOf(targetDate))
                workoutDao.deleteEntriesOnDates(listOf(targetDate))
            }
            copyEntriesToDate(
                sourceEntries = sourceEntries,
                targetDate = targetDate,
                keepConfirmed = true,
                baseCreatedAt = nextCreatedAt()
            )
            workoutDao.deleteSetsOnDates(listOf(sourceDate))
            workoutDao.deleteEntriesOnDates(listOf(sourceDate))
        }
    }

    suspend fun copyDateRangeAsPlan(
        sourceStart: String,
        sourceEnd: String,
        targetStart: String,
        conflictMode: CalendarConflictMode
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val sourceDates = dateRange(sourceStart, sourceEnd)
            val sourceEntriesByDate = sourceDates.map { sourceDate ->
                sourceDate to workoutDao.entriesWithSets(sourceDate)
            }
            val targetStartDate = LocalDate.parse(targetStart, DateTimeFormatter.ISO_LOCAL_DATE)
            val targetDates = sourceDates.mapIndexed { index, _ ->
                targetStartDate.plusDays(index.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
            if (conflictMode == CalendarConflictMode.Overwrite && targetDates.isNotEmpty()) {
                workoutDao.deleteSetsOnDates(targetDates)
                workoutDao.deleteEntriesOnDates(targetDates)
            }
            var createdAt = nextCreatedAt()
            sourceEntriesByDate.forEachIndexed { index, (_, entries) ->
                if (entries.isNotEmpty()) {
                    copyEntriesToDate(
                        sourceEntries = entries,
                        targetDate = targetDates[index],
                        keepConfirmed = false,
                        baseCreatedAt = createdAt
                    )
                    createdAt += entries.size
                }
            }
        }
    }

    suspend fun saveDailyMetric(
        date: String,
        sleepHours: Double?,
        bodyWeightKg: Double?
    ) = withContext(Dispatchers.IO) {
        dailyMetricDao.upsert(
            DailyMetric(
                date = date,
                sleepHours = sleepHours,
                bodyWeightKg = bodyWeightKg,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun importRestoreCsv(data: RecordCsvImportData.Restore): RecordCsvTransferResult {
        var exerciseCount = 0
        var dailyCount = 0
        var profileCount = 0
        var entryCount = 0
        var setCount = 0
        var skipped = 0
        db.withTransaction {
            data.profileRows.toInitialUserProfile()?.let { profile ->
                initialUserProfileDao.upsert(profile)
                profileCount = 1
            }
            data.exerciseRows.forEach { row ->
                if (upsertRestoredExercise(row)) {
                    exerciseCount += 1
                }
            }
            data.dailyRows.forEach { row ->
                if (row.sleepHours != null || row.bodyWeightKg != null) {
                    dailyMetricDao.upsert(
                        DailyMetric(
                            date = row.date,
                            sleepHours = row.sleepHours,
                            bodyWeightKg = row.bodyWeightKg
                        )
                    )
                    dailyCount += 1
                }
            }
            data.setRows
                .filter { row -> row.sleepHours != null || row.bodyWeightKg != null }
                .distinctBy { row -> row.date }
                .forEach { row ->
                    dailyMetricDao.upsert(
                        DailyMetric(
                            date = row.date,
                            sleepHours = row.sleepHours,
                            bodyWeightKg = row.bodyWeightKg
                        )
                    )
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
                    val exercise = findOrCreateImportedExercise(first.exerciseName, first.category)
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
            profileCount = profileCount,
            entryCount = entryCount,
            setCount = setCount,
            skippedDuplicateCount = skipped,
            warningCount = data.warningCount
        )
    }

    private suspend fun importDailyTimeseriesCsv(
        data: RecordCsvImportData.DailyTimeseries
    ): RecordCsvTransferResult {
        var dailyCount = 0
        var entryCount = 0
        var setCount = 0
        var skipped = 0
        db.withTransaction {
            data.rows.forEach { row ->
                if (row.sleepHours != null || row.bodyWeightKg != null) {
                    dailyMetricDao.upsert(
                        DailyMetric(
                            date = row.date,
                            sleepHours = row.sleepHours,
                            bodyWeightKg = row.bodyWeightKg
                        )
                    )
                    dailyCount += 1
                }

                val existing = workoutDao.entriesWithSets(row.date)
                if (existing.any { item -> item.entry.notes == TIMESERIES_IMPORT_NOTE }) {
                    skipped += 1
                    return@forEach
                }

                val confirmedCategoryCounts = row.confirmedCategoryCounts()
                if (confirmedCategoryCounts.isEmpty() && row.plannedEntries <= 0) return@forEach

                val confirmedTotal = confirmedCategoryCounts.values.sum().coerceAtLeast(1)
                confirmedCategoryCounts.forEach { (category, categoryCount) ->
                    val ratio = categoryCount.toDouble() / confirmedTotal
                    val reps = (row.totalReps * ratio).toInt().coerceAtLeast(0)
                    val tonnage = row.totalTonnageKg * ratio
                    val seconds = (row.totalSeconds * ratio).toInt().coerceAtLeast(0)
                    val setsForCategory = (row.totalSets * ratio).toInt().coerceAtLeast(1)
                    val weight = if (reps > 0 && tonnage > 0.0) tonnage / reps else 0.0
                    val exercise = findOrCreateImportedExercise(
                        name = "CSV 복원 $category",
                        category = category,
                        forceFatigueOnly = true
                    )
                    val entryId = workoutDao.insertEntry(
                        WorkoutEntry(
                            date = row.date,
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            category = category,
                            notes = TIMESERIES_IMPORT_NOTE,
                            completedAt = System.currentTimeMillis()
                        )
                    )
                    workoutDao.insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = 1,
                            reps = reps,
                            weightKg = weight,
                            seconds = seconds,
                            confirmed = true,
                            manualWeight = weight > 0.0
                        )
                    )
                    entryCount += 1
                    setCount += 1
                    repeat((setsForCategory - 1).coerceAtLeast(0)) { index ->
                        workoutDao.insertSet(
                            WorkoutSet(
                                entryId = entryId,
                                setIndex = index + 2,
                                reps = 0,
                                weightKg = 0.0,
                                seconds = 0,
                                confirmed = true,
                                manualWeight = false
                            )
                        )
                        setCount += 1
                    }
                }

                if (row.plannedEntries > 0) {
                    val exercise = findOrCreateImportedExercise(
                        name = "CSV 복원 계획",
                        category = "근력운동",
                        forceFatigueOnly = true
                    )
                    val entryId = workoutDao.insertEntry(
                        WorkoutEntry(
                            date = row.date,
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            category = exercise.category,
                            notes = TIMESERIES_IMPORT_NOTE
                        )
                    )
                    workoutDao.insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = 1,
                            confirmed = false
                        )
                    )
                    entryCount += 1
                    setCount += 1
                }
            }
        }
        return RecordCsvTransferResult(
            format = "daily_timeseries",
            dailyMetricCount = dailyCount,
            entryCount = entryCount,
            setCount = setCount,
            skippedDuplicateCount = skipped,
            warningCount = data.warningCount
        )
    }

    private suspend fun hasDuplicateRestoreEntry(
        first: RestoreSetRow,
        rows: List<RestoreSetRow>
    ): Boolean =
        workoutDao.entriesWithSets(first.date).any { existing ->
            existing.entry.exerciseName == first.exerciseName &&
                existing.entry.category == first.category &&
                existing.entry.restSeconds == first.restSeconds &&
                existing.entry.notes == first.notes &&
                existing.entry.rpe == first.rpe &&
                existing.entry.maxReps == first.maxReps &&
            existing.sets.sortedBy { set -> set.setIndex }.matchesRestoreRows(rows)
        }

    private suspend fun upsertRestoredExercise(row: RestoreExerciseRow): Boolean {
        val stableKey = row.stableKey.ifBlank { "imported_${row.name.stableToken()}" }
        val category = row.category.ifBlank { "근력운동" }
        val restored = ExerciseMetadataMapper.applyLegacyMetadata(
            Exercise(
                name = row.name,
                category = category,
                detail1 = row.detail1,
                detail2 = row.detail2,
                mode = row.mode,
                description = row.description,
                defaultRestSeconds = row.defaultRestSeconds,
                stableKey = stableKey,
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
                trainingRole = row.trainingRole,
                sportTransferDirect = row.sportTransferDirect,
                sportTransferSupportive = row.sportTransferSupportive,
                loadProfile = row.loadProfile,
                metadataConfidence = row.metadataConfidence.ifBlank { MetadataConfidence.LOW.name }
            )
        ).copy(
            imageAssetName = row.imageAssetName,
            isActive = row.isActive,
            archivedAt = if (row.isActive) null else System.currentTimeMillis(),
            isCustom = row.isCustom,
            needsReview = row.needsReview
        )
        val existing = exerciseDao.findByStableKey(stableKey) ?: exerciseDao.findByName(row.name)
        if (existing == null) {
            exerciseDao.insertExercise(restored)
        } else {
            exerciseDao.updateExercise(
                restored.copy(
                    id = existing.id,
                    stableKey = existing.stableKey,
                    imageAssetName = restored.imageAssetName.ifBlank { existing.imageAssetName },
                    isCustom = existing.isCustom || restored.isCustom,
                    needsReview = existing.needsReview || restored.needsReview
                )
            )
        }
        return true
    }

    private fun List<RestoreProfileRow>.toInitialUserProfile(): InitialUserProfile? {
        if (isEmpty()) return null
        val values = associate { row -> row.key to row.value }
        val sex = normalizeProfileSex(values["sex"].orEmpty().ifBlank { values["gender"].orEmpty() })
        val birthYear = values["birthYear"]?.toIntOrNull()
            ?: values["birthYearOrAgeRange"]?.trim()?.toIntOrNull()?.takeIf { it in 1900..2100 }
        val breakCategory = values["trainingBreakCategory"].orEmpty().ifBlank {
            breakWeeksToProfileCategory(values["breakWeeks"]?.toIntOrNull())
        }
        val breakReason = values["trainingBreakReason"].orEmpty().ifBlank {
            if (values["breakDueToPain"].toCsvBoolean()) "PAIN_OR_INJURY" else "NONE"
        }
        return InitialUserProfile(
            id = 1,
            bodyWeightKg = values["bodyWeightKg"]?.toDoubleOrNull(),
            heightCm = values["heightCm"]?.toDoubleOrNull(),
            birthYearOrAgeRange = values["birthYearOrAgeRange"].orEmpty(),
            gender = values["gender"].orEmpty(),
            birthYear = birthYear,
            sex = sex,
            strengthSessionsPerWeek = values["strengthSessionsPerWeek"]?.toDoubleOrNull(),
            strengthMinutesPerSession = values["strengthMinutesPerSession"]?.toIntOrNull(),
            strengthAverageRpe = values["strengthAverageRpe"]?.toDoubleOrNull(),
            badmintonSessionsPerWeek = values["badmintonSessionsPerWeek"]?.toDoubleOrNull(),
            badmintonMinutesPerSession = values["badmintonMinutesPerSession"]?.toIntOrNull(),
            badmintonAverageRpe = values["badmintonAverageRpe"]?.toDoubleOrNull(),
            strengthTrainingAge = values["strengthTrainingAge"].orEmpty(),
            badmintonTrainingAge = values["badmintonTrainingAge"].orEmpty(),
            strengthTrainingYears = values["strengthTrainingYears"]?.toDoubleOrNull()
                ?: values["strengthTrainingAge"].parseProfileYears(),
            badmintonTrainingYears = values["badmintonTrainingYears"]?.toDoubleOrNull()
                ?: values["badmintonTrainingAge"].parseProfileYears(),
            hadRecentTrainingBreak = values["hadRecentTrainingBreak"].toCsvBoolean(),
            breakWeeks = values["breakWeeks"]?.toIntOrNull(),
            breakDueToPain = values["breakDueToPain"].toCsvBoolean(),
            trainingBreakCategory = breakCategory,
            trainingBreakReason = breakReason,
            squatLevel = values["squatLevel"].orEmpty(),
            deadliftLevel = values["deadliftLevel"].orEmpty(),
            benchPressLevel = values["benchPressLevel"].orEmpty(),
            pullUpLevel = values["pullUpLevel"].orEmpty(),
            squatKg = values["squatKg"]?.toDoubleOrNull(),
            deadliftKg = values["deadliftKg"]?.toDoubleOrNull(),
            benchPressKg = values["benchPressKg"]?.toDoubleOrNull(),
            pullUpMaxReps = values["pullUpMaxReps"]?.toIntOrNull(),
            pullUpAddedWeightKg = values["pullUpAddedWeightKg"]?.toDoubleOrNull(),
            typicalSleepHours = values["typicalSleepHours"]?.toDoubleOrNull(),
            usualSleepHours = values["usualSleepHours"]?.toDoubleOrNull()
                ?: values["typicalSleepHours"]?.toDoubleOrNull(),
            sleepQuality = values["sleepQuality"]?.toIntOrNull(),
            currentFatigue = values["currentFatigue"]?.toIntOrNull(),
            currentSoreness = values["currentSoreness"]?.toIntOrNull(),
            currentStress = values["currentStress"]?.toIntOrNull(),
            currentMood = values["currentMood"]?.toIntOrNull(),
            currentCondition = values["currentCondition"]?.toIntOrNull()
                ?: values["currentMood"]?.toIntOrNull(),
            painAreas = values["painAreas"].orEmpty(),
            painAreaTags = values["painAreaTags"].orEmpty().ifBlank {
                if (values["painAreas"].isNullOrBlank()) "NONE" else "OTHER"
            },
            avoidedMovements = values["avoidedMovements"].orEmpty(),
            avoidMovementTags = values["avoidMovementTags"].orEmpty().ifBlank {
                if (values["avoidedMovements"].isNullOrBlank()) "NONE" else "OTHER"
            },
            goals = values["goals"].orEmpty(),
            primaryGoal = values["primaryGoal"].orEmpty().ifBlank { legacyGoalToKey(values["goals"].orEmpty()) },
            secondaryGoalTags = values["secondaryGoalTags"].orEmpty(),
            freeNote = values["freeNote"].orEmpty(),
            createdAt = values["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
            updatedAt = values["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }

    private fun String?.toCsvBoolean(): Boolean =
        when (this?.trim()?.lowercase(Locale.US)) {
            "1", "true", "yes", "y" -> true
            else -> false
        }

    private fun normalizeProfileSex(value: String): String =
        when (value.trim().lowercase(Locale.US)) {
            "male", "m", "남", "남성" -> "MALE"
            "female", "f", "여", "여성" -> "FEMALE"
            else -> "UNSPECIFIED"
        }

    private fun String?.parseProfileYears(): Double? {
        val value = this?.trim()?.lowercase(Locale.US).orEmpty()
        if (value == "반년") return 0.5
        return Regex("""\d+(\.\d+)?""").find(value)?.value?.toDoubleOrNull()
    }

    private fun breakWeeksToProfileCategory(weeks: Int?): String =
        when {
            weeks == null || weeks <= 0 -> "NONE"
            weeks <= 1 -> "LESS_THAN_1_WEEK"
            weeks <= 2 -> "ONE_TO_TWO_WEEKS"
            weeks <= 4 -> "THREE_TO_FOUR_WEEKS"
            weeks <= 8 -> "FIVE_TO_EIGHT_WEEKS"
            else -> "MORE_THAN_EIGHT_WEEKS"
        }

    private fun legacyGoalToKey(value: String): String {
        val goal = value.lowercase(Locale.US)
        return when {
            "배드민턴" in goal -> "BADMINTON_PERFORMANCE"
            "근력" in goal && "유지" in goal -> "STRENGTH_MAINTENANCE"
            "근력" in goal -> "STRENGTH_GAIN"
            "근비대" in goal || "체형" in goal -> "HYPERTROPHY_PHYSIQUE"
            "회복" in goal || "부상" in goal -> "RECOVERY_INJURY_PREVENTION"
            "체중" in goal -> "WEIGHT_MANAGEMENT"
            else -> "MIXED"
        }
    }

    private fun List<WorkoutSet>.matchesRestoreRows(rows: List<RestoreSetRow>): Boolean {
        if (size != rows.size) return false
        return zip(rows).all { (set, row) ->
            set.confirmed == row.setConfirmed &&
                set.reps == row.reps &&
                kotlin.math.abs(set.weightKg - row.weightKg) < 0.001 &&
                set.seconds == row.seconds &&
                set.rpe == row.rpe
        }
    }

    private suspend fun findOrCreateImportedExercise(
        name: String,
        category: String,
        forceFatigueOnly: Boolean = false
    ): Exercise {
        exerciseDao.findByName(name)?.let { existing ->
            val updated = if (forceFatigueOnly) {
                existing.withFatigueOnlyPlanningMetadata()
            } else {
                existing.withInferredPlanningMetadata()
            }
            if (updated != existing) {
                exerciseDao.updateExercise(updated)
            }
            return updated
        }
        val mapped = ExerciseMetadataMapper.applyLegacyMetadata(
            Exercise(
                name = name,
                category = category,
                stableKey = "imported_${name.stableToken()}",
                movementPattern = category.defaultMovementPattern(),
                movementCategory = category.defaultMovementCategory(),
                primaryMuscles = category.defaultPrimaryMuscles(),
                equipment = "NONE",
                forceType = category.defaultForceType(),
                plane = category.defaultPlane(),
                laterality = "BILATERAL",
                metadataConfidence = MetadataConfidence.LOW.name
            )
        ).let { exercise ->
            if (forceFatigueOnly) exercise.withFatigueOnlyPlanningMetadata() else exercise
        }
        val insertedId = exerciseDao.insertExercise(mapped)
        return if (insertedId > 0) {
            mapped.copy(id = insertedId)
        } else {
            exerciseDao.findByName(name) ?: mapped
        }
    }

    private fun DailyTimeseriesRow.confirmedCategoryCounts(): Map<String, Int> {
        val raw = linkedMapOf(
            "근력운동" to strengthEntries,
            "기능성운동" to functionalEntries,
            "유산소운동" to cardioEntries,
            "스포츠" to sportsEntries
        ).filterValues { count -> count > 0 }
        if (raw.isNotEmpty()) return raw
        return if (confirmedEntries > 0 || totalSets > 0 || totalReps > 0 || totalSeconds > 0 || totalTonnageKg > 0.0) {
            mapOf("근력운동" to 1)
        } else {
            emptyMap()
        }
    }

    private fun String.stableToken(): String =
        lowercase(Locale.US)
            .replace(Regex("[^a-z0-9가-힣]+"), "_")
            .trim('_')
            .ifBlank { "record" }

    private fun String.defaultMovementPattern(): String =
        when (this) {
            "유산소운동" -> MovementPattern.LOCOMOTION.name
            "스포츠" -> MovementPattern.FOOTWORK.name
            "기능성운동" -> MovementPattern.ANTI_ROTATION.name
            else -> MovementPattern.SQUAT.name
        }

    private fun String.defaultMovementCategory(): String =
        when (this) {
            "유산소운동" -> MovementCategory.CONDITIONING.name
            "스포츠" -> MovementCategory.SKILL_DRILL.name
            "기능성운동" -> MovementCategory.STABILITY.name
            else -> MovementCategory.STRENGTH.name
        }

    private fun String.defaultPrimaryMuscles(): String =
        when (this) {
            "유산소운동", "스포츠" -> "QUADRICEPS,CALF"
            "기능성운동" -> "CORE"
            else -> "QUADRICEPS"
        }

    private fun String.defaultForceType(): String =
        when (this) {
            "유산소운동", "스포츠" -> FatigueForceType.ACCELERATE.name
            "기능성운동" -> FatigueForceType.BRACE.name
            else -> FatigueForceType.SQUAT.name
        }

    private fun String.defaultPlane(): String =
        when (this) {
            "스포츠", "기능성운동" -> Plane.MULTI_PLANAR.name
            else -> Plane.SAGITTAL.name
        }

    private suspend fun seedMissingPrograms() {
        SeedData.programs(context).forEach { seed ->
            val programName = seed.displayName()
            if (programDao.findProgramByName(programName) != null) return@forEach

            val programId = programDao.insertProgram(
                TrainingProgram(
                    name = programName,
                    durationDays = seed.durationDays
                )
            )
            val items = seed.items.map { itemSeed ->
                val exercise = exerciseDao.findByName(itemSeed.exerciseName)
                TrainingProgramItem(
                    programId = programId,
                    weekNumber = itemSeed.weekNumber,
                    dayOfWeek = itemSeed.dayOfWeek,
                    orderIndex = itemSeed.orderIndex,
                    exerciseId = exercise?.id ?: 0,
                    exerciseName = exercise?.name ?: itemSeed.exerciseName,
                    category = exercise?.category ?: itemSeed.category,
                    restSeconds = itemSeed.restSeconds,
                    prescription = itemSeed.prescription,
                    setCount = itemSeed.setCount.coerceAtLeast(1),
                    reps = itemSeed.reps,
                    weightKg = itemSeed.weightKg,
                    seconds = itemSeed.seconds
                )
            }
            programDao.insertProgramItems(items)
        }
    }

    private fun defaultSet(entryId: Long, setIndex: Int, exercise: Exercise?): WorkoutSet {
        val seconds = when (exercise?.category) {
            "유산소운동" -> 20 * 60
            "스포츠" -> 60 * 60
            else -> if (exercise?.mode?.contains("시간") == true) 30 else 0
        }

        return WorkoutSet(
            entryId = entryId,
            setIndex = setIndex,
            reps = 0,
            weightKg = 0.0,
            seconds = seconds,
            confirmed = false,
            manualWeight = false
        )
    }

    private suspend fun refreshEntryCompletion(entryId: Long) {
        val completedAt = if (workoutDao.confirmedCountForEntry(entryId) > 0) {
            System.currentTimeMillis()
        } else {
            null
        }
        workoutDao.updateEntryCompletedAt(entryId, completedAt)
    }

    private suspend fun reindexProgramDay(programId: Long, weekNumber: Int, dayOfWeek: Int) {
        programDao.itemsForProgramDay(programId, weekNumber, dayOfWeek)
            .forEachIndexed { index, remaining ->
                programDao.updateProgramItemOrder(remaining.id, index + 1)
            }
    }

    private fun targetDates(startDate: String, items: List<TrainingProgramItem>): List<String> =
        items.map { dateForProgramItem(startDate, it) }.distinct()

    private fun TrainingProgram.dateRangeFor(startDate: String): Pair<String, String>? =
        runCatching {
            val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val end = start.plusDays(durationDays.coerceAtLeast(1).toLong() - 1L)
            start.format(DateTimeFormatter.ISO_LOCAL_DATE) to end.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()

    private fun dateForProgramItem(startDate: String, item: TrainingProgramItem): String {
        val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val daysFromStart = ((item.weekNumber - 1) * 7L) + (item.dayOfWeek - 1L)
        return start.plusDays(daysFromStart).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private suspend fun copyEntriesToDate(
        sourceEntries: List<WorkoutEntryWithSets>,
        targetDate: String,
        keepConfirmed: Boolean,
        baseCreatedAt: Long
    ) {
        sourceEntries.forEachIndexed { entryIndex, entryWithSets ->
            val confirmedCount = entryWithSets.sets.count { it.confirmed }
            val copiedEntryId = workoutDao.insertEntry(
                entryWithSets.entry.copy(
                    id = 0,
                    date = targetDate,
                    createdAt = baseCreatedAt + entryIndex,
                    completedAt = if (keepConfirmed && confirmedCount > 0) System.currentTimeMillis() else null
                )
            )
            entryWithSets.sets.sortedBy { it.setIndex }.forEach { sourceSet ->
                workoutDao.insertSet(
                    sourceSet.copy(
                        id = 0,
                        entryId = copiedEntryId,
                        confirmed = keepConfirmed && sourceSet.confirmed
                    )
                )
            }
        }
    }

    private fun dateRange(startDate: String, endDate: String): List<String> {
        val start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val end = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val first = minOf(start, end)
        val last = maxOf(start, end)
        val days = last.toEpochDay() - first.toEpochDay()
        return (0L..days).map { offset ->
            first.plusDays(offset).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    private fun nextCreatedAt(): Long = System.currentTimeMillis()

    private fun noteFromPrescription(prescription: String): String {
        val trimmed = prescription.trim()
        if (trimmed.isEmpty()) return ""
        val structureOnly = listOf(
            Regex("""^\d+\s*(세트|sets?|set)?\s*[xX×]\s*\d+\s*(회|reps?)?(\s*@\s*\d+(\.\d+)?\s*kg)?$"""),
            Regex("""^\d+\s*(초|분)\s*[xX×]\s*\d+\s*(세트|sets?)$"""),
            Regex("""^\d+\s*(세트|sets?)\s*[xX×]\s*\d+\s*(초|분)$"""),
            Regex("""^\d+\s*(회|reps?)\s*[xX×]\s*\d+\s*(세트|sets?)$""")
        ).any { it.matches(trimmed) }
        return if (structureOnly) "" else trimmed
    }

    private suspend fun logDebugSummary() {
        val isDebuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) return

        val analysisDateProvider = SystemAnalysisDateProvider()
        val today = analysisDateProvider.today().format(DateTimeFormatter.ISO_LOCAL_DATE)
        Log.d(
            "TrainingDbSummary",
            listOf(
                "exerciseCount=${exerciseDao.countExercises()}",
                "trainingProgramCount=${programDao.countPrograms()}",
                "trainingProgramItemCount=${programDao.countProgramItems()}",
                "todayWorkoutEntryCount=${workoutDao.countEntriesOnDate(today)}",
                "todayConfirmedSetCount=${workoutDao.countConfirmedSetsOnDate(today)}",
                "todayUnconfirmedSetCount=${workoutDao.countUnconfirmedSetsOnDate(today)}"
            ).joinToString(", ")
        )

        val metadataReport = MetadataSanityChecker.checkAll(exerciseDao.allExercises())
        Log.d(
            "ExerciseMetadataSummary",
            listOf(
                "total=${metadataReport.totalExerciseCount}",
                "confidence=${metadataReport.confidenceCounts}",
                "issueCount=${metadataReport.issueCount}",
                "errorCount=${metadataReport.errorCount}",
                "needsReview=${metadataReport.needsReviewExerciseNames.take(20)}"
            ).joinToString(", ")
        )

        runCatching {
            AnalysisEngineV3(
                inputCollector = AnalysisInputCollector(db),
                dateProvider = analysisDateProvider
            ).analyze()
        }.onSuccess { result ->
            Log.d(
                "AnalysisEngineV3",
                listOf(
                    "today=${result.today}",
                    "weeklyLoad7=${result.commonLoadMetrics.weeklyLoad7}",
                    "chronicLoad28=${result.commonLoadMetrics.chronicLoad28}",
                    "plannedSessionsNext7=${result.commonPlanProjectionMetrics.plannedSessionsNext7}",
                    "enabledMethodResults=${result.methodResults.size}",
                    "warnings=${result.debugWarnings.joinToString("|")}"
                ).joinToString(", ")
            )
        }.onFailure { error ->
            Log.w("AnalysisEngineV3", "V3 debug summary failed.", error)
        }
    }

    private suspend fun AppMetaDao.intValue(key: String): Int =
        value(key)?.toIntOrNull() ?: 0

    private suspend fun refreshExerciseAnalysisMetadata() {
        exerciseDao.allExercises().forEach { exercise ->
            if (!exercise.needsAnalysisMetadataRefresh()) return@forEach
            val mapped = ExerciseMetadataMapper.applyLegacyMetadata(exercise)
            if (mapped != exercise) {
                exerciseDao.updateExercise(mapped)
            }
        }
    }

    private fun Exercise.needsAnalysisMetadataRefresh(): Boolean =
        compoundType.isBlank() ||
            plane.isBlank() ||
            axialLoadLevel.isBlank() ||
            fatigueCategories.isBlank() ||
            adaptiveBaselineGroups.isBlank() ||
            recoveryDecayProfile.isBlank() ||
            progressMetricType.isBlank() ||
            strengthProgressionGroup.isBlank() ||
            hypertrophyVolumeGroup.isBlank() ||
            mainLiftGroup.isBlank() ||
            accessoryContributionGroup.isBlank() ||
            badmintonTransferStrength.isBlank() ||
            courtMovementTypes.isBlank() ||
            badmintonSkillTargets.isBlank() ||
            stabilityDemandLevel.isBlank() ||
            mobilityDemandLevel.isBlank() ||
            analysisEligibility.isBlank() ||
            activityKind.isBlank() ||
            planningEligibility.isBlank() ||
            activityKind !in ActivityKind.entries.map { kind -> kind.name } ||
            planningEligibility !in PlanningEligibility.entries.map { eligibility -> eligibility.name } ||
            metadataConfidence !in MetadataConfidence.entries.map { confidence -> confidence.name }

    private fun ProgramSeed.displayName(): String =
        when (name) {
            "배드민턴 웨이트 보조 4주" -> "배드민턴 보조 4주"
            else -> name
        }

    private companion object {
        const val EXERCISE_SEED_VERSION = 5
        const val PROGRAM_SEED_VERSION = 1
        const val META_EXERCISE_SEED_VERSION = "exercise_seed_version"
        const val META_PROGRAM_SEED_VERSION = "program_seed_version"
        const val TIMESERIES_IMPORT_NOTE = "CSV daily_timeseries import"
        val timedCategories = setOf("유산소운동", "스포츠")
    }
}
