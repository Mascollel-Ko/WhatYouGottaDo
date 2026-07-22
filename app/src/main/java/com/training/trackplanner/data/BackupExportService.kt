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
    private val strengthPosteriorDao: StrengthPosteriorDao
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
            posteriorEvidence = posteriorEvidence
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
            posteriorEvidenceCount = posteriorEvidence.size
        )
    }
}
