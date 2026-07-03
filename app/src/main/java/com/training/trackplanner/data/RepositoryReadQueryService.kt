package com.training.trackplanner.data

import kotlinx.coroutines.flow.Flow

internal class RepositoryReadQueryService(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val initialUserProfileDao: InitialUserProfileDao
) {
    val exercises: Flow<List<Exercise>> = exerciseDao.observeExercises()
    val analysisStats: Flow<AnalysisStats> = workoutDao.observeAnalysisStats()
    val initialUserProfile: Flow<InitialUserProfile?> = initialUserProfileDao.observeProfile()

    fun entriesForDate(date: String): Flow<List<WorkoutEntryWithSets>> =
        workoutDao.observeEntriesWithSets(date)

    fun entryCount(date: String): Flow<Int> =
        workoutDao.observeEntryCount(date)

    fun plannedSetCount(date: String): Flow<Int> =
        workoutDao.observePlannedSetCount(date)

    fun confirmedSetCount(date: String): Flow<Int> =
        workoutDao.observeConfirmedSetCount(date)

    fun dailySummaries(startDate: String, endDate: String): Flow<List<DailyRecordSummary>> =
        workoutDao.observeDailySummariesBetween(startDate, endDate)
}
