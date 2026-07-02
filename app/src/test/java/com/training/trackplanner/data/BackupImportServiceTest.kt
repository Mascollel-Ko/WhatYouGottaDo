package com.training.trackplanner.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupImportServiceTest {
    @Test
    fun importTextDispatchesRestoreBackup() = runBlocking {
        var importedSetRows = 0
        val service = BackupImportService(
            restoreImporter = { data ->
                importedSetRows = data.setRows.size
                RecordCsvTransferResult(format = "restore", setCount = importedSetRows)
            },
            dailyTimeseriesImporter = { error("daily importer should not run") }
        )
        val csv = """
            schema_version,row_type,date,entry_key,entry_order,exercise_name,category,confirmed,rest_seconds,set_index,set_confirmed,reps,weight_kg,seconds
            1,set,2026-06-15,e1,1,Deadlift,Strength,1,120,1,1,3,160,0
        """.trimIndent()

        val result = service.importText(csv)

        assertEquals("restore", result.format)
        assertEquals(1, importedSetRows)
    }

    @Test
    fun importTextDispatchesDailyTimeseriesBackup() = runBlocking {
        var importedRows = 0
        val service = BackupImportService(
            restoreImporter = { error("restore importer should not run") },
            dailyTimeseriesImporter = { data ->
                importedRows = data.rows.size
                RecordCsvTransferResult(format = "daily_timeseries", dailyMetricCount = importedRows)
            }
        )
        val csv = """
            date,sleep_hours,body_weight_kg,total_entries,confirmed_entries,planned_entries,total_sets,total_reps,total_tonnage_kg,total_seconds,strength_entries,functional_entries,cardio_entries,sports_entries,exercises_summary
            2026-06-15,7.5,72,1,1,0,1,3,480,0,1,0,0,0,Deadlift
        """.trimIndent()

        val result = service.importText(csv)

        assertEquals("daily_timeseries", result.format)
        assertEquals(1, importedRows)
    }
}
