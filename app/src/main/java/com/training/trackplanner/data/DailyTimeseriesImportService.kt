package com.training.trackplanner.data

import androidx.room.withTransaction

internal class DailyTimeseriesImportService(
    private val db: TrainingDatabase,
    private val dailyStatusService: DailyStatusService
) {
    suspend fun importDailyTimeseriesCsv(
        data: RecordCsvImportData.DailyTimeseries
    ): RecordCsvTransferResult {
        var dailyCount = 0
        var aggregateRowsSkipped = 0
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
                if (row.hasWorkoutAggregate()) aggregateRowsSkipped += 1
            }
        }
        return RecordCsvTransferResult(
            format = "daily_timeseries",
            dailyMetricCount = dailyCount,
            warningCount = data.warningCount + aggregateRowsSkipped
        )
    }

    private fun DailyTimeseriesRow.hasWorkoutAggregate(): Boolean =
        totalEntries > 0 ||
            confirmedEntries > 0 ||
            plannedEntries > 0 ||
            totalSets > 0 ||
            totalReps > 0 ||
            totalTonnageKg > 0.0 ||
            totalSeconds > 0 ||
            strengthEntries > 0 ||
            functionalEntries > 0 ||
            cardioEntries > 0 ||
            sportsEntries > 0
}
