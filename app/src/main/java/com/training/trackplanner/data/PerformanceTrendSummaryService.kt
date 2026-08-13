package com.training.trackplanner.data

import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.lab.CheckInMetricSeriesBuilder
import com.training.trackplanner.analysis.lab.SmashSpeedMetricSeriesBuilder
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthPerformanceSummary
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthPerformanceSummaryBuilder
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.trends.AnalysisChartTemporalPolicy
import com.training.trackplanner.analysis.trends.PerformanceTrendEngine
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.format.DateTimeFormatter

internal class PerformanceTrendSummaryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val smashSpeedDao: SmashSpeedDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val canonicalCoreCatalog: com.training.trackplanner.analysis.core.CanonicalCoreCatalog =
        com.training.trackplanner.analysis.core.CanonicalCoreCatalog.EMPTY,
    private val badmintonObjectiveCatalog: com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog =
        com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog.EMPTY,
    private val strengthPosteriorDao: StrengthPosteriorDao,
    private val strengthPerformanceRegistry: StrengthPerformanceRegistry,
    private val appMetaDao: AppMetaDao
) {
    suspend fun build(): PerformanceTrendSummary {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        val entries = workoutDao.entriesWithSetsUntil(todayString)
        val runtimeMetadataCatalog = resolvedRuntimeMetadataCatalog(exercises)
        val base = PerformanceTrendEngine(
            runtimeMetadataCatalog = runtimeMetadataCatalog,
            canonicalCoreCatalog = canonicalCoreCatalog,
            badmintonObjectiveCatalog = badmintonObjectiveCatalog
        ).analyze(
            today = today,
            exercises = exercises,
            entriesWithSets = entries,
            dailyMetrics = dailyMetrics
        )
        val checkInSeries = CheckInMetricSeriesBuilder.build(
            checkIns = dailyCheckInDao.between(MIN_DATE, todayString),
            dailyMetrics = dailyMetrics
        )
        val smashSpeedSeries = SmashSpeedMetricSeriesBuilder.build(
            records = smashSpeedDao.between(MIN_DATE, todayString)
        )
        val strengthAndMuscleSeries = StrengthAndMuscleMetricSeriesBuilder.build(
            entriesWithSets = entries,
            exercises = exercises,
            runtimeMetadataCatalog = runtimeMetadataCatalog,
            dailyMetrics = dailyMetrics
        )
        val initialProfile = initialUserProfileDao.profile()
        val currentBodyWeightKg = dailyMetrics.asReversed().firstNotNullOfOrNull(DailyMetric::bodyWeightKg)
            ?: initialProfile?.bodyWeightKg
        val currentRevision = strengthPosteriorDao.revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
        val rebuildMarker = appMetaDao.value(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY)
        val activeRevision = currentRevision?.takeIf { revision ->
            revision.status == StrengthModelRevisionPolicy.STATUS_ACTIVE &&
                rebuildMarker != null &&
                StrengthModelRevisionPolicy.isCompatible(revision)
        }
        val failedEvent = currentRevision?.revisionKey
            ?.let { revisionKey -> strengthPosteriorDao.eventsForRevision(revisionKey) }
            ?.firstOrNull { event -> event.status == StrengthPosteriorEventProcessor.STATUS_FAILED }
        val lifecycle = when {
            activeRevision != null ->
                StrengthAnalysisLifecycleResult(StrengthAnalysisLifecycleStatus.CURRENT)
            currentRevision?.status == StrengthModelRevisionPolicy.STATUS_FAILED ->
                StrengthAnalysisLifecycleResult(
                    StrengthAnalysisLifecycleStatus.REBUILD_FAILED,
                    failedEvent?.errorCode ?: currentRevision.errorCode ?: "REBUILD_FAILED",
                    buildString {
                        failedEvent?.sessionDate?.let { appendLine("실패한 운동일: $it") }
                        appendLine(
                            "오류 유형: ${failedEvent?.errorCode ?: currentRevision.errorCode ?: "REBUILD_FAILED"}"
                        )
                        append(
                            failedEvent?.errorMessage
                                ?: currentRevision.errorMessage
                                ?: "계산기가 추가 오류 메시지를 남기지 않았습니다."
                        )
                    }
                )
            currentRevision != null && !StrengthModelRevisionPolicy.isCompatible(currentRevision) ->
                StrengthAnalysisLifecycleResult(
                    StrengthAnalysisLifecycleStatus.REBUILD_FAILED,
                    "INCOMPATIBLE_CURRENT_REVISION",
                    "현재 저장된 근력 분석 모델이 앱의 canonical 모델과 호환되지 않습니다."
                )
            else -> StrengthAnalysisLifecycleResult(StrengthAnalysisLifecycleStatus.REBUILDING)
        }
        val revisionKey = activeRevision?.revisionKey
        val revisionEvents = revisionKey?.let { strengthPosteriorDao.eventsForRevision(it) }
            ?: emptyList()
        val revisionHistory = revisionKey?.let { strengthPosteriorDao.historyForRevision(it) }
            ?: emptyList()
        val revisionEvidence = revisionKey?.let { strengthPosteriorDao.evidenceForRevision(it) }
            ?: emptyList()
        val revisionCurves = if (revisionKey != null) {
            strengthPosteriorDao.allCurvePosteriors()
                .filter { it.curveSubjectKey.startsWith("$revisionKey|") }
                .map { entity ->
                    entity.copy(
                        curveSubjectKey = StrengthModelRevisionPolicy.originalCurveSubjectKey(
                            revisionKey,
                            entity.curveSubjectKey
                        )
                    )
                }
        } else emptyList()
        val revisionLocalStates = if (revisionKey != null) strengthPosteriorDao.localStates(revisionKey) else emptyList()
        val revisionLocalHistory = if (revisionKey != null) strengthPosteriorDao.localHistory(revisionKey) else emptyList()
        val revisionProxyHistory = if (revisionKey != null) strengthPosteriorDao.proxyHistory(revisionKey) else emptyList()
        val persistentStrengthSummary = PersistentStrengthPerformanceSummaryBuilder.build(
            registry = strengthPerformanceRegistry,
            modelState = revisionKey?.let(StrengthModelRevisionPolicy::modelInstanceKey)
                ?.let { key -> strengthPosteriorDao.modelState(key) },
            history = revisionHistory,
            events = revisionEvents,
            evidence = revisionEvidence,
            curvePosteriors = revisionCurves,
            currentBodyWeightKg = currentBodyWeightKg,
            bootstrapProvenance = appMetaDao.value(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY),
            backupRestorationProvenance = appMetaDao.value(StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY),
            activeRevision = activeRevision,
            lifecycle = lifecycle,
            localExerciseStateCount = revisionLocalStates.size,
            proxyTransferCount = revisionProxyHistory.count { row -> row.applied },
            supersededRevisionCount = strengthPosteriorDao.allRevisions()
                .count { it.status == StrengthModelRevisionPolicy.STATUS_SUPERSEDED },
            localHistory = revisionLocalHistory,
            proxyTransfers = revisionProxyHistory
        )
        val persistentStrengthMetricSeries = persistentStrengthPosteriorMetricSeries(persistentStrengthSummary)
        return base.copy(
            metricSeries = base.metricSeries + checkInSeries + smashSpeedSeries +
                strengthAndMuscleSeries + persistentStrengthMetricSeries,
            proxyPerformanceSummary = null,
            persistentStrengthPerformanceSummary = persistentStrengthSummary
        )
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)

    private companion object {
        const val MIN_DATE = "0001-01-01"
    }
}

internal fun persistentStrengthPosteriorMetricSeries(
    summary: PersistentStrengthPerformanceSummary
): Map<TrendMetricId, List<TrendDataPoint>> = summary.targets.mapNotNull { target ->
    val metricId = when (target.targetKey) {
        StrengthPerformanceRegistry.BENCH_PRESS.value -> TrendMetricId.BENCH_PRESS_E1RM
        StrengthPerformanceRegistry.BACK_SQUAT.value -> TrendMetricId.SQUAT_E1RM
        StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT.value -> TrendMetricId.DEADLIFT_E1RM
        else -> null
    } ?: return@mapNotNull null
    val weeklyPosteriorMedian = target.history
        .mapNotNull { point ->
            point.posteriorMedianKg
                ?.takeIf(Double::isFinite)
                ?.let { value -> AnalysisChartTemporalPolicy.weekStart(point.sessionDate) to value }
        }
        .groupBy({ (week, _) -> week }, { (_, value) -> value })
        .toSortedMap()
        .map { (week, values) -> TrendDataPoint(week, values.last()) }
    metricId to weeklyPosteriorMedian
}.toMap()
