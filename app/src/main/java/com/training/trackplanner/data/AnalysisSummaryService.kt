package com.training.trackplanner.data

import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.fatigue.DailyCanonicalStrengthPosterior
import com.training.trackplanner.analysis.fatigue.DailyFatigueResult
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal class AnalysisSummaryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
    private val canonicalOfiAxisProfiles: Map<String, CanonicalOfiAxisProfile>,
    private val strengthPosteriorDao: StrengthPosteriorDao,
    private val strengthPerformanceRegistry: StrengthPerformanceRegistry,
    private val appMetaDao: AppMetaDao
) {
    suspend fun fatigueAnalysisHistory(days: Int = 28 * 7): List<DailyFatigueResult> {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        return DailyFatigueCalculator(
            resolvedRuntimeMetadataCatalog(exercises),
            canonicalOfiAxisProfiles,
            dailyCanonicalStrengthPosterior()
        ).calculateSeries(
            endDate = today,
            days = days.coerceIn(1, 28 * 7),
            exercises = exercises,
            entriesWithSets = workoutDao.entriesWithSetsUntil(todayString),
            initialProfile = initialUserProfileDao.profile(),
            dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        )
    }

    suspend fun calendarOfiByDate(
        startDate: String,
        endDate: String
    ): Map<String, Int> {
        val start = LocalDate.parse(startDate)
        val requestedEnd = LocalDate.parse(endDate)
        if (requestedEnd < start) return emptyMap()

        val today = SystemAnalysisDateProvider().today()
        if (start > today) return emptyMap()

        val effectiveEnd = minOf(requestedEnd, today)
        val effectiveEndString = effectiveEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val days = ChronoUnit.DAYS.between(start, effectiveEnd).toInt() + 1
        return DailyFatigueCalculator(
            resolvedRuntimeMetadataCatalog(exercises),
            canonicalOfiAxisProfiles,
            dailyCanonicalStrengthPosterior()
        ).calculateSeries(
            endDate = effectiveEnd,
            days = days,
            exercises = exercises,
            entriesWithSets = workoutDao.entriesWithSetsUntil(effectiveEndString),
            initialProfile = initialUserProfileDao.profile(),
            dailyMetrics = dailyMetricDao.metricsUntil(effectiveEndString)
        ).associate { result ->
            result.state.date.format(DateTimeFormatter.ISO_LOCAL_DATE) to
                result.state.overallFatigueIndex
        }
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)

    private suspend fun dailyCanonicalStrengthPosterior(): DailyCanonicalStrengthPosterior {
        val revision = strengthPosteriorDao.revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
        val history = if (
            revision?.status == StrengthModelRevisionPolicy.STATUS_ACTIVE &&
            StrengthModelRevisionPolicy.isCompatible(revision) &&
            appMetaDao.value(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY) != null
        ) {
            strengthPosteriorDao.historyForRevision(revision.revisionKey)
        } else {
            emptyList()
        }
        return dailyCanonicalStrengthPosterior(history, strengthPerformanceRegistry)
    }
}

internal fun dailyCanonicalStrengthPosterior(
    history: List<StrengthPosteriorHistoryEntity>,
    registry: StrengthPerformanceRegistry
): DailyCanonicalStrengthPosterior {
    val targetKeys = setOf(
        StrengthPerformanceRegistry.BENCH_PRESS,
        StrengthPerformanceRegistry.BACK_SQUAT,
        StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT,
        StrengthPerformanceRegistry.WEIGHTED_PULL_UP
    )
    val targets = targetKeys.mapNotNull(registry::target).associateBy { it.targetKey.value }
    val valuesByDate = mutableMapOf<LocalDate, MutableMap<String, Double>>()
    history.sortedWith(compareBy(StrengthPosteriorHistoryEntity::sessionDate, StrengthPosteriorHistoryEntity::createdAt))
        .forEach { point ->
            val target = targets[point.targetKey] ?: return@forEach
            val date = runCatching { LocalDate.parse(point.sessionDate) }.getOrNull() ?: return@forEach
            val value = point.posteriorMedian?.takeIf { it.isFinite() && it > 0.0 } ?: return@forEach
            target.anchorStableKeys.forEach { stableKey ->
                valuesByDate.getOrPut(date, ::mutableMapOf)[stableKey] = value
            }
        }
    return DailyCanonicalStrengthPosterior(
        canonicalExerciseStableKeys = targets.values.flatMap { it.anchorStableKeys }.toSet(),
        valuesByDate = valuesByDate
    )
}
