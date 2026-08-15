package com.training.trackplanner.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.fatigue.HomeTodaySummaryState
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.tissue.TissueCurrentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

        private val habitualTrainingIntensityKeys = setOf("LIGHT", "NORMAL", "HARD")
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
    private val exerciseIdentityMigrationIssueDao = db.exerciseIdentityMigrationIssueDao()
    private val dataTransferReportStore = DataTransferReportStore(appMetaDao)
    private val initialUserProfileDao = db.initialUserProfileDao()
    private val runtimeExerciseMetadataDao = db.runtimeExerciseMetadataDao()
    private val exerciseMetadataUserOverrideDao = db.exerciseMetadataUserOverrideDao()
    private val exerciseRoleRelationDao = db.exerciseRoleRelationDao()
    private val strengthPosteriorDao = db.strengthPosteriorDao()
    private val canonicalMetadataRepository = CanonicalExerciseMetadataRepositoryProvider.get(context)
    private val canonicalRuntimeMetadataCatalog = canonicalMetadataRepository.runtimeMetadataCatalog()
    private val canonicalOfiAxisProfiles = canonicalMetadataRepository.ofiAxisProfiles()
    private val canonicalCoreCatalog = canonicalMetadataRepository.coreCatalog()
    private val badmintonObjectiveCatalog = canonicalMetadataRepository.badmintonObjectiveCatalog()
    private val exerciseRoleRelationAssetLoader = ExerciseRoleRelationAssetLoader(context)
    private val legacyExerciseImportMapper = LegacyExerciseImportMapper.fromAssets(context)
    private val strengthPerformanceRegistry = StrengthPerformanceRegistry.fromContext(context)
    private val repetitionCurveRegistry = RepetitionCurveRegistry.fromContext(context)
    private val rpeRirPolicy = RpeRirPolicy.fromContext(context)
    private val workoutSourceIdentityProvider = WorkoutSourceIdentityProvider(
        db = db,
        appMetaDao = appMetaDao,
        workoutDao = workoutDao
    )
    private val exerciseMetadataReconciliationService = ExerciseMetadataReconciliationService(
        context = context,
        db = db,
        exerciseDao = exerciseDao,
        runtimeMetadataDao = runtimeExerciseMetadataDao,
        relationDao = exerciseRoleRelationDao,
        overrideDao = exerciseMetadataUserOverrideDao,
        appMetaDao = appMetaDao,
        canonicalRepository = canonicalMetadataRepository,
        seedExercisesByStableKey = ::seedExercisesByStableKey
    )
    private val strengthPosteriorEventProcessor = StrengthPosteriorEventProcessor(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao,
        initialUserProfileDao = initialUserProfileDao,
        posteriorDao = strengthPosteriorDao,
        registry = strengthPerformanceRegistry,
        curves = repetitionCurveRegistry,
        rirPolicy = rpeRirPolicy
    )
    private val strengthPosteriorCoordinator = StrengthPosteriorUpdateCoordinator(
        db = db,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        appMetaDao = appMetaDao,
        posteriorDao = strengthPosteriorDao,
        processor = strengthPosteriorEventProcessor
    )
    private val dailyStatusService = DailyStatusService(
        db = db,
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao
    )
    private val exerciseMetadataEditorService = ExerciseMetadataEditorService(
        db = db,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        programDao = programDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        exerciseRoleRelationDao = exerciseRoleRelationDao,
        overrideDao = exerciseMetadataUserOverrideDao,
        canonicalMetadataRepository = canonicalMetadataRepository,
        seedExercisesByStableKey = ::seedExercisesByStableKey,
        semanticRevision = {
            ExerciseMetadataRevisionPolicy.project(context, canonicalMetadataRepository)
                .semanticCanonicalMetadataRevision
        }
    )
    private val backupRestorePlanner = BackupRestorePlanner(
        initialUserProfileDao = initialUserProfileDao,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        programDao = programDao,
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao,
        smashSpeedDao = smashSpeedDao,
        runtimeMetadataDao = runtimeExerciseMetadataDao,
        relationDao = exerciseRoleRelationDao,
        overrideDao = exerciseMetadataUserOverrideDao,
        appMetaDao = appMetaDao,
        canonicalExercises = ::seedExercisesByStableKey,
        semanticRevision = {
            ExerciseMetadataRevisionPolicy.project(context, canonicalMetadataRepository)
                .semanticCanonicalMetadataRevision
        }
    )
    private val backupRestoreImportService = BackupRestoreImportService(
        db = db,
        initialUserProfileDao = initialUserProfileDao,
        exerciseDao = exerciseDao,
        exerciseRoleRelationDao = exerciseRoleRelationDao,
        workoutDao = workoutDao,
        programDao = programDao,
        dailyMetricDao = dailyMetricDao,
        dailyCheckInDao = dailyCheckInDao,
        dailyStatusService = dailyStatusService,
        smashSpeedDao = smashSpeedDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        exerciseMetadataUserOverrideDao = exerciseMetadataUserOverrideDao,
        appMetaDao = appMetaDao,
        workoutSourceIdentityProvider = workoutSourceIdentityProvider,
        strengthPosteriorDao = strengthPosteriorDao,
        strengthPosteriorCoordinator = strengthPosteriorCoordinator,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
        canonicalMetadataRepository = canonicalMetadataRepository,
        restorePlanner = backupRestorePlanner,
        seedExercisesByStableKey = ::seedExercisesByStableKey,
        profileFromRows = { rows -> rows.toInitialUserProfile() }
    )
    private val dailyTimeseriesImportService = DailyTimeseriesImportService(
        db = db,
        dailyStatusService = dailyStatusService
    )
    private var pendingBackupRestore: BackupImportService.PreparedRestore? = null
    private var pendingBackupRestorePlan: BackupRestorePlan? = null
    private val readQueryService = RepositoryReadQueryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        initialUserProfileDao = initialUserProfileDao
    )
    private val performanceTrendSummaryService = PerformanceTrendSummaryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        initialUserProfileDao = initialUserProfileDao,
        dailyCheckInDao = dailyCheckInDao,
        smashSpeedDao = smashSpeedDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
        canonicalCoreCatalog = canonicalCoreCatalog,
        badmintonObjectiveCatalog = badmintonObjectiveCatalog,
        strengthPosteriorDao = strengthPosteriorDao,
        strengthPerformanceRegistry = strengthPerformanceRegistry,
        appMetaDao = appMetaDao
    )
    private val analysisSummaryService = AnalysisSummaryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        initialUserProfileDao = initialUserProfileDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
        canonicalOfiAxisProfiles = canonicalOfiAxisProfiles
    )
    private val smashSpeedService = SmashSpeedService(
        smashSpeedDao = smashSpeedDao
    )
    private val calendarRecordService = CalendarRecordService(
        db = db,
        workoutDao = workoutDao,
        workoutSourceIdentityProvider = workoutSourceIdentityProvider,
        strengthPosteriorCoordinator = strengthPosteriorCoordinator
    )
    private val recordMutationService = RecordMutationService(
        db = db,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        workoutSourceIdentityProvider = workoutSourceIdentityProvider,
        strengthPosteriorCoordinator = strengthPosteriorCoordinator
    )
    private val programPlanService = ProgramPlanService(
        db = db,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        programDao = programDao,
        workoutSourceIdentityProvider = workoutSourceIdentityProvider,
        prescriptionNoteFormatter = ::noteFromPrescription,
        builtInProgramKeys = { SeedData.programs(context).mapTo(mutableSetOf(), ProgramSeed::key) }
    )
    private val programGenerationService = ProgramGenerationService(
        exerciseDao = exerciseDao
    )
    private val dailyReadinessInputService = DailyReadinessInputService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
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
        dailyMetricDao = dailyMetricDao,
        initialUserProfileDao = initialUserProfileDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
        canonicalOfiAxisProfiles = canonicalOfiAxisProfiles
    )
    private val coachingSignalsSummaryService = CoachingSignalsSummaryService(
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
        canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
        dailyStatusService = dailyStatusService
    )
    private val connectiveTissueAnalysisService = ConnectiveTissueAnalysisService(
        context = context,
        exerciseDao = exerciseDao,
        workoutDao = workoutDao,
        dailyMetricDao = dailyMetricDao,
        initialUserProfileDao = initialUserProfileDao,
        dailyCheckInDao = dailyCheckInDao
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

    suspend fun calendarOfiByDate(startDate: String, endDate: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            analysisSummaryService.calendarOfiByDate(startDate, endDate)
        }

    suspend fun performanceTrendSummary(): PerformanceTrendSummary = withContext(Dispatchers.IO) {
        performanceTrendSummaryService.build()
    }

    suspend fun rebuildStrengthAnalysisFromRawHistory(): PerformanceTrendSummary = withContext(Dispatchers.IO) {
        strengthPosteriorCoordinator.scheduleDerivedResetRebuild()
        strengthPosteriorCoordinator.ensureCurrentRevision()
        performanceTrendSummaryService.build()
    }

    suspend fun connectiveTissueState(): TissueCurrentState = withContext(Dispatchers.IO) {
        connectiveTissueAnalysisService.build()
    }

    suspend fun coachingSignalsSummary(
        history: List<DailyFatigueResult>
    ): CoachingSignalsSummary =
        coachingSignalsSummaryService.build(history)

    fun entriesForDate(date: String): Flow<List<WorkoutEntryWithSets>> =
        readQueryService.entriesForDate(date)

    suspend fun exportRecordsBackup(
        uri: Uri,
        onReportChanged: (DataTransferReport) -> Unit = {}
    ): RecordCsvTransferResult = withContext(Dispatchers.IO) {
        BackupExportService(
            context = context,
            workoutDao = workoutDao,
            dailyMetricDao = dailyMetricDao,
            dailyCheckInDao = dailyCheckInDao,
            smashSpeedDao = smashSpeedDao,
            exerciseDao = exerciseDao,
            exerciseRoleRelationDao = exerciseRoleRelationDao,
            initialUserProfileDao = initialUserProfileDao,
            runtimeExerciseMetadataDao = runtimeExerciseMetadataDao,
            exerciseMetadataUserOverrideDao = exerciseMetadataUserOverrideDao,
            appMetaDao = appMetaDao,
            strengthPosteriorDao = strengthPosteriorDao,
            programDao = programDao,
            exerciseIdentityMigrationIssueDao = exerciseIdentityMigrationIssueDao,
            canonicalRuntimeMetadataCatalog = canonicalRuntimeMetadataCatalog,
            canonicalMetadataRepository = canonicalMetadataRepository,
            workoutSourceIdentityProvider = workoutSourceIdentityProvider,
            reportStore = dataTransferReportStore,
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        ).export(uri, onReportChanged)
    }

    suspend fun latestDataTransferReport(): DataTransferReport? = withContext(Dispatchers.IO) {
        dataTransferReportStore.latest()
    }

    suspend fun recentDataTransferReports(): List<DataTransferReport> = withContext(Dispatchers.IO) {
        dataTransferReportStore.recent()
    }

    suspend fun saveDataTransferReport(uri: Uri, report: DataTransferReport) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(report.detailText())
        } ?: error("진단 보고서 파일을 열 수 없습니다.")
    }

    suspend fun importRecordsBackup(
        uri: Uri,
        onReportChanged: (DataTransferReport) -> Unit = {}
    ): RecordCsvTransferResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                "The restore file is empty or unavailable."
            )
        val service = backupImportService()
        when (RecordCsvBackupRestore.parse(text)) {
            is RecordCsvImportData.DailyTimeseries -> service.import(context, uri, onReportChanged)
            is RecordCsvImportData.Restore -> {
                val prepared = service.prepare(context, uri)
                val plan = service.plan(
                    prepared,
                    WorkoutRestoreMode.APPEND_TO_CURRENT,
                    ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES
                )
                service.execute(prepared, plan, onReportChanged)
            }
        }
    }

    suspend fun prepareRecordsRestore(uri: Uri): BackupRestorePreparation = withContext(Dispatchers.IO) {
        cancelPendingRecordsRestore()
        val prepared = backupImportService().prepare(context, uri)
        pendingBackupRestore = prepared
        BackupRestorePreparation(
            hasOverlappingWorkoutDates = prepared.prepared.overlappingDates.isNotEmpty(),
            impact = prepared.prepared.baseImpact
        )
    }

    suspend fun planRecordsRestore(
        workoutMode: WorkoutRestoreMode,
        exerciseMode: ExerciseListRestoreMode
    ): BackupRestoreImpact = withContext(Dispatchers.IO) {
        val prepared = checkNotNull(pendingBackupRestore) { "No prepared backup restore is pending." }
        val plan = backupImportService().plan(prepared, workoutMode, exerciseMode)
        pendingBackupRestorePlan = plan
        plan.impact
    }

    suspend fun confirmRecordsRestore(
        onReportChanged: (DataTransferReport) -> Unit = {}
    ): RecordCsvTransferResult = withContext(Dispatchers.IO) {
        val prepared = checkNotNull(pendingBackupRestore) { "No prepared backup restore is pending." }
        val plan = checkNotNull(pendingBackupRestorePlan) { "No confirmed backup restore plan is pending." }
        try {
            backupImportService().execute(prepared, plan, onReportChanged)
        } finally {
            cancelPendingRecordsRestore()
        }
    }

    fun cancelPendingRecordsRestore() {
        pendingBackupRestore = null
        pendingBackupRestorePlan = null
    }

    private fun backupImportService(): BackupImportService = BackupImportService(
        restoreImporter = backupRestoreImportService::importRestoreCsv,
        restorePlanImporter = backupRestoreImportService::importRestorePlan,
        restorePlanner = backupRestorePlanner,
        dailyTimeseriesImporter = dailyTimeseriesImportService::importDailyTimeseriesCsv,
        canonicalizer = BackupRestoreCanonicalizer(legacyExerciseImportMapper),
        canonicalExercises = { SeedData.exactExerciseMetadataByStableKey(context) },
        reportStore = dataTransferReportStore
    )

    fun entryCount(date: String): Flow<Int> =
        readQueryService.entryCount(date)

    fun plannedSetCount(date: String): Flow<Int> =
        readQueryService.plannedSetCount(date)

    fun confirmedSetCount(date: String): Flow<Int> =
        readQueryService.confirmedSetCount(date)

    fun dailySummaries(startDate: String, endDate: String): Flow<List<DailyRecordSummary>> =
        readQueryService.dailySummaries(startDate, endDate)

    fun confirmedExerciseDates(
        startDate: String,
        endDate: String,
        query: String
    ): Flow<List<String>> =
        readQueryService.confirmedExerciseDates(startDate, endDate, query)

    fun programItems(programId: Long): Flow<List<TrainingProgramItem>> =
        programPlanService.programItems(programId)

    fun programItemSets(programId: Long): Flow<List<TrainingProgramItemSet>> =
        programPlanService.programItemSets(programId)

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
        val semanticRevision = exerciseMetadataReconciliationService.markRequiredIfNeeded()

        if (exerciseSeedVersion < EXERCISE_SEED_VERSION || exerciseDao.countExercises() == 0) {
            upsertSeedExercises()
            seedExerciseRoleRelations()
            appMetaDao.upsert(
                AppMeta(
                    key = META_EXERCISE_SEED_VERSION,
                    value = EXERCISE_SEED_VERSION.toString()
                )
            )
        }
        exerciseMetadataReconciliationService.reconcileIfRequired(semanticRevision)
        workoutSourceIdentityProvider.backfillMissingWorkoutSourceIds()
        repairCustomExerciseStableKeys()
        refreshExerciseAnalysisMetadata()
        repairLegacyProgramStableKeys()

        if (programSeedVersion < PROGRAM_SEED_VERSION) {
            seedMissingPrograms()
            appMetaDao.upsert(
                AppMeta(
                    key = META_PROGRAM_SEED_VERSION,
                    value = PROGRAM_SEED_VERSION.toString()
                )
            )
        }

        val strengthAnalysisLifecycle = strengthPosteriorCoordinator.ensureCurrentRevision()

        logDebugSummary()
        strengthAnalysisLifecycle
    }

    private suspend fun upsertSeedExercises() {
        SeedData.exactExerciseMetadataByStableKey(context).values.forEach { seed ->
            val historyOnly = canonicalMetadataRepository.identity(seed.stableKey)?.historyOnly == true
            val existing = exerciseDao.findByStableKey(seed.stableKey)
            if (existing == null) {
                exerciseDao.insertExercise(seed)
            } else if (historyOnly) {
                ExerciseStableKeyPolicy.mergeSeed(existing, seed)?.let { merged ->
                    exerciseDao.updateExercise(merged.copy(isActive = false))
                }
            } else {
                ExerciseStableKeyPolicy.mergeSeed(existing, seed)?.let { merged ->
                    exerciseDao.updateExercise(merged)
                }
            }
        }
    }

    private suspend fun seedExerciseRoleRelations() {
        val stableKeys = exerciseDao.allExercises().mapTo(mutableSetOf(), Exercise::stableKey)
        exerciseRoleRelationAssetLoader.load(stableKeys)
        val trainingRoles = exerciseRoleRelationAssetLoader.trainingRelations()
        val slotCapabilities = exerciseRoleRelationAssetLoader.programSlotCapabilityRelations()
        db.withTransaction {
            canonicalMetadataRepository.identities().forEach { identity ->
                exerciseRoleRelationDao.deleteTrainingRoles(identity.stableKey)
                exerciseRoleRelationDao.deleteProgramSlotCapabilities(identity.stableKey)
            }
            exerciseRoleRelationDao.upsertTrainingRoles(trainingRoles)
            exerciseRoleRelationDao.upsertProgramSlotCapabilities(slotCapabilities)
        }
    }

    suspend fun exerciseEditorData(exerciseStableKey: String?): ExerciseRuntimeMetadataEditorData =
        withContext(Dispatchers.IO) {
            exerciseMetadataEditorService.exerciseEditorData(exerciseStableKey)
        }

    suspend fun saveExerciseEditor(data: ExerciseRuntimeMetadataEditorData): String =
        withContext(Dispatchers.IO) {
            exerciseMetadataEditorService.saveExerciseEditor(data)
        }

    suspend fun resetExerciseMetadataOverride(exerciseStableKey: String): Boolean =
        withContext(Dispatchers.IO) {
            exerciseMetadataEditorService.resetExerciseMetadataOverride(exerciseStableKey)
        }

    suspend fun resolveRuntimeMetadata(exercise: Exercise): RuntimeExerciseMetadata =
        withContext(Dispatchers.IO) {
            exerciseMetadataEditorService.resolveRuntimeMetadata(exercise)
        }

    suspend fun resolvedRuntimeMetadataByExerciseStableKey(): Map<String, RuntimeExerciseMetadata> =
        withContext(Dispatchers.IO) {
            exerciseMetadataEditorService.resolvedRuntimeMetadataByExerciseStableKey()
        }

    private suspend fun repairCustomExerciseStableKeys() {
        exerciseDao.customExercisesWithBlankStableKey().forEach { exercise ->
            exerciseDao.updateExercise(
                exercise.copy(stableKey = exerciseMetadataEditorService.uniqueUserExerciseStableKey())
            )
        }
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        exerciseMetadataEditorService.resolvedRuntimeMetadataCatalog(exercises)

    suspend fun setExerciseActive(exerciseStableKey: String, active: Boolean) = withContext(Dispatchers.IO) {
        exerciseMetadataEditorService.setExerciseActive(exerciseStableKey, active)
    }

    suspend fun deleteExerciseIfUnused(exerciseStableKey: String): ExerciseDeleteResult = withContext(Dispatchers.IO) {
        exerciseMetadataEditorService.deleteExerciseIfUnused(exerciseStableKey)
    }

    suspend fun addWorkoutEntry(date: String, exerciseStableKey: String): Long = withContext(Dispatchers.IO) {
        recordMutationService.addWorkoutEntry(date, exerciseStableKey)
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
        exerciseStableKey: String
    ) = withContext(Dispatchers.IO) {
        programPlanService.addExerciseToProgram(programId, weekNumber, dayOfWeek, exerciseStableKey)
    }

    suspend fun updateProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programPlanService.updateProgramItem(item)
    }

    suspend fun deleteProgramItem(item: TrainingProgramItem) = withContext(Dispatchers.IO) {
        programPlanService.deleteProgramItem(item)
    }

    suspend fun recordRangeProgramSummary(
        firstDate: String,
        secondDate: String
    ): RecordRangeProgramSummary = withContext(Dispatchers.IO) {
        programPlanService.recordRangeProgramSummary(firstDate, secondDate)
    }

    suspend fun createProgramFromRecordRange(
        firstDate: String,
        secondDate: String,
        name: String
    ): Long = withContext(Dispatchers.IO) {
        programPlanService.createProgramFromRecordRange(firstDate, secondDate, name)
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
        mode: ProgramApplyMode
    ) = withContext(Dispatchers.IO) {
        programPlanService.applyProgramToDates(programId, startDate, mode)
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

    @Suppress("unused")
    private suspend fun legacyUpsertRestoredExercise(
        row: RestoreExerciseRow,
        seedByStableKey: Map<String, Exercise>,
        restoredRuntimeOverrideKeys: Set<String>
    ): Boolean {
        val stableKey = row.stableKey.trim()
        require(stableKey.isNotBlank()) { "Restore exercise stableKey must be nonblank." }
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
        val historyOnly = seedByStableKey[ExerciseMetadataOverrideBackupMapper.overrideKey(stableKey)]
            ?.planningEligibility == "HISTORY_ONLY"
        val restored = if (ExerciseSeedMetadataPolicy.isBuiltInStableKey(stableKey, seedByStableKey) && !hasRestoredOverride) {
            ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(csvExercise, seedByStableKey)
        } else {
            csvExercise
        }.let { exercise ->
            if (historyOnly) exercise.copy(isActive = false, planningEligibility = "HISTORY_ONLY") else exercise
        }
        val existing = exerciseDao.findByStableKey(stableKey)
        if (existing == null) {
            exerciseDao.insertExercise(restored)
        } else {
            val updated = restored.copy(
                stableKey = existing.stableKey,
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
            habitualTrainingIntensity = values["habitualTrainingIntensity"]
                ?.trim()
                ?.uppercase()
                ?.takeIf { it in habitualTrainingIntensityKeys },
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
        stableKey: String,
        seedByStableKey: Map<String, Exercise> = emptyMap()
    ): Exercise {
        require(stableKey.isNotBlank()) { "Restore exercise stableKey must not be blank." }
        exerciseDao.findByStableKey(stableKey)?.let { existing -> return existing }
        stableKey
            .let { key -> seedByStableKey[key.trim().lowercase()] }
            ?.let { seed ->
                exerciseDao.insertExercise(seed)
                return exerciseDao.findByStableKey(seed.stableKey) ?: seed
            }
        val mapped = Exercise(
            name = name,
            category = category,
            stableKey = stableKey,
            activityKind = ActivityKind.UNKNOWN.name,
            planningEligibility = PlanningEligibility.UNKNOWN.name,
            metadataConfidence = MetadataConfidence.NEEDS_REVIEW.name,
            isCustom = true,
            needsReview = true
        )
        exerciseDao.insertExercise(mapped)
        return exerciseDao.findByStableKey(mapped.stableKey) ?: mapped
    }

    internal suspend fun seedMissingPrograms(
        seeds: List<ProgramSeed> = SeedData.programs(context)
    ) {
        val exercisesByStableKey = exerciseDao.allExercises()
            .associateBy(Exercise::stableKey)
        seeds.forEach { seed ->
            val programName = seed.displayName()
            if (programDao.findProgramByStableKey(seed.key) != null) return@forEach
            if (programDao.findProgramTombstone(seed.key) != null) return@forEach

            val resolvedItems = seed.items.map { itemSeed ->
                itemSeed to exercisesByStableKey[itemSeed.exerciseStableKey]
            }
            val unresolved = resolvedItems.firstOrNull { (itemSeed, exercise) ->
                itemSeed.exerciseStableKey.isBlank() ||
                    exercise?.stableKey.isNullOrBlank() ||
                    canonicalMetadataRepository.identity(itemSeed.exerciseStableKey)?.selectable != true
            }
            if (unresolved != null) {
                if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    Log.d(
                        "ProgramSeed",
                        "Skipped program '$programName': unresolved '${unresolved.first.exerciseName}' " +
                            "(${unresolved.first.exerciseStableKey})"
                    )
                }
                return@forEach
            }

            db.withTransaction {
                val programId = programDao.insertProgram(
                    TrainingProgram(
                        stableKey = seed.key,
                        name = programName,
                        durationDays = seed.durationDays
                    )
                )
                programDao.insertProgramItems(
                    resolvedItems.map { (itemSeed, resolvedExercise) ->
                        val exercise = requireNotNull(resolvedExercise)
                        TrainingProgramItem(
                            programId = programId,
                            weekNumber = itemSeed.weekNumber,
                            dayOfWeek = itemSeed.dayOfWeek,
                            orderIndex = itemSeed.orderIndex,
                            exerciseStableKey = exercise.stableKey,
                            exerciseName = exercise.name,
                            category = exercise.category,
                            restSeconds = itemSeed.restSeconds,
                            prescription = itemSeed.prescription,
                            setCount = itemSeed.setCount.coerceAtLeast(1),
                            reps = itemSeed.reps,
                            weightKg = itemSeed.weightKg,
                            seconds = itemSeed.seconds
                        )
                    }
                )
            }
        }
    }

    internal suspend fun repairLegacyProgramStableKeys() {
        if (appMetaDao.intValue(META_PROGRAM_STABLE_KEY_REPAIR_VERSION) >= PROGRAM_STABLE_KEY_REPAIR_VERSION) {
            return
        }
        db.withTransaction {
            val legacyPrograms = programDao.allPrograms()
                .filter { program -> program.stableKey.startsWith(ProgramStableKeyPolicy.LEGACY_PREFIX) }
            SeedData.programs(context).forEach { seed ->
                if (programDao.findProgramByStableKey(seed.key) != null) return@forEach
                val matches = legacyPrograms.filter { program ->
                    program.name == seed.displayName() &&
                        program.durationDays == seed.durationDays &&
                        programDao.itemsForProgram(program.id).matchesSeedItems(seed.items)
                }
                if (matches.size == 1) {
                    programDao.updateProgram(matches.single().copy(stableKey = seed.key))
                }
            }
            appMetaDao.upsert(
                AppMeta(
                    key = META_PROGRAM_STABLE_KEY_REPAIR_VERSION,
                    value = PROGRAM_STABLE_KEY_REPAIR_VERSION.toString()
                )
            )
        }
    }

    private fun List<TrainingProgramItem>.matchesSeedItems(seeds: List<ProgramItemSeed>): Boolean {
        val ordered = sortedWith(
            compareBy(TrainingProgramItem::weekNumber)
                .thenBy(TrainingProgramItem::dayOfWeek)
                .thenBy(TrainingProgramItem::orderIndex)
                .thenBy(TrainingProgramItem::id)
        )
        val expected = seeds.sortedWith(
            compareBy(ProgramItemSeed::weekNumber)
                .thenBy(ProgramItemSeed::dayOfWeek)
                .thenBy(ProgramItemSeed::orderIndex)
        )
        return ordered.size == expected.size && ordered.zip(expected).all { (item, seed) ->
            item.weekNumber == seed.weekNumber &&
                item.dayOfWeek == seed.dayOfWeek &&
                item.orderIndex == seed.orderIndex &&
                item.exerciseStableKey == seed.exerciseStableKey &&
                item.category == seed.category &&
                item.restSeconds == seed.restSeconds &&
                item.prescription == seed.prescription &&
                item.setCount == seed.setCount.coerceAtLeast(1) &&
                item.reps == seed.reps &&
                kotlin.math.abs(item.weightKg - seed.weightKg) < 0.001 &&
                item.seconds == seed.seconds
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
            if (seedBacked == exercise) return@forEach
            val mapped = seedBacked
            if (mapped != exercise) {
                exerciseDao.updateExercise(mapped)
            }
        }
    }

    private fun seedExercisesByStableKey(): Map<String, Exercise> =
        SeedData.exactExerciseMetadataByStableKey(context)

    private fun ProgramSeed.displayName(): String =
        when (name) {
            "배드민턴 웨이트 보조 4주" -> "배드민턴 보조 4주"
            else -> name
        }

    private companion object {
        const val EXERCISE_SEED_VERSION = 9
        const val PROGRAM_SEED_VERSION = 1
        const val PROGRAM_STABLE_KEY_REPAIR_VERSION = 1
        const val META_EXERCISE_SEED_VERSION = "exercise_seed_version"
        const val META_PROGRAM_SEED_VERSION = "program_seed_version"
        const val META_PROGRAM_STABLE_KEY_REPAIR_VERSION = "program_stable_key_repair_version"
    }
}
