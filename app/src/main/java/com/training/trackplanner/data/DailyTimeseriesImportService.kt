package com.training.trackplanner.data

import androidx.room.withTransaction

internal class DailyTimeseriesImportService(
    private val db: TrainingDatabase,
    private val workoutDao: WorkoutDao,
    private val dailyStatusService: DailyStatusService,
    private val confirmedCategoryCounts: (DailyTimeseriesRow) -> Map<String, Int>,
    private val findOrCreateConfirmedExercise: suspend (String) -> Exercise,
    private val findOrCreatePlannedExercise: suspend () -> Exercise
) {
    suspend fun importDailyTimeseriesCsv(
        data: RecordCsvImportData.DailyTimeseries
    ): RecordCsvTransferResult {
        var dailyCount = 0
        var entryCount = 0
        var setCount = 0
        var skipped = 0
        db.withTransaction {
            data.rows.forEach { row ->
                if (row.sleepHours != null || row.bodyWeightKg != null) {
                    dailyStatusService.saveDailyMetricInTransaction(
                        date = row.date,
                        sleepHours = row.sleepHours,
                        bodyWeightKg = row.bodyWeightKg
                    )
                    dailyCount += 1
                }

                val existing = workoutDao.entriesWithSets(row.date)
                if (existing.any { item -> item.entry.notes == TIMESERIES_IMPORT_NOTE }) {
                    skipped += 1
                    return@forEach
                }

                val confirmedCategoryCounts = confirmedCategoryCounts(row)
                if (confirmedCategoryCounts.isEmpty() && row.plannedEntries <= 0) return@forEach

                val confirmedTotal = confirmedCategoryCounts.values.sum().coerceAtLeast(1)
                confirmedCategoryCounts.forEach { (category, categoryCount) ->
                    val ratio = categoryCount.toDouble() / confirmedTotal
                    val reps = (row.totalReps * ratio).toInt().coerceAtLeast(0)
                    val tonnage = row.totalTonnageKg * ratio
                    val seconds = (row.totalSeconds * ratio).toInt().coerceAtLeast(0)
                    val setsForCategory = (row.totalSets * ratio).toInt().coerceAtLeast(1)
                    val weight = if (reps > 0 && tonnage > 0.0) tonnage / reps else 0.0
                    val exercise = findOrCreateConfirmedExercise(category)
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
                    val exercise = findOrCreatePlannedExercise()
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

    private companion object {
        const val TIMESERIES_IMPORT_NOTE = "CSV daily_timeseries import"
    }
}
