package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DailyStatusServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun canonicalSaveSynchronizesLegacyProjectionAndObservers() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val saved = DailyCheckIn(
            date = "2026-07-17",
            sleepHours = 7.5,
            bodyWeightKg = 80.5,
            overallFatigue = 3
        )

        service.upsertDailyCheckIn(saved)

        val canonical = service.checkInForDate(saved.date)!!
        assertEquals(7.5, canonical.sleepHours ?: 0.0, 0.001)
        assertEquals(3, canonical.overallFatigue)
        assertEquals(80.5, db.dailyMetricDao().metric(saved.date)?.bodyWeightKg ?: 0.0, 0.001)
        assertEquals(80.5, service.observeCheckInForDate(saved.date).first()?.bodyWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun legacyMetricSavePreservesSubjectiveConditionAndCannotDiverge() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        service.upsertDailyCheckIn(
            DailyCheckIn(
                date = "2026-07-17",
                overallFatigue = 4,
                focusMotivation = 2,
                note = "preserve me"
            )
        )

        service.saveDailyMetric("2026-07-17", sleepHours = 6.25, bodyWeightKg = 79.75)

        val canonical = service.checkInForDate("2026-07-17")!!
        assertEquals(4, canonical.overallFatigue)
        assertEquals(2, canonical.focusMotivation)
        assertEquals("preserve me", canonical.note)
        assertEquals(6.25, canonical.sleepHours ?: 0.0, 0.001)
        assertEquals(79.75, canonical.bodyWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun clearingBodyWeightPreservesOtherConditionFields() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        service.upsertDailyCheckIn(
            DailyCheckIn(
                date = "2026-07-17",
                sleepHours = 8.0,
                bodyWeightKg = 80.5,
                lowerBodyFatigue = 5
            )
        )

        service.upsertDailyCheckIn(service.checkInForDate("2026-07-17")!!.copy(bodyWeightKg = null))

        val canonical = service.checkInForDate("2026-07-17")!!
        assertNull(canonical.bodyWeightKg)
        assertEquals(8.0, canonical.sleepHours ?: 0.0, 0.001)
        assertEquals(5, canonical.lowerBodyFatigue)
        assertNull(db.dailyMetricDao().metric("2026-07-17")?.bodyWeightKg)
    }

    @Test
    fun repeatedSameDateSavesUpdateOneRecordAndHistoricalDateStaysSeparate() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        service.upsertDailyCheckIn(DailyCheckIn(date = "2026-07-17", bodyWeightKg = 80.0))
        service.upsertDailyCheckIn(DailyCheckIn(date = "2026-07-17", bodyWeightKg = 80.5))
        service.upsertDailyCheckIn(DailyCheckIn(date = "2026-07-16", bodyWeightKg = 81.0))

        val records = db.dailyCheckInDao().all()
        assertEquals(2, records.size)
        assertEquals(80.5, records.single { it.date == "2026-07-17" }.bodyWeightKg ?: 0.0, 0.001)
        assertEquals(81.0, records.single { it.date == "2026-07-16" }.bodyWeightKg ?: 0.0, 0.001)
        assertEquals(2, db.dailyMetricDao().allMetrics().size)
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase): DailyStatusService =
        DailyStatusService(
            db = db,
            dailyMetricDao = db.dailyMetricDao(),
            dailyCheckInDao = db.dailyCheckInDao()
        )
}
