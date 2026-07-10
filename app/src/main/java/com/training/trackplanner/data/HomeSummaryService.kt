package com.training.trackplanner.data

import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import com.training.trackplanner.analysis.fatigue.FatigueLabelResolver
import com.training.trackplanner.analysis.fatigue.HomeFatigueCardSummaryFactory
import com.training.trackplanner.analysis.fatigue.HomeMiniChartSeriesBuilder
import com.training.trackplanner.analysis.fatigue.HomeTodaySummaryState
import com.training.trackplanner.analysis.fatigue.MiniTrendPoint
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class HomeSummaryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val runtimeExerciseMetadataDao: RuntimeExerciseMetadataDao,
    private val canonicalRuntimeMetadataCatalog: RuntimeExerciseMetadataCatalog
) {
    suspend fun build(todayStatus: PhaseAwareTodayStatus? = null): HomeTodaySummaryState = withContext(Dispatchers.IO) {
        val today = SystemAnalysisDateProvider().today()
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val exercises = exerciseDao.allExercises()
        val runtimeMetadataCatalog = resolvedRuntimeMetadataCatalog(exercises)
        val entries = workoutDao.entriesWithSetsUntil(todayString)
        val dailyMetrics = dailyMetricDao.metricsUntil(todayString)
        val initialProfile = initialUserProfileDao.profile()
        val calculator = DailyFatigueCalculator(runtimeMetadataCatalog)
        val results = calculator.calculateSeries(
            endDate = today,
            days = 7,
            exercises = exercises,
            entriesWithSets = entries,
            initialProfile = initialProfile,
            dailyMetrics = dailyMetrics
        )
        val todayEntries = entries.filter { it.entry.date == todayString }
        val confirmedSetCount = todayEntries.sumOf { item -> item.sets.count { it.confirmed } }
        val unconfirmedSetCount = todayEntries.sumOf { item -> item.sets.count { !it.confirmed } }
        val todayState = results.last().state
        val preWorkoutState = calculator.calculate(
            targetDate = today,
            exercises = exercises,
            entriesWithSets = entries.filterNot { it.entry.date == todayString },
            initialProfile = initialProfile,
            dailyMetrics = dailyMetrics
        ).state
        val projectedState = if (unconfirmedSetCount > 0) {
            val projectedEntries = entries.map { item ->
                if (item.entry.date != todayString) item
                else item.copy(sets = item.sets.map { set -> set.copy(confirmed = true) })
            }
            calculator.calculate(
                targetDate = today,
                exercises = exercises,
                entriesWithSets = projectedEntries,
                initialProfile = initialProfile,
                dailyMetrics = dailyMetrics
            ).state
        } else {
            null
        }
        val firstConfirmedWorkoutDate = entries.asSequence()
            .filter { item -> item.sets.any { it.confirmed } }
            .mapNotNull { item -> runCatching { LocalDate.parse(item.entry.date) }.getOrNull() }
            .minOrNull()
        val confirmedChartResults = if (firstConfirmedWorkoutDate == null) {
            emptyList()
        } else {
            val periodStart = today.minusDays(6)
            val chartStart = maxOf(periodStart, firstConfirmedWorkoutDate)
            results.filter { result -> result.state.date >= chartStart }
        }
        val chartResults = if (unconfirmedSetCount > 0 && confirmedChartResults.size < 2) {
            results.takeLast(2)
        } else {
            confirmedChartResults
        }
        val currentTrainingLoadSeries = chartResults.map { result ->
            MiniTrendPoint(result.state.date, result.state.confirmedTrainingLoad)
        }
        val currentFatigueSeries = chartResults.map { result ->
            MiniTrendPoint(result.state.date, result.state.overallFatigueIndex.toDouble())
        }
        return@withContext HomeTodaySummaryState(
            date = today,
            plannedExerciseCount = todayEntries.size,
            confirmedSetCount = confirmedSetCount,
            unconfirmedSetCount = unconfirmedSetCount,
            fatigueLabel = todayState.readinessLabel,
            fatigueScore = todayState.overallFatigueIndex,
            fatigueHeadline = FatigueLabelResolver.headline(todayState.readinessLabel),
            fatigueCard = HomeFatigueCardSummaryFactory.create(
                preWorkout = preWorkoutState,
                current = todayState,
                projected = projectedState,
                confirmedSetCount = confirmedSetCount,
                unconfirmedSetCount = unconfirmedSetCount,
                todayStatus = todayStatus
            ),
            cautionReasons = todayState.cautionReasons,
            recentTrainingLoadSeries = currentTrainingLoadSeries,
            projectedTrainingLoadSeries = HomeMiniChartSeriesBuilder.projected(
                current = currentTrainingLoadSeries,
                projectedTodayValue = projectedState?.confirmedTrainingLoad
            ),
            recentFatigueSeries = currentFatigueSeries,
            projectedFatigueSeries = HomeMiniChartSeriesBuilder.projected(
                current = currentFatigueSeries,
                projectedTodayValue = projectedState?.overallFatigueIndex?.toDouble()
            ),
            confidence = todayState.confidence,
            projectedFatigueScore = projectedState?.overallFatigueIndex
        )
    }

    private suspend fun resolvedRuntimeMetadataCatalog(
        exercises: List<Exercise>
    ): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataResolver(
            canonicalRuntimeMetadataCatalog,
            runtimeExerciseMetadataDao.all().map(RuntimeExerciseMetadataEntity::toRuntimeMetadata)
        ).catalog(exercises)
}
