package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnalysisSummaryServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun fatigueAnalysisHistoryReturnsRepresentativeSeriesForConfirmedRecords() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val exerciseStableKey = insertFatigueExercise(db, "analysis.fatigue", "Fatigue lift")
        insertEntryWithSet(db, today.toString(), exerciseStableKey, "Fatigue lift", confirmed = true)

        val history = service.fatigueAnalysisHistory(days = 7)

        assertEquals(7, history.size)
        assertEquals(today, history.last().state.date)
        assertTrue(history.last().recordContributions.isNotEmpty())
        history.flatMap { it.axisValues() }.forEach { value ->
            assertFalse(value.isNaN())
            assertFalse(value.isInfinite())
        }
    }

    @Test
    fun fatigueAnalysisHistoryEmptyDataKeepsSafeFallbackSeries() = runBlocking {
        val db = newDatabase()
        val service = service(db)

        val history = service.fatigueAnalysisHistory(days = 3)

        assertEquals(3, history.size)
        assertTrue(history.all { it.recordContributions.isEmpty() })
        assertTrue(history.all { it.state.confirmedTrainingLoad == 0.0 })
    }

    @Test
    fun analysisSummariesIgnoreFutureAndOutOfWindowRecords() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val exerciseStableKey = insertFatigueExercise(db, "analysis.window", "Window lift")
        insertEntryWithSet(db, today.minusDays(2).toString(), exerciseStableKey, "Window lift", confirmed = true)
        insertEntryWithSet(db, today.plusDays(1).toString(), exerciseStableKey, "Window lift", confirmed = true)
        insertEntryWithSet(db, today.minusDays(40).toString(), exerciseStableKey, "Window lift", confirmed = true)

        val fatigueHistory = service.fatigueAnalysisHistory(days = 3)

        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), fatigueHistory.map { it.state.date })
    }

    @Test
    fun calendarOfiRangeUsesCanonicalHistoryForHistoricalAndRestDates() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()
        val workoutDate = today.minusDays(3)
        val stableKey = insertFatigueExercise(db, "analysis.calendar", "Calendar lift")
        insertEntryWithSet(db, workoutDate.toString(), stableKey, "Calendar lift", confirmed = true)

        val ofiByDate = service.calendarOfiByDate(
            workoutDate.toString(),
            workoutDate.plusDays(2).toString()
        )
        val canonical = service.fatigueAnalysisHistory(days = 4)
            .associate { it.state.date.toString() to it.state.overallFatigueIndex }

        assertEquals(3, ofiByDate.size)
        assertEquals(canonical[workoutDate.toString()], ofiByDate[workoutDate.toString()])
        assertEquals(canonical[workoutDate.plusDays(1).toString()], ofiByDate[workoutDate.plusDays(1).toString()])
        assertTrue((ofiByDate[workoutDate.plusDays(1).toString()] ?: 0) > 0)
    }

    @Test
    fun calendarOfiRangeLoadsMonthsOutsideRecentAnalysisWindow() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val historicalDate = LocalDate.now().minusMonths(18).withDayOfMonth(10)
        val stableKey = insertFatigueExercise(db, "analysis.old.calendar", "Old calendar lift")
        insertEntryWithSet(db, historicalDate.toString(), stableKey, "Old calendar lift", confirmed = true)

        val ofiByDate = service.calendarOfiByDate(
            historicalDate.withDayOfMonth(1).toString(),
            historicalDate.withDayOfMonth(historicalDate.lengthOfMonth()).toString()
        )

        assertEquals(historicalDate.lengthOfMonth(), ofiByDate.size)
        assertTrue((ofiByDate[historicalDate.toString()] ?: 0) > 0)
    }

    @Test
    fun calendarOfiRangeReturnsEmptyForFutureAndClampsRangesCrossingToday() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val today = LocalDate.now()

        assertTrue(
            service.calendarOfiByDate(
                today.plusDays(1).toString(),
                today.plusDays(10).toString()
            ).isEmpty()
        )

        val crossing = service.calendarOfiByDate(
            today.minusDays(1).toString(),
            today.plusDays(10).toString()
        )
        assertEquals(setOf(today.minusDays(1).toString(), today.toString()), crossing.keys)
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase): AnalysisSummaryService =
        AnalysisSummaryService(
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            dailyMetricDao = db.dailyMetricDao(),
            initialUserProfileDao = db.initialUserProfileDao(),
            runtimeExerciseMetadataDao = db.runtimeExerciseMetadataDao(),
            canonicalRuntimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
            canonicalOfiAxisProfiles = emptyMap()
        )

    private suspend fun insertFatigueExercise(
        db: TrainingDatabase,
        stableKey: String,
        name: String
    ): String {
        db.exerciseDao().insertExercise(
            Exercise(
                name = name,
                category = "Strength",
                stableKey = stableKey,
                analysisEligibility = "FATIGUE|STRENGTH_PROGRESS",
                progressMetricType = "ESTIMATED_1RM",
                strengthProgressionGroup = "SQUAT",
                movementPattern = "SQUAT",
                movementCategory = "LOWER_STRENGTH",
                primaryMuscles = "QUADS|GLUTES",
                systemicLoadWeight = 0.8,
                neuralHeavyWeight = 0.7,
                localLoadWeight = 0.6,
                decelerationWeight = 0.2,
                stabilityDemandLevel = "MODERATE",
                recoveryDecayProfile = "LONG",
                estimated1RmEligible = true,
                volumeLoadEligible = true
            )
        )
        val exerciseStableKey = stableKey
        return stableKey
    }

    private suspend fun insertEntryWithSet(
        db: TrainingDatabase,
        date: String,
        exerciseStableKey: String,
        exerciseName: String,
        confirmed: Boolean
    ) {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = date,
                exerciseStableKey = exerciseStableKey,
                exerciseName = exerciseName,
                category = "Test",
                rpe = 8.0,
                completedAt = if (confirmed) 1_000L else null,
                firstConfirmedAt = if (confirmed) 1_000L else null,
                displayOrder = 1
            )
        )
        db.workoutDao().insertSet(
            WorkoutSet(
                entryId = entryId,
                setIndex = 1,
                reps = 10,
                weightKg = 60.0,
                seconds = 0,
                confirmed = confirmed,
                rpe = 8.0
            )
        )
    }
}

private fun com.training.trackplanner.analysis.fatigue.DailyFatigueResult.axisValues(): List<Double> =
    listOf(
        state.highForceNeuralFatigue,
        state.systemicMuscularFatigue,
        state.localMuscularFatigue,
        state.highSpeedFatigue,
        state.reactiveFatigue,
        state.recoveryPressure,
        state.confirmedTrainingLoad
    )
