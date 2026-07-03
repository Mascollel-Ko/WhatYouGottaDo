package com.training.trackplanner.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.coach.BadmintonTransferCoverageSummary
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.core.AnalysisInputCollector
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.engine.AnalysisEngineV3
import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.HomeTodaySummaryState
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val breakCategoryKeys = setOf(
    "NONE",
    "LESS_THAN_1_WEEK",
    "ONE_TO_TWO_WEEKS",
    "THREE_TO_FOUR_WEEKS",
    "FIVE_TO_EIGHT_WEEKS",
    "MORE_THAN_EIGHT_WEEKS"
)
private val breakReasonKeys = setOf("NONE", "SCHEDULE", "FATIGUE", "PAIN_OR_INJURY", "ILLNESS", "OTHER")
private val painAreaKeys = setOf(
    "NONE",
    "NECK",
    "SHOULDER",
    "ELBOW",
    "WRIST_HAND",
    "UPPER_BACK",
    "LOW_BACK",
    "HIP",
    "HAMSTRING",
    "KNEE",
    "CALF_ACHILLES",
    "ANKLE_FOOT",
    "OTHER"
)
private val avoidMovementKeys = setOf(
    "NONE",
    "HEAVY_SQUAT",
    "HEAVY_DEADLIFT",
    "BENCH_OR_PUSH",
    "OVERHEAD_PRESS",
    "JUMP_LANDING",
    "LUNGE_DECELERATION",
    "ROTATION",
    "LONG_BADMINTON",
    "HIGH_INTENSITY_INTERVAL",
    "OTHER"
)
private val primaryGoalKeys = setOf(
    "BADMINTON_PERFORMANCE",
    "STRENGTH_GAIN",
    "STRENGTH_MAINTENANCE",
    "HYPERTROPHY_PHYSIQUE",
    "RECOVERY_INJURY_PREVENTION",
    "WEIGHT_MANAGEMENT",
    "MIXED"
)

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
    private val dailyCheckInDao = db.dailyCheckInDao()
    private val smashSpeedDao = db.smashSpeedDao()
    private val appMetaDao = db.appMetaDao()
    private val initialUserProfileDao = db.initialUserProfileDao()
    private val runtimeExerciseMetadataDao = db.runtimeExerciseMetadataDao()
    private val canonicalRuntimeMetadataCatalog = RuntimeExerciseMetadataCatalogProvider.get(context)
    private val readQueryService = RepositoryReadQueryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        initialUserProfileDao = initialUserProfileDao
    )
    private val performanceTrendSummaryService = PerformanceTrendSummaryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao,
        smashSpeedDao = smashSpeedDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog
    )
    private val analysisSummaryService = AnalysisSummaryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        initialUserProfileDao = initialUserProfileDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog
    )
    private val smashSpeedService = SmashSpeedService(
        smashSpeedDao = smashSpeedDao
    )
    private val calendarRecordService = CalendarRecordService(
        db = db,
        workoutDao = workoutDao
    )
    private val recordMutationService = RecordMutationService(
        db = db,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao
    )
    private val programPlanService = ProgramPlanService(
        db = db,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        programDao = programDao,
        runtimeMetadataCatalogResolver = ::resolvedRuntimeMetadataCatalog,
        prescriptionNoteFormatter = ::noteFromPrescription
    )
    private val programGenerationService = ProgramGenerationService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        initialUserProfileDao = initialUserProfileDao,
        runtimeMetadataCatalogResolver = ::resolvedRuntimeMetadataCatalog
    )
    private val dailyStatusService = DailyStatusService(
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao
    )
    private val dailyReadinessInputService = DailyReadinessInputService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao,
        initialUserProfileDao = initialUserProfileDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
        dailyStatusService = dailyStatusService
    )
    private val todayStatusSummaryService = TodayStatusSummaryService(
        dailyReadinessInputService = dailyReadinessInputService
    )
    private val homeSummaryService = HomeSummaryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        initialUserProfileDao = initialUserProfileDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog
    )
    private val coachingSignalsSummaryService = CoachingSignalsSummaryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
        dailyStatusService = dailyStatusService
    )

    val exercises: Flow<List<Exercise>> = readQueryService.exercises
    val programs: Flow<List<TrainingProgram>> = programPlanService.programs
    val analysisStats: Flow<AnalysisStats> = readQueryService.analysisStats
    val initialUserProfile: Flow<InitialUserProfile?> = readQueryService.initialUserProfile

    fun observeCheckInForDate(date: String): Flow<DailyCheckIn?> =
        dailyStatusService.observeCheckInForDate(date)

    fun observeRecentCheckIns(startDate: String, endDate: String): Flow<List<DailyCheckIn>> =
        dailyStatusService.observeRecentCheckIns(startDate, endDate)

    fun observeSmashSpeedsForDate(date: String): Flow<List<SmashSpeedRecord>> =
        smashSpeedService.observeForDate(date)

    suspend fun addSmashSpeed(date: String, speedKmh: Double, note: String? = null) = withContext(Dispatchers.IO) {
        smashSpeedService.add(date, speedKmh, note)
    }

    suspend fun deleteSmashSpeed(recordId: Long) = withContext(Dispatchers.IO) {
        smashSpeedService.delete(recordId)
    }

    suspend fun checkInForDate(date: String): DailyCheckIn? =
        dailyStatusService.checkInForDate(date)

    suspend fun recentCheckIns(startDate: String, endDate: String): List<DailyCheckIn> =
        dailyStatusService.recentCheckIns(startDate, endDate)

    suspend fun upsertDailyCheckIn(checkIn: DailyCheckIn) =
        dailyStatusService.upsertDailyCheckIn(checkIn)

    suspend fun deleteDailyCheckIn(date: String) =
        dailyStatusService.deleteDailyCheckIn(date)

    suspend fun todayReadinessSummary(): TodayReadinessSummary =
        todayStatusSummaryService.todayReadinessSummary()

    suspend fun phaseAwareTodayStatus(): PhaseAwareTodayStatus =
        todayStatusSummaryService.phaseAwareTodayStatus()

    suspend fun homeTodaySummary(todayStatus: PhaseAwareTodayStatus? = null): HomeTodaySummaryState =
        homeSummaryService.build(todayStatus)

    suspend fun fatigueAnalysisHistory(days: Int = 28 * 7): List<DailyFatigueResult> =
        withContext(Dispatchers.IO) {
            analysisSummaryService.fatigueAnalysisHistory(days)
        }

    suspend fun performanceTrendSummary(): PerformanceTrendSummary = withContext(Dispatchers.IO) {
        performanceTrendSummaryService.build()
    }

    suspend fun badmintonTransferSummary(
        readinessSummary: TodayReadinessSummary? = null
    ): BadmintonTransferSummary = withContext(Dispatchers.IO) {
        analysisSummaryService.badmintonTransferSummary(readinessSummary)
    }

    suspend fun badmintonTransferCoverageSummary(
        latestFatigueState: DailyFatigueState?
    ): BadmintonTransferCoverageSummary = withContext(Dispatchers.IO) {
        analysisSummaryService.badmintonTransferCoverageSummary(latestFatigueState)
    }

    suspend fun coachingSignalsSummary(
        history: List<DailyFatigueResult>
    ): CoachingSignalsSummary =
        coachingSignalsSummaryService.build(history)

    fun entriesForDate(date: String): Flow<List<WorkoutEntryWithSets>> =
        readQueryService.entriesForDate(date)

    suspend fun exportRecordsBackup(uri: Uri): RecordCsvTransferResult = withContext(Dispatchers.IO) {
        BackupExportService(
            context = context,
            workoutDao = workoutDao,
            dailyMetricDao = dailyMetricDao,
            dailyCheckInDao = dailyCheckInDao,
            smashSpeedDao = smashSpeedDao,
            exerciseDao = exerciseDao,
            initialUserProfileDao = initialUserProfileDao,
            runtimeExerciseMetadataDao = runtimeExerciseMetadataDao
        ).export(uri)
    }

    suspend fun importRecordsBackup(uri: Uri): RecordCsvTransferResult = withContext(Dispatchers.IO) {
        BackupImportService(
            restoreImporter = ::importRestoreCsv,
            dailyTimeseriesImporter = ::importDailyTimeseriesCsv
        ).import(context, uri)
    }

    fun entryCount(date: String): Flow<Int> =
        readQueryService.entryCount(date)

    fun plannedSetCount(date: String): Flow<Int> =
        readQueryService.plannedSetCount(date)

    fun confirmedSetCount(date: String): Flow<Int> =
        readQueryService.confirmedSetCount(date)

    fun dailySummaries(startDate: String, endDate: String): Flow<List<DailyRecordSummary>> =
        readQueryService.dailySummaries(startDate, endDate)

    fun programItems(programId: Long): Flow<List<TrainingProgramItem>> =
        programPlanService.programItems(programId)

    fun metricForDate(date: String): Flow<DailyMetric?> =
        dailyStatusService.metricForDate(date)

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
        repairCustomExerciseStableKeys()
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
        val runtimeOverrideKeys = runtimeExerciseMetadataDao.all()
            .map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
            .let(ExerciseMetadataOverrideBackupMapper::overrideKeys)
        SeedData.exercises(context).forEach { seed ->
            val existing = exerciseDao.findByStableKey(seed.stableKey)
                ?: exerciseDao.findByName(seed.name)
            if (existing == null) {
                exerciseDao.insertExercise(seed)
            } else if (ExerciseMetadataOverrideBackupMapper.hasOverride(existing.stableKey, runtimeOverrideKeys)) {
                return@forEach
            } else {
                ExerciseStableKeyPolicy.mergeSeed(existing, seed)?.let { merged ->
                    exerciseDao.updateExercise(merged)
                }
            }
        }
    }

    suspend fun exerciseEditorData(exerciseId: Long?): ExerciseRuntimeMetadataEditorData =
        withContext(Dispatchers.IO) {
            val persistedRows = runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
            val resolver = RuntimeExerciseMetadataResolver(canonicalRuntimeMetadataCatalog, persistedRows)
            val options = RuntimeMetadataEditorOptions.from(
                canonicalRuntimeMetadataCatalog.all() + persistedRows
            )
            val exercise = exerciseId?.let { exerciseDao.findById(it) }
                ?: Exercise(
                    name = "",
                    category = "근력운동",
                    stableKey = "",
                    isCustom = true
                )
            val metadata = if (exerciseId == null) {
                RuntimeExerciseMetadataDefaults.forIdentity("", "")
            } else {
                resolver.resolve(exercise)
            }
            val copySources = exerciseDao.allExercises()
                .asSequence()
                .filter { source -> source.id != exercise.id && source.name.isNotBlank() }
                .sortedBy { source -> source.name }
                .map { source -> ExerciseMetadataCopySource(source, resolver.resolve(source)) }
                .toList()
            ExerciseRuntimeMetadataEditorData(exercise, metadata, options, copySources)
        }

    suspend fun saveExerciseEditor(data: ExerciseRuntimeMetadataEditorData): Long =
        withContext(Dispatchers.IO) {
            require(data.exercise.name.isNotBlank()) { "운동 이름을 입력하세요." }
            require(data.exercise.category.isNotBlank()) { "분류를 입력하세요." }
            require(data.exercise.defaultRestSeconds in 0..3600) { "휴식 시간은 0~3600초로 입력하세요." }
            db.withTransaction {
                val existing = data.exercise.id.takeIf { it > 0 }?.let { exerciseDao.findById(it) }
                val savedExercise = if (existing == null) {
                    insertUserExerciseWithUniqueKey(data.exercise)
                } else {
                    val stableKey = existing.stableKey.ifBlank {
                        uniqueUserExerciseStableKey()
                    }
                    ExerciseStableKeyPolicy.preserveOnEdit(existing, data.exercise, stableKey)
                        .also { exerciseDao.updateExercise(it) }
                }
                runtimeExerciseMetadataDao.upsert(
                    data.metadata.copy(
                        stableKey = savedExercise.stableKey,
                        exerciseName = savedExercise.name,
                        safeForSeedMutation = false
                    ).toEntity()
                )
                savedExercise.id
            }
        }

    suspend fun resetExerciseMetadataOverride(exerciseId: Long): Boolean =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val exercise = exerciseDao.findById(exerciseId) ?: return@withTransaction false
                runtimeExerciseMetadataDao.deleteByStableKey(exercise.stableKey)
                val seed = seedExercisesByStableKey()[ExerciseMetadataOverrideBackupMapper.overrideKey(exercise.stableKey)]
                if (seed != null) {
                    exerciseDao.updateExercise(
                        seed.copy(
                            id = exercise.id,
                            stableKey = seed.stableKey,
                            imageAssetName = seed.imageAssetName.ifBlank { exercise.imageAssetName },
                            isActive = exercise.isActive,
                            archivedAt = exercise.archivedAt,
                            isCustom = false,
                            needsReview = exercise.needsReview || seed.needsReview
                        )
                    )
                }
                true
            }
        }

    suspend fun resolveRuntimeMetadata(exercise: Exercise): RuntimeExerciseMetadata =
        withContext(Dispatchers.IO) {
            RuntimeExerciseMetadataResolver(
                canonicalRuntimeMetadataCatalog,
                runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
            ).resolve(exercise)
        }

    suspend fun resolvedRuntimeMetadataByExerciseId(): Map<Long, RuntimeExerciseMetadata> =
        withContext(Dispatchers.IO) {
            val exercises = exerciseDao.allExercises()
            val catalog = resolvedRuntimeMetadataCatalog(exercises)
            exercises.associate { exercise ->
                exercise.id to (catalog.resolve(exercise) ?: RuntimeExerciseMetadataDefaults.forExercise(exercise))
            }
        }

    private suspend fun repairCustomExerciseStableKeys() {
        exerciseDao.customExercisesWithBlankStableKey().forEach { exercise ->
            exerciseDao.updateExercise(exercise.copy(stableKey = uniqueUserExerciseStableKey()))
        }
    }

    private suspend fun insertUserExerciseWithUniqueKey(draft: Exercise): Exercise {
        repeat(USER_KEY_RETRY_LIMIT) {
            val candidate = draft.copy(
                id = 0,
                stableKey = uniqueUserExerciseStableKey(),
                isCustom = true
            )
            val id = exerciseDao.insertExercise(candidate)
            if (id > 0) return candidate.copy(id = id)
        }
        error("사용자 운동 식별자를 생성하지 못했습니다.")
    }

    private suspend fun uniqueUserExerciseStableKey(): String {
        repeat(USER_KEY_RETRY_LIMIT) {
            val candidate = UserExerciseStableKeyGenerator.generate()
            if (exerciseDao.findByStableKey(candidate) == null) return candidate
        }
        error("사용자 운동 식별자 충돌을 해결하지 못했습니다.")
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)

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
            runtimeExerciseMetadataDao.deleteByStableKey(exercise.stableKey)
            exerciseDao.deleteExercise(exercise)
            ExerciseDeleteResult(deleted = true, referenced = false)
        }
    }

    suspend fun addWorkoutEntry(date: String, exerciseId: Long): Long = withContext(Dispatchers.IO) {
        recordMutationService.addWorkoutEntry(date, exerciseId)
    }

    suspend fun updateWorkoutEntry(entry: WorkoutEntry) = withContext(Dispatchers.IO) {
        recordMutationService.updateWorkoutEntry(entry)
    }

    suspend fun deleteWorkoutEntry(entry: WorkoutEntry) = withContext(Dispatchers.IO) {
        recordMutationService.deleteWorkoutEntry(entry)
    }

    suspend fun addSet(entry: WorkoutEntry) = withContext(Dispatchers.IO) {
        recordMutationService.addSet(entry)
    }

    suspend fun updateSet(set: WorkoutSet) = withContext(Dispatchers.IO) {
        recordMutationService.updateSet(set)
    }

    suspend fun deleteSet(set: WorkoutSet): Boolean = withContext(Dispatchers.IO) {
        recordMutationService.deleteSet(set)
    }

    suspend fun createProgram() = withContext(Dispatchers.IO) {
        programPlanService.createProgram()
    }

    suspend fun generateProgramSkeleton(request: ProgramSkeletonRequest): GeneratedProgramSkeleton =
        withContext(Dispatchers.IO) {
            programGenerationService.generateProgramSkeleton(request)
        }

    suspend fun saveGeneratedProgram(
        existingProgramId: Long?,
        skeleton: GeneratedProgramSkeleton
    ): Long = withContext(Dispatchers.IO) {
        programPlanService.saveGeneratedProgram(existingProgramId, skeleton)
    }

    suspend fun deleteProgram(programId: Long) = withContext(Dispatchers.IO) {
        programPlanService.deleteProgram(programId)
    }

    suspend fun addExerciseToProgram(
        programId: Long,
        weekNumber: Int,
        dayOfWeek: Int,
        exerciseId: Long
    ) = withContext(Dispatchers.IO) {
        programPlanService.addExerciseToProgram(programId, weekNumber, dayOfWeek, exerciseId)
    }

    suspend fun updateProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programPlanService.updateProgramItem(item)
    }

    suspend fun deleteProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programPlanService.deleteProgramItem(item)
    }

    suspend fun programHasDateConflicts(programId: Long, startDate: String): Boolean =
        withContext(Dispatchers.IO) {
            programPlanService.programHasDateConflicts(programId, startDate)
        }

    suspend fun programApplyConflictSummary(
        programId: Long,
        startDate: String
    ): ProgramApplyConflictSummary = withContext(Dispatchers.IO) {
        programPlanService.programApplyConflictSummary(programId, startDate)
    }

    suspend fun applyProgramToDates(
        programId: Long,
        startDate: String,
        mode: ProgramApplyMode,
        trainingGate: TrainingGateSnapshot? = null
    ) = withContext(Dispatchers.IO) {
        programPlanService.applyProgramToDates(programId, startDate, mode, trainingGate)
    }

    suspend fun calendarConflictSummary(dates: List<String>): CalendarConflictSummary =
        withContext(Dispatchers.IO) {
            calendarRecordService.calendarConflictSummary(dates)
        }

    suspend fun deleteDate(date: String) = withContext(Dispatchers.IO) {
        calendarRecordService.deleteDate(date)
    }

    suspend fun deleteDateRange(
        startDate: String,
        endDate: String,
        includeConfirmed: Boolean
    ) = withContext(Dispatchers.IO) {
        calendarRecordService.deleteDateRange(startDate, endDate, includeConfirmed)
    }

    suspend fun copyDate(
        sourceDate: String,
        targetDate: String,
        keepConfirmed: Boolean,
        conflictMode: CalendarConflictMode
    ) = withContext(Dispatchers.IO) {
        calendarRecordService.copyDate(sourceDate, targetDate, keepConfirmed, conflictMode)
    }

    suspend fun moveDate(
        sourceDate: String,
        targetDate: String,
        conflictMode: CalendarConflictMode
    ) = withContext(Dispatchers.IO) {
        calendarRecordService.moveDate(sourceDate, targetDate, conflictMode)
    }

    suspend fun copyDateRangeAsPlan(
        sourceStart: String,
        sourceEnd: String,
        targetStart: String,
        conflictMode: CalendarConflictMode,
        keepConfirmed: Boolean = false
    ) = withContext(Dispatchers.IO) {
        calendarRecordService.copyDateRangeAsPlan(sourceStart, sourceEnd, targetStart, conflictMode, keepConfirmed)
    }

    suspend fun saveDailyMetric(
        date: String,
        sleepHours: Double?,
        bodyWeightKg: Double?
    ) = dailyStatusService.saveDailyMetric(date, sleepHours, bodyWeightKg)

    private suspend fun importRestoreCsv(data: RecordCsvImportData.Restore): RecordCsvTransferResult {
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
            val restoredRuntimeOverrideKeys = ExerciseMetadataOverrideBackupMapper.overrideKeys(data.runtimeMetadataRows)
            data.profileRows.toInitialUserProfile()?.let { profile ->
                initialUserProfileDao.upsert(profile)
                profileCount = 1
            }
            data.exerciseRows.forEach { row ->
                if (upsertRestoredExercise(row, seedByStableKey, restoredRuntimeOverrideKeys)) {
                    exerciseCount += 1
                }
            }
            data.runtimeMetadataRows.forEach { metadata ->
                runtimeExerciseMetadataDao.upsert(
                    metadata.copy(safeForSeedMutation = false).toEntity()
                )
            }
            val importedDailyMetrics = mutableMapOf<String, DailyMetric>()
            data.dailyRows.forEach { row ->
                if (row.sleepHours != null || row.bodyWeightKg != null) {
                    val metric = DailyMetric(
                        date = row.date,
                        sleepHours = row.sleepHours,
                        bodyWeightKg = row.bodyWeightKg
                    )
                    dailyMetricDao.upsert(metric)
                    importedDailyMetrics[row.date] = metric
                    dailyCount += 1
                }
            }
            data.checkInRows.forEach { row ->
                val now = System.currentTimeMillis()
                val canonicalMetric = importedDailyMetrics[row.date]
                val canonicalSleep = canonicalMetric?.sleepHours ?: row.sleepHours
                if (canonicalMetric?.sleepHours == null && row.sleepHours != null) {
                    val promotedMetric = DailyMetric(
                        date = row.date,
                        sleepHours = row.sleepHours,
                        bodyWeightKg = canonicalMetric?.bodyWeightKg
                    )
                    dailyMetricDao.upsert(promotedMetric)
                    importedDailyMetrics[row.date] = promotedMetric
                    if (canonicalMetric == null) dailyCount += 1
                }
                dailyCheckInDao.upsert(
                    DailyCheckIn(
                        date = row.date,
                        sleepHours = canonicalSleep,
                        overallFatigue = row.overallFatigue,
                        lowerBodyFatigue = row.lowerBodyFatigue,
                        jointTendonDiscomfort = row.jointTendonDiscomfort,
                        focusMotivation = row.focusMotivation,
                        note = row.note,
                        createdAt = row.createdAt ?: now,
                        updatedAt = row.updatedAt ?: now
                    ).validated()
                )
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
                    val metric = DailyMetric(
                        date = row.date,
                        sleepHours = row.sleepHours ?: existingMetric?.sleepHours,
                        bodyWeightKg = row.bodyWeightKg ?: existingMetric?.bodyWeightKg
                    )
                    dailyMetricDao.upsert(metric)
                    importedDailyMetrics[row.date] = metric
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
                        name = first.exerciseName,
                        category = first.category,
                        stableKey = first.stableKey,
                        seedByStableKey = seedByStableKey
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

    private suspend fun upsertRestoredExercise(
        row: RestoreExerciseRow,
        seedByStableKey: Map<String, Exercise>,
        restoredRuntimeOverrideKeys: Set<String>
    ): Boolean {
        val stableKey = row.stableKey.ifBlank {
            if (row.isCustom) {
                "imported_${row.name.stableToken()}"
            } else {
                canonicalRuntimeMetadataCatalog.resolveLegacyName(row.name)?.stableKey
                    ?: "imported_${row.name.stableToken()}"
            }
        }
        val category = row.category.ifBlank { "근력운동" }
        val csvExercise = Exercise(
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
            metadataConfidence = row.metadataConfidence.ifBlank { MetadataConfidence.LOW.name },
            imageAssetName = row.imageAssetName,
            isActive = row.isActive,
            archivedAt = if (row.isActive) null else System.currentTimeMillis(),
            isCustom = row.isCustom,
            needsReview = row.needsReview
        )
        val hasRestoredOverride = ExerciseMetadataOverrideBackupMapper.hasOverride(stableKey, restoredRuntimeOverrideKeys)
        val restored = if (ExerciseSeedMetadataPolicy.isBuiltInStableKey(stableKey, seedByStableKey) && !hasRestoredOverride) {
            ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(csvExercise, seedByStableKey)
        } else {
            ExerciseMetadataMapper.applyLegacyMetadata(csvExercise)
        }
        val existing = exerciseDao.findByStableKey(stableKey) ?: exerciseDao.findByName(row.name)
        if (existing == null) {
            exerciseDao.insertExercise(restored)
        } else {
            val updated = restored.copy(
                id = existing.id,
                stableKey = existing.stableKey.ifBlank { restored.stableKey },
                imageAssetName = restored.imageAssetName.ifBlank { existing.imageAssetName },
                isCustom = if (ExerciseSeedMetadataPolicy.isBuiltInStableKey(stableKey, seedByStableKey)) {
                    false
                } else {
                    existing.isCustom || restored.isCustom
                },
                needsReview = existing.needsReview || restored.needsReview
            )
            exerciseDao.updateExercise(
                if (hasRestoredOverride) updated else ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(updated, seedByStableKey)
            )
        }
        return true
    }

    private fun List<RestoreProfileRow>.toInitialUserProfile(): InitialUserProfile? {
        if (isEmpty()) return null
        val values = associate { row -> row.key to row.value }
        val recoveryScaleHighIsGood = values["profileRecoveryScaleDirection"]
            ?.trim()
            ?.equals("HIGH_IS_GOOD", ignoreCase = true) == true
        fun recoveryScore(key: String): Int? {
            val value = values[key].toScale5Int() ?: return null
            return if (recoveryScaleHighIsGood || key == "sleepQuality" || key == "currentCondition" || key == "currentMood") {
                value
            } else {
                6 - value
            }
        }
        val sex = normalizeProfileSex(values["sex"].orEmpty().ifBlank { values["gender"].orEmpty() })
        val currentYear = LocalDate.now().year
        val birthYear = values["birthYear"]?.toIntOrNull()?.takeIf { it in 1900..currentYear }
            ?: values["birthYearOrAgeRange"]?.trim()?.toIntOrNull()?.takeIf { it in 1900..currentYear }
        val breakCategory = sanitizeProfileKey(values["trainingBreakCategory"], breakCategoryKeys).ifBlank {
            breakWeeksToProfileCategory(values["breakWeeks"]?.toIntOrNull())
        }
        val breakReason = sanitizeProfileKey(values["trainingBreakReason"], breakReasonKeys).ifBlank {
            if (values["breakDueToPain"].toCsvBoolean()) "PAIN_OR_INJURY" else "NONE"
        }
        val painAreaTags = sanitizeTagList(
            raw = values["painAreaTags"],
            allowed = painAreaKeys,
            legacyText = values["painAreas"]
        )
        val avoidMovementTags = sanitizeTagList(
            raw = values["avoidMovementTags"],
            allowed = avoidMovementKeys,
            legacyText = values["avoidedMovements"]
        )
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
            strengthAverageRpe = values["strengthAverageRpe"].toRpeDouble(),
            badmintonSessionsPerWeek = values["badmintonSessionsPerWeek"]?.toDoubleOrNull(),
            badmintonMinutesPerSession = values["badmintonMinutesPerSession"]?.toIntOrNull(),
            badmintonAverageRpe = values["badmintonAverageRpe"].toRpeDouble(),
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
            sleepQuality = recoveryScore("sleepQuality"),
            currentFatigue = recoveryScore("currentFatigue"),
            currentSoreness = recoveryScore("currentSoreness"),
            currentStress = recoveryScore("currentStress"),
            currentMood = recoveryScore("currentMood"),
            currentCondition = recoveryScore("currentCondition")
                ?: recoveryScore("currentMood"),
            painAreas = values["painAreas"].orEmpty(),
            painAreaTags = painAreaTags,
            avoidedMovements = values["avoidedMovements"].orEmpty(),
            avoidMovementTags = avoidMovementTags,
            goals = values["goals"].orEmpty(),
            primaryGoal = sanitizeProfileKey(values["primaryGoal"], primaryGoalKeys).ifBlank {
                legacyGoalToKey(values["goals"].orEmpty())
            },
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

    private fun String?.toRpeDouble(): Double? =
        this?.toDoubleOrNull()?.takeIf { value -> value in 1.0..10.0 }

    private fun String?.toScale5Int(): Int? =
        this?.toIntOrNull()?.takeIf { value -> value in 1..5 }

    private fun sanitizeProfileKey(value: String?, allowed: Set<String>): String =
        value?.trim()?.uppercase(Locale.US)?.takeIf { key -> key in allowed }.orEmpty()

    private fun sanitizeTagList(raw: String?, allowed: Set<String>, legacyText: String?): String {
        val tags = raw.orEmpty()
            .split(",", "|", ";")
            .map { value -> value.trim().uppercase(Locale.US) }
            .filter { key -> key in allowed && key != "NONE" }
            .distinct()
        return when {
            tags.isNotEmpty() -> tags.sorted().joinToString(",")
            legacyText.isNullOrBlank() -> "NONE"
            "OTHER" in allowed -> "OTHER"
            else -> "NONE"
        }
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
        forceFatigueOnly: Boolean = false,
        stableKey: String = "",
        seedByStableKey: Map<String, Exercise> = emptyMap()
    ): Exercise {
        val resolvedStableKey = stableKey.takeIf { it.isNotBlank() }
            ?: canonicalRuntimeMetadataCatalog.resolveLegacyName(name)?.stableKey
        val existingExercise = resolvedStableKey
            ?.let { key -> exerciseDao.findByStableKey(key) }
            ?: exerciseDao.findByName(name)
        existingExercise?.let { existing ->
            val seedBacked = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(existing, seedByStableKey)
            val updated = if (forceFatigueOnly) {
                seedBacked.withFatigueOnlyPlanningMetadata()
            } else {
                seedBacked.withInferredPlanningMetadata()
            }
            if (updated != existing) {
                exerciseDao.updateExercise(updated)
            }
            return updated
        }
        resolvedStableKey
            ?.let { key -> seedByStableKey[key.trim().lowercase()] }
            ?.let { seed ->
                val insertedId = exerciseDao.insertExercise(seed)
                return if (insertedId > 0) seed.copy(id = insertedId) else exerciseDao.findByStableKey(seed.stableKey) ?: seed
            }
        val mapped = ExerciseMetadataMapper.applyLegacyMetadata(
            Exercise(
                name = name,
                category = category,
                stableKey = resolvedStableKey ?: "imported_${name.stableToken()}",
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
            else -> MovementPattern.ISOLATION.name
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
            else -> FatigueForceType.BRACE.name
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
            val exercises = exerciseDao.allExercises()
            AnalysisEngineV3(
                inputCollector = AnalysisInputCollector(db, resolvedRuntimeMetadataCatalog(exercises)),
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
        val seedByStableKey = seedExercisesByStableKey()
        val runtimeOverrideKeys = runtimeExerciseMetadataDao.all()
            .map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
            .let(ExerciseMetadataOverrideBackupMapper::overrideKeys)
        exerciseDao.allExercises().forEach { exercise ->
            val hasBuiltInOverride = ExerciseSeedMetadataPolicy.isBuiltInStableKey(exercise.stableKey, seedByStableKey) &&
                ExerciseMetadataOverrideBackupMapper.hasOverride(exercise.stableKey, runtimeOverrideKeys)
            if (hasBuiltInOverride) return@forEach
            val seedBacked = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(exercise, seedByStableKey)
            val mapped = if (seedBacked != exercise) {
                seedBacked
            } else {
                if (!exercise.needsAnalysisMetadataRefresh()) return@forEach
                ExerciseMetadataMapper.applyLegacyMetadata(exercise)
            }
            if (mapped != exercise) {
                exerciseDao.updateExercise(mapped)
            }
        }
    }

    private fun seedExercisesByStableKey(): Map<String, Exercise> =
        SeedData.exactExerciseMetadataByStableKey(context)

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
        const val USER_KEY_RETRY_LIMIT = 8
        const val EXERCISE_SEED_VERSION = 7
        const val PROGRAM_SEED_VERSION = 1
        const val META_EXERCISE_SEED_VERSION = "exercise_seed_version"
        const val META_PROGRAM_SEED_VERSION = "program_seed_version"
        const val TIMESERIES_IMPORT_NOTE = "CSV daily_timeseries import"
    }
}
