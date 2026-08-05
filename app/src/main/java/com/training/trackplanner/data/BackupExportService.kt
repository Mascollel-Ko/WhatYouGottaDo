package com.training.trackplanner.data

import android.content.Context
import android.net.Uri

internal class BackupExportService(
    private val context: Context,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val smashSpeedDao: SmashSpeedDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseRoleRelationDao: ExerciseRoleRelationDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val appMetaDao: AppMetaDao,
    private val strengthPosteriorDao: StrengthPosteriorDao,
    private val programDao: ProgramDao,
    private val exerciseIdentityMigrationIssueDao: ExerciseIdentityMigrationIssueDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val reportStore: DataTransferReportStore,
    private val appVersion: String
) {
    suspend fun export(
        uri: Uri,
        onReportChanged: (DataTransferReport) -> Unit = {}
    ): RecordCsvTransferResult {
        val session = DataTransferReportSession(
            store = reportStore,
            operation = DataTransferOperation.BACKUP,
            fileDisplayName = uri.lastPathSegment.orEmpty(),
            onChanged = onReportChanged
        )
        session.begin()
        try {
            session.stage(DataTransferStages.LOADING)
            val entriesWithSets = workoutDao.allEntriesWithSets()
            val entries = workoutDao.allEntries()
            val sets = workoutDao.allSets()
            val metrics = dailyMetricDao.allMetrics()
            val checkIns = dailyCheckInDao.all()
            val smashSpeeds = smashSpeedDao.all()
            val exercises = exerciseDao.allExercises()
            val trainingRoleRelations = exerciseRoleRelationDao.allTrainingRoles()
            val programSlotCapabilityRelations = exerciseRoleRelationDao.allProgramSlotCapabilities()
            val persistedRuntimeMetadata = runtimeExerciseMetadataDao.all()
                .map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
            val runtimeResolver = RuntimeExerciseMetadataResolver(
                canonicalRuntimeMetadataCatalog,
                persistedRuntimeMetadata
            )
            val runtimeMetadata = exercises.map(runtimeResolver::resolve)
            val profile = initialUserProfileDao.profile()
            val posteriorEvents = strengthPosteriorDao.allEvents()
            val posteriorHistory = strengthPosteriorDao.allHistory()
            val posteriorStates = strengthPosteriorDao.allModelStates()
            val curvePosteriors = strengthPosteriorDao.allCurvePosteriors()
            val posteriorEvidence = strengthPosteriorDao.allEvidence()
            val posteriorRevisions = strengthPosteriorDao.allRevisions()
            val programs = programDao.allPrograms()
            val programItems = programDao.allProgramItems()
            val programItemSets = programDao.allProgramItemSets()
            val programTombstones = programDao.allProgramTombstones()
            val migrationIssues = exerciseIdentityMigrationIssueDao.all()
            val posteriorLocalStates = posteriorRevisions.flatMap { revision ->
                strengthPosteriorDao.localStates(revision.revisionKey)
            }
            val posteriorLocalHistory = posteriorRevisions.flatMap { revision ->
                strengthPosteriorDao.localHistory(revision.revisionKey)
            }
            val posteriorProxyHistory = posteriorRevisions.flatMap { revision ->
                strengthPosteriorDao.proxyHistory(revision.revisionKey)
            }

            session.stage(DataTransferStages.PREFLIGHT)
            val preflight = BackupPreflightValidator.validate(
                exercises = exercises,
                workoutEntries = entries,
                workoutSets = sets,
                programs = programs,
                programItems = programItems,
                programItemSets = programItemSets,
                runtimeMetadata = runtimeMetadata,
                migrationIssues = migrationIssues
            )
            session.counts(
                preflight.entityCounts + mapOf(
                    "daily_metric" to metrics.size,
                    "daily_check_in" to checkIns.size,
                    "smash_speed" to smashSpeeds.size,
                    "runtime_metadata" to runtimeMetadata.size
                )
            )
            if (preflight.errors.isNotEmpty()) {
                throw DataTransferFailure(
                    session.finish(warnings = preflight.warnings, errors = preflight.errors)
                )
            }

            val programsById = programs.associateBy(TrainingProgram::id)
            val programItemsById = programItems.associateBy(TrainingProgramItem::id)
            val exercisesByKey = exercises.associateBy(Exercise::stableKey)
            val backupProgramItems = programItems.map { item ->
                val program = checkNotNull(programsById[item.programId])
                val exercise = checkNotNull(exercisesByKey[item.exerciseStableKey])
                ProgramBackupItem(
                    programStableKey = program.stableKey,
                    weekNumber = item.weekNumber,
                    dayOfWeek = item.dayOfWeek,
                    orderIndex = item.orderIndex,
                    exerciseStableKey = exercise.stableKey,
                    exerciseName = item.exerciseName,
                    category = item.category,
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
            }
            val backupProgramItemSets = programItemSets.map { set ->
                val item = checkNotNull(programItemsById[set.programItemId])
                val program = checkNotNull(programsById[item.programId])
                ProgramBackupItemSet(
                    programStableKey = program.stableKey,
                    weekNumber = item.weekNumber,
                    dayOfWeek = item.dayOfWeek,
                    orderIndex = item.orderIndex,
                    setIndex = set.setIndex,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    seconds = set.seconds
                )
            }
            val trainingRolesByKey = trainingRoleRelations.groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
            val capabilitiesByKey = programSlotCapabilityRelations.groupBy(
                ExerciseProgramSlotCapabilityRelation::exerciseStableKey
            )
            val runtimeByKey = runtimeMetadata.associateBy(RuntimeExerciseMetadata::stableKey)
            val metadataSnapshots = exercises.flatMap { exercise ->
                ExerciseMetadataFieldPolicyRegistry.snapshot(
                    ExerciseMetadataSnapshotSource(
                        exercise = exercise,
                        runtimeMetadata = checkNotNull(runtimeByKey[exercise.stableKey]),
                        trainingRoles = trainingRolesByKey[exercise.stableKey].orEmpty()
                            .mapTo(sortedSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode),
                        programSlotCapabilities = capabilitiesByKey[exercise.stableKey].orEmpty()
                            .mapTo(sortedSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode)
                    )
                )
            }

            session.stage(DataTransferStages.SERIALIZING)
            val body = RecordCsvBackupRestore.buildRestoreCsv(
                entriesWithSets = entriesWithSets,
                metrics = metrics,
                exercises = exercises,
                initialProfile = profile,
                checkIns = checkIns,
                smashSpeeds = smashSpeeds,
                runtimeMetadata = runtimeMetadata,
                posteriorBootstrapMarker = appMetaDao.value(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY),
                posteriorEvents = posteriorEvents,
                posteriorHistory = posteriorHistory,
                posteriorModelStates = posteriorStates,
                curvePosteriors = curvePosteriors,
                posteriorEvidence = posteriorEvidence,
                posteriorRevisions = posteriorRevisions,
                posteriorLocalStates = posteriorLocalStates,
                posteriorLocalHistory = posteriorLocalHistory,
                posteriorProxyHistory = posteriorProxyHistory,
                programs = programs,
                programItems = backupProgramItems,
                programItemSets = backupProgramItemSets,
                programTombstones = programTombstones,
                trainingRoleRelations = trainingRoleRelations,
                programSlotCapabilityRelations = programSlotCapabilityRelations,
                metadataSnapshots = metadataSnapshots,
                includeProgramSnapshot = true
            )
            val dailyBackupCount = (
                metrics.map(DailyMetric::date) +
                    checkIns.filter { it.sleepHours != null || it.bodyWeightKg != null }.map(DailyCheckIn::date)
                ).distinct().size
            val manifestCounts = RecordCsvBackupRestore.backupEntityCounts(
                exerciseCount = exercises.size,
                dailyMetricCount = dailyBackupCount,
                dailyCheckInCount = checkIns.size,
                smashSpeedCount = smashSpeeds.size,
                profileCount = if (profile == null) 0 else 1,
                entryCount = entriesWithSets.count { it.sets.isNotEmpty() },
                setCount = sets.size,
                runtimeMetadataCount = runtimeMetadata.count { it.stableKey.isNotBlank() },
                programCount = programs.size,
                programItemCount = backupProgramItems.size,
                programItemSetCount = backupProgramItemSets.size,
                programTombstoneCount = programTombstones.size,
                metadataSnapshotCount = metadataSnapshots.size
            )
            val csv = RecordCsvBackupRestore.wrapWithManifest(
                body = body,
                appVersion = appVersion,
                exportedAt = System.currentTimeMillis(),
                entityCounts = manifestCounts
            )

            session.stage(DataTransferStages.WRITING)
            val output = try {
                context.contentResolver.openOutputStream(uri)
            } catch (error: Throwable) {
                throw exportFailure(
                    code = DataTransferDiagnosticCodes.BACKUP_FILE_OPEN_FAILED,
                    message = "백업 파일을 열 수 없습니다.",
                    stage = DataTransferStages.WRITING,
                    cause = error
                )
            } ?: throw exportFailure(
                code = DataTransferDiagnosticCodes.BACKUP_FILE_OPEN_FAILED,
                message = "백업 파일을 열 수 없습니다.",
                stage = DataTransferStages.WRITING
            )
            try {
                output.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(csv) }
            } catch (error: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw exportFailure(
                    code = DataTransferDiagnosticCodes.BACKUP_FILE_WRITE_FAILED,
                    message = "백업 파일을 쓰는 중 오류가 발생했습니다.",
                    stage = DataTransferStages.WRITING,
                    cause = error
                )
            }

            session.stage(DataTransferStages.POSTWRITE_VALIDATION)
            try {
                val written = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: error("작성한 백업 파일을 다시 열 수 없습니다.")
                val parsed = RecordCsvBackupRestore.parse(written)
                check(parsed is RecordCsvImportData.Restore && parsed.manifest != null)
            } catch (error: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw exportFailure(
                    code = DataTransferDiagnosticCodes.BACKUP_POSTWRITE_VALIDATION_FAILED,
                    message = "작성된 백업 파일의 무결성 재검증에 실패했습니다.",
                    stage = DataTransferStages.POSTWRITE_VALIDATION,
                    cause = error
                )
            }

            val result = RecordCsvTransferResult(
                format = "restore-v${RecordCsvBackupRestore.CURRENT_BACKUP_FORMAT_VERSION}",
                exerciseCount = exercises.size,
                dailyMetricCount = metrics.size,
                dailyCheckInCount = checkIns.size,
                smashSpeedCount = smashSpeeds.size,
                profileCount = if (profile != null) 1 else 0,
                entryCount = entries.size,
                setCount = sets.size,
                posteriorEventCount = posteriorEvents.size,
                posteriorHistoryCount = posteriorHistory.size,
                posteriorStateCount = posteriorStates.size,
                posteriorCurveCount = curvePosteriors.size,
                posteriorEvidenceCount = posteriorEvidence.size,
                posteriorRevisionCount = posteriorRevisions.size,
                posteriorLocalStateCount = posteriorLocalStates.size,
                posteriorLocalHistoryCount = posteriorLocalHistory.size,
                posteriorProxyTransferCount = posteriorProxyHistory.size,
                programCount = programs.size,
                programItemCount = backupProgramItems.size,
                programItemSetCount = backupProgramItemSets.size,
                programTombstoneCount = programTombstones.size,
                warningCount = preflight.warnings.size
            )
            session.finish(warnings = preflight.warnings)
            return result
        } catch (failure: DataTransferFailure) {
            throw failure
        } catch (error: Throwable) {
            val diagnostic = if (error is DataTransferFormatException) {
                DataTransferDiagnostic(
                    code = error.diagnosticCode,
                    messageKo = error.message,
                    stage = session.report.currentStage
                )
            } else {
                DataTransferDiagnostic(
                    code = if (session.report.currentStage == DataTransferStages.SERIALIZING) {
                        DataTransferDiagnosticCodes.BACKUP_SERIALIZATION_FAILED
                    } else {
                        DataTransferDiagnosticCodes.BACKUP_FILE_WRITE_FAILED
                    },
                    messageKo = error.message ?: "백업 작업 중 알 수 없는 오류가 발생했습니다.",
                    stage = session.report.currentStage
                )
            }
            throw DataTransferFailure(session.finish(errors = listOf(diagnostic)))
        }
    }

    private fun exportFailure(
        code: String,
        message: String,
        stage: String,
        cause: Throwable? = null
    ): DataTransferFormatException =
        DataTransferFormatException(code, if (cause?.message.isNullOrBlank()) message else "$message ${cause?.message}")
}
