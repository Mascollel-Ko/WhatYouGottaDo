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
    private val initialUserProfileDao: InitialUserProfileDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val appMetaDao: AppMetaDao,
    private val strengthPosteriorDao: StrengthPosteriorDao,
    private val programDao: ProgramDao
) {
    suspend fun export(uri: Uri): RecordCsvTransferResult {
        val entries = workoutDao.allEntriesWithSets()
        val metrics = dailyMetricDao.allMetrics()
        val checkIns = dailyCheckInDao.all()
        val smashSpeeds = smashSpeedDao.all()
        val exercises = exerciseDao.allExercises()
        val runtimeMetadata = runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        val profile = initialUserProfileDao.profile()
        val posteriorEvents = strengthPosteriorDao.allEvents()
        val posteriorHistory = strengthPosteriorDao.allHistory()
        val posteriorStates = strengthPosteriorDao.allModelStates()
        val curvePosteriors = strengthPosteriorDao.allCurvePosteriors()
        val posteriorEvidence = strengthPosteriorDao.allEvidence()
        val posteriorRevisions = strengthPosteriorDao.allRevisions()
        val programs = programDao.allPrograms()
        val programsById = programs.associateBy(TrainingProgram::id)
        val exercisesById = exercises.associateBy(Exercise::id)
        val programItems = programDao.allProgramItems().map { item ->
            val program = requireNotNull(programsById[item.programId]) {
                "Program backup cannot export orphan item ${item.id}."
            }
            val exercise = requireNotNull(exercisesById[item.exerciseId]) {
                "Program backup cannot resolve exercise ${item.exerciseId} for item ${item.id}."
            }
            require(exercise.stableKey.isNotBlank()) {
                "Program backup cannot export item ${item.id} with a blank exercise stable key."
            }
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
        val programTombstones = programDao.allProgramTombstones()
        val posteriorLocalStates = posteriorRevisions.flatMap { revision ->
            strengthPosteriorDao.localStates(revision.revisionKey)
        }
        val posteriorLocalHistory = posteriorRevisions.flatMap { revision ->
            strengthPosteriorDao.localHistory(revision.revisionKey)
        }
        val posteriorProxyHistory = posteriorRevisions.flatMap { revision ->
            strengthPosteriorDao.proxyHistory(revision.revisionKey)
        }
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = entries,
            metrics = metrics,
            exercises = ExerciseMetadataOverrideBackupMapper.exportExercises(
                exercises = exercises,
                seedByStableKey = SeedData.exactExerciseMetadataByStableKey(context),
                runtimeMetadata = runtimeMetadata
            ),
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
            programItems = programItems,
            programTombstones = programTombstones,
            includeProgramSnapshot = true
        )
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(csv)
        } ?: error("백업 파일을 열 수 없습니다.")
        return RecordCsvTransferResult(
            format = "restore",
            exerciseCount = exercises.size,
            dailyMetricCount = metrics.size,
            dailyCheckInCount = checkIns.size,
            smashSpeedCount = smashSpeeds.size,
            profileCount = if (profile != null) 1 else 0,
            entryCount = entries.size,
            setCount = entries.sumOf { item -> item.sets.size },
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
            programItemCount = programItems.size,
            programTombstoneCount = programTombstones.size
        )
    }
}
