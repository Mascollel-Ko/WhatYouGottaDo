package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectiveTissueAnalysisPerformanceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zoneId = ZoneId.of("Asia/Seoul")
    private val anchor = LocalDate.of(2026, 7, 17)

    @Test
    fun representativeHistorySizesRemainBoundedAndReportMeasurements() = runBlocking {
        val measurements = listOf(
            measure("short", (18 downTo 0 step 2).map { anchor.minusDays(it.toLong()) }),
            measure("approximately_56_valid_days", (70 downTo 0).map { anchor.minusDays(it.toLong()) }),
            measure("long_with_multiple_excluded_gaps", longHistoryDates())
        )

        measurements.forEach { measurement ->
            println(
                "TISSUE_PHASE2_PERFORMANCE scenario=${measurement.name} " +
                    "records=${measurement.recordCount} elapsedMs=${measurement.elapsedMs}"
            )
            assertEquals(77, measurement.loadUnitCount)
        }
    }

    private suspend fun measure(name: String, dates: List<LocalDate>): Measurement {
        val database = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return try {
            val exerciseId = database.exerciseDao().insertExercise(
                Exercise(
                    name = "스쿼트",
                    category = "근력운동",
                    stableKey = "barbell_back_squat"
                )
            )
            dates.sorted().forEach { date ->
                val performedAt = date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
                val entryId = database.workoutDao().insertEntry(
                    WorkoutEntry(
                        date = date.toString(),
                        exerciseId = exerciseId,
                        exerciseName = "스쿼트",
                        category = "근력운동",
                        rpe = 7.0,
                        completedAt = performedAt,
                        firstConfirmedAt = performedAt,
                        performedAt = performedAt
                    )
                )
                database.workoutDao().insertSet(
                    WorkoutSet(
                        entryId = entryId,
                        setIndex = 1,
                        reps = 8,
                        weightKg = 60.0,
                        confirmed = true,
                        rpe = 7.0
                    )
                )
            }
            val service = ConnectiveTissueAnalysisService(
                context = context,
                exerciseDao = database.exerciseDao(),
                workoutDao = database.workoutDao(),
                dailyMetricDao = database.dailyMetricDao(),
                initialUserProfileDao = database.initialUserProfileDao(),
                dailyCheckInDao = database.dailyCheckInDao(),
                zoneId = zoneId
            )
            val now = anchor.atTime(20, 0).atZone(zoneId).toInstant().toEpochMilli()
            lateinit var state: com.training.trackplanner.analysis.tissue.TissueCurrentState
            val elapsed = measureTimeMillis { state = service.build(now) }
            Measurement(name, dates.size, elapsed, state.loadUnits.size)
        } finally {
            database.close()
        }
    }

    private fun longHistoryDates(): List<LocalDate> = buildList {
        addAll((134 downTo 95).map { anchor.minusDays(it.toLong()) })
        addAll((80 downTo 41).map { anchor.minusDays(it.toLong()) })
        addAll((26 downTo 0).map { anchor.minusDays(it.toLong()) })
    }

    private data class Measurement(
        val name: String,
        val recordCount: Int,
        val elapsedMs: Long,
        val loadUnitCount: Int
    )
}
