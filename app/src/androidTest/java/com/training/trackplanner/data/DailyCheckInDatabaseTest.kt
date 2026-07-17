package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyCheckInDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrainingDatabase::class.java
    )

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun sameDateUpsertsAndNullableValuesSurvive() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val dao = db.dailyCheckInDao()

        dao.upsert(DailyCheckIn(date = "2026-06-23", bodyWeightKg = 80.0, overallFatigue = 3))
        dao.upsert(DailyCheckIn(date = "2026-06-23", bodyWeightKg = 80.5, overallFatigue = 5, lowerBodyFatigue = null))

        val stored = dao.getForDate("2026-06-23")
        assertEquals(5, stored?.overallFatigue)
        assertEquals(80.5, stored?.bodyWeightKg ?: 0.0, 0.001)
        assertNull(stored?.lowerBodyFatigue)
    }

    @Test
    fun recentRangeIsInclusiveAndOrdered() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val dao = db.dailyCheckInDao()
        dao.upsert(DailyCheckIn(date = "2026-06-21"))
        dao.upsert(DailyCheckIn(date = "2026-06-23"))
        dao.upsert(DailyCheckIn(date = "2026-06-25"))

        val rows = dao.observeBetween("2026-06-22", "2026-06-25").first()
        assertEquals(listOf("2026-06-23", "2026-06-25"), rows.map { it.date })
    }

    @Test
    @Throws(IOException::class)
    fun migration14To15CreatesDailyCheckInTable() {
        val name = "daily-check-in-migration"
        migrationHelper.createDatabase(name, 14).close()
        val migrated = migrationHelper.runMigrationsAndValidate(
            name,
            15,
            true,
            TrainingDatabase.MIGRATION_14_15
        )

        migrated.query("SELECT date FROM daily_check_ins").use { cursor ->
            assertEquals(0, cursor.count)
        }
        migrated.close()
    }
}
