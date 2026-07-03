package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DailyTimeseriesImportBehaviorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun dailyTimeseriesImport_importsMetricsAndGeneratedEntries() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)

        val result = repository.importRecordsBackup(writeBackup(dailyTimeseriesCsv()))
        val metric = db.dailyMetricDao().metric("2026-07-10")
        val entries = db.workoutDao().entriesWithSets("2026-07-10")
        val confirmedEntry = entries.single { item -> item.sets.all { set -> set.confirmed } }
        val plannedEntry = entries.single { item -> item.sets.any { set -> !set.confirmed } }

        assertEquals("daily_timeseries", result.format)
        assertEquals(1, result.dailyMetricCount)
        assertEquals(2, result.entryCount)
        assertEquals(4, result.setCount)
        assertEquals(0, result.skippedDuplicateCount)
        assertNotNull(metric)
        assertEquals(7.25, metric?.sleepHours ?: 0.0, 0.001)
        assertEquals(72.5, metric?.bodyWeightKg ?: 0.0, 0.001)
        assertEquals(3, confirmedEntry.sets.size)
        assertTrue(confirmedEntry.sets.all { set -> set.confirmed })
        assertEquals(1, plannedEntry.sets.size)
        assertFalse(plannedEntry.sets.single().confirmed)
        assertTrue(entries.all { item -> item.entry.notes == "CSV daily_timeseries import" })
    }

    @Test
    fun dailyTimeseriesImport_skipsGeneratedEntriesWhenDateAlreadyImported() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        repository.importRecordsBackup(writeBackup(dailyTimeseriesCsv()))

        val duplicate = repository.importRecordsBackup(writeBackup(dailyTimeseriesCsv()))
        val entries = db.workoutDao().entriesWithSets("2026-07-10")

        assertEquals(1, duplicate.dailyMetricCount)
        assertEquals(0, duplicate.entryCount)
        assertEquals(0, duplicate.setCount)
        assertEquals(1, duplicate.skippedDuplicateCount)
        assertEquals(2, entries.size)
    }

    @Test
    fun dailyTimeseriesImport_keepsExistingNullOverwritePolicyForPartialMetricRows() = runBlocking {
        val db = newDatabase()
        db.dailyMetricDao().upsert(DailyMetric(date = "2026-07-11", sleepHours = 8.0, bodyWeightKg = 70.0))
        val repository = repository(db)
        val csv = dailyTimeseriesCsv(
            rows = listOf("2026-07-11,,73.4,0,0,0,0,0,0,0,0,0,0,0,partial metric")
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val metric = db.dailyMetricDao().metric("2026-07-11")
        val entries = db.workoutDao().entriesWithSets("2026-07-11")

        assertEquals(1, result.dailyMetricCount)
        assertEquals(0, result.entryCount)
        assertEquals(0, result.setCount)
        assertEquals(null, metric?.sleepHours)
        assertEquals(73.4, metric?.bodyWeightKg ?: 0.0, 0.001)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun dailyTimeseriesImport_reportsWarningsAndSkipsInvalidDateRows() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val csv = dailyTimeseriesCsv(
            rows = listOf(
                "not-a-date,7.0,71.0,1,1,0,1,10,100,0,1,0,0,0,bad date",
                "2026-07-12,6.5,,0,0,0,0,0,0,0,0,0,0,0,valid metric"
            )
        )

        val result = repository.importRecordsBackup(writeBackup(csv))

        assertEquals(1, result.warningCount)
        assertEquals(1, result.dailyMetricCount)
        assertEquals(null, db.dailyMetricDao().metric("not-a-date"))
        assertEquals(6.5, db.dailyMetricDao().metric("2026-07-12")?.sleepHours ?: 0.0, 0.001)
    }

    private fun dailyTimeseriesCsv(
        rows: List<String> = listOf(
            "2026-07-10,7.25,72.5,2,1,1,3,30,1200,600,1,0,0,0,deadlift"
        )
    ): String = buildString {
        appendLine(
            "date,sleep_hours,body_weight_kg,total_entries,confirmed_entries,planned_entries," +
                "total_sets,total_reps,total_tonnage_kg,total_seconds,strength_entries,functional_entries," +
                "cardio_entries,sports_entries,exercises_summary"
        )
        rows.forEach { row -> appendLine(row) }
    }.trimEnd()

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun repository(db: TrainingDatabase): TrainingRepository =
        TrainingRepository(db, context)

    private fun writeBackup(csv: String): Uri {
        val file = File.createTempFile("daily-timeseries-import", ".csv")
        file.writeText(csv, Charsets.UTF_8)
        file.deleteOnExit()
        return Uri.fromFile(file)
    }
}
